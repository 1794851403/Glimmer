package com.glimmer.content.service.impl;

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
import com.glimmer.content.vo.ImageItemVO;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.content.vo.TagVO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostServiceImplTest {

    private PostMapper postMapper;
    private PostImageMapper postImageMapper;
    private PostTagMapper postTagMapper;
    private TagMapper tagMapper;
    private UserService userService;
    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postMapper = mock(PostMapper.class);
        postImageMapper = mock(PostImageMapper.class);
        postTagMapper = mock(PostTagMapper.class);
        tagMapper = mock(TagMapper.class);
        userService = mock(UserService.class);
        postService = new PostServiceImpl(postMapper, postImageMapper, postTagMapper, tagMapper, userService);
        UserContext.set(new LoginUser(1L, "USER"));
        when(postMapper.insert(any(PostDO.class))).thenAnswer(invocation -> {
            PostDO post = invocation.getArgument(0);
            post.setId(1001L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreatePostForNormalUser() {
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L), tag(10L)));

        PostCreateVO response = postService.create(new CreatePostDTO(
                "  周末去山里露营  ",
                "第一次在秋天露营，夜里能看到银河。",
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"),
                List.of("3", "10")));

        assertThat(response.id()).isEqualTo("1001");
        assertThat(response.status()).isZero();
        assertThat(response.statusText()).isEqualTo("已发布待审核");
        assertThat(response.message()).contains("审核");
        assertThat(response.publishedAt()).isPositive();

        ArgumentCaptor<PostDO> postCaptor = ArgumentCaptor.forClass(PostDO.class);
        verify(postMapper).insert(postCaptor.capture());
        PostDO post = postCaptor.getValue();
        assertThat(post.getUserId()).isEqualTo(1L);
        assertThat(post.getTitle()).isEqualTo("周末去山里露营");
        assertThat(post.getContent()).isEqualTo("第一次在秋天露营，夜里能看到银河。");
        assertThat(post.getStatus()).isZero();
        assertThat(post.getIsDeleted()).isZero();
        assertThat(post.getAiTagStatus()).isZero();
        assertThat(post.getPublishedAt()).isNotNull();
        assertThat(post.getLastInteractionAt()).isNotNull();

        ArgumentCaptor<PostImageDO> imageCaptor = ArgumentCaptor.forClass(PostImageDO.class);
        verify(postImageMapper, times(2)).insert(imageCaptor.capture());
        assertThat(imageCaptor.getAllValues())
                .extracting(PostImageDO::getSortOrder)
                .containsExactly(0, 1);
        assertThat(imageCaptor.getAllValues())
                .extracting(PostImageDO::getUrl)
                .containsExactly("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg");

        ArgumentCaptor<PostTagDO> tagCaptor = ArgumentCaptor.forClass(PostTagDO.class);
        verify(postTagMapper, times(2)).insert(tagCaptor.capture());
        assertThat(tagCaptor.getAllValues())
                .extracting(PostTagDO::getTagId)
                .containsExactly(3L, 10L);
        assertThat(tagCaptor.getAllValues())
                .extracting(PostTagDO::getSource)
                .containsOnly(0);
    }

    @Test
    void shouldCreatePublishedPostForAdmin() {
        UserContext.set(new LoginUser(2L, "ADMIN"));

        PostCreateVO response = postService.create(new CreatePostDTO(
                "管理员公告", "公告内容", List.of("https://cdn.example.com/1.jpg"), null));

        assertThat(response.status()).isEqualTo(1);
        assertThat(response.statusText()).isEqualTo("已发布");
        assertThat(response.message()).isEqualTo("发布成功");
        assertThat(response.id()).isEqualTo("1001");
        verifyNoInteractions(tagMapper);
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "   ", "正文", List.of("https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectTooLongContent() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "a".repeat(20_001), List.of("https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectContentOverTextByteLimit() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "😀".repeat(20_000), List.of("https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectEmptyImages() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "正文", Collections.emptyList(), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectMoreThanNineImages() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "正文",
                Collections.nCopies(10, "https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPLOAD_COUNT_EXCEEDED));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectTooLongImageUrl() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/" + "a".repeat(500)), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectMoreThanFiveTags() {
        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/1.jpg"),
                List.of("1", "2", "3", "4", "5", "6"))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper, tagMapper);
    }

    @Test
    void shouldRejectMissingTag() {
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L)));

        assertThatThrownBy(() -> postService.create(new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/1.jpg"),
                List.of("3", "10"))))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postMapper, postImageMapper, postTagMapper);
    }

    @Test
    void shouldDeduplicateTags() {
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L)));

        postService.create(new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/1.jpg"),
                List.of("3", "3")));

        verify(postTagMapper, times(1)).insert(any(PostTagDO.class));
    }

    @Test
    void shouldReturnPublicPostDetail() {
        UserContext.clear();
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0),
                image(1001L, "https://cdn.example.com/2.jpg", 1)));
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(1001L, 3L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L, "露营")));
        when(userService.getBrief(1L)).thenReturn(brief(1L, "小光"));

        PostDetailVO detail = postService.getDetail("1001");

        assertThat(detail.id()).isEqualTo("1001");
        assertThat(detail.coverUrl()).isEqualTo("https://cdn.example.com/1.jpg");
        assertThat(detail.images()).extracting(ImageItemVO::sortOrder).containsExactly(0, 1);
        assertThat(detail.tags()).extracting(TagVO::name).containsExactly("露营");
        assertThat(detail.author().nickname()).isEqualTo("小光");
        assertThat(detail.status()).isEqualTo(1);
        assertThat(detail.statusText()).isEqualTo("已发布");
        assertThat(detail.auditReason()).isNull();
        assertThat(detail.aiTagStatus()).isNull();
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.canResubmit()).isFalse();
        assertThat(detail.canDelete()).isFalse();
        assertThat(detail.isFollowingAuthor()).isFalse();
        assertThat(detail.liked()).isFalse();
        assertThat(detail.collected()).isFalse();
    }

    @Test
    void shouldExposeAuditFieldsToAuthor() {
        PostDO rejected = post(1001L, 1L, 2, LocalDateTime.of(2026, 10, 29, 12, 0));
        rejected.setAuditReason("内容不符合社区规范");
        when(postMapper.selectById(1001L)).thenReturn(rejected);
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of());
        when(userService.getBrief(1L)).thenReturn(brief(1L, "小光"));

        PostDetailVO detail = postService.getDetail("1001");

        assertThat(detail.status()).isEqualTo(2);
        assertThat(detail.statusText()).isEqualTo("审核拒绝");
        assertThat(detail.auditReason()).isEqualTo("内容不符合社区规范");
        assertThat(detail.aiTagStatus()).isZero();
        assertThat(detail.canEdit()).isTrue();
        assertThat(detail.canResubmit()).isTrue();
        assertThat(detail.canDelete()).isTrue();
    }

    @Test
    void shouldHideRejectedPostFromOtherUser() {
        UserContext.set(new LoginUser(2L, "USER"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 2,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.getDetail("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verifyNoInteractions(postImageMapper, postTagMapper, tagMapper, userService);
    }

    @Test
    void shouldHideDeletedPost() {
        UserContext.clear();
        PostDO deleted = post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0));
        deleted.setIsDeleted(1);
        when(postMapper.selectById(1001L)).thenReturn(deleted);

        assertThatThrownBy(() -> postService.getDetail("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verifyNoInteractions(postImageMapper, postTagMapper, tagMapper, userService);
    }

    @Test
    void shouldPageFeedPostsByAuthors() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        PostDO newest = post(1001L, 1L, 1, now);
        PostDO middle = post(1002L, 1L, 1, now.minusMinutes(1));
        PostDO oldest = post(1003L, 2L, 1, now.minusMinutes(2));
        when(postMapper.selectList(any()))
                .thenReturn(List.of(newest, middle, oldest))
                .thenReturn(List.of(oldest));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0),
                image(1002L, "https://cdn.example.com/2.jpg", 0),
                image(1003L, "https://cdn.example.com/3.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(1001L, 3L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L, "露营")));
        when(userService.getBriefs(any())).thenReturn(Map.of(
                1L, brief(1L, "小光"),
                2L, brief(2L, "阿野")));

        CursorPage<PostSummaryVO> firstPage = postService.listByAuthors(List.of(1L, 2L), null, 2);

        assertThat(firstPage.items()).extracting(PostSummaryVO::id)
                .containsExactly("1001", "1002");
        assertThat(firstPage.items().get(0).coverUrl()).isEqualTo("https://cdn.example.com/1.jpg");
        assertThat(firstPage.items().get(0).tags()).extracting(TagVO::name).containsExactly("露营");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        CursorPage<PostSummaryVO> secondPage = postService.listByAuthors(
                List.of(1L, 2L), firstPage.nextCursor(), 2);

        assertThat(secondPage.items()).extracting(PostSummaryVO::id).containsExactly("1003");
        assertThat(secondPage.items().get(0).author().nickname()).isEqualTo("阿野");
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void shouldReturnEmptyPageWhenNoAuthors() {
        CursorPage<PostSummaryVO> page = postService.listByAuthors(List.of(), null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(postMapper, userService);
    }

    private TagDO tag(long id) {
        return tag(id, "标签" + id);
    }

    private TagDO tag(long id, String name) {
        TagDO tag = new TagDO();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private PostDO post(long id, long userId, int status, LocalDateTime publishedAt) {
        PostDO post = new PostDO();
        post.setId(id);
        post.setUserId(userId);
        post.setTitle("周末去山里露营");
        post.setContent("第一次在秋天露营，夜里能看到银河。");
        post.setStatus(status);
        post.setIsDeleted(0);
        post.setPublishedAt(publishedAt);
        post.setLikeCount(12);
        post.setCommentCount(3);
        post.setCollectCount(4);
        post.setViewCount(100);
        post.setAiTagStatus(0);
        post.setLastInteractionAt(publishedAt);
        post.setCreatedAt(publishedAt);
        post.setUpdatedAt(publishedAt);
        return post;
    }

    private PostImageDO image(long postId, String url, int sortOrder) {
        PostImageDO image = new PostImageDO();
        image.setPostId(postId);
        image.setUrl(url);
        image.setSortOrder(sortOrder);
        return image;
    }

    private PostTagDO postTag(long postId, long tagId) {
        PostTagDO postTag = new PostTagDO();
        postTag.setPostId(postId);
        postTag.setTagId(tagId);
        postTag.setSource(0);
        return postTag;
    }

    private UserBriefVO brief(long id, String nickname) {
        return new UserBriefVO(String.valueOf(id), nickname, "");
    }
}
