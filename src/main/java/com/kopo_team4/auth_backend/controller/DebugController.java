package com.kopo_team4.auth_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
public class DebugController {

    @Value("${spring.datasource.url:NOT_SET}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:NOT_SET}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:NOT_SET}")
    private String datasourcePassword;

    @Value("${MYSQL_HOST:NOT_SET}")
    private String mysqlHost;

    @Value("${MYSQL_PORT:NOT_SET}")
    private String mysqlPort;

    @Value("${MYSQL_DATABASE:NOT_SET}")
    private String mysqlDatabase;

    @Value("${MYSQL_USER:NOT_SET}")
    private String mysqlUser;

    @Value("${MYSQL_PASSWORD:NOT_SET}")
    private String mysqlPassword;

    @Value("${SPRING_PROFILES_ACTIVE:NOT_SET}")
    private String activeProfile;

    @GetMapping("/env")
    public Map<String, String> getEnvironmentVariables() {
        Map<String, String> env = new HashMap<>();
        env.put("datasourceUrl", datasourceUrl);
        env.put("datasourceUsername", datasourceUsername);
        env.put("datasourcePassword", "***HIDDEN***");
        env.put("mysqlHost", mysqlHost);
        env.put("mysqlPort", mysqlPort);
        env.put("mysqlDatabase", mysqlDatabase);
        env.put("mysqlUser", mysqlUser);
        env.put("mysqlPassword", "***HIDDEN***");
        env.put("activeProfile", activeProfile);
        return env;
    }
}
