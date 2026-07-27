package com.ott.api_admin.upload.support;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.category.domain.Category;
import com.ott.infra.db.category.repository.CategoryRepository;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MediaTagLinker {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final MediaTagRepository mediaTagRepository;

    public void linkTags(Media media, Long categoryId, List<Long> tagIdList) {

        Category category = categoryRepository.findByIdAndStatus(categoryId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TAG_CATEGORY));

        Set<Long> uniqueTagIdSet = new LinkedHashSet<>();
        for (Long tagId : tagIdList) {
            if (!uniqueTagIdSet.add(tagId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_TAG_IN_LIST);
            }
        }

        List<Tag> tagList = tagRepository.findAllByIdInAndStatus(new ArrayList<>(uniqueTagIdSet), Status.ACTIVE);

        // 카테고리에 맞지 않거나 존재하지 않는 태그가 포함 확인.
        if (tagList.size() != uniqueTagIdSet.size()) {
            throw new BusinessException(ErrorCode.INVALID_TAG_SELECTION);
        }
        boolean hasInvalidCategoryTag = tagList.stream()
                .anyMatch(tag -> !tag.getCategory().getId().equals(category.getId()));
        if (hasInvalidCategoryTag) {
            throw new BusinessException(ErrorCode.INVALID_TAG_SELECTION);
        }

        List<MediaTag> mediaTagList = tagList.stream()
                .map(tag -> MediaTag.builder()
                        .media(media)
                        .tag(tag)
                        .build())
                .toList();

        mediaTagRepository.saveAll(mediaTagList);
    }
}
