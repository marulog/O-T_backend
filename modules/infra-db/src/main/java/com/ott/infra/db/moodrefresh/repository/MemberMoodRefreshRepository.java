package com.ott.infra.db.moodrefresh.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ott.domain.moodrefresh.domain.MemberMoodRefresh;

public interface MemberMoodRefreshRepository extends JpaRepository<MemberMoodRefresh, Long>{

    // 특정 유저의 닫기 버튼을 누르지 않은 상태의 최신 카드 1건 조회
    Optional<MemberMoodRefresh> findTopByMemberIdAndIsHiddenFalseOrderByCreatedDateDesc(Long memberId);
    // 6시간 쿨다운 확인
    boolean existsByMemberIdAndCreatedDateAfter(Long memberId, LocalDateTime cutoff);
}
