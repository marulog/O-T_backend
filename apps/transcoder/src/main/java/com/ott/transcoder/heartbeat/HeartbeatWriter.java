package com.ott.transcoder.heartbeat;

import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Heartbeat DB 갱신 담당.
 * 스케줄러 스레드에서 호출되므로, 프록시 경유 @Transactional이 필요하다.
 */
@RequiredArgsConstructor
@Component
public class HeartbeatWriter {

    private final IngestJobRepository ingestJobRepository;

    @Transactional
    public void updateHeartbeat(Long jobId) {
        ingestJobRepository.updateHeartbeat(jobId);
    }
}
