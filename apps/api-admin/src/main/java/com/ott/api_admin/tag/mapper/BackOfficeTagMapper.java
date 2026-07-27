package com.ott.api_admin.tag.mapper;

import com.ott.api_admin.tag.dto.response.TagResponse;
import com.ott.api_admin.tag.dto.response.TagViewResponse;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.watch_history.repository.TagViewCountProjection;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BackOfficeTagMapper {

    public TagViewResponse toTagViewResponse(TagViewCountProjection projection) {
        return new TagViewResponse(
                projection.tagName(),
                projection.viewCount()
        );
    }

    public TagResponse toTagResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName()
        );
    }
}
