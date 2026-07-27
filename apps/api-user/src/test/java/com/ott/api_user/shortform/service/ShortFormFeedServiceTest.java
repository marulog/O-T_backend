package com.ott.api_user.shortform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.playlist.service.PlaylistPreferenceService;
import com.ott.api_user.shortform.dto.response.ShortFormFeedResponse;
import com.ott.common.core.response.PageResult;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.domain.common.MediaType;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.domain.short_form.domain.ShortForm.ShortFormBuilder;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import com.ott.domain.series.domain.Series;
import com.ott.domain.series.domain.Series.SeriesBuilder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortFormFeedServiceTest {

    @Mock
    private ShortFormRepository shortFormRepository;

    @Mock
    private LikesRepository likesRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private PlaylistPreferenceService playlistPreferenceService;

    @InjectMocks
    private ShortFormFeedService shortFormFeedService;

    @Test
    void getShortFormFeed_combinesRecommendAndLatestAndMarksInteractions() {
        Long memberId = 5L;

        // 추천 태그 점수 맵을 먼저 반환
        when(playlistPreferenceService.getTotalTagScores(memberId)).thenReturn(Map.of(1L, 10));

        // 추천 숏폼 2개 준비
        ShortForm recommended = createShortForm(101L, 1001L, "추천", true);
        ShortForm another = createShortForm(102L, 1002L, "추천2", true);
        List<Long> recommendedMediaIds = List.of(
                recommended.getMedia().getId(),
                another.getMedia().getId()
        );

        when(shortFormRepository.findRecommendedShortForms(eq(Map.of(1L, 10)), eq(3), eq(0L)))
                .thenReturn(List.of(recommended, another));

        // 최신 숏폼(추천 ID 제외) 준비
        ShortForm latest = createShortForm(103L, 1003L, "최신", false);
        when(shortFormRepository.findLatestShortForms(eq(2), eq(0L), eq(recommendedMediaIds)))
                .thenReturn(List.of(latest));

        // 좋아요/북마크 상태 설정
        when(likesRepository.findLikedMediaIds(eq(memberId), any())).thenReturn(Set.of(recommended.getMedia().getId()));
        when(bookmarkRepository.findBookmarkedMediaIds(eq(memberId), any())).thenReturn(Set.of(latest.getMedia().getId()));

        // 서비스가 추천 + 최신 숏폼을 합쳐 DTO로 변환하는지 검사
        PageResult<ShortFormFeedResponse> response = shortFormFeedService.getShortFormFeed(memberId, 0, 5);

        // 페이지 정보와 노출 아이템 개수 검증
        assertThat(response.getPageMetadata().getCurrentPage()).isZero();
        assertThat(response.getDataList()).hasSize(3);

        Set<Long> ids = response.getDataList().stream()
                .map(ShortFormFeedResponse::getShortFormId)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(ids).containsExactlyInAnyOrder(
                recommended.getMedia().getId(),
                another.getMedia().getId(),
                latest.getMedia().getId()
        );
        assertThat(response.getDataList().stream()
                .filter(dto -> dto.getShortFormId().equals(recommended.getMedia().getId()))
                .findFirst()
                .orElseThrow()
                .getIsLiked()).isTrue();
        assertThat(response.getDataList().stream()
                .filter(dto -> dto.getShortFormId().equals(latest.getMedia().getId()))
                .findFirst()
                .orElseThrow()
                .getIsBookmarked()).isTrue();
    }

    @Test
    void getShortFormFeed_deduplicatesSameMediaIds() {
        Long memberId = 6L;
        when(playlistPreferenceService.getTotalTagScores(memberId)).thenReturn(Map.of(2L, 5));

        ShortForm recommend = createShortForm(201L, 2001L, "중복추천", true);
        when(shortFormRepository.findRecommendedShortForms(eq(Map.of(2L, 5)), eq(3), eq(0L)))
                .thenReturn(List.of(recommend));

        ShortForm latestWithSame = createShortForm(201L, 2001L, "중복최신", false);
        List<Long> recommendationIds = List.of(recommend.getMedia().getId());
        when(shortFormRepository.findLatestShortForms(eq(2), eq(0L), eq(recommendationIds)))
                .thenReturn(List.of(latestWithSame));

        ArgumentCaptor<List<Long>> likedCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Long>> bookmarkedCaptor = ArgumentCaptor.forClass(List.class);

        when(likesRepository.findLikedMediaIds(eq(memberId), any())).thenReturn(Set.of());
        when(bookmarkRepository.findBookmarkedMediaIds(eq(memberId), any())).thenReturn(Set.of());

        PageResult<ShortFormFeedResponse> response = shortFormFeedService.getShortFormFeed(memberId, 0, 5);
        assertThat(response.getDataList()).hasSize(2);
        assertThat(response.getDataList()).allMatch(dto -> dto.getShortFormId().equals(recommend.getMedia().getId()));

        verify(likesRepository).findLikedMediaIds(eq(memberId), likedCaptor.capture());
        verify(bookmarkRepository).findBookmarkedMediaIds(eq(memberId), bookmarkedCaptor.capture());
        assertThat(likedCaptor.getValue()).containsExactlyInAnyOrder(recommend.getMedia().getId());
        assertThat(bookmarkedCaptor.getValue()).containsExactlyInAnyOrder(recommend.getMedia().getId());
    }

    // 테스트용 ShortForm 엔티티를 간편하게 만드는 헬퍼 (origin media + uploader 포함)
    private static ShortForm createShortForm(Long shortFormId, Long originMediaId, String title, boolean recommend) {
        Member uploader = Member.builder()
                .id(1L)
                .email("m@" + shortFormId)
                .nickname("editor")
                .provider(Provider.KAKAO)
                .role(Role.MEMBER)
                .build();

        Media shortFormMedia = Media.builder()
                .id(shortFormId)
                .uploader(uploader)
                .title(title)
                .description("desc")
                .posterUrl("poster")
                .thumbnailUrl("thumb")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.CONTENTS)
                .publicStatus(com.ott.domain.common.PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();

        Media originMedia = Media.builder()
                .id(originMediaId)
                .uploader(uploader)
                .title("origin")
                .description("origin-desc")
                .posterUrl("origin-poster")
                .thumbnailUrl("origin-thumb")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.SERIES)
                .publicStatus(com.ott.domain.common.PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();

        Series series = Series.builder()
                .media(originMedia)
                .actors("actors")
                .build();

        ShortFormBuilder builder = ShortForm.builder()
                .media(shortFormMedia)
                .series(series)
                .duration(30)
                .videoSize(1)
                .originUrl("http://origin")
                .masterPlaylistUrl("http://short");

        if (!recommend) {
            builder.videoSize(2);
        }

        return builder.build();
    }
}
