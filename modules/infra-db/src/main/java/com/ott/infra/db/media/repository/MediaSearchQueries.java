package com.ott.infra.db.media.repository;

import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.media_mood_tag.domain.QMediaMoodTag.mediaMoodTag;
import static com.ott.domain.mood_tag.domain.QMoodTag.moodTag;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.isActiveAndPublic;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.isDisplayable;
import static com.ott.infra.db.media.repository.MediaQueryPredicates.titleContains;

import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
final class MediaSearchQueries {

        private final JPAQueryFactory queryFactory;

        Page<Media> findUserSearchMediaList(Pageable pageable, String searchWord) {
                List<Media> mediaList = queryFactory
                                .selectFrom(media)
                                .where(
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                titleContains(searchWord))
                                .orderBy(media.createdDate.desc(), media.id.desc())
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                JPAQuery<Long> countQuery = queryFactory
                                .select(media.count())
                                .from(media)
                                .where(
                                                isActiveAndPublic(),
                                                isDisplayable(),
                                                titleContains(searchWord));

                return PageableExecutionUtils.getPage(mediaList, pageable, countQuery::fetchOne);
        }

        List<Media> findByTop3ByMoodTagName(String tagName) {
                if (!StringUtils.hasText(tagName)) {
                        return Collections.emptyList();
                }

                return queryFactory
                                .selectDistinct(media)
                                .from(mediaMoodTag)
                                .join(mediaMoodTag.media, media)
                                .join(mediaMoodTag.moodTag, moodTag)
                                .where(
                                                moodTag.name.eq(tagName),
                                                moodTag.status.eq(Status.ACTIVE),
                                                mediaMoodTag.status.eq(Status.ACTIVE),
                                                isActiveAndPublic(),
                                                isDisplayable())
                                .orderBy(media.createdDate.desc(), media.id.desc())
                                .limit(3)
                                .fetch();
        }
}
