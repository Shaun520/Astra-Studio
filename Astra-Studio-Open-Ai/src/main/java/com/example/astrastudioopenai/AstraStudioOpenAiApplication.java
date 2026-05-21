package com.example.astrastudioopenai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class AstraStudioOpenAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstraStudioOpenAiApplication.class, args);
    }

}
