package com.ott.infra.db.media_tag.repository;

import com.ott.domain.media_tag.domain.MediaTag;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.ott.domain.media_tag.domain.QMediaTag.mediaTag;
import static com.ott.domain.tag.domain.QTag.tag;
import static com.ott.domain.category.domain.QCategory.category;

@RequiredArgsConstructor
public class MediaTagRepositoryImpl implements MediaTagRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MediaTag> findWithTagAndCategoryByMediaIds(List<Long> mediaIds) {
        return queryFactory
                .selectFrom(mediaTag)
                .join(mediaTag.tag, tag).fetchJoin()
                .join(tag.category, category).fetchJoin()
                .where(mediaTag.media.id.in(mediaIds))
                .fetch();
    }

    @Override
    public List<MediaTag> findWithTagAndCategoryByMediaId(Long mediaId) {
        return queryFactory
                .selectFrom(mediaTag)
                .join(mediaTag.tag, tag).fetchJoin()
                .join(tag.category, category).fetchJoin()
                .where(mediaTag.media.id.eq(mediaId))
                .fetch();
    }
}
