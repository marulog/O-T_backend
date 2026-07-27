package com.ott.infra.db.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ott.domain.category.domain.Category;
import com.ott.domain.common.Status;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  Optional<Category> findByNameAndStatus(String name, Status status);

  @Query("""
      SELECT DISTINCT c.name
      FROM MediaTag mt
      JOIN mt.tag t
      JOIN t.category c
      WHERE mt.media.id = :mediaId
      AND mt.status = :status
      AND t.status = :status
      AND c.status = :status
      """)
  List<String> findCategoryNamesByMediaId(@Param("mediaId") Long mediaId, @Param("status") Status status);

  List<Category> findAllByStatus(Status status);

  Optional<Category> findByIdAndStatus(Long id, Status status);


}
