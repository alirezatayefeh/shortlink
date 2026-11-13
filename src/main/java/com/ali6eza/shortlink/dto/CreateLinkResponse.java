package com.ali6eza.shortlink.dto;

import java.time.Instant;

public record CreateLinkResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt
) {
}
