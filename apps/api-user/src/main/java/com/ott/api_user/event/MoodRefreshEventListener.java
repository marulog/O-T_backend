package com.ott.api_user.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ott.api_user.moodrefresh.service.MoodRefreshService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoodRefreshEventListener {
    private final MoodRefreshService moodRefreshService;

    /**
     * @Async: 유저의 메인 응답 스레드와 완전히 분리되어 백그라운드에서 돌아감
     * @EventListener: 스프링 어플리케이션 내에서 WatchHistoryCreatedEvent를 발생시키면 알아서 실행됨.
     * @TransactionalEventListener: 메인 트랜잭션이 완벽하게 커밋(DB 저장 확정)된 직후에만 실행됨!.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleWatchHistoryCreated(WatchHistoryCreatedEvent event) {
        Long memberId = event.getMemberId();
        log.info("[Mood Refresh] 유저 {}의 시청 기록 추가 감지! 환기 조건 검사를 시작합니다.", memberId);

        try {
            moodRefreshService.analyzeAndCreateRefreshCard(memberId);
        } catch (Exception e) {
            // 비동기 스레드에서 터진 에러는 메인 서버에 영향을 주지 않도록 여기서 예외를 미리 처리
            log.error("[Mood Refresh] 환기 카드 생성 중 백그라운드 에러 발생: {}", e.getMessage());
        }
    }
}
