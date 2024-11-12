package com.fluxcache.core.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.interceptor.FluxCacheErrorHandler;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * @author : wh
 * @date : 2024/11/12 12:34
 * @description:
 */
@Configuration(proxyBeanMethods = false)
public class FluxAbstractCachingConfiguration {

    @Nullable
    protected Supplier<FluxCacheManager> fluxCacheManagerSupplier;

    @Nullable
    protected Supplier<CacheResolver> cacheResolver;

    protected Supplier<FluxKeyGenerator> fluxKeyGeneratorSupplier;

    protected Supplier<FluxCacheErrorHandler> fluxCacheErrorHandlerSupplier;

    @Autowired
    void setConfigurers(ObjectProvider<FluxCachingConfigurer> fluxCachingConfigurers) {
        Supplier<FluxCachingConfigurer> configurer = () -> {
            List<FluxCachingConfigurer> candidates = fluxCachingConfigurers.stream().collect(Collectors.toList());
            if (CollectionUtils.isEmpty(candidates)) {
                return null;
            }
            if (candidates.size() > 1) {
                throw new IllegalStateException(candidates.size() + " implementations of " +
                    "CachingConfigurer were found when only 1 was expected. " +
                    "Refactor the configuration such that CachingConfigurer is " +
                    "implemented only once or not at all.");
            }
            return candidates.get(0);
        };
        useFluxCachingConfigurer(new FluxCachingConfigurerSupplier(configurer));
    }

    protected void useFluxCachingConfigurer(
        FluxCachingConfigurerSupplier cachingConfigurerSupplier) {
        this.fluxCacheManagerSupplier = cachingConfigurerSupplier.adapt(FluxCachingConfigurer::cacheManager);
        // todo flux cacheResolver
        this.cacheResolver = cachingConfigurerSupplier.adapt(FluxCachingConfigurer::cacheResolver);
        this.fluxKeyGeneratorSupplier = cachingConfigurerSupplier.adapt(FluxCachingConfigurer::keyGenerator);
        this.fluxCacheErrorHandlerSupplier = cachingConfigurerSupplier.adapt(FluxCachingConfigurer::errorHandler);
    }

    protected static class FluxCachingConfigurerSupplier {

        private final Supplier<FluxCachingConfigurer> supplier;

        public FluxCachingConfigurerSupplier(Supplier<FluxCachingConfigurer> supplier) {
            this.supplier = SingletonSupplier.of(supplier);
        }

        @Nullable
        public <T> Supplier<T> adapt(Function<FluxCachingConfigurer, T> provider) {
            return () -> {
                FluxCachingConfigurer cachingConfigurer = this.supplier.get();
                return (cachingConfigurer != null ? provider.apply(cachingConfigurer) : null);
            };
        }
    }

}
