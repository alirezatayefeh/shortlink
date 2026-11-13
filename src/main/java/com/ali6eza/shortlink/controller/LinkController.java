package com.ali6eza.shortlink.controller;

import com.ali6eza.shortlink.dto.CreateLinkRequest;
import com.ali6eza.shortlink.dto.CreateLinkResponse;
import com.ali6eza.shortlink.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class LinkController {
    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/api/links")
    public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        CreateLinkResponse response = linkService.create(request.originalUrl(), request.expiresAt());
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable("shortCode") String shortCode) {
        String originalUrl = linkService.getOriginalUrl(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
