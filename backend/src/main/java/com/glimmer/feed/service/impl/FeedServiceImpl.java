package com.glimmer.feed.service.impl;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.feed.service.FeedService;
import com.glimmer.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关注流实现。
 *
 * <p>MVP 为读时拉取：先取关注关系，再按作者集合查公开帖子；
 * Redis 收件箱、大 V 拉模式与热榜留到后续迭代。
 */
@Service
public class FeedServiceImpl implements FeedService {

    private final UserService userService;
    private final PostService postService;

    public FeedServiceImpl(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @Override
    public CursorPage<PostSummaryVO> followFeed(String cursor, Integer size) {
        List<Long> followingIds = userService.listFollowingIds(UserContext.userId());
        if (followingIds.isEmpty()) {
            return CursorPage.empty();
        }
        return postService.listByAuthors(followingIds, cursor, size);
    }
}
