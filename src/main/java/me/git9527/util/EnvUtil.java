package me.git9527.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class EnvUtil {

    private static Map<String, String> map = new HashMap<>();

    public static String getEnvValue(EnvKeys key) {
        String val = getEnvValue(key, "");
        if (StringUtils.isBlank(val)) {
            throw new IllegalArgumentException("Didn't found val for key: " + key);
        }
        return val;
    }

    public static String getEnvValue(EnvKeys envKey, String defaultValue) {
        String key = envKey.name();
        if (map.containsKey(key)) {
            return map.get(key);
        }
        String val = System.getenv(key);
        if (StringUtils.isBlank(val)) {
            val = defaultValue;
        }
        logger.info("Get value: {} for key: {}", val, key);
        map.put(key, val);
        return val;
    }
}
