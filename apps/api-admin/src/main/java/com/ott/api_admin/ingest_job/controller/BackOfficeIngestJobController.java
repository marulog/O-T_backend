package com.ott.api_admin.ingest_job.controller;

import com.ott.api_admin.common.application.AdminActorMapper;
import com.ott.api_admin.ingest_job.dto.response.IngestJobListResponse;
import com.ott.api_admin.ingest_job.service.BackOfficeIngestJobService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back-office/ingest-jobs")
@RequiredArgsConstructor
public class BackOfficeIngestJobController implements BackOfficeIngestJobApi {

    private final BackOfficeIngestJobService backOfficeIngestJobService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<IngestJobListResponse>>> getIngestJobList(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeIngestJobService.getIngestJobList(
                        page,
                        size,
                        searchWord,
                        AdminActorMapper.from(authentication)
                )))
        );
    }
}
