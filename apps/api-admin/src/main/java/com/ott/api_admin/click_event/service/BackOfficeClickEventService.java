package com.ott.api_admin.click_event.service;

import com.ott.api_admin.click_event.dto.response.ShortFormConversionResponse;
import com.ott.api_admin.click_event.mapper.BackOfficeClickEventMapper;
import com.ott.domain.click_event.domain.ClickType;
import com.ott.infra.db.click_event.repository.ClickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class BackOfficeClickEventService {

    private final ClickRepository clickRepository;
    private final BackOfficeClickEventMapper backOfficeClickEventMapper;

    @Transactional(readOnly = true)
    public ShortFormConversionResponse getShortFormConversionStats() {
        LocalDate now = LocalDate.now();
        LocalDate lastMonth = now.minusMonths(1);

        double thisMonthRate = calculateConversionRate(now.getYear(), now.getMonthValue());
        double lastMonthRate = calculateConversionRate(lastMonth.getYear(), lastMonth.getMonthValue());
        return backOfficeClickEventMapper.toShortFormConversionResponse(thisMonthRate, lastMonthRate);
    }

    // 특정 연월의 전환율 계산
    public double calculateConversionRate(int year, int month) {
        Long shortClickCount = clickRepository.countByMonthAndType(year, month, ClickType.SHORT_CLICK);

        if (shortClickCount == null || shortClickCount == 0) {
            return 0.0;
        }

        Long ctaClickCount = clickRepository.countByMonthAndType(year, month, ClickType.CTA_CLICK);
        return (double) ctaClickCount / shortClickCount * 100;
    }
}
