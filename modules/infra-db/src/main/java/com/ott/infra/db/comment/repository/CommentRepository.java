package com.ott.infra.db.comment.repository;

import com.ott.domain.comment.domain.Comment;
import com.ott.domain.common.Status;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import com.ott.domain.comment.domain.Comment;
import com.ott.domain.common.Status;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {

     @EntityGraph(attributePaths = {"member", "contents", "contents.media"})
     Optional<Comment> findByIdAndStatus(Long id, Status status);

     @Query("""
                        SELECT c FROM Comment c
                        JOIN FETCH c.member m
                        WHERE c.contents.id = :contentsId
                        AND c.status = :status
                        and (:includeSpoiler = true or c.isSpoiler = false)
                        """)
        Page<Comment> findByContents_IdAndStatusWithSpoilerCondition(
                        @Param("contentsId") Long contentsId,
                        @Param("status") Status status,
                        @Param("includeSpoiler") boolean includeSpoiler,
                        Pageable pageable

        );

     // 회원 탈퇴
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.status = 'DELETE' WHERE c.member.id = :memberId")
    void softDeleteAllByMemberId(@Param("memberId") Long memberId);
}
