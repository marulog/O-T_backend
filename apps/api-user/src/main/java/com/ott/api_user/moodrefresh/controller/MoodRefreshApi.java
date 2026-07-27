package com.ott.api_user.moodrefresh.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ott.api_user.moodrefresh.dto.response.MoodRefreshResponse;
import com.ott.common.web.exception.ErrorResponse;
import com.ott.common.web.response.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "MoodRefresh", description = "분위기 환기 시스템 API")
public interface MoodRefreshApi {

    // 1. 유저가 홈 화면에 들어왔을 때 호출 (활성화된 카드 조회)
    @Operation(summary = "활성화된 환기 카드 조회", description = "유저가 홈 화면에 진입했을 때 노출할 활성화된 분위기 환기 카드를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "0", description = "조회 성공 - 환기 카드 상세 데이터", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = MoodRefreshResponse.class)) }),
            @ApiResponse(responseCode = "200", description = "환기 카드 조회 성공", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = SuccessResponse.class)) })
    })
    @GetMapping("/active")
    ResponseEntity<SuccessResponse<MoodRefreshResponse>> getActiveCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    );


    // 2. 유저가 X(닫기) 버튼을 눌렀을 때 호출
    @Operation(summary = "환기 카드 숨김 처리", description = "유저가 환기 카드의 X(닫기) 버튼을 눌렀을 때 해당 카드를 숨김 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "환기 카드 숨김 처리 성공, 응답 본문 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 환기 카드 ID", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) })
    })
    @PatchMapping("/{refreshId}/hide")
    ResponseEntity<Void> hideCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable("refreshId") @Parameter(description = "숨김 처리할 환기 카드 ID", example = "1") Long refreshId
    );
}