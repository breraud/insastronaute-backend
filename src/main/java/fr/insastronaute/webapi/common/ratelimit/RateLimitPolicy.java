package fr.insastronaute.webapi.common.ratelimit;

import java.time.Duration;

public record RateLimitPolicy(
        String id,
        int limit,
        Duration window
) {
    public RateLimitPolicy {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rate-limit policy id is required.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Rate-limit policy limit must be positive.");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate-limit policy window must be positive.");
        }
    }
}
