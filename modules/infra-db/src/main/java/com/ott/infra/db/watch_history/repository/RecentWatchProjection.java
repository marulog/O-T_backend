package com.ott.infra.db.watch_history.repository;

import com.ott.domain.common.MediaType;
import lombok.Getter;

@Getter
public class RecentWatchProjection {

    private final Long mediaId;
    private final MediaType mediaType;
    private final String posterUrl;
    private final Integer positionSec;
    private final Integer duration;

    public RecentWatchProjection(Long mediaId, MediaType mediaType, String posterUrl, Integer positionSec, Integer duration) {
        this.mediaId = mediaId;
        this.mediaType = mediaType;
        this.posterUrl = posterUrl;
        this.positionSec = positionSec;
        this.duration = duration;
    }

}