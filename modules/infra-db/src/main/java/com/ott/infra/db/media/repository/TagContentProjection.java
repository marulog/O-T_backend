package com.ott.infra.db.media.repository;

import com.ott.domain.common.MediaType;
import lombok.Getter;

@Getter
public class TagContentProjection {

    private final Long mediaId;
    private final String posterUrl;
    private final MediaType mediaType;

    public TagContentProjection(Long mediaId, String posterUrl, MediaType mediaType) {
        this.mediaId = mediaId;
        this.posterUrl = posterUrl;
        this.mediaType = mediaType;
    }
}