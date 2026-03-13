package com.example.chatinterface;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.chatinterface.context.ContextProperties;

@SpringBootApplication
@EnableConfigurationProperties(ContextProperties.class)
public class ChatInterfaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatInterfaceApplication.class, args);
    }
}
