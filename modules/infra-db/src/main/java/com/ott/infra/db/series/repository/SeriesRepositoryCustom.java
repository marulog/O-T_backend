package com.ott.infra.db.series.repository;

import com.ott.domain.series.domain.Series;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SeriesRepositoryCustom {

    Page<Series> findSeriesListWithMediaBySearchWord(Pageable pageable, String searchWord);

    Optional<Series> findWithMediaById(Long seriesId);

    Optional<Series> findWithMediaAndUploaderByMediaId(Long mediaId);

    List<Series> findAllByMediaIdIn(List<Long> mediaIdList);
}
