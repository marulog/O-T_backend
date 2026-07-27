package com.ott.api_user.content.dto;

import java.util.List;

import com.ott.domain.contents.domain.Contents;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "컨텐츠 상세(재생) 조회 응답 DTO")
public class ContentsDetailResponse {
    @Schema(description = "미디어 고유 ID", example = "1")
    private Long mediaId;

    @Schema(description = "해당 미디어의 시리즈ID (본체 미디어 ID) (단편이면 null)", example = "101")
    private Long seriesMediaId;

    @Schema(description = "콘텐츠 제목", example = "비밀의 숲")
    private String title;

    @Schema(description = "콘텐츠 설명", example = "검경 수사극의 새로운 지평을 연 드라마")
    private String description;

    @Schema(description = "출연진", example = "송혜교, 이도현, 임지연")
    private String actors;

    @Schema(description = "가로형 썸네일 이미지 URL", example = "https://cdn.ott.com/thumbnails/101.jpg")
    private String thumbnailUrl;

    @Schema(description = "카테고리", example = "드라마")
    private String category;

    @Schema(description = "태그 목록", example = "드라마, 범죄, 수사")
    private List<String> tags;

    @Schema(description = "사용자 북마크 여부(seriesMediaId 에 대한 여부)", example = "true")
    private Boolean isBookmarked;

    @Schema(description = "사용자 좋아요 여부(seriesMediaId 에 대한 여부)", example = "true")
    private Boolean isLiked;

    @Schema(description = "마스터 재생목록 URL(HLS)", example = "https://example.com/master.m3u8")
    private String masterPlaylistUrl;


    @Schema(description= "재생 시간 (초)", example = "3600")
    private Integer duration;

    @Schema(description = "기존 이어보기 지점(없으면 0)", example = "150")
    private Integer positionSec;

    public static ContentsDetailResponse from(Contents contents, List<String> tags,
            List<String> categories, Boolean isBookmarked, Boolean isLiked, String masterPlaylistUrl,
            Integer positionSec) {

        Long seriesMediaId = null;

        if (contents.getSeries() != null && contents.getSeries().getMedia() != null) {
            seriesMediaId = contents.getSeries().getMedia().getId();
        }

        return ContentsDetailResponse.builder()
                .mediaId(contents.getMedia().getId())
                .seriesMediaId(seriesMediaId)
                .title(contents.getMedia().getTitle())
                .description(contents.getMedia().getDescription())
                .actors(contents.getActors())
                .thumbnailUrl(contents.getMedia().getThumbnailUrl())
                .category(categories.isEmpty() ? null : categories.get(0))
                .tags(tags)
                .isBookmarked(isBookmarked)
                .isLiked(isLiked)
                .masterPlaylistUrl(masterPlaylistUrl)
                .duration(contents.getDuration())
                .positionSec(positionSec)
                .build();
    }
}
