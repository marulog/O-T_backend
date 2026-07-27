package com.ott.infra.db.media_metrics.repository;

import com.ott.domain.media.domain.Media;

import java.util.List;

public interface MediaMetricsRepositoryCustom {

    List<Media> findTopByWeightedScore(int popularity, int immersion, int mania, int recency, int reWatch, Long excludeMediaId, int limit);
}
