package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
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
import com.example.aichinese.entity.enums.ReviewTargetType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.ReviewRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final CommunicationTargetResolver communicationTargetResolver;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ReviewService(
            ReviewRepository reviewRepository,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            StudentLearningAccessService studentLearningAccessService,
            CommunicationTargetResolver communicationTargetResolver,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.reviewRepository = reviewRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
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
        // Only fire the teacher notification when this is the FIRST review
        // submission for this (student, target) tuple. Without this guard,
        // a student who saved their review then changed their mind and
        // re-submitted with a different rating would re-notify the teacher
        // every time, polluting the teacher's notification feed. Edits go
        // through updateMyReview which already does not notify.
        boolean isFirstReview = review.getId() == null;
        review.setReviewer(student);
        review.setTargetType(target.targetType());
        review.setTargetId(target.targetId());
        review.setRating(normalizeRating(request.rating()));
        review.setReviewComment(TextUtils.trimToNull(request.comment()));
        review.setReviewStatus(ReviewStatus.VISIBLE);
        review.setVisible(true);
        Review saved = reviewRepository.save(review);

        if (isFirstReview && target.relatedTeacher() != null && target.relatedTeacher().getUser() != null) {
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
        review.setReviewComment(TextUtils.trimToNull(request.comment()));
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
        Long teacherUserId = currentUser.getUserId();

        // Previously this method called reviewRepository.findAll(), iterated
        // every row in JVM memory and re-resolved each review's target via
        // CommunicationTargetResolver (one extra SELECT per review). That was
        // O(reviews_total) memory + 2N+1 SQL — fine for a small demo, fatal
        // once the catalog grew.
        //
        // The new pipeline:
        //   1. Resolve the teacher's owned lesson + package ids in two
        //      teacher-scoped queries (each backed by indexes already declared
        //      on lessons.teacher_user_id / course_packages.teacher_user_id).
        //   2. Issue at most two bounded IN-list queries against reviews,
        //      filtering on the indexed (target_type, target_id) tuple.
        //   3. Merge, sort by creation date, and map.
        //
        // Worst case is now O(reviews_targeting_this_teacher) — i.e. the size
        // of the response itself — and exactly four SQL statements regardless
        // of total review volume.
        List<Lesson> ownedLessons = lessonRepository.findByTeacherUserIdOrderByUpdatedAtDesc(teacherUserId);
        List<CoursePackage> ownedPackages = coursePackageRepository.findByTeacherUserIdOrderByUpdatedAtDesc(teacherUserId);

        Map<Long, String> lessonTitlesById = new HashMap<>();
        for (Lesson lesson : ownedLessons) {
            lessonTitlesById.put(lesson.getId(), lesson.getTitle());
        }

        Map<Long, String> packageTitlesById = new HashMap<>();
        for (CoursePackage coursePackage : ownedPackages) {
            packageTitlesById.put(coursePackage.getId(), coursePackage.getTitle());
        }

        List<Review> teacherReviews = new ArrayList<>();
        if (!lessonTitlesById.isEmpty()) {
            teacherReviews.addAll(reviewRepository.findByTargetTypeAndTargetIdInOrderByCreatedAtDesc(
                    ReviewTargetType.LESSON.name(), lessonTitlesById.keySet()));
        }
        if (!packageTitlesById.isEmpty()) {
            teacherReviews.addAll(reviewRepository.findByTargetTypeAndTargetIdInOrderByCreatedAtDesc(
                    ReviewTargetType.PACKAGE.name(), packageTitlesById.keySet()));
        }

        teacherReviews.sort(Comparator.comparing(Review::getCreatedAt).reversed());

        List<ReviewResponse> responses = new ArrayList<>(teacherReviews.size());
        for (Review review : teacherReviews) {
            String title = resolveTeacherReviewTitle(review, lessonTitlesById, packageTitlesById);
            responses.add(communicationMapper.toReviewResponse(review, title));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getAdminReviews() {
        // Push ordering down to the database (uses the BaseEntity created_at
        // column, which is normally indexed via the @CreationTimestamp default
        // ordering pattern) so the JVM does not have to re-sort the full set.
        return reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
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

    /**
     * Fast-path title resolver for {@link #getTeacherReviews}: the caller has
     * already pre-loaded the title for every lesson + package this teacher
     * owns, so we can answer in O(1) without re-hitting the database for each
     * review. We fall back to the generic resolver only if the review somehow
     * points at a target that is not in the teacher's owned set (defensive
     * — should not happen given the upstream filter, but keeps the response
     * shape compatible with {@link #resolveTitle}).
     */
    private String resolveTeacherReviewTitle(
            Review review,
            Map<Long, String> lessonTitlesById,
            Map<Long, String> packageTitlesById) {
        if (review.getTargetType() == null || review.getTargetId() == null) {
            return null;
        }
        if (ReviewTargetType.LESSON.name().equalsIgnoreCase(review.getTargetType())) {
            String title = lessonTitlesById.get(review.getTargetId());
            if (title != null) {
                return title;
            }
        }
        if (ReviewTargetType.PACKAGE.name().equalsIgnoreCase(review.getTargetType())) {
            String title = packageTitlesById.get(review.getTargetId());
            if (title != null) {
                return title;
            }
        }
        return resolveTitle(review);
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

    private String resolveTitle(Review review) {
        try {
            return communicationTargetResolver.resolveReviewTarget(review.getTargetType(), review.getTargetId()).targetTitle();
        } catch (ResponseStatusException exception) {
            return null;
        }
    }
}
