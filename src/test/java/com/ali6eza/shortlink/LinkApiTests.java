package com.ali6eza.shortlink;

import com.ali6eza.shortlink.entity.Link;
import com.ali6eza.shortlink.repository.LinkRepository;
import com.ali6eza.shortlink.service.ShortCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.base-url=http://localhost:8080/")
@Import(TestcontainersConfiguration.class)
class LinkApiTests {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private LinkRepository repository;
    @MockitoBean
    private ShortCodeGenerator generator;
    @MockitoBean
    private Clock clock;

    private MockMvc mvc;
    private String shortCode;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        shortCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        when(generator.generate()).thenReturn(shortCode);
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createsPersistsAndRedirectsLink() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/article?q=java\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/" + shortCode))
                .andExpect(jsonPath("$.shortCode").value(shortCode))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/" + shortCode))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/article?q=java"))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(repository.findByShortCode(shortCode)).isPresent();
        assertThat(repository.findByShortCode(shortCode).orElseThrow().getExpiresAt()).isNull();
        when(clock.instant()).thenReturn(NOW.plusSeconds(86400));
        mvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/article?q=java"));
    }

    @Test
    void returnsNotFoundForUnknownCode() throws Exception {
        mvc.perform(get("/" + shortCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Link not found."));
    }

    @Test
    void rejectsInvalidUrlsWithoutSaving() throws Exception {
        for (String url : new String[]{"", "ftp://example.com", "https://user:pass@example.com",
                "not-a-url", "https://example.com:99999"}) {
            mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"originalUrl\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.findByShortCode(shortCode)).isEmpty();
    }

    @Test
    void rejectsMissingAndMalformedRequestBodies() throws Exception {
        for (String body : new String[]{"{}", "{", "{\"originalUrl\":null}"}) {
            mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void retriesCollisionInFreshTransaction() throws Exception {
        repository.saveAndFlush(new Link("https://example.com/first", shortCode, NOW, null));
        String nextCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        when(generator.generate()).thenReturn(shortCode, nextCode);

        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/second\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(nextCode));

        assertThat(repository.findByShortCode(shortCode).orElseThrow().getOriginalUrl())
                .isEqualTo("https://example.com/first");
        assertThat(repository.findByShortCode(nextCode)).isPresent();
        verify(generator, times(2)).generate();
    }

    @Test
    void stopsAfterRepeatedCollisions() throws Exception {
        repository.saveAndFlush(new Link("https://example.com/first", shortCode, NOW, null));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/second\"}"))
                .andExpect(status().isServiceUnavailable());
        verify(generator, times(5)).generate();
    }

    @Test
    void redirectsBeforeExpirationAndReturnsGoneAtAndAfterExpiration() throws Exception {
        Instant expiresAt = NOW.plusSeconds(60);
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com","expiresAt":"2030-01-01T00:01:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));

        assertThat(repository.findByShortCode(shortCode).orElseThrow().getExpiresAt())
                .isEqualTo(expiresAt);
        when(clock.instant()).thenReturn(expiresAt.minusNanos(1));
        mvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));

        for (Instant time : new Instant[]{expiresAt, expiresAt.plusSeconds(1)}) {
            when(clock.instant()).thenReturn(time);
            mvc.perform(get("/" + shortCode))
                    .andExpect(status().isGone())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.detail").value("Link has expired."));
        }
        assertThat(repository.findByShortCode(shortCode)).isPresent();
    }

    @Test
    void rejectsPastPresentAndMalformedExpirationWithoutSaving() throws Exception {
        for (String expiration : new String[]{
                "2029-12-31T23:59:59Z", "2030-01-01T00:00:00Z", "not-a-date"}) {
            mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"originalUrl":"https://example.com","expiresAt":"%s"}
                                    """.formatted(expiration)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.findByShortCode(shortCode)).isEmpty();
    }

    @Test
    void acceptsExplicitNullExpiration() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com","expiresAt":null}
                                """))
                .andExpect(status().isCreated());
        assertThat(repository.findByShortCode(shortCode).orElseThrow().getExpiresAt()).isNull();
    }

    @Test
    void normalizesOffsetAndSubMicrosecondPrecisionBeforeSaving() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com","expiresAt":"2030-01-01T03:31:00.123456789+03:30"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:01:00.123456Z"));
        assertThat(repository.findByShortCode(shortCode).orElseThrow().getExpiresAt())
                .isEqualTo(Instant.parse("2030-01-01T00:01:00.123456Z"));
    }
}
