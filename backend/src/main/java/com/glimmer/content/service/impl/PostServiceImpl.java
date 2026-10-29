package com.glimmer.content.service.impl;

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
import com.glimmer.content.vo.PostCreateVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 发帖实现：最小校验后，事务写入帖子、图片与标签。
 *
 * <p>MVP 不校验图片 URL 是否由当前用户上传；Feed、ES 与 AI 打标在后续模块接入。
 */
@Service
public class PostServiceImpl implements PostService {

    private static final int TITLE_MAX = 100;
    private static final int CONTENT_MAX = 20_000;
    private static final int CONTENT_MAX_BYTES = 65_535;
    private static final int IMAGE_MAX = 9;
    private static final int IMAGE_URL_MAX = 512;
    private static final int TAG_MAX = 5;

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;

    public PostServiceImpl(PostMapper postMapper,
                           PostImageMapper postImageMapper,
                           PostTagMapper postTagMapper,
                           TagMapper tagMapper) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
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

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
