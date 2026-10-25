package com.glimmer.common.api;

import java.util.List;

/**
 * 用户端游标分页响应。cursor 对客户端不透明。
 */
public record CursorPage<T>(
        /** 当前页的数据列表。 */
        List<T> items,

        /** 下一页游标，对客户端不透明；为 null 表示没有下一页。 */
        String nextCursor,

        /** 是否还有下一页。 */
        boolean hasMore
) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
