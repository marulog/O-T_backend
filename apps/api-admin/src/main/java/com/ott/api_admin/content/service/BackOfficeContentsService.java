package com.ott.api_admin.content.service;

import com.ott.api_admin.content.dto.request.ContentsUpdateRequest;
import com.ott.api_admin.content.dto.request.ContentsUploadRequest;
import com.ott.api_admin.content.dto.response.ContentsDetailResponse;
import com.ott.api_admin.content.dto.response.ContentsListResponse;
import com.ott.api_admin.content.dto.response.ContentsUpdateResponse;
import com.ott.api_admin.content.dto.response.ContentsUploadResponse;
import com.ott.api_admin.content.vo.IngestJobResult;
import com.ott.api_admin.publish.RabbitTranscodePublisher;
import com.ott.api_admin.upload.dto.response.MultipartUploadPartUrlResponse;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.PublicStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Contents 오케스트레이션 서비스
 * 트랜잭션을 직접 갖지 않으며, Reader/Writer에 위임
 * S3 같은 외부 호출은 이 계층에서 직접 호출하여 트랜잭션 밖에서 실행
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class BackOfficeContentsService {

    private final BackOfficeContentsReader reader;
    private final BackOfficeContentsWriter writer;
    private final UploadHelper uploadHelper;
    private final RabbitTranscodePublisher transcodePublisher;

    // ── 읽기 위임 ──

    public PageResult<ContentsListResponse> getContents(int page, int size, String searchWord, PublicStatus publicStatus) {
        return reader.getContents(page, size, searchWord, publicStatus);
    }

    public ContentsDetailResponse getContentsDetail(Long mediaId) {
        return reader.getContentsDetail(mediaId);
    }

    public PageResult<MultipartUploadPartUrlResponse> getContentsOriginUploadPartUrls(
            Long contentsId, String objectKey, String uploadId, Integer page, Integer size) {
        return reader.getContentsOriginUploadPartUrls(contentsId, objectKey, uploadId, page, size);
    }

    // ── 쓰기 위임 ──

    public ContentsUploadResponse createContentsUpload(ContentsUploadRequest request, Long memberId) {
        return writer.createContentsUpload(request, memberId);
    }

    public ContentsUpdateResponse updateContentsUpload(Long contentsId, ContentsUpdateRequest request) {
        return writer.updateContentsUpload(contentsId, request);
    }

    // ── complete ──

    public void completeContentsOriginUpload(
            Long contentsId, String objectKey, String uploadId, List<UploadHelper.MultipartPartETag> parts
    ) {

        // Phase 1: 검증 + 정보 조회 (readOnly 트랜잭션)
        int totalPartCount = reader.getContentsUploadInfo(contentsId, objectKey);

        // Phase 2: S3 멀티파트 완료 (트랜잭션 밖 — 외부 호출)
        uploadHelper.completeMultipartUpload(objectKey, uploadId, totalPartCount, parts);

        // Phase 3: IngestJob 생성 (쓰기 트랜잭션)
        IngestJobResult result = writer.createIngestJobWithOutbox(contentsId, objectKey);

        log.info("outbox 저장 완료 - contentsId: {}, mediaId: {}, ingestJobId: {}",
                contentsId, result.mediaId(), result.ingestJobId());
    }
}
