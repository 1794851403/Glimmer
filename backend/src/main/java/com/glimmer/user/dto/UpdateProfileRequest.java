package com.glimmer.user.dto;

/**
 * 修改本人资料请求。
 *
 * <p>字段为 {@code null} 表示不修改；{@code bio} 传空字符串表示清空。
 * 校验在 Service 内完成，以便区分 40001（格式错误）与 40004（文本内容非法）。
 */
public record UpdateProfileRequest(
        /** 新昵称；null 表示不修改。提交后去除首尾空白，长度 2~32 个字符。 */
        String nickname,

        /** 新头像 URL；null 表示不修改。必须是 http(s) URL，长度 ≤512。 */
        String avatarUrl,

        /** 新个人简介；null 表示不修改，空字符串表示清空。长度 ≤200。 */
        String bio
) {

    /**
     * 是否一个字段都没提供。
     */
    public boolean isEmpty() {
        return nickname == null && avatarUrl == null && bio == null;
    }
}
