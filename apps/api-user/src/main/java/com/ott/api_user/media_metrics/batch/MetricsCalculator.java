package com.ott.api_user.media_metrics.batch;

import com.ott.infra.db.media_metrics.repository.MediaMetricsJdbcRepository;
import com.ott.infra.db.media_metrics.repository.MediaMetricsProjection;
import com.ott.infra.db.media_metrics.repository.MediaMetricsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCalculator {

    private final MediaMetricsJdbcRepository mediaMetricsJdbcRepository;
    private final MetricsWriter metricsWriter;

    public void calculate() {
        log.info("[MetricsCalculator] 배치 시작");

        Map<Long, BigDecimal> popularityMap = toScoreMap(mediaMetricsJdbcRepository.computePopularity());
        Map<Long, BigDecimal> immersionMap  = toScoreMap(mediaMetricsJdbcRepository.computeImmersion());
        Map<Long, BigDecimal> maniaMap      = toScoreMap(mediaMetricsJdbcRepository.computeMania());
        Map<Long, BigDecimal> recencyMap    = toScoreMap(mediaMetricsJdbcRepository.computeRecency());
        Map<Long, BigDecimal> reWatchMap    = toScoreMap(mediaMetricsJdbcRepository.computeReWatch());

        List<MediaMetricsRow> rowList = mergeResults(
                popularityMap, immersionMap, maniaMap, recencyMap, reWatchMap);

        if (!rowList.isEmpty()) {
            metricsWriter.save(rowList);
        }

        log.info("[MetricsCalculator] 배치 완료 — {}건 갱신", rowList.size());
    }

    private Map<Long, BigDecimal> toScoreMap(List<MediaMetricsProjection> projectionList) {
        return projectionList.stream()
                .collect(Collectors.toMap(
                        MediaMetricsProjection::mediaId,
                        MediaMetricsProjection::score,
                        (existing, replacement) -> replacement
                ));
    }

    private List<MediaMetricsRow> mergeResults(
            Map<Long, BigDecimal> popularityMap,
            Map<Long, BigDecimal> immersionMap,
            Map<Long, BigDecimal> maniaMap,
            Map<Long, BigDecimal> recencyMap,
            Map<Long, BigDecimal> reWatchMap) {

        Set<Long> allMediaIdSet = new HashSet<>();
        allMediaIdSet.addAll(popularityMap.keySet());
        allMediaIdSet.addAll(immersionMap.keySet());
        allMediaIdSet.addAll(maniaMap.keySet());
        allMediaIdSet.addAll(recencyMap.keySet());
        allMediaIdSet.addAll(reWatchMap.keySet());

        return allMediaIdSet.stream()
                .map(mediaId -> new MediaMetricsRow(
                        mediaId,
                        popularityMap.getOrDefault(mediaId, BigDecimal.ZERO),
                        immersionMap.getOrDefault(mediaId, BigDecimal.ZERO),
                        maniaMap.getOrDefault(mediaId, BigDecimal.ZERO),
                        recencyMap.getOrDefault(mediaId, BigDecimal.ZERO),
                        reWatchMap.getOrDefault(mediaId, BigDecimal.ZERO)
                ))
                .toList();
    }
}
