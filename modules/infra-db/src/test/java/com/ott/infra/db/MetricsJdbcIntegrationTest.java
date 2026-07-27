package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.media_metrics.repository.MediaMetricsJdbcRepository;
import com.ott.infra.db.media_metrics.repository.MediaMetricsProjection;
import com.ott.infra.db.media_metrics.repository.MediaMetricsRow;
import com.ott.infra.db.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = InfraDbConfiguration.class)
class MetricsJdbcIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MediaMetricsJdbcRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void metricQueriesAndBulkUpsertExecuteAgainstMySql() {
        Member uploader = memberRepository.saveAndFlush(member());
        Media lessPopular = persistMedia(uploader, "Less popular", 1L);
        Media morePopular = persistMedia(uploader, "More popular", 10L);

        List<MediaMetricsProjection> popularity = metricsRepository.computePopularity();
        assertThat(popularity).extracting(MediaMetricsProjection::mediaId)
                .containsExactlyInAnyOrder(lessPopular.getId(), morePopular.getId());
        assertThat(scoreFor(popularity, lessPopular.getId())).isEqualByComparingTo("0.00");
        assertThat(scoreFor(popularity, morePopular.getId())).isEqualByComparingTo("100.00");

        metricsRepository.bulkUpsert(List.of(row(morePopular.getId(), "10.00")));
        metricsRepository.bulkUpsert(List.of(row(morePopular.getId(), "90.00")));

        Map<String, Object> persisted = jdbcTemplate.queryForMap("""
                SELECT popularity, immersion, mania, recency, re_watch, status
                FROM media_metrics
                WHERE media_id = ?
                """, morePopular.getId());
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_metrics WHERE media_id = ?", Integer.class, morePopular.getId());

        assertThat(rowCount).isEqualTo(1);
        assertThat((BigDecimal) persisted.get("popularity")).isEqualByComparingTo("90.00");
        assertThat((BigDecimal) persisted.get("immersion")).isEqualByComparingTo("90.00");
        assertThat((BigDecimal) persisted.get("mania")).isEqualByComparingTo("90.00");
        assertThat((BigDecimal) persisted.get("recency")).isEqualByComparingTo("90.00");
        assertThat((BigDecimal) persisted.get("re_watch")).isEqualByComparingTo("90.00");
        assertThat(persisted.get("status")).hasToString("ACTIVE");
    }

    private Media persistMedia(Member uploader, String title, long bookmarkCount) {
        Media media = Media.builder()
                .uploader(uploader)
                .title(title)
                .description("Task 15 metrics integration sample")
                .posterUrl("https://cdn.example.com/poster.jpg")
                .thumbnailUrl("https://cdn.example.com/thumb.jpg")
                .bookmarkCount(bookmarkCount)
                .likesCount(0L)
                .mediaType(MediaType.CONTENTS)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
        entityManager.persist(media);
        entityManager.flush();
        return media;
    }

    private static Member member() {
        return Member.builder()
                .provider(Provider.KAKAO)
                .providerId("metrics-owner")
                .email("metrics-owner@example.com")
                .nickname("metrics-owner")
                .role(Role.EDITOR)
                .build();
    }

    private static MediaMetricsRow row(Long mediaId, String value) {
        BigDecimal score = new BigDecimal(value);
        return new MediaMetricsRow(mediaId, score, score, score, score, score);
    }

    private static BigDecimal scoreFor(List<MediaMetricsProjection> projections, Long mediaId) {
        return projections.stream()
                .filter(projection -> projection.mediaId().equals(mediaId))
                .map(MediaMetricsProjection::score)
                .findFirst()
                .orElseThrow();
    }
}
