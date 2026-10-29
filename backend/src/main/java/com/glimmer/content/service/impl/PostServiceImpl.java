package com.glimmer.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

        for (int i = 0; i < imageUrls.size(); i++) {
            PostImageDO image = new PostImageDO();
            image.setPostId(post.getId());
            image.setUrl(imageUrls.get(i));
            image.setSortOrder(i);
            postImageMapper.insert(image);
        }

        for (Long tagId : tagIds) {
            PostTagDO postTag = new PostTagDO();
            postTag.setPostId(post.getId());
            postTag.setTagId(tagId);
            postTag.setSource(0);
            postTagMapper.insert(postTag);
        }

        return PostCreateVO.from(post);
    }

    @Override
    public PostDetailVO getDetail(String rawPostId) {
        Long postId = parsePostId(rawPostId);
        PostDO post = postMapper.selectById(postId);
        if (post == null || isDeleted(post)) {
            throw notVisible();
        }

        LoginUser viewer = UserContext.get();
        boolean author = viewer != null && Objects.equals(viewer.userId(), post.getUserId());
        boolean admin = viewer != null && viewer.isAdmin();
        int status = statusOf(post);
        if (!publicVisible(status) && !author && !admin) {
            throw notVisible();
        }

        List<ImageItemVO> images = loadImages(postId);
        List<TagVO> tags = loadTags(postId);
        UserBriefVO authorBrief = userService.getBrief(post.getUserId());
        if (authorBrief == null) {
            authorBrief = UserBriefVO.placeholder(post.getUserId());
        }
        boolean followingAuthor = viewer != null
                && !author
                && userService.isFollowing(viewer.userId(), post.getUserId());
        boolean privileged = author || admin;
        String coverUrl = images.isEmpty() ? "" : images.get(0).url();

        return new PostDetailVO(
                String.valueOf(post.getId()),
                post.getTitle(),
                post.getContent(),
                coverUrl,
                images,
                authorBrief,
                tags,
                status,
                statusText(status),
                privileged ? post.getAuditReason() : null,
                value(post.getLikeCount()),
                value(post.getCommentCount()),
                value(post.getCollectCount()),
                value(post.getViewCount()),
                epochMillis(post.getPublishedAt()),
                epochMillis(post.getCreatedAt()),
                epochMillis(post.getUpdatedAt()),
                false,
                false,
                followingAuthor,
                author && status <= 2,
                author && status == 2,
                author,
                privileged ? post.getAiTagStatus() : null
        );
    }

    @Override
    public CursorPage<PostSummaryVO> listByAuthors(Collection<Long> authorIds, String rawCursor, Integer rawSize) {
        List<Long> ids = authorIds == null
                ? List.of()
                : authorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return CursorPage.empty();
        }

        int size = normalizePageSize(rawSize);
        PostCursor cursor = decodeCursor(rawCursor);
        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<PostDO>()
                .in(PostDO::getUserId, ids)
                .in(PostDO::getStatus, List.of(0, 1))
                .eq(PostDO::getIsDeleted, 0);
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(PostDO::getPublishedAt, cursor.publishedAt())
                    .or(sameTime -> sameTime
                            .eq(PostDO::getPublishedAt, cursor.publishedAt())
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

        List<PostSummaryVO> items = toSummaries(rows);
        String nextCursor = hasMore ? encodeCursor(rows.get(rows.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private List<PostSummaryVO> toSummaries(List<PostDO> posts) {
        List<Long> postIds = posts.stream().map(PostDO::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ImageItemVO>> imagesByPost = loadImagesByPost(postIds);
        Map<Long, List<Long>> tagIdsByPost = loadTagIdsByPost(postIds);
        List<Long> allTagIds = tagIdsByPost.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
        Map<Long, TagVO> tagsById = loadTagsById(allTagIds);
        List<Long> authorIds = posts.stream()
                .map(PostDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserBriefVO> authors = userService.getBriefs(authorIds);
        if (authors == null) {
            authors = Map.of();
        }

        List<PostSummaryVO> items = new ArrayList<>(posts.size());
        for (PostDO post : posts) {
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
        return items;
    }

    private List<ImageItemVO> loadImages(Long postId) {
        List<PostImageDO> rows = postImageMapper.selectList(new LambdaQueryWrapper<PostImageDO>()
                .eq(PostImageDO::getPostId, postId)
                .orderByAsc(PostImageDO::getSortOrder)
                .orderByAsc(PostImageDO::getId));
        return safeList(rows).stream().map(ImageItemVO::from).toList();
    }

    private List<TagVO> loadTags(Long postId) {
        List<PostTagDO> links = postTagMapper.selectList(new LambdaQueryWrapper<PostTagDO>()
                .eq(PostTagDO::getPostId, postId)
                .orderByAsc(PostTagDO::getId));
        List<Long> tagIds = safeList(links).stream()
                .map(PostTagDO::getTagId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, TagVO> tagsById = loadTagsById(tagIds);
        return tagIds.stream().map(tagsById::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, List<ImageItemVO>> loadImagesByPost(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<PostImageDO> rows = postImageMapper.selectList(new LambdaQueryWrapper<PostImageDO>()
                .in(PostImageDO::getPostId, postIds)
                .orderByAsc(PostImageDO::getSortOrder)
                .orderByAsc(PostImageDO::getId));
        Map<Long, List<ImageItemVO>> result = new HashMap<>();
        for (PostImageDO row : safeList(rows)) {
            if (row.getPostId() != null) {
                result.computeIfAbsent(row.getPostId(), key -> new ArrayList<>())
                        .add(ImageItemVO.from(row));
            }
        }
        return result;
    }

    private Map<Long, List<Long>> loadTagIdsByPost(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<PostTagDO> rows = postTagMapper.selectList(new LambdaQueryWrapper<PostTagDO>()
                .in(PostTagDO::getPostId, postIds)
                .orderByAsc(PostTagDO::getId));
        Map<Long, List<Long>> result = new HashMap<>();
        for (PostTagDO row : safeList(rows)) {
            if (row.getPostId() != null && row.getTagId() != null) {
                result.computeIfAbsent(row.getPostId(), key -> new ArrayList<>())
                        .add(row.getTagId());
            }
        }
        return result;
    }

    private Map<Long, TagVO> loadTagsById(Collection<Long> tagIds) {
        List<Long> ids = tagIds == null
                ? List.of()
                : tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<TagDO> rows = tagMapper.selectBatchIds(ids);
        Map<Long, TagVO> result = new LinkedHashMap<>();
        for (TagDO tag : safeList(rows)) {
            if (tag.getId() != null) {
                result.put(tag.getId(), TagVO.from(tag));
            }
        }
        return result;
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

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private String encodeCursor(PostDO post) {
        if (post.getPublishedAt() == null || post.getId() == null) {
            return null;
        }
        String raw = epochMillis(post.getPublishedAt()) + ":" + post.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private PostCursor decodeCursor(String raw) {
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
            return new PostCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE),
                    id
            );
        } catch (RuntimeException e) {
            // 游标对客户端不透明，坏游标按首页处理，不额外报参数错误。
            return null;
        }
    }

    private boolean publicVisible(int status) {
        return status == 0 || status == 1;
    }

    private boolean isDeleted(PostDO post) {
        return post.getIsDeleted() != null && post.getIsDeleted() == 1;
    }

    private int statusOf(PostDO post) {
        return post.getStatus() == null ? 0 : post.getStatus();
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

    private record PostCursor(LocalDateTime publishedAt, Long id) {
    }
}
