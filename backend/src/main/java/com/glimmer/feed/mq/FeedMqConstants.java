package com.glimmer.feed.mq;

/**
 * Feed 写扩散用到的 RabbitMQ 名称。
 */
public final class FeedMqConstants {

    /** 帖子发布事件交换机。 */
    public static final String EXCHANGE = "glimmer.feed.exchange";

    /** 写扩散队列。 */
    public static final String FANOUT_QUEUE = "glimmer.feed.fanout.queue";

    /** 帖子发布路由键。 */
    public static final String POST_PUBLISHED_ROUTING_KEY = "feed.post.published";

    /** 死信交换机。 */
    public static final String DLX = "glimmer.feed.dlx";

    /** 死信队列。 */
    public static final String DLQ = "glimmer.feed.fanout.dlq";

    /** 死信路由键。 */
    public static final String DEAD_ROUTING_KEY = "feed.fanout.dead";

    private FeedMqConstants() {
    }
}
