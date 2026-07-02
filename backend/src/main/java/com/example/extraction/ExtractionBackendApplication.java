package com.example.extraction;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.example.extraction.mapper")
@SpringBootApplication
public class ExtractionBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtractionBackendApplication.class, args);
    }
}
