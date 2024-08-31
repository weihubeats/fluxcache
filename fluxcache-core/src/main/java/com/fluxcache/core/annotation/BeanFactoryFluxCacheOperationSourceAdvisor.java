package com.fluxcache.core.annotation;

import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;

/**
 * @author : wh
 * @date : 2024/8/31 15:02
 * @description:
 */
public class BeanFactoryFluxCacheOperationSourceAdvisor extends AbstractBeanFactoryPointcutAdvisor {
    @Override
    public Pointcut getPointcut() {
        return null;
    }
}
