package com.ott.infra.db.media_metrics.repository;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.media_metrics.domain.QMediaMetrics.mediaMetrics;

@RequiredArgsConstructor
public class MediaMetricsRepositoryImpl implements MediaMetricsRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Media> findTopByWeightedScore(
            int popularity, int immersion, int mania, int recency, int reWatch, Long excludeMediaId, int limit
    ) {
        NumberExpression<BigDecimal> weightedScore =
                mediaMetrics.popularity.multiply(popularity)
                        .add(mediaMetrics.immersion.multiply(immersion))
                        .add(mediaMetrics.mania.multiply(mania))
                        .add(mediaMetrics.recency.multiply(recency))
                        .add(mediaMetrics.reWatch.multiply(reWatch));

        return queryFactory
                .select(media)
                .from(mediaMetrics)
                .join(mediaMetrics.media, media)
                .where(
                        media.status.eq(Status.ACTIVE),
                        media.publicStatus.eq(PublicStatus.PUBLIC),
                        media.mediaStatus.eq(MediaStatus.COMPLETED), // 유저에게 보여주는 추천 쿼리
                        media.mediaType.eq(MediaType.SERIES)
                                .or(media.mediaType.eq(MediaType.CONTENTS)
                                        .and(JPAExpressions.selectOne()
                                                .from(contents)
                                                .where(contents.media.id.eq(media.id)
                                                        .and(contents.series.isNotNull()))
                                                .notExists())),
                        excludeMediaIdEq(excludeMediaId)
                )
                .orderBy(weightedScore.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression excludeMediaIdEq(Long excludeMediaId) {
        return excludeMediaId != null ? media.id.ne(excludeMediaId) : null;
    }
}
