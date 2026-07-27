package com.ott.api_admin.content.controller;

import com.ott.api_admin.content.dto.request.ContentsUpdateRequest;
import com.ott.api_admin.content.dto.request.ContentsUploadRequest;
import com.ott.api_admin.content.dto.response.ContentsDetailResponse;
import com.ott.api_admin.content.dto.response.ContentsListResponse;
import com.ott.api_admin.content.dto.response.ContentsUpdateResponse;
import com.ott.api_admin.content.dto.response.ContentsUploadResponse;
import com.ott.api_admin.upload.dto.request.MultipartUploadCompleteRequest;
import com.ott.api_admin.upload.dto.response.MultipartUploadPartUrlResponse;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.api_admin.content.service.BackOfficeContentsService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import com.ott.domain.common.PublicStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back-office/admin/contents")
@RequiredArgsConstructor
public class BackOfficeContentsController implements BackOfficeContentsApi {

    private final BackOfficeContentsService backOfficeContentsService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ContentsListResponse>>> getContents(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord,
            @RequestParam(value = "publicStatus", required = false) PublicStatus publicStatus
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeContentsService.getContents(page, size, searchWord, publicStatus)))
        );
    }

    @Override
    @GetMapping("/{mediaId}")
    public ResponseEntity<SuccessResponse<ContentsDetailResponse>> getContentsDetail(
            @PathVariable("mediaId") Long mediaId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(backOfficeContentsService.getContentsDetail(mediaId))
        );
    }

    @Override
    @PostMapping("/upload")
    public ResponseEntity<SuccessResponse<ContentsUploadResponse>> createContentsUpload(
            @Valid @RequestBody ContentsUploadRequest request,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(SuccessResponse.of(backOfficeContentsService.createContentsUpload(request, memberId)));
    }

    @Override
    @PatchMapping("/{contentsId}/upload")
    public ResponseEntity<SuccessResponse<ContentsUpdateResponse>> updateContentsUpload(
            @PathVariable("contentsId") Long contentsId,
            @Valid @RequestBody ContentsUpdateRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(backOfficeContentsService.updateContentsUpload(contentsId, request)));
    }

    @Override
    @PostMapping("/{contentsId}/upload/complete")
    public ResponseEntity<SuccessResponse<Void>> completeContentsUpload(
            @PathVariable("contentsId") Long contentsId,
            @Valid @RequestBody MultipartUploadCompleteRequest request
    ) {
        backOfficeContentsService.completeContentsOriginUpload(
                contentsId,
                request.objectKey(),
                request.uploadId(),
                request.parts().stream()
                        .map(part -> new UploadHelper.MultipartPartETag(part.partNumber(), part.eTag()))
                        .toList()
        );
        return ResponseEntity.ok(SuccessResponse.of(null));
    }
    @Override
    @GetMapping("/{contentsId}/upload/parts")
    public ResponseEntity<SuccessResponse<PageResponse<MultipartUploadPartUrlResponse>>> getContentsUploadPartUrls(
            @PathVariable("contentsId") Long contentsId,
            @RequestParam("objectKey") String objectKey,
            @RequestParam("uploadId") String uploadId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "100") Integer size
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeContentsService.getContentsOriginUploadPartUrls(contentsId, objectKey, uploadId, page, size)))
        );
    }
}
