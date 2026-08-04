package com.fluxcache.core.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.interceptor.FluxCacheErrorHandler;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.interceptor.CacheResolver;

import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 配置器装配：空候选、单候选、多候选冲突与 Supplier 适配。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxAbstractCachingConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    public void setConfigurers_emptyCandidates_nullSuppliers() {
        TestConfig config = new TestConfig();
        ObjectProvider<FluxCachingConfigurer> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(inv -> java.util.stream.Stream.empty());

        config.setConfigurers(provider);

        assertNull(config.fluxCacheManagerSupplier.get());
        assertNull(config.cacheResolver.get());
        assertNull(config.fluxKeyGeneratorSupplier.get());
        assertNull(config.fluxCacheErrorHandlerSupplier.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void setConfigurers_singleCandidate_adaptsAll() {
        TestConfig config = new TestConfig();
        FluxCacheManager manager = mock(FluxCacheManager.class);
        CacheResolver resolver = mock(CacheResolver.class);
        FluxKeyGenerator keyGenerator = (target, method, params) -> "k";
        FluxCacheErrorHandler errorHandler = mock(FluxCacheErrorHandler.class);
        FluxCachingConfigurer doc = new FluxCachingConfigurer() {
            @Override
            public FluxCacheManager cacheManager() {
                return manager;
            }

            @Override
            public CacheResolver cacheResolver() {
                return resolver;
            }

            @Override
            public FluxKeyGenerator keyGenerator() {
                return keyGenerator;
            }

            @Override
            public FluxCacheErrorHandler errorHandler() {
                return errorHandler;
            }
        };
        ObjectProvider<FluxCachingConfigurer> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(inv -> java.util.stream.Stream.of(doc));

        config.setConfigurers(provider);

        assertSame(manager, config.fluxCacheManagerSupplier.get());
        assertSame(resolver, config.cacheResolver.get());
        assertSame(keyGenerator, config.fluxKeyGeneratorSupplier.get());
        assertSame(errorHandler, config.fluxCacheErrorHandlerSupplier.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void setConfigurers_multipleCandidates_throws() {
        TestConfig config = new TestConfig();
        ObjectProvider<FluxCachingConfigurer> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(inv -> java.util.stream.Stream.of(
                        mock(FluxCachingConfigurer.class),
                        mock(FluxCachingConfigurer.class)));

        try {
            config.setConfigurers(provider);
            config.fluxCacheManagerSupplier.get();
            fail("应抛出 IllegalStateException");
        } catch (IllegalStateException expected) {
            // multiple configurers
        }
    }

    @Test
    public void nullValue_asConfigurer_returnsNull() {
        FluxAbstractCachingConfiguration.FluxCachingConfigurerSupplier supplier =
                new FluxAbstractCachingConfiguration.FluxCachingConfigurerSupplier(() -> null);
        assertNull(supplier.adapt(FluxCachingConfigurer::cacheManager).get());
    }

    private static class TestConfig extends FluxAbstractCachingConfiguration {

        // expose protected setConfigurers
        @Override
        public void setConfigurers(ObjectProvider<FluxCachingConfigurer> fluxCachingConfigurers) {
            super.setConfigurers(fluxCachingConfigurers);
        }
    }
}