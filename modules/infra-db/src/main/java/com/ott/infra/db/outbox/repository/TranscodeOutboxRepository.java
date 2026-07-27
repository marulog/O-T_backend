package com.ott.infra.db.outbox.repository;

import com.ott.domain.outbox.domain.OutboxStatus;
import com.ott.domain.outbox.domain.TranscodeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranscodeOutboxRepository extends JpaRepository<TranscodeOutbox, Long> {

    // 발행 대기 중인 Outbox 메시지를 생성순으로 조회 (배치 크기 제한)
    List<TranscodeOutbox> findTop50ByOutboxStatusOrderByCreatedDateAsc(OutboxStatus status);
}
