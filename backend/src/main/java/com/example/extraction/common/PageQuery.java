package com.example.extraction.common;

public class PageQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;

    public Integer getPageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 200);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
