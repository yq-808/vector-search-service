package com.example.vectorsearch;

import com.example.vectorsearch.config.VectorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(VectorProperties.class)
public class VectorSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(VectorSearchApplication.class, args);
    }
}
