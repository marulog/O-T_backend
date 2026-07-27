package com.ott.api_user.radar_preference.service;

import com.ott.api_user.radar_preference.dto.request.RadarPreferenceRequest;
import com.ott.api_user.radar_preference.dto.response.RadarMediaResponse;
import com.ott.api_user.radar_preference.dto.response.RadarPreferenceResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.MediaType;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media_metrics.repository.MediaMetricsRepository;
import com.ott.domain.member_radar_preference.domain.MemberRadarPreference;
import com.ott.infra.db.member_radar_preference.repository.MemberRadarPreferenceRepository;
import com.ott.domain.playback.domain.Playback;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ott.api_user.radar_preference.constant.RadarPreferenceConstant.RECOMMEND_LIMIT;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RadarPreferenceService {

    private final MediaMetricsRepository mediaMetricsRepository;
    private final MemberRadarPreferenceRepository memberRadarPreferenceRepository;
    private final ContentsRepository contentsRepository;
    private final PlaybackRepository playbackRepository;

    public RadarPreferenceResponse getPreference(Long memberId) {
        MemberRadarPreference preference = memberRadarPreferenceRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RADAR_PREFERENCE_NOT_FOUND));

        return RadarPreferenceResponse.from(preference);
    }

    @Transactional
    public void updatePreference(Long memberId, RadarPreferenceRequest request) {
        MemberRadarPreference preference = memberRadarPreferenceRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RADAR_PREFERENCE_NOT_FOUND));

        int total = request.getPopularity()
                + request.getImmersion()
                + request.getMania()
                + request.getRecency()
                + request.getReWatch();

        if (total != 100) {
            throw new BusinessException(ErrorCode.RADAR_PREFERENCE_UNMODIFIABLE);
        }

        preference.updatePreference(
                request.getPopularity(),
                request.getImmersion(),
                request.getMania(),
                request.getRecency(),
                request.getReWatch()
        );
    }

    public PageResult<RadarMediaResponse> getRecommendations(Long memberId, Long excludeMediaId) {
        MemberRadarPreference memberRadarPreference = memberRadarPreferenceRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RADAR_PREFERENCE_NOT_FOUND));

        int popularity = memberRadarPreference.getPopularity();
        int immersion = memberRadarPreference.getImmersion();
        int mania = memberRadarPreference.getMania();
        int recency = memberRadarPreference.getRecency();
        int reWatch = memberRadarPreference.getReWatch();

        if (needDefaultWeight(popularity, immersion, mania, recency, reWatch)) {
            return PageResult.of(PageMetadata.of(0, 0, 0), List.of());
        }

        List<Media> mediaList = mediaMetricsRepository.findTopByWeightedScore(
                popularity, immersion, mania, recency, reWatch, excludeMediaId, RECOMMEND_LIMIT
        );

        // CONTENTS / SERIES mediaId 분류
        List<Long> contentsMediaIdList = mediaList.stream()
                .filter(m -> m.getMediaType() == MediaType.CONTENTS)
                .map(Media::getId)
                .toList();

        List<Long> seriesMediaIdList = mediaList.stream()
                .filter(m -> m.getMediaType() == MediaType.SERIES)
                .map(Media::getId)
                .toList();

        // CONTENTS — duration, positionSec
        Map<Long, Integer> durationMap = contentsMediaIdList.isEmpty() ? Map.of() :
                contentsRepository.findAllByMediaIdIn(contentsMediaIdList).stream()
                        .collect(Collectors.toMap(
                                c -> c.getMedia().getId(),
                                c -> c.getDuration() != null ? c.getDuration() : 0,
                                (a, b) -> a));

        Map<Long, Integer> positionMap = contentsMediaIdList.isEmpty() ? Map.of() :
                playbackRepository.findAllByMemberIdAndMediaIds(memberId, contentsMediaIdList).stream()
                        .collect(Collectors.toMap(
                                p -> p.getContents().getMedia().getId(),
                                p -> p.getPositionSec() != null ? p.getPositionSec() : 0,
                                (a, b) -> a));

        // SERIES — 최근 시청 에피소드의 duration, positionSec
        List<Playback> latestPlaybackList = seriesMediaIdList.isEmpty() ? List.of() :
                playbackRepository.findLatestByMemberAndSeriesMediaIds(memberId, seriesMediaIdList);

        Map<Long, Integer> seriesDurationMap = latestPlaybackList.stream()
                .collect(Collectors.toMap(
                        p -> p.getContents().getSeries().getMedia().getId(),
                        p -> p.getContents().getDuration() != null ? p.getContents().getDuration() : 0,
                        (a, b) -> a));

        Map<Long, Integer> seriesPositionMap = latestPlaybackList.stream()
                .collect(Collectors.toMap(
                        p -> p.getContents().getSeries().getMedia().getId(),
                        p -> p.getPositionSec() != null ? p.getPositionSec() : 0,
                        (a, b) -> a));

        List<RadarMediaResponse> contentList = mediaList.stream()
                .map(media -> {
                    int duration;
                    int positionSec;
                    if (media.getMediaType() == MediaType.SERIES) {
                        duration = seriesDurationMap.getOrDefault(media.getId(), 0);
                        positionSec = seriesPositionMap.getOrDefault(media.getId(), 0);
                    } else {
                        duration = durationMap.getOrDefault(media.getId(), 0);
                        positionSec = positionMap.getOrDefault(media.getId(), 0);
                    }
                    return RadarMediaResponse.from(media, duration, positionSec);
                })
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(0, 1, contentList.size());
        return PageResult.of(pageMetadata, contentList);
    }

    boolean needDefaultWeight(int popularity, int immersion, int mania, int recency, int reWatch) {
        return popularity == 0 && immersion == 0 && mania == 0 && recency == 0 && reWatch == 0;
    }
}
