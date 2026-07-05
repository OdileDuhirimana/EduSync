package com.edusync.course;

import com.edusync.common.error.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// WHY @Import(GlobalExceptionHandler.class): guarantees the shared
// @RestControllerAdvice from the `common` module is registered regardless of
// component-scan base-package heuristics, so every service gets identical
// error-response behavior deterministically rather than "usually works
// because packages happen to nest under com.edusync".
@OpenAPIDefinition(info = @Info(
        title = "EduSync Course Service",
        version = "0.1.0",
        description = "Course catalog: creation, publishing, and paginated listing."))
@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class CourseServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}
