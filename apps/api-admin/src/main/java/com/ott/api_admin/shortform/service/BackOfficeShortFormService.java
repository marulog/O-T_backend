package com.ott.api_admin.shortform.service;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.content.vo.IngestJobResult;
import com.ott.api_admin.publish.RabbitTranscodePublisher;
import com.ott.api_admin.shortform.dto.request.ShortFormUpdateRequest;
import com.ott.api_admin.shortform.dto.request.ShortFormUploadRequest;
import com.ott.api_admin.shortform.dto.response.OriginMediaTitleListResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormDetailResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormListResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUpdateResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUploadResponse;
import com.ott.api_admin.upload.dto.response.MultipartUploadPartUrlResponse;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.PublicStatus;
import com.ott.infra.mq.TranscodeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ShortForm 오케스트레이션 서비스
 * 트랜잭션을 직접 갖지 않으며, Reader/Writer에 위임
 * S3 같은 외부 호출은 이 계층에서 직접 호출하여 트랜잭션 밖에서 실행
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class BackOfficeShortFormService {

    private final BackOfficeShortFormReader reader;
    private final BackOfficeShortFormWriter writer;
    private final UploadHelper uploadHelper;
    private final RabbitTranscodePublisher transcodePublisher;

    // ── 읽기 위임 ──

    public PageResult<ShortFormListResponse> getShortFormList(
            Integer page, Integer size, String searchWord, PublicStatus publicStatus,
            AdminActor actor) {
        return reader.getShortFormList(page, size, searchWord, publicStatus, actor);
    }

    public PageResult<OriginMediaTitleListResponse> getOriginMediaTitle(Integer page, Integer size, String searchWord) {
        return reader.getOriginMediaTitle(page, size, searchWord);
    }

    public ShortFormDetailResponse getShortFormDetail(Long mediaId, AdminActor actor) {
        return reader.getShortFormDetail(mediaId, actor);
    }

    public PageResult<MultipartUploadPartUrlResponse> getShortFormOriginUploadPartUrls(
            Long shortFormId, String objectKey, String uploadId,
            Integer page, Integer size, AdminActor actor) {
        return reader.getShortFormOriginUploadPartUrls(shortFormId, objectKey, uploadId, page, size, actor);
    }

    // ── 쓰기 위임 ──

    public ShortFormUploadResponse createShortFormUpload(ShortFormUploadRequest request, AdminActor actor) {
        return writer.createShortFormUpload(request, actor);
    }

    public ShortFormUpdateResponse updateShortFormUpload(Long shortformId, ShortFormUpdateRequest request, AdminActor actor) {
        return writer.updateShortFormUpload(shortformId, request, actor);
    }

    // ── complete ──

    public void completeShortFormOriginUpload(
            Long shortFormId, String objectKey, String uploadId,
            List<UploadHelper.MultipartPartETag> parts, AdminActor actor
    ) {

        // Phase 1: 검증 + 권한 체크 + 정보 조회 (readOnly 트랜잭션)
        int totalPartCount = reader.getShortFormUploadInfo(shortFormId, objectKey, actor);

        // Phase 2: S3 멀티파트 완료 (트랜잭션 밖 — 외부 호출)
        uploadHelper.completeMultipartUpload(objectKey, uploadId, totalPartCount, parts);

        // Phase 3: IngestJob + Outbox 생성 (쓰기 트랜잭션)
        IngestJobResult result = writer.createIngestJobWithOutbox(shortFormId, objectKey);

        log.info("Outbox 저장 완료 - shortFormId: {}, mediaId: {}, ingestJobId: {}",
                shortFormId, result.mediaId(), result.ingestJobId());
    }
}
