package com.ott.infra.db.preferred_tag.repository;

import com.ott.domain.common.Status;
import com.ott.domain.member.domain.Member;
import com.ott.domain.preferred_tag.domain.PreferredTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PreferredTagRepository extends JpaRepository<PreferredTag, Long> {
    boolean existsByMemberId(Long memberId);

    @Query("""
            select pt
            from PreferredTag pt
            join fetch pt.tag t
            join fetch t.category c
            where pt.member.id = :memberId
              and pt.status = :status
              and t.status = :status
              and c.status = :status
            order by pt.id asc
            """)
    List<PreferredTag> findAllWithTagAndCategoryByMemberIdAndStatus(@Param("memberId") Long memberId,
                                                                    @Param("status") Status status);

    // 선호 태그 삭제,  영속성 컨텍스트 들어있는 내용 삭제
    @Modifying
    @Query("DELETE FROM PreferredTag pt WHERE pt.member = :member")
    void deleteAllByMember(@Param("member") Member member);


    // 회원 탈퇴 시 soft delete 사용
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PreferredTag pt SET pt.status = 'DELETE' WHERE pt.member.id = :memberId")
    void softDeleteAllByMemberId(@Param("memberId") Long memberId);


    // 사용자의 선호 태그 ID만 조회
    @Query("""
            SELECT pt.tag.id
            FROM PreferredTag pt
            WHERE pt.member.id = :memberId AND pt.status = :status
            """)
    List<Long> findTagIdsByMemberId(@Param("memberId") Long memberId, @Param("status") Status status);

}
