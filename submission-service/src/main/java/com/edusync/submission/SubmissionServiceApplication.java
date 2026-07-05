package com.edusync.submission;

import com.edusync.common.error.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@OpenAPIDefinition(info = @Info(
        title = "EduSync Submission Service",
        version = "0.1.0",
        description = "Student submission intake and Jaccard-similarity plagiarism detection."))
@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class SubmissionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionServiceApplication.class, args);
    }
}
