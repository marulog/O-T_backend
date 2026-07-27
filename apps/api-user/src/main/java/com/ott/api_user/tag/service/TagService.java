package com.ott.api_user.tag.service;

import com.ott.api_user.member.service.MemberService;
import com.ott.api_user.tag.dto.response.TagMonthlyCompareResponse;
import com.ott.api_user.tag.dto.response.TagRankingResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.Status;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.tag.repository.TagRepository;
import com.ott.infra.db.watch_history.repository.TagRankingProjection;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final MemberRepository memberRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final TagRepository tagRepository;

    /**
     * 마이페이지 - 시청이력 기반 상위 태그 랭킹 조회 1달
     * - 상위 4개: 개별 태그 항목
     * - 나머지: count 합산하여 기타 항목으로 반환
     */
    @Transactional(readOnly = true)
    public TagRankingResponse getTagRanking(Long memberId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 집계일과 마감일 선정 1일~말일까지
        YearMonth currentYearMonth = YearMonth.now();
        LocalDateTime startDate = currentYearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate   = currentYearMonth.plusMonths(1).atDay(1).atStartOfDay(); // 다음 달 1일 00:00:00

        List<TagRankingProjection> tagRankingProjections =
                watchHistoryRepository.findTopTagsByMemberIdAndWatchedBetween(memberId, startDate, endDate);

        List<TagRankingResponse.TagRankItem> rankItems = new ArrayList<>();

        // 시청이력이 없을 경우 빈 리스트가 전달됨
        if (tagRankingProjections.isEmpty()) {
            return TagRankingResponse.builder().rankings(rankItems).build();
        }

        int topN = Math.min(4, tagRankingProjections.size());

        // 상위 4개 추가
        for (int i = 0; i < topN; i++) {
            TagRankingProjection projection = tagRankingProjections.get(i);
            rankItems.add(TagRankingResponse.TagRankItem.of(projection.getTagId(), projection.getTagName(), projection.getCount()));
        }

        // 나머지 → 기타로 합산
        if (tagRankingProjections.size() > 4) {
            long etcCount = tagRankingProjections.subList(4, tagRankingProjections.size())
                    .stream()
                    .mapToLong(TagRankingProjection::getCount)
                    .sum();
            rankItems.add(TagRankingResponse.TagRankItem.ofEtc(etcCount));
        }

        return TagRankingResponse.builder().rankings(rankItems).build();
    }

    /**
     * 마이페이지 - 특정 태그의 이번 달 vs 저번 달 시청 count 비교
     */
    @Transactional(readOnly = true)
    public TagMonthlyCompareResponse getTagMonthlyCompare(Long memberId, Long tagId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Tag findTag = tagRepository.findByIdAndStatus(tagId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        // 이번 달 범위
        YearMonth currentYearMonth = YearMonth.now();
        LocalDateTime currentStart = currentYearMonth.atDay(1).atStartOfDay();
        LocalDateTime currentEnd   = currentYearMonth.plusMonths(1).atDay(1).atStartOfDay(); // 다음 달 1일 00:00:00

        // 저번 달 범위
        YearMonth prevYearMonth = currentYearMonth.minusMonths(1);
        LocalDateTime prevStart = prevYearMonth.atDay(1).atStartOfDay();
        LocalDateTime prevEnd   = currentYearMonth.atDay(1).atStartOfDay();  // 이번 달 1일 00:00:00

        Long currentCount  = watchHistoryRepository.countByMemberIdAndTagIdAndWatchedBetween(memberId, tagId, currentStart, currentEnd);
        Long previousCount = watchHistoryRepository.countByMemberIdAndTagIdAndWatchedBetween(memberId, tagId, prevStart, prevEnd);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // 저번 달 시청 기록이 없으면 null
        TagMonthlyCompareResponse.MonthlyCount previousMonth = previousCount > 0
                ? TagMonthlyCompareResponse.MonthlyCount.builder()
                .yearMonth(prevYearMonth.format(formatter))
                .count(previousCount)
                .build()
                : null;

        return TagMonthlyCompareResponse.builder()
                .tagId(findTag.getId())
                .tagName(findTag.getName())
                .currentMonth(TagMonthlyCompareResponse.MonthlyCount.builder()
                        .yearMonth(currentYearMonth.format(formatter))
                        .count(currentCount)
                        .build())
                .previousMonth(previousMonth)
                .build();
    }
}
