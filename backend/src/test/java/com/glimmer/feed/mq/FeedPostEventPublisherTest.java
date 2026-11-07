package com.glimmer.feed.mq;

import com.glimmer.content.event.PostPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FeedPostEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private FeedPostEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new FeedPostEventPublisher(rabbitTemplate);
    }

    @Test
    void shouldIgnoreInvalidEvent() {
        publisher.onPostPublished(null);
        publisher.onPostPublished(new PostPublishedEvent(null, 2L, 1000L, 1));
        publisher.onPostPublished(new PostPublishedEvent(1L, null, 1000L, 1));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldConvertEventToMessage() {
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.onPostPublished(new PostPublishedEvent(100L, 2L, 5000L, 1));

        verify(rabbitTemplate).convertAndSend(
                eq(FeedMqConstants.EXCHANGE),
                eq(FeedMqConstants.POST_PUBLISHED_ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class));
        assertThat(messageCaptor.getValue()).isEqualTo(
                new PostPublishedMessage(100L, 2L, 5000L, 1));
    }

    @Test
    void shouldSwallowSendFailure() {
        doThrow(new AmqpException("broker down")).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.onPostPublished(
                new PostPublishedEvent(100L, 2L, 5000L, 1)))
                .doesNotThrowAnyException();
    }
}
