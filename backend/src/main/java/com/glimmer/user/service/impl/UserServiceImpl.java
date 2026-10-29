package com.glimmer.user.service.impl;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.mapper.UserMapper;
import com.glimmer.user.service.UserService;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * 本人资料查询与修改实现。
 *
 * <p>封禁状态不在这里重复校验：登录时已拒绝封禁账号，封禁动作会删除 Token 白名单，
 * 未通过 AuthInterceptor 的请求不会进入本 Service。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 32;
    private static final int AVATAR_MAX = 512;
    private static final int BIO_MAX = 200;

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public SelfProfileVO getMyProfile() {
        return SelfProfileVO.from(requireCurrentUser());
    }

    @Override
    public SelfProfileVO updateMyProfile(UpdateProfileDTO request) {
        if (request == null || request.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少提供一个待修改字段");
        }

        UserDO patch = new UserDO();
        patch.setId(UserContext.userId());
        patch.setNickname(normalizeNickname(request.nickname()));
        patch.setAvatarUrl(normalizeAvatarUrl(request.avatarUrl()));
        patch.setBio(normalizeBio(request.bio()));

        int updated = userMapper.updateById(patch);
        if (updated == 0) {
            // 账号不物理删除，行不存在正常不会发生；按登录失效处理。
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return SelfProfileVO.from(requireCurrentUser());
    }

    private UserDO requireCurrentUser() {
        UserDO user = userMapper.selectById(UserContext.userId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    /**
     * 昵称去首尾空白后校验长度；空白昵称按内容非法处理。
     */
    private String normalizeNickname(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        int length = codePointLength(value);
        if (length < NICKNAME_MIN || length > NICKNAME_MAX) {
            throw new BizException(ErrorCode.CONTENT_INVALID,
                    "昵称长度需为 " + NICKNAME_MIN + "~" + NICKNAME_MAX + " 个字符");
        }
        return value;
    }

    /**
     * 头像只校验 http(s) URL 与长度；「必须是已上传图片」等上传模块落地后再收紧。
     */
    private String normalizeAvatarUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "头像 URL 不能为空");
        }
        if (codePointLength(value) > AVATAR_MAX) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "头像 URL 不能超过 " + AVATAR_MAX + " 个字符");
        }
        if (!isHttpUrl(value)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "头像 URL 格式错误");
        }
        return value;
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 简介允许空字符串（清空）；只限制长度，不做 trim，保留用户原始内容。
     */
    private String normalizeBio(String raw) {
        if (raw == null) {
            return null;
        }
        if (codePointLength(raw) > BIO_MAX) {
            throw new BizException(ErrorCode.CONTENT_INVALID,
                    "个人简介不能超过 " + BIO_MAX + " 个字符");
        }
        return raw;
    }

    /**
     * 按 Unicode 码点计数，与 MySQL VARCHAR(n) 的字符口径一致；emoji 不会被算成 2 个字符。
     */
    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
