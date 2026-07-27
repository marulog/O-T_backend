package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.series.domain.Series;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.series.repository.SeriesRepository;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import jakarta.persistence.EntityManager;
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
class MediaCatalogPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ContentsRepository contentsRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private ShortFormRepository shortFormRepository;

    @Test
    void customRepositoriesPreserveCatalogQueriesAndVisibilityFilters() {
        Member uploader = memberRepository.saveAndFlush(member("catalog-uploader"));

        Media seriesMedia = mediaRepository.saveAndFlush(
                media(uploader, "Public Series", MediaType.SERIES, PublicStatus.PUBLIC, MediaStatus.COMPLETED));
        Series series = seriesRepository.saveAndFlush(Series.builder()
                .media(seriesMedia)
                .actors("Series Actor")
                .build());

        Media contentsMedia = mediaRepository.saveAndFlush(
                media(uploader, "Public Feature", MediaType.CONTENTS, PublicStatus.PUBLIC, MediaStatus.COMPLETED));
        Contents contents = contentsRepository.saveAndFlush(contents(contentsMedia));

        Media shortFormMedia = mediaRepository.saveAndFlush(
                media(uploader, "Public Short", MediaType.SHORT_FORM, PublicStatus.PUBLIC, MediaStatus.COMPLETED));
        ShortForm shortForm = shortFormRepository.saveAndFlush(shortForm(shortFormMedia, contents));

        Media privateMedia = mediaRepository.saveAndFlush(
                media(uploader, "Private Feature", MediaType.CONTENTS, PublicStatus.PRIVATE, MediaStatus.COMPLETED));
        contentsRepository.saveAndFlush(contents(privateMedia));

        Media incompleteMedia = mediaRepository.saveAndFlush(
                media(uploader, "Incomplete Feature", MediaType.CONTENTS, PublicStatus.PUBLIC, MediaStatus.INIT));
        Contents incompleteContents = contentsRepository.saveAndFlush(contents(incompleteMedia));

        Media incompleteShortMedia = mediaRepository.saveAndFlush(
                media(uploader, "Incomplete Short", MediaType.SHORT_FORM, PublicStatus.PUBLIC, MediaStatus.INIT));
        shortFormRepository.saveAndFlush(shortForm(incompleteShortMedia, incompleteContents));
        entityManager.clear();

        Page<Media> trending = mediaRepository.findTrendingPlaylists(
                MediaType.CONTENTS, null, PageRequest.of(0, 20));
        Contents loadedContents = contentsRepository.findWithMediaById(contents.getId()).orElseThrow();
        Page<Series> loadedSeries = seriesRepository.findSeriesListWithMediaBySearchWord(
                PageRequest.of(0, 20), "Public Series");
        List<ShortForm> latestShortForms = shortFormRepository.findLatestShortForms(20, 0, List.of());

        assertThat(trending.getContent())
                .extracting(Media::getTitle)
                .containsExactly("Public Feature");
        assertThat(loadedContents.getMedia().getId()).isEqualTo(contentsMedia.getId());
        assertThat(loadedSeries.getContent())
                .extracting(item -> item.getMedia().getId())
                .containsExactly(seriesMedia.getId());
        assertThat(latestShortForms)
                .extracting(ShortForm::getId)
                .containsExactly(shortForm.getId());
        assertThat(contentsRepository.findWithMediaById(incompleteContents.getId())).isEmpty();
    }

    private static Member member(String providerId) {
        return Member.builder()
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .email(providerId + "@example.com")
                .nickname(providerId)
                .role(Role.EDITOR)
                .build();
    }

    private static Media media(Member uploader, String title, MediaType mediaType,
            PublicStatus publicStatus, MediaStatus mediaStatus) {
        return Media.builder()
                .uploader(uploader)
                .title(title)
                .description("Media catalog integration fixture")
                .posterUrl("https://cdn.example.com/poster.jpg")
                .thumbnailUrl("https://cdn.example.com/thumbnail.jpg")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(mediaType)
                .publicStatus(publicStatus)
                .mediaStatus(mediaStatus)
                .build();
    }

    private static Contents contents(Media media) {
        return Contents.builder()
                .media(media)
                .actors("Feature Actor")
                .duration(120)
                .videoSize(1_024)
                .originUrl("s3://catalog/origin.mp4")
                .masterPlaylistUrl("s3://catalog/master.m3u8")
                .build();
    }

    private static ShortForm shortForm(Media media, Contents contents) {
        return ShortForm.builder()
                .media(media)
                .contents(contents)
                .duration(30)
                .videoSize(256)
                .originUrl("s3://catalog/short.mp4")
                .masterPlaylistUrl("s3://catalog/short.m3u8")
                .build();
    }
}
