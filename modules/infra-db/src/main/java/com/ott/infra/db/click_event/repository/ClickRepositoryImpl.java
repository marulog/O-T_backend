package com.ott.infra.db.click_event.repository;

import com.ott.domain.click_event.domain.ClickType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import static com.ott.domain.click_event.domain.QClickEvent.clickEvent;

@RequiredArgsConstructor
public class ClickRepositoryImpl implements ClickRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Long countByMonthAndType(int year, int month, ClickType type) {
        return queryFactory
                .select(clickEvent.count())
                .from(clickEvent)
                .where(
                        clickEvent.clickAt.year().eq(year),
                        clickEvent.clickAt.month().eq(month),
                        clickEvent.clickType.eq(type)
                )
                .fetchOne();
    }
}
