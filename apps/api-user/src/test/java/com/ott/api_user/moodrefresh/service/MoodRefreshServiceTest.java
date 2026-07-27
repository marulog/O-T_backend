package com.ott.api_user.moodrefresh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.ai.client.AiClient;
import com.ott.api_user.ai.service.GeminiService;
import com.ott.api_user.moodrefresh.dto.response.MoodRefreshResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.Status;
import com.ott.domain.common.MediaType;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.media_mood_tag.domain.MediaMoodTag;
import com.ott.infra.db.media_mood_tag.repository.MediaMoodTagRepository;
import com.ott.domain.member.domain.Member;
import com.ott.domain.mood_category.domain.MoodCategory;
import com.ott.domain.mood_tag.domain.MoodTag;
import com.ott.domain.moodrefresh.domain.MemberMoodRefresh;
import com.ott.infra.db.moodrefresh.repository.MemberMoodRefreshRepository;
import com.ott.domain.watch_history.domain.WatchHistory;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MoodRefreshServiceTest {

    @Mock
    private MemberMoodRefreshRepository refreshRepository;

    @Mock
    private com.ott.infra.db.media.repository.MediaRepository mediaRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private MediaMoodTagRepository mediaMoodTagRepository;

    @Mock
    private com.ott.infra.db.mood_tag.repository.MoodTagRepository moodTagRepository;

    @Mock
    private com.ott.infra.db.member.repository.MemberRepository memberRepository;

    @Mock
    private GeminiService geminiService;

    @InjectMocks
    private MoodRefreshService moodRefreshService;

    @Test
    void getActiveRefreshCard_parsesSubtitleAndTags() {
        Long memberId = 3L;
        MemberMoodRefresh refresh = MemberMoodRefresh.builder()
                .member(createMember(memberId))
                .imageId((byte) 1)
                .subtitle("heal|calm,bright")
                .recommendedMediaIds(List.of(11L, 12L))
                .build();

        when(refreshRepository.findTopByMemberIdAndIsHiddenFalseOrderByCreatedDateDesc(memberId))
                .thenReturn(Optional.of(reflectRefreshId(refresh, 99L)));

        Media first = createMedia(11L);
        Media second = createMedia(12L);
        when(mediaRepository.findAllById(refresh.getRecommendedMediaIds()))
                .thenReturn(List.of(first, second));

        MoodRefreshResponse response = moodRefreshService.getActiveRefreshCard(memberId);

        assertThat(response).isNotNull();
        assertThat(response.getSubtitle()).isEqualTo("heal");
        assertThat(response.getTags()).containsExactly("calm", "bright");
        assertThat(response.getRecommendedMediaList()).hasSize(2);
    }

    @Test
    void hideRefreshCard_checksOwnership() {
        Long refreshId = 888L;
        MemberMoodRefresh card = MemberMoodRefresh.builder()
                .member(createMember(1L))
                .imageId((byte) 2)
                .subtitle("hi")
                .recommendedMediaIds(List.of(1L))
                .build();
        when(refreshRepository.findById(refreshId)).thenReturn(Optional.of(card));

        moodRefreshService.hideRefreshCard(1L, refreshId);

        assertThat(card.isHidden()).isTrue();
    }

    @Test
    void hideRefreshCard_throwsWhenNotOwner() {
        Long refreshId = 777L;
        MemberMoodRefresh card = MemberMoodRefresh.builder()
                .member(createMember(5L))
                .imageId((byte) 2)
                .subtitle("hi")
                .recommendedMediaIds(List.of(1L))
                .build();
        when(refreshRepository.findById(refreshId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> moodRefreshService.hideRefreshCard(99L, refreshId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void getCommonMoodCategoryId_returnsCategoryWhenAllSame() {
        List<WatchHistory> histories = List.of(
                createWatchHistory(21L, 101L),
                createWatchHistory(22L, 102L)
        );
        MoodCategory category = MoodCategory.builder().id(77L).name("calm").build();
        MoodTag tag = MoodTag.builder().id(3L).name("sunny").moodCategory(category).build();

        Media media1 = createMedia(101L);
        Media media2 = createMedia(102L);

        when(mediaMoodTagRepository.findByMedia_IdInAndStatusAndPriorityOrderByMedia_IdAscPriorityAsc(
                        List.of(101L, 102L), Status.ACTIVE, 1))
                .thenReturn(List.of(
                        MediaMoodTag.builder().media(media1).moodTag(tag).priority(1).build(),
                        MediaMoodTag.builder().media(media2).moodTag(tag).priority(1).build()
                ));

        Long result = ReflectionTestUtils.invokeMethod(moodRefreshService, "getCommonMoodCategoryId", histories);
        assertThat(result).isEqualTo(category.getId());
    }

    @Test
    void extractTagsFromHistories_dropsDuplicatesAndLimitsPerMedia() {
        List<WatchHistory> histories = List.of(
                createWatchHistory(31L, 201L),
                createWatchHistory(32L, 202L)
        );

        Media media1 = createMedia(201L);
        Media media2 = createMedia(202L);
        MoodCategory cat = MoodCategory.builder().id(88L).name("mood").build();

        when(mediaMoodTagRepository.findByMedia_IdInAndStatusOrderByMedia_IdAscPriorityAsc(List.of(201L, 202L), Status.ACTIVE))
                .thenReturn(List.of(
                        MediaMoodTag.builder().media(media1).moodTag(buildTag(cat, "one")).priority(1).build(),
                        MediaMoodTag.builder().media(media1).moodTag(buildTag(cat, "two")).priority(2).build(),
                        MediaMoodTag.builder().media(media1).moodTag(buildTag(cat, "two")).priority(3).build(),
                        MediaMoodTag.builder().media(media2).moodTag(buildTag(cat, "three")).priority(1).build(),
                        MediaMoodTag.builder().media(media2).moodTag(buildTag(cat, "")).priority(2).build()
                ));

        List<String> tags = ReflectionTestUtils.invokeMethod(moodRefreshService, "extractTagsFromHistories", histories);
        assertThat(tags).containsExactly("one", "two", "three");
    }

    @Test
    void analyzeAndCreateRefreshCard_shortCircuitsWhenCooldown() {
        Long memberId = 50L;
        when(refreshRepository.existsByMemberIdAndCreatedDateAfter(eq(memberId), any(LocalDateTime.class)))
                .thenReturn(true);

        moodRefreshService.analyzeAndCreateRefreshCard(memberId);

        verify(watchHistoryRepository, never()).findRecentUnusedHistoriesWithin(eq(memberId), any(LocalDateTime.class), eq(3));
        verify(refreshRepository, never()).save(any(MemberMoodRefresh.class));
    }

    @Test
    void analyzeAndCreateRefreshCard_returnsWhenHistoriesTooFew() {
        Long memberId = 51L;
        when(refreshRepository.existsByMemberIdAndCreatedDateAfter(eq(memberId), any(LocalDateTime.class)))
                .thenReturn(false);
        when(watchHistoryRepository.findRecentUnusedHistoriesWithin(eq(memberId), any(LocalDateTime.class), eq(3)))
                .thenReturn(List.of(createWatchHistory(1L, 900L), createWatchHistory(2L, 901L)));

        moodRefreshService.analyzeAndCreateRefreshCard(memberId);

        verify(refreshRepository, never()).save(any(MemberMoodRefresh.class));
    }

    @Test
    void analyzeAndCreateRefreshCard_returnsWhenCategoryGateFails() {
        Long memberId = 52L;
        when(refreshRepository.existsByMemberIdAndCreatedDateAfter(eq(memberId), any(LocalDateTime.class)))
                .thenReturn(false);

        List<WatchHistory> histories = List.of(
                createWatchHistory(10L, 1001L),
                createWatchHistory(11L, 1002L),
                createWatchHistory(12L, 1003L)
        );

        when(watchHistoryRepository.findRecentUnusedHistoriesWithin(eq(memberId), any(LocalDateTime.class), eq(3)))
                .thenReturn(histories);

        when(mediaMoodTagRepository.findByMedia_IdInAndStatusAndPriorityOrderByMedia_IdAscPriorityAsc(
                        List.of(1001L, 1002L, 1003L), Status.ACTIVE, 1))
                .thenReturn(List.of(MediaMoodTag.builder().media(createMedia(1001L)).moodTag(buildTag(MoodCategory.builder().id(1L).name("x").build(), "a")).priority(1).build()));

        moodRefreshService.analyzeAndCreateRefreshCard(memberId);

        verify(refreshRepository, never()).save(any(MemberMoodRefresh.class));
    }

    @Test
    void analyzeAndCreateRefreshCard_createsCardWhenAllGatesPass() {
        Long memberId = 60L;
        when(refreshRepository.existsByMemberIdAndCreatedDateAfter(eq(memberId), any(LocalDateTime.class)))
                .thenReturn(false);

        List<WatchHistory> histories = List.of(
                createWatchHistory(21L, 1101L),
                createWatchHistory(22L, 1102L),
                createWatchHistory(23L, 1103L)
        );

        when(watchHistoryRepository.findRecentUnusedHistoriesWithin(eq(memberId), any(LocalDateTime.class), eq(3)))
                .thenReturn(histories);

        MoodCategory category = MoodCategory.builder().id(9L).name("joy").build();
        MoodTag tag = MoodTag.builder().id(10L).name("bright").moodCategory(category).build();
        when(mediaMoodTagRepository.findByMedia_IdInAndStatusAndPriorityOrderByMedia_IdAscPriorityAsc(
                        List.of(1101L, 1102L, 1103L), Status.ACTIVE, 1))
                .thenReturn(List.of(
                        MediaMoodTag.builder().media(createMedia(1101L)).moodTag(tag).priority(1).build(),
                        MediaMoodTag.builder().media(createMedia(1102L)).moodTag(tag).priority(1).build(),
                        MediaMoodTag.builder().media(createMedia(1103L)).moodTag(tag).priority(1).build()
                ));

        MoodCategory cat2 = MoodCategory.builder().id(11L).name("mood").build();
        when(mediaMoodTagRepository.findByMedia_IdInAndStatusOrderByMedia_IdAscPriorityAsc(List.of(1101L, 1102L, 1103L), Status.ACTIVE))
                .thenReturn(List.of(
                        MediaMoodTag.builder().media(createMedia(1101L)).moodTag(buildTag(cat2, "one")).priority(1).build(),
                        MediaMoodTag.builder().media(createMedia(1102L)).moodTag(buildTag(cat2, "two")).priority(1).build(),
                        MediaMoodTag.builder().media(createMedia(1103L)).moodTag(buildTag(cat2, "three")).priority(1).build()
                ));

        List<String> targetTags = List.of("bright", "calm");
        when(aiClient.getTargetTags(eq(memberId), any())).thenReturn(targetTags);
        List<Media> recommendedMedia = List.of(createMedia(2101L), createMedia(2102L), createMedia(2103L));
        when(mediaRepository.findByTop3ByMoodTagName("bright")).thenReturn(recommendedMedia);
        when(geminiService.generateHealingMessage(any(String.class), eq(targetTags))).thenReturn("message");
        Member member = createMember(memberId);
        when(memberRepository.getReferenceById(memberId)).thenReturn(member);
        when(refreshRepository.save(any(MemberMoodRefresh.class))).thenAnswer(invocation -> invocation.getArgument(0));

        moodRefreshService.analyzeAndCreateRefreshCard(memberId);

        ArgumentCaptor<MemberMoodRefresh> captor = ArgumentCaptor.forClass(MemberMoodRefresh.class);
        verify(refreshRepository).save(captor.capture());
        MemberMoodRefresh saved = captor.getValue();
        assertThat(saved.getRecommendedMediaIds()).containsExactly(2101L, 2102L, 2103L);
        assertThat(saved.getSubtitle()).isEqualTo("message|" + String.join(",", targetTags));
    }

    private static Member createMember(Long id) {
        return Member.builder()
                .id(id)
                .email("member-" + id + "@ott")
                .nickname("member-" + id)
                .provider(com.ott.domain.member.domain.Provider.KAKAO)
                .role(com.ott.domain.member.domain.Role.MEMBER)
                .build();
    }

    private static Media createMedia(Long id) {
        return Media.builder()
                .id(id)
                .uploader(createMember(999L))
                .title("title-" + id)
                .description("desc")
                .posterUrl("poster")
                .thumbnailUrl("thumb")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.CONTENTS)
                .publicStatus(com.ott.domain.common.PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
    }

    private static WatchHistory createWatchHistory(Long historyId, Long mediaId) {
        ContentsHolder holder = new ContentsHolder(createMedia(mediaId));
        return WatchHistory.builder()
                .id(historyId)
                .member(createMember(99L))
                .contents(holder.contents)
                .reWatchCount(0)
                .isUsedForMl(false)
                .build();
    }

    private static MoodTag buildTag(MoodCategory category, String name) {
        return MoodTag.builder().moodCategory(category).name(name).build();
    }

    private static MemberMoodRefresh reflectRefreshId(MemberMoodRefresh source, Long id) {
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }

    // 가짜 Contents 유틸 클래스(Builder에 Contents 의존성 제거 목적)
    private static final class ContentsHolder {
        private final com.ott.domain.contents.domain.Contents contents;

        private ContentsHolder(Media media) {
            this.contents = com.ott.domain.contents.domain.Contents.builder()
                    .id(media.getId())
                    .media(media)
                    .actors("actor")
                    .duration(100)
                    .videoSize(1)
                    .originUrl("origin")
                    .masterPlaylistUrl("master")
                    .build();
        }
    }
}
