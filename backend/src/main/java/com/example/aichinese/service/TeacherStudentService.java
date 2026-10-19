package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.teacher.TeacherStudentSummaryResponse;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
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
 * by student id. All published content is free, so a student appears in
 * the teacher's roster as soon as they start one of the teacher's lessons,
 * attempt one of the teacher's quizzes or enrol in one of the teacher's
 * live classes.</p>
 *
 * <h2>Needs support</h2>
 * <p>A student "needs support" (atRisk) when the average percentage of their submitted attempts
 * on this teacher's quizzes is below {@value #LOW_QUIZ_AVERAGE_THRESHOLD_PERCENT} % or when they
 * have shown no learning activity for {@value #INACTIVE_DAYS} days. Teacher feedback is the
 * teacher's own action and never flags a student. Weak areas are the lesson titles of the
 * student's wrong answers on this teacher's quizzes.</p>
 *
 * <h2>PII boundary</h2>
 * <p>Teacher-facing student responses
 * ({@link TeacherStudentSummaryResponse}) include the student's
 * display name and engagement aggregates only — the underlying email
 * address and other account details never leak through this surface,
 * even to an approved teacher.</p>
 */
@Service
public class TeacherStudentService {

    public static final String SUPPORT_LOW_QUIZ_SCORE = "LOW_QUIZ_SCORE";
    public static final String SUPPORT_INACTIVE = "INACTIVE";
    static final int LOW_QUIZ_AVERAGE_THRESHOLD_PERCENT = 60;
    static final int INACTIVE_DAYS = 14;

    private static final String TEACHER_PROFILE_NOT_FOUND = "Teacher profile could not be found.";
    private static final int WEAK_AREA_LIMIT = 4;
    private static final int PERCENT_MAX = 100;

    private final TeacherProfileRepository teacherProfileRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final StudentProfileRepository studentProfileRepository;

    public TeacherStudentService(
            TeacherProfileRepository teacherProfileRepository,
            LessonProgressRepository lessonProgressRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            TeacherFeedbackRepository teacherFeedbackRepository,
            StudentProfileRepository studentProfileRepository) {
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.teacherFeedbackRepository = Objects.requireNonNull(teacherFeedbackRepository, "teacherFeedbackRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherStudentSummaryResponse> getTeacherStudents(SecurityUserDetails currentUser, int page, int size) {
        TeacherProfile teacher = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, TEACHER_PROFILE_NOT_FOUND));

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
        List<QuizAttemptAnswer> wrongAnswers = quizAttemptAnswerRepository
                .findByQuizAttemptQuizTeacherIdAndQuizAttemptStudentIdInAndCorrectFalseOrderByCreatedAtDesc(teacherId, studentIds);

        for (TeacherFeedback feedback : feedbackList) {
            StudentAggregate aggregate = aggregateFor(aggregates, feedback.getStudent());
            if (aggregate != null) {
                aggregate.captureFeedback(feedback);
            }
        }

        for (QuizAttempt attempt : attempts) {
            StudentAggregate aggregate = aggregateFor(aggregates, attempt.getStudent());
            if (aggregate != null) {
                aggregate.captureAttempt(attempt);
            }
        }

        for (LiveClassEnrolment enrolment : enrolments) {
            StudentAggregate aggregate = aggregateFor(aggregates, enrolment.getStudent());
            if (aggregate != null && enrolment.getLiveClass() != null) {
                aggregate.captureEnrolment(enrolment);
            }
        }

        for (LessonProgress progress : lessonProgressList) {
            StudentAggregate aggregate = aggregateFor(aggregates, progress.getStudent());
            if (aggregate != null) {
                aggregate.captureLessonProgress(progress);
            }
        }

        for (QuizAttemptAnswer answer : wrongAnswers) {
            StudentAggregate aggregate = answer.getQuizAttempt() == null
                    ? null
                    : aggregateFor(aggregates, answer.getQuizAttempt().getStudent());
            if (aggregate != null) {
                aggregate.captureWrongAnswer(answer);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<TeacherStudentSummaryResponse> items = aggregates.values().stream()
                .map(aggregate -> aggregate.toResponse(now))
                .sorted(Comparator
                        .comparing(TeacherStudentSummaryResponse::latestActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TeacherStudentSummaryResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, studentIdPage.getTotalElements(), studentIdPage.getTotalPages());
    }

    /** Support reasons for a roster entry (pure rule, unit-tested). */
    static List<String> supportReasons(Double quizAveragePercent, LocalDateTime latestActivityAt, LocalDateTime now) {
        List<String> reasons = new ArrayList<>();
        if (quizAveragePercent != null && quizAveragePercent < LOW_QUIZ_AVERAGE_THRESHOLD_PERCENT) {
            reasons.add(SUPPORT_LOW_QUIZ_SCORE);
        }
        if (latestActivityAt == null || latestActivityAt.isBefore(now.minusDays(INACTIVE_DAYS))) {
            reasons.add(SUPPORT_INACTIVE);
        }
        return reasons;
    }

    private StudentAggregate aggregateFor(
            Map<Long, StudentAggregate> aggregates,
            StudentProfile student) {
        return student == null ? null : aggregates.get(student.getId());
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
        private final List<QuizAttempt> attempts = new ArrayList<>();
        private final Map<Long, LessonProgress> progressByLesson = new LinkedHashMap<>();

        private StudentAggregate(StudentProfile student) {
            this.student = student;
        }

        private void captureFeedback(TeacherFeedback feedback) {
            // Feedback is the teacher's action: it is shown as "latest feedback" but never counts as
            // student activity, weak area or a support signal.
            if (feedback.getFeedbackType() != null && !feedback.getFeedbackType().isBlank()
                    && (latestFeedbackAt == null || feedback.getCreatedAt().isAfter(latestFeedbackAt))) {
                latestFeedbackType = feedback.getFeedbackType();
                latestFeedbackAt = feedback.getCreatedAt();
            }
        }

        private void captureAttempt(QuizAttempt attempt) {
            Integer percentage = LessonMasteryCalculator.attemptPercentage(attempt);
            if (percentage != null) {
                submittedAttemptCount += 1;
                submittedAttemptPercentTotal += percentage;
            }
            attempts.add(attempt);
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
            if (progress.getLesson() != null) {
                progressByLesson.put(progress.getLesson().getId(), progress);
            }
            bumpLatestActivity(progress.getLastAccessedAt() != null ? progress.getLastAccessedAt() : progress.getUpdatedAt());
        }

        private void captureWrongAnswer(QuizAttemptAnswer answer) {
            if (weakAreas.size() >= WEAK_AREA_LIMIT) {
                return;
            }
            String topic = StudentLearningProgressService.weakAreaTopic(answer);
            if (topic != null) {
                weakAreas.add(topic);
            }
        }

        private void bumpLatestActivity(LocalDateTime candidate) {
            if (candidate != null && (latestActivityAt == null || candidate.isAfter(latestActivityAt))) {
                latestActivityAt = candidate;
            }
        }

        private TeacherStudentSummaryResponse toResponse(LocalDateTime now) {
            var user = student.getUser();
            int progressPercent = lessonProgressCount > 0 ? Math.round((float) lessonProgressTotal / lessonProgressCount) : 0;
            Double quizAveragePercent = submittedAttemptCount > 0
                    ? submittedAttemptPercentTotal / (double) submittedAttemptCount
                    : null;
            LocalDateTime activityAt = latestActivityAt != null ? latestActivityAt : student.getCreatedAt();
            List<String> reasons = supportReasons(quizAveragePercent, activityAt, now);
            boolean needsSupport = !reasons.isEmpty();

            return new TeacherStudentSummaryResponse(
                    student.getId(),
                    user != null ? user.getId() : null,
                    user != null ? user.getDisplayName() : student.getDisplayName(),
                    student.getDisplayName(),
                    student.getCurrentLevel() != null ? student.getCurrentLevel().getName() : null,
                    progressPercent,
                    quizAveragePercent,
                    needsSupport,
                    needsSupport,
                    reasons,
                    List.copyOf(weakAreas),
                    latestFeedbackType,
                    latestFeedbackAt,
                    activityAt,
                    student.getCreatedAt(),
                    lessonMastery());
        }

        private List<TeacherStudentSummaryResponse.LessonMasteryItem> lessonMastery() {
            Map<Long, Integer> bestQuizByLesson = LessonMasteryCalculator.bestQuizPercentageByLesson(attempts);
            Map<Long, Lesson> lessons = new LinkedHashMap<>();
            progressByLesson.values().forEach(progress -> lessons.put(progress.getLesson().getId(), progress.getLesson()));
            attempts.stream()
                    .filter(attempt -> attempt.getQuiz() != null && attempt.getQuiz().getLesson() != null)
                    .forEach(attempt -> lessons.putIfAbsent(attempt.getQuiz().getLesson().getId(), attempt.getQuiz().getLesson()));
            return lessons.values().stream()
                    .map(lesson -> {
                        LessonProgress progress = progressByLesson.get(lesson.getId());
                        Integer best = bestQuizByLesson.get(lesson.getId());
                        return new TeacherStudentSummaryResponse.LessonMasteryItem(
                                lesson.getId(),
                                lesson.getTitle(),
                                progress == null ? 0 : clampPercentage(progress.getCompletionPercent()),
                                LessonMasteryCalculator.mastery(progress, best).name(),
                                best);
                    })
                    .toList();
        }

        private int clampPercentage(Integer value) {
            if (value == null) {
                return 0;
            }
            return Math.max(0, Math.min(PERCENT_MAX, value));
        }
    }
}
