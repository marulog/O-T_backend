package com.ott.infra.db.watch_history.repository;

public record TagViewCountProjection(
        String tagName,
        Long viewCount
) {
}
