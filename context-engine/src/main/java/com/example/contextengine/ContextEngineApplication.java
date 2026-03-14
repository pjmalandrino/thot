package com.example.contextengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ContextEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextEngineApplication.class, args);
    }
}
