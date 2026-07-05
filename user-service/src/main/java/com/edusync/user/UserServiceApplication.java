package com.edusync.user;

import com.edusync.common.error.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@OpenAPIDefinition(info = @Info(
        title = "EduSync User Service",
        version = "0.1.0",
        description = "Authenticated caller profile access (X-User-Id derived from a verified gateway JWT)."))
@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
