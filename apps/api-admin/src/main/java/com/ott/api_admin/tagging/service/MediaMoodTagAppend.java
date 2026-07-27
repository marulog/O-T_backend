package com.ott.api_admin.tagging.service;

import com.ott.domain.media_mood_tag.domain.MediaMoodTag;
import com.ott.infra.db.media_mood_tag.repository.MediaMoodTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaMoodTagAppend {

    private final MediaMoodTagRepository mediaMoodTagRepository;

    @Transactional
    public void replaceMediaMoodTags(Long mediaId, List<MediaMoodTag> newMediaMoodTags) {
        mediaMoodTagRepository.deleteByMedia_Id(mediaId);
        mediaMoodTagRepository.saveAll(newMediaMoodTags);
    }
}
