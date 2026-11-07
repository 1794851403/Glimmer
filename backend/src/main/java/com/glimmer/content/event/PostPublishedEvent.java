package com.glimmer.content.event;

/**
 * 帖子发布成功事件。
 *
 * <p>在发帖事务内发布，Feed 模块用 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 接收，保证只有事务提交成功后才做写扩散。content 模块不依赖 feed 模块。
 *
 * @param postId            帖子 ID
 * @param authorId          作者用户 ID
 * @param publishedAtMillis 发布时间，epoch 毫秒
 * @param status            帖子状态：0=已发布待审核，1=已发布
 */
public record PostPublishedEvent(
        Long postId,
        Long authorId,
        long publishedAtMillis,
        int status
) {
}
