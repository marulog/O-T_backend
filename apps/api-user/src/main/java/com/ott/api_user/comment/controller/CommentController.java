package com.ott.api_user.comment.controller;

import com.ott.api_user.comment.dto.request.CreateCommentRequest;
import com.ott.api_user.comment.dto.request.UpdateCommentRequest;
import com.ott.api_user.comment.dto.response.CommentResponse;
import com.ott.api_user.comment.dto.response.ContentsCommentResponse;
import com.ott.api_user.comment.dto.response.MyCommentResponse;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;

import lombok.RequiredArgsConstructor;
import com.ott.api_user.comment.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/comments")
public class CommentController implements CommentApi {

    private final CommentService commentService;

    // 댓글 등록
    @Override
    @PostMapping
    public ResponseEntity<SuccessResponse<CommentResponse>> createComment(
           @RequestBody CreateCommentRequest request,
           @AuthenticationPrincipal Long memberId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.of(commentService.createComment(memberId, request)));
    }

    // 댓글 수정
    @Override
    @PatchMapping("/{commentId}")
    public ResponseEntity<SuccessResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal Long memberId) {

        return ResponseEntity.ok(SuccessResponse.of(commentService.updateComment(memberId, commentId, request)));
    }

    // 댓글 삭제
    @Override
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal Long memberId) {

        commentService.deleteComment(memberId, commentId);

        return ResponseEntity.noContent().build();
    }

    // 댓글 조회 - 본인 댓글만 조회 가능, 최신순 정렬
    @Override
    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<PageResponse<MyCommentResponse>>> getMyComments(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(SuccessResponse.of(PageResponseMapper.from(commentService.getMyComments(memberId, page, size))));
    }

    // 콘텐츠 상세 페이지의 댓글 목록 조회
    @Override
    public ResponseEntity<SuccessResponse<PageResponse<ContentsCommentResponse>>> getContentCommentsList(
            @PathVariable(value = "mediaId") Long mediaId,
            @RequestParam(value = "page", defaultValue = "0") Integer pageParam,
            @RequestParam(value = "size", defaultValue = "20")  Integer sizeParam,
            @RequestParam(value = "includeSpoiler", defaultValue = "false") boolean includeSpoiler,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(commentService.getContentsCommentList(mediaId, memberId, pageParam,
                        sizeParam, includeSpoiler))));

    }
}
