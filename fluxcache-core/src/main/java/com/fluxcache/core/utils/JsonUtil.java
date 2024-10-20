package com.fluxcache.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * @author : wh
 * @date : 2024/10/20 20:50
 * @description:
 */
@Slf4j
public class JsonUtil {

    /**
     * Object Mapper.
     */
    public static final ObjectMapper OM;

    static {
        OM = new ObjectMapper();
        OM.registerModule(new JavaTimeModule());
        OM.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    private JsonUtil() {
    }

    /**
     * Object to Json.
     *
     * @param object object to be serialized
     * @param <T>    type of object
     * @return Json String
     */
    public static <T> String serialize2Json(T object) {
        return serialize2Json(object, false);
    }

    /**
     * Object to Json.
     *
     * @param object object to be serialized
     * @param pretty pretty print
     * @param <T>    type of object
     * @return Json String
     */
    public static <T> String serialize2Json(T object, boolean pretty) {
        try {
            if (pretty) {
                ObjectWriter objectWriter = OM.writerWithDefaultPrettyPrinter();
                return objectWriter.writeValueAsString(object);
            } else {
                return OM.writeValueAsString(object);
            }
        } catch (JsonProcessingException e) {
            log.error("Object to Json failed. {}", object, e);
            throw new RuntimeException("Object to Json failed.", e);
        }
    }

    public static <T> T deserialize(String jsonStr, Class<T> type) {
        try {
            return OM.readValue(jsonStr, type);
        } catch (JsonProcessingException e) {
            log.error("Json to object failed. {}", type, e);
            throw new RuntimeException("Json to object failed.", e);
        }
    }

    /**
     * Json to Map.
     *
     * @param jsonStr Json String
     * @return Map
     */
    public static Map<String, String> deserialize2Map(String jsonStr) {
        try {
            if (StringUtils.hasText(jsonStr)) {
                Map<String, Object> temp = OM.readValue(jsonStr, Map.class);
                Map<String, String> result = new HashMap<>();
                temp.forEach((key, value) -> {
                    result.put(String.valueOf(key), String.valueOf(value));
                });
                return result;
            }
            return new HashMap<>();
        } catch (JsonProcessingException e) {
            log.error(
                "Json to map failed. check if the format of the json string[{}] is correct.", jsonStr, e);
            throw new RuntimeException("Json to map failed.", e);
        }
    }
}
