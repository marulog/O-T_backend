package com.ott.api_user.comment.service;

import com.ott.api_user.comment.dto.request.CreateCommentRequest;
import com.ott.api_user.comment.dto.request.UpdateCommentRequest;
import com.ott.api_user.comment.dto.response.CommentResponse;
import com.ott.api_user.comment.dto.response.ContentsCommentResponse;
import com.ott.api_user.comment.dto.response.MyCommentResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.comment.domain.Comment;
import com.ott.infra.db.comment.repository.CommentRepository;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class CommentService {

        private final CommentRepository commentRepository;
        private final ContentsRepository contentsRepository;
        private final MemberRepository memberRepository;

        // 댓글 작성
        @Transactional
        public CommentResponse createComment(Long memberId, CreateCommentRequest request) {

                Member findMember = memberRepository.findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                Contents contents = contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(
                                request.getMediaId(), Status.ACTIVE, PublicStatus.PUBLIC)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

                Comment saved = commentRepository.save(
                        Comment.builder()
                                .member(findMember)
                                .contents(contents)
                                .content(request.getContent())
                                .isSpoiler(request.getIsSpoiler())
                                .build()
                );

                return CommentResponse.from(saved);

        }

        // 댓글 수정 - 본인 댓글만 수정 가능
        @Transactional
        public CommentResponse updateComment(Long memberId, Long commentId, @Valid UpdateCommentRequest request) {
                Comment comment = commentRepository.findByIdAndStatus(commentId, Status.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

                // 본인만 수정 가능
                if (!comment.getMember().getId().equals(memberId)) {
                throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
                }

                comment.update(request.getContent(), request.getIsSpoiler());

                return CommentResponse.from(comment);
        }

        // 댓글 삭제 - 본인 댓글만 삭제 가능
        @Transactional
        public void deleteComment(Long memberId, Long commentId) {
                Comment comment = commentRepository.findByIdAndStatus(commentId, Status.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

                // 본인만 삭제 가능 -> softDelete
                if (!comment.getMember().getId().equals(memberId)) {
                throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
                }

                comment.updateStatus(Status.DELETE);
        }

        // 댓글 조회 - 본인 댓글만 조회 가능(최신순)
        @Transactional(readOnly = true)
        public PageResult<MyCommentResponse> getMyComments(
                Long memberId,
                Integer page,
                Integer size) {

                PageRequest pageable = PageRequest.of(page, size);

                Page<Comment> commentPage = commentRepository.findMyComments(memberId, Status.ACTIVE, pageable);

                List<MyCommentResponse> responseList = commentPage.getContent().stream()
                        .map(MyCommentResponse::from)
                        .toList();

                PageMetadata pageMetadata = PageMetadata.of(
                        commentPage.getNumber(),
                        commentPage.getTotalPages(),
                        commentPage.getSize()
                );

                return PageResult.of(pageMetadata, responseList);
        }

        // 콘텐츠 상세 조회 댓글 목록
        @Transactional(readOnly = true)
        public PageResult<ContentsCommentResponse> getContentsCommentList(Long mediaId, Long memberId, int page, int size, boolean includeSpoiler) {

                // mediaId를 기준으로 Contents 엔티티 조회
                Contents contents = contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

                Page<Comment> commentPage = commentRepository.findByContents_IdAndStatusWithSpoilerCondition(contents.getId(), Status.ACTIVE, includeSpoiler, pageable);

                List<ContentsCommentResponse> responseList = commentPage.getContent().stream()
                        .map(comment -> {
                                // 유저 ID가 댓글 작성자의 ID와 같다면 true
                                Boolean isMine = isCommentOwner(comment, memberId);

                                // 수정된 DTO의 of 메서드 사용
                                return ContentsCommentResponse.from(comment, isMine);
                        })
                        .toList();

                PageMetadata pageMetadata = PageMetadata.of(
                        commentPage.getNumber(),
                        commentPage.getTotalPages(),
                        commentPage.getSize());

                return PageResult.of(pageMetadata, responseList);
        }

        private boolean isCommentOwner(Comment comment, Long currentMemberId) {
                if (currentMemberId == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
                return comment.getMember().getId().equals(currentMemberId);
        }
}
