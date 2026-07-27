package com.ott.common.web.response;

import com.ott.common.core.response.PageResult;

public final class PageResponseMapper {

    private PageResponseMapper() {
    }

    public static <T> PageResponse<T> from(PageResult<T> pageResult) {
        return PageResponse.toPageResponse(
                PageInfo.from(pageResult.getPageMetadata()),
                pageResult.getDataList()
        );
    }
}
