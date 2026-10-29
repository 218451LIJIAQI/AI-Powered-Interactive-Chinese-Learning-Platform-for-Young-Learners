package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.base.TeacherArchivableContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class TeacherContentAccessService {

    private static final String APPROVED_TEACHER_CONTENT_MESSAGE = "Only approved teachers can create or submit learning content.";

    /**
     * Message of the FRTVER04 guard for the learner-facing teacher features (roster, feedback, practice
     * submissions, analytics, AI drafts).
     */
    private static final String APPROVED_TEACHER_FEATURE_MESSAGE = "Only approved teachers can use this teaching feature.";

    private final TeacherProfileRepository teacherProfileRepository;
    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;

    public TeacherContentAccessService(
            TeacherProfileRepository teacherProfileRepository,
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.learningLevelRepository = learningLevelRepository;
        this.learningCategoryRepository = learningCategoryRepository;
    }

    @Transactional(readOnly = true)
    public TeacherProfile requireApprovedTeacher(SecurityUserDetails currentUser) {
        TeacherProfile teacherProfile = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        if (teacherProfile.getTeacherStatus() != TeacherStatus.APPROVED) {
            throw new ResponseStatusException(FORBIDDEN, APPROVED_TEACHER_CONTENT_MESSAGE);
        }

        return teacherProfile;
    }

    /**
     * Teacher-approval guard (FRTVER04) used by {@code ApprovedTeacherInterceptor} for the learner-facing
     * {@code /api/v1/teachers/**} endpoints; the other protected teacher endpoints use
     * {@link #requireApprovedTeacherForContent}. The teacher must be APPROVED and active.
     */
    @Transactional(readOnly = true)
    public void requireApprovedTeacherAccess(SecurityUserDetails currentUser) {
        if (!isApprovedTeacher(currentUser)) {
            throw new ResponseStatusException(FORBIDDEN, APPROVED_TEACHER_FEATURE_MESSAGE);
        }
    }

    /** Same guard as {@link #requireApprovedTeacherAccess} with the content-authoring message. */
    @Transactional(readOnly = true)
    public void requireApprovedTeacherForContent(SecurityUserDetails currentUser) {
        if (!isApprovedTeacher(currentUser)) {
            throw new ResponseStatusException(FORBIDDEN, APPROVED_TEACHER_CONTENT_MESSAGE);
        }
    }

    /** True when the current teacher is APPROVED and active. */
    private boolean isApprovedTeacher(SecurityUserDetails currentUser) {
        return currentUser != null && currentUser.getUserId() != null
                && teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                        .map(ContentVisibilityPolicy::isActiveApprovedTeacher)
                        .orElse(false);
    }

    @Transactional(readOnly = true)
    public LearningLevel requireLevel(Long levelId) {
        return learningLevelRepository.findById(levelId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected learning level does not exist."));
    }

    @Transactional(readOnly = true)
    public LearningCategory requireCategory(Long categoryId) {
        return learningCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected learning category does not exist."));
    }

    /**
     * Archives the teacher's own item and keeps the status it had, so an admin restore returns a draft or a
     * rejected item to the teacher instead of publishing it.
     */
    public void archiveOwnClass(TeacherArchivableContent item) {
        ContentStatus currentStatus = item.getContentStatus();
        if (!ContentStatus.TEACHER_ARCHIVABLE.contains(currentStatus)) {
            throw new ResponseStatusException(BAD_REQUEST, currentStatus == ContentStatus.ARCHIVED
                    ? "Class has already been archived."
                    : "Class cannot be archived while it is under review.");
        }
        item.setStatusBeforeArchive(currentStatus);
        item.setContentStatus(ContentStatus.ARCHIVED);
    }
}
