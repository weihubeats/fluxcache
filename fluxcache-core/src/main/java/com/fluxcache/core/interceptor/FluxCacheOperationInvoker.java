package com.fluxcache.core.interceptor;

import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/11/12 12:41
 * @description:
 */
@FunctionalInterface
public interface FluxCacheOperationInvoker {

    @Nullable
    Object invoke() throws ThrowableWrapper;

    class ThrowableWrapper extends RuntimeException {

        private final Throwable original;

        public ThrowableWrapper(Throwable original) {
            super(original.getMessage(), original);
            this.original = original;
        }

        public Throwable getOriginal() {
            return this.original;
        }
    }
}
