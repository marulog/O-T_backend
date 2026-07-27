package com.ott.infra.db.media.repository;

import static com.ott.domain.common.MediaType.CONTENTS;
import static com.ott.domain.common.MediaType.SERIES;
import static com.ott.domain.common.PublicStatus.PUBLIC;
import static com.ott.domain.common.Status.ACTIVE;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.media_tag.domain.QMediaTag.mediaTag;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.excludeId;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.isActiveAndPublic;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.isDisplayable;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.mediaStatusEq;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.mediaTypeEq;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MediaRecommendationQueries {

        private final JPAQueryFactory queryFactory;

        List<Media> findMediasByTagId(Long tagId, Long excludeMediaId, int limit, long offset) {
                return queryFactory.selectFrom(media)
                                .join(mediaTag).on(mediaTag.media.id.eq(media.id))
                                .where(
                                                media.status.eq(Status.ACTIVE),
                                                media.publicStatus.eq(PublicStatus.PUBLIC),
                                                mediaTag.tag.id.eq(tagId),
                                                mediaStatusEq(MediaStatus.COMPLETED),
                                                excludeMediaId != null ? media.id.ne(excludeMediaId) : null)
                                .orderBy(media.id.desc())
                                .limit(limit)
                                .offset(offset)
                                .fetch();
        }

        List<TagContentProjection> findRecommendContentsByTagId(Long tagId, int limit) {
                return queryFactory
                                .select(Projections.constructor(TagContentProjection.class,
                                                media.id,
                                                media.posterUrl,
                                                media.mediaType))
                                .from(media)
                                .join(mediaTag).on(
                                                mediaTag.media.id.eq(media.id),
                                                mediaTag.tag.id.eq(tagId),
                                                mediaTag.status.eq(ACTIVE))
                                .leftJoin(contents).on(
                                                contents.media.id.eq(media.id),
                                                contents.series.isNull())
                                .where(
                                                media.status.eq(ACTIVE),
                                                media.publicStatus.eq(PUBLIC),
                                                mediaStatusEq(MediaStatus.COMPLETED),
                                                media.mediaType.eq(SERIES)
                                                                .or(media.mediaType.eq(CONTENTS)
                                                                                .and(contents.id.isNotNull())))
                                .orderBy(media.bookmarkCount.desc())
                                .limit(limit)
                                .fetch();
        }

        List<Media> findRecommendedMedias(Map<Long, Integer> tagScores, MediaType mediaType, Long excludeMediaId,
                        int limit, long offset) {
                if (tagScores.isEmpty()) {
                        return queryFactory.selectFrom(media)
                                        .where(
                                                        isActiveAndPublic(),
                                                        mediaTypeEq(mediaType),
                                                        isDisplayable(),
                                                        excludeId(excludeMediaId))
                                        .orderBy(media.id.desc())
                                        .limit(limit)
                                        .offset(offset)
                                        .fetch();
                }

                NumberExpression<Integer> scoreExpression = new CaseBuilder()
                                .when(mediaTag.tag.id.isNotNull()).then(0).otherwise(0);

                for (Map.Entry<Long, Integer> entry : tagScores.entrySet()) {
                        scoreExpression = scoreExpression.add(
                                        new CaseBuilder()
                                                        .when(mediaTag.tag.id.eq(entry.getKey())).then(entry.getValue())
                                                        .otherwise(0));
                }

                return queryFactory.selectFrom(media)
                                .join(mediaTag).on(mediaTag.media.id.eq(media.id))
                                .where(
                                                isActiveAndPublic(),
                                                mediaTypeEq(mediaType),
                                                isDisplayable(),
                                                excludeId(excludeMediaId))
                                .groupBy(media.id)
                                .orderBy(scoreExpression.sum().desc(), media.id.desc())
                                .limit(limit)
                                .offset(offset)
                                .fetch();
        }
}
