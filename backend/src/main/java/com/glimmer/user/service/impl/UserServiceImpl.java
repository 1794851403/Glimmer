package com.glimmer.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.UpdateProfileDTO;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.entity.UserFollowDO;
import com.glimmer.user.event.UserFollowedEvent;
import com.glimmer.user.mapper.UserFollowMapper;
import com.glimmer.user.mapper.UserMapper;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.FollowResultVO;
import com.glimmer.user.vo.FollowUserVO;
import com.glimmer.user.vo.SelfProfileVO;
import com.glimmer.user.vo.UserBriefVO;
import com.glimmer.user.vo.UserProfileVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
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

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final ApplicationEventPublisher eventPublisher;

    public UserServiceImpl(UserMapper userMapper, UserFollowMapper userFollowMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 按 {@link UserContext#userId()} 查询当前用户；行不存在按登录失效处理。
     *
     * @return 当前用户的完整本人资料
     */
    @Override
    public SelfProfileVO getMyProfile() {
        return SelfProfileVO.from(requireCurrentUser());
    }

    /**
     * 只把请求中出现的字段写入更新对象，未提供的字段保持 {@code null} 不参与 SET；
     * {@code bio} 允许空字符串表示清空。更新成功后重新查询并返回完整资料。
     *
     * @param request 待修改字段，至少一个
     * @return 修改后的完整本人资料
     */
    @Override
    public SelfProfileVO updateMyProfile(UpdateProfileDTO request) {
        if (request == null || request.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少提供一个待修改字段");
        }

        // 昵称去首尾空白后校验长度；空白昵称按内容非法处理
        String nickname = request.nickname();
        if (nickname != null) {
            nickname = nickname.strip();
            int length = nickname.codePointCount(0, nickname.length());
            if (length < NICKNAME_MIN || length > NICKNAME_MAX) {
                throw new BizException(ErrorCode.CONTENT_INVALID,
                        "昵称长度需为 " + NICKNAME_MIN + "~" + NICKNAME_MAX + " 个字符");
            }
        }

        // 头像只校验 http(s) URL 与长度；「必须是已上传图片」等上传模块落地后再收紧
        String avatarUrl = request.avatarUrl();
        if (avatarUrl != null) {
            avatarUrl = avatarUrl.strip();
            if (avatarUrl.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAM, "头像 URL 不能为空");
            }
            if (avatarUrl.codePointCount(0, avatarUrl.length()) > AVATAR_MAX) {
                throw new BizException(ErrorCode.INVALID_PARAM,
                        "头像 URL 不能超过 " + AVATAR_MAX + " 个字符");
            }
            boolean isHttpUrl;
            try {
                URI uri = URI.create(avatarUrl);
                String scheme = uri.getScheme();
                isHttpUrl = scheme != null
                        && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && uri.getHost() != null;
            } catch (IllegalArgumentException e) {
                isHttpUrl = false;
            }
            if (!isHttpUrl) {
                throw new BizException(ErrorCode.INVALID_PARAM, "头像 URL 格式错误");
            }
        }

        // 简介允许空字符串（清空）；只限制长度，不做 trim，保留用户原始内容
        String bio = request.bio();
        if (bio != null && bio.codePointCount(0, bio.length()) > BIO_MAX) {
            throw new BizException(ErrorCode.CONTENT_INVALID,
                    "个人简介不能超过 " + BIO_MAX + " 个字符");
        }

        UserDO patch = new UserDO();
        patch.setId(UserContext.userId());
        patch.setNickname(nickname);
        patch.setAvatarUrl(avatarUrl);
        patch.setBio(bio);

        int updated = userMapper.updateById(patch);
        if (updated == 0) {
            // 账号不物理删除，行不存在正常不会发生；按登录失效处理。
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return SelfProfileVO.from(requireCurrentUser());
    }

    /**
     * 查询公开主页：校验用户存在且未封禁，再按登录态计算关注关系。
     *
     * @param rawUserId 用户 ID，允许数字字符串
     * @return 用户公开主页
     */
    @Override
    public UserProfileVO getPublicProfile(String rawUserId) {
        UserDO user = requirePublicUser(rawUserId);
        LoginUser viewer = UserContext.get();
        boolean isMe = viewer != null && viewer.userId().equals(user.getId());
        boolean isFollowing = viewer != null
                && !isMe
                && isFollowing(viewer.userId(), user.getId());
        return UserProfileVO.from(user, isFollowing, isMe);
    }

    /**
     * 解析公开用户 ID；不存在、非法或已封禁统一按 40401 处理。
     *
     * @param rawUserId 用户 ID，允许数字字符串
     * @return 用户 ID
     */
    @Override
    public Long resolvePublicUserId(String rawUserId) {
        return requirePublicUser(rawUserId).getId();
    }

    /**
     * 按主键查询单个用户，只组装对外可见的简要字段。
     *
     * @param userId 用户 ID，可为空
     * @return 用户简要信息；ID 为空或用户不存在时返回 {@code null}
     */
    @Override
    public UserBriefVO getBrief(Long userId) {
        if (userId == null) {
            return null;
        }
        UserDO user = userMapper.selectById(userId);
        return user == null ? null : UserBriefVO.from(user);
    }

    /**
     * ID 去空去重后一次批量查询，结果以用户 ID 为键返回；不存在的用户不会出现在 Map 中。
     *
     * @param userIds 用户 ID 集合，允许为空或重复
     * @return 用户 ID → 简要信息
     */
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

    /**
     * 空参数或自己关注自己直接返回 {@code false}，否则统计关注关系行数。
     *
     * @param followerId 关注者
     * @param followeeId 被关注者
     * @return 是否已关注
     */
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

    /**
     * 查询关注者名下的全部关系行，去重并排除自己后返回被关注用户 ID。
     *
     * @param followerId 关注者，可为空
     * @return 被关注用户 ID 列表；未关注任何人时返回空列表
     */
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

    /**
     * 直接委托 Mapper 做有界批量查询；空作者返回空列表，批大小夹在 1~1000。
     *
     * @param followeeId      被关注者 ID
     * @param afterFollowerId 上一批最后一个粉丝 ID，可为空
     * @param limit           批大小
     * @return 粉丝 ID 列表
     */
    @Override
    public List<Long> listFollowerIds(Long followeeId, Long afterFollowerId, int limit) {
        if (followeeId == null) {
            return List.of();
        }
        long after = afterFollowerId == null ? 0L : afterFollowerId;
        int batchSize = Math.max(1, Math.min(limit, 1000));
        List<Long> rows = userFollowMapper.listFollowerIds(followeeId, after, batchSize);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().filter(Objects::nonNull).toList();
    }

    /**
     * 解析目标用户并禁止自关注；先查关系再插入，重复关注直接按已关注返回。
     * 并发重复关注由唯一键兜底；真正插入时在同一事务内递增双方计数。
     *
     * @param rawUserId 目标用户 ID，允许数字字符串
     * @return 目标用户 ID、关注状态与最新粉丝数
     */
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
            // 只有真正插入关系后才回填；重复关注不会触发 Feed 回填。
            eventPublisher.publishEvent(new UserFollowedEvent(viewer.userId(), targetId));
        }
        return new FollowResultVO(String.valueOf(targetId), true, followerCount);
    }

    /**
     * 解析目标用户并禁止自取关；删除关注关系，只有实际删到行时才在同一事务内递减双方计数，
     * 未关注时同样返回成功（幂等）。
     *
     * @param rawUserId 目标用户 ID，允许数字字符串
     * @return 目标用户 ID、关注状态与最新粉丝数
     */
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

    /**
     * 粉丝列表：以 {@code followee_id = 目标用户} 查关注关系，其余分页与组装逻辑复用
     * {@link #listRelations}。
     *
     * @param rawUserId 目标用户 ID，允许数字字符串
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量，默认 20，越界夹到 1~50
     * @return 粉丝简要信息游标页
     */
    @Override
    public CursorPage<FollowUserVO> listFollowers(String rawUserId, String rawCursor, Integer rawSize) {
        return listRelations(parseUserId(rawUserId), true, rawCursor, rawSize);
    }

    /**
     * 关注列表：以 {@code follower_id = 目标用户} 查关注关系，其余分页与组装逻辑复用
     * {@link #listRelations}。
     *
     * @param rawUserId 目标用户 ID，允许数字字符串
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量，默认 20，越界夹到 1~50
     * @return 关注用户简要信息游标页
     */
    @Override
    public CursorPage<FollowUserVO> listFollowing(String rawUserId, String rawCursor, Integer rawSize) {
        return listRelations(parseUserId(rawUserId), false, rawCursor, rawSize);
    }

    private CursorPage<FollowUserVO> listRelations(Long userId, boolean followers,
                                                   String rawCursor, Integer rawSize) {
        requireExistingUser(userId);
        int size = rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));

        // 坏游标按首页处理
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<UserFollowDO> query = new LambdaQueryWrapper<>();
        if (followers) {
            query.eq(UserFollowDO::getFolloweeId, userId);
        } else {
            query.eq(UserFollowDO::getFollowerId, userId);
        }
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(UserFollowDO::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(UserFollowDO::getCreatedAt, cursor.time())
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

        // 批量查询当前登录用户是否关注了列表中的用户，避免逐条查询
        LoginUser viewer = UserContext.get();
        Set<Long> followedByViewer = Set.of();
        if (viewer != null && !userIds.isEmpty()) {
            List<UserFollowDO> followRows = safeList(userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollowDO>()
                            .eq(UserFollowDO::getFollowerId, viewer.userId())
                            .in(UserFollowDO::getFolloweeId, userIds)));
            followedByViewer = followRows.stream()
                    .map(UserFollowDO::getFolloweeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        List<FollowUserVO> items = new ArrayList<>(rows.size());
        for (UserFollowDO row : rows) {
            Long id = followers ? row.getFollowerId() : row.getFolloweeId();
            if (id == null) {
                continue;
            }
            UserBriefVO brief = briefs.getOrDefault(id, UserBriefVO.placeholder(id));
            items.add(FollowUserVO.from(brief, followedByViewer.contains(id)));
        }

        String nextCursor = null;
        if (hasMore) {
            UserFollowDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
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

    private UserDO requirePublicUser(String rawUserId) {
        Long userId = parseUserId(rawUserId);
        return requireExistingUser(userId);
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

}
