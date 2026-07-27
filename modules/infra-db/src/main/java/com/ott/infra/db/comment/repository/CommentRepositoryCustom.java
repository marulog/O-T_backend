package com.ott.infra.db.comment.repository;

import com.ott.domain.comment.domain.Comment;
import com.ott.domain.common.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentRepositoryCustom {

    // 내가 작성한 댓글 목록 조회 (페이징, 최신순)
    Page<Comment> findMyComments(Long memberId, Status status, Pageable pageable);
}
