package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.ClassStudentViews;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.ClassFeedbackService;
import com.example.aichinese.service.classes.TeacherClassStudentsService;

import jakarta.validation.Valid;

/** The teacher's students (roster and detail) and the feedback the teacher writes to them. */
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherStudentController {

    private final TeacherClassStudentsService students;
    private final ClassFeedbackService feedback;

    public TeacherStudentController(TeacherClassStudentsService students, ClassFeedbackService feedback) {
        this.students = students;
        this.feedback = feedback;
    }

    @GetMapping("/students")
    public ApiResponse<ClassStudentViews.Roster> roster(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "filter", required = false) String filter) {
        return ApiResponse.success("Students retrieved successfully.", students.roster(currentUser, search, filter));
    }

    @GetMapping("/students/{studentId}")
    public ApiResponse<ClassStudentViews.StudentDetail> student(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long studentId,
            @RequestParam(value = "classId", required = false) Long classId) {
        return ApiResponse.success("Student retrieved successfully.", students.detail(currentUser, studentId, classId));
    }

    @GetMapping("/feedback")
    public ApiResponse<List<ClassStudentViews.FeedbackRow>> history(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.success("Feedback retrieved successfully.", feedback.history(currentUser, studentId, type));
    }

    @PostMapping("/feedback")
    public ApiResponse<ClassStudentViews.FeedbackRow> send(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody ClassRequests.Feedback request) {
        return ApiResponse.success("Feedback sent.", feedback.create(currentUser, request));
    }

    @PutMapping("/feedback/{feedbackId}")
    public ApiResponse<ClassStudentViews.FeedbackRow> update(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long feedbackId,
            @Valid @RequestBody ClassRequests.FeedbackUpdate request) {
        return ApiResponse.success("Feedback updated.", feedback.update(currentUser, feedbackId, request));
    }
}
