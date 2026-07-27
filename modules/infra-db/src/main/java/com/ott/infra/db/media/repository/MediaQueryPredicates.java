package com.ott.infra.db.media.repository;

import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.MediaStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import org.springframework.util.StringUtils;

final class MediaQueryPredicates {

        private MediaQueryPredicates() {
        }

        static BooleanExpression titleContains(String searchWord) {
                if (StringUtils.hasText(searchWord))
                        return media.title.contains(searchWord);
                return null;
        }

        static BooleanExpression mediaTypeEq(MediaType mediaType) {
                if (mediaType != null)
                        return media.mediaType.eq(mediaType);
                return null;
        }

        static BooleanExpression publicStatusEq(PublicStatus publicStatus) {
                if (publicStatus != null)
                        return media.publicStatus.eq(publicStatus);
                return null;
        }

        static BooleanExpression uploaderIdEq(Long uploaderId) {
                if (uploaderId != null)
                        return media.uploader.id.eq(uploaderId);
                return null;
        }

        static BooleanExpression isActiveAndPublic() {
                return media.status.eq(Status.ACTIVE)
                                .and(media.publicStatus.eq(PublicStatus.PUBLIC)
                                                .and(media.mediaStatus.eq(MediaStatus.COMPLETED)));
        }

        static BooleanExpression excludeId(Long excludeMediaId) {
                return excludeMediaId != null ? media.id.ne(excludeMediaId) : null;
        }

        static BooleanExpression isDisplayable() {
                return media.mediaType.eq(MediaType.SERIES)
                                .or(media.mediaType.eq(MediaType.CONTENTS)
                                                .and(JPAExpressions.selectOne()
                                                                .from(contents)
                                                                .where(contents.media.id.eq(media.id)
                                                                                .and(contents.series.isNotNull()))
                                                                .notExists()));
        }

        static BooleanExpression mediaStatusEq(MediaStatus mediaStatus) {
                if (mediaStatus != null)
                        return media.mediaStatus.eq(mediaStatus);
                return null;
        }
}
