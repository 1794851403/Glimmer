package com.glimmer.feed.controller;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.api.Result;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.feed.service.FeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feed 接口。关注流必须登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * 关注流（需登录）。
     *
     * @param cursor 上一页游标，首页不传；坏游标按首页处理
     * @param size   每页数量，默认 20，越界自动夹到 1~50
     */
    @GetMapping("/follow")
    public Result<CursorPage<PostSummaryVO>> follow(@RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer size) {
        return Result.ok(feedService.followFeed(cursor, size));
    }
}
