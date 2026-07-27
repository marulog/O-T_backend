package com.ott.infra.db.member_radar_preference.repository;

import com.ott.domain.member_radar_preference.domain.MemberRadarPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRadarPreferenceRepository extends JpaRepository<MemberRadarPreference, Long> {

    Optional<MemberRadarPreference> findByMemberId(Long memberId);

    @Modifying
    @Query("UPDATE MemberRadarPreference p SET p.status = 'DELETE' WHERE p.member.id = :memberId")
    void softDeleteByMemberId(@Param("memberId") Long memberId);
}
