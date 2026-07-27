package com.ott.infra.db.likes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ott.domain.common.Status;
import com.ott.domain.likes.domain.Likes;
import org.springframework.data.jpa.repository.Modifying;


import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;

public interface LikesRepository extends JpaRepository<Likes, Long> {
        boolean existsByMemberIdAndMediaIdAndStatus(Long memberId, Long mediaId, Status status);

        Optional<Likes> findByMemberIdAndMediaIdAndStatus(Long memberId, Long mediaId, Status status);

        Optional<Likes> findByMemberIdAndMediaId(Long memberId, Long mediaId);

        // 최근 좋아요한 미디어의 태그 ID 조회
        // 1단계 : 최근 좋아요 누른 미디어 100개 먼저 조회 (JOIN 보다 LIMIT 먼저)
        @Query("""
            SELECT l.media.id FROM Likes l
            WHERE l.member.id = :memberId AND l.status = :status
            ORDER BY l.createdDate DESC
            """)
        List<Long> findRecentLikedMediaIds(@Param("memberId") Long memberId, @Param("status") Status status,
                        Pageable pageable);

   // 회원 탈퇴 soft delete
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Likes l SET l.status = 'DELETE' WHERE l.member.id = :memberId")
    void softDeleteAllByMemberId(@Param("memberId") Long memberId);


    @Query("SELECT l.media.id FROM Likes l " +
           "WHERE l.member.id = :memberId " +
           "AND l.media.id IN :mediaIds " +
           "AND l.status = 'ACTIVE'")
    Set<Long> findLikedMediaIds(
            @Param("memberId") Long memberId,
            @Param("mediaIds") List<Long> mediaIds
    );

}
