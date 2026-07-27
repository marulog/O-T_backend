package com.ott.infra.db.media_mood_tag.repository;

import com.ott.domain.common.Status;
import com.ott.domain.media_mood_tag.domain.MediaMoodTag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaMoodTagRepository extends JpaRepository<MediaMoodTag, Long> {
    void deleteByMedia_Id(Long mediaId);

    // 최근 시청이력 리스트들 중 우선순위가 1위인 영상의 감정태그 조회
    @EntityGraph(attributePaths = {"moodTag", "moodTag.moodCategory"})
    List<MediaMoodTag> findByMedia_IdInAndStatusAndPriorityOrderByMedia_IdAscPriorityAsc(
            List<Long> mediaIds,
            Status status,
            Integer priority
    );

     // 최근 시청이력 리스트들의 영상의 감정태그 전부 조회
    @EntityGraph(attributePaths = {"moodTag"})
    List<MediaMoodTag> findByMedia_IdInAndStatusOrderByMedia_IdAscPriorityAsc(
            List<Long> mediaIds,
            Status status
    );
}
