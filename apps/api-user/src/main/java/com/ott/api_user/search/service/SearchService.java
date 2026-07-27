package com.ott.api_user.search.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ott.api_user.search.dto.SearchItemResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media.repository.MediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final MediaRepository mediaRepository;

    public PageResult<SearchItemResponse> search(String searchWord, int page, int size) {

        String keyword = searchWord.trim();

        if (keyword.length() < 2) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }

        // 2. DB 레벨의 페이징 및 최신순 정렬 (생성일 기준 내림차순)
        Pageable pageable = PageRequest.of(page, size);

        // 3. 통합 검색 쿼리 실행
        Page<Media> mediaPage = mediaRepository.findUserSearchMediaList(pageable, keyword);


        // 4. Entity -> DTO 변환
        List<SearchItemResponse> pagedResult = mediaPage.getContent().stream()
                .map(SearchItemResponse::from)
                .collect(Collectors.toList());


        // 5. 응답용 PageInfo 생성
        PageMetadata pageMetadata = PageMetadata.of(
                mediaPage.getNumber(),
                mediaPage.getTotalPages(),
                mediaPage.getSize()
        );

        return PageResult.of(pageMetadata, pagedResult);
    }
}
