package com.glimmer.feed.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.vo.PostSummaryVO;

/**
 * Feed 模块对外的服务。
 */
public interface FeedService {

    /**
     * 当前用户关注的作者的公开帖子流。
     *
     * <p>第一轮为“收件箱优先 + MySQL 兜底”：发帖后异步写粉丝收件箱；
     * 收件箱凑不满一页或 Redis 不可用时回源 MySQL。未关注任何人时返回空页。
     *
     * @param cursor 上一页游标，可为空
     * @param size   每页数量，默认 20
     * @return 帖子摘要游标页
     */
    CursorPage<PostSummaryVO> followFeed(String cursor, Integer size);
}
