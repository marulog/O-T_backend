package com.ott.api_user.shortform.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ott.api_user.playlist.service.PlaylistPreferenceService;
import com.ott.api_user.shortform.dto.response.ShortFormFeedResponse;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.click_event.repository.ClickRepository;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.short_form.repository.ShortFormRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortFormFeedService {
    private final ShortFormRepository shortFormRepository;
    private final LikesRepository likesRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PlaylistPreferenceService playlistPreferenceService;

    public PageResult<ShortFormFeedResponse> getShortFormFeed(Long memberId, int page, int size){


        int recommendLimit = (int) (size * 0.7); // ex) 7개 - 사용자 기반 추천 콘텐츠
        int latestLimit = size - recommendLimit; // ex) 3개 - 완전 새로운 것 (최신성)

        // 무한 스와이프를 위한 독립적인 Offset 계산
        long recommendOffset = (long) page * recommendLimit;
        long latestOffset = (long) page * latestLimit;


        // 사용자의 취향을 기반으로 tag 점수 가져오기
        Map<Long, Integer> tagScores = playlistPreferenceService.getTotalTagScores(memberId);

        // 태그 점수와 추천 개수로 숏폼 가져오기
        // 무한 페이징을 위해 DB 단에서 가중치 조인 후 정확히 잘라오기
        List<ShortForm> recommendList = shortFormRepository.findRecommendedShortForms(
                tagScores, recommendLimit, recommendOffset
        );

        // 숏폼 리스트에서 Id 만 꺼낸 리스트
        List<Long> recommendIdList = recommendList.stream()
                                .map(sf -> sf.getMedia().getId())
                                .toList();


        // 위 숏폼에서 추천된 리스트는 제외하고 최신순 숏폼 가져오기
        List<ShortForm> latestList = shortFormRepository.findLatestShortForms(
            latestLimit, latestOffset, recommendIdList
        );


        List<ShortForm> combinedList = new ArrayList<>(recommendList);
        combinedList.addAll(latestList);
        Collections.shuffle(combinedList); // 무작위 셔플


        // 최종 노출될 숏폼
        List<Long> finalmediaIdList = combinedList.stream()
                        .map(sf -> sf.getMedia().getId())
                        .distinct() // 중복 방지
                        .toList();

        // 사용자의 좋아요, 북마크 여부
        Set<Long> likedMediaIds = finalmediaIdList.isEmpty() ? Collections.emptySet() :
                likesRepository.findLikedMediaIds(memberId, finalmediaIdList);

        Set<Long> bookmarkedMediaIds = finalmediaIdList.isEmpty() ? Collections.emptySet() :
                bookmarkRepository.findBookmarkedMediaIds(memberId, finalmediaIdList);


        List<ShortFormFeedResponse> responseList = combinedList.stream()
                .map(sf -> ShortFormFeedResponse.of(
                        sf,
                        bookmarkedMediaIds.contains(sf.getMedia().getId()),
                        likedMediaIds.contains(sf.getMedia().getId())
                ))
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(page, 0, size);

        return PageResult.of(pageMetadata, responseList);
    }
}
