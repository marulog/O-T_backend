package com.ott.api_user.category.service;

import com.ott.api_user.category.dto.response.CategoryResponse;
import com.ott.api_user.tag.dto.response.TagResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.category.domain.Category;
import com.ott.infra.db.category.repository.CategoryRepository;
import com.ott.domain.common.Status;
import com.ott.infra.db.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByStatus(Status.ACTIVE)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    // 카테고리별 태그 목록 조회
    public List<TagResponse> getTagsByCategory(Long categoryId) {
        Category findCategory = categoryRepository.findByIdAndStatus(categoryId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        return tagRepository.findAllByCategoryAndStatus(findCategory, Status.ACTIVE)
                .stream()
                .map(TagResponse::from)
                .toList();
    }
}
