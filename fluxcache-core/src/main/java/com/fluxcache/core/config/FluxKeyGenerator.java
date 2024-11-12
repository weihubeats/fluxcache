package com.fluxcache.core.config;

import java.lang.reflect.Method;

/**
 * @author : wh
 * @date : 2024/11/12 12:35
 * @description:
 */
@FunctionalInterface
public interface FluxKeyGenerator {

    Object generate(Object target, Method method, Object... params);

}
