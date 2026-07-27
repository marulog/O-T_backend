package com.ott.infra.db.ingest_job.repository;

import com.ott.domain.ingest_job.domain.IngestJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestJobRepository extends JpaRepository<IngestJob, Long>, IngestJobRepositoryCustom {

    boolean existsByMediaId(Long mediaId);

    /**
     * CAS 선점: 조건에 맞는 경우에만 PROCESSING 상태로 전이하고 heartbeat를 갱신한다.
     * - PENDING → PROCESSING (최초 선점)
     * - PROCESSING/PARTIAL_SUCCESS + heartbeat 만료 → 상태 유지 + heartbeat 갱신 (takeover)
     *
     * @return affected rows (1=선점 성공, 0=실패)
     */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
        UPDATE ingest_job
           SET ingest_status = CASE
                   WHEN ingest_status = 'PENDING' THEN 'PROCESSING'
                   ELSE ingest_status
               END,
               heartbeat_at = NOW()
         WHERE id = :jobId
           AND (
                 ingest_status = 'PENDING'
              OR (
                   ingest_status IN ('PROCESSING', 'PARTIAL_SUCCESS')
                   AND (heartbeat_at IS NULL
                        OR heartbeat_at < DATE_SUB(NOW(), INTERVAL :heartbeatTimeoutSec SECOND))
                 )
               )
        """)
    int tryPreempt(@Param("jobId") Long jobId,
                   @Param("heartbeatTimeoutSec") int heartbeatTimeoutSec);

    /**
     * Heartbeat 갱신: 워커 생존 신호를 DB에 기록한다.
     */
    @Modifying
    @Query("UPDATE IngestJob i SET i.heartbeatAt = CURRENT_TIMESTAMP WHERE i.id = :jobId")
    void updateHeartbeat(@Param("jobId") Long jobId);
}
