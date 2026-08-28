package com.mage.onvifcms;

import com.mage.onvifcms.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class OnvifCmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnvifCmsApplication.class, args);
        System.err.println("Application Started!!");
    }
}

