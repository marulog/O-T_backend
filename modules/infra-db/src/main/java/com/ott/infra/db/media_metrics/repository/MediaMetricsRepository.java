package com.ott.infra.db.media_metrics.repository;

import com.ott.domain.media_metrics.domain.MediaMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaMetricsRepository extends JpaRepository<MediaMetrics, Long>, MediaMetricsRepositoryCustom {
}
