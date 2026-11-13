package com.ali6eza.shortlink.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String baseUrl
) {

    @AssertTrue(message =
            "app.base-url must be an absolute HTTP/HTTPS URL without credentials, query or fragment")
    public boolean isBaseUrlValid() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }

        try {
            URI uri = new URI(baseUrl);

            boolean allowedScheme =
                    "http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme());

            return allowedScheme
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && (uri.getPort() == -1
                    || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}