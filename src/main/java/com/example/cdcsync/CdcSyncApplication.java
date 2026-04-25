package com.example.cdcsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.example.cdcsync.config")
public class CdcSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdcSyncApplication.class, args);
    }
}
