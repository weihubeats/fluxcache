package com.fluxcache.core.spi;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import com.fluxcache.core.model.FluxCacheCacheable;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存创建器注册表：构建、覆盖、懒加载、非法输入。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheCreatorRegistryTest {

    private static class StubCreator implements FluxCacheCreator {

        private final FluxCacheType type;

        StubCreator(FluxCacheType type) {
            this.type = type;
        }

        @Override
        public FluxCacheType supportType() {
            return type;
        }

        @Override
        public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable,
                                                           FluxCacheCreateContext context) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void listCtor_resolvesInstantly() {
        CaffeineFluxCacheCreator creator = new CaffeineFluxCacheCreator();
        FluxCacheCreatorRegistry registry =
                new FluxCacheCreatorRegistry(Collections.singletonList(creator));

        assertSame(creator, registry.getRequired(FluxCacheType.CAFFEINE));
        assertTrue(registry.supports(FluxCacheType.CAFFEINE));
        assertTrue(registry.getCreators().containsKey(FluxCacheType.CAFFEINE));
    }

    @Test
    public void getRequired_unsupportedType_throws() {
        FluxCacheCreatorRegistry registry =
                new FluxCacheCreatorRegistry(Collections.singletonList(new CaffeineFluxCacheCreator()));

        assertThrows(FluxCacheNotSupperException.class, () -> registry.getRequired(FluxCacheType.REDIS));
    }

    @Test
    public void supports_unknownType_false() {
        FluxCacheCreatorRegistry registry =
                new FluxCacheCreatorRegistry(Collections.singletonList(new CaffeineFluxCacheCreator()));

        assertTrue(!registry.supports(FluxCacheType.REDIS));
    }

    @Test
    public void duplicateType_lastWins() {
        StubCreator first = new StubCreator(FluxCacheType.REDIS);
        StubCreator second = new StubCreator(FluxCacheType.REDIS);
        FluxCacheCreatorRegistry registry =
                new FluxCacheCreatorRegistry(Arrays.asList(first, second));

        assertSame(second, registry.getRequired(FluxCacheType.REDIS));
    }

    @Test
    public void nullCreator_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new FluxCacheCreatorRegistry(Collections.singletonList(null)));
    }

    @Test
    public void nullSupportType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new FluxCacheCreatorRegistry(Collections.singletonList(new StubCreator(null))));
    }

    @Test
    public void providerCtor_lazyResolve() {
        StubCreator creator = new StubCreator(FluxCacheType.CAFFEINE);
        ObjectProvider<FluxCacheCreator> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(java.util.stream.Stream.of(creator));
        FluxCacheCreatorRegistry registry = new FluxCacheCreatorRegistry(provider);

        assertSame(creator, registry.getRequired(FluxCacheType.CAFFEINE));
        assertSame(creator, registry.getRequired(FluxCacheType.CAFFEINE));
        verify(provider, times(1)).orderedStream();
    }
}
