package com.ali6eza.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @NotBlank(message = "originalUrl is required")
        @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
        String originalUrl
) {
}
