package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.learn.LevelCheckNextRequest;
import com.example.aichinese.dto.learn.LevelCheckNextResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.LevelCheckService;

/** The adaptive level check (stateless: every call sends the answers so far). */
@RestController
@RequestMapping("/api/v1/students/level-check")
public class StudentLevelCheckController {

    private final LevelCheckService levelCheckService;
    private final StudentLearningAccessService accessService;

    public StudentLevelCheckController(LevelCheckService levelCheckService, StudentLearningAccessService accessService) {
        this.levelCheckService = levelCheckService;
        this.accessService = accessService;
    }

    @PostMapping("/next")
    public ApiResponse<LevelCheckNextResponse> next(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody LevelCheckNextRequest request) {
        accessService.requireStudent(currentUser);
        return ApiResponse.success("Level check step retrieved successfully.", levelCheckService.next(request));
    }
}
