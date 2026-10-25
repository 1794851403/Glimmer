package com.glimmer.common.api;

import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * 管理端页码分页响应。用户端信息流使用 {@link CursorPage}。
 */
public record PageResult<T>(
        /** 当前页的数据列表。 */
        List<T> items,

        /** 当前页码，从 1 开始。 */
        int page,

        /** 每页条数。 */
        int size,

        /** 符合条件的记录总数。 */
        long total,

        /** 总页数。 */
        int totalPages
) {

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
