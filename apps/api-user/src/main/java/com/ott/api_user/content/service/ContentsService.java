package com.ott.api_user.content.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.ott.api_user.content.dto.ContentsDetailResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.category.repository.CategoryRepository;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.domain.playback.domain.Playback;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.infra.db.tag.repository.TagRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentsService {
        private final ContentsRepository contentsRepository;
        // private final PlaybackRepository playbackRepository;

        private final BookmarkRepository bookmarkRepository;
        private final LikesRepository likesRepository;
        private final TagRepository tagRepository;
        private final CategoryRepository categoryRepository;
        private final PlaybackRepository playbackRepository;


        // 재생 상세
        public ContentsDetailResponse getContentDetail(Long mediaId, Long memberId) {

                Contents contents = contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC)
                                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

                // 북마크, 좋아요 종속을 위한 컨텐츠의 본체 MediaID 찾기 (단편이라면 본인 MediaId 그대로 반환)
                Long targetMediaId = mediaId;

                if (contents.getSeries() != null) {
                        if (contents.getSeries().getMedia() == null) {
                                // 시리즈 데이터는 있는데 부모 미디어가 없을 떄 - 데이터 정합성 오류 보장
                                throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
                        }
                        targetMediaId = contents.getSeries().getMedia().getId();
                }


                List<String> tags = tagRepository.findTagNamesByMediaId(mediaId, Status.ACTIVE);
                List<String> categories = categoryRepository.findCategoryNamesByMediaId(mediaId, Status.ACTIVE);

                Boolean isBookmarked = bookmarkRepository.existsByMemberIdAndMediaIdAndStatus(memberId, targetMediaId, Status.ACTIVE);
                Boolean isLiked = likesRepository.existsByMemberIdAndMediaIdAndStatus(memberId, targetMediaId, Status.ACTIVE);

                //  이어보기 지점 조회
                Integer positionSec = playbackRepository.findByMemberIdAndMediaId(memberId, mediaId)
                                .map(Playback::getPositionSec)
                                .orElse(0); // 기록 없으면 0초부터

                String masterPlaylistUrl = contents.getMasterPlaylistUrl();



                return ContentsDetailResponse.from(contents, tags, categories, isBookmarked, isLiked,masterPlaylistUrl, positionSec);
        }
}
