package com.glimmer.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.PageResult;
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
import com.glimmer.content.service.InteractionStatusProvider;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.AdminPostSummaryVO;
import com.glimmer.content.vo.ImageItemVO;
import com.glimmer.content.vo.MyPostSummaryVO;
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
import java.util.Set;
import java.util.function.Function;

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
    private static final int ADMIN_MAX_PAGE_SIZE = 100;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final UserService userService;
    private final InteractionStatusProvider interactionStatusProvider;

    public PostServiceImpl(PostMapper postMapper,
                           PostImageMapper postImageMapper,
                           PostTagMapper postTagMapper,
                           TagMapper tagMapper,
                           UserService userService,
                           InteractionStatusProvider interactionStatusProvider) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
        this.userService = userService;
        this.interactionStatusProvider = interactionStatusProvider;
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
     * 审核原因与 AI 打标状态只对作者或管理员返回；登录用户补真实的 {@code liked} / {@code collected}。
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

        return assembleDetail(post, viewer, false);
    }

    /**
     * 管理端查看帖子详情。
     *
     * <p>只校验帖子存在且未删除，不限状态；不存在 / ID 非法 / 已删除统一返回 40401。
     * 不查询关注关系与当前管理员的点赞 / 收藏状态。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 管理端帖子详情
     */
    @Override
    public PostDetailVO getAdminDetail(String rawPostId) {
        PostDO post = requireAuditablePost(rawPostId);
        return assembleDetail(post, UserContext.require(), true);
    }

    /**
     * 组装详情。调用方已完成可见性 / 存在性校验，这里只负责查询与拼装。
     *
     * <p>{@code adminView=true} 时补充最近裁决信息与 {@code canAudit}，
     * 跳过关注和互动状态查询，用户操作标记统一为 false。
     *
     * @param post      帖子行
     * @param viewer    当前登录用户，游客为 null
     * @param adminView 是否管理端视图
     * @return 帖子详情
     */
    private PostDetailVO assembleDetail(PostDO post, LoginUser viewer, boolean adminView) {
        Long postId = post.getId();
        boolean isAuthor = viewer != null && Objects.equals(viewer.userId(), post.getUserId());
        boolean isAdmin = viewer != null && viewer.isAdmin();
        boolean canSeeAuditInfo = adminView || isAuthor || isAdmin;
        int status = statusOf(post);

        // 查询图片，按排序号升序
        List<ImageItemVO> images = safeList(postImageMapper.selectList(
                        new LambdaQueryWrapper<PostImageDO>()
                                .eq(PostImageDO::getPostId, postId)
                                .orderByAsc(PostImageDO::getSortOrder)
                                .orderByAsc(PostImageDO::getId)))
                .stream()
                .map(ImageItemVO::from)
                .toList();

        // 查询标签关联，再用标签字典补名称
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

        // 作者信息；账号不会物理删除，缺失时用占位信息兜底
        UserBriefVO author = userService.getBrief(post.getUserId());
        if (author == null) {
            author = UserBriefVO.placeholder(post.getUserId());
        }

        // 用户端才需要关注状态；管理端不查关系表
        boolean isFollowingAuthor = !adminView
                && viewer != null
                && !isAuthor
                && userService.isFollowing(viewer.userId(), post.getUserId());

        // 用户端才需要点赞 / 收藏状态；管理端统一为 false
        InteractionStatus interactionStatus = adminView
                ? InteractionStatus.empty()
                : loadInteractionStatus(viewer == null ? null : viewer.userId(), List.of(postId));

        return PostDetailVO.builder()
                .id(String.valueOf(post.getId()))
                .title(post.getTitle())
                .content(post.getContent())
                .coverUrl(images.isEmpty() ? "" : images.get(0).url())
                .images(images)
                .author(author)
                .tags(tags)
                .status(status)
                .statusText(statusText(status))
                .auditReason(canSeeAuditInfo ? post.getAuditReason() : null)
                .likeCount(value(post.getLikeCount()))
                .commentCount(value(post.getCommentCount()))
                .collectCount(value(post.getCollectCount()))
                .viewCount(value(post.getViewCount()))
                .publishedAt(epochMillis(post.getPublishedAt()))
                .createdAt(epochMillis(post.getCreatedAt()))
                .updatedAt(epochMillis(post.getUpdatedAt()))
                .liked(interactionStatus.liked().contains(postId))
                .collected(interactionStatus.collected().contains(postId))
                .isFollowingAuthor(isFollowingAuthor)
                .canEdit(!adminView && isAuthor && status <= 2)
                .canResubmit(!adminView && isAuthor && status == 2)
                .canDelete(!adminView && isAuthor)
                .aiTagStatus(canSeeAuditInfo ? post.getAiTagStatus() : null)
                .auditedAt(adminView ? epochMillisOrNull(post.getAuditedAt()) : null)
                .auditedBy(adminView && post.getAuditedBy() != null
                        ? String.valueOf(post.getAuditedBy()) : null)
                .canAudit(adminView && viewer != null
                        ? !Objects.equals(viewer.userId(), post.getUserId()) : null)
                .build();
    }

    /**
     * 解析帖子 ID 并只放行公开可见状态：存在、未删除且 status 为 0/1。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 帖子 ID
     */
    @Override
    public Long resolvePublicPostId(String rawPostId) {
        Long postId = parsePostId(rawPostId);
        PostDO post = postMapper.selectById(postId);
        if (post == null || isDeleted(post) || !publicVisible(post)) {
            throw notVisible();
        }
        return postId;
    }

    /**
     * 只有关系真正变化时才更新计数；delta=0 表示重复点赞 / 取消，仅读取当前计数。
     * 计数更新与帖子可见性校验在同一条 UPDATE 内完成，更新不到行说明帖子已不可见。
     *
     * @param postId 帖子 ID
     * @param delta  计数变化量
     * @return 更新后的点赞数
     */
    @Override
    @Transactional
    public int onLikeChanged(Long postId, int delta) {
        return changeInteractionCount(postId, delta, "like_count", PostDO::getLikeCount, true);
    }

    /**
     * 收藏计数更新，规则与 {@link #onLikeChanged(Long, int)} 一致。
     *
     * @param postId 帖子 ID
     * @param delta  计数变化量
     * @return 更新后的收藏数
     */
    @Override
    @Transactional
    public int onCollectChanged(Long postId, int delta) {
        return changeInteractionCount(postId, delta, "collect_count", PostDO::getCollectCount, true);
    }

    /**
     * 评论计数更新：新增时要求帖子公开可见，删除时只要求帖子行存在。
     *
     * @param postId 帖子 ID
     * @param delta  计数变化量
     * @return 更新后的评论数
     */
    @Override
    @Transactional
    public int onCommentChanged(Long postId, int delta) {
        return changeInteractionCount(postId, delta, "comment_count", PostDO::getCommentCount,
                delta > 0);
    }

    /**
     * 帖子行存在且作者匹配即为帖子作者；不限制帖子状态，便于清理已下架帖子的评论。
     *
     * @param postId 帖子 ID
     * @param userId 用户 ID
     * @return 是帖子作者返回 {@code true}
     */
    @Override
    public boolean isPostAuthor(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }
        PostDO post = postMapper.selectById(postId);
        return post != null && Objects.equals(post.getUserId(), userId);
    }

    /**
     * 按 ID 批量组装公开帖子摘要，供“我的点赞 / 我的收藏”按关系顺序回填。
     * 只查公开可见帖子，隐藏 / 已删除的帖子不出现在结果中。
     *
     * @param postIds 帖子 ID 集合
     * @return 以帖子 ID 为键的摘要
     */
    @Override
    public Map<Long, PostSummaryVO> listSummariesByIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<PostDO> rows = safeList(postMapper.selectList(new LambdaQueryWrapper<PostDO>()
                .in(PostDO::getId, ids)
                .in(PostDO::getStatus, List.of(0, 1))
                .eq(PostDO::getIsDeleted, 0)));
        if (rows.isEmpty()) {
            return Map.of();
        }

        List<Long> visibleIds = rows.stream().map(PostDO::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ImageItemVO>> imagesByPost = loadImages(visibleIds);
        Map<Long, List<TagVO>> tagsByPost = loadTags(visibleIds);
        Map<Long, UserBriefVO> authors = loadAuthors(rows);
        LoginUser viewer = UserContext.get();
        InteractionStatus interactionStatus = loadInteractionStatus(
                viewer == null ? null : viewer.userId(), visibleIds);

        Map<Long, PostSummaryVO> result = new HashMap<>();
        for (PostDO post : rows) {
            List<ImageItemVO> images = imagesByPost.getOrDefault(post.getId(), List.of());
            result.put(post.getId(), new PostSummaryVO(
                    String.valueOf(post.getId()),
                    post.getTitle(),
                    images.isEmpty() ? "" : images.get(0).url(),
                    authors.getOrDefault(post.getUserId(),
                            UserBriefVO.placeholder(post.getUserId())),
                    tagsByPost.getOrDefault(post.getId(), List.of()),
                    value(post.getLikeCount()),
                    value(post.getCommentCount()),
                    value(post.getCollectCount()),
                    value(post.getViewCount()),
                    epochMillis(post.getPublishedAt()),
                    interactionStatus.liked().contains(post.getId()),
                    interactionStatus.collected().contains(post.getId())
            ));
        }
        return result;
    }

    /**
     * 按作者集合查询公开帖子；作者集合为 {@code null} 或空时返回空页。
     *
     * @param authorIds 作者 ID 集合
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> listByAuthors(Collection<Long> authorIds, String rawCursor, Integer rawSize) {
        if (authorIds == null) {
            return CursorPage.empty();
        }
        return listPublicPosts(authorIds, rawCursor, rawSize);
    }

    /**
     * 校验用户公开可见后，按作者查询公开帖子。
     *
     * @param rawUserId 作者用户 ID，允许数字字符串
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> listByAuthor(String rawUserId, String rawCursor, Integer rawSize) {
        Long authorId = userService.resolvePublicUserId(rawUserId);
        return listPublicPosts(List.of(authorId), rawCursor, rawSize);
    }

    /**
     * 只支持 latest；hot 待热榜模块接入后再实现，避免前端误以为热榜已生效。
     *
     * @param sort      排序方式，空值按 latest 处理
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> listLatest(String sort, String rawCursor, Integer rawSize) {
        if (StringUtils.hasText(sort) && !"latest".equalsIgnoreCase(sort.strip())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "暂不支持该排序方式");
        }
        return listPublicPosts(null, rawCursor, rawSize);
    }

    /**
     * 查询当前登录用户的全部未删除帖子，按 {@code created_at DESC, id DESC} 游标分页；
     * 状态可选，不传表示全部。
     *
     * @param rawStatus 帖子状态 0~3，可为空
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量
     * @return 我的帖子摘要游标页
     */
    @Override
    public CursorPage<MyPostSummaryVO> listMyPosts(Integer rawStatus, String rawCursor, Integer rawSize) {
        if (rawStatus != null && (rawStatus < 0 || rawStatus > 3)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "状态取值应为 0~3");
        }

        int size = pageSize(rawSize);
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);
        Long userId = UserContext.require().userId();

        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getUserId, userId)
                .eq(PostDO::getIsDeleted, 0);
        if (rawStatus != null) {
            query.eq(PostDO::getStatus, rawStatus);
        }
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(PostDO::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(PostDO::getCreatedAt, cursor.time())
                            .lt(PostDO::getId, cursor.id())));
        }
        query.orderByDesc(PostDO::getCreatedAt)
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

        List<Long> postIds = rows.stream().map(PostDO::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ImageItemVO>> imagesByPost = loadImages(postIds);
        Map<Long, List<TagVO>> tagsByPost = loadTags(postIds);
        Map<Long, UserBriefVO> authors = loadAuthors(rows);
        InteractionStatus interactionStatus = loadInteractionStatus(userId, postIds);

        List<MyPostSummaryVO> items = new ArrayList<>(rows.size());
        for (PostDO post : rows) {
            List<ImageItemVO> images = imagesByPost.getOrDefault(post.getId(), List.of());
            int status = statusOf(post);
            items.add(MyPostSummaryVO.builder()
                    .id(String.valueOf(post.getId()))
                    .title(post.getTitle())
                    .coverUrl(images.isEmpty() ? "" : images.get(0).url())
                    .author(authors.getOrDefault(post.getUserId(),
                            UserBriefVO.placeholder(post.getUserId())))
                    .tags(tagsByPost.getOrDefault(post.getId(), List.of()))
                    .likeCount(value(post.getLikeCount()))
                    .commentCount(value(post.getCommentCount()))
                    .collectCount(value(post.getCollectCount()))
                    .viewCount(value(post.getViewCount()))
                    .publishedAt(epochMillis(post.getPublishedAt()))
                    .status(status)
                    .statusText(statusText(status))
                    .auditReason(status == 2 || status == 3 ? post.getAuditReason() : null)
                    .createdAt(epochMillis(post.getCreatedAt()))
                    .updatedAt(epochMillis(post.getUpdatedAt()))
                    .liked(interactionStatus.liked().contains(post.getId()))
                    .collected(interactionStatus.collected().contains(post.getId()))
                    .build());
        }

        String nextCursor = null;
        if (hasMore) {
            PostDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 管理端帖子列表。
     *
     * <p>参数归一化：{@code status} 不传按 0，{@code ALL} 表示不过滤，非法状态 40001；
     * 非法作者 ID 按查无结果返回空页；页码至少为 1，每页 1~100 条。
     * 待审队列按创建时间升序，其他状态 / ALL 按创建时间倒序。
     *
     * @param rawStatus   状态或 ALL，可为空
     * @param rawAuthorId 作者用户 ID，可为空
     * @param rawKeyword  标题关键词，可为空
     * @param rawPage     页码
     * @param rawSize     每页数量
     * @return 管理端帖子摘要页码分页
     */
    @Override
    public PageResult<AdminPostSummaryVO> listAdminPosts(String rawStatus, String rawAuthorId,
                                                         String rawKeyword, Integer rawPage,
                                                         Integer rawSize) {
        int page = rawPage == null || rawPage < 1 ? 1 : rawPage;
        int size = rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, ADMIN_MAX_PAGE_SIZE));

        Integer status;
        if (!StringUtils.hasText(rawStatus)) {
            status = 0;
        } else if ("ALL".equalsIgnoreCase(rawStatus.strip())) {
            status = null;
        } else {
            try {
                status = Integer.parseInt(rawStatus.strip());
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.INVALID_PARAM, "状态取值应为 0~3 或 ALL");
            }
            if (status < 0 || status > 3) {
                throw new BizException(ErrorCode.INVALID_PARAM, "状态取值应为 0~3 或 ALL");
            }
        }

        Long authorId = null;
        if (StringUtils.hasText(rawAuthorId)) {
            try {
                authorId = Long.parseLong(rawAuthorId.strip());
            } catch (NumberFormatException e) {
                return new PageResult<>(List.of(), page, size, 0, 0);
            }
            if (authorId <= 0) {
                return new PageResult<>(List.of(), page, size, 0, 0);
            }
        }

        String keyword = StringUtils.hasText(rawKeyword) ? rawKeyword.strip() : null;

        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getIsDeleted, 0);
        if (status != null) {
            query.eq(PostDO::getStatus, status);
        }
        if (authorId != null) {
            query.eq(PostDO::getUserId, authorId);
        }
        if (keyword != null) {
            query.like(PostDO::getTitle, keyword);
        }
        if (status != null && status == 0) {
            query.orderByAsc(PostDO::getCreatedAt).orderByAsc(PostDO::getId);
        } else {
            query.orderByDesc(PostDO::getCreatedAt).orderByDesc(PostDO::getId);
        }

        PageHelper.startPage(page, size);
        List<PostDO> rows = postMapper.selectList(query);
        if (rows == null) {
            return new PageResult<>(List.of(), page, size, 0, 0);
        }
        PageInfo<PostDO> pageInfo = new PageInfo<>(rows);
        if (rows.isEmpty()) {
            return new PageResult<>(List.of(), pageInfo.getPageNum(), pageInfo.getPageSize(),
                    pageInfo.getTotal(), pageInfo.getPages());
        }

        List<Long> postIds = rows.stream().map(PostDO::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ImageItemVO>> imagesByPost = loadImages(postIds);
        Map<Long, List<TagVO>> tagsByPost = loadTags(postIds);
        Map<Long, UserBriefVO> authors = loadAuthors(rows);

        List<AdminPostSummaryVO> items = new ArrayList<>(rows.size());
        for (PostDO post : rows) {
            List<ImageItemVO> images = imagesByPost.getOrDefault(post.getId(), List.of());
            int postStatus = statusOf(post);
            items.add(AdminPostSummaryVO.builder()
                    .id(String.valueOf(post.getId()))
                    .title(post.getTitle())
                    .coverUrl(images.isEmpty() ? "" : images.get(0).url())
                    .author(authors.getOrDefault(post.getUserId(),
                            UserBriefVO.placeholder(post.getUserId())))
                    .tags(tagsByPost.getOrDefault(post.getId(), List.of()))
                    .likeCount(value(post.getLikeCount()))
                    .commentCount(value(post.getCommentCount()))
                    .collectCount(value(post.getCollectCount()))
                    .viewCount(value(post.getViewCount()))
                    .publishedAt(epochMillis(post.getPublishedAt()))
                    .status(postStatus)
                    .statusText(statusText(postStatus))
                    .auditReason(postStatus == 2 || postStatus == 3 ? post.getAuditReason() : null)
                    .createdAt(epochMillis(post.getCreatedAt()))
                    .updatedAt(epochMillis(post.getUpdatedAt()))
                    .liked(false)
                    .collected(false)
                    .imageCount(images.size())
                    .auditedAt(epochMillisOrNull(post.getAuditedAt()))
                    .auditedBy(post.getAuditedBy() == null
                            ? null : String.valueOf(post.getAuditedBy()))
                    .build());
        }

        return new PageResult<>(items, pageInfo.getPageNum(), pageInfo.getPageSize(),
                pageInfo.getTotal(), pageInfo.getPages());
    }

    /**
     * 公开帖子列表的公共查询：只查状态 0/1 且未删除，按发布时间倒序。
     *
     * @param authorIds 作者 ID 集合；{@code null} 表示全站
     * @param rawCursor 上一页游标，可为空，坏游标按首页处理
     * @param rawSize   每页数量，默认 20，越界夹到 1~50
     * @return 帖子摘要游标页
     */
    private CursorPage<PostSummaryVO> listPublicPosts(Collection<Long> authorIds,
                                                      String rawCursor, Integer rawSize) {
        List<Long> ids = null;
        if (authorIds != null) {
            ids = authorIds.stream().filter(Objects::nonNull).distinct().toList();
            if (ids.isEmpty()) {
                return CursorPage.empty();
            }
        }

        int size = pageSize(rawSize);

        // 坏游标按首页处理
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<PostDO>()
                .in(PostDO::getStatus, List.of(0, 1))
                .eq(PostDO::getIsDeleted, 0);
        if (ids != null) {
            query.in(PostDO::getUserId, ids);
        }
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
        Map<Long, List<ImageItemVO>> imagesByPost = loadImages(postIds);
        Map<Long, List<TagVO>> tagsByPost = loadTags(postIds);
        Map<Long, UserBriefVO> authors = loadAuthors(rows);
        LoginUser viewer = UserContext.get();
        InteractionStatus interactionStatus = loadInteractionStatus(
                viewer == null ? null : viewer.userId(), postIds);

        List<PostSummaryVO> items = new ArrayList<>(rows.size());
        for (PostDO post : rows) {
            List<ImageItemVO> images = imagesByPost.getOrDefault(post.getId(), List.of());
            items.add(new PostSummaryVO(
                    String.valueOf(post.getId()),
                    post.getTitle(),
                    images.isEmpty() ? "" : images.get(0).url(),
                    authors.getOrDefault(post.getUserId(),
                            UserBriefVO.placeholder(post.getUserId())),
                    tagsByPost.getOrDefault(post.getId(), List.of()),
                    value(post.getLikeCount()),
                    value(post.getCommentCount()),
                    value(post.getCollectCount()),
                    value(post.getViewCount()),
                    epochMillis(post.getPublishedAt()),
                    interactionStatus.liked().contains(post.getId()),
                    interactionStatus.collected().contains(post.getId())
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
     * 互动计数的公共更新逻辑：只有 delta 非 0 才更新计数列；
     * {@code publicOnly=true} 时要求帖子未删除且公开可见，false 时只要求帖子行存在。
     * 最后返回数据库中的最新计数。
     */
    private int changeInteractionCount(Long postId, int delta, String column,
                                       Function<PostDO, Integer> counter, boolean publicOnly) {
        if (delta != 0 && delta != 1 && delta != -1) {
            throw new IllegalArgumentException("delta 只能是 -1、0 或 1");
        }
        if (delta != 0) {
            LambdaUpdateWrapper<PostDO> update = new LambdaUpdateWrapper<PostDO>()
                    .eq(PostDO::getId, postId)
                    .set(PostDO::getLastInteractionAt, LocalDateTime.now());
            if (publicOnly) {
                update.eq(PostDO::getIsDeleted, 0)
                        .in(PostDO::getStatus, List.of(0, 1));
            }
            if (delta > 0) {
                update.setSql(column + " = " + column + " + 1");
            } else {
                update.setSql(column + " = IF(" + column + " > 0, " + column + " - 1, 0)");
            }
            if (postMapper.update(null, update) == 0) {
                throw notVisible();
            }
        }

        PostDO post = postMapper.selectById(postId);
        if (post == null || (publicOnly && (isDeleted(post) || !publicVisible(post)))) {
            throw notVisible();
        }
        return value(counter.apply(post));
    }

    /**
     * 登录用户按当前页帖子 ID 批量查点赞 / 收藏状态；游客或空页不查库。
     */
    private InteractionStatus loadInteractionStatus(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return InteractionStatus.empty();
        }
        Set<Long> liked = interactionStatusProvider.likedPostIds(userId, postIds);
        Set<Long> collected = interactionStatusProvider.collectedPostIds(userId, postIds);
        return new InteractionStatus(
                liked == null ? Set.of() : liked,
                collected == null ? Set.of() : collected
        );
    }

    private boolean publicVisible(PostDO post) {
        int status = statusOf(post);
        return status == 0 || status == 1;
    }

    private Map<Long, List<ImageItemVO>> loadImages(List<Long> postIds) {
        Map<Long, List<ImageItemVO>> imagesByPost = new HashMap<>();
        if (postIds.isEmpty()) {
            return imagesByPost;
        }
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
        return imagesByPost;
    }

    private Map<Long, List<TagVO>> loadTags(List<Long> postIds) {
        Map<Long, List<TagVO>> tagsByPost = new HashMap<>();
        if (postIds.isEmpty()) {
            return tagsByPost;
        }
        List<PostTagDO> links = safeList(postTagMapper.selectList(
                new LambdaQueryWrapper<PostTagDO>()
                        .in(PostTagDO::getPostId, postIds)
                        .orderByAsc(PostTagDO::getId)));
        List<Long> tagIds = links.stream()
                .map(PostTagDO::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, TagVO> tagsById = new HashMap<>();
        if (!tagIds.isEmpty()) {
            for (TagDO tag : safeList(tagMapper.selectBatchIds(tagIds))) {
                if (tag.getId() != null) {
                    tagsById.put(tag.getId(), TagVO.from(tag));
                }
            }
        }
        for (PostTagDO link : links) {
            TagVO tag = link.getTagId() == null ? null : tagsById.get(link.getTagId());
            if (link.getPostId() != null && tag != null) {
                tagsByPost.computeIfAbsent(link.getPostId(), key -> new ArrayList<>()).add(tag);
            }
        }
        return tagsByPost;
    }

    private Map<Long, UserBriefVO> loadAuthors(List<PostDO> rows) {
        List<Long> authorIds = rows.stream()
                .map(PostDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserBriefVO> authors = userService.getBriefs(authorIds);
        return authors == null ? Map.of() : authors;
    }

    private int pageSize(Integer rawSize) {
        return rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));
    }

    private String statusText(int status) {
        return switch (status) {
            case 0 -> "已发布待审核";
            case 1 -> "已发布";
            case 2 -> "审核拒绝";
            case 3 -> "已下架";
            default -> "未知状态";
        };
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

    private Long epochMillisOrNull(LocalDateTime time) {
        return time == null ? null : epochMillis(time);
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

    /**
     * 当前请求用户对一批帖子的点赞 / 收藏状态。
     */
    private record InteractionStatus(Set<Long> liked, Set<Long> collected) {

        private static InteractionStatus empty() {
            return new InteractionStatus(Set.of(), Set.of());
        }
    }
}
