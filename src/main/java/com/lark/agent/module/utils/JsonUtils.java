package com.lark.agent.module.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared JSON serialization and deserialization utilities backed by Jackson.
 */
@Slf4j
public class JsonUtils {

    @Getter
    private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Replaces the static mapper with an externally configured mapper.
     *
     * @param objectMapper mapper to use for subsequent utility calls.
     */
    public static void init(ObjectMapper objectMapper) {
        JsonUtils.objectMapper = objectMapper;
    }

    /**
     * Serializes an object to JSON.
     *
     * @param object object to serialize.
     * @return JSON string.
     */
    @SneakyThrows
    public static String toJsonString(Object object) {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * Serializes an object to UTF-8 JSON bytes.
     *
     * @param object object to serialize.
     * @return JSON byte array.
     */
    @SneakyThrows
    public static byte[] toJsonByte(Object object) {
        return objectMapper.writeValueAsBytes(object);
    }

    /**
     * Serializes an object to pretty-printed JSON.
     *
     * @param object object to serialize.
     * @return formatted JSON string.
     */
    @SneakyThrows
    public static String toJsonPrettyString(Object object) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    /**
     * Parses JSON text into a target class.
     *
     * @param text JSON text.
     * @param clazz target class.
     * @param <T> target type.
     * @return parsed object, or null when text is empty.
     */
    public static <T> T parseObject(String text, Class<T> clazz) {
        if (!StringUtils.hasLength(text)) {
            return null;
        }
        try {
            return objectMapper.readValue(text, clazz);
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses a nested JSON path into a target class.
     *
     * @param text JSON text.
     * @param path field name to read from the root node.
     * @param clazz target class.
     * @param <T> target type.
     * @return parsed nested object, or null when text is empty.
     */
    public static <T> T parseObject(String text, String path, Class<T> clazz) {
        if (!StringUtils.hasLength(text)) {
            return null;
        }
        try {
            JsonNode treeNode = objectMapper.readTree(text);
            JsonNode pathNode = treeNode.path(path);
            return objectMapper.readValue(pathNode.toString(), clazz);
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses JSON text into a generic Java type.
     *
     * @param text JSON text.
     * @param type target Java type.
     * @param <T> target type.
     * @return parsed object, or null when text is empty.
     */
    public static <T> T parseObject(String text, Type type) {
        if (!StringUtils.hasLength(text)) {
            return null;
        }
        try {
            return objectMapper.readValue(text, objectMapper.getTypeFactory().constructType(type));
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses JSON text into a target class.
     *
     * @param text JSON text.
     * @param clazz target class.
     * @param <T> target type.
     * @return parsed object, or null when text is empty.
     */
    public static <T> T parseObject2(String text, Class<T> clazz) {
        if (!StringUtils.hasLength(text)) {
            return null;
        }
        return JsonUtils.parseObject(text, clazz);
    }

    /**
     * Parses JSON text using a Jackson type reference.
     *
     * @param text JSON text.
     * @param typeReference target type reference.
     * @param <T> target type.
     * @return parsed object.
     */
    public static <T> T parseObject(String text, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(text, typeReference);
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses JSON text using a type reference and returns null when parsing fails.
     *
     * @param text JSON text.
     * @param typeReference target type reference.
     * @param <T> target type.
     * @return parsed object, or null on parse failure.
     */
    public static <T> T parseObjectQuietly(String text, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(text, typeReference);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Parses JSON array text into a typed list.
     *
     * @param text JSON array text.
     * @param clazz element class.
     * @param <T> element type.
     * @return parsed list, or an empty list when text is empty.
     */
    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        if (!StringUtils.hasLength(text)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(text, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses a nested JSON array into a typed list.
     *
     * @param text JSON text.
     * @param path field name to read from the root node.
     * @param clazz element class.
     * @param <T> element type.
     * @return parsed list, or null when text is empty.
     */
    public static <T> List<T> parseArray(String text, String path, Class<T> clazz) {
        if (!StringUtils.hasLength(text)) {
            return null;
        }
        try {
            JsonNode treeNode = objectMapper.readTree(text);
            JsonNode pathNode = treeNode.path(path);
            return objectMapper.readValue(pathNode.toString(), objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses JSON text into a Jackson tree.
     *
     * @param text JSON text.
     * @return root JSON node.
     */
    public static JsonNode parseTree(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses JSON bytes into a Jackson tree.
     *
     * @param text JSON bytes.
     * @return root JSON node.
     */
    public static JsonNode parseTree(byte[] text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            log.error("json parse err,json:{}", text, e);
            throw new RuntimeException(e);
        }
    }
}
