package com.glimmer.common.api;

import java.util.List;

/**
 * 用户端游标分页响应。cursor 对客户端不透明。
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
