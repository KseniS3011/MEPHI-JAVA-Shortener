package model;

import java.time.Instant;

public record Link(
        String code,
        String ownerUuid,
        String originalUrl,
        String shortUrl,
        int maxClicks,
        int clicksDone,
        Instant createdAt,
        Instant expiresAt
) {}
