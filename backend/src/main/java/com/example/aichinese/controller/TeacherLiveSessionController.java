package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.ClassLiveSessionService;

import jakarta.validation.Valid;

/** The teacher's live sessions: Upcoming and Past, schedule, edit, cancel, details with bookings and recordings. */
@RestController
@RequestMapping("/api/v1/teachers/live-sessions")
public class TeacherLiveSessionController {

    private final ClassLiveSessionService liveSessions;

    public TeacherLiveSessionController(ClassLiveSessionService liveSessions) {
        this.liveSessions = liveSessions;
    }

    @GetMapping
    public ApiResponse<LiveSessionViews.TeacherSessions> sessions(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Live sessions retrieved successfully.", liveSessions.teacherSessions(currentUser));
    }

    @GetMapping("/options")
    public ApiResponse<LiveSessionViews.ScheduleOptions> options(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Schedule options retrieved successfully.", liveSessions.scheduleOptions(currentUser));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<LiveSessionViews.TeacherSessionDetail> session(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success("Live session retrieved successfully.", liveSessions.teacherSession(currentUser, sessionId));
    }

    @PostMapping
    public ApiResponse<LiveSessionViews.TeacherSession> schedule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody ClassRequests.LiveSession request) {
        return ApiResponse.success("Live session scheduled.", liveSessions.schedule(currentUser, request));
    }

    @PutMapping("/{sessionId}")
    public ApiResponse<LiveSessionViews.TeacherSession> update(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody ClassRequests.LiveSession request) {
        return ApiResponse.success("Live session updated.", liveSessions.update(currentUser, sessionId, request));
    }

    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<LiveSessionViews.TeacherSession> cancel(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success("Live session cancelled.", liveSessions.cancel(currentUser, sessionId));
    }

    @PutMapping("/{sessionId}/recording")
    public ApiResponse<LiveSessionViews.TeacherSession> saveRecording(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody ClassRequests.Recording request) {
        return ApiResponse.success("Recording link saved.", liveSessions.saveRecording(currentUser, sessionId, request));
    }
}
