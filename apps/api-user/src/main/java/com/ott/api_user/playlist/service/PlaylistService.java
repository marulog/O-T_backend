package com.ott.api_user.playlist.service;

import java.util.List;

import com.ott.api_user.playlist.dto.response.RecentWatchResponse;
import com.ott.api_user.playlist.dto.response.TagPlaylistResponse;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.tag.repository.TagRepository;
import com.ott.infra.db.watch_history.repository.RecentWatchProjection;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.infra.db.contents.repository.ContentsRepository;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final ContentsRepository contentsRepository;
    private final MemberRepository memberRepository;
    private final TagRepository tagRepository;
    private final MediaRepository mediaRepository;
    private final WatchHistoryRepository watchHistoryRepository;


    // 태그별 추천 콘텐츠 목록 조회 (최대 20개)
    @Transactional(readOnly = true)
    public List<TagPlaylistResponse> getRecommendContentsByTag(Long memberId, Long tagId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        return mediaRepository.findRecommendContentsByTagId(tagId, 20)
                .stream()
                .map(TagPlaylistResponse::from)
                .toList();
    }

    // 전체 시청이력 플레이리스트 페이징 조회 (최신순, 10개씩)
    @Transactional(readOnly = true)
    public PageResult<RecentWatchResponse> getWatchHistoryPlaylist(Long memberId, Integer page) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PageRequest pageable = PageRequest.of(page, 10);

        Page<RecentWatchProjection> watchPage =
                watchHistoryRepository.findWatchHistoryByMemberId(memberId, pageable);

        List<RecentWatchResponse> dataList = watchPage.getContent()
                .stream()
                .map(RecentWatchResponse::from)
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(
                watchPage.getNumber(),
                watchPage.getTotalPages(),
                watchPage.getSize()
        );

        return PageResult.of(pageMetadata, dataList);
    }
}
