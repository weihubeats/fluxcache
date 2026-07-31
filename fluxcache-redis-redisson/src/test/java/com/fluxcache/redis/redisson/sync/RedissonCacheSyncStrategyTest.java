package com.fluxcache.redis.redisson.sync;

import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RedissonCacheSyncStrategyTest {

    private RedissonClient redissonClient;
    private CacheSyncStrategy syncStrategy;
    private CacheSyncPostProcessor processor;

    @Before
    public void setUp() {
        redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        processor = mock(CacheSyncPostProcessor.class);
        syncStrategy = new RedissonCacheSyncStrategy(redissonClient, Arrays.asList(processor));
    }

    @Test
    public void postClear_publishesAndProcesses() {
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.singletonList("test-key"));
        dto.setTopicName("test-topic");

        syncStrategy.postClear(dto);

        verify(redissonClient).getTopic(eq("test-topic"));
        verify(redissonClient.getTopic("test-topic")).publish(eq(dto));
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void postEvict_publishesAndProcesses() {
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.singletonList("test-key"));
        dto.setTopicName("test-topic");

        syncStrategy.postEvict(dto);

        verify(redissonClient.getTopic("test-topic")).publish(eq(dto));
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void postEvict_publishExceptionDoesNotBreakPostProcess() {
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.singletonList("test-key"));
        dto.setTopicName("test-topic");
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        doThrow(new RuntimeException("redis down")).when(topic).publish(any());

        syncStrategy.postEvict(dto);
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void sendPutEvent_publishesAndProcesses() {
        PutCacheDTO dto = new PutCacheDTO("test-cache", "test-key", "test-value");
        dto.setTopicName("test-topic");

        syncStrategy.sendPutEvent(dto);

        verify(redissonClient.getTopic("test-topic")).publish(eq(dto));
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void sendPutEvent_publishExceptionDoesNotBreakPostProcess() {
        PutCacheDTO dto = new PutCacheDTO("test-cache", "test-key", "test-value");
        dto.setTopicName("test-topic");
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        doThrow(new RuntimeException("redis down")).when(topic).publish(any());

        syncStrategy.sendPutEvent(dto);
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void postProcess_emptyList_doesNotThrow() {
        RedissonCacheSyncStrategy strategy = new RedissonCacheSyncStrategy(redissonClient, Collections.emptyList());
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.emptyList());
        dto.setTopicName("test-topic");
        try {
            strategy.postEvict(dto);
        } catch (Throwable e) {
            fail("should not throw: " + e.getMessage());
        }
    }
}
