package me.git9527.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class EnvUtil {

    public static String getEnvValue(EnvKeys key) {
        String val = getEnvValue(key, "");
        if (StringUtils.isBlank(val)) {
            throw new IllegalArgumentException("Didn't found val for key: " + key);
        }
        return val;
    }

    public static String getEnvValue(EnvKeys key, String defaultValue) {
        String val = System.getenv(key.name());
        if (StringUtils.isBlank(val)) {
            val = defaultValue;
        }
        logger.info("Get value: {} for key: {}", val, key);
        return val;
    }
}
