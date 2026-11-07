package com.glimmer.feed.mq;

import com.glimmer.feed.service.FeedFanoutService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedFanoutListenerTest {

    private FeedFanoutService feedFanoutService;
    private Channel channel;
    private FeedFanoutListener listener;

    @BeforeEach
    void setUp() {
        feedFanoutService = mock(FeedFanoutService.class);
        channel = mock(Channel.class);
        listener = new FeedFanoutListener(feedFanoutService);
    }

    @Test
    void shouldAckAfterSuccessfulFanout() throws Exception {
        PostPublishedMessage message = new PostPublishedMessage(100L, 2L, 5000L, 1);

        listener.onMessage(message, channel, 7L);

        verify(feedFanoutService).fanout(message);
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldAckAndDropEmptyMessage() throws Exception {
        listener.onMessage(null, channel, 8L);

        verifyNoInteractions(feedFanoutService);
        verify(channel).basicAck(8L, false);
    }

    @Test
    void shouldNackWithoutRequeueOnFailure() throws Exception {
        PostPublishedMessage message = new PostPublishedMessage(100L, 2L, 5000L, 1);
        doThrow(new RuntimeException("redis down"))
                .when(feedFanoutService).fanout(message);

        listener.onMessage(message, channel, 9L);

        verify(channel).basicNack(9L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void shouldNotPropagateNackFailure() throws Exception {
        PostPublishedMessage message = new PostPublishedMessage(100L, 2L, 5000L, 1);
        doThrow(new RuntimeException("fanout failed"))
                .when(feedFanoutService).fanout(message);
        doThrow(new java.io.IOException("channel closed"))
                .when(channel).basicNack(9L, false, false);

        listener.onMessage(message, channel, 9L);

        verify(channel).basicNack(9L, false, false);
    }
}
