package com.ott.infra.db.playback.repository;

import com.ott.domain.contents.domain.QContents;
import com.ott.domain.media.domain.QMedia;
import com.ott.domain.playback.domain.Playback;
import com.ott.domain.playback.domain.QPlayback;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.ott.domain.common.Status.ACTIVE;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.playback.domain.QPlayback.playback;
import static com.ott.domain.series.domain.QSeries.series;

@RequiredArgsConstructor
public class PlaybackRepositoryImpl implements PlaybackRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 시리즈별 가장 최근 시청한 에피소드의 Playback 조회
    // modifiedDate(마지막 시청 시점) 기준, 동률 시 id가 큰 쪽 선택
    @Override
    public List<Playback> findLatestByMemberAndSeriesMediaIds(Long memberId, List<Long> seriesMediaIdList) {
        QMedia seriesMedia = new QMedia("seriesMedia");

        QPlayback sub = new QPlayback("sub");
        QPlayback sub2 = new QPlayback("sub2");
        QContents subContents = new QContents("subContents");
        QContents subContents2 = new QContents("subContents2");

        return queryFactory
                .selectFrom(playback)
                .join(playback.contents, contents).fetchJoin()
                .join(contents.series, series).fetchJoin()
                .join(series.media, seriesMedia).fetchJoin()
                .where(
                        playback.member.id.eq(memberId),
                        playback.status.eq(ACTIVE),
                        seriesMedia.id.in(seriesMediaIdList),
                        playback.id.eq(
                                JPAExpressions
                                        .select(sub.id.max())
                                        .from(sub)
                                        .join(sub.contents, subContents)
                                        .where(
                                                sub.member.id.eq(memberId),
                                                sub.status.eq(ACTIVE),
                                                subContents.series.id.eq(series.id),
                                                sub.modifiedDate.eq(
                                                        JPAExpressions
                                                                .select(sub2.modifiedDate.max())
                                                                .from(sub2)
                                                                .join(sub2.contents, subContents2)
                                                                .where(
                                                                        sub2.member.id.eq(memberId),
                                                                        sub2.status.eq(ACTIVE),
                                                                        subContents2.series.id.eq(series.id)
                                                                )
                                                )
                                        )
                        )
                )
                .fetch();
    }
}
