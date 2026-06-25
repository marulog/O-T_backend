package com.ott.api_user.playlist.service;

import com.ott.api_user.playlist.dto.response.PlaylistResponse;
import com.ott.api_user.playlist.dto.response.TrendingCache;
import com.ott.domain.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrendingCacheService {

    public static final String CACHE_NAME = "trending";
    public static final String CACHE_KEY = "'all'";
    private static final int PRELOAD_SIZE = 100;

    private final MediaRepository mediaRepository;
    private final ContentsRepository contentsRepository;


    @Cacheable(cacheNames = CACHE_NAME, key = CACHE_KEY)
    @Transactional(readOnly = true)
    public TrendingCache getTrending() {
        return new TrendingCache(loadFromDb());
    }

    @CachePut(cacheNames = CACHE_NAME, key = CACHE_KEY) // 반환 값을 캐시에 덮어쓰기, 해당 메소드 반드시 실행
    @Scheduled(fixedRate = 45 * 60 * 1000, initialDelay = 0) // 45분 주기, 부팅 직후 1회 실행 -> COLD START 해결
//    @Scheduled(fixedRate = 60 * 1000, initialDelay = 0)
    @SchedulerLock(name = "refreshTrending", lockAtMostFor = "PT40M", lockAtLeastFor = "PT1M")  // ← 추가
    @Transactional(readOnly = true)
    public TrendingCache refreshTrending() {
        return new TrendingCache(loadFromDb());
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = CACHE_KEY)
    public void evictTrending() {
        // 어노테이션이 처리. 본문 비움.
    }


    private List<PlaylistResponse> loadFromDb() {
        // mediaType=null → 현재 trending API와 동일하게 통합 조회
        List<Media> medias = mediaRepository.findTrendingPlaylists(
                null, null, PageRequest.of(0, PRELOAD_SIZE)
        ).getContent();

        if (medias.isEmpty()) return List.of();

        List<Long> mediaIds = medias.stream().map(Media::getId).toList();
        Map<Long, Integer> durationMap = contentsRepository.findAllByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(
                        c -> c.getMedia().getId(),
                        c -> c.getDuration() != null ? c.getDuration() : 0,
                        (a, b) -> a));

        return medias.stream()
                .map(m -> PlaylistResponse.from(m, durationMap.getOrDefault(m.getId(), 0), 0))
                .toList();
    }
}
