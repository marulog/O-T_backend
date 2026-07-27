package com.ott.api_user.history.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;

import com.ott.api_user.history.dto.request.WatchHistoryRequest;
import com.ott.common.web.exception.ErrorResponse;
import com.ott.common.web.response.SuccessResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@Tag(name = "WatchHistory", description = "시청 이력 생성 및 갱신 API")
public interface WatchHistoryApi {

        @Operation(summary = "시청 이력 생성 및 갱신", description = "영상을 시청하기 시작할 때 시청 이력을 남깁니다.")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "204", description = "시청 이력 기록 성공, 응답 본문 없음"),
                @ApiResponse(responseCode = "400", description = "잘못된 요청 (mediaId 누락 등)", content = {
                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) }),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 미디어 ID", content = {
                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) })
        })
        @PutMapping
        ResponseEntity<Void> upsertWatchHistory(
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
                @Valid @RequestBody WatchHistoryRequest request
        );
}
