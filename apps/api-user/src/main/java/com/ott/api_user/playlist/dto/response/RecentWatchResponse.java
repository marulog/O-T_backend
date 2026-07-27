package com.ott.api_user.playlist.dto.response;

import com.ott.domain.common.MediaType;
import com.ott.infra.db.watch_history.repository.RecentWatchProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "시청이력 플레이리스트 DTO")
public class RecentWatchResponse {

    @Schema(type = "Long", example = "3", description = "미디어 ID")
    private Long mediaId;

    @Schema(type = "String", example = "CONTENTS", description = "미디어 타입 (CONTENTS, SERIES, SHORT_FORM)")
    private MediaType mediaType;

    @Schema(type = "String", example = "https://cdn.ott.com/poster/thriller01.jpg", description = "포스터 URL")
    private String posterUrl;

    @Schema(type = "Integer", example = "150", description = "이어보기 시점 (초), 없으면 0")
    private Integer positionSec;

    @Schema(type = "Integer", example = "3600", description = "전체 재생 시간 (초)")
    private Integer duration;

    public static RecentWatchResponse from(RecentWatchProjection projection) {
        return RecentWatchResponse.builder()
                .mediaId(projection.getMediaId())
                .mediaType(projection.getMediaType())
                .posterUrl(projection.getPosterUrl())
                .positionSec(projection.getPositionSec())
                .duration(projection.getDuration())
                .build();
    }
}