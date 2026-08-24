package com.fluxcache.redis.spring.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * 反序列化白名单校验器回归：
 * - 包前缀必须按边界匹配（"com.foo" 不得放行 "comfoobar.*"）
 * - 框架 pub/sub DTO 必须始终放行，否则失效消息被静默丢弃
 *
 * @author : wh
 */
public class AllowlistSubTypeValidatorTest {

    private static final JavaType OBJECT_TYPE =
            TypeFactory.defaultInstance().constructType(Object.class);

    private AllowlistSubTypeValidator validator;

    @Before
    public void setUp() {
        validator = new AllowlistSubTypeValidator(Arrays.asList(
                "java.lang.", "java.util.", "com.example.cache"));
    }

    private AllowlistSubTypeValidator.Validity validate(String subClassName) {
        return validator.validateSubClassName(null, OBJECT_TYPE, subClassName);
    }

    @Test
    public void allowedPrefix_matchesOwnPackage() {
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED,
                validate("com.example.cache.UserVO"));
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED,
                validate("java.lang.String"));
    }

    @Test
    public void prefixMatch_isBoundarySafe() {
        // 回归：startsWith("com.foo") 曾放行 comfoobar.*
        assertEquals(AllowlistSubTypeValidator.Validity.DENIED,
                validate("comexamplecache.UserVO"));
        assertEquals(AllowlistSubTypeValidator.Validity.DENIED,
                validate("com.example.cacheevil.Gadget"));
        // 精确类名本身放行
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED,
                validate("com.example.cache"));
    }

    @Test
    public void frameworkDtos_alwaysAllowed() {
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED,
                validate("com.fluxcache.core.model.PutCacheDTO"));
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED,
                validate("com.fluxcache.core.model.DeleteCacheDTO"));
    }

    @Test
    public void unknownTypes_denied() {
        assertEquals(AllowlistSubTypeValidator.Validity.DENIED,
                validate("com.evil.Payload"));
        assertEquals(AllowlistSubTypeValidator.Validity.DENIED,
                validate("org.apache.commons.io.FileUtils"));
    }

    @Test
    public void primitives_allowed() {
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED, validate("int"));
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED, validate("boolean"));
        assertEquals(AllowlistSubTypeValidator.Validity.ALLOWED, validate(null));
    }

    @Test
    public void wildcardPrefix_disablesProtection() {
        AllowlistSubTypeValidator open =
                new AllowlistSubTypeValidator(Collections.singletonList("*"));
        // "*" 不在白名单前缀语义内：仍按边界匹配，不意外放行（防护关闭走 activateDefaultTyping 分支）
        assertEquals(AllowlistSubTypeValidator.Validity.DENIED,
                open.validateSubClassName(null, OBJECT_TYPE, "com.evil.Payload"));
    }
}
