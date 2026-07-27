package com.ott.infra.db.bookmark.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.ott.domain.bookmark.domain.QBookmark.bookmark;
import static com.ott.domain.common.MediaType.CONTENTS;
import static com.ott.domain.common.MediaType.SERIES;
import static com.ott.domain.common.Status.ACTIVE;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.playback.domain.QPlayback.playback;

@RequiredArgsConstructor
public class BookmarkRepositoryImpl implements BookmarkRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 북마크 콘텐츠/시리즈 목록 조회
     * - CONTENTS: playback LEFT JOIN → positionSec(없으면 0), contents LEFT JOIN → duration
     * - SERIES: positionSec = null, duration = null
     */
    @Override
    public Page<BookmarkMediaProjection> findBookmarkMediaList(Long memberId, Pageable pageable) {

        List<BookmarkMediaProjection> content = queryFactory
                .select(Projections.constructor(BookmarkMediaProjection.class,
                        media.id,
                        media.mediaType,
                        media.title,
                        media.description,
                        media.posterUrl,
                        media.mediaType.when(CONTENTS).then(playback.positionSec.coalesce(0)).otherwise(Expressions.nullExpression(Integer.class)), // SERIES는 null, CONTENTS만 playback 없으면 0
                        contents.duration                  // SERIES면 null (LEFT JOIN 미매칭)
                ))
                .from(bookmark)
                .join(bookmark.media, media)
                // CONTENTS 타입일 때만 contents, playback 매칭됨
                .leftJoin(contents).on(
                        contents.media.id.eq(media.id)
                )
                .leftJoin(playback).on(
                        playback.contents.id.eq(contents.id)
                                .and(playback.member.id.eq(memberId))
                                .and(playback.status.eq(ACTIVE))
                )
                .where(
                        bookmark.member.id.eq(memberId),
                        bookmark.status.eq(ACTIVE),
                        media.mediaType.in(CONTENTS, SERIES)
                )
                .orderBy(bookmark.createdDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(bookmark.count())
                .from(bookmark)
                .join(bookmark.media, media)
                .where(
                        bookmark.member.id.eq(memberId),
                        bookmark.status.eq(ACTIVE),
                        media.mediaType.in(CONTENTS, SERIES)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
