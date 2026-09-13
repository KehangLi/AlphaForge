package com.likehang.alphaforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AlphaForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlphaForgeApplication.class, args);
    }

}

//基础启动 ./mvnw spring-boot:run
//根据不同配置启动  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local