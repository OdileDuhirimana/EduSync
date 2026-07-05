package com.edusync.analytics;

import com.edusync.common.error.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@OpenAPIDefinition(info = @Info(
        title = "EduSync Analytics Service",
        version = "0.1.0",
        description = "Study-plan generation, at-risk learner scoring, and grade forecasting."))
@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
