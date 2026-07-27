package com.ott.infra.db.click_event.repository;

import com.ott.domain.click_event.domain.ClickType;

public interface ClickRepositoryCustom {
    Long countByMonthAndType(int year, int month, ClickType type);
}
