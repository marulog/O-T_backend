package com.ott.infra.db.media_metrics.repository;

import java.math.BigDecimal;

public record MediaMetricsProjection(Long mediaId, BigDecimal score) {}
