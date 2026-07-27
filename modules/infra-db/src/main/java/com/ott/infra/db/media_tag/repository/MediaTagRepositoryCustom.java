package com.ott.infra.db.media_tag.repository;

import com.ott.domain.media_tag.domain.MediaTag;

import java.util.List;

public interface MediaTagRepositoryCustom {

    List<MediaTag> findWithTagAndCategoryByMediaIds(List<Long> mediaIds);

    List<MediaTag> findWithTagAndCategoryByMediaId(Long mediaId);
}
