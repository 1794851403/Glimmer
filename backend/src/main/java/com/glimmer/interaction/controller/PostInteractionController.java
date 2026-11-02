package com.glimmer.interaction.controller;

import com.glimmer.common.api.Result;
import com.glimmer.interaction.service.CollectService;
import com.glimmer.interaction.service.LikeService;
import com.glimmer.interaction.vo.CollectResultVO;
import com.glimmer.interaction.vo.LikeResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子互动接口：点赞与收藏。均需登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}")
public class PostInteractionController {

    private final LikeService likeService;
    private final CollectService collectService;

    public PostInteractionController(LikeService likeService, CollectService collectService) {
        this.likeService = likeService;
        this.collectService = collectService;
    }

    /**
     * 点赞（需登录）。
     *
     * <p>重复点赞幂等，返回当前状态与最新点赞数；帖子不存在或不可见返回 40402。
     *
     * @param postId 帖子 ID
     */
    @PostMapping("/like")
    public Result<LikeResultVO> like(@PathVariable String postId) {
        return Result.ok(likeService.like(postId));
    }

    /**
     * 取消点赞（需登录）。
     *
     * <p>重复取消幂等；帖子不存在或不可见返回 40402。
     *
     * @param postId 帖子 ID
     */
    @DeleteMapping("/like")
    public Result<LikeResultVO> unlike(@PathVariable String postId) {
        return Result.ok(likeService.unlike(postId));
    }

    /**
     * 收藏（需登录）。
     *
     * <p>重复收藏幂等，返回当前状态与最新收藏数；帖子不存在或不可见返回 40402。
     *
     * @param postId 帖子 ID
     */
    @PostMapping("/collect")
    public Result<CollectResultVO> collect(@PathVariable String postId) {
        return Result.ok(collectService.collect(postId));
    }

    /**
     * 取消收藏（需登录）。
     *
     * <p>重复取消幂等；帖子不存在或不可见返回 40402。
     *
     * @param postId 帖子 ID
     */
    @DeleteMapping("/collect")
    public Result<CollectResultVO> uncollect(@PathVariable String postId) {
        return Result.ok(collectService.uncollect(postId));
    }
}
