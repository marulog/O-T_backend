package com.ott.api_user.search.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ott.api_user.search.dto.SearchItemResponse;
import com.ott.common.web.exception.ErrorResponse;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Tag(name = "Search API", description = "통합 검색 API입니다.")
public interface SearchApi {

        @Operation(summary = "통합 검색 API", description = "콘텐츠와 시리즈를 통합하여 최신순으로 검색합니다.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "0", description = "검색 성공 - 검색 결과 항목 응답 구성", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = SearchItemResponse.class)) }),
                        @ApiResponse(responseCode = "200", description = "검색 성공", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)) }),
                        @ApiResponse(responseCode = "400", description = "잘못된 요청 (검색어 누락 등)", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) })
        })
        @GetMapping
        ResponseEntity<SuccessResponse<PageResponse<SearchItemResponse>>> search(
                        @NotBlank(message = "검색어를 입력해주세요.")
                        @Size(min = 2, message = "검색어는 최소 2글자 이상이어야 합니다.")
                        @Parameter(description = "검색어를 입력해주세요.", required = true, example = "비밀") @RequestParam(value = "searchWord") String searchWord,

                        @PositiveOrZero
                        @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(value = "page", defaultValue = "0") Integer page,

                        @Positive @Max(100)
                        @Parameter(description = "한 페이지 당 최대 항목 개수(기본적으로 6의 배수)") @RequestParam(value = "size", defaultValue = "24") Integer size);

}
