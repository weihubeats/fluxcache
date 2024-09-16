package com.fluxcache.core.annotation;

import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.interceptor.FluxCacheOperationSourcePointcut;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/8/31 15:02
 * @description:
 */
public class BeanFactoryFluxCacheOperationSourceAdvisor extends AbstractBeanFactoryPointcutAdvisor {

    @Nullable
    private FluxCacheOperationSource fluxCacheOperationSource;

    private final FluxCacheOperationSourcePointcut fluxCacheOperationSourcePointcut = new FluxCacheOperationSourcePointcut() {
        @Override
        protected FluxCacheOperationSource getFluxCacheOperationSource() {
            return fluxCacheOperationSource;
        }
    };

    @Override
    public Pointcut getPointcut() {
        return fluxCacheOperationSourcePointcut;
    }
}
