package com.glimmer.interaction.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.glimmer.interaction.vo.CommentVO;
import com.glimmer.interaction.vo.ReplyVO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentServiceImplTest {

    private CommentMapper commentMapper;
    private PostService postService;
    private UserService userService;
    private CommentServiceImpl commentService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测没有 MyBatis 启动流程，先注册实体元数据，Lambda 条件才能生成 SQL。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CommentDO.class);
    }

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        postService = mock(PostService.class);
        userService = mock(UserService.class);
        commentService = new CommentServiceImpl(commentMapper, postService, userService);
        UserContext.set(new LoginUser(1L, "USER"));

        when(commentMapper.insert(any(CommentDO.class))).thenAnswer(invocation -> {
            CommentDO comment = invocation.getArgument(0);
            comment.setId(5001L);
            return 1;
        });
        when(userService.getBriefs(any())).thenReturn(Map.of(
                1L, brief(1L, "小光"),
                2L, brief(2L, "阿野"),
                3L, brief(3L, "小满")));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreateRootCommentAndIncreasePostCount() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);

        CommentVO response = commentService.create("1001",
                new CreateCommentDTO("  这地方太美了  ", null, null));

        assertThat(response.id()).isEqualTo("5001");
        assertThat(response.postId()).isEqualTo("1001");
        assertThat(response.parentId()).isEqualTo("0");
        assertThat(response.content()).isEqualTo("这地方太美了");
        assertThat(response.author().nickname()).isEqualTo("小光");
        assertThat(response.replyToUser()).isNull();
        assertThat(response.deleted()).isFalse();
        assertThat(response.replyCount()).isZero();

        ArgumentCaptor<CommentDO> captor = ArgumentCaptor.forClass(CommentDO.class);
        verify(commentMapper).insert(captor.capture());
        CommentDO saved = captor.getValue();
        assertThat(saved.getPostId()).isEqualTo(1001L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getParentId()).isZero();
        assertThat(saved.getReplyToUserId()).isNull();
        assertThat(saved.getIsDeleted()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(postService).onCommentChanged(1001L, 1);
    }

    @Test
    void shouldCreateReplyToRootAndDefaultReplyToRootAuthor() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectById(5002L)).thenReturn(
                comment(5002L, 1001L, 2L, 0L, null, "根评论", 0, LocalDateTime.now()));

        CommentVO response = commentService.create("1001",
                new CreateCommentDTO("同问！", "5002", null));

        assertThat(response.parentId()).isEqualTo("5002");
        assertThat(response.replyToUser().id()).isEqualTo("2");

        ArgumentCaptor<CommentDO> captor = ArgumentCaptor.forClass(CommentDO.class);
        verify(commentMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(5002L);
        assertThat(captor.getValue().getReplyToUserId()).isEqualTo(2L);
        verify(postService).onCommentChanged(1001L, 1);
    }

    @Test
    void shouldNormalizeReplyParentToRootComment() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectById(5003L)).thenReturn(
                comment(5003L, 1001L, 3L, 5002L, 2L, "二级回复", 0, LocalDateTime.now()));
        when(commentMapper.selectById(5002L)).thenReturn(
                comment(5002L, 1001L, 2L, 0L, null, "根评论", 0, LocalDateTime.now()));

        commentService.create("1001", new CreateCommentDTO("回复你", "5003", "3"));

        ArgumentCaptor<CommentDO> captor = ArgumentCaptor.forClass(CommentDO.class);
        verify(commentMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(5002L);
        assertThat(captor.getValue().getReplyToUserId()).isEqualTo(3L);
    }

    @Test
    void shouldRejectReplyToReplyWithoutReplyToUser() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectById(5003L)).thenReturn(
                comment(5003L, 1001L, 3L, 5002L, 2L, "二级回复", 0, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.create("1001",
                new CreateCommentDTO("回复你", "5003", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verify(commentMapper, never()).insert(any(CommentDO.class));
    }

    @Test
    void shouldRejectParentFromAnotherPost() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectById(5002L)).thenReturn(
                comment(5002L, 1002L, 2L, 0L, null, "别的帖子", 0, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.create("1001",
                new CreateCommentDTO("回复", "5002", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verify(commentMapper, never()).insert(any(CommentDO.class));
    }

    @Test
    void shouldRejectDeletedParent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectById(5002L)).thenReturn(
                comment(5002L, 1001L, 2L, 0L, null, "已删除", 1, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.create("1001",
                new CreateCommentDTO("回复", "5002", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    void shouldRejectBlankContent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);

        assertThatThrownBy(() -> commentService.create("1001",
                new CreateCommentDTO("   ", null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
        verify(commentMapper, never()).insert(any(CommentDO.class));
    }

    @Test
    void shouldRejectTooLongContent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        String content = "字".repeat(1001);

        assertThatThrownBy(() -> commentService.create("1001",
                new CreateCommentDTO(content, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTENT_INVALID));
    }

    @Test
    void shouldRejectCreateWhenPostNotVisible() {
        when(postService.resolvePublicPostId("1001"))
                .thenThrow(new BizException(ErrorCode.POST_NOT_VISIBLE));

        assertThatThrownBy(() -> commentService.create("1001",
                new CreateCommentDTO("内容", null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verifyNoInteractions(commentMapper);
    }

    @Test
    void shouldListRootCommentsWithReplyCountAndDeletedPlaceholder() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);
        CommentDO normal = comment(5001L, 1001L, 1L, 0L, null, "正常评论", 0, now);
        CommentDO deleted = comment(5002L, 1001L, 2L, 0L, null, "原始内容", 1, now.minusMinutes(1));
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectList(any())).thenReturn(List.of(normal, deleted));
        when(commentMapper.countVisibleReplies(eq(1001L), any())).thenReturn(List.of(
                replyCount(5001L, 2), replyCount(5002L, 1)));

        CursorPage<CommentVO> page = commentService.listByPost("1001", null, 20);

        assertThat(page.items()).hasSize(2);
        CommentVO first = page.items().get(0);
        assertThat(first.id()).isEqualTo("5001");
        assertThat(first.content()).isEqualTo("正常评论");
        assertThat(first.author().nickname()).isEqualTo("小光");
        assertThat(first.deleted()).isFalse();
        assertThat(first.replyCount()).isEqualTo(2);

        CommentVO second = page.items().get(1);
        assertThat(second.id()).isEqualTo("5002");
        assertThat(second.content()).isEqualTo("该评论已删除");
        assertThat(second.author()).isNull();
        assertThat(second.deleted()).isTrue();
        assertThat(second.replyCount()).isEqualTo(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldFilterDeletedRootWithoutReplies() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(5002L, 1001L, 2L, 0L, null, "已删除", 1, LocalDateTime.now())));
        when(commentMapper.countVisibleReplies(eq(1001L), any())).thenReturn(List.of());

        CursorPage<CommentVO> page = commentService.listByPost("1001", null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldKeepCursorWhenFilteredPageIsSparse() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(5002L, 1001L, 2L, 0L, null, "已删除", 1, now),
                comment(5003L, 1001L, 3L, 0L, null, "下一页", 0, now.minusMinutes(1))));
        when(commentMapper.countVisibleReplies(eq(1001L), any())).thenReturn(List.of());

        CursorPage<CommentVO> page = commentService.listByPost("1001", null, 1);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void shouldListRepliesInAscendingOrderAndFillUsers() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 1L, 0L, null, "根评论", 0, now));
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(5002L, 1001L, 2L, 5001L, 1L, "第一条回复", 0, now),
                comment(5003L, 1001L, 3L, 5001L, 2L, "第二条回复", 0, now.plusMinutes(1))));

        CursorPage<ReplyVO> page = commentService.listReplies("5001", null, 20);

        assertThat(page.items()).extracting(ReplyVO::id).containsExactly("5002", "5003");
        assertThat(page.items().get(0).commentId()).isEqualTo("5001");
        assertThat(page.items().get(0).author().nickname()).isEqualTo("阿野");
        assertThat(page.items().get(0).replyToUser().nickname()).isEqualTo("小光");
        assertThat(page.items().get(1).replyToUser().nickname()).isEqualTo("阿野");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void shouldRejectRepliesForNonRootComment() {
        when(commentMapper.selectById(5003L)).thenReturn(
                comment(5003L, 1001L, 3L, 5002L, 2L, "二级回复", 0, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.listReplies("5003", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        verifyNoInteractions(postService);
    }

    @Test
    void shouldReturnNotFoundForMissingRootComment() {
        when(commentMapper.selectById(5001L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.listReplies("5001", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    void shouldRejectRepliesWhenPostNotVisible() {
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 1L, 0L, null, "根评论", 0, LocalDateTime.now()));
        when(postService.resolvePublicPostId("1001"))
                .thenThrow(new BizException(ErrorCode.POST_NOT_VISIBLE));

        assertThatThrownBy(() -> commentService.listReplies("5001", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    void shouldDeleteByCommentAuthorAndDecreasePostCount() {
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 1L, 0L, null, "评论", 0, LocalDateTime.now()));
        when(commentMapper.update(isNull(), any())).thenReturn(1);

        commentService.delete("5001");

        verify(commentMapper).update(isNull(), any());
        verify(postService).onCommentChanged(1001L, -1);
    }

    @Test
    void shouldDeleteByPostAuthor() {
        UserContext.set(new LoginUser(2L, "USER"));
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 3L, 0L, null, "评论", 0, LocalDateTime.now()));
        when(postService.isPostAuthor(1001L, 2L)).thenReturn(true);
        when(commentMapper.update(isNull(), any())).thenReturn(1);

        commentService.delete("5001");

        verify(postService).onCommentChanged(1001L, -1);
    }

    @Test
    void shouldDeleteByAdmin() {
        UserContext.set(new LoginUser(9L, "ADMIN"));
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 3L, 0L, null, "评论", 0, LocalDateTime.now()));
        when(commentMapper.update(isNull(), any())).thenReturn(1);

        commentService.delete("5001");

        verify(postService).onCommentChanged(1001L, -1);
        verify(postService, never()).isPostAuthor(anyLong(), anyLong());
    }

    @Test
    void shouldRejectDeleteByOtherUser() {
        UserContext.set(new LoginUser(2L, "USER"));
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 3L, 0L, null, "评论", 0, LocalDateTime.now()));
        when(postService.isPostAuthor(1001L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.delete("5001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(commentMapper, never()).update(isNull(), any());
        verify(postService, never()).onCommentChanged(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldTreatRepeatDeleteAsIdempotent() {
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 1L, 0L, null, "已删除", 1, LocalDateTime.now()));

        commentService.delete("5001");

        verify(commentMapper, never()).update(isNull(), any());
        verify(postService, never()).onCommentChanged(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingComment() {
        when(commentMapper.selectById(5001L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.delete("5001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    void shouldNotDecreaseCountWhenSoftDeleteLosesRace() {
        when(commentMapper.selectById(5001L)).thenReturn(
                comment(5001L, 1001L, 1L, 0L, null, "评论", 0, LocalDateTime.now()));
        when(commentMapper.update(isNull(), any())).thenReturn(0);

        commentService.delete("5001");

        verify(postService, never()).onCommentChanged(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    private CommentDO comment(long id, long postId, long userId, long parentId, Long replyToUserId,
                              String content, int isDeleted, LocalDateTime createdAt) {
        CommentDO comment = new CommentDO();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setIsDeleted(isDeleted);
        comment.setCreatedAt(createdAt);
        return comment;
    }

    private CommentReplyCount replyCount(long parentId, int count) {
        CommentReplyCount replyCount = new CommentReplyCount();
        replyCount.setParentId(parentId);
        replyCount.setReplyCount(count);
        return replyCount;
    }

    private UserBriefVO brief(long id, String nickname) {
        return new UserBriefVO(String.valueOf(id), nickname, "");
    }
}
