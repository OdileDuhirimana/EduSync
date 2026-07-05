package com.edusync.grading;

import com.edusync.common.error.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@OpenAPIDefinition(info = @Info(
        title = "EduSync Grading Service",
        version = "0.1.0",
        description = "Auto/manual grading, gradebook, and the regrade moderation workflow."))
@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class GradingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GradingServiceApplication.class, args);
    }
}
