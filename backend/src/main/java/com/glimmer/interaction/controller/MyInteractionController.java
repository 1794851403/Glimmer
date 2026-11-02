package com.glimmer.interaction.controller;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.Result;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.service.CollectService;
import com.glimmer.interaction.service.LikeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的互动列表接口（需登录）。
 *
 * <p>放在 interaction 模块，避免 user 模块反向依赖 interaction；路径仍归在 /users/me 下对前端保持统一。
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class MyInteractionController {

    private final LikeService likeService;
    private final CollectService collectService;

    public MyInteractionController(LikeService likeService, CollectService collectService) {
        this.likeService = likeService;
        this.collectService = collectService;
    }

    /**
     * 我的点赞列表。
     *
     * <p>按点赞时间倒序；只返回当前仍公开可见的帖子。
     * 坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping("/likes")
    public Result<CursorPage<PostSummaryVO>> myLikes(@RequestParam(required = false) String cursor,
                                                     @RequestParam(required = false) Integer size) {
        return Result.ok(likeService.listMyLikes(cursor, size));
    }

    /**
     * 我的收藏列表。
     *
     * <p>按收藏时间倒序；只返回当前仍公开可见的帖子。
     * 坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param cursor 上一页的 nextCursor，可为空
     * @param size   每页数量，可为空
     */
    @GetMapping("/collections")
    public Result<CursorPage<PostSummaryVO>> myCollections(@RequestParam(required = false) String cursor,
                                                           @RequestParam(required = false) Integer size) {
        return Result.ok(collectService.listMyCollections(cursor, size));
    }
}
