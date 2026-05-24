package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Unauthenticated read endpoint for an APPROVED teacher's public
 * profile page (bio, teaching categories, published course count).
 *
 * <p>Strictly filtered: the teacher must be both
 * {@link TeacherStatus#APPROVED} and have the {@code publicVisible}
 * flag set — so a teacher who opts out (or who is suspended) does
 * not appear in the public surface. Course count is computed as the
 * sum of {@link ContentStatus#PUBLISHED} lessons + course packages,
 * matching what the public catalogue actually surfaces.</p>
 */
@Service
public class PublicTeacherProfileService {

    private static final Logger log = LoggerFactory.getLogger(PublicTeacherProfileService.class);

    private final TeacherProfileRepository teacherProfileRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final TeacherManagementMapper teacherManagementMapper;

    public PublicTeacherProfileService(
            TeacherProfileRepository teacherProfileRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            TeacherManagementMapper teacherManagementMapper) {
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.teacherManagementMapper = Objects.requireNonNull(teacherManagementMapper, "teacherManagementMapper");
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
