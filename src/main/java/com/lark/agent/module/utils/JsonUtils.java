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
 * 基于 Jackson 的通用 JSON 序列化和反序列化工具。
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
     * 使用外部配置的 mapper 替换静态 mapper。
     *
     * @param objectMapper 后续工具方法使用的 mapper。
     */
    public static void init(ObjectMapper objectMapper) {
        JsonUtils.objectMapper = objectMapper;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param object 待序列化对象。
     * @return JSON 字符串。
     */
    @SneakyThrows
    public static String toJsonString(Object object) {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * 将对象序列化为 UTF-8 JSON 字节数组。
     *
     * @param object 待序列化对象。
     * @return JSON 字节数组。
     */
    @SneakyThrows
    public static byte[] toJsonByte(Object object) {
        return objectMapper.writeValueAsBytes(object);
    }

    /**
     * 将对象序列化为格式化后的 JSON 字符串。
     *
     * @param object 待序列化对象。
     * @return 格式化后的 JSON 字符串。
     */
    @SneakyThrows
    public static String toJsonPrettyString(Object object) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    /**
     * 将 JSON 文本解析为目标类。
     *
     * @param text JSON 文本。
     * @param clazz 目标类。
     * @param <T> 目标类型。
     * @return 解析后的对象；文本为空时返回 null。
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
     * 将 JSON 中指定路径的节点解析为目标类。
     *
     * @param text JSON 文本。
     * @param path 从根节点读取的字段名。
     * @param clazz 目标类。
     * @param <T> 目标类型。
     * @return 解析后的嵌套对象；文本为空时返回 null。
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
     * 将 JSON 文本解析为泛型 Java 类型。
     *
     * @param text JSON 文本。
     * @param type 目标 Java 类型。
     * @param <T> 目标类型。
     * @return 解析后的对象；文本为空时返回 null。
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
     * 将 JSON 文本解析为目标类。
     *
     * @param text JSON 文本。
     * @param clazz 目标类。
     * @param <T> 目标类型。
     * @return 解析后的对象；文本为空时返回 null。
     */
    public static <T> T parseObject2(String text, Class<T> clazz) {
        if (!StringUtils.hasLength(text)) {
            return null;
        }
        return JsonUtils.parseObject(text, clazz);
    }

    /**
     * 使用 Jackson 类型引用解析 JSON 文本。
     *
     * @param text JSON 文本。
     * @param typeReference 目标类型引用。
     * @param <T> 目标类型。
     * @return 解析后的对象。
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
     * 使用类型引用解析 JSON 文本，并在解析失败时返回 null。
     *
     * @param text JSON 文本。
     * @param typeReference 目标类型引用。
     * @param <T> 目标类型。
     * @return 解析后的对象；解析失败时返回 null。
     */
    public static <T> T parseObjectQuietly(String text, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(text, typeReference);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 将 JSON 数组文本解析为指定类型列表。
     *
     * @param text JSON 数组文本。
     * @param clazz 元素类。
     * @param <T> 元素类型。
     * @return 解析后的列表；文本为空时返回空列表。
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
     * 将 JSON 中指定路径的数组节点解析为指定类型列表。
     *
     * @param text JSON 文本。
     * @param path 从根节点读取的字段名。
     * @param clazz 元素类。
     * @param <T> 元素类型。
     * @return 解析后的列表；文本为空时返回 null。
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
     * 将 JSON 文本解析为 Jackson 树节点。
     *
     * @param text JSON 文本。
     * @return 根 JSON 节点。
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
     * 将 JSON 字节数组解析为 Jackson 树节点。
     *
     * @param text JSON 字节数组。
     * @return 根 JSON 节点。
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
