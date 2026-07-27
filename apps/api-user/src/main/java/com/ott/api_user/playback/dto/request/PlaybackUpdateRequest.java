package com.ott.api_user.playback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이어보기 요청 DTO")
public class PlaybackUpdateRequest {

    @NotNull(message = "미디어 ID는 필수입니다.")
    @Schema(type = "Long", description = "미디어 ID", example = "101")
    private Long mediaId;

    @NotNull(message = "재생 지점은 필수입니다.")
    @Schema(type = "Integer", description = "재생 지점(초)", example = "120")
    private Integer positionSec;
}
