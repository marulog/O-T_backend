package com.ott.common.core.response;

import java.util.List;

public class PageResult<T> {

    private final PageMetadata pageMetadata;
    private final List<T> dataList;

    public PageResult(PageMetadata pageMetadata, List<T> dataList) {
        this.pageMetadata = pageMetadata;
        this.dataList = List.copyOf(dataList);
    }

    public static <T> PageResult<T> of(PageMetadata pageMetadata, List<T> dataList) {
        return new PageResult<>(pageMetadata, dataList);
    }

    public PageMetadata getPageMetadata() {
        return pageMetadata;
    }

    public List<T> getDataList() {
        return dataList;
    }
}
