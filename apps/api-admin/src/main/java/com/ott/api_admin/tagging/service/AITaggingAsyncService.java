package com.ott.api_admin.tagging.service;

import com.ott.api_admin.ai.client.AiClient;
import com.ott.api_admin.tagging.event.AiTaggingRequestedEvent;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.media_mood_tag.domain.MediaMoodTag;
import com.ott.domain.mood_tag.domain.MoodTag;
import com.ott.infra.db.mood_tag.repository.MoodTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AITaggingAsyncService {

    private final AiClient aiClient;
    private final MediaRepository mediaRepository;
    private final MoodTagRepository moodTagRepository;
    private final MediaMoodTagAppend mediaMoodTagAppend;

    /**
     * TransactionPhase : 원본 트랜잭션의 어느 시점에 해당 이벤트를 실행시킬 것인가를 결정
     * 이벤트를 발행한 쪽의 트랜잭션 상태에 따라 해당 리스너가 달린 함수의 호출 시점이 결정됨
     * AFTER_COMMIT - 원본 트랜잭션이 커밋된 이후 해당 함수 실행
     * BEFORE_COMMIT - 커밋 직전에 실행되어, 원본 트랜잭션 안에서 같이 묶이고 싶을 때 사용
     * AFTER_ROLLBACK - 원본 트랜잭션이 롤백된 이후 실행
     * AFTER_COMPLETION - 커밋이든 롤백이는 원본 트랜잭션이 끝나면 실행 -> finally 느낌임
     *
     * ==========================================
     * Propagation : 트랜잭션을 어떻게 만들거나 참여할 것인가를 결정
     * 트랜잭션 전파 레벨이라고 부르며, 메소드 A가 메소드 B를 호출할 경우, B가 A의 트랜잭션에 참여할지, 새로 만들지 등을 결정
     * REQUIRED(Default) - 기존 트랜잭션이 있으면 참여하고, 없으면 새로 만들어라
     * REQUIRED_NEW - 기존 트랜잭션이 있으며녀 멈추고, 새로운 트랜잭션을 만들어서 먼저 커밋/롤백 시켜라
     * SUPPORTS - 기존 트랜잭션이 있으면 참여하고, 없으면 트랜잭션 없이 실행해라 -> 읽이 전용 조회일 경우 사용함
     * NOT_SUPPORTED - 트랜잭션 없이 실행하지만, 기존 트랜잭션이 있으면 기존 트랜잭션을 멈추고 수행
     * MANDATORY - 기존 트랜잭션이 없으면 예외처리
     * NEVER - 기존 트랜잭션이 있으면 예외처리
     * NESTED - 잘 안씀
     */
    @Async // 별도 백그라운드 스레드로 진행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAiTagging(AiTaggingRequestedEvent event) {

        log.info("[AI Tagging] 미디어 ID: {} 백그라운드로 태깅 분류 시작", event.mediaId());

        List<String> aiTags;
        try {
            // ML에서 추론된 태그 리스트 -> 순서가 보장됨
            aiTags = aiClient.getEmotionTags(event.mediaId(), event.description());

        } catch (Exception e) {
            log.error("[AI Tagging] mediaId={} AI 호출 실패", event.mediaId(), e);
            return;
        }


        if (aiTags == null || aiTags.isEmpty()) {
                log.info("[AI Tagging] 미디어 ID: {} - AI가 반환한 태그가 없습니다.", event.mediaId());
                return;
            }

            Media media = mediaRepository.findById(event.mediaId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));

            // DB단에서 매핑된 리스트 -> 순서 보장 x
            List<MoodTag> foundMoodTags = moodTagRepository.findByNameInAndStatus(aiTags, Status.ACTIVE);

            // AI 태깅 리스트 <-> DB 리스트 일치
            Map<String, MoodTag> moodTagByName = foundMoodTags.stream()
                    .collect(Collectors.toMap(
                            MoodTag::getName,       // 키: 태그 이름
                            Function.identity(),    // 값: MoodTag 엔티티 자체
                            (left, right) -> left,  // 이름 중복 시 먼저 온 걸 사용
                            LinkedHashMap::new      // 순서 유지
                    ));

            // MediaMoodTag에 저장할 리스트
            List<MediaMoodTag> newMediaMoodTags = new ArrayList<>();

            // 중복 방지용
            Set<String> seen = new LinkedHashSet<>();

            for (int i = 0; i < aiTags.size(); i++) {
                String tagName = aiTags.get(i);
                MoodTag moodTag = moodTagByName.get(tagName);

                // DB에 없거나 중복태그일 경우 스킵
                if (moodTag == null || !seen.add(tagName)) {
                    continue;
                }

                newMediaMoodTags.add(MediaMoodTag.builder()
                        .media(media)
                        .moodTag(moodTag)
                        .priority(i + 1)    // 1부터 시작하는 우선순위
                        .build());
            }


            if (newMediaMoodTags.isEmpty()) {
                log.warn("[AI Tagging] 미디어 ID: {} - DB에 매핑 가능한 mood_tag가 없습니다.", event.mediaId());
                return;
            }

            // DB <-> AI 태킹 불일치 확인용 로그
            List<String> missingTags = aiTags.stream()
                    .filter(tagName -> !moodTagByName.containsKey(tagName))
                    .distinct()
                    .toList();

            if (!missingTags.isEmpty()) {
                log.warn("[AI Tagging] mediaId={} DB에 없는 mood tag 제외 {}", event.mediaId(), missingTags);
            }

            // 등록 후, 줄거리가 수정될 경우 변경할 경우 삭제 후 삽입
            mediaMoodTagAppend.replaceMediaMoodTags(event.mediaId(), newMediaMoodTags);

            log.info("[AI Tagging] mediaId={} mood tag {}건 저장 완료", event.mediaId(), newMediaMoodTags.size());

    }
}
