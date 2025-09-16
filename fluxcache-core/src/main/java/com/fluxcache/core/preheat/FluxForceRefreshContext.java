package com.fluxcache.core.preheat;

/**
 * @author : wh
 * @date : 2025/8/12
 * @description:
 */
public class FluxForceRefreshContext {

    private static final ThreadLocal<Boolean> FORCE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FluxForceRefreshContext() {
    }

    public static void enable() {
        FORCE.set(Boolean.TRUE);
    }

    public static void disable() {
        FORCE.remove();
    }

    public static boolean isForceRefresh() {
        return FORCE.get();
    }

    /**
     * 包装一个 Runnable 在强制刷新模式下执行。
     */
    public static void runWithForce(Runnable runnable) {
        enable();
        try {
            runnable.run();
        } finally {
            disable();
        }
    }

    /**
     * 包装一个返回值逻辑在强制刷新模式下执行。
     */
    public static <T> T callWithForce(java.util.concurrent.Callable<T> callable) {
        enable();
        try {
            return callable.call();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            disable();
        }
    }

}
