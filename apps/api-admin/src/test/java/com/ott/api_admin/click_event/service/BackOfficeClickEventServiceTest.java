package com.ott.api_admin.click_event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ott.api_admin.click_event.dto.response.ShortFormConversionResponse;
import com.ott.api_admin.click_event.mapper.BackOfficeClickEventMapper;
import com.ott.domain.click_event.domain.ClickType;
import com.ott.infra.db.click_event.repository.ClickRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackOfficeClickEventServiceTest {

    @Mock
    private ClickRepository clickRepository;

    @Mock
    private BackOfficeClickEventMapper mapper;

    @InjectMocks
    private BackOfficeClickEventService clickEventService;

    // 단위 테스트에서 0건 조회가 있으면 0.0을 반환하는지 확인
    @Test
    void calculateConversionRate_withNoShortClicks_returnsZero() {
        int year = 2025;
        int month = 5;
        when(clickRepository.countByMonthAndType(year, month, ClickType.SHORT_CLICK)).thenReturn(0L);

        double rate = clickEventService.calculateConversionRate(year, month);

        assertThat(rate).isZero();
    }

    // 이번 달/지난 달 클릭 데이터를 기반으로 mapper가 올바른 비율을 받는지 검증
    @Test
    void getShortFormConversionStats_mapsCalculatedRates() {
        LocalDate now = LocalDate.now();
        int thisYear = now.getYear();
        int thisMonth = now.getMonthValue();
        LocalDate last = now.minusMonths(1);
        int lastYear = last.getYear();
        int lastMonth = last.getMonthValue();

        when(clickRepository.countByMonthAndType(thisYear, thisMonth, ClickType.SHORT_CLICK)).thenReturn(10L);
        when(clickRepository.countByMonthAndType(thisYear, thisMonth, ClickType.CTA_CLICK)).thenReturn(6L);
        when(clickRepository.countByMonthAndType(lastYear, lastMonth, ClickType.SHORT_CLICK)).thenReturn(5L);
        when(clickRepository.countByMonthAndType(lastYear, lastMonth, ClickType.CTA_CLICK)).thenReturn(5L);

        ShortFormConversionResponse dto = new ShortFormConversionResponse(60.0, -40.0);
        when(mapper.toShortFormConversionResponse(eq(60.0), eq(100.0))).thenReturn(dto);

        ShortFormConversionResponse response = clickEventService.getShortFormConversionStats();

        assertThat(response).isSameAs(dto);
    }
}
