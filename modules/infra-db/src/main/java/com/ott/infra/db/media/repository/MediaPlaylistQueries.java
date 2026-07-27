package com.ott.infra.db.media.repository;

import static com.ott.domain.bookmark.domain.QBookmark.bookmark;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.media_tag.domain.QMediaTag.mediaTag;
import static com.ott.domain.playback.domain.QPlayback.playback;
import static com.ott.domain.series.domain.QSeries.series;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.excludeId;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.isActiveAndPublic;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.isDisplayable;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.mediaTypeEq;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
final class MediaPlaylistQueries {

        private final JPAQueryFactory queryFactory;

        Page<Media> findTrendingPlaylists(MediaType mediaType, Long excludeMediaId, Pageable pageable) {
                List<Media> content = queryFactory
                                .selectFrom(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                excludeId(excludeMediaId))
                                .orderBy(media.bookmarkCount.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.count())
                                .from(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                excludeId(excludeMediaId));

                return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
        }

        Page<Media> findHistoryPlaylists(Long memberId, MediaType mediaType, Long excludeMediaId, Pageable pageable) {
                List<Media> contentList = queryFactory
                                .select(media)
                                .from(playback)
                                .join(playback.contents, contents)
                                .leftJoin(contents.series, series)
                                .join(media).on(
                                                series.isNull().and(media.id.eq(contents.media.id))
                                                                .or(series.isNotNull()
                                                                                .and(media.id.eq(series.media.id))))
                                .where(
                                                playback.member.id.eq(memberId),
                                                mediaTypeEq(mediaType),
                                                isActiveAndPublic(),
                                                excludeId(excludeMediaId))
                                .groupBy(media.id)
                                .orderBy(playback.modifiedDate.max().desc(), media.id.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.id.countDistinct())
                                .from(playback)
                                .join(playback.contents, contents)
                                .leftJoin(contents.series, series)
                                .join(media).on(
                                                series.isNull().and(media.id.eq(contents.media.id))
                                                                .or(series.isNotNull()
                                                                                .and(media.id.eq(series.media.id))))
                                .where(
                                                playback.member.id.eq(memberId),
                                                mediaTypeEq(mediaType),
                                                isActiveAndPublic(),
                                                excludeId(excludeMediaId));

                return PageableExecutionUtils.getPage(contentList, pageable, countQuery::fetchOne);
        }

        Page<Media> findBookmarkedPlaylists(Long memberId, MediaType mediaType, Long excludeMediaId,
                        Pageable pageable) {
                List<Media> content = queryFactory
                                .select(media)
                                .from(bookmark)
                                .join(bookmark.media, media)
                                .where(
                                                bookmark.member.id.eq(memberId),
                                                mediaTypeEq(mediaType),
                                                bookmark.status.eq(Status.ACTIVE),
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                excludeId(excludeMediaId))
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
                                                mediaTypeEq(mediaType),
                                                bookmark.status.eq(Status.ACTIVE),
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                excludeId(excludeMediaId));

                return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
        }

        Page<Media> findPlaylistsByTag(Long tagId, MediaType mediaType, Long excludeMediaId, Pageable pageable) {
                List<Media> content = queryFactory
                                .select(media)
                                .from(mediaTag)
                                .join(mediaTag.media, media)
                                .where(
                                                mediaTag.tag.id.eq(tagId),
                                                mediaTypeEq(mediaType),
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                excludeId(excludeMediaId))
                                .orderBy(media.createdDate.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(mediaTag.count())
                                .from(mediaTag)
                                .join(mediaTag.media, media)
                                .where(
                                                mediaTag.tag.id.eq(tagId),
                                                mediaTypeEq(mediaType),
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                excludeId(excludeMediaId));

                return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
        }

        List<Media> findMediasByTagId(Long tagId, MediaType mediaType, Long excludeMediaId, int limit, long offset) {
                return queryFactory.selectFrom(media)
                                .join(mediaTag).on(mediaTag.media.id.eq(media.id))
                                .where(
                                                mediaTag.tag.id.eq(tagId),
                                                isActiveAndPublic(),
                                                mediaTypeEq(mediaType),
                                                isDisplayable(),
                                                excludeMediaId != null ? media.id.ne(excludeMediaId) : null)
                                .orderBy(media.id.desc())
                                .limit(limit)
                                .offset(offset)
                                .fetch();
        }
}
