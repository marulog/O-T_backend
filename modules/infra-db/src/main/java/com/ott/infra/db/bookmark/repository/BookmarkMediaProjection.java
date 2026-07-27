package com.ott.infra.db.bookmark.repository;

import com.ott.domain.common.MediaType;
import lombok.Getter;

@Getter
public class BookmarkMediaProjection {

    private final Long mediaId;
    private final MediaType mediaType;
    private final String title;
    private final String description;
    private final String posterUrl;
    private final Integer positionSec; // 콘텐츠만, SERIES는 null
    private final Integer duration;    // 콘텐츠만, SERIES는 null

    public BookmarkMediaProjection(
            Long mediaId,
            MediaType mediaType,
            String title,
            String description,
            String posterUrl,
            Integer positionSec,
            Integer duration
    ) {
        this.mediaId = mediaId;
        this.mediaType = mediaType;
        this.title = title;
        this.description = description;
        this.posterUrl = posterUrl;
        this.positionSec = positionSec;
        this.duration = duration;
    }
}