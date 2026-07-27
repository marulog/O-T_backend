package com.ott.infra.db.ingest_job.repository;

import com.ott.domain.ingest_job.domain.IngestJob;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.ott.domain.ingest_job.domain.QIngestJob.ingestJob;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.member.domain.QMember.member;

@RequiredArgsConstructor
public class IngestJobRepositoryImpl implements IngestJobRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<IngestJob> findIngestJobListWithMediaBySearchWordAndUploaderId(Pageable pageable, String searchWord,
            Long uploaderId) {
        List<IngestJob> ingestJobList = queryFactory
                .selectFrom(ingestJob)
                .join(ingestJob.media, media).fetchJoin()
                .join(media.uploader, member).fetchJoin()
                .where(
                        titleContains(searchWord),
                        uploaderIdEq(uploaderId))
                .orderBy(ingestJob.createdDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(ingestJob.count())
                .from(ingestJob)
                .join(ingestJob.media, media)
                .where(
                        titleContains(searchWord),
                        uploaderIdEq(uploaderId));

        return PageableExecutionUtils.getPage(ingestJobList, pageable, countQuery::fetchOne);
    }

    private BooleanExpression titleContains(String searchWord) {
        if (StringUtils.hasText(searchWord))
            return media.title.contains(searchWord);
        return null;
    }

    private BooleanExpression uploaderIdEq(Long uploaderId) {
        if (uploaderId != null)
            return media.uploader.id.eq(uploaderId);
        return null;
    }
}
