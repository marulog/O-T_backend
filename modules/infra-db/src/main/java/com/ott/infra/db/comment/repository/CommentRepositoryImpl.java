package com.ott.infra.db.comment.repository;

import com.ott.domain.comment.domain.Comment;
import com.ott.domain.common.Status;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.ott.domain.comment.domain.QComment.comment;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.member.domain.QMember.member;
import static com.ott.domain.series.domain.QSeries.series;
import com.ott.domain.media.domain.QMedia;

@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private static final QMedia seriesMedia = new QMedia("seriesMedia");
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Comment> findMyComments(Long memberId, Status status, Pageable pageable) {
        List<Comment> commentList = queryFactory
                .selectFrom(comment)
                .join(comment.member, member).fetchJoin()
                .join(comment.contents, contents).fetchJoin()
                .join(contents.media, media).fetchJoin()
                .leftJoin(contents.series, series).fetchJoin()
                .leftJoin(series.media, seriesMedia).fetchJoin()
                .where(
                        comment.member.id.eq(memberId),
                        comment.status.eq(status)
                )
                .orderBy(comment.createdDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        comment.member.id.eq(memberId),
                        comment.status.eq(status)
                );

        return PageableExecutionUtils.getPage(commentList, pageable, countQuery::fetchOne);
    }
}