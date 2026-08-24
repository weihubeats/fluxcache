package com.fluxcache.redis.spring.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Redis 值反序列化类型白名单校验器。
 * 在子类名解析阶段（类加载前）拦截未白名单的类型，
 * 阻断 Jackson default-typing 反序列化 gadget 攻击链。
 *
 * @author : wh
 * @date : 2026/7/31
 */
public class AllowlistSubTypeValidator extends PolymorphicTypeValidator.Base implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> PRIMITIVE_NAMES =
            Arrays.asList("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

    /**
     * framework pub/sub DTOs must always deserialize, otherwise invalidation messages are dropped
     */
    private static final String FRAMEWORK_DTO_PREFIX = "com.fluxcache.core.model.";

    private final List<String> allowedPrefixes;

    public AllowlistSubTypeValidator(List<String> allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes;
    }

    @Override
    public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) {
        if (subClassName == null || PRIMITIVE_NAMES.contains(subClassName)) {
            return Validity.ALLOWED;
        }
        if (isAllowed(subClassName)) {
            return Validity.ALLOWED;
        }
        return Validity.DENIED;
    }

    @Override
    public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) {
        return validateSubClassName(config, baseType, subType.getRawClass().getName());
    }

    private boolean isAllowed(String subClassName) {
        if (subClassName.startsWith(FRAMEWORK_DTO_PREFIX)) {
            return true;
        }
        for (String prefix : allowedPrefixes) {
            if (prefix.endsWith(".")) {
                if (subClassName.startsWith(prefix)) {
                    return true;
                }
            } else {
                // package boundary: "com.foo" must not admit "comfoobar.Gadget"
                if (subClassName.equals(prefix) || subClassName.startsWith(prefix + ".")) {
                    return true;
                }
            }
        }
        return false;
    }
}
