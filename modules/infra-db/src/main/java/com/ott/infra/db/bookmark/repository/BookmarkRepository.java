package com.ott.infra.db.bookmark.repository;

import com.ott.domain.common.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ott.domain.bookmark.domain.Bookmark;
import com.ott.domain.common.Status;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long>, BookmarkRepositoryCustom {
    boolean existsByMemberIdAndMediaIdAndStatus(Long memberId, Long mediaId, Status status);

    Optional<Bookmark> findByMemberIdAndMediaId(Long memberId, Long mediaId);

    // 콘텐츠 북마크 목록 조회 (CONTENTS, SERIES)
    @EntityGraph(attributePaths = {"media"})
    Page<Bookmark> findByMemberIdAndStatusAndMedia_MediaTypeInOrderByCreatedDateDesc(
            Long memberId, Status status, List<MediaType> mediaTypes, Pageable pageable);

    // 숏폼 북마크 목록 (SHORT_FORM)
    @EntityGraph(attributePaths = {"media"})
    Page<Bookmark> findByMemberIdAndStatusAndMedia_MediaTypeOrderByCreatedDateDesc(
            Long memberId, Status status, MediaType mediaType, Pageable pageable);

    // 회원탈퇴 soft delete
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Bookmark b SET b.status = 'DELETE' WHERE b.member.id = :memberId")
    void softDeleteAllByMemberId(@Param("memberId") Long memberId);


    //주어진 미디어 중 북마크한 미디어들의 Id 리스트 조회
    @Query("SELECT b.media.id FROM Bookmark b " +
           "WHERE b.member.id = :memberId " +
           "AND b.media.id IN :mediaIds " +
           "AND b.status = 'ACTIVE'")
    Set<Long> findBookmarkedMediaIds(
            @Param("memberId") Long memberId,
            @Param("mediaIds") List<Long> mediaIds
    );

}
