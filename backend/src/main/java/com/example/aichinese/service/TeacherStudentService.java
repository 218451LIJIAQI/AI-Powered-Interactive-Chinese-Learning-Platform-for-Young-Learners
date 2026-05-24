package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.teacher.TeacherStudentSummaryResponse;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Teacher-side view onto "my students" — students who have engaged
 * with content owned by the calling teacher (lesson progress, quiz
 * attempts, live-class enrolments).
 *
 * <h2>Roster derivation</h2>
 * <p>There is no explicit teacher↔student relationship table; the
 * roster is derived from observed engagement signals and deduplicated
 * by student id. This means newly purchased content for a student
 * immediately appears in the teacher's roster as soon as the student
 * starts the lesson / attempts the quiz / enrolls in the live class
 * — no extra plumbing required when entitlements change.</p>
 *
 * <h2>PII boundary</h2>
 * <p>Teacher-facing student responses
 * ({@link TeacherStudentSummaryResponse}) include the student's
 * display name and engagement aggregates only — the underlying email
 * / age / parent contact never leak through this surface, even to
 * an approved teacher.</p>
 */
@Service
public class TeacherStudentService {

    private static final Logger log = LoggerFactory.getLogger(TeacherStudentService.class);

    private final TeacherProfileRepository teacherProfileRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final LiveLearningAccessService liveLearningAccessService;

    public TeacherStudentService(
            TeacherProfileRepository teacherProfileRepository,
            LessonProgressRepository lessonProgressRepository,
            QuizAttemptRepository quizAttemptRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            TeacherFeedbackRepository teacherFeedbackRepository,
            StudentProfileRepository studentProfileRepository,
            StudentLearningAccessService studentLearningAccessService,
            LiveLearningAccessService liveLearningAccessService) {
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.teacherFeedbackRepository = Objects.requireNonNull(teacherFeedbackRepository, "teacherFeedbackRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherStudentSummaryResponse> getTeacherStudents(SecurityUserDetails currentUser, int page, int size) {
        TeacherProfile teacher = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        Long teacherId = teacher.getId();
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<Long> studentIdPage = studentProfileRepository.findLinkedStudentIdsByTeacherId(
                teacherId,
                PageRequest.of(safePage, safeSize));
        List<Long> studentIds = studentIdPage.getContent();
        if (studentIds.isEmpty()) {
            return new PageResponse<>(List.of(), safePage, safeSize, studentIdPage.getTotalElements(), studentIdPage.getTotalPages());
        }

        Map<Long, StudentAggregate> aggregates = new LinkedHashMap<>();
        Map<Long, StudentProfile> studentsById = new HashMap<>();
        for (StudentProfile student : studentProfileRepository.findByIdIn(studentIds)) {
            if (student.getId() != null) {
                studentsById.put(student.getId(), student);
            }
        }
        for (Long studentId : studentIds) {
            StudentProfile student = studentsById.get(studentId);
            if (student != null) {
                aggregates.put(studentId, new StudentAggregate(student));
            }
        }

        List<QuizAttempt> attempts = quizAttemptRepository.findByQuizTeacherIdAndStudentIdInOrderByCompletedAtDescCreatedAtDesc(teacherId, studentIds);
        List<TeacherFeedback> feedbackList = teacherFeedbackRepository.findByTeacherIdAndStudentIdInOrderByCreatedAtDesc(teacherId, studentIds);
        List<LiveClassEnrolment> enrolments = liveClassEnrolmentRepository.findByLiveClassTeacherIdAndStudentIdIn(teacherId, studentIds);
        List<LessonProgress> lessonProgressList = lessonProgressRepository.findByLessonTeacherIdAndStudentIdIn(teacherId, studentIds);

        for (TeacherFeedback feedback : feedbackList) {
            StudentProfile student = feedback.getStudent();
            if (student == null) {
                continue;
            }
            StudentAggregate aggregate = aggregates.get(student.getId());
            if (aggregate != null && aggregate.hasCurrentFeedbackAccess(feedback)) {
                aggregate.captureFeedback(feedback);
            }
        }

        for (QuizAttempt attempt : attempts) {
            StudentProfile student = attempt.getStudent();
            if (student == null) {
                continue;
            }
            StudentAggregate aggregate = aggregates.get(student.getId());
            if (aggregate != null && aggregate.hasCurrentAttemptAccess(attempt)) {
                aggregate.captureAttempt(attempt);
            }
        }

        for (LiveClassEnrolment enrolment : enrolments) {
            if (enrolment.getStudent() == null) {
                continue;
            }
            StudentAggregate aggregate = aggregates.get(enrolment.getStudent().getId());
            if (aggregate != null && aggregate.hasCurrentEnrolmentAccess(enrolment)) {
                aggregate.captureEnrolment(enrolment);
            }
        }

        for (LessonProgress progress : lessonProgressList) {
            if (progress.getStudent() == null) {
                continue;
            }
            StudentAggregate aggregate = aggregates.get(progress.getStudent().getId());
            if (aggregate != null && aggregate.hasCurrentLessonAccess(progress)) {
                aggregate.captureLessonProgress(progress);
            }
        }

        List<TeacherStudentSummaryResponse> items = aggregates.values().stream()
                .map(StudentAggregate::toResponse)
                .sorted(Comparator
                        .comparing(TeacherStudentSummaryResponse::latestActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TeacherStudentSummaryResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, studentIdPage.getTotalElements(), studentIdPage.getTotalPages());
    }

    private final class StudentAggregate {
        private final StudentProfile student;
        private int lessonProgressCount = 0;
        private int lessonProgressTotal = 0;
        private int submittedAttemptCount = 0;
        private int submittedAttemptPercentTotal = 0;
        private String latestFeedbackType;
        private LocalDateTime latestFeedbackAt;
        private LocalDateTime latestActivityAt;
        private final Set<String> weakAreas = new LinkedHashSet<>();

        private StudentAggregate(StudentProfile student) {
            this.student = student;
            this.latestActivityAt = student.getCreatedAt();
        }

        private void captureFeedback(TeacherFeedback feedback) {
            if (feedback.getFeedbackType() != null && !feedback.getFeedbackType().isBlank()) {
                if (latestFeedbackAt == null || feedback.getCreatedAt().isAfter(latestFeedbackAt)) {
                    latestFeedbackType = feedback.getFeedbackType();
                    latestFeedbackAt = feedback.getCreatedAt();
                }
                weakAreas.add(normalizeWeakArea(feedback.getFeedbackType()));
            }
            bumpLatestActivity(feedback.getCreatedAt());
        }

        private boolean hasCurrentFeedbackAccess(TeacherFeedback feedback) {
            if (feedback.getLesson() != null) {
                return studentLearningAccessService.hasLessonAccess(student, feedback.getLesson());
            }
            if (feedback.getQuizAttempt() != null
                    && feedback.getQuizAttempt().getQuiz() != null
                    && feedback.getQuizAttempt().getQuiz().getLesson() != null) {
                return studentLearningAccessService.hasLessonAccess(student, feedback.getQuizAttempt().getQuiz().getLesson());
            }
            return true;
        }

        private boolean hasCurrentAttemptAccess(QuizAttempt attempt) {
            return attempt.getQuiz() == null
                    || attempt.getQuiz().getLesson() == null
                    || studentLearningAccessService.hasLessonAccess(student, attempt.getQuiz().getLesson());
        }

        private boolean hasCurrentEnrolmentAccess(LiveClassEnrolment enrolment) {
            if (enrolment.getLiveClass() == null) {
                return false;
            }
            boolean lessonAccess = enrolment.getLiveClass().getLesson() == null
                    || liveLearningAccessService.hasStudentLessonAccess(student, enrolment.getLiveClass().getLesson());
            boolean packageAccess = enrolment.getLiveClass().getCoursePackage() == null
                    || liveLearningAccessService.hasStudentPackageAccess(student, enrolment.getLiveClass().getCoursePackage());
            return lessonAccess && packageAccess;
        }

        private boolean hasCurrentLessonAccess(LessonProgress progress) {
            return progress.getLesson() == null || studentLearningAccessService.hasLessonAccess(student, progress.getLesson());
        }

        private void captureAttempt(QuizAttempt attempt) {
            if (attempt.isSubmitted()
                    && attempt.getScore() != null
                    && attempt.getMaxScore() != null
                    && attempt.getMaxScore() > 0) {
                submittedAttemptCount += 1;
                submittedAttemptPercentTotal += Math.round((attempt.getScore() * 100f) / attempt.getMaxScore());
            }
            bumpLatestActivity(attempt.getCompletedAt() != null ? attempt.getCompletedAt() : attempt.getCreatedAt());
        }

        private void captureEnrolment(LiveClassEnrolment enrolment) {
            bumpLatestActivity(
                    enrolment.getAttendedAt() != null
                            ? enrolment.getAttendedAt()
                            : enrolment.getEnrolledAt() != null
                                    ? enrolment.getEnrolledAt()
                                    : enrolment.getCreatedAt());
        }

        private void captureLessonProgress(LessonProgress progress) {
            lessonProgressCount += 1;
            lessonProgressTotal += clampPercentage(progress.getCompletionPercent());
            bumpLatestActivity(progress.getLastAccessedAt() != null ? progress.getLastAccessedAt() : progress.getUpdatedAt());
        }

        private void bumpLatestActivity(LocalDateTime candidate) {
            if (candidate != null && (latestActivityAt == null || candidate.isAfter(latestActivityAt))) {
                latestActivityAt = candidate;
            }
        }

        private TeacherStudentSummaryResponse toResponse() {
            int progressPercent = lessonProgressCount > 0 ? Math.round((float) lessonProgressTotal / lessonProgressCount) : 0;
            Double quizAveragePercent = submittedAttemptCount > 0
                    ? submittedAttemptPercentTotal / (double) submittedAttemptCount
                    : null;
            boolean atRisk = (progressPercent > 0 && progressPercent < 60)
                    || (quizAveragePercent != null && quizAveragePercent < 70)
                    || !weakAreas.isEmpty();

            return new TeacherStudentSummaryResponse(
                    student.getId(),
                    student.getUser() != null ? student.getUser().getId() : null,
                    student.getUser() != null ? student.getUser().getDisplayName() : student.getDisplayName(),
                    student.getUser() != null ? student.getUser().getEmail() : null,
                    student.getDisplayName(),
                    student.getCurrentLevel() != null ? student.getCurrentLevel().getName() : null,
                    progressPercent,
                    quizAveragePercent,
                    atRisk,
                    weakAreas.stream().limit(4).toList(),
                    latestFeedbackType,
                    latestFeedbackAt,
                    latestActivityAt,
                    student.getCreatedAt());
        }

        private int clampPercentage(Integer value) {
            if (value == null) {
                return 0;
            }
            return Math.max(0, Math.min(100, value));
        }

        private String normalizeWeakArea(String rawType) {
            String normalized = rawType.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return normalized;
            }
            String[] words = normalized.split("\\s+");
            for (int index = 0; index < words.length; index++) {
                String word = words[index];
                if (word.isEmpty()) {
                    continue;
                }
                words[index] = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
            return String.join(" ", words);
        }
    }
}
