package com.ott.api_user.series.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ott.api_user.series.dto.SeriesContentsResponse;
import com.ott.api_user.series.dto.SeriesDetailResponse;
import com.ott.api_user.series.service.SeriesService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/series")
public class SeriesController implements SeriesApi {
    private final SeriesService seriesService;

    @Override
    public ResponseEntity<SuccessResponse<SeriesDetailResponse>> getSeriesDetail(
            @PathVariable(value = "mediaId") Long mediaId,
            @AuthenticationPrincipal Long memberId) {

        return ResponseEntity.ok(
                SuccessResponse.of(seriesService.getSeriesDetail(mediaId, memberId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<PageResponse<SeriesContentsResponse>>> getSeriesContents(
            @PathVariable(value = "mediaId") Long mediaId,
            @RequestParam(value = "page") Integer pageParam,
            @RequestParam(value = "size") Integer sizeParam,
            @AuthenticationPrincipal Long memberId) {

        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(seriesService.getSeriesContents(mediaId, pageParam, sizeParam, memberId))));
    }
}
