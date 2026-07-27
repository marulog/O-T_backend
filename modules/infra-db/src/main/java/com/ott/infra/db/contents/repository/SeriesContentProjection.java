package com.ott.infra.db.contents.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;

//일단 사용안함
//QueryDsl 이 꽂아줄 순수 Query DTO (도메인 내부 전용 DTO)
@Getter
@AllArgsConstructor
public class SeriesContentProjection {
    private Long mediaId;
    private Long seriesMediaId;
    private String title;
    private String description;
    private String thumbnailUrl;
    private Integer duration;
    private Integer positionSec;
}
