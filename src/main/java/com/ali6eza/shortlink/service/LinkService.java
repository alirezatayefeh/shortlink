package com.ali6eza.shortlink.service;

import com.ali6eza.shortlink.dto.CreateLinkResponse;
import com.ali6eza.shortlink.entity.Link;
import com.ali6eza.shortlink.exception.InvalidUrlException;
import com.ali6eza.shortlink.exception.InvalidExpirationException;
import com.ali6eza.shortlink.exception.LinkExpiredException;
import com.ali6eza.shortlink.exception.LinkNotFoundException;
import com.ali6eza.shortlink.exception.ShortCodeGenerationException;
import com.ali6eza.shortlink.repository.LinkRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LinkService {
    private static final int MAX_ATTEMPTS = 5;
    private static final String SHORT_CODE_CONSTRAINT = "uk_links_short_code";

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator codeGenerator;
    private final String baseUrl;
    private final Clock clock;

    public LinkService(
            LinkRepository linkRepository,
            ShortCodeGenerator codeGenerator,
            Clock clock,
            @Value("${app.base-url}") String baseUrl
    ) {
        this.linkRepository = linkRepository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public CreateLinkResponse create(String originalUrl, Instant expiresAt) {
        validateUrl(originalUrl);
        // PostgreSQL timestamps preserve microseconds, not Java's full nanosecond precision.
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiration = expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MICROS);
        if (expiration != null && !expiration.isAfter(createdAt)) {
            throw new InvalidExpirationException();
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Link link = new Link(originalUrl, codeGenerator.generate(), createdAt, expiration);
            try {
                // Each attempt uses the repository's own transaction; a collision must roll back first.
                Link savedLink = linkRepository.saveAndFlush(link);
                return toResponse(savedLink);
            } catch (DataIntegrityViolationException exception) {
                if (!isShortCodeCollision(exception)) {
                    throw exception;
                }
            }
        }
        throw new ShortCodeGenerationException();
    }

    public String getOriginalUrl(String shortCode) {
        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(LinkNotFoundException::new);
        if (link.isExpiredAt(clock.instant())) {
            throw new LinkExpiredException();
        }
        return link.getOriginalUrl();
    }

    private CreateLinkResponse toResponse(Link link) {
        return new CreateLinkResponse(
                link.getShortCode(),
                baseUrl + "/" + link.getShortCode(),
                link.getOriginalUrl(),
                link.getCreatedAt(),
                link.getExpiresAt()
        );
    }

    private boolean isShortCodeCollision(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return SHORT_CODE_CONSTRAINT.equals(violation.getConstraintName());
            }
        }
        return false;
    }

    private void validateUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank() || originalUrl.length() > 2048) {
            throw new InvalidUrlException();
        }
        try {
            URI uri = new URI(originalUrl);
            boolean allowedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!allowedScheme || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535) {
                throw new InvalidUrlException();
            }
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException();
        }
    }
}
