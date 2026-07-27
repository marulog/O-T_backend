package com.ott.api_user.bookmark.controller;

import com.ott.api_user.bookmark.dto.response.BookmarkMediaResponse;
import com.ott.api_user.bookmark.dto.response.BookmarkShortFormResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ott.api_user.bookmark.dto.request.BookmarkRequest;
import com.ott.api_user.bookmark.service.BookmarkService;
import com.ott.common.web.response.SuccessResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@RequestMapping("/bookmarks")
public class BookmarkController implements BookmarkAPI  {

    private final BookmarkService bookmarkService;

    // 북마크 수정
    @Override
    public ResponseEntity<SuccessResponse<Void>> editBookmark(
            @Valid @RequestBody BookmarkRequest request,
            @AuthenticationPrincipal Long memberId) {

        bookmarkService.editBookmark(memberId, request.getMediaId());
        return ResponseEntity.noContent().build();
    }

    // 북마크한 콘텐츠 or 시리즈 리스트 조회
    @Override
    public ResponseEntity<SuccessResponse<PageResponse<BookmarkMediaResponse>>> getBookmarkMediaList(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal Long memberId) {

        return ResponseEntity.ok(SuccessResponse.of(
                PageResponseMapper.from(bookmarkService.getBookmarkMediaList(memberId, page, size))));
    }

    // 북마크한 숏폼 리스트 조회
    @Override
    public ResponseEntity<SuccessResponse<PageResponse<BookmarkShortFormResponse>>> getBookmarkShortFormList(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal Long memberId) {

        return ResponseEntity.ok(SuccessResponse.of(
                PageResponseMapper.from(bookmarkService.getBookmarkShortFormList(memberId, page, size))));
    }
}
