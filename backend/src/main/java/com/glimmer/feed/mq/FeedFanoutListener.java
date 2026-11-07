package com.glimmer.feed.mq;

import com.glimmer.feed.service.FeedFanoutService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 写扩散消费者：手动 ack，失败进死信队列。
 */
@Slf4j
@Component
public class FeedFanoutListener {

    private final FeedFanoutService feedFanoutService;

    public FeedFanoutListener(FeedFanoutService feedFanoutService) {
        this.feedFanoutService = feedFanoutService;
    }

    @RabbitListener(queues = FeedMqConstants.FANOUT_QUEUE)
    public void onMessage(PostPublishedMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null) {
            log.warn("empty feed message, drop it");
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            feedFanoutService.fanout(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("feed fanout failed, postId={}, authorId={}",
                    message.postId(), message.authorId(), e);
            try {
                // 不重回原队列，避免毒消息死循环；消息进死信队列保留现场。
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException nackError) {
                log.error("nack feed message failed, deliveryTag={}", deliveryTag, nackError);
            }
        }
    }
}
