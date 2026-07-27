package com.ott.api_admin.content.service;

import com.ott.api_admin.content.dto.response.ContentsDetailResponse;
import com.ott.api_admin.content.dto.response.ContentsListResponse;
import com.ott.api_admin.content.mapper.BackOfficeContentsMapper;
import com.ott.api_admin.upload.dto.response.MultipartUploadPartUrlResponse;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BackOfficeContentsReader {

    private final BackOfficeContentsMapper backOfficeContentsMapper;
    private final MediaRepository mediaRepository;
    private final MediaTagRepository mediaTagRepository;
    private final ContentsRepository contentsRepository;
    private final UploadHelper uploadHelper;

    @Transactional(readOnly = true)
    public PageResult<ContentsListResponse> getContents(int page, int size, String searchWord, PublicStatus publicStatus) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Media> mediaPage = mediaRepository.findMediaListByMediaTypeAndSearchWordAndPublicStatus(
                pageable,
                MediaType.CONTENTS,
                searchWord,
                publicStatus
        );

        List<ContentsListResponse> responseList = mediaPage.getContent().stream()
                .map(backOfficeContentsMapper::toContentsListResponse)
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(
                mediaPage.getNumber(),
                mediaPage.getTotalPages(),
                mediaPage.getSize()
        );
        return PageResult.of(pageMetadata, responseList);
    }

    @Transactional(readOnly = true)
    public ContentsDetailResponse getContentsDetail(Long mediaId) {
        Contents contents = contentsRepository.findWithMediaAndUploaderByMediaId(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

        Media media = contents.getMedia();
        String uploaderNickname = media.getUploader().getNickname();
        Long bookmarkCount = media.getBookmarkCount();

        Long originMediaId = mediaId;
        String seriesTitle = null;
        Long seriesId = null;
        if (contents.getSeries() != null) {
            Media originMedia = contents.getSeries().getMedia();
            originMediaId = originMedia.getId();
            seriesTitle = originMedia.getTitle();
            seriesId = contents.getSeries().getId();
            bookmarkCount = originMedia.getBookmarkCount();
        }

        List<MediaTag> mediaTagList = mediaTagRepository.findWithTagAndCategoryByMediaId(originMediaId);

        return backOfficeContentsMapper.toContentsDetailResponse(seriesId, contents, media, uploaderNickname, seriesTitle, mediaTagList, bookmarkCount);
    }

    @Transactional(readOnly = true)
    public int getContentsUploadInfo(Long contentsId, String objectKey) {
        Contents contents = contentsRepository.findById(contentsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

        uploadHelper.validateOriginObjectKey(
                objectKey,
                contents.getOriginUrl(),
                ErrorCode.CONTENTS_ORIGIN_OBJECT_KEY_MISMATCH
        );

        return uploadHelper.getMultipartPartCount(contents.getVideoSize());
    }

    @Transactional(readOnly = true)
    public PageResult<MultipartUploadPartUrlResponse> getContentsOriginUploadPartUrls(
            Long contentsId, String objectKey, String uploadId, Integer page, Integer size) {
        Contents contents = contentsRepository.findById(contentsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

        uploadHelper.validateOriginObjectKey(
                objectKey,
                contents.getOriginUrl(),
                ErrorCode.CONTENTS_ORIGIN_OBJECT_KEY_MISMATCH
        );

        int totalPartCount = uploadHelper.getMultipartPartCount(contents.getVideoSize());
        var partUrlPage = uploadHelper.getMultipartPartUrls(
                objectKey, uploadId, totalPartCount, page, size
        );

        List<MultipartUploadPartUrlResponse> dataList = partUrlPage.getDataList().stream()
                .map(part -> new MultipartUploadPartUrlResponse(part.partNumber(), part.uploadUrl()))
                .toList();

        return PageResult.of(partUrlPage.getPageMetadata(), dataList);
    }
}
