package com.gcd.coding.gcdgatewayconfig.loader;

import com.gcd.coding.gcdgatewayconfig.config.Config;
import com.gcd.coding.gcdgatewayconfig.util.ConfigUtil;

import static com.gcd.coding.gcdgatewaycommon.constant.ConfigConstant.CONFIG_PATH;
import static com.gcd.coding.gcdgatewaycommon.constant.ConfigConstant.CONFIG_PREFIX;

/**
 * 配置加载
 */
public class ConfigLoader {

    public static Config load(String[] args) {
        return ConfigUtil.loadConfigFromYaml(CONFIG_PATH, Config.class, CONFIG_PREFIX);
    }

}
