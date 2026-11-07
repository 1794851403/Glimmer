package com.glimmer.common.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * 游标分页的编解码工具，游标对客户端不透明。
 *
 * <p>内容为 {@code 时间戳毫秒:主键} 的 Base64URL 编码；坏游标统一返回 {@code null}，
 * 由调用方按首页处理，不额外报参数错误。
 */
public final class CursorCodec {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private CursorCodec() {
    }

    /**
     * 把排序时间与主键编码成下一页游标；任一为空时返回 {@code null}。
     */
    public static String encode(LocalDateTime time, Long id) {
        if (time == null || id == null) {
            return null;
        }
        String raw = time.atZone(ZONE).toInstant().toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 用 epoch 毫秒编码游标；Feed 收件箱的 score 本身就是毫秒时间戳。
     */
    public static String encodeMillis(long epochMillis, Long id) {
        if (epochMillis <= 0 || id == null || id <= 0) {
            return null;
        }
        return encode(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE), id);
    }

    /**
     * 解码游标；空值、格式错误或字段越界统一返回 {@code null}。
     */
    public static CursorValue decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(raw.strip()), StandardCharsets.UTF_8);
            String[] parts = text.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            long millis = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (millis < 0 || id <= 0) {
                return null;
            }
            return new CursorValue(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE),
                    id
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 解码后的游标：排序时间与主键。
     */
    public record CursorValue(LocalDateTime time, Long id) {

        /**
         * 转回 epoch 毫秒，便于与 Redis ZSet 的 score 直接比较。
         */
        public long epochMilli() {
            return time.atZone(ZONE).toInstant().toEpochMilli();
        }
    }
}
