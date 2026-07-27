package com.ott.api_user.history.service;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ott.api_user.event.WatchHistoryCreatedEvent;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.domain.watch_history.domain.WatchHistory;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class WatchHistoryService {
    private final WatchHistoryRepository watchHistoryRepository;
    private final ContentsRepository contentsRepository;
    private final ApplicationEventPublisher eventPublisher; // 스프링의 이벤트 발송기

    //사용자가 영상 클릭 시 시청 이력 생성
    public void upsertWatchHistory(Long memberId, Long mediaId){
        Contents contents = contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC)
                .orElseThrow(()-> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));



        watchHistoryRepository.upsertWatchHistory(memberId, contents.getId());

        log.info("이벤트 발송 직전");

        eventPublisher.publishEvent(new WatchHistoryCreatedEvent(memberId));

    }

}
