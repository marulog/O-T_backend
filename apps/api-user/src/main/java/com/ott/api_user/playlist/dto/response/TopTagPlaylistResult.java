package com.ott.api_user.playlist.dto.response;

import com.ott.common.core.response.PageResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TopTagPlaylistResult {

    private CategoryInfo category;
    private TagInfo tag;
    private PageResult<PlaylistResponse> medias;

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class TagInfo {
        private Long id;
        private String name;
    }
}
