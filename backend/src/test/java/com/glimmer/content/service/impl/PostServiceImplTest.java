package com.glimmer.content.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.glimmer.content.service.InteractionStatusProvider;
import com.glimmer.content.vo.ImageItemVO;
import com.glimmer.content.vo.MyPostSummaryVO;
import com.glimmer.content.vo.PostAuditVO;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.content.vo.TagVO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private InteractionStatusProvider interactionStatusProvider;
    private PostServiceImpl postService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测没有 MyBatis 启动流程，先注册实体元数据，Lambda 条件才能生成 SQL。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PostDO.class);
    }

    @BeforeEach
    void setUp() {
        postMapper = mock(PostMapper.class);
        postImageMapper = mock(PostImageMapper.class);
        postTagMapper = mock(PostTagMapper.class);
        tagMapper = mock(TagMapper.class);
        userService = mock(UserService.class);
        interactionStatusProvider = mock(InteractionStatusProvider.class);
        postService = new PostServiceImpl(postMapper, postImageMapper, postTagMapper, tagMapper, userService,
                interactionStatusProvider);
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
        CursorPage<PostSummaryVO> nullPage = postService.listByAuthors(null, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(nullPage.items()).isEmpty();
        verifyNoInteractions(postMapper, userService);
    }

    @Test
    void shouldListPublicPostsByAuthor() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        when(userService.resolvePublicUserId("1")).thenReturn(1L);
        when(postMapper.selectList(any())).thenReturn(List.of(post(1001L, 1L, 1, now)));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of());
        when(userService.getBriefs(any())).thenReturn(Map.of(1L, brief(1L, "小光")));

        CursorPage<PostSummaryVO> page = postService.listByAuthor("1", null, 20);

        assertThat(page.items()).extracting(PostSummaryVO::id).containsExactly("1001");
        assertThat(page.items().get(0).author().nickname()).isEqualTo("小光");
        assertThat(page.hasMore()).isFalse();
        verify(userService).resolvePublicUserId("1");
    }

    @Test
    void shouldRejectPostsForMissingUser() {
        when(userService.resolvePublicUserId("99"))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "用户不存在"));

        assertThatThrownBy(() -> postService.listByAuthor("99", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(postMapper);
    }

    @Test
    void shouldListLatestPublicPosts() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        when(postMapper.selectList(any())).thenReturn(List.of(
                post(1001L, 1L, 1, now),
                post(1002L, 2L, 0, now.minusMinutes(1))));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0),
                image(1002L, "https://cdn.example.com/2.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(1001L, 3L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L, "露营")));
        when(userService.getBriefs(any())).thenReturn(Map.of(
                1L, brief(1L, "小光"),
                2L, brief(2L, "阿野")));

        CursorPage<PostSummaryVO> page = postService.listLatest("latest", null, 20);

        assertThat(page.items()).extracting(PostSummaryVO::id)
                .containsExactly("1001", "1002");
        assertThat(page.items().get(0).tags()).extracting(TagVO::name)
                .containsExactly("露营");
        assertThat(page.hasMore()).isFalse();
        verify(userService, never()).resolvePublicUserId(any());
    }

    @Test
    void shouldRejectUnsupportedSort() {
        assertThatThrownBy(() -> postService.listLatest("hot", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postMapper);
    }

    @Test
    void shouldListMyPostsWithStatusAndAuditInfo() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        PostDO published = post(1001L, 1L, 1, now);
        PostDO rejected = post(1002L, 1L, 2, now.minusMinutes(1));
        rejected.setAuditReason("内容不符合社区规范");
        PostDO takedown = post(1003L, 1L, 3, now.minusMinutes(2));
        takedown.setAuditReason("违反社区规范");

        when(postMapper.selectList(any())).thenReturn(List.of(published, rejected, takedown));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0),
                image(1002L, "https://cdn.example.com/2.jpg", 0),
                image(1003L, "https://cdn.example.com/3.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of());
        when(userService.getBriefs(any())).thenReturn(Map.of(1L, brief(1L, "小光")));

        CursorPage<MyPostSummaryVO> page = postService.listMyPosts(null, null, 2);

        assertThat(page.items()).extracting(MyPostSummaryVO::id)
                .containsExactly("1001", "1002");
        assertThat(page.items().get(0).statusText()).isEqualTo("已发布");
        assertThat(page.items().get(0).auditReason()).isNull();
        assertThat(page.items().get(1).statusText()).isEqualTo("审核拒绝");
        assertThat(page.items().get(1).auditReason()).isEqualTo("内容不符合社区规范");
        assertThat(page.items().get(0).createdAt()).isPositive();
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void shouldRejectInvalidMyPostStatus() {
        assertThatThrownBy(() -> postService.listMyPosts(9, null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postMapper);
    }

    @Test
    void shouldUpdateOwnPostAndResetAuditState() {
        LocalDateTime published = LocalDateTime.of(2026, 10, 29, 12, 0);
        PostDO rejected = post(1001L, 1L, 2, published);
        rejected.setAuditReason("内容不符合社区规范");
        PostDO updated = post(1001L, 1L, 0, published);
        updated.setTitle("新标题");
        updated.setContent("新正文");

        when(postMapper.selectById(1001L)).thenReturn(rejected, updated);
        when(postMapper.update(isNull(), any())).thenReturn(1);
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(1001L, 3L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L, "露营")));
        when(userService.getBrief(1L)).thenReturn(brief(1L, "小光"));

        PostDetailVO detail = postService.update("1001", new CreatePostDTO(
                "  新标题  ", "新正文",
                List.of("https://cdn.example.com/2.jpg"),
                List.of("3")));

        assertThat(detail.title()).isEqualTo("新标题");
        assertThat(detail.status()).isZero();
        assertThat(detail.statusText()).isEqualTo("已发布待审核");
        assertThat(detail.auditReason()).isNull();
        verify(postImageMapper).delete(any());
        verify(postTagMapper).delete(any());
        verify(postImageMapper).insert(any(PostImageDO.class));
        verify(postTagMapper).insert(any(PostTagDO.class));
    }

    @Test
    void shouldRejectEditByOtherUser() {
        UserContext.set(new LoginUser(2L, "USER"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.update("1001", new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectEditForTakedownPost() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 3,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.update("1001", new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldHideDeletedPostFromEdit() {
        PostDO deleted = post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0));
        deleted.setIsDeleted(1);
        when(postMapper.selectById(1001L)).thenReturn(deleted);

        assertThatThrownBy(() -> postService.update("1001", new CreatePostDTO(
                "标题", "正文", List.of("https://cdn.example.com/1.jpg"), null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldDeleteOwnPost() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(1);

        postService.delete("1001");

        verify(postMapper).update(isNull(), any());
    }

    @Test
    void shouldTreatRepeatDeleteAsIdempotent() {
        PostDO deleted = post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0));
        deleted.setIsDeleted(1);
        when(postMapper.selectById(1001L)).thenReturn(deleted);

        postService.delete("1001");

        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectDeleteByOtherUser() {
        UserContext.set(new LoginUser(2L, "USER"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.delete("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldResubmitRejectedPost() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 2,
                LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(1);

        PostCreateVO response = postService.resubmit("1001");

        assertThat(response.id()).isEqualTo("1001");
        assertThat(response.status()).isZero();
        assertThat(response.statusText()).isEqualTo("已发布待审核");
        assertThat(response.message()).isEqualTo("已重新发布，等待审核");
        verify(postMapper).update(isNull(), any());
    }

    @Test
    void shouldRejectResubmitWhenNotRejected() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.resubmit("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldApprovePendingPost() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 0,
                LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(1);

        PostAuditVO result = postService.approve("1001");

        assertThat(result.id()).isEqualTo("1001");
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.statusText()).isEqualTo("已发布");
        assertThat(result.auditReason()).isNull();
        assertThat(result.auditedBy()).isEqualTo("9");
        verify(postMapper).update(isNull(), any());
    }

    @Test
    void shouldReturnCurrentStateWhenAlreadyApproved() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(
                post(1001L, 1L, 0, LocalDateTime.of(2026, 10, 29, 12, 0)),
                post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(0);

        PostAuditVO result = postService.approve("1001");

        assertThat(result.status()).isEqualTo(1);
    }

    @Test
    void shouldRejectSelfAudit() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 9L, 0,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.approve("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AUDIT_SELF_FORBIDDEN));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectPendingPostWithReason() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 0,
                LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(1);

        PostAuditVO result = postService.reject("1001", "  图片与正文无关  ");

        assertThat(result.status()).isEqualTo(2);
        assertThat(result.statusText()).isEqualTo("审核拒绝");
        assertThat(result.auditReason()).isEqualTo("图片与正文无关");
        assertThat(result.auditedBy()).isEqualTo("9");
    }

    @Test
    void shouldRejectBlankAuditReason() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 0,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThatThrownBy(() -> postService.reject("1001", "   "))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldTakedownAndRestorePost() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(
                post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0)),
                post(1001L, 1L, 3, LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(1);

        PostAuditVO takedown = postService.takedown("1001", "违反社区规范");
        PostAuditVO restore = postService.restore("1001");

        assertThat(takedown.status()).isEqualTo(3);
        assertThat(takedown.statusText()).isEqualTo("已下架");
        assertThat(restore.status()).isEqualTo(1);
        assertThat(restore.auditReason()).isNull();
    }

    @Test
    void shouldRejectAuditWhenStatusChanged() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(postMapper.selectById(1001L)).thenReturn(
                post(1001L, 1L, 0, LocalDateTime.of(2026, 10, 29, 12, 0)),
                post(1001L, 1L, 3, LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> postService.approve("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AUDIT_STATE_CHANGED));
    }

    @Test
    void shouldFillInteractionStatusInDetail() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of());
        when(userService.getBrief(1L)).thenReturn(brief(1L, "小光"));
        when(interactionStatusProvider.likedPostIds(1L, List.of(1001L)))
                .thenReturn(Set.of(1001L));
        when(interactionStatusProvider.collectedPostIds(1L, List.of(1001L)))
                .thenReturn(Set.of());

        PostDetailVO detail = postService.getDetail("1001");

        assertThat(detail.liked()).isTrue();
        assertThat(detail.collected()).isFalse();
    }

    @Test
    void shouldResolvePublicPostId() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        assertThat(postService.resolvePublicPostId("1001")).isEqualTo(1001L);
    }

    @Test
    void shouldRejectInvisiblePostWhenResolving() {
        PostDO hidden = post(1001L, 1L, 2, LocalDateTime.of(2026, 10, 29, 12, 0));
        when(postMapper.selectById(1001L)).thenReturn(hidden);

        assertThatThrownBy(() -> postService.resolvePublicPostId("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));

        PostDO deleted = post(1002L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0));
        deleted.setIsDeleted(1);
        when(postMapper.selectById(1002L)).thenReturn(deleted);
        assertThatThrownBy(() -> postService.resolvePublicPostId("1002"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));

        assertThatThrownBy(() -> postService.resolvePublicPostId("abc"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    void shouldChangeLikeCountAndReturnLatestValue() {
        PostDO liked = post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0));
        liked.setLikeCount(13);
        when(postMapper.update(isNull(), any())).thenReturn(1);
        when(postMapper.selectById(1001L)).thenReturn(liked);

        int likeCount = postService.onLikeChanged(1001L, 1);

        assertThat(likeCount).isEqualTo(13);
        verify(postMapper).update(isNull(), any());
    }

    @Test
    void shouldOnlyReadCountWhenDeltaIsZero() {
        when(postMapper.selectById(1001L)).thenReturn(post(1001L, 1L, 1,
                LocalDateTime.of(2026, 10, 29, 12, 0)));

        int likeCount = postService.onLikeChanged(1001L, 0);

        assertThat(likeCount).isEqualTo(12);
        verify(postMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectInteractionWhenPostNotVisible() {
        when(postMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> postService.onLikeChanged(1001L, 1))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verify(postMapper, never()).selectById(any());

        assertThatThrownBy(() -> postService.onCollectChanged(1001L, -1))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    void shouldRejectInvalidInteractionDelta() {
        assertThatThrownBy(() -> postService.onLikeChanged(1001L, 2))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(postMapper);
    }

    @Test
    void shouldChangeCollectCount() {
        PostDO collected = post(1001L, 1L, 1, LocalDateTime.of(2026, 10, 29, 12, 0));
        collected.setCollectCount(5);
        when(postMapper.update(isNull(), any())).thenReturn(1);
        when(postMapper.selectById(1001L)).thenReturn(collected);

        assertThat(postService.onCollectChanged(1001L, 1)).isEqualTo(5);
    }

    @Test
    void shouldListSummariesByIdsWithInteractionStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        when(postMapper.selectList(any())).thenReturn(List.of(
                post(1001L, 1L, 1, now),
                post(1002L, 2L, 0, now.minusMinutes(1))));
        when(postImageMapper.selectList(any())).thenReturn(List.of(
                image(1001L, "https://cdn.example.com/1.jpg", 0)));
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(1001L, 3L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(3L, "露营")));
        when(userService.getBriefs(any())).thenReturn(Map.of(
                1L, brief(1L, "小光"),
                2L, brief(2L, "阿野")));
        when(interactionStatusProvider.likedPostIds(1L, List.of(1001L, 1002L)))
                .thenReturn(Set.of(1001L));
        when(interactionStatusProvider.collectedPostIds(1L, List.of(1001L, 1002L)))
                .thenReturn(Set.of(1002L));

        Map<Long, PostSummaryVO> summaries = postService.listSummariesByIds(
                List.of(1001L, 1002L));

        assertThat(summaries).containsOnlyKeys(1001L, 1002L);
        assertThat(summaries.get(1001L).liked()).isTrue();
        assertThat(summaries.get(1001L).collected()).isFalse();
        assertThat(summaries.get(1002L).liked()).isFalse();
        assertThat(summaries.get(1002L).collected()).isTrue();
        assertThat(summaries.get(1001L).tags()).extracting(TagVO::name)
                .containsExactly("露营");
    }

    @Test
    void shouldReturnEmptySummariesForEmptyIds() {
        assertThat(postService.listSummariesByIds(null)).isEmpty();
        assertThat(postService.listSummariesByIds(List.of())).isEmpty();
        verifyNoInteractions(postMapper);
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
