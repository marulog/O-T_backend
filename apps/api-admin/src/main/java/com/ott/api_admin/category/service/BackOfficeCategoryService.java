package com.ott.api_admin.category.service;

import com.ott.api_admin.category.dto.response.CategoryListResponse;
import com.ott.api_admin.category.mapper.BackOfficeCategoryMapper;
import com.ott.infra.db.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BackOfficeCategoryService {

    private final BackOfficeCategoryMapper backOfficeCategoryMapper;

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryListResponse> getCategoryList() {
        return categoryRepository.findAll().stream()
                .map(backOfficeCategoryMapper::toCategoryListResponse)
                .toList();
    }
}
