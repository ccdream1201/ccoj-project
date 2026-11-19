package com.cc.ccojbackend.problem;

import com.cc.ccojbackend.common.auth.WebAccessConfig;
import com.cc.ccojbackend.common.exception.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.cc.ccojbackend.problem.mapper")
@Import({WebAccessConfig.class, GlobalExceptionHandler.class})
public class ProblemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProblemApplication.class, args);
    }
}
