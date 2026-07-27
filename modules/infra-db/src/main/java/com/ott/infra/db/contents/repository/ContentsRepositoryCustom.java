package com.ott.infra.db.contents.repository;

import com.ott.domain.contents.domain.Contents;
import com.ott.domain.series.domain.Series;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContentsRepositoryCustom {

    Optional<Contents> findWithMediaById(Long contentsId);

    Optional<Contents> findWithMediaAndUploaderByMediaId(Long mediaId);

    List<Contents> findAllByMediaIdIn(List<Long> mediaIdList);

    List<Contents> findLastEpisodeBySeriesMediaIds(List<Long> seriesMediaIdList);
}
