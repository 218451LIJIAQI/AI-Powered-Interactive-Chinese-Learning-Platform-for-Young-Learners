package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.ClassStudentViews;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.TeacherClassService;
import com.example.aichinese.service.classes.TeacherLessonEditorService;
import com.example.aichinese.service.classes.TeachingDashboardService;

import jakarta.validation.Valid;

/** The approved teacher's classes: list, class page (details, units and lessons, history) and the teaching dashboard. */
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherClassController {

    private final TeacherClassService classes;
    private final TeacherLessonEditorService lessons;
    private final TeachingDashboardService dashboard;

    public TeacherClassController(
            TeacherClassService classes,
            TeacherLessonEditorService lessons,
            TeachingDashboardService dashboard) {
        this.classes = classes;
        this.lessons = lessons;
        this.dashboard = dashboard;
    }

    @GetMapping("/teaching-dashboard")
    public ApiResponse<ClassStudentViews.Dashboard> teachingDashboard(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Teaching dashboard retrieved successfully.", dashboard.dashboard(currentUser));
    }

    @GetMapping("/classes")
    public ApiResponse<List<TeacherClassViews.ClassListItem>> listClasses(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Classes retrieved successfully.", classes.listClasses(currentUser));
    }

    @PostMapping("/classes")
    public ApiResponse<TeacherClassViews.ClassPage> createClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody ClassRequests.ClassDetails request) {
        return ApiResponse.success("Class created as a draft.", classes.createClass(currentUser, request));
    }

    @GetMapping("/classes/{classId}")
    public ApiResponse<TeacherClassViews.ClassPage> getClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", classes.getClass(currentUser, classId));
    }

    @PutMapping("/classes/{classId}")
    public ApiResponse<TeacherClassViews.ClassPage> saveDetails(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassRequests.ClassDetails request) {
        return ApiResponse.success("Class details saved.", classes.saveDetails(currentUser, classId, request));
    }

    @PostMapping("/classes/{classId}/submit")
    public ApiResponse<TeacherClassViews.ClassPage> submitDetails(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Details submitted for review.", classes.submitDetails(currentUser, classId));
    }

    @DeleteMapping("/classes/{classId}/pending-details")
    public ApiResponse<TeacherClassViews.ClassPage> discardDetails(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Changes discarded.", classes.discardDetails(currentUser, classId));
    }

    @PutMapping("/classes/{classId}/note")
    public ApiResponse<TeacherClassViews.ClassPage> saveNote(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassRequests.TeacherNote request) {
        return ApiResponse.success("Note saved.", classes.saveNote(currentUser, classId, request));
    }

    @PostMapping("/classes/{classId}/archive")
    public ApiResponse<TeacherClassViews.ClassPage> archiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class archived.", classes.archiveClass(currentUser, classId));
    }

    @PostMapping("/classes/{classId}/restore")
    public ApiResponse<TeacherClassViews.ClassPage> restoreClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class restored.", classes.restoreClass(currentUser, classId));
    }

    @DeleteMapping("/classes/{classId}")
    public ApiResponse<Void> deleteClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        classes.deleteClass(currentUser, classId);
        return ApiResponse.success("Draft class deleted.", null);
    }

    @GetMapping("/classes/{classId}/history")
    public ApiResponse<List<TeacherClassViews.Event>> history(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class history retrieved successfully.", classes.history(currentUser, classId));
    }

    @PostMapping("/classes/{classId}/units")
    public ApiResponse<TeacherClassViews.ClassPage> addUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassRequests.UnitTitle request) {
        return ApiResponse.success("Unit added.", classes.addUnit(currentUser, classId, request));
    }

    @PutMapping("/classes/{classId}/structure")
    public ApiResponse<TeacherClassViews.ClassPage> reorder(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassRequests.Structure request) {
        return ApiResponse.success("New order saved.", classes.reorder(currentUser, classId, request));
    }

    @PutMapping("/units/{unitId}")
    public ApiResponse<TeacherClassViews.ClassPage> renameUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId,
            @Valid @RequestBody ClassRequests.UnitTitle request) {
        return ApiResponse.success("Unit renamed.", classes.renameUnit(currentUser, unitId, request));
    }

    @PostMapping("/units/{unitId}/archive")
    public ApiResponse<TeacherClassViews.ClassPage> archiveUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId) {
        return ApiResponse.success("Unit archived.", classes.archiveUnit(currentUser, unitId));
    }

    /** Adds a draft lesson to the unit and answers with its editor. */
    @PostMapping("/units/{unitId}/lessons")
    public ApiResponse<TeacherClassViews.LessonEditor> addLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId,
            @Valid @RequestBody ClassRequests.LessonTitle request) {
        Long lessonId = classes.addLesson(currentUser, unitId, request);
        return ApiResponse.success("Lesson created as a draft.", lessons.editor(currentUser, lessonId));
    }
}
