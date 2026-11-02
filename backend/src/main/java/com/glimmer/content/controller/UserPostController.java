package com.glimmer.content.controller;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.Result;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.MyPostSummaryVO;
import com.glimmer.content.vo.PostSummaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户维度的帖子接口。
 *
 * <p>放在 content 模块，避免 user 模块反向依赖 content；路径仍归在 /users 下对前端保持统一。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserPostController {

    private final PostService postService;

    public UserPostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 用户公开帖子（可选登录）。
     *
     * <p>只返回公开可见帖子，按发布时间倒序；用户不存在或已封禁返回 40401。
     *
     * @param userId 作者用户 ID
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping("/{userId}/posts")
    public Result<CursorPage<PostSummaryVO>> userPosts(@PathVariable String userId,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) Integer size) {
        return Result.ok(postService.listByAuthor(userId, cursor, size));
    }

    /**
     * 我的帖子（需登录）。
     *
     * <p>包含全部未删除状态，可按 status=0~3 筛选；按创建时间倒序。
     *
     * @param status 帖子状态，可为空表示全部
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping("/me/posts")
    public Result<CursorPage<MyPostSummaryVO>> myPosts(@RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) Integer size) {
        return Result.ok(postService.listMyPosts(status, cursor, size));
    }
}
