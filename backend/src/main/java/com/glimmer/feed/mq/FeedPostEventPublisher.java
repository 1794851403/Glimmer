package com.glimmer.feed.mq;

import com.glimmer.content.event.PostPublishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 把 content 的帖子发布事件转成 RabbitMQ 消息。
 *
 * <p>监听器在事务提交后执行；发送失败只记日志，不影响发帖接口。
 * 不等待 publisher confirm，避免 Broker 异常时拖慢发帖。
 */
@Slf4j
@Component
public class FeedPostEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public FeedPostEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        if (event == null || event.postId() == null || event.authorId() == null) {
            return;
        }
        PostPublishedMessage message = new PostPublishedMessage(
                event.postId(), event.authorId(), event.publishedAtMillis(), event.status());
        CorrelationData correlationData = new CorrelationData(String.valueOf(event.postId()));
        try {
            rabbitTemplate.convertAndSend(
                    FeedMqConstants.EXCHANGE,
                    FeedMqConstants.POST_PUBLISHED_ROUTING_KEY,
                    message,
                    correlationData);
            correlationData.getFuture().whenComplete((confirm, ex) -> {
                if (ex != null) {
                    log.warn("feed message confirm failed, postId={}", event.postId(), ex);
                } else if (confirm == null || !confirm.isAck()) {
                    log.warn("feed message nacked, postId={}, reason={}", event.postId(),
                            confirm == null ? "null" : confirm.getReason());
                }
            });
        } catch (AmqpException e) {
            // 发帖优先：MQ 不可用时只记日志，读路径由回源兜底。
            log.error("send feed message failed, postId={}", event.postId(), e);
        }
    }
}
