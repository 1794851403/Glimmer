package com.glimmer.content.controller;

import com.glimmer.common.api.Result;
import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostCreateVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子接口。必须登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 发布帖子（需登录）。
     *
     * <p>普通用户发布后为待审核状态，管理员跳过审核；图片与标签随帖子同一事务写入。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostCreateVO> create(@RequestBody CreatePostDTO request) {
        return Result.ok(postService.create(request));
    }
}
