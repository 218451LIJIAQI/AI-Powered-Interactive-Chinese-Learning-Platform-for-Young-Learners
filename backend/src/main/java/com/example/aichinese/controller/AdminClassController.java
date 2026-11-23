package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.AdminClassViews;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.AdminClassList;
import com.example.aichinese.service.classes.ClassAuthoringService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

/**
 * Admin › Content › Classes: the classes and lessons list, the class page (details, units and lessons), publishing,
 * archiving, restoring and deleting a draft class. Every admin edits every class; there is no review step.
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminClassController {

    private final ClassAuthoringService classes;
    private final AdminClassList classList;

    @GetMapping("/classes")
    public ApiResponse<PageResponse<AdminClassViews.ContentRow>> list(
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 40, message = "Type filter must not exceed 40 characters.")
            @RequestParam(value = "type", required = false) String type,
            @Size(max = 40, message = "Status filter must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success("Classes retrieved successfully.", classList.list(keyword, type, status, page, size));
    }

    @PostMapping("/classes")
    public ApiResponse<AdminClassViews.ClassPage> createClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody ClassRequests.ClassDetails request) {
        return ApiResponse.success("Class created as a draft.", classes.createClass(currentUser, request));
    }

    @GetMapping("/classes/{classId}")
    public ApiResponse<AdminClassViews.ClassPage> getClass(@PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", classes.classPage(classId));
    }

    @PutMapping("/classes/{classId}")
    public ApiResponse<AdminClassViews.ClassPage> saveDetails(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassRequests.ClassDetails request) {
        return ApiResponse.success("Class details saved.", classes.saveDetails(currentUser, classId, request));
    }

    @PostMapping("/classes/{classId}/publish")
    public ApiResponse<AdminClassViews.ClassPage> publish(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class published.", classes.publish(currentUser, classId));
    }

    @PostMapping("/classes/{classId}/archive")
    public ApiResponse<AdminClassViews.ClassPage> archive(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class archived.", classes.archive(currentUser, classId));
    }

    @PostMapping("/classes/{classId}/restore")
    public ApiResponse<AdminClassViews.ClassPage> restore(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class restored.", classes.restore(currentUser, classId));
    }

    @DeleteMapping("/classes/{classId}")
    public ApiResponse<Void> deleteClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        classes.deleteClass(currentUser, classId);
        return ApiResponse.success("Draft class deleted.", null);
    }

    /** A class has 2 units: a third is refused (409). */
    @PostMapping("/classes/{classId}/units")
    public ApiResponse<AdminClassViews.ClassPage> addUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassRequests.UnitTitle request) {
        return ApiResponse.success("Unit added.", classes.addUnit(currentUser, classId, request));
    }

    @PutMapping("/units/{unitId}")
    public ApiResponse<AdminClassViews.ClassPage> renameUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId,
            @Valid @RequestBody ClassRequests.UnitTitle request) {
        return ApiResponse.success("Unit renamed.", classes.renameUnit(currentUser, unitId, request));
    }

    /** Adds a draft lesson to the unit and answers with its editor. */
    @PostMapping("/units/{unitId}/lessons")
    public ApiResponse<AdminClassViews.LessonEditor> addLesson(
            @PathVariable Long unitId,
            @Valid @RequestBody ClassRequests.LessonTitle request) {
        return ApiResponse.success("Lesson created as a draft.", classes.addLesson(unitId, request));
    }
}
