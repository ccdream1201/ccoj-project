package com.cc.ccojbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.cc.ccojbackend.mapper")
public class CcojBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CcojBackendApplication.class, args);
    }

}
