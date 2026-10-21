package com.glimmer.common.api;

import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * 管理端页码分页响应。用户端信息流使用 {@link CursorPage}。
 */
public record PageResult<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResult<T> from(PageInfo<T> pageInfo) {
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                pageInfo.getPages()
        );
    }
}
