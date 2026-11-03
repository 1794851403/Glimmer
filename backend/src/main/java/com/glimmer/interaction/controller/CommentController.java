package com.glimmer.interaction.controller;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.Result;
import com.glimmer.interaction.dto.CreateCommentDTO;
import com.glimmer.interaction.service.CommentService;
import com.glimmer.interaction.vo.CommentVO;
import com.glimmer.interaction.vo.ReplyVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口。发表与删除需登录，两个列表可选登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 发表评论 / 回复（需登录）。
     *
     * <p>只做两级；回复某条回复时 parentId 归一化为根评论 ID。
     */
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<CommentVO> create(@PathVariable String postId,
                                    @RequestBody CreateCommentDTO request) {
        return Result.ok(commentService.create(postId, request));
    }

    /**
     * 一级评论列表（可选登录）。
     *
     * <p>只返回根评论与 replyCount，不返回二级回复内容；已删除但有回复的根评论返回占位。
     */
    @GetMapping("/posts/{postId}/comments")
    public Result<CursorPage<CommentVO>> list(@PathVariable String postId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer size) {
        return Result.ok(commentService.listByPost(postId, cursor, size));
    }

    /**
     * 回复列表（可选登录）。
     *
     * <p>commentId 必须是根评论；按创建时间正序，已删除的回复不返回。
     */
    @GetMapping("/comments/{commentId}/replies")
    public Result<CursorPage<ReplyVO>> replies(@PathVariable String commentId,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer size) {
        return Result.ok(commentService.listReplies(commentId, cursor, size));
    }

    /**
     * 删除评论 / 回复（需登录）。
     *
     * <p>允许评论作者、帖子作者与管理员；逻辑删除，重复删除幂等返回 204。
     */
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String commentId) {
        commentService.delete(commentId);
    }
}
