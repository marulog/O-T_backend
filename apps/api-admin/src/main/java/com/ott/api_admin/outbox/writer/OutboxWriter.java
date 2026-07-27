package com.ott.api_admin.outbox.writer;

import com.ott.domain.outbox.domain.TranscodeOutbox;
import com.ott.infra.db.outbox.repository.TranscodeOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final TranscodeOutboxRepository outboxRepository;

    @Transactional
    public void markAsPublished(Long id) {
        outboxRepository.findById(id)
                .ifPresent(TranscodeOutbox::markPublished);
    }

    @Transactional
    public void markAsFailed(Long id, String errorMessage) {
        outboxRepository.findById(id)
                .ifPresent(outbox -> outbox.markFailed(errorMessage));
    }
}
