package com.ali6eza.shortlink.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080",
            "https://example.com",
            "HTTP://example.com",
            "HTTPS://example.com",
            "https://example.com/api/",
            "http://localhost:1",
            "https://example.com:65535",
            "http://[::1]:8080"
    })
    void bindsValidBaseUrls(String baseUrl) {
        contextRunner.withPropertyValues("app.base-url=" + baseUrl).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AppProperties.class);
            assertThat(context.getBean(AppProperties.class).baseUrl()).isEqualTo(baseUrl);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-url",
            "/relative/path",
            "//example.com",
            "ftp://example.com",
            "https:/missing-host",
            "https://user:password@example.com",
            "https://example.com?query=value",
            "https://example.com#fragment",
            "https://example.com:0",
            "https://example.com:65536",
            "https://example.com:invalid",
            "https://example.com/a b",
            "https://example.com/%ZZ"
    })
    void invalidBaseUrlPreventsContextStartup(String baseUrl) {
        contextRunner.withPropertyValues("app.base-url=" + baseUrl).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .hasStackTraceContaining("app.base-url must be an absolute HTTP/HTTPS URL");
        });
    }

    @Test
    void missingBaseUrlPreventsContextStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .hasStackTraceContaining("baseUrl");
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void blankValuesAreRejectedByNotBlankWithoutDuplicateUrlErrors(String baseUrl) {
        // Use Bean Validation directly to preserve whitespace that property parsing may trim.
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new AppProperties(baseUrl));
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("baseUrl");
        }
    }

    @Test
    void emptyBaseUrlPreventsContextStartup() {
        contextRunner.withPropertyValues("app.base-url=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfiguration {
    }
}
