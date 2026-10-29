package com.example.aichinese;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(proxyBeanMethods = false)
@ConfigurationPropertiesScan
public class AichineseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AichineseApplication.class, args);
    }

}
