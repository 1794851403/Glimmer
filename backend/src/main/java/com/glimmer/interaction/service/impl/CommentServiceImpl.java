package com.glimmer.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.interaction.dto.CreateCommentDTO;
import com.glimmer.interaction.entity.CommentDO;
import com.glimmer.interaction.mapper.CommentMapper;
import com.glimmer.interaction.mapper.CommentReplyCount;
import com.glimmer.interaction.service.CommentService;
import com.glimmer.interaction.vo.CommentVO;
import com.glimmer.interaction.vo.ReplyVO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评论实现：两级评论树、一级列表 + replyCount、回复按需加载与软删除。
 *
 * <p>评论关系写 t_comment，帖子评论计数委托 content；两者在同一 MySQL 事务内提交。
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final int CONTENT_MAX = 1000;
    private static final String DELETED_CONTENT = "该评论已删除";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CommentMapper commentMapper;
    private final PostService postService;
    private final UserService userService;

    public CommentServiceImpl(CommentMapper commentMapper, PostService postService, UserService userService) {
        this.commentMapper = commentMapper;
        this.postService = postService;
        this.userService = userService;
    }

    /**
     * 校验帖子可见性与内容后写入评论；回复某条回复时把 parentId 归一化为根评论 ID。
     * 插入成功后调用 content 增加帖子评论数，同一事务提交。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @param request   评论内容、父评论与被回复用户
     * @return 新评论
     */
    @Override
    @Transactional
    public CommentVO create(String rawPostId, CreateCommentDTO request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请求体不能为空");
        }

        Long userId = UserContext.require().userId();
        Long postId = postService.resolvePublicPostId(rawPostId);
        String content = normalizeContent(request.content());

        long parentId = parseParentId(request.parentId());
        Long replyToUserId = parseOptionalUserId(request.replyToUserId());

        if (parentId > ROOT_PARENT_ID) {
            CommentDO parent = commentMapper.selectById(parentId);
            if (parent == null || isDeleted(parent)) {
                throw commentNotFound();
            }
            if (!Objects.equals(parent.getPostId(), postId)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "父评论不属于当前帖子");
            }

            if (isRoot(parent)) {
                if (replyToUserId == null) {
                    replyToUserId = parent.getUserId();
                }
            } else {
                if (replyToUserId == null) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "回复某条回复时必须指定被回复用户");
                }
                // 二级回复统一挂到根评论下，实际被回复人由 replyToUserId 记录
                parentId = parent.getParentId();
                CommentDO root = commentMapper.selectById(parentId);
                if (root == null || isDeleted(root) || !isRoot(root)) {
                    throw commentNotFound();
                }
                if (!Objects.equals(root.getPostId(), postId)) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "父评论不属于当前帖子");
                }
            }
        } else {
            replyToUserId = null;
        }

        CommentDO comment = new CommentDO();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setIsDeleted(0);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        postService.onCommentChanged(postId, 1);
        return toCommentVO(comment);
    }

    /**
     * 一级评论按创建时间倒序游标分页；当前页根评论批量统计 replyCount，
     * 已删除且没有回复的根评论被过滤，允许稀疏页。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量，可为空
     * @return 一级评论游标页
     */
    @Override
    public CursorPage<CommentVO> listByPost(String rawPostId, String rawCursor, Integer rawSize) {
        Long postId = postService.resolvePublicPostId(rawPostId);
        int size = pageSize(rawSize);
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<CommentDO> query = new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getPostId, postId)
                .eq(CommentDO::getParentId, ROOT_PARENT_ID)
                // 包含已删除根评论，后面再按 replyCount 过滤，才能给有回复的根评论返回占位
                .in(CommentDO::getIsDeleted, List.of(0, 1));
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(CommentDO::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(CommentDO::getCreatedAt, cursor.time())
                            .lt(CommentDO::getId, cursor.id())));
        }
        query.orderByDesc(CommentDO::getCreatedAt)
                .orderByDesc(CommentDO::getId)
                .last("LIMIT " + (size + 1));

        List<CommentDO> rows = safeList(commentMapper.selectList(query));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> rootIds = rows.stream()
                .map(CommentDO::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Integer> replyCounts = loadReplyCounts(postId, rootIds);

        List<CommentDO> visibleRows = rows.stream()
                .filter(row -> !isDeleted(row) || replyCounts.getOrDefault(row.getId(), 0) > 0)
                .toList();
        List<Long> authorIds = visibleRows.stream()
                .filter(row -> !isDeleted(row))
                .map(CommentDO::getUserId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, UserBriefVO> authors = userService.getBriefs(authorIds);
        if (authors == null) {
            authors = Map.of();
        }

        List<CommentVO> items = new ArrayList<>(visibleRows.size());
        for (CommentDO row : visibleRows) {
            boolean deleted = isDeleted(row);
            UserBriefVO author = deleted
                    ? null
                    : authors.getOrDefault(row.getUserId(), UserBriefVO.placeholder(row.getUserId()));
            items.add(new CommentVO(
                    String.valueOf(row.getId()),
                    String.valueOf(row.getPostId()),
                    String.valueOf(ROOT_PARENT_ID),
                    deleted ? DELETED_CONTENT : row.getContent(),
                    author,
                    null,
                    deleted,
                    replyCounts.getOrDefault(row.getId(), 0),
                    epochMillis(row.getCreatedAt())
            ));
        }

        String nextCursor = null;
        if (hasMore) {
            CommentDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 按根评论查询未删除的二级回复，按创建时间正序游标分页。
     * 根评论已删除但有回复时仍允许展开查看。
     *
     * @param rawCommentId 根评论 ID，允许数字字符串
     * @param rawCursor    上一页游标，可为空
     * @param rawSize      每页数量，可为空
     * @return 回复游标页
     */
    @Override
    public CursorPage<ReplyVO> listReplies(String rawCommentId, String rawCursor, Integer rawSize) {
        Long commentId = parseCommentId(rawCommentId);
        CommentDO root = commentMapper.selectById(commentId);
        if (root == null) {
            throw commentNotFound();
        }
        if (!isRoot(root)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只能查看根评论的回复");
        }
        postService.resolvePublicPostId(String.valueOf(root.getPostId()));

        int size = pageSize(rawSize);
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<CommentDO> query = new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getPostId, root.getPostId())
                .eq(CommentDO::getParentId, commentId)
                .eq(CommentDO::getIsDeleted, 0);
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .gt(CommentDO::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(CommentDO::getCreatedAt, cursor.time())
                            .gt(CommentDO::getId, cursor.id())));
        }
        query.orderByAsc(CommentDO::getCreatedAt)
                .orderByAsc(CommentDO::getId)
                .last("LIMIT " + (size + 1));

        List<CommentDO> rows = safeList(commentMapper.selectList(query));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (CommentDO row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
            if (row.getReplyToUserId() != null) {
                userIds.add(row.getReplyToUserId());
            }
        }
        Map<Long, UserBriefVO> briefs = userService.getBriefs(userIds);
        if (briefs == null) {
            briefs = Map.of();
        }

        List<ReplyVO> items = new ArrayList<>(rows.size());
        for (CommentDO row : rows) {
            UserBriefVO author = briefs.getOrDefault(row.getUserId(),
                    UserBriefVO.placeholder(row.getUserId()));
            UserBriefVO replyToUser = row.getReplyToUserId() == null
                    ? null
                    : briefs.getOrDefault(row.getReplyToUserId(),
                            UserBriefVO.placeholder(row.getReplyToUserId()));
            items.add(new ReplyVO(
                    String.valueOf(row.getId()),
                    String.valueOf(commentId),
                    row.getContent(),
                    author,
                    replyToUser,
                    epochMillis(row.getCreatedAt())
            ));
        }

        String nextCursor = null;
        if (hasMore) {
            CommentDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 评论作者、帖子作者或管理员可删除；只软删除当前未删除的行，
     * 真正影响 1 行时才让 content 递减评论数，重复删除幂等。
     *
     * @param rawCommentId 评论 ID，允许数字字符串
     */
    @Override
    @Transactional
    public void delete(String rawCommentId) {
        Long commentId = parseCommentId(rawCommentId);
        CommentDO comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw commentNotFound();
        }

        LoginUser user = UserContext.require();
        boolean allowed = Objects.equals(user.userId(), comment.getUserId())
                || user.isAdmin()
                || postService.isPostAuthor(comment.getPostId(), user.userId());
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (isDeleted(comment)) {
            return;
        }

        int affected = commentMapper.update(null, new LambdaUpdateWrapper<CommentDO>()
                .eq(CommentDO::getId, commentId)
                .eq(CommentDO::getIsDeleted, 0)
                .set(CommentDO::getIsDeleted, 1));
        if (affected > 0) {
            postService.onCommentChanged(comment.getPostId(), -1);
        }
    }

    private Map<Long, Integer> loadReplyCounts(Long postId, List<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        List<CommentReplyCount> rows = commentMapper.countVisibleReplies(postId, rootIds);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (CommentReplyCount row : rows) {
            if (row.getParentId() != null) {
                result.put(row.getParentId(), row.getReplyCount() == null ? 0 : row.getReplyCount());
            }
        }
        return result;
    }

    private CommentVO toCommentVO(CommentDO comment) {
        List<Long> userIds = new ArrayList<>();
        userIds.add(comment.getUserId());
        if (comment.getReplyToUserId() != null) {
            userIds.add(comment.getReplyToUserId());
        }
        Map<Long, UserBriefVO> briefs = userService.getBriefs(userIds);
        if (briefs == null) {
            briefs = Map.of();
        }
        UserBriefVO author = briefs.getOrDefault(comment.getUserId(),
                UserBriefVO.placeholder(comment.getUserId()));
        UserBriefVO replyToUser = comment.getReplyToUserId() == null
                ? null
                : briefs.getOrDefault(comment.getReplyToUserId(),
                        UserBriefVO.placeholder(comment.getReplyToUserId()));
        return new CommentVO(
                String.valueOf(comment.getId()),
                String.valueOf(comment.getPostId()),
                String.valueOf(comment.getParentId() == null ? ROOT_PARENT_ID : comment.getParentId()),
                comment.getContent(),
                author,
                replyToUser,
                false,
                0,
                epochMillis(comment.getCreatedAt())
        );
    }

    private String normalizeContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.CONTENT_INVALID, "评论内容不能为空");
        }
        String value = raw.strip();
        if (codePointLength(value) > CONTENT_MAX) {
            throw new BizException(ErrorCode.CONTENT_INVALID,
                    "评论内容不能超过 " + CONTENT_MAX + " 个字符");
        }
        return value;
    }

    private long parseParentId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ROOT_PARENT_ID;
        }
        try {
            long id = Long.parseLong(raw.strip());
            if (id < ROOT_PARENT_ID) {
                throw new BizException(ErrorCode.INVALID_PARAM, "父评论 ID 格式错误");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "父评论 ID 格式错误");
        }
    }

    private Long parseOptionalUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            if (id <= 0) {
                throw new BizException(ErrorCode.INVALID_PARAM, "被回复用户 ID 格式错误");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "被回复用户 ID 格式错误");
        }
    }

    private Long parseCommentId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw commentNotFound();
        }
        try {
            long id = Long.parseLong(raw.strip());
            if (id <= 0) {
                throw commentNotFound();
            }
            return id;
        } catch (NumberFormatException e) {
            throw commentNotFound();
        }
    }

    private int pageSize(Integer rawSize) {
        return rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));
    }

    private boolean isRoot(CommentDO comment) {
        return comment.getParentId() == null || comment.getParentId() == ROOT_PARENT_ID;
    }

    private boolean isDeleted(CommentDO comment) {
        return comment.getIsDeleted() != null && comment.getIsDeleted() == 1;
    }

    private long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private BizException commentNotFound() {
        return new BizException(ErrorCode.COMMENT_NOT_FOUND);
    }
}
