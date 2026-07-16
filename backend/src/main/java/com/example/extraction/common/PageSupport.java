package com.example.extraction.common;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import java.util.List;
import java.util.function.Supplier;

public final class PageSupport {
    private PageSupport() {
    }

    public static <T> PageResponse<T> page(PageQuery query, Supplier<List<T>> supplier) {
        PageQuery safeQuery = query == null ? new PageQuery() : query;
        Page<T> page = PageHelper.startPage(safeQuery.getPageNo(), safeQuery.getPageSize(), true);
        List<T> records = supplier.get();
        return PageResponse.of(records, page.getTotal(), safeQuery.getPageNo(), safeQuery.getPageSize());
    }
}
