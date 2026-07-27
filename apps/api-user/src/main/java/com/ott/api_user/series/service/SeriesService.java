package com.ott.api_user.series.service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ott.domain.media.domain.MediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ott.api_user.series.dto.SeriesContentsResponse;
import com.ott.api_user.series.dto.SeriesDetailResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.category.repository.CategoryRepository;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.domain.series.domain.Series;
import com.ott.infra.db.series.repository.SeriesRepository;
import com.ott.infra.db.tag.repository.TagRepository;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 더티 체킹 비활성화
public class SeriesService {

        private final SeriesRepository seriesRepository;
        private final ContentsRepository contentsRepository;
        private final TagRepository tagRepository;
        private final CategoryRepository categoryRepository;
        private final BookmarkRepository bookmarkRepository;
        private final LikesRepository likesRepository;
        private final WatchHistoryRepository watchHistoryRepository;
        private final PlaybackRepository playbackRepository;

        // 시리즈 상세 조회
        public SeriesDetailResponse getSeriesDetail(Long mediaId, Long memberId) {

                Series series = seriesRepository.findByMediaIdAndStatusAndPublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC)
                                .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));

                List<String> tags = tagRepository.findTagNamesByMediaId(mediaId, Status.ACTIVE);
                List<String> categories = categoryRepository.findCategoryNamesByMediaId(mediaId, Status.ACTIVE);

                Boolean isBookmarked = bookmarkRepository.existsByMemberIdAndMediaIdAndStatus(memberId, mediaId,
                                Status.ACTIVE);
                Boolean isLiked = likesRepository.existsByMemberIdAndMediaIdAndStatus(memberId, mediaId, Status.ACTIVE);

                // 마지막 시청지점이 있는지 조회
                Long resumeMediaId = calculateResumeMediaId(series.getId(), memberId);

                return SeriesDetailResponse.of(series, tags, categories, isBookmarked, isLiked , resumeMediaId);
        }

        // 시리즈 콘텐츠 목록 조회 (페이징)
        // 반환 타입 제네릭으로 수정
        public PageResult<SeriesContentsResponse> getSeriesContents(Long mediaId, int page, int size,
                        Long memberId) {

                Series series = seriesRepository.findByMediaIdAndStatusAndPublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC)
                                .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));

                Long targetSeriesId = series.getId();

                Pageable pageable = PageRequest.of(page, size);


                Page<Contents> contentsPage = contentsRepository
                                .findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(targetSeriesId, Status.ACTIVE, PublicStatus.PUBLIC, MediaStatus.COMPLETED, pageable);


                // 시리즈의 에피소드들의 mediaId 추출
                List<Long> mediaIds = contentsPage.getContent().stream()
                        .map(content -> content.getMedia().getId())
                        .toList();


                // 미디어 컨텐츠들에 대한 이어보기 지점 IN 절로 한번에 조회
                final Map<Long, Integer> playbackMap = mediaIds.isEmpty() ? new HashMap<>() :
                        playbackRepository.findAllByMemberIdAndMediaIds(memberId, mediaIds).stream()
                        .collect(Collectors.toMap(
                                p -> p.getContents().getMedia().getId(),
                                p -> p.getPositionSec() != null ? p.getPositionSec() : 0,
                                (existing, replacement) -> existing // 중복 방어
                ));

                // 에피소드별로 시청 이력을 매핑하여 DTO 변환
                List<SeriesContentsResponse> contentsList = contentsPage.getContent().stream()
                        .map(content -> {
                            Integer positionSec = playbackMap.getOrDefault(content.getMedia().getId(), 0);
                            return SeriesContentsResponse.from(content, positionSec);
                        })
                        .toList();


                PageMetadata pageMetadata = PageMetadata.of(
                                contentsPage.getNumber(),
                                contentsPage.getTotalPages(),
                                contentsPage.getSize());

                return PageResult.of(pageMetadata, contentsList);
        }

        // 시청 이력에 따른 ResumeMediaId 값 조회
        private Long calculateResumeMediaId(Long seriesId, Long memberId){
                 // 해당 멤버가 이 시리즈에서 마지막으로 본 에피소드의 mediaId를 QueryDSL로 조회
                Optional<Long> lastWatchedMediaId = watchHistoryRepository.findLatestContentMediaIdByMemberIdAndSeriesId(memberId, seriesId);

                // 기록이 없으면 1화의 mediaId를 반환
                return lastWatchedMediaId.orElseGet(() -> getFirstEpisodeMediaId(seriesId));
        }



        // 에피소드의 1화 MediaId 가져오기
        private Long getFirstEpisodeMediaId(Long seriesId) {
                Pageable limitOne = PageRequest.of(0, 1);
                Page<Contents> firstContentPage = contentsRepository
                        .findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(seriesId, Status.ACTIVE, PublicStatus.PUBLIC, MediaStatus.COMPLETED, limitOne);

                if (firstContentPage.isEmpty()) {
                        throw new BusinessException(ErrorCode.EPISODE_NOT_REGISTERED);} // 1화조차 없는 경우

                return firstContentPage.getContent().get(0).getMedia().getId();
        }

}
