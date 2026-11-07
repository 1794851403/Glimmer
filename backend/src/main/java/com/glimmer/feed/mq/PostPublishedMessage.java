package com.glimmer.feed.mq;

/**
 * 帖子发布 MQ 消息。
 *
 * <p>只携带写扩散所需的最小字段，消费者不需要回查帖子表；读路径再按 postId
 * 批量回填摘要，能自然反映标题、状态等最新数据。
 *
 * @param postId            帖子 ID
 * @param authorId          作者用户 ID
 * @param publishedAtMillis 发布时间，epoch 毫秒
 * @param status            帖子状态：0=已发布待审核，1=已发布
 */
public record PostPublishedMessage(
        Long postId,
        Long authorId,
        long publishedAtMillis,
        int status
) {
}
