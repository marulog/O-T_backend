package com.ott.infra.db.watch_history.repository;

import lombok.Getter;

@Getter
public class TagRankingProjection {

    private final Long tagId;
    private final String tagName;
    private final Long count;

    public TagRankingProjection(Long tagId, String tagName, Long count) {
        this.tagId = tagId;
        this.tagName = tagName;
        this.count = count;
    }
}
