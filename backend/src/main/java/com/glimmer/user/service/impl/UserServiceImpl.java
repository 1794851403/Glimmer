package com.glimmer.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.entity.UserFollowDO;
import com.glimmer.user.mapper.UserFollowMapper;
import com.glimmer.user.mapper.UserMapper;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.FollowResultVO;
import com.glimmer.user.vo.FollowUserVO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.vo.UserBriefVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;

    public UserServiceImpl(UserMapper userMapper, UserFollowMapper userFollowMapper) {
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
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

    @Override
    public UserBriefVO getBrief(Long userId) {
        if (userId == null) {
            return null;
        }
        UserDO user = userMapper.selectById(userId);
        return user == null ? null : UserBriefVO.from(user);
    }

    @Override
    public Map<Long, UserBriefVO> getBriefs(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UserDO> users = userMapper.selectBatchIds(ids);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserBriefVO> result = new LinkedHashMap<>();
        for (UserDO user : users) {
            result.put(user.getId(), UserBriefVO.from(user));
        }
        return result;
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null || followerId.equals(followeeId)) {
            return false;
        }
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, followerId)
                .eq(UserFollowDO::getFolloweeId, followeeId));
        return count != null && count > 0;
    }

    @Override
    public List<Long> listFollowingIds(Long followerId) {
        if (followerId == null) {
            return List.of();
        }
        List<UserFollowDO> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, followerId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> followeeIds = new LinkedHashSet<>();
        for (UserFollowDO row : rows) {
            Long followeeId = row.getFolloweeId();
            if (followeeId != null && !followeeId.equals(followerId)) {
                followeeIds.add(followeeId);
            }
        }
        return List.copyOf(followeeIds);
    }

    @Override
    @Transactional
    public FollowResultVO follow(String rawUserId) {
        LoginUser viewer = UserContext.require();
        Long targetId = parseUserId(rawUserId);
        if (targetId.equals(viewer.userId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "不能关注自己");
        }
        UserDO target = requireExistingUser(targetId);

        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, viewer.userId())
                .eq(UserFollowDO::getFolloweeId, targetId));
        boolean exists = count != null && count > 0;
        int followerCount = value(target.getFollowerCount());
        if (!exists) {
            UserFollowDO relation = new UserFollowDO();
            relation.setFollowerId(viewer.userId());
            relation.setFolloweeId(targetId);
            try {
                userFollowMapper.insert(relation);
            } catch (DuplicateKeyException e) {
                // 并发重复关注：唯一键兜底，按已关注处理。
                return new FollowResultVO(String.valueOf(targetId), true, followerCount);
            }
            adjustFollowCounts(viewer.userId(), targetId, 1);
            followerCount++;
        }
        return new FollowResultVO(String.valueOf(targetId), true, followerCount);
    }

    @Override
    @Transactional
    public FollowResultVO unfollow(String rawUserId) {
        LoginUser viewer = UserContext.require();
        Long targetId = parseUserId(rawUserId);
        if (targetId.equals(viewer.userId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "不能取关自己");
        }
        UserDO target = requireExistingUser(targetId);

        int deleted = userFollowMapper.delete(new LambdaQueryWrapper<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, viewer.userId())
                .eq(UserFollowDO::getFolloweeId, targetId));
        int followerCount = value(target.getFollowerCount());
        if (deleted > 0) {
            adjustFollowCounts(viewer.userId(), targetId, -1);
            followerCount = Math.max(0, followerCount - 1);
        }
        return new FollowResultVO(String.valueOf(targetId), false, followerCount);
    }

    @Override
    public CursorPage<FollowUserVO> listFollowers(String rawUserId, String rawCursor, Integer rawSize) {
        return listRelations(parseUserId(rawUserId), true, rawCursor, rawSize);
    }

    @Override
    public CursorPage<FollowUserVO> listFollowing(String rawUserId, String rawCursor, Integer rawSize) {
        return listRelations(parseUserId(rawUserId), false, rawCursor, rawSize);
    }

    private CursorPage<FollowUserVO> listRelations(Long userId, boolean followers,
                                                   String rawCursor, Integer rawSize) {
        requireExistingUser(userId);
        int size = normalizePageSize(rawSize);
        FollowCursor cursor = decodeCursor(rawCursor);

        LambdaQueryWrapper<UserFollowDO> query = new LambdaQueryWrapper<>();
        if (followers) {
            query.eq(UserFollowDO::getFolloweeId, userId);
        } else {
            query.eq(UserFollowDO::getFollowerId, userId);
        }
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(UserFollowDO::getCreatedAt, cursor.createdAt())
                    .or(sameTime -> sameTime
                            .eq(UserFollowDO::getCreatedAt, cursor.createdAt())
                            .lt(UserFollowDO::getId, cursor.id())));
        }
        query.orderByDesc(UserFollowDO::getCreatedAt)
                .orderByDesc(UserFollowDO::getId)
                .last("LIMIT " + (size + 1));

        List<UserFollowDO> rows = safeList(userFollowMapper.selectList(query));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> userIds = rows.stream()
                .map(row -> followers ? row.getFollowerId() : row.getFolloweeId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, UserBriefVO> briefs = getBriefs(userIds);
        Set<Long> followedByViewer = viewerFollowing(userIds);

        List<FollowUserVO> items = new ArrayList<>(rows.size());
        for (UserFollowDO row : rows) {
            Long id = followers ? row.getFollowerId() : row.getFolloweeId();
            if (id == null) {
                continue;
            }
            UserBriefVO brief = briefs.getOrDefault(id, UserBriefVO.placeholder(id));
            items.add(FollowUserVO.from(brief, followedByViewer.contains(id)));
        }

        String nextCursor = hasMore ? encodeCursor(rows.get(rows.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 批量查询当前登录用户是否关注了列表中的用户，避免逐条查询。
     */
    private Set<Long> viewerFollowing(List<Long> userIds) {
        LoginUser viewer = UserContext.get();
        if (viewer == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<UserFollowDO> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, viewer.userId())
                .in(UserFollowDO::getFolloweeId, userIds));
        return safeList(rows).stream()
                .map(UserFollowDO::getFolloweeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void adjustFollowCounts(Long followerId, Long followeeId, int delta) {
        if (delta > 0) {
            userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                    .eq(UserDO::getId, followerId)
                    .setSql("following_count = following_count + 1"));
            userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                    .eq(UserDO::getId, followeeId)
                    .setSql("follower_count = follower_count + 1"));
        } else {
            // UNSIGNED 列相减会报错，先判零再减。
            userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                    .eq(UserDO::getId, followerId)
                    .setSql("following_count = IF(following_count > 0, following_count - 1, 0)"));
            userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                    .eq(UserDO::getId, followeeId)
                    .setSql("follower_count = IF(follower_count > 0, follower_count - 1, 0)"));
        }
    }

    private UserDO requireExistingUser(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 1)) {
            throw notFoundUser();
        }
        return user;
    }

    private Long parseUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw notFoundUser();
        }
        try {
            long id = Long.parseLong(raw.strip());
            if (id <= 0) {
                throw notFoundUser();
            }
            return id;
        } catch (NumberFormatException e) {
            throw notFoundUser();
        }
    }

    private BizException notFoundUser() {
        return new BizException(ErrorCode.NOT_FOUND, "用户不存在");
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private String encodeCursor(UserFollowDO relation) {
        if (relation.getCreatedAt() == null || relation.getId() == null) {
            return null;
        }
        String raw = relation.getCreatedAt().atZone(ZONE).toInstant().toEpochMilli()
                + ":" + relation.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private FollowCursor decodeCursor(String raw) {
        if (!StringUtils.hasText(raw)) {
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
            return new FollowCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE),
                    id
            );
        } catch (RuntimeException e) {
            // 游标对客户端不透明，坏游标按首页处理。
            return null;
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
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

    private record FollowCursor(LocalDateTime createdAt, Long id) {
    }
}
