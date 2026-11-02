package com.glimmer.content.controller;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.Result;
import com.glimmer.content.dto.CreatePostDTO;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostCreateVO;
import com.glimmer.content.vo.PostDetailVO;
import com.glimmer.content.vo.PostSummaryVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子接口。发布、编辑、删除需登录，公开列表与详情可选登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 全站最新公开帖子（可选登录）。
     *
     * <p>当前只支持 sort=latest；hot 待热榜模块接入。坏游标按首页处理。
     *
     * @param sort   排序方式，可为空
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping
    public Result<CursorPage<PostSummaryVO>> list(@RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer size) {
        return Result.ok(postService.listLatest(sort, cursor, size));
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

    /**
     * 帖子详情（可选登录）。
     *
     * <p>游客只能查看公开可见帖子；作者本人可查看自己的未删除帖子；管理员可查看任意未删除帖子。
     */
    @GetMapping("/{postId}")
    public Result<PostDetailVO> detail(@PathVariable String postId) {
        return Result.ok(postService.getDetail(postId));
    }

    /**
     * 编辑帖子（需登录，仅作者本人）。
     *
     * <p>全量替换标题、正文、图片与标签；编辑后状态回到 0 待审核并清空审核信息。
     * 已下架帖子不可编辑。
     */
    @PutMapping("/{postId}")
    public Result<PostDetailVO> update(@PathVariable String postId,
                                       @RequestBody CreatePostDTO request) {
        return Result.ok(postService.update(postId, request));
    }

    /**
     * 删除帖子（需登录，仅作者本人）。
     *
     * <p>软删除，重复删除幂等返回 204。
     */
    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String postId) {
        postService.delete(postId);
    }

    /**
     * 重新提交审核（需登录，仅作者本人）。
     *
     * <p>不修改内容，仅审核拒绝（status=2）的帖子可重提；成功后回到 0 待审核。
     */
    @PostMapping("/{postId}/resubmit")
    public Result<PostCreateVO> resubmit(@PathVariable String postId) {
        return Result.ok(postService.resubmit(postId));
    }
}
