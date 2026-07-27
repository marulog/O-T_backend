package com.ott.infra.db.mood_category.repository;

import com.ott.domain.common.Status;
import com.ott.domain.mood_category.domain.MoodCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MoodCategoryRepository extends JpaRepository<MoodCategory, Long> {

}
