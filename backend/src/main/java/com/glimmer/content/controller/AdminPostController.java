package com.glimmer.content.controller;

import com.glimmer.common.api.Result;
import com.glimmer.content.dto.AuditReasonDTO;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostAuditVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端帖子审核接口。仅 ADMIN 可访问，角色校验由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 审核通过（需管理员）。
     *
     * <p>仅待审核（status=0）可操作；已是已发布状态时幂等返回；不能审核自己的帖子。
     */
    @PostMapping("/{postId}/approve")
    public Result<PostAuditVO> approve(@PathVariable String postId) {
        return Result.ok(postService.approve(postId));
    }

    /**
     * 审核拒绝（需管理员）。
     *
     * <p>仅待审核（status=0）可操作；拒绝原因 1~200 字符；不能审核自己的帖子。
     */
    @PostMapping("/{postId}/reject")
    public Result<PostAuditVO> reject(@PathVariable String postId,
                                      @RequestBody AuditReasonDTO request) {
        return Result.ok(postService.reject(postId, request == null ? null : request.reason()));
    }

    /**
     * 下架帖子（需管理员）。
     *
     * <p>待审核 / 已发布（status=0/1）可下架；下架原因 1~200 字符；不能操作自己的帖子。
     */
    @PostMapping("/{postId}/takedown")
    public Result<PostAuditVO> takedown(@PathVariable String postId,
                                        @RequestBody AuditReasonDTO request) {
        return Result.ok(postService.takedown(postId, request == null ? null : request.reason()));
    }

    /**
     * 恢复已下架帖子（需管理员）。
     *
     * <p>仅已下架（status=3）可恢复为已发布；不能操作自己的帖子。
     */
    @PostMapping("/{postId}/restore")
    public Result<PostAuditVO> restore(@PathVariable String postId) {
        return Result.ok(postService.restore(postId));
    }
}
