package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.communication.CreateReviewRequest;
import com.example.aichinese.dto.communication.ReviewResponse;
import com.example.aichinese.dto.communication.UpdateReviewRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.ReviewStatus;
import com.example.aichinese.repository.ReviewRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final CommunicationTargetResolver communicationTargetResolver;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ReviewService(
            ReviewRepository reviewRepository,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            StudentLearningAccessService studentLearningAccessService,
            CommunicationTargetResolver communicationTargetResolver,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.reviewRepository = reviewRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.studentLearningAccessService = studentLearningAccessService;
        this.communicationTargetResolver = communicationTargetResolver;
        this.communicationMapper = communicationMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ReviewResponse createReview(SecurityUserDetails currentUser, CreateReviewRequest request) {
        StudentProfile student = requireStudent(currentUser);
        CommunicationTargetResolver.ResolvedTarget target = communicationTargetResolver.resolveReviewTarget(
                request.targetType(),
                request.targetId());
        validateStudentAccess(student, target);

        Review review = reviewRepository.findByReviewerIdAndTargetTypeAndTargetId(student.getId(), target.targetType(), target.targetId())
                .orElseGet(Review::new);
        review.setReviewer(student);
        review.setTargetType(target.targetType());
        review.setTargetId(target.targetId());
        review.setRating(normalizeRating(request.rating()));
        review.setReviewComment(trimToNull(request.comment()));
        review.setReviewStatus(ReviewStatus.VISIBLE);
        review.setVisible(true);
        Review saved = reviewRepository.save(review);

        if (target.relatedTeacher() != null && target.relatedTeacher().getUser() != null) {
            notificationService.safeCreateNotification(
                    target.relatedTeacher().getUser(),
                    NotificationType.REVIEW_RECEIVED,
                    "New review received",
                    "A student has submitted a review for your content.",
                    saved.getTargetType(),
                    saved.getTargetId(),
                    "/teacher/reviews",
                    student.getUser());
        }

        return communicationMapper.toReviewResponse(saved, target.targetTitle());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(SecurityUserDetails currentUser) {
        return reviewRepository.findByReviewerUserIdOrderByUpdatedAtDesc(currentUser.getUserId()).stream()
                .map(review -> communicationMapper.toReviewResponse(review, resolveTitle(review)))
                .toList();
    }

    @Transactional
    public ReviewResponse updateMyReview(SecurityUserDetails currentUser, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findByIdAndReviewerUserId(reviewId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Review was not found."));
        review.setRating(normalizeRating(request.rating()));
        review.setReviewComment(trimToNull(request.comment()));
        return communicationMapper.toReviewResponse(review, resolveTitle(review));
    }

    @Transactional
    public ReviewResponse hideMyReview(SecurityUserDetails currentUser, Long reviewId) {
        Review review = reviewRepository.findByIdAndReviewerUserId(reviewId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Review was not found."));
        review.setReviewStatus(ReviewStatus.HIDDEN);
        review.setVisible(false);
        return communicationMapper.toReviewResponse(review, resolveTitle(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getPublicVisibleReviews(String targetType, Long targetId) {
        return reviewRepository.findByTargetTypeAndTargetIdAndReviewStatusOrderByCreatedAtDesc(
                targetType.trim().toUpperCase(),
                targetId,
                ReviewStatus.VISIBLE).stream()
                .filter(Review::isVisible)
                .map(review -> communicationMapper.toReviewResponse(review, resolveTitle(review)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getTeacherReviews(SecurityUserDetails currentUser) {
        return reviewRepository.findAll().stream()
                .filter(review -> belongsToTeacher(review, currentUser.getUserId()))
                .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                .map(review -> communicationMapper.toReviewResponse(review, resolveTitle(review)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getAdminReviews() {
        return reviewRepository.findAll().stream()
                .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                .map(review -> communicationMapper.toReviewResponse(review, resolveTitle(review)))
                .toList();
    }

    @Transactional
    public ReviewResponse hideReview(SecurityUserDetails currentUser, Long reviewId) {
        return updateAdminReviewStatus(currentUser, reviewId, ReviewStatus.HIDDEN, false, "REVIEW_HIDDEN");
    }

    @Transactional
    public ReviewResponse showReview(SecurityUserDetails currentUser, Long reviewId) {
        return updateAdminReviewStatus(currentUser, reviewId, ReviewStatus.VISIBLE, true, "REVIEW_RESTORED");
    }

    @Transactional
    public ReviewResponse flagReview(SecurityUserDetails currentUser, Long reviewId) {
        return updateAdminReviewStatus(currentUser, reviewId, ReviewStatus.FLAGGED, false, "REVIEW_FLAGGED");
    }

    private ReviewResponse updateAdminReviewStatus(
            SecurityUserDetails currentUser,
            Long reviewId,
            ReviewStatus status,
            boolean visible,
            String actionType) {
        User admin = requireUser(currentUser.getUserId());
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Review was not found."));
        review.setReviewStatus(status);
        review.setVisible(visible);
        auditLogService.safeRecord(
                admin,
                actionType,
                review.getTargetType(),
                review.getTargetId(),
                "Admin updated review #" + review.getId() + " to " + status.name() + ".",
                null);
        return communicationMapper.toReviewResponse(review, resolveTitle(review));
    }

    private void validateStudentAccess(StudentProfile student, CommunicationTargetResolver.ResolvedTarget target) {
        Lesson lesson = target.lesson();
        if (lesson != null) {
            if (lesson.getContentStatus() != ContentStatus.PUBLISHED) {
                throw new ResponseStatusException(NOT_FOUND, "Lesson was not found.");
            }
            studentLearningAccessService.ensureLessonAccess(student, lesson);
            return;
        }

        CoursePackage coursePackage = target.coursePackage();
        if (coursePackage != null) {
            if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
                throw new ResponseStatusException(NOT_FOUND, "Package was not found.");
            }
            if (coursePackage.getAccessType() == AccessType.PAID
                    && !studentLearningAccessService.hasPackageAccess(student, coursePackage)) {
                throw new ResponseStatusException(NOT_FOUND, "Package was not found.");
            }
        }
    }

    private boolean belongsToTeacher(Review review, Long teacherUserId) {
        try {
            return communicationTargetResolver.resolveReviewTarget(review.getTargetType(), review.getTargetId()).relatedTeacher() != null
                    && communicationTargetResolver.resolveReviewTarget(review.getTargetType(), review.getTargetId()).relatedTeacher().getUser() != null
                    && communicationTargetResolver.resolveReviewTarget(review.getTargetType(), review.getTargetId()).relatedTeacher().getUser().getId().equals(teacherUserId);
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private BigDecimal normalizeRating(BigDecimal rating) {
        return rating.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String resolveTitle(Review review) {
        try {
            return communicationTargetResolver.resolveReviewTarget(review.getTargetType(), review.getTargetId()).targetTitle();
        } catch (ResponseStatusException exception) {
            return null;
        }
    }
}
