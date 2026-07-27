package com.ott.common.core.response;

public class PageMetadata {

    private final int currentPage;
    private final int totalPage;
    private final int pageSize;

    public PageMetadata(int currentPage, int totalPage, int pageSize) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
        this.pageSize = pageSize;
    }

    public static PageMetadata of(int currentPage, int totalPage, int pageSize) {
        return new PageMetadata(currentPage, totalPage, pageSize);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPage() {
        return totalPage;
    }

    public int getPageSize() {
        return pageSize;
    }
}
