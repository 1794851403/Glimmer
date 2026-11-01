package com.glimmer.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.entity.PostDO;
import com.glimmer.content.entity.PostImageDO;
import com.glimmer.content.entity.PostTagDO;
import com.glimmer.content.entity.TagDO;
import com.glimmer.content.mapper.PostImageMapper;
import com.glimmer.content.mapper.PostMapper;
import com.glimmer.content.mapper.PostTagMapper;
import com.glimmer.content.mapper.TagMapper;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.ImageItemVO;
import com.glimmer.content.vo.PostAuditVO;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.content.vo.TagVO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 发帖与帖子查询实现。
 *
 * <p>MVP 只做最小必要校验；详情与列表直接查 MySQL，不接缓存、Feed 写扩散与 ES。
 */
@Service
public class PostServiceImpl implements PostService {

    private static final int TITLE_MAX = 100;
    private static final int CONTENT_MAX = 20_000;
    private static final int CONTENT_MAX_BYTES = 65_535;
    private static final int IMAGE_MAX = 9;
    private static final int IMAGE_URL_MAX = 512;
    private static final int TAG_MAX = 5;
    private static final int AUDIT_REASON_MAX = 200;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final UserService userService;

    public PostServiceImpl(PostMapper postMapper,
                           PostImageMapper postImageMapper,
                           PostTagMapper postTagMapper,
                           TagMapper tagMapper,
                           UserService userService) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
        this.userService = userService;
    }

    /**
     * 先规范并校验标题 / 正文 / 图片 / 标签，再按角色决定初始状态：
     * 普通用户为 0（待审核），管理员为 1（已发布）。帖子、图片、标签在同一事务内写入。
     *
     * @param request 标题、正文、图片与标签
     * @return 新帖子的 ID、状态与发布时间
     */
    @Override
    @Transactional
    public PostCreateVO create(CreatePostDTO request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请求体不能为空");
        }

        String title = normalizeTitle(request.title());
        String content = normalizeContent(request.content());
        List<String> imageUrls = normalizeImages(request.imageUrls());
        List<Long> tagIds = normalizeTags(request.tagIds());

        LoginUser user = UserContext.require();
        LocalDateTime now = LocalDateTime.now();

        PostDO post = new PostDO();
        post.setUserId(user.userId());
        post.setTitle(title);
        post.setContent(content);
        post.setStatus(user.isAdmin() ? 1 : 0);
        post.setIsDeleted(0);
        post.setPublishedAt(now);
        post.setAiTagStatus(0);
        post.setLastInteractionAt(now);
        postMapper.insert(post);

        insertImages(post.getId(), imageUrls);
        insertTags(post.getId(), tagIds);

        return PostCreateVO.from(post);
    }

    /**
     * 查看帖子详情。
     *
     * <p>公开帖子所有人可见；作者本人可看自己未删除的 0/1/2/3；管理员可看任意未删除帖子。
     * 审核原因与 AI 打标状态只对作者或管理员返回；{@code liked} / {@code collected} 在互动模块接入前固定为 false。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 帖子详情
     */
    @Override
    public PostDetailVO getDetail(String rawPostId) {
        // 1. 帖子必须存在且未删除
        Long postId = parsePostId(rawPostId);
        PostDO post = postMapper.selectById(postId);
        if (post == null || isDeleted(post)) {
            throw notVisible();
        }

        // 2. 公开帖子所有人可见；作者可看自己的全部状态；管理员可看任意未删除帖子
        LoginUser viewer = UserContext.get();
        boolean isAuthor = viewer != null && Objects.equals(viewer.userId(), post.getUserId());
        boolean isAdmin = viewer != null && viewer.isAdmin();
        int status = statusOf(post);
        if (status != 0 && status != 1 && !isAuthor && !isAdmin) {
            throw notVisible();
        }

        // 3. 查询图片，按排序号升序
        List<ImageItemVO> images = safeList(postImageMapper.selectList(
                        new LambdaQueryWrapper<PostImageDO>()
                                .eq(PostImageDO::getPostId, postId)
                                .orderByAsc(PostImageDO::getSortOrder)
                                .orderByAsc(PostImageDO::getId)))
                .stream()
                .map(ImageItemVO::from)
                .toList();

        // 4. 查询标签关联，再用标签字典补名称
        List<PostTagDO> postTags = safeList(postTagMapper.selectList(
                new LambdaQueryWrapper<PostTagDO>()
                        .eq(PostTagDO::getPostId, postId)
                        .orderByAsc(PostTagDO::getId)));
        List<Long> tagIds = postTags.stream()
                .map(PostTagDO::getTagId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, TagVO> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            for (TagDO tag : safeList(tagMapper.selectBatchIds(tagIds))) {
                tagMap.put(tag.getId(), TagVO.from(tag));
            }
        }
        List<TagVO> tags = tagIds.stream()
                .map(tagMap::get)
                .filter(Objects::nonNull)
                .toList();

        // 5. 作者信息；账号不会物理删除，缺失时用占位信息兜底
        UserBriefVO author = userService.getBrief(post.getUserId());
        if (author == null) {
            author = UserBriefVO.placeholder(post.getUserId());
        }

        // 6. 登录用户看别人的帖子时，补充关注状态
        boolean isFollowingAuthor = viewer != null
                && !isAuthor
                && userService.isFollowing(viewer.userId(), post.getUserId());

        // 7. 组装详情
        String statusText = switch (status) {
            case 0 -> "已发布待审核";
            case 1 -> "已发布";
            case 2 -> "审核拒绝";
            case 3 -> "已下架";
            default -> "未知状态";
        };
        boolean canSeeAuditInfo = isAuthor || isAdmin;

        return PostDetailVO.builder()
                .id(String.valueOf(post.getId()))
                .title(post.getTitle())
                .content(post.getContent())
                .coverUrl(images.isEmpty() ? "" : images.get(0).url())
                .images(images)
                .author(author)
                .tags(tags)
                .status(status)
                .statusText(statusText)
                .auditReason(canSeeAuditInfo ? post.getAuditReason() : null)
                .likeCount(value(post.getLikeCount()))
                .commentCount(value(post.getCommentCount()))
                .collectCount(value(post.getCollectCount()))
                .viewCount(value(post.getViewCount()))
                .publishedAt(epochMillis(post.getPublishedAt()))
                .createdAt(epochMillis(post.getCreatedAt()))
                .updatedAt(epochMillis(post.getUpdatedAt()))
                .liked(false)       // 互动模块未接入
                .collected(false)   // 互动模块未接入
                .isFollowingAuthor(isFollowingAuthor)
                .canEdit(isAuthor && status <= 2)
                .canResubmit(isAuthor && status == 2)
                .canDelete(isAuthor)
                .aiTagStatus(canSeeAuditInfo ? post.getAiTagStatus() : null)
                .build();
    }

    /**
     * 只查公开状态（0/1）且未删除的帖子，按 {@code published_at DESC, id DESC} 游标分页；
     * 多取一条判断 {@code hasMore}，再批量补图片、标签与作者摘要，避免 N+1。
     *
     * @param authorIds 作者 ID 集合，为空时返回空页
     * @param rawCursor 上一页游标，可为空，坏游标按首页处理
     * @param rawSize   每页数量，默认 20，越界夹到 1~50
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> listByAuthors(Collection<Long> authorIds, String rawCursor, Integer rawSize) {
        List<Long> ids = authorIds == null
                ? List.of()
                : authorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return CursorPage.empty();
        }

        int size = rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));

        // 坏游标按首页处理
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<PostDO>()
                .in(PostDO::getUserId, ids)
                .in(PostDO::getStatus, List.of(0, 1))
                .eq(PostDO::getIsDeleted, 0);
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(PostDO::getPublishedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(PostDO::getPublishedAt, cursor.time())
                            .lt(PostDO::getId, cursor.id())));
        }
        query.orderByDesc(PostDO::getPublishedAt)
                .orderByDesc(PostDO::getId)
                .last("LIMIT " + (size + 1));

        List<PostDO> rows = safeList(postMapper.selectList(query));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        // 批量补图片、标签和作者，避免逐条查询
        List<Long> postIds = rows.stream().map(PostDO::getId).filter(Objects::nonNull).toList();

        Map<Long, List<ImageItemVO>> imagesByPost = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<PostImageDO> images = safeList(postImageMapper.selectList(
                    new LambdaQueryWrapper<PostImageDO>()
                            .in(PostImageDO::getPostId, postIds)
                            .orderByAsc(PostImageDO::getSortOrder)
                            .orderByAsc(PostImageDO::getId)));
            for (PostImageDO image : images) {
                if (image.getPostId() != null) {
                    imagesByPost.computeIfAbsent(image.getPostId(), key -> new ArrayList<>())
                            .add(ImageItemVO.from(image));
                }
            }
        }

        Map<Long, List<Long>> tagIdsByPost = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<PostTagDO> links = safeList(postTagMapper.selectList(
                    new LambdaQueryWrapper<PostTagDO>()
                            .in(PostTagDO::getPostId, postIds)
                            .orderByAsc(PostTagDO::getId)));
            for (PostTagDO link : links) {
                if (link.getPostId() != null && link.getTagId() != null) {
                    tagIdsByPost.computeIfAbsent(link.getPostId(), key -> new ArrayList<>())
                            .add(link.getTagId());
                }
            }
        }

        List<Long> allTagIds = tagIdsByPost.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
        Map<Long, TagVO> tagsById = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            for (TagDO tag : safeList(tagMapper.selectBatchIds(allTagIds))) {
                if (tag.getId() != null) {
                    tagsById.put(tag.getId(), TagVO.from(tag));
                }
            }
        }

        List<Long> authorIdsForPage = rows.stream()
                .map(PostDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserBriefVO> authors = userService.getBriefs(authorIdsForPage);
        if (authors == null) {
            authors = Map.of();
        }

        List<PostSummaryVO> items = new ArrayList<>(rows.size());
        for (PostDO post : rows) {
            List<ImageItemVO> images = imagesByPost.getOrDefault(post.getId(), List.of());
            List<TagVO> tags = tagIdsByPost.getOrDefault(post.getId(), List.of()).stream()
                    .map(tagsById::get)
                    .filter(Objects::nonNull)
                    .toList();
            UserBriefVO author = authors.getOrDefault(post.getUserId(),
                    UserBriefVO.placeholder(post.getUserId()));
            items.add(new PostSummaryVO(
                    String.valueOf(post.getId()),
                    post.getTitle(),
                    images.isEmpty() ? "" : images.get(0).url(),
                    author,
                    tags,
                    value(post.getLikeCount()),
                    value(post.getCommentCount()),
                    value(post.getCollectCount()),
                    value(post.getViewCount()),
                    epochMillis(post.getPublishedAt()),
                    false,
                    false
            ));
        }

        String nextCursor = null;
        if (hasMore) {
            PostDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getPublishedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 校验作者身份与状态（已下架不可编辑），全量替换标题 / 正文 / 图片 / 标签；
     * 更新主表时统一回到 0 并清空审核信息，图片与标签在同一事务内先删后插。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @param request   标题、正文、图片与标签
     * @return 编辑后的帖子详情
     */
    @Override
    @Transactional
    public PostDetailVO update(String rawPostId, CreatePostDTO request) {
        PostDO post = requireAuthorPost(rawPostId);
        if (isDeleted(post)) {
            throw notVisible();
        }
        if (statusOf(post) == 3) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "已下架帖子不可编辑");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请求体不能为空");
        }

        String title = normalizeTitle(request.title());
        String content = normalizeContent(request.content());
        List<String> imageUrls = normalizeImages(request.imageUrls());
        List<Long> tagIds = normalizeTags(request.tagIds());

        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .eq(PostDO::getId, post.getId())
                .set(PostDO::getTitle, title)
                .set(PostDO::getContent, content)
                .set(PostDO::getStatus, 0)
                .set(PostDO::getAuditReason, null)
                .set(PostDO::getAuditedAt, null)
                .set(PostDO::getAuditedBy, null));

        postImageMapper.delete(new LambdaQueryWrapper<PostImageDO>()
                .eq(PostImageDO::getPostId, post.getId()));
        postTagMapper.delete(new LambdaQueryWrapper<PostTagDO>()
                .eq(PostTagDO::getPostId, post.getId()));
        insertImages(post.getId(), imageUrls);
        insertTags(post.getId(), tagIds);

        return getDetail(String.valueOf(post.getId()));
    }

    /**
     * 校验作者身份后置 {@code is_deleted=1}；已删除的帖子直接返回，保证重复删除幂等。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     */
    @Override
    public void delete(String rawPostId) {
        PostDO post = requireAuthorPost(rawPostId);
        if (isDeleted(post)) {
            return;
        }
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .eq(PostDO::getId, post.getId())
                .set(PostDO::getIsDeleted, 1));
    }

    /**
     * 校验作者身份且当前状态为 2（审核拒绝），不修改内容，状态 2 → 0 并清空审核信息。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 重提后的帖子状态
     */
    @Override
    public PostCreateVO resubmit(String rawPostId) {
        PostDO post = requireAuthorPost(rawPostId);
        if (isDeleted(post)) {
            throw notVisible();
        }
        if (statusOf(post) != 2) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅审核拒绝的帖子可重新提交");
        }
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .eq(PostDO::getId, post.getId())
                .set(PostDO::getStatus, 0)
                .set(PostDO::getAuditReason, null)
                .set(PostDO::getAuditedAt, null)
                .set(PostDO::getAuditedBy, null));
        post.setStatus(0);
        post.setAuditReason(null);
        return PostCreateVO.resubmit(post);
    }

    /**
     * 仅管理员且不能审核自己的帖子；用 CAS 把状态 0 → 1，成功后清空拒绝原因。
     * 若 CAS 未命中但当前已是 1，按幂等成功返回；其他状态变化返回状态冲突。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 审核结果
     */
    @Override
    public PostAuditVO approve(String rawPostId) {
        LoginUser admin = UserContext.require();
        PostDO post = requireAuditablePost(rawPostId);
        requireNotSelfAudit(admin, post);

        LocalDateTime now = LocalDateTime.now();
        int updated = auditUpdate(post.getId(), List.of(0), 1, null, now, admin.userId());
        if (updated == 0) {
            PostDO current = postMapper.selectById(post.getId());
            if (current != null && statusOf(current) == 1) {
                return PostAuditVO.from(current);
            }
            throw new BizException(ErrorCode.AUDIT_STATE_CHANGED);
        }
        return PostAuditVO.of(post, 1, null, now, admin.userId());
    }

    /**
     * 仅管理员且不能审核自己的帖子；原因去空白后校验 1~200 字符；
     * CAS 把状态 0 → 2，失败说明状态已变化，返回状态冲突。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @param rawReason 拒绝原因
     * @return 审核结果
     */
    @Override
    public PostAuditVO reject(String rawPostId, String rawReason) {
        LoginUser admin = UserContext.require();
        PostDO post = requireAuditablePost(rawPostId);
        requireNotSelfAudit(admin, post);
        String reason = normalizeAuditReason(rawReason);

        LocalDateTime now = LocalDateTime.now();
        int updated = auditUpdate(post.getId(), List.of(0), 2, reason, now, admin.userId());
        if (updated == 0) {
            throw new BizException(ErrorCode.AUDIT_STATE_CHANGED);
        }
        return PostAuditVO.of(post, 2, reason, now, admin.userId());
    }

    /**
     * 仅管理员且不能操作自己的帖子；原因去空白后校验 1~200 字符；
     * CAS 把状态 0/1 → 3，失败返回状态冲突。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @param rawReason 下架原因
     * @return 审核结果
     */
    @Override
    public PostAuditVO takedown(String rawPostId, String rawReason) {
        LoginUser admin = UserContext.require();
        PostDO post = requireAuditablePost(rawPostId);
        requireNotSelfAudit(admin, post);
        String reason = normalizeAuditReason(rawReason);

        LocalDateTime now = LocalDateTime.now();
        int updated = auditUpdate(post.getId(), List.of(0, 1), 3, reason, now, admin.userId());
        if (updated == 0) {
            throw new BizException(ErrorCode.AUDIT_STATE_CHANGED);
        }
        return PostAuditVO.of(post, 3, reason, now, admin.userId());
    }

    /**
     * 仅管理员且不能操作自己的帖子；CAS 把状态 3 → 1，失败返回状态冲突。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 审核结果
     */
    @Override
    public PostAuditVO restore(String rawPostId) {
        LoginUser admin = UserContext.require();
        PostDO post = requireAuditablePost(rawPostId);
        requireNotSelfAudit(admin, post);

        LocalDateTime now = LocalDateTime.now();
        int updated = auditUpdate(post.getId(), List.of(3), 1, null, now, admin.userId());
        if (updated == 0) {
            throw new BizException(ErrorCode.AUDIT_STATE_CHANGED);
        }
        return PostAuditVO.of(post, 1, null, now, admin.userId());
    }

    private int auditUpdate(Long postId, List<Integer> fromStatuses, int toStatus, String reason,
                            LocalDateTime auditedAt, Long adminId) {
        return postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .eq(PostDO::getId, postId)
                .eq(PostDO::getIsDeleted, 0)
                .in(PostDO::getStatus, fromStatuses)
                .set(PostDO::getStatus, toStatus)
                .set(PostDO::getAuditReason, reason)
                .set(PostDO::getAuditedAt, auditedAt)
                .set(PostDO::getAuditedBy, adminId));
    }

    private PostDO requireAuthorPost(String rawPostId) {
        PostDO post = postMapper.selectById(parsePostId(rawPostId));
        if (post == null) {
            throw notVisible();
        }
        LoginUser user = UserContext.require();
        if (!Objects.equals(user.userId(), post.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    private PostDO requireAuditablePost(String rawPostId) {
        if (!StringUtils.hasText(rawPostId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        long postId;
        try {
            postId = Long.parseLong(rawPostId.strip());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (postId <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        PostDO post = postMapper.selectById(postId);
        if (post == null || isDeleted(post)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return post;
    }

    private void requireNotSelfAudit(LoginUser admin, PostDO post) {
        if (Objects.equals(admin.userId(), post.getUserId())) {
            throw new BizException(ErrorCode.AUDIT_SELF_FORBIDDEN);
        }
    }

    private String normalizeAuditReason(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审核原因不能为空");
        }
        String value = raw.strip();
        if (codePointLength(value) > AUDIT_REASON_MAX) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "审核原因不能超过 " + AUDIT_REASON_MAX + " 个字符");
        }
        return value;
    }

    private void insertImages(Long postId, List<String> imageUrls) {
        for (int i = 0; i < imageUrls.size(); i++) {
            PostImageDO image = new PostImageDO();
            image.setPostId(postId);
            image.setUrl(imageUrls.get(i));
            image.setSortOrder(i);
            postImageMapper.insert(image);
        }
    }

    private void insertTags(Long postId, List<Long> tagIds) {
        for (Long tagId : tagIds) {
            PostTagDO postTag = new PostTagDO();
            postTag.setPostId(postId);
            postTag.setTagId(tagId);
            postTag.setSource(0);
            postTagMapper.insert(postTag);
        }
    }

    private String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.CONTENT_INVALID, "标题不能为空");
        }
        String value = raw.strip();
        if (codePointLength(value) > TITLE_MAX) {
            throw new BizException(ErrorCode.CONTENT_INVALID,
                    "标题不能超过 " + TITLE_MAX + " 个字符");
        }
        return value;
    }

    private String normalizeContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.CONTENT_INVALID, "正文不能为空");
        }
        if (codePointLength(raw) > CONTENT_MAX) {
            throw new BizException(ErrorCode.CONTENT_INVALID,
                    "正文不能超过 " + CONTENT_MAX + " 个字符");
        }
        // TEXT 的上限是 65535 字节；4 字节字符较多时，仅按字符数校验仍可能写库失败。
        if (raw.getBytes(StandardCharsets.UTF_8).length > CONTENT_MAX_BYTES) {
            throw new BizException(ErrorCode.CONTENT_INVALID, "正文过长");
        }
        return raw;
    }

    private List<String> normalizeImages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少上传一张图片");
        }
        if (raw.size() > IMAGE_MAX) {
            throw new BizException(ErrorCode.UPLOAD_COUNT_EXCEEDED);
        }

        List<String> urls = new ArrayList<>(raw.size());
        for (String rawUrl : raw) {
            if (rawUrl == null || rawUrl.isBlank()) {
                throw new BizException(ErrorCode.INVALID_PARAM, "图片 URL 不能为空");
            }
            String url = rawUrl.strip();
            if (codePointLength(url) > IMAGE_URL_MAX) {
                throw new BizException(ErrorCode.INVALID_PARAM,
                        "图片 URL 不能超过 " + IMAGE_URL_MAX + " 个字符");
            }
            urls.add(url);
        }
        return urls;
    }

    private List<Long> normalizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > TAG_MAX) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "标签不能超过 " + TAG_MAX + " 个");
        }

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String rawId : raw) {
            if (rawId == null || rawId.isBlank()) {
                throw new BizException(ErrorCode.INVALID_PARAM, "标签 ID 不能为空");
            }
            long id;
            try {
                id = Long.parseLong(rawId.strip());
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.INVALID_PARAM, "标签 ID 格式错误");
            }
            if (id <= 0) {
                throw new BizException(ErrorCode.INVALID_PARAM, "标签 ID 格式错误");
            }
            ids.add(id);
        }

        if (ids.isEmpty()) {
            return List.of();
        }
        List<TagDO> tags = tagMapper.selectBatchIds(ids);
        if (tags == null || tags.size() != ids.size()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "标签不存在");
        }
        return List.copyOf(ids);
    }

    private Long parsePostId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw notVisible();
        }
        try {
            long id = Long.parseLong(raw.strip());
            if (id <= 0) {
                throw notVisible();
            }
            return id;
        } catch (NumberFormatException e) {
            throw notVisible();
        }
    }

    private boolean isDeleted(PostDO post) {
        return post.getIsDeleted() != null && post.getIsDeleted() == 1;
    }

    private int statusOf(PostDO post) {
        return post.getStatus() == null ? 0 : post.getStatus();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
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

    private BizException notVisible() {
        return new BizException(ErrorCode.POST_NOT_VISIBLE);
    }
}
