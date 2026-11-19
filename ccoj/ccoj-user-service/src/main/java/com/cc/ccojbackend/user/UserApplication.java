package com.cc.ccojbackend.user;

import com.cc.ccojbackend.common.exception.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("com.cc.ccojbackend.user.mapper")
@Import({GlobalExceptionHandler.class, com.cc.ccojbackend.common.auth.WebAccessConfig.class})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
