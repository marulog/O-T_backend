package com.ott.infra.db.bookmark.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookmarkRepositoryCustom {

    // 북마크 콘텐츠/시리즈 목록 조회 (positionSec, duration 포함)
    Page<BookmarkMediaProjection> findBookmarkMediaList(Long memberId, Pageable pageable);
}
