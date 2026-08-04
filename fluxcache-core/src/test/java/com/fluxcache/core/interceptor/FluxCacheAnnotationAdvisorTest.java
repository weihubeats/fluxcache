package com.fluxcache.core.interceptor;

import com.fluxcache.core.annotation.FluxCacheable;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 注解切面：方法匹配、代理类跳过、接口实现解析、BeanFactory 传播。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheAnnotationAdvisorTest {

    private MethodInterceptor interceptor;
    private FluxCacheAnnotationAdvisor advisor;
    private MethodMatcher matcher;

    @Before
    public void setUp() {
        interceptor = mock(MethodInterceptor.class);
        advisor = new FluxCacheAnnotationAdvisor(interceptor, FluxCacheable.class);
        matcher = advisor.getPointcut().getMethodMatcher();
    }

    @Test
    public void constructor_andGetters() {
        Pointcut pc = advisor.getPointcut();
        Advice advice = advisor.getAdvice();
        assertSame(interceptor, advice);
        assertTrue(pc.getClassFilter().matches(Object.class));
    }

    @Test
    public void matchesAnnotatedMethod() throws Exception {
        Method m = AnnotatedService.class.getMethod("load", String.class);
        assertTrue(matcher.matches(m, AnnotatedService.class));
    }

    @Test
    public void doesNotMatchPlainMethod() throws Exception {
        Method m = AnnotatedService.class.getMethod("plain");
        assertFalse(matcher.matches(m, AnnotatedService.class));
    }

    @Test
    public void proxyClass_withoutAnnotationSkipped() throws Exception {
        Method iface = PlainInterface.class.getMethod("get", String.class);
        Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlainInterface.class},
                (p, method, args) -> null);
        assertFalse(matcher.matches(iface, proxy.getClass()));
    }

    @Test
    public void interfaceMethod_resolvesMostSpecificOnImpl() throws Exception {
        Method iface = AnnotatedInterface.class.getMethod("get", String.class);
        assertTrue(matcher.matches(iface, AnnotatedImpl.class));
    }

    @Test
    public void setBeanFactory_propagatesToAwareAdvice() {
        BeanFactoryAwareInterceptor aware = new BeanFactoryAwareInterceptor();
        FluxCacheAnnotationAdvisor a = new FluxCacheAnnotationAdvisor(aware, FluxCacheable.class);
        BeanFactory beanFactory = mock(BeanFactory.class);

        a.setBeanFactory(beanFactory);

        assertSame(beanFactory, aware.received);
    }

    @Test
    public void setBeanFactory_ignoresNonAwareAdvice() {
        advisor.setBeanFactory(mock(BeanFactory.class));
    }

    public interface PlainInterface {

        String get(String key);
    }

    public interface AnnotatedInterface {

        String get(String key);
    }

    public static class AnnotatedImpl implements AnnotatedInterface {

        @FluxCacheable(cacheName = "annotated-impl")
        @Override
        public String get(String key) {
            return key;
        }
    }

    public static class AnnotatedService {

        @FluxCacheable(cacheName = "svc-cache")
        public String load(String key) {
            return key;
        }

        public String plain() {
            return "x";
        }
    }

    private static class BeanFactoryAwareInterceptor implements MethodInterceptor, BeanFactoryAware {

        BeanFactory received;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.received = beanFactory;
        }

        @Override
        public Object invoke(org.aopalliance.intercept.MethodInvocation invocation) throws Throwable {
            return invocation.proceed();
        }
    }
}