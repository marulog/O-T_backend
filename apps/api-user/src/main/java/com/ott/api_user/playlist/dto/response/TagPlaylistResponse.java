package com.ott.api_user.playlist.dto.response;

import com.ott.domain.common.MediaType;
import com.ott.infra.db.media.repository.TagContentProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "태그별 추천 콘텐츠 아이템")
public class TagPlaylistResponse {

    @Schema(type = "Long", example = "5", description = "미디어 ID")
    private Long mediaId;

    @Schema(type = "String", example = "https://cdn.ott.com/poster/thriller01.jpg", description = "포스터 URL")
    private String posterUrl;

    @Schema(type = "String", example = "SERIES", description = "미디어 타입 (SERIES / CONTENTS)")
    private MediaType mediaType;

    public static TagPlaylistResponse from(TagContentProjection projection) {
        return TagPlaylistResponse.builder()
                .mediaId(projection.getMediaId())
                .posterUrl(projection.getPosterUrl())
                .mediaType(projection.getMediaType())
                .build();
    }
}