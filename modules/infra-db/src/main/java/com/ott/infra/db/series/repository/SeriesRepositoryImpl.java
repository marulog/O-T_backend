package com.ott.infra.db.series.repository;

import com.ott.domain.series.domain.Series;
import com.ott.domain.media.domain.MediaStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.member.domain.QMember.member;
import static com.ott.domain.series.domain.QSeries.series;

@RequiredArgsConstructor
public class SeriesRepositoryImpl implements SeriesRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Series> findWithMediaById(Long seriesId) {
        Series result = queryFactory
                .selectFrom(series)
                .join(series.media, media).fetchJoin()
                .where(series.id.eq(seriesId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Series> findWithMediaAndUploaderByMediaId(Long mediaId) {
        Series result = queryFactory
                .selectFrom(series)
                .join(series.media, media).fetchJoin()
                .join(media.uploader, member).fetchJoin()
                .where(
                        media.id.eq(mediaId),
                        media.mediaStatus.eq(MediaStatus.COMPLETED))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Page<Series> findSeriesListWithMediaBySearchWord(Pageable pageable, String searchWord) {
        List<Series> seriesList = queryFactory
                .selectFrom(series)
                .join(series.media, media).fetchJoin()
                .where(
                        titleContains(searchWord),
                        media.mediaStatus.eq(MediaStatus.COMPLETED)
                )
                .orderBy(series.createdDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(series.count())
                .from(series)
                .join(series.media, media)
                .where(titleContains(searchWord),
                        media.mediaStatus.eq(MediaStatus.COMPLETED)
                );

        return PageableExecutionUtils.getPage(seriesList, pageable, countQuery::fetchOne);
    }

    @Override
    public List<Series> findAllByMediaIdIn(List<Long> mediaIdList) {
        return queryFactory
                .selectFrom(series)
                .where(
                        series.media.id.in(mediaIdList),
                        media.mediaStatus.eq(MediaStatus.COMPLETED))
                .fetch();
    }

    private BooleanExpression titleContains(String searchWord) {
        if (StringUtils.hasText(searchWord))
            return media.title.contains(searchWord);
        return null;
    }
}
