package com.glimmer.content.dto;

/**
 * 审核拒绝 / 下架原因入参。
 */
public record AuditReasonDTO(
        /** 原因，去首尾空白后 1~200 字符。 */
        String reason
) {
}
