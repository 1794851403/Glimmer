package com.glimmer.feed.service;

import com.glimmer.feed.mq.PostPublishedMessage;

/**
 * 帖子发布后的写扩散。
 */
public interface FeedFanoutService {

    /**
     * 给作者粉丝批量写收件箱；只写最近活跃用户。
     *
     * @param message 帖子发布消息
     */
    void fanout(PostPublishedMessage message);
}
