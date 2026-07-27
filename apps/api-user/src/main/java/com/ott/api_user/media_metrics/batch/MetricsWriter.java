package com.ott.api_user.media_metrics.batch;

import com.ott.infra.db.media_metrics.repository.MediaMetricsJdbcRepository;
import com.ott.infra.db.media_metrics.repository.MediaMetricsRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MetricsWriter {

    private final MediaMetricsJdbcRepository mediaMetricsJdbcRepository;

    @Transactional
    public void save(List<MediaMetricsRow> rowList) {
        mediaMetricsJdbcRepository.bulkUpsert(rowList);
    }
}
