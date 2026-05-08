package com.example.aichinese.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.service.PublicTeacherProfileService;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final PublicTeacherProfileService publicTeacherProfileService;

    public PublicController(PublicTeacherProfileService publicTeacherProfileService) {
        this.publicTeacherProfileService = publicTeacherProfileService;
    }

    @GetMapping("/teachers/{id}")
    public ApiResponse<PublicTeacherProfileResponse> getApprovedTeacherProfile(@PathVariable Long id) {
        return ApiResponse.success(
                "Approved teacher profile retrieved successfully.",
                publicTeacherProfileService.getApprovedTeacherProfile(id));
    }
}
