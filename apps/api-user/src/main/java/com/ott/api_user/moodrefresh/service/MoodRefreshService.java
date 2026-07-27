package com.ott.api_user.moodrefresh.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ott.api_user.ai.client.AiClient;
import com.ott.api_user.ai.service.GeminiService;
import com.ott.api_user.moodrefresh.dto.response.MoodRefreshResponse;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media_mood_tag.domain.MediaMoodTag;
import com.ott.infra.db.media_mood_tag.repository.MediaMoodTagRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.domain.mood_tag.domain.MoodTag;
import com.ott.infra.db.mood_tag.repository.MoodTagRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.moodrefresh.domain.MemberMoodRefresh;
import com.ott.infra.db.moodrefresh.repository.MemberMoodRefreshRepository;
import com.ott.domain.watch_history.domain.WatchHistory;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class MoodRefreshService {
    private final MemberMoodRefreshRepository refreshRepository;
    private final MediaRepository mediaRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final AiClient aiClient;
    private final MediaMoodTagRepository mediaMoodTagRepository;
    private final MoodTagRepository moodTagRepository;
    private final MemberRepository memberRepository;
    private final GeminiService geminiService;

    // 홈 화면에 노출시킬 카드
    @Transactional(readOnly = true)
    public MoodRefreshResponse getActiveRefreshCard(Long memberId) {
    return refreshRepository.findTopByMemberIdAndIsHiddenFalseOrderByCreatedDateDesc(memberId)
            .map(activeCard -> {
                List<Media> mediaList = mediaRepository.findAllById(activeCard.getRecommendedMediaIds());
                return MoodRefreshResponse.of(activeCard, mediaList);
            })
            .orElse(null); // 에러를 던지지 않고 그냥 null을 반환합니다.
    }

    // 카드 숨김 요청 시
    @Transactional
    public void hideRefreshCard(Long memberId, Long refreshId) {
        MemberMoodRefresh card = refreshRepository.findById(refreshId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_CARD_NOT_FOUND));

                if (!card.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED); // 접근 권한 에러 던지기
        }

        card.hideCard();
    }


    // 이벤트리스너 시작 시 - 메인 로직
    @Transactional
    public void analyzeAndCreateRefreshCard(Long memberId) {

        log.info("🚨 [테스트] analyzeAndCreateRefreshCard 함수에 확실히 진입했습니다! memberId: {}", memberId);

        // 해당 멤버의 쿨다운 확인
        if (isInCooldown(memberId)) {
            log.info("[Mood Refresh] 쿨다운(6시간) 통과 실패 - memberId: {}", memberId);
            return;
        }

        LocalDateTime seventyTwoHoursAgo = LocalDateTime.now().minusHours(72);

        // 최근 72시간 내 가장 최근 3개 시청 기록 가져오기
        List<WatchHistory> recentHistories = watchHistoryRepository
                .findRecentUnusedHistoriesWithin(memberId, seventyTwoHoursAgo, 3);

        if (recentHistories.size() < 3) {
            log.info("[Mood Refresh] 최근 72시간 이력 부족 - memberId: {}, records={}", memberId, recentHistories.size());
            return;
        }


        // 사용자의 현재 감정 조회 -> 3일 연속 일치 시 해당 대표 카테고리 노출
        Long currentMoodCategoryId = getCommonMoodCategoryId(recentHistories);
        if (currentMoodCategoryId == null) {
            log.info("[Mood Refresh] 대표 감정 그룹 불일치 - memberId: {}", memberId);
            return;
        }


        List<String> inputTags = extractTagsFromHistories(recentHistories);
        log.info("[Mood Refresh] Gate 통과 - memberId: {}, inputTags={}", memberId, inputTags);

        // 감정 이미지 저장
        Byte themeImageId = currentMoodCategoryId.byteValue();


        // AI 호출
        List<String> targetTags = aiClient.getTargetTags(memberId, inputTags);
        if (targetTags == null || targetTags.isEmpty()) {
            log.warn("[Mood Refresh] AI 타겟 태그 응답이 없어 카드를 생성하지 않습니다. memberId: {}", memberId);
            return;
        }

        String topTargetTag = targetTags.get(0);
        List<Media> recommendedMedias = mediaRepository.findByTop3ByMoodTagName(topTargetTag);


        if (recommendedMedias.size() < 3) {
            log.info("[Mood Refresh] 타겟 태그({})에 해당하는 활성 영상이 3개 미만입니다. 카드 생성 취소", topTargetTag);
            return;
        }

        List<Long> recommendedMediaIds = recommendedMedias.stream()
                .map(Media::getId)
                .toList();

        String userMoodStr = String.join(",", inputTags);

        // 2. Gemini 호출! (유저 기분 문자열과 AI가 추천해준 타겟 태그 리스트 전달)
        String llmSubtitle = geminiService.generateHealingMessage(userMoodStr, targetTags);

        log.info("[Mood Refresh] Gemini 멘트 생성 완료: {}", llmSubtitle);

        String combinedContent = llmSubtitle + "|" + String.join(",", targetTags);

        MemberMoodRefresh newCard = MemberMoodRefresh.builder()
                .member(memberRepository.getReferenceById(memberId)) // 프록시 객체로 조회 쿼리 최적화
                .imageId(themeImageId)
                .subtitle(combinedContent) // 합친 문자열을 저장
                .recommendedMediaIds(recommendedMediaIds)
                .build();

        refreshRepository.save(newCard);

        recentHistories.forEach(WatchHistory::markAsUsedForMl);

        log.info("[Mood Refresh] 유저 {}을 위한 분위기 환기 카드 생성 완료! (타겟 태그: {})", memberId, topTargetTag);


    }


    // 위 메인 로직에 필요한 필터링 메서드

    // 1차 필터링: 쿨타임 확인용
    private boolean isInCooldown(Long memberId) {
        LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
        return refreshRepository.existsByMemberIdAndCreatedDateAfter(memberId, sixHoursAgo);
    }

    // 2차 필터링: 최근 시청 이력 (72시간 내)의 감정 태그의 대표 카테고리가 같은지 확인하고, 같다면 카테고리 ID 반환
    private Long getCommonMoodCategoryId(List<WatchHistory> histories) {
        List<Long> mediaIds = histories.stream()
                .map(WatchHistory::getContents)
                .filter(Objects::nonNull)      // NPE 방어
                .map(Contents::getMedia)
                .filter(Objects::nonNull)      // NPE 방어
                .map(Media::getId)
                .distinct()
                .toList();
            if (mediaIds.isEmpty()) return null;

        // 1순위(priority = 1) 태그들 한 번에 IN 절로 조회
        List<MediaMoodTag> primaryTags = mediaMoodTagRepository
                .findByMedia_IdInAndStatusAndPriorityOrderByMedia_IdAscPriorityAsc(mediaIds, Status.ACTIVE, 1);

        // 태그가 누락된 영상이 하나라도 있다면 환기 불가로 간주
        if (primaryTags.size() < mediaIds.size()) {
            log.info("🚨 [Mood Refresh]: 해당 영상에는 태그가 없어 누락으로 간주합니다.미디어 IDs: {}", mediaIds);
            return null;
        }

        // Stream을 이용해 대분류 카테고리 ID만 추출하고 중복을 제거
        List<Long> distinctCategoryIds = primaryTags.stream()
                .map(tag -> tag.getMoodTag().getMoodCategory().getId()) // 카테고리 ID 추출
                .distinct() // 중복 제거
                .toList();

        log.info("🚨 [Mood Refresh Debug] 미디어 IDs: {}, 추출된 카테고리 IDs: {}", mediaIds, distinctCategoryIds);

        // 1개면 모두 같은 감정 그룹! 해당 카테고리 ID를 반환
        if (distinctCategoryIds.size() == 1) {
            return distinctCategoryIds.get(0);
        }


        return null;
    }

    // 3차 필터링: 3개의 시청 이력에서 각 영상당 상위 3개의 감정 태그를 추출하여 중복 없이 병합
    private List<String> extractTagsFromHistories(List<WatchHistory> histories) {
        List<Long> mediaIds = histories.stream()
                .map(WatchHistory::getContents)
                .filter(Objects::nonNull)      // NPE 방어
                .map(Contents::getMedia)
                .filter(Objects::nonNull)      // NPE 방어
                .map(Media::getId)
                .distinct()
                .toList();

        if (mediaIds.isEmpty()) return List.of();

        // 3개 영상에 달린 모든 활성 태그 조회
        List<MediaMoodTag> allMoodTags = mediaMoodTagRepository
                .findByMedia_IdInAndStatusOrderByMedia_IdAscPriorityAsc(mediaIds, Status.ACTIVE);

        return allMoodTags.stream()
                // 1. 미디어 ID별로 그룹화 하면서 태그 이름만 리스트로 묶음
                .collect(Collectors.groupingBy(
                        tag -> tag.getMedia().getId(),
                        Collectors.mapping(tag -> tag.getMoodTag().getName(), Collectors.toList())
                ))
                .values().stream()
                // 2. 각 영상당 상위 3개의 태그만 자름 (limit 3)
                .flatMap(tags -> tags.stream().limit(3))
                .filter(StringUtils::hasText) // 3. 빈 문자열 방어
                .distinct() // 4. 최종적으로 남은 태그들의 중복 제거
                .toList();
    }

}
