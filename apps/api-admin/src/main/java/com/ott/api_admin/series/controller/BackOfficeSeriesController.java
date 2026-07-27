package com.ott.api_admin.series.controller;

import com.ott.api_admin.series.dto.request.SeriesUpdateRequest;
import com.ott.api_admin.series.dto.request.SeriesUploadRequest;
import com.ott.api_admin.series.dto.response.SeriesDetailResponse;
import com.ott.api_admin.series.dto.response.SeriesListResponse;
import com.ott.api_admin.series.dto.response.SeriesTitleListResponse;
import com.ott.api_admin.series.dto.response.SeriesUpdateResponse;
import com.ott.api_admin.series.dto.response.SeriesUploadResponse;
import com.ott.api_admin.series.service.BackOfficeSeriesService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back-office/admin/series")
@RequiredArgsConstructor
public class BackOfficeSeriesController implements BackOfficeSeriesApi {

    private final BackOfficeSeriesService backOfficeSeriesService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<SeriesListResponse>>> getSeries(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeSeriesService.getSeries(page, size, searchWord)))
        );
    }

    @Override
    @GetMapping("/titles")
    public ResponseEntity<SuccessResponse<PageResponse<SeriesTitleListResponse>>> getSeriesTitle(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeSeriesService.getSeriesTitle(page, size, searchWord)))
        );
    }

    @Override
    @GetMapping("/{mediaId}")
    public ResponseEntity<SuccessResponse<SeriesDetailResponse>> getSeriesDetail(@PathVariable("mediaId") Long mediaId) {
        return ResponseEntity.ok(
                SuccessResponse.of(backOfficeSeriesService.getSeriesDetail(mediaId))
        );
    }

    @Override
    @PostMapping("/upload")
    public ResponseEntity<SuccessResponse<SeriesUploadResponse>> createSeriesUpload(
            @Valid @RequestBody SeriesUploadRequest request,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(SuccessResponse.of(backOfficeSeriesService.createSeriesUpload(request, memberId)));
    }

    @Override
    @PatchMapping("/{seriesId}/upload")
    public ResponseEntity<SuccessResponse<SeriesUpdateResponse>> updateSeriesUpload(
            @PathVariable("seriesId") Long seriesId,
            @Valid @RequestBody SeriesUpdateRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(backOfficeSeriesService.updateSeriesUpload(seriesId, request)));
    }
}
