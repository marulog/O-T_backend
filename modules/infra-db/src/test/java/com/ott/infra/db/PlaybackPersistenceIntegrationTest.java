package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.category.domain.Category;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.playback.domain.Playback;
import com.ott.domain.series.domain.Series;
import com.ott.domain.tag.domain.Tag;
import com.ott.domain.watch_history.domain.WatchHistory;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.infra.db.watch_history.repository.RecentWatchProjection;
import com.ott.infra.db.watch_history.repository.TagRankingProjection;
import com.ott.infra.db.watch_history.repository.TagViewCountProjection;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
class PlaybackPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlaybackRepository playbackRepository;

    @Autowired
    private WatchHistoryRepository watchHistoryRepository;

    @Test
    void playbackQueryReturnsLatestActiveEpisodePerSeries() {
        Member member = persistMember("playback-member");
        Series series = persistSeries(member, "series");
        Contents olderContents = persistContents(member, series, "episode-1", 300);
        Contents latestContents = persistContents(member, series, "episode-2", 320);
        Playback older = persistPlayback(member, olderContents, 15);
        Playback latest = persistPlayback(member, latestContents, 45);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE playback SET modified_date = :modifiedDate WHERE id = :id")
                .setParameter("modifiedDate", LocalDateTime.now().minusDays(1))
                .setParameter("id", older.getId())
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE playback SET modified_date = :modifiedDate WHERE id = :id")
                .setParameter("modifiedDate", LocalDateTime.now())
                .setParameter("id", latest.getId())
                .executeUpdate();
        entityManager.clear();

        List<Playback> result = playbackRepository.findLatestByMemberAndSeriesMediaIds(
                member.getId(),
                List.of(series.getMedia().getId())
        );

        assertThat(result)
                .singleElement()
                .satisfies(playback -> {
                    assertThat(playback.getId()).isEqualTo(latest.getId());
                    assertThat(playback.getPositionSec()).isEqualTo(45);
                    assertThat(playback.getContents().getMedia().getTitle()).isEqualTo("episode-2");
                });
    }

    @Test
    void watchHistoryProjectionExcludesInactiveHistoryAndKeepsPlaybackPosition() {
        Member member = persistMember("history-member");
        Contents activeContents = persistContents(member, null, "active-history", 500);
        Contents deletedContents = persistContents(member, null, "deleted-history", 600);
        persistPlayback(member, activeContents, 88);
        entityManager.persist(history(member, activeContents, Status.ACTIVE, LocalDateTime.now()));
        entityManager.persist(history(member, deletedContents, Status.DELETE, LocalDateTime.now().plusMinutes(1)));
        entityManager.flush();
        entityManager.clear();

        Page<RecentWatchProjection> result = watchHistoryRepository.findWatchHistoryByMemberId(
                member.getId(),
                PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .singleElement()
                .satisfies(projection -> {
                    assertThat(projection.getMediaId()).isEqualTo(activeContents.getMedia().getId());
                    assertThat(projection.getMediaType()).isEqualTo(MediaType.CONTENTS);
                    assertThat(projection.getPosterUrl()).isEqualTo("active-history poster");
                    assertThat(projection.getPositionSec()).isEqualTo(88);
                    assertThat(projection.getDuration()).isEqualTo(500);
                });
    }

    @Test
    void tagRankingQueriesKeepProjectionConstructorsAndDescendingCounts() {
        Member member = persistMember("ranking-member");
        Category category = Category.builder().name("genre").build();
        entityManager.persist(category);
        Tag drama = Tag.builder().category(category).name("drama").build();
        Tag comedy = Tag.builder().category(category).name("comedy").build();
        entityManager.persist(drama);
        entityManager.persist(comedy);
        Contents first = persistContents(member, null, "ranking-first", 100);
        Contents second = persistContents(member, null, "ranking-second", 100);
        entityManager.persist(MediaTag.builder().media(first.getMedia()).tag(drama).build());
        entityManager.persist(MediaTag.builder().media(second.getMedia()).tag(drama).build());
        entityManager.persist(MediaTag.builder().media(second.getMedia()).tag(comedy).build());
        LocalDateTime now = LocalDateTime.now();
        entityManager.persist(history(member, first, Status.ACTIVE, now.minusMinutes(2)));
        entityManager.persist(history(member, second, Status.ACTIVE, now.minusMinutes(1)));
        entityManager.flush();
        entityManager.clear();

        List<TagRankingProjection> ranking = watchHistoryRepository.findTopTagsByMemberIdAndWatchedBetween(
                member.getId(),
                now.minusDays(1),
                now.plusDays(1)
        );
        List<TagViewCountProjection> viewCounts = watchHistoryRepository.countByTagAndCategoryIdAndWatchedBetween(
                category.getId(),
                now.minusDays(1),
                now.plusDays(1)
        );

        assertThat(ranking)
                .extracting(TagRankingProjection::getTagName, TagRankingProjection::getCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("drama", 2L),
                        org.assertj.core.groups.Tuple.tuple("comedy", 1L)
                );
        assertThat(viewCounts)
                .extracting(TagViewCountProjection::tagName, TagViewCountProjection::viewCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("drama", 2L),
                        org.assertj.core.groups.Tuple.tuple("comedy", 1L)
                );
    }

    private Member persistMember(String providerId) {
        Member member = Member.builder()
                .email(providerId + "@example.com")
                .nickname(providerId)
                .role(Role.MEMBER)
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .build();
        entityManager.persist(member);
        return member;
    }

    private Series persistSeries(Member uploader, String title) {
        Media media = persistMedia(uploader, title, MediaType.SERIES);
        Series series = Series.builder().media(media).actors("series actor").build();
        entityManager.persist(series);
        return series;
    }

    private Contents persistContents(Member uploader, Series series, String title, int duration) {
        Media media = persistMedia(uploader, title, MediaType.CONTENTS);
        Contents contents = Contents.builder()
                .media(media)
                .series(series)
                .actors("actor")
                .duration(duration)
                .videoSize(1)
                .originUrl(title + " origin")
                .masterPlaylistUrl(title + " master")
                .build();
        entityManager.persist(contents);
        return contents;
    }

    private Media persistMedia(Member uploader, String title, MediaType mediaType) {
        Media media = Media.builder()
                .uploader(uploader)
                .title(title)
                .description(title + " description")
                .posterUrl(title + " poster")
                .thumbnailUrl(title + " thumbnail")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(mediaType)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
        entityManager.persist(media);
        return media;
    }

    private Playback persistPlayback(Member member, Contents contents, int positionSec) {
        Playback playback = Playback.builder()
                .member(member)
                .contents(contents)
                .positionSec(positionSec)
                .build();
        entityManager.persist(playback);
        return playback;
    }

    private static WatchHistory history(
            Member member,
            Contents contents,
            Status status,
            LocalDateTime lastWatchedAt
    ) {
        WatchHistory history = WatchHistory.builder()
                .member(member)
                .contents(contents)
                .lastWatchedAt(lastWatchedAt)
                .reWatchCount(0)
                .isUsedForMl(false)
                .build();
        history.updateStatus(status);
        return history;
    }
}
