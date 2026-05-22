package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.TeacherProfileRepository;

@Service
public class PublicTeacherProfileService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final TeacherManagementMapper teacherManagementMapper;

    public PublicTeacherProfileService(
            TeacherProfileRepository teacherProfileRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            TeacherManagementMapper teacherManagementMapper) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.teacherManagementMapper = teacherManagementMapper;
    }

    @Transactional(readOnly = true)
    public PublicTeacherProfileResponse getApprovedTeacherProfile(Long teacherId) {
        TeacherProfile teacherProfile = teacherProfileRepository
                .findByIdAndTeacherStatusAndPublicVisibleTrue(teacherId, TeacherStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Approved teacher profile was not found."));

        long courseCount = lessonRepository.countByTeacherIdAndContentStatus(teacherId, ContentStatus.PUBLISHED)
                + coursePackageRepository.countByTeacherIdAndContentStatus(teacherId, ContentStatus.PUBLISHED);
        int safeCourseCount = courseCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) courseCount;
        return teacherManagementMapper.toPublicTeacherProfileResponse(teacherProfile, safeCourseCount);
    }
}
