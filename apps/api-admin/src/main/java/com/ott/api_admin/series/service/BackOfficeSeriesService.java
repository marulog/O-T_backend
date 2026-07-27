package com.ott.api_admin.series.service;

import com.ott.api_admin.series.dto.request.SeriesUploadRequest;
import com.ott.api_admin.series.dto.request.SeriesUpdateRequest;
import com.ott.api_admin.series.dto.response.SeriesDetailResponse;
import com.ott.api_admin.series.dto.response.SeriesListResponse;
import com.ott.api_admin.series.dto.response.SeriesTitleListResponse;
import com.ott.api_admin.series.dto.response.SeriesUpdateResponse;
import com.ott.api_admin.series.dto.response.SeriesUploadResponse;
import com.ott.api_admin.series.mapper.BackOfficeSeriesMapper;
import com.ott.api_admin.trending.event.TrendingCacheInvalidationEvent;
import com.ott.api_admin.upload.support.MediaTagLinker;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.MediaType;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.domain.member.domain.Member;
import com.ott.domain.series.domain.Series;
import com.ott.infra.db.series.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class BackOfficeSeriesService {

    private final BackOfficeSeriesMapper backOfficeSeriesMapper;

    private final MediaRepository mediaRepository;
    private final MediaTagRepository mediaTagRepository;
    private final SeriesRepository seriesRepository;
    private final UploadHelper uploadHelper;
    private final MediaTagLinker mediaTagLinker;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResult<SeriesListResponse> getSeries(int page, int size, String searchWord) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Media> mediaPage = mediaRepository.findMediaListByMediaTypeAndSearchWord(pageable, MediaType.SERIES, searchWord);

        List<Long> mediaIdList = mediaPage.getContent().stream()
                .map(Media::getId)
                .toList();

        Map<Long, List<MediaTag>> tagListByMediaId = mediaIdList.isEmpty()
                ? Collections.emptyMap()
                : mediaTagRepository.findWithTagAndCategoryByMediaIds(mediaIdList).stream()
                .collect(Collectors.groupingBy(mt -> mt.getMedia().getId()));

        List<SeriesListResponse> responseList = mediaPage.getContent().stream()
                .map(media -> backOfficeSeriesMapper.toSeriesListResponse(
                        media,
                        tagListByMediaId.getOrDefault(media.getId(), List.of())
                ))
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(
                mediaPage.getNumber(),
                mediaPage.getTotalPages(),
                mediaPage.getSize()
        );
        return PageResult.of(pageMetadata, responseList);
    }

    @Transactional(readOnly = true)
    public PageResult<SeriesTitleListResponse> getSeriesTitle(Integer page, Integer size, String searchWord) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Series> seriesPage = seriesRepository.findSeriesListWithMediaBySearchWord(pageable, searchWord);

        List<Long> mediaIdList = seriesPage.getContent().stream()
                .map(series -> series.getMedia().getId())
                .toList();

        Map<Long, List<MediaTag>> tagListByMediaId = mediaIdList.isEmpty()
                ? Collections.emptyMap()
                : mediaTagRepository.findWithTagAndCategoryByMediaIds(mediaIdList).stream()
                .collect(Collectors.groupingBy(mt -> mt.getMedia().getId()));

        List<SeriesTitleListResponse> responseList = seriesPage.getContent().stream()
                .map(series -> backOfficeSeriesMapper.toSeriesTitleList(
                        series,
                        tagListByMediaId.getOrDefault(series.getMedia().getId(), List.of())
                ))
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(
                seriesPage.getNumber(),
                seriesPage.getTotalPages(),
                seriesPage.getSize()
        );
        return PageResult.of(pageMetadata, responseList);
    }

    @Transactional(readOnly = true)
    public SeriesDetailResponse getSeriesDetail(Long mediaId) {
        Series series = seriesRepository.findWithMediaAndUploaderByMediaId(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));

        Media media = series.getMedia();
        String uploaderNickname = media.getUploader().getNickname();

        List<MediaTag> mediaTagList = mediaTagRepository.findWithTagAndCategoryByMediaId(mediaId);

        return backOfficeSeriesMapper.toSeriesDetailResponse(series, media, uploaderNickname, mediaTagList);
    }

    @Transactional
    public SeriesUploadResponse createSeriesUpload(SeriesUploadRequest request, Long memberId) {
        Member uploader = uploadHelper.resolveUploader(memberId);

        Media media = mediaRepository.save(
                Media.builder()
                        .uploader(uploader)
                        .title(request.title())
                        .description(request.description())
                        .posterUrl("PENDING")
                        .thumbnailUrl("PENDING")
                        .bookmarkCount(0L)
                        .likesCount(0L)
                        .mediaType(MediaType.SERIES)
                        .mediaStatus(MediaStatus.INIT)
                        .publicStatus(request.publicStatus())
                        .build()
        );

        Series series = seriesRepository.save(
                Series.builder()
                        .media(media)
                        .actors(request.actors())
                        .build()
        );

        Long seriesId = series.getId();
        UploadHelper.ImageCreateUploadResult imageCreateUploadResult = uploadHelper.prepareImageCreate(
                "series", seriesId, request.posterFileName(), request.thumbnailFileName()
        );
        media.updateImageKeys(
                imageCreateUploadResult.posterObjectUrl(),
                imageCreateUploadResult.thumbnailObjectUrl()
        );
        mediaTagLinker.linkTags(media, request.categoryId(), request.tagIdList());

        return backOfficeSeriesMapper.toSeriesUploadResponse(
                seriesId,
                imageCreateUploadResult.posterObjectKey(),
                imageCreateUploadResult.thumbnailObjectKey(),
                imageCreateUploadResult.posterUploadUrl(),
                imageCreateUploadResult.thumbnailUploadUrl()
        );
    }

    @Transactional
    public SeriesUpdateResponse updateSeriesUpload(Long seriesId, SeriesUpdateRequest request) {
        Series series = seriesRepository.findWithMediaById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));

        Media media = series.getMedia();
        media.updateMetadata(request.title(), request.description(), request.publicStatus());
        series.updateActors(request.actors());

        UploadHelper.ImageUpdateUploadResult imageUpdateUploadResult = uploadHelper.prepareImageUpdate(
                "series",
                seriesId,
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

        eventPublisher.publishEvent(new TrendingCacheInvalidationEvent());

        return backOfficeSeriesMapper.toSeriesUpdateResponse(
                seriesId,
                imageUpdateUploadResult.posterObjectKey(),
                imageUpdateUploadResult.thumbnailObjectKey(),
                imageUpdateUploadResult.posterUploadUrl(),
                imageUpdateUploadResult.thumbnailUploadUrl()
        );
    }
}
