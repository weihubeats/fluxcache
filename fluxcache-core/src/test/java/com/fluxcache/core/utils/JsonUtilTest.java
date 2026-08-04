package com.fluxcache.core.utils;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * JSON 工具：序列化/反序列化/Map 转换与异常兜底。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class JsonUtilTest {

    @Test
    public void serialize2Json_simpleAndPretty() {
        assertEquals("{\"a\":1}", JsonUtil.serialize2Json(Map.of("a", 1)));
        String pretty = JsonUtil.serialize2Json(Map.of("a", 1), true);
        assertTrue(pretty.contains("\n"));
    }

    @Test
    public void serialize2Json_failingObject_throwsRuntime() {
        try {
            JsonUtil.serialize2Json(new BrokenBean());
            org.junit.Assert.fail("应抛出 RuntimeException");
        } catch (RuntimeException expected) {
            // serialization failure
        }
    }

    @Test
    public void deserialize_roundTrip() {
        assertEquals("hello", JsonUtil.deserialize("\"hello\"", String.class));
        assertEquals(42, JsonUtil.deserialize("42", Integer.class).intValue());
    }

    @Test
    public void deserialize_invalidJson_throwsRuntime() {
        try {
            JsonUtil.deserialize("{not-json", String.class);
            org.junit.Assert.fail("应抛出 RuntimeException");
        } catch (RuntimeException expected) {
            // parse failure
        }
    }

    @Test
    public void deserialize2Map_roundTrip() {
        Map<String, String> map = JsonUtil.deserialize2Map("{\"a\":1,\"b\":\"x\"}");
        assertEquals("1", map.get("a"));
        assertEquals("x", map.get("b"));
    }

    @Test
    public void deserialize2Map_blankOrNull_returnsEmpty() {
        assertTrue(JsonUtil.deserialize2Map("").isEmpty());
        assertTrue(JsonUtil.deserialize2Map(null).isEmpty());
    }

    @Test
    public void deserialize2Map_invalidJson_throwsRuntime() {
        try {
            JsonUtil.deserialize2Map("[1,2");
            org.junit.Assert.fail("应抛出 RuntimeException");
        } catch (RuntimeException expected) {
            // parse failure
        }
    }

    public static class BrokenBean {

        public Object getBroken() {
            throw new RuntimeException("cannot-serialize");
        }
    }
}