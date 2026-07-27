package com.ott.infra.db.media.repository;

import static com.ott.domain.common.MediaType.CONTENTS;
import static com.ott.domain.common.MediaType.SERIES;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.mediaStatusEq;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.mediaTypeEq;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.publicStatusEq;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.titleContains;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.uploaderIdEq;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
final class AdminMediaQueries {

        private final JPAQueryFactory queryFactory;

        Page<Media> findMediaListByMediaTypeAndSearchWord(Pageable pageable, MediaType mediaType,
                        String searchWord) {
                List<Media> mediaList = queryFactory
                                .selectFrom(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                titleContains(searchWord),
                                                mediaStatusEq(MediaStatus.COMPLETED))
                                .orderBy(media.createdDate.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.count())
                                .from(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                titleContains(searchWord),
                                                mediaStatusEq(MediaStatus.COMPLETED));

                return PageableExecutionUtils.getPage(mediaList, pageable, countQuery::fetchOne);
        }

        Page<Media> findMediaListByMediaTypeAndSearchWordAndPublicStatus(Pageable pageable, MediaType mediaType,
                        String searchWord, PublicStatus publicStatus) {
                List<Media> mediaList = queryFactory
                                .selectFrom(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                titleContains(searchWord),
                                                publicStatusEq(publicStatus),
                                                mediaStatusEq(MediaStatus.COMPLETED))
                                .orderBy(media.createdDate.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.count())
                                .from(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                titleContains(searchWord),
                                                publicStatusEq(publicStatus),
                                                mediaStatusEq(MediaStatus.COMPLETED));

                return PageableExecutionUtils.getPage(mediaList, pageable, countQuery::fetchOne);
        }

        Page<Media> findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(Pageable pageable,
                        MediaType mediaType, String searchWord, PublicStatus publicStatus, Long uploaderId) {
                List<Media> mediaList = queryFactory
                                .selectFrom(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                titleContains(searchWord),
                                                publicStatusEq(publicStatus),
                                                uploaderIdEq(uploaderId),
                                                mediaStatusEq(MediaStatus.COMPLETED))
                                .orderBy(media.createdDate.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.count())
                                .from(media)
                                .where(
                                                mediaTypeEq(mediaType),
                                                titleContains(searchWord),
                                                publicStatusEq(publicStatus),
                                                uploaderIdEq(uploaderId),
                                                mediaStatusEq(MediaStatus.COMPLETED));

                return PageableExecutionUtils.getPage(mediaList, pageable, countQuery::fetchOne);
        }

        Page<Media> findOriginMediaListBySearchWord(Pageable pageable, String searchWord) {
                BooleanExpression condition = media.mediaType.in(List.of(SERIES, CONTENTS))
                                .and(
                                                JPAExpressions.selectOne()
                                                                .from(contents)
                                                                .where(
                                                                                contents.media.id.eq(media.id),
                                                                                contents.series.isNotNull())
                                                                .notExists());

                List<Media> mediaList = queryFactory
                                .selectFrom(media)
                                .where(
                                                condition,
                                                titleContains(searchWord),
                                                mediaStatusEq(MediaStatus.COMPLETED))
                                .orderBy(media.createdDate.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.count())
                                .from(media)
                                .where(
                                                condition,
                                                titleContains(searchWord),
                                                mediaStatusEq(MediaStatus.COMPLETED));

                return PageableExecutionUtils.getPage(mediaList, pageable, countQuery::fetchOne);
        }
}
