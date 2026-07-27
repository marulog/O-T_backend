package com.ott.api_admin.content.service;

import com.ott.api_admin.content.dto.request.ContentsUpdateRequest;
import com.ott.api_admin.content.dto.request.ContentsUploadRequest;
import com.ott.api_admin.content.dto.response.ContentsUpdateResponse;
import com.ott.api_admin.content.dto.response.ContentsUploadResponse;
import com.ott.api_admin.content.mapper.BackOfficeContentsMapper;
import com.ott.api_admin.content.vo.IngestJobResult;
import com.ott.api_admin.tagging.event.AiTaggingRequestedEvent;
import com.ott.api_admin.trending.event.TrendingCacheInvalidationEvent;
import com.ott.api_admin.upload.support.MediaTagLinker;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.MediaType;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.ingest_job.domain.IngestJob;
import com.ott.domain.ingest_job.domain.IngestStatus;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.domain.member.domain.Member;
import com.ott.domain.outbox.domain.TranscodeOutbox;
import com.ott.infra.db.outbox.repository.TranscodeOutboxRepository;
import com.ott.domain.series.domain.Series;
import com.ott.infra.db.series.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackOfficeContentsWriter {

    private final BackOfficeContentsMapper backOfficeContentsMapper;

    private final MediaRepository mediaRepository;
    private final MediaTagRepository mediaTagRepository;
    private final ContentsRepository contentsRepository;
    private final SeriesRepository seriesRepository;
    private final IngestJobRepository ingestJobRepository;
    private final TranscodeOutboxRepository transcodeOutboxRepository;

    private final UploadHelper uploadHelper;
    private final MediaTagLinker mediaTagLinker;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ContentsUploadResponse createContentsUpload(ContentsUploadRequest request, Long memberId) {
        Member uploader = uploadHelper.resolveUploader(memberId);
        Series series = resolveSeries(request.seriesId());

        Media media = mediaRepository.save(
                Media.builder()
                        .uploader(uploader)
                        .title(request.title())
                        .description(request.description())
                        .posterUrl("PENDING")
                        .thumbnailUrl("PENDING")
                        .bookmarkCount(0L)
                        .likesCount(0L)
                        .mediaType(MediaType.CONTENTS)
                        .mediaStatus(MediaStatus.INIT)
                        .publicStatus(request.publicStatus())
                        .build()
        );

        Contents contents = contentsRepository.save(
                Contents.builder()
                        .media(media)
                        .series(series)
                        .actors(request.actors())
                        .duration(request.duration())
                        .videoSize(request.videoSize())
                        .originUrl("PENDING")
                        .masterPlaylistUrl("PENDING")
                        .build()
        );

        Long contentsId = contents.getId();

        UploadHelper.MediaCreateUploadResult mediaCreateUploadResult = null;
        try {
            mediaCreateUploadResult = uploadHelper.prepareMediaCreate(
                    "contents",
                    contentsId,
                    request.posterFileName(),
                    request.thumbnailFileName(),
                    request.originFileName(),
                    request.videoSize()
            );

            media.updateImageKeys(
                    mediaCreateUploadResult.posterObjectUrl(),
                    mediaCreateUploadResult.thumbnailObjectUrl()
            );
            contents.updateStorageKeys(
                    mediaCreateUploadResult.originObjectUrl(),
                    mediaCreateUploadResult.masterPlaylistObjectUrl()
            );

            mediaTagLinker.linkTags(media, request.categoryId(), request.tagIdList());

            eventPublisher.publishEvent(new AiTaggingRequestedEvent(media.getId(), request.description()));

            return backOfficeContentsMapper.toContentsUploadResponse(
                    contentsId,
                    mediaCreateUploadResult.posterObjectKey(),
                    mediaCreateUploadResult.thumbnailObjectKey(),
                    mediaCreateUploadResult.originObjectKey(),
                    mediaCreateUploadResult.masterPlaylistObjectKey(),
                    mediaCreateUploadResult.posterUploadUrl(),
                    mediaCreateUploadResult.thumbnailUploadUrl(),
                    mediaCreateUploadResult.originUploadId(),
                    mediaCreateUploadResult.originTotalPartCount(),
                    mediaCreateUploadResult.originPartSizeBytes()
            );
        } catch (RuntimeException ex) {
            if (mediaCreateUploadResult != null) {
                try {
                    uploadHelper.abortMultipartUpload(
                            mediaCreateUploadResult.originObjectKey(),
                            mediaCreateUploadResult.originUploadId()
                    );
                } catch (Exception abortEx) {
                    log.warn("Failed to abort multipart upload. objectKey={}, uploadId={}",
                            mediaCreateUploadResult.originObjectKey(),
                            mediaCreateUploadResult.originUploadId(),
                            abortEx);
                }
            }
            throw ex;
        }
    }

    @Transactional
    public IngestJobResult createIngestJobWithOutbox(Long contentsId, String originObjectKey) {
        Contents contents = contentsRepository.findById(contentsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));
        Media media = contents.getMedia();

        if (ingestJobRepository.existsByMediaId(media.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_INGESTED);
        }

        // 1. IngestJob 저장
        IngestJob ingestJob = ingestJobRepository.save(
                IngestJob.builder()
                        .media(media)
                        .ingestStatus(IngestStatus.PENDING)
                        .build());

        // 2. Outbox 저장
        TranscodeOutbox outbox = TranscodeOutbox.builder()
                .mediaId(media.getId())
                .ingestJobId(ingestJob.getId())
                .originUrl(originObjectKey)
                .fileSize((long) contents.getVideoSize() * 1024)
                .mediaType(MediaType.CONTENTS)
                .build();
        transcodeOutboxRepository.save(outbox);

        log.info("IngestJob + Outbox 생성 - contentsId: {}, mediaId: {}, ingestJobId: {}, outboxId: {}",
                contentsId, media.getId(), ingestJob.getId(), outbox.getId());

        return new IngestJobResult(
                media.getId(), ingestJob.getId(),
                originObjectKey, (long) contents.getVideoSize() * 1024,
                MediaType.CONTENTS);
    }

    @Transactional
    public ContentsUpdateResponse updateContentsUpload(Long contentsId, ContentsUpdateRequest request) {
        Contents contents = contentsRepository.findWithMediaById(contentsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

        Media media = contents.getMedia();
        Series series = resolveSeries(request.seriesId());

        media.updateMetadata(request.title(), request.description(), request.publicStatus());
        contents.updateMetadata(series, request.actors());

        UploadHelper.ImageUpdateUploadResult imageUpdateUploadResult = uploadHelper.prepareImageUpdate(
                "contents",
                contentsId,
                request.posterFileName(),
                request.thumbnailFileName(),
                media.getPosterUrl(),
                media.getThumbnailUrl()
        );

        media.updateImageKeys(
                imageUpdateUploadResult.nextPosterUrl(),
                imageUpdateUploadResult.nextThumbnailUrl()
        );

        mediaTagRepository.deleteAllByMedia_Id(media.getId());
        mediaTagLinker.linkTags(media, request.categoryId(), request.tagIdList());

        eventPublisher.publishEvent(new TrendingCacheInvalidationEvent()); // 이벤트 추가

        return backOfficeContentsMapper.toContentsUpdateResponse(
                contentsId,
                imageUpdateUploadResult.posterObjectKey(),
                imageUpdateUploadResult.thumbnailObjectKey(),
                imageUpdateUploadResult.posterUploadUrl(),
                imageUpdateUploadResult.thumbnailUploadUrl()
        );
    }

    private Series resolveSeries(Long seriesId) {
        if (seriesId == null) {
            return null;
        }
        return seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));
    }
}
