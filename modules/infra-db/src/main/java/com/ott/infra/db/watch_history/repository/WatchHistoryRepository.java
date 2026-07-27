package com.ott.infra.db.watch_history.repository;

import com.ott.domain.common.Status;
import com.ott.domain.watch_history.domain.WatchHistory;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long>, WatchHistoryRepositoryCustom {

    // 회원 탈퇴
    @Modifying(clearAutomatically = true)
    @Query("UPDATE WatchHistory w SET w.status = 'DELETE' WHERE w.member.id = :memberId")
    void softDeleteAllByMemberId(@Param("memberId") Long memberId);


    // @Modifying
    // @Query(value = """
    //         INSERT INTO watch_history (member_id, contents_id, last_watched_at, re_watch_count, created_date, modified_date, status)
    //         VALUES (:memberId, :contentsId, NOW(), 0, NOW(), NOW(), 'ACTIVE')
    //         ON DUPLICATE KEY UPDATE
    //             last_watched_at = NOW(),
    //             re_watch_count = re_watch_count + 1,
    //             modified_date = NOW(),
    //             status = 'ACTIVE'
    //         """, nativeQuery = true)
    // void upsertWatchHistory(
    //         @Param("memberId") Long memberId,
    //         @Param("contentsId") Long contentsId
    // );

    @Modifying
    @Query(value = """
            INSERT INTO watch_history (
                member_id, contents_id, last_watched_at, re_watch_count,
                created_date, modified_date, status, is_used_for_ml
            )
            VALUES (:memberId, :contentsId, NOW(), 0, NOW(), NOW(), 'ACTIVE', false)
            ON DUPLICATE KEY UPDATE
                last_watched_at = VALUES(last_watched_at),
                re_watch_count = re_watch_count + 1,
                modified_date = VALUES(modified_date),
                status = 'ACTIVE',
                is_used_for_ml = 0;
            """, nativeQuery = true)
    void upsertWatchHistory(
        @Param("memberId") Long memberId,
        @Param("contentsId") Long contentsId
    );
    // Optional<WatchHistory> findByMember_IdAndContents_IdAndStatus(Long memberId, Long contentsId , Status status);

}
