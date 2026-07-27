package com.ott.infra.db.media_metrics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MediaMetricsJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MediaMetricsProjection> SCORE_MAPPER = (rs, rowNum) ->
            new MediaMetricsProjection(
                    rs.getLong("media_id"),
                    rs.getBigDecimal("score")
            );

    // 대중성: bookmark_count 기준 PERCENT_RANK
    public List<MediaMetricsProjection> computePopularity() {
        String sql = """
                SELECT id AS media_id,
                       ROUND(PERCENT_RANK() OVER (ORDER BY bookmark_count) * 100, 2) AS score
                FROM media
                WHERE status = 'ACTIVE'
                AND public_status = 'PUBLIC'
                AND media_status = 'COMPLETED'
                """;
        return jdbcTemplate.query(sql, SCORE_MAPPER);
    }

    /**
     * 몰입도: 80% 이상 시청 완주한 사용자 비율 기준 PERCENT_RANK
     * - 단편: position_sec / duration >= 0.8 비율
     * - 시리즈: 사용자별 SUM(position_sec) / SUM(duration) >= 0.8 비율
     */
    public List<MediaMetricsProjection> computeImmersion() {
        String sql = """
                SELECT media_id,
                       ROUND(PERCENT_RANK() OVER (ORDER BY completion_rate) * 100, 2) AS score
                FROM (
                    SELECT c.media_id,
                           SUM(CASE WHEN p.position_sec / c.duration >= 0.8 THEN 1 ELSE 0 END)
                               / COUNT(*) AS completion_rate
                    FROM playback p
                    JOIN contents c ON p.contents_id = c.id
                    WHERE c.series_id IS NULL
                      AND c.duration > 0
                      AND p.status = 'ACTIVE'
                      AND media_status = 'COMPLETED'
                    GROUP BY c.media_id

                    UNION ALL

                    SELECT s.media_id,
                           SUM(CASE WHEN user_agg.watched / user_agg.total_dur >= 0.8 THEN 1 ELSE 0 END)
                               / COUNT(*) AS completion_rate
                    FROM (
                        SELECT c.series_id,
                               p.member_id,
                               SUM(p.position_sec) AS watched,
                               SUM(c.duration)     AS total_dur
                        FROM playback p
                        JOIN contents c ON p.contents_id = c.id
                        WHERE c.series_id IS NOT NULL
                          AND c.duration > 0
                          AND p.status = 'ACTIVE'
                        GROUP BY c.series_id, p.member_id
                    ) user_agg
                    JOIN series s ON user_agg.series_id = s.id
                    GROUP BY s.media_id
                ) raw_data
                """;
        return jdbcTemplate.query(sql, SCORE_MAPPER);
    }

    /**
     * 마니아: 시청자 대비 북마크 비율 기준 PERCENT_RANK
     * - 시청이력 없는 미디어 제외 (JOIN으로 자연 필터)
     */
    public List<MediaMetricsProjection> computeMania() {
        String sql = """
                SELECT media_id,
                       ROUND(PERCENT_RANK() OVER (ORDER BY mania_ratio) * 100, 2) AS score
                FROM (
                    SELECT c.media_id,
                           m.bookmark_count / COUNT(DISTINCT wh.member_id) AS mania_ratio
                    FROM watch_history wh
                    JOIN contents c ON wh.contents_id = c.id
                    JOIN media m ON c.media_id = m.id
                    WHERE c.series_id IS NULL
                      AND wh.status = 'ACTIVE'
                      AND m.status = 'ACTIVE'
                      AND media_status = 'COMPLETED'
                      AND m.public_status = 'PUBLIC'
                    GROUP BY c.media_id, m.bookmark_count

                    UNION ALL

                    SELECT s.media_id,
                           m.bookmark_count / COUNT(DISTINCT wh.member_id) AS mania_ratio
                    FROM watch_history wh
                    JOIN contents c ON wh.contents_id = c.id
                    JOIN series s ON c.series_id = s.id
                    JOIN media m ON s.media_id = m.id
                    WHERE wh.status = 'ACTIVE'
                      AND m.status = 'ACTIVE'
                      AND m.public_status = 'PUBLIC'
                      AND media_status = 'COMPLETED'
                    GROUP BY s.media_id, m.bookmark_count
                ) raw_data
                """;
        return jdbcTemplate.query(sql, SCORE_MAPPER);
    }

    // 최신성: created_date 기준 PERCENT_RANK
    public List<MediaMetricsProjection> computeRecency() {
        String sql = """
                SELECT id AS media_id,
                       ROUND(PERCENT_RANK() OVER (ORDER BY created_date) * 100, 2) AS score
                FROM media
                WHERE status = 'ACTIVE'
                AND public_status = 'PUBLIC'
                AND media_status = 'COMPLETED'
                """;
        return jdbcTemplate.query(sql, SCORE_MAPPER);
    }

    /**
     * 재시청률: B / (A + B) 기준 PERCENT_RANK
     * - A = 시청이력 수 (COUNT), B = 재시청 횟수 합 (SUM(re_watch_count))
     */
    public List<MediaMetricsProjection> computeReWatch() {
        String sql = """
                SELECT media_id,
                       ROUND(PERCENT_RANK() OVER (ORDER BY re_watch_rate) * 100, 2) AS score
                FROM (
                    SELECT c.media_id,
                           SUM(wh.re_watch_count)
                               / (COUNT(*) + SUM(wh.re_watch_count)) AS re_watch_rate
                    FROM watch_history wh
                    JOIN contents c ON wh.contents_id = c.id
                    WHERE c.series_id IS NULL
                      AND wh.status = 'ACTIVE'
                      AND media_status = 'COMPLETED'
                    GROUP BY c.media_id

                    UNION ALL

                    SELECT s.media_id,
                           SUM(wh.re_watch_count)
                               / (COUNT(*) + SUM(wh.re_watch_count)) AS re_watch_rate
                    FROM watch_history wh
                    JOIN contents c ON wh.contents_id = c.id
                    JOIN series s ON c.series_id = s.id
                    WHERE wh.status = 'ACTIVE'
                    GROUP BY s.media_id
                ) raw_data
                """;
        return jdbcTemplate.query(sql, SCORE_MAPPER);
    }

    // media_metrics 벌크 UPSERT (media_id 기준)
    public void bulkUpsert(List<MediaMetricsRow> rowList) {
        String sql = """
                INSERT INTO media_metrics
                    (media_id, popularity, immersion, mania, recency, re_watch,
                     batch_updated_at, created_date, modified_date, status)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW(), 'ACTIVE')
                ON DUPLICATE KEY UPDATE
                    popularity       = VALUES(popularity),
                    immersion        = VALUES(immersion),
                    mania            = VALUES(mania),
                    recency          = VALUES(recency),
                    re_watch         = VALUES(re_watch),
                    batch_updated_at = NOW(),
                    modified_date    = NOW()
                """;

        jdbcTemplate.batchUpdate(sql, rowList, rowList.size(),
                (ps, row) -> {
                    ps.setLong(1, row.mediaId());
                    ps.setBigDecimal(2, row.popularity());
                    ps.setBigDecimal(3, row.immersion());
                    ps.setBigDecimal(4, row.mania());
                    ps.setBigDecimal(5, row.recency());
                    ps.setBigDecimal(6, row.reWatch());
                });
    }
}
