package com.ott.infra.db.short_form.repository;

import com.ott.domain.short_form.domain.ShortForm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortFormRepositoryCustom {

    Optional<ShortForm> findWithMediaAndUploaderByMediaId(Long mediaId);
    Optional<ShortForm> findWithMediaAndUploaderByShortFormId(Long shortFormId);

    List<ShortForm> findAllByMediaIdIn(List<Long> mediaIdList);

    // 유저의 취향(태그 점수)을 반영하여 숏폼을 추천해주는 메서드
    List<ShortForm> findRecommendedShortForms(Map<Long, Integer> tagScores, int limit, long offset);

    // 추천된 숏폼을 제외하고 최신순으로 숏폼을 가져오는 메서드
    List<ShortForm> findLatestShortForms(int limit, long offset, List<Long> excludeIds);
}
