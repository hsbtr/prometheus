package io.dataease.plugins.datasource.prometheus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JSON 对象 互转 工具类
 *
 * @author abao
 * @create 2018-01-03 19:16
 **/
public class JsonUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * 将对象转换成JSON格式字符串
     *
     * @param data POJO
     * @return string
     */
    public static String objectToJson(Object data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 将JSON格式字符串转对象
     *
     * @param jsonData JSON格式字符串
     * @param beanType POJO对象
     * @return POJO
     */
    public static <T> T jsonToPojo(String jsonData, Class<T> beanType) {
        try {
            return MAPPER.readValue(jsonData, beanType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 将json数据转换成pojo对象list
     *
     * @param jsonData JSON格式数据
     * @param beanType POJO
     */
    public static <T> List<T> jsonToList(String jsonData, Class<T> beanType) {
        JavaType javaType = MAPPER.getTypeFactory().constructParametricType(List.class, beanType);
        try {
            return MAPPER.readValue(jsonData, javaType);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 将json数据转换成pojo对象list
     *
     * @param jsonData JSON格式数据
     */
    public static <T> Map<Integer, String> jsonToMap(String jsonData) {
        try {
            Map<Integer, String> map = MAPPER.readValue(jsonData, Map.class);
            return map;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * 将json数据转换成pojo对象list
     *
     * @param jsonData JSON格式数据
     */
    public static <T> Map<String, String> jsonToMap2(String jsonData) {
        try {
            Map<String, String> map = MAPPER.readValue(jsonData, Map.class);
            return map;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 将json数据转换成pojo对象list
     *
     * @param jsonData JSON格式数据
     */
    public static <T> Map<String, Object> jsonToMap3(String jsonData) {
        try {
            Map<String, Object> map = MAPPER.readValue(jsonData, Map.class);
            return map;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * 将POJO对象转换成yaml格式字符串
     * @return
     */
    public static String pojoToYaml(Object data) {
        try {
            return YAML_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 将yaml格式数据写入文件
     *
     * @param filePath 文件生成路径
     * @param yamlData yaml格式数据
     */
    public static void createYamlFile(String filePath, String yamlData) {
        Yaml yaml = new Yaml();
        FileWriter writer;
        try {
            Map<String, Object> map = (Map<String, Object>) yaml.load(yamlData);
            writer = new FileWriter(filePath);
            yaml.dump(map, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 将yaml格式文件转成json格式字符串
     *
     * @param filePath
     * @return
     */
    public static String yamlFileToJson(String filePath) {
        Map<String, Object> map = null;
        try {
            FileInputStream fis = new FileInputStream(filePath);
            Yaml yaml = new Yaml();
            map = (Map<String, Object>) yaml.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectToJson(map);
    }

    /**
     * 将yaml格式字符串转成json格式字符串
     *
     * @param filePath
     * @return
     */
    public static String yamlToJson(String filePath) {
        Map<String, Object> map = null;
        try {
            Yaml yaml = new Yaml();
            map = (Map<String, Object>) yaml.load(filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectToJson(map);
    }

    /**
     * 将yaml格式字符串转成map
     *
     * @param filePath
     * @return
     */
    public static Map<String, String> yamlToMap(String filePath) {
        Map<String, String> map = null;
        try {
            FileInputStream fis = new FileInputStream(filePath);
            Yaml yaml = new Yaml();
            map = (Map<String, String>) yaml.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }


}
