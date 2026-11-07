package com.glimmer.feed.config;

import com.glimmer.feed.mq.FeedMqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feed RabbitMQ 拓扑：正常队列 + 死信队列。
 *
 * <p>消息消费失败时由消费者 {@code basicNack(requeue=false)} 转入死信交换机，
 * 避免毒消息无限重回原队列；死信队列保留现场，便于排查和人工重放。
 */
@Slf4j
@Configuration
@EnableRabbit
public class FeedMqConfig {

    @Bean
    public TopicExchange feedExchange() {
        return new TopicExchange(FeedMqConstants.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange feedDeadLetterExchange() {
        return new DirectExchange(FeedMqConstants.DLX, true, false);
    }

    @Bean
    public Queue feedFanoutQueue() {
        return QueueBuilder.durable(FeedMqConstants.FANOUT_QUEUE)
                .deadLetterExchange(FeedMqConstants.DLX)
                .deadLetterRoutingKey(FeedMqConstants.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue feedFanoutDeadLetterQueue() {
        return QueueBuilder.durable(FeedMqConstants.DLQ).build();
    }

    @Bean
    public Binding feedFanoutBinding(
            @Qualifier("feedFanoutQueue") Queue queue,
            @Qualifier("feedExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FeedMqConstants.POST_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public Binding feedDeadLetterBinding(
            @Qualifier("feedFanoutDeadLetterQueue") Queue queue,
            @Qualifier("feedDeadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(FeedMqConstants.DEAD_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器；限制反序列化可信包，避免任意类型反序列化。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.glimmer.feed.mq", "java.util", "java.lang");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * 路由失败（exchange 有但 queue 没绑上）时记录返回消息，便于发现拓扑配置错误。
     */
    @Bean
    public RabbitTemplateCustomizer feedRabbitTemplateCustomizer() {
        return template -> template.setReturnsCallback(returned ->
                log.warn("feed message returned: exchange={}, routingKey={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
