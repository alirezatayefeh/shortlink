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
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private LinkRepository repository;
    @MockitoBean
    private ShortCodeGenerator generator;

    private MockMvc mvc;
    private String shortCode;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        shortCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        when(generator.generate()).thenReturn(shortCode);
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
        repository.saveAndFlush(new Link("https://example.com/first", shortCode));
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
        repository.saveAndFlush(new Link("https://example.com/first", shortCode));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/second\"}"))
                .andExpect(status().isServiceUnavailable());
        verify(generator, times(5)).generate();
    }
}
