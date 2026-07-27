package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.bookmark.domain.Bookmark;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.playback.domain.Playback;
import com.ott.infra.db.bookmark.repository.BookmarkMediaProjection;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.config.InfraDbConfiguration;
import jakarta.persistence.EntityManager;
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
class EngagementPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Test
    void bookmarkProjectionKeepsPlaybackPositionAndExcludesSoftDeletedRows() {
        Member member = persistMember("engagement-member");
        Contents activeContents = persistContents(member, "active-content", 420);
        Contents deletedContents = persistContents(member, "deleted-content", 240);
        entityManager.persist(Playback.builder()
                .member(member)
                .contents(activeContents)
                .positionSec(37)
                .build());
        entityManager.persist(Bookmark.builder().member(member).media(activeContents.getMedia()).build());
        Bookmark deletedBookmark = Bookmark.builder().member(member).media(deletedContents.getMedia()).build();
        deletedBookmark.updateStatus(Status.DELETE);
        entityManager.persist(deletedBookmark);
        entityManager.flush();
        entityManager.clear();

        Page<BookmarkMediaProjection> result = bookmarkRepository.findBookmarkMediaList(
                member.getId(),
                PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .singleElement()
                .satisfies(projection -> {
                    assertThat(projection.getMediaId()).isEqualTo(activeContents.getMedia().getId());
                    assertThat(projection.getMediaType()).isEqualTo(MediaType.CONTENTS);
                    assertThat(projection.getTitle()).isEqualTo("active-content");
                    assertThat(projection.getPositionSec()).isEqualTo(37);
                    assertThat(projection.getDuration()).isEqualTo(420);
                });
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

    private Contents persistContents(Member uploader, String title, int duration) {
        Media media = Media.builder()
                .uploader(uploader)
                .title(title)
                .description(title + " description")
                .posterUrl(title + " poster")
                .thumbnailUrl(title + " thumbnail")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.CONTENTS)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
        entityManager.persist(media);
        Contents contents = Contents.builder()
                .media(media)
                .actors("actor")
                .duration(duration)
                .videoSize(1)
                .originUrl(title + " origin")
                .masterPlaylistUrl(title + " master")
                .build();
        entityManager.persist(contents);
        return contents;
    }
}
