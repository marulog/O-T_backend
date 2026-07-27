package com.ott.api_admin.shortform.controller;

import com.ott.api_admin.common.application.AdminActorMapper;
import com.ott.api_admin.shortform.dto.response.OriginMediaTitleListResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormDetailResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormListResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUpdateResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUploadResponse;
import com.ott.api_admin.upload.dto.request.MultipartUploadCompleteRequest;
import com.ott.api_admin.upload.dto.response.MultipartUploadPartUrlResponse;
import com.ott.api_admin.shortform.dto.request.ShortFormUpdateRequest;
import com.ott.api_admin.shortform.dto.request.ShortFormUploadRequest;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.api_admin.shortform.service.BackOfficeShortFormService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import com.ott.domain.common.PublicStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back-office/short-forms")
@RequiredArgsConstructor
public class BackOfficeShortFormController implements BackOfficeShortFormApi {

    private final BackOfficeShortFormService backOfficeShortFormService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ShortFormListResponse>>> getShortFormList(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord,
            @RequestParam(value = "publicStatus", required = false) PublicStatus publicStatus,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeShortFormService.getShortFormList(
                        page,
                        size,
                        searchWord,
                        publicStatus,
                        AdminActorMapper.from(authentication)
                )))
        );
    }

    @Override
    @GetMapping("/origin-media")
    public ResponseEntity<SuccessResponse<PageResponse<OriginMediaTitleListResponse>>> getOriginMediaTitle(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeShortFormService.getOriginMediaTitle(page, size, searchWord)))
        );
    }

    @Override
    @GetMapping("/{mediaId}")
    public ResponseEntity<SuccessResponse<ShortFormDetailResponse>> getShortFormDetail(
            @PathVariable("mediaId") Long mediaId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(backOfficeShortFormService.getShortFormDetail(mediaId, AdminActorMapper.from(authentication)))
        );
    }

    @Override
    @PostMapping("/upload")
    public ResponseEntity<SuccessResponse<ShortFormUploadResponse>> createShortFormUpload(
            @Valid @RequestBody ShortFormUploadRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.of(backOfficeShortFormService.createShortFormUpload(
                request,
                AdminActorMapper.from(authentication)
        )));
    }

    @Override
    @PatchMapping("/{shortformId}/upload")
    public ResponseEntity<SuccessResponse<ShortFormUpdateResponse>> updateShortFormUpload(
            @PathVariable("shortformId") Long shortformId,
            @Valid @RequestBody ShortFormUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.of(backOfficeShortFormService.updateShortFormUpload(
                shortformId,
                request,
                AdminActorMapper.from(authentication)
        )));
    }

    @Override
    @PostMapping("/{shortformId}/upload/complete")
    public ResponseEntity<SuccessResponse<Void>> completeShortFormUpload(
            @PathVariable("shortformId") Long shortformId,
            @Valid @RequestBody MultipartUploadCompleteRequest request,
            Authentication authentication
    ) {
        backOfficeShortFormService.completeShortFormOriginUpload(
                shortformId,
                request.objectKey(),
                request.uploadId(),
                request.parts().stream()
                        .map(part -> new UploadHelper.MultipartPartETag(part.partNumber(), part.eTag()))
                        .toList(),
                AdminActorMapper.from(authentication)
        );
        return ResponseEntity.ok(SuccessResponse.of(null));
    }

    @Override
    @GetMapping("/{shortformId}/upload/parts")
    public ResponseEntity<SuccessResponse<PageResponse<MultipartUploadPartUrlResponse>>> getShortFormUploadPartUrls(
            @PathVariable("shortformId") Long shortformId,
            @RequestParam("objectKey") String objectKey,
            @RequestParam("uploadId") String uploadId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "100") Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(
                        PageResponseMapper.from(backOfficeShortFormService.getShortFormOriginUploadPartUrls(
                                shortformId,
                                objectKey,
                                uploadId,
                                page,
                                size,
                                AdminActorMapper.from(authentication)
                        ))
                )
        );
    }
}
