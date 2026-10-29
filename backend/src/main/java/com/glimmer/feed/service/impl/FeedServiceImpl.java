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

    /**
     * 先取当前用户关注的作者 ID；未关注任何人时直接返回空页，否则委托
     * {@link PostService#listByAuthors} 按发布时间倒序查询公开帖子。
     *
     * @param cursor 上一页游标，可为空
     * @param size   每页数量，默认 20
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> followFeed(String cursor, Integer size) {
        List<Long> followingIds = userService.listFollowingIds(UserContext.userId());
        if (followingIds.isEmpty()) {
            return CursorPage.empty();
        }
        return postService.listByAuthors(followingIds, cursor, size);
    }
}
