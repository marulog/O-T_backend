package com.ott.infra.db.tag.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.ott.domain.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ott.domain.common.Status;
import com.ott.domain.tag.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByCategoryIdAndNameInAndStatus(Long categoryId, Set<String> nameList, Status status);

    // 시리즈/콘텐츠에 연결된 태그 조회
    @Query("""
            SELECT DISTINCT t.name
            FROM MediaTag mt
            JOIN mt.tag t
            WHERE mt.media.id = :mediaId
            AND t.status = :status
            AND mt.status = :status
            """)
    List<String> findTagNamesByMediaId(@Param("mediaId") Long mediaId, @Param("status") Status status);

    // 모든 카테고리에 있는 태그 조회
    List<Tag> findAllByCategoryAndStatus(Category category, Status status);

    // ACTIVE&&리스트안에 있는 태그 조회
    List<Tag> findAllByIdInAndStatus(List<Long> ids, Status status);

    // ACTIVE한 특정 태그 조회
   Optional<Tag> findByIdAndStatus(Long tagId, Status status);

    List<Tag> findAllByCategory(Category category);
}