package com.ott.api_admin.shortform.service;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.content.vo.IngestJobResult;
import com.ott.api_admin.shortform.dto.request.ShortFormUpdateRequest;
import com.ott.api_admin.shortform.dto.request.ShortFormUploadRequest;
import com.ott.api_admin.shortform.dto.response.ShortFormUpdateResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUploadResponse;
import com.ott.api_admin.shortform.mapper.BackOfficeShortFormMapper;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Role;
import com.ott.domain.ingest_job.domain.IngestJob;
import com.ott.domain.ingest_job.domain.IngestStatus;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.domain.outbox.domain.TranscodeOutbox;
import com.ott.infra.db.outbox.repository.TranscodeOutboxRepository;
import com.ott.domain.series.domain.Series;
import com.ott.infra.db.series.repository.SeriesRepository;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackOfficeShortFormWriter {

    private final BackOfficeShortFormMapper backOfficeShortFormMapper;
    private final MediaRepository mediaRepository;
    private final MediaTagRepository mediaTagRepository;
    private final SeriesRepository seriesRepository;
    private final ContentsRepository contentsRepository;
    private final ShortFormRepository shortFormRepository;
    private final IngestJobRepository ingestJobRepository;
    private final TranscodeOutboxRepository transcodeOutboxRepository;
    private final UploadHelper uploadHelper;

    @Transactional
    public ShortFormUploadResponse createShortFormUpload(ShortFormUploadRequest request, AdminActor actor) {
        Member uploader = uploadHelper.resolveUploader(actor.memberId());
        Series series = null;
        Contents contents = null;

        if (request.mediaType().equals(MediaType.SERIES)) {
            series = seriesRepository.findById(request.originId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));
        } else if (request.mediaType().equals(MediaType.CONTENTS)) {
            contents = resolveContents(request.originId());
        } else {
            throw new BusinessException(ErrorCode.INVALID_SHORTFORM_TARGET);
        }

        Media media = mediaRepository.save(
                Media.builder()
                        .uploader(uploader)
                        .title(request.title())
                        .description(request.description())
                        .posterUrl("PENDING")
                        .thumbnailUrl("PENDING")
                        .bookmarkCount(0L)
                        .likesCount(0L)
                        .mediaType(MediaType.SHORT_FORM)
                        .mediaStatus(MediaStatus.INIT)
                        .publicStatus(request.publicStatus())
                        .build());

        ShortForm shortForm = shortFormRepository.save(
                ShortForm.builder()
                        .media(media)
                        .series(series)
                        .contents(contents)
                        .duration(request.duration())
                        .videoSize(request.videoSize())
                        .originUrl("PENDING")
                        .masterPlaylistUrl("PENDING")
                        .build());

        Long shortFormId = shortForm.getId();
        UploadHelper.MediaCreateUploadResult mediaCreateUploadResult = null;
        try {
            mediaCreateUploadResult = uploadHelper.prepareMediaCreate(
                    "short-forms",
                    shortFormId,
                    request.posterFileName(),
                    null,
                    request.originFileName(),
                    request.videoSize()
            );

            media.updateImageKeys(
                    mediaCreateUploadResult.posterObjectUrl(),
                    mediaCreateUploadResult.thumbnailObjectUrl());
            shortForm.updateStorageKeys(
                    mediaCreateUploadResult.originObjectUrl(),
                    mediaCreateUploadResult.masterPlaylistObjectUrl());

            Long originMediaId = resolveOriginMediaId(series, contents);
            inheritOriginMediaTags(media, originMediaId);

            return backOfficeShortFormMapper.toShortFormUploadResponse(
                    shortFormId,
                    mediaCreateUploadResult.posterObjectKey(),
                    mediaCreateUploadResult.thumbnailObjectKey(),
                    mediaCreateUploadResult.originObjectKey(),
                    mediaCreateUploadResult.masterPlaylistObjectKey(),
                    mediaCreateUploadResult.posterUploadUrl(),
                    mediaCreateUploadResult.thumbnailUploadUrl(),
                    mediaCreateUploadResult.originUploadId(),
                    mediaCreateUploadResult.originTotalPartCount(),
                    mediaCreateUploadResult.originPartSizeBytes());
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
    public ShortFormUpdateResponse updateShortFormUpload(Long shortformId, ShortFormUpdateRequest request, AdminActor actor) {
        ShortForm shortForm = shortFormRepository.findWithMediaAndUploaderByShortFormId(shortformId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHORT_FORM_NOT_FOUND));

        Media media = shortForm.getMedia();
        Long memberId = actor.memberId();
        boolean isEditor = actor.roleKeys().contains(Role.EDITOR.getKey());
        if (isEditor && !media.getUploader().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Series series = null;
        Contents contents = null;

        if (request.mediaType().equals(MediaType.SERIES)) {
            series = seriesRepository.findById(request.originId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));
        } else if (request.mediaType().equals(MediaType.CONTENTS)) {
            contents = resolveContents(request.originId());
        } else {
            throw new BusinessException(ErrorCode.INVALID_SHORTFORM_TARGET);
        }

        media.updateMetadata(request.title(), request.description(), request.publicStatus());
        shortForm.updateMetadata(series, contents);

        Long shortFormId = shortForm.getId();
        UploadHelper.ImageUpdateUploadResult imageUpdateUploadResult = uploadHelper.prepareImageUpdate(
                "short-forms",
                shortFormId,
                request.posterFileName(),
                null,
                media.getPosterUrl(),
                media.getThumbnailUrl()
        );

        media.updateImageKeys(
                imageUpdateUploadResult.nextPosterUrl(),
                imageUpdateUploadResult.nextThumbnailUrl()
        );

        Long originMediaId = resolveOriginMediaId(series, contents);
        mediaTagRepository.deleteAllByMedia_Id(media.getId());
        inheritOriginMediaTags(media, originMediaId);

        return backOfficeShortFormMapper.toShortFormUpdateResponse(
                shortFormId,
                imageUpdateUploadResult.posterObjectKey(),
                imageUpdateUploadResult.thumbnailObjectKey(),
                imageUpdateUploadResult.posterUploadUrl(),
                imageUpdateUploadResult.thumbnailUploadUrl());
    }

    @Transactional
    public IngestJobResult createIngestJobWithOutbox(Long shortFormId, String originObjectKey) {
        ShortForm shortForm = shortFormRepository.findById(shortFormId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHORT_FORM_NOT_FOUND));
        Media media = shortForm.getMedia();

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
                .fileSize((long) shortForm.getVideoSize() * 1024)
                .mediaType(MediaType.SHORT_FORM)
                .build();
        transcodeOutboxRepository.save(outbox);

        log.info("IngestJob + Outbox 생성 - shortFormId: {}, mediaId: {}, ingestJobId: {}, outboxId: {}",
                shortFormId, media.getId(), ingestJob.getId(), outbox.getId());

        return new IngestJobResult(
                media.getId(),
                ingestJob.getId(),
                originObjectKey,
                (long) shortForm.getVideoSize() * 1024,
                MediaType.SHORT_FORM);
    }

    private Contents resolveContents(Long contentsId) {
        if (contentsId == null) {
            return null;
        }
        Contents contents = contentsRepository.findById(contentsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));
        if (contents.getSeries() != null) {
            throw new BusinessException(ErrorCode.INVALID_SHORTFORM_CONTENTS_TARGET);
        }
        return contents;
    }

    private Long resolveOriginMediaId(Series series, Contents contents) {
        if (series != null) {
            return series.getMedia().getId();
        }
        if (contents != null) {
            return contents.getMedia().getId();
        }
        throw new BusinessException(ErrorCode.SHORTFORM_ORIGIN_MEDIA_NOT_FOUND);
    }

    private void inheritOriginMediaTags(Media targetMedia, Long originMediaId) {
        List<MediaTag> originMediaTagList = mediaTagRepository.findWithTagAndCategoryByMediaId(originMediaId);
        if (originMediaTagList.isEmpty()) {
            return;
        }

        List<MediaTag> targetMediaTagList = originMediaTagList.stream()
                .map(originMediaTag -> MediaTag.builder()
                        .media(targetMedia)
                        .tag(originMediaTag.getTag())
                        .build())
                .toList();
        mediaTagRepository.saveAll(targetMediaTagList);
    }
}
