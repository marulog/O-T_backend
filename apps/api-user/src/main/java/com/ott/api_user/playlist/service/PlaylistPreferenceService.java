package com.ott.api_user.playlist.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ott.domain.common.Status;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.infra.db.preferred_tag.repository.PreferredTagRepository;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.tag.repository.TagRepository;

import lombok.RequiredArgsConstructor;


// 유저의 행동(선호태그, 시청 - 태그 , 선호 태그)를 수집하여
// Top3 태그와 oo 님이 좋아하실만한 콘텐츠
// 종합 점수표 계산
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistPreferenceService {

    private final PreferredTagRepository preferredTagRepository;
    private final PlaybackRepository playbackRepository;
    private final LikesRepository likesRepository;
    private final TagRepository tagRepository;
    private final MediaTagRepository mediaTagRepository;

    /*
     * [TAG 전략용] 시청 이력(+3) + 선호 태그(+5) 점수만 합산하여 Top 3 태그 추출
     */
    public List<Tag> getTopTags(Long memberId){
        // 태그별 합산 점수를 담을 점수표
        Map<Long, Integer> tagScores = new HashMap<>();

        // 최근 100개까지만 가져옴
        Pageable limit100 = PageRequest.of(0, 100);


        // 1. 온보딩 선호 태그 가중치 반영 (+5점)
        // Map.merge 를 통해 누적 점수 계산
        preferredTagRepository.findTagIdsByMemberId(memberId, Status.ACTIVE)
                .forEach(id -> tagScores.merge(id, 5, Integer::sum));


        // 2. 최근 시청 이력 가중치 반영 (+3점)
         // [1단계] 최근 시청한 영상에 대해 '영상 ID' 최대 100개를 가져옴
        List<Long> playedMediaIds = playbackRepository.findRecentPlayedMediaIds(memberId, Status.ACTIVE, limit100);

        // [2단계] 가져온 영상이 하나라도 있다면, 그 영상들의 '태그 ID'를 한 번에 가져와 점수 부여
        if (!playedMediaIds.isEmpty()) {
                mediaTagRepository.findTagIdsByMediaIds(playedMediaIds)
                        .forEach(id -> tagScores.merge(id, 3, Integer::sum)); // 혹은 totalScores.merge
        }

        // 3. 점수가 가장 높은 순(내림차순)으로 정렬한 뒤, 상위 3개의 태그 ID만 추출
        List<Long> topTagIds = tagScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // [Fallback 처리] 정보가 아예 없는 신규 유저라면? -> 시스템 전체 태그 중 무작위 3개를 던져줌
        if (topTagIds.isEmpty()) {
            List<Tag> allTags = new ArrayList<>(tagRepository.findAll());
            Collections.shuffle(allTags);
            return allTags.stream().limit(3).collect(Collectors.toList());
        }

        // 최종적으로 추출된 3개의 ID로 실제 Tag 엔티티들을 DB에서 가져와 반환
        // findAllById (In 절은 순서 보장 x 한번 더 TopTagIds 의 인덱스 순서에 맞게 정렬해주어야함)
        List<Tag> tags = new ArrayList<>(tagRepository.findAllById(topTagIds));
        tags.sort(Comparator.comparing(tag -> topTagIds.indexOf(tag.getId())));

        return tags;

    }


    /**
     * [RECOMMEND 전략용]
     * 선호 태그(+5) + 시청 이력(+3) + 좋아요(+2) -  종합 점수표 반환
     */
    public Map<Long, Integer> getTotalTagScores(Long memberId) {
        Map<Long, Integer> totalScores = new HashMap<>();
        Pageable limit100 = PageRequest.of(0, 100);

        // 1. 고정 취향: 온보딩 선호 태그 (+5점)
        preferredTagRepository.findTagIdsByMemberId(memberId, Status.ACTIVE)
                .forEach(id -> totalScores.merge(id, 5, Integer::sum));

        // 2. 최근 관심사: 최근 시청 이력 (+3점)
        List<Long> playedMediaIds = playbackRepository.findRecentPlayedMediaIds(memberId, Status.ACTIVE, limit100);

        if (!playedMediaIds.isEmpty()) {
                mediaTagRepository.findTagIdsByMediaIds(playedMediaIds)
                .forEach(id -> totalScores.merge(id, 3, Integer::sum));
        }

        // 3. 강한 선호도: 최근 좋아요 누른 이력 (+2점)
        // [1단계] 최근 좋아요 누른 '영상 ID' 최대 100개를 가져옴
        List<Long> likedMediaIds = likesRepository.findRecentLikedMediaIds(memberId, Status.ACTIVE, limit100);

        // [2단계] 가져온 영상이 하나라도 있다면, 그 영상들의 '태그 ID'를 한 번에 가져와 점수 부여
        if (!likedMediaIds.isEmpty()) {
                mediaTagRepository.findTagIdsByMediaIds(likedMediaIds)
                        .forEach(id -> totalScores.merge(id, 2, Integer::sum));
        }

        // 만들어진 유저의 최종 점수를 반환
        return totalScores;
    }

}
