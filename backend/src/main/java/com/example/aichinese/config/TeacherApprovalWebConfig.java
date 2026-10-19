package com.example.aichinese.config;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the central teacher-approval guard for the teacher API. */
@Configuration
public class TeacherApprovalWebConfig implements WebMvcConfigurer {

    private final ApprovedTeacherInterceptor approvedTeacherInterceptor;

    public TeacherApprovalWebConfig(ApprovedTeacherInterceptor approvedTeacherInterceptor) {
        this.approvedTeacherInterceptor = Objects.requireNonNull(approvedTeacherInterceptor, "approvedTeacherInterceptor");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(approvedTeacherInterceptor)
                .addPathPatterns(ApprovedTeacherInterceptor.TEACHER_API_PATTERN);
    }
}