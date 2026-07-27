package com.ott.infra.db.mood_tag.repository;

import com.ott.domain.common.Status;
import com.ott.domain.mood_tag.domain.MoodTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MoodTagRepository extends JpaRepository<MoodTag, Long> {

    List<MoodTag> findByNameInAndStatus(List<String> aiTags, Status status);

    Optional<MoodTag> findByNameAndStatus(String name, Status status);
}
