package com.fluxcache.redis.spring.sync;

import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RedisTemplateCacheSyncStrategyTest {

    private RedisTemplate<String, Object> redisTemplate;
    private CacheSyncStrategy syncStrategy;
    private CacheSyncPostProcessor processor;

    @Before
    public void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        processor = mock(CacheSyncPostProcessor.class);
        syncStrategy = new RedisTemplateCacheSyncStrategy(redisTemplate, Arrays.asList(processor));
    }

    @Test
    public void postClear_publishesAndProcesses() {
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.singletonList("test-key"));
        dto.setTopicName("test-topic");

        syncStrategy.postClear(dto);

        verify(redisTemplate).convertAndSend(eq("test-topic"), eq(dto));
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void postEvict_publishesAndProcesses() {
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.singletonList("test-key"));
        dto.setTopicName("test-topic");

        syncStrategy.postEvict(dto);

        verify(redisTemplate).convertAndSend(eq("test-topic"), eq(dto));
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void postEvict_publishExceptionDoesNotBreakPostProcess() {
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.singletonList("test-key"));
        dto.setTopicName("test-topic");
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(eq("test-topic"), eq(dto));

        syncStrategy.postEvict(dto);

        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void sendPutEvent_publishesAndProcesses() {
        PutCacheDTO dto = new PutCacheDTO("test-cache", "test-key", "test-value");
        dto.setTopicName("test-topic");

        syncStrategy.sendPutEvent(dto);

        verify(redisTemplate).convertAndSend(eq("test-topic"), eq(dto));
        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void sendPutEvent_publishExceptionDoesNotBreakPostProcess() {
        PutCacheDTO dto = new PutCacheDTO("test-cache", "test-key", "test-value");
        dto.setTopicName("test-topic");
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(eq("test-topic"), eq(dto));

        syncStrategy.sendPutEvent(dto);

        verify(processor).postProcess(eq(dto));
    }

    @Test
    public void postProcess_emptyList_doesNotThrow() {
        RedisTemplateCacheSyncStrategy strategy = new RedisTemplateCacheSyncStrategy(redisTemplate, Collections.emptyList());
        DeleteCacheDTO dto = new DeleteCacheDTO("test-cache", Collections.emptyList());
        dto.setTopicName("test-topic");
        try {
            strategy.postEvict(dto);
        } catch (Throwable e) {
            fail("should not throw: " + e.getMessage());
        }
    }
}
