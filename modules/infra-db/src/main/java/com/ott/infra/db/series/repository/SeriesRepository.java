package com.ott.infra.db.series.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.series.domain.Series;

public interface SeriesRepository extends JpaRepository<Series, Long>, SeriesRepositoryCustom {

        // 미디어 Id 로 해당 시리즈 조회
        @Query("SELECT s FROM Series s JOIN FETCH s.media m WHERE m.id = :mediaId AND s.status = :status AND m.publicStatus = :publicStatus AND m.mediaStatus = com.ott.domain.media.domain.MediaStatus.COMPLETED")
        Optional<Series> findByMediaIdAndStatusAndPublicStatus(
                        @Param("mediaId") Long mediaId,
                        @Param("status") Status status,
                        @Param("publicStatus") PublicStatus publicStatus);

}
