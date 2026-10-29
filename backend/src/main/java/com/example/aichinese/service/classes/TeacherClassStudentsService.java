package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassStudentViews;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.LessonMasteryCalculator;

/**
 * The teacher's students: everyone who joined one of the teacher's classes, with class progress, quiz average and
 * whether they need support (a quiz average under 60 % or no activity for 7 days), and a student's detail: progress
 * by lesson, quiz results, weak areas (question skills under 60 % correct in a quiz) and the feedback sent.
 */
@Service
public class TeacherClassStudentsService {

    private static final int SUPPORT_QUIZ_AVERAGE = 60;
    private static final int INACTIVE_DAYS = 7;
    private static final int WEAK_AREA_PERCENT = 60;
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}+");

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final TeacherFeedbackRepository feedbackRepository;

    public TeacherClassStudentsService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassProgressService progress,
            ClassEnrolmentRepository enrolmentRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository answerRepository,
            TeacherFeedbackRepository feedbackRepository) {
        this.access = access;
        this.content = content;
        this.progress = progress;
        this.enrolmentRepository = enrolmentRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.answerRepository = answerRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /** {@code filter}: ALL or SUPPORT (needs support); {@code search} matches the name. */
    @Transactional(readOnly = true)
    public ClassStudentViews.Roster roster(SecurityUserDetails currentUser, String search, String filter) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        List<ClassStudentViews.RosterRow> rows = rosterRows(teacher);
        String query = search == null || search.isBlank() ? null : search.strip().toLowerCase(Locale.ROOT);
        boolean supportOnly = "SUPPORT".equalsIgnoreCase(filter);
        List<ClassStudentViews.RosterRow> shown = rows.stream()
                .filter(row -> query == null || row.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(row -> !supportOnly || row.needsSupport())
                .toList();
        return new ClassStudentViews.Roster(shown, rows.size(), rows.stream().filter(ClassStudentViews.RosterRow::needsSupport).count());
    }

    /** One row per student of the teacher's classes, the most recently joined class first per student. */
    List<ClassStudentViews.RosterRow> rosterRows(TeacherProfile teacher) {
        List<ClassEnrolment> enrolments = enrolmentRepository.findByCoursePackageTeacherIdOrderByJoinedAtDesc(teacher.getId());
        Map<Long, List<ClassEnrolment>> byStudent = new LinkedHashMap<>();
        enrolments.forEach(enrolment -> byStudent.computeIfAbsent(enrolment.getStudent().getId(), ignored -> new ArrayList<>()).add(enrolment));
        Map<Long, ClassSnapshot> snapshots = new HashMap<>();
        for (CoursePackage coursePackage : enrolments.stream().map(ClassEnrolment::getCoursePackage).distinct().toList()) {
            List<Long> studentIds = enrolments.stream()
                    .filter(enrolment -> enrolment.getCoursePackage().getId().equals(coursePackage.getId()))
                    .map(enrolment -> enrolment.getStudent().getId())
                    .toList();
            snapshots.put(coursePackage.getId(), snapshot(coursePackage, studentIds));
        }
        List<ClassStudentViews.RosterRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<ClassEnrolment>> entry : byStudent.entrySet()) {
            ClassEnrolment primary = entry.getValue().get(0);
            StudentProfile student = primary.getStudent();
            ClassSnapshot snapshot = snapshots.get(primary.getCoursePackage().getId());
            List<Integer> percents = new ArrayList<>();
            LocalDateTime lastActive = null;
            for (ClassEnrolment enrolment : entry.getValue()) {
                ClassSnapshot classSnapshot = snapshots.get(enrolment.getCoursePackage().getId());
                percents.addAll(classSnapshot.attemptPercents().getOrDefault(student.getId(), List.of()));
                lastActive = ClassContentSupport.latest(lastActive,
                        ClassContentSupport.latest(classSnapshot.lastActive().get(student.getId()), enrolment.getJoinedAt()));
            }
            Integer quizAverage = ClassProgressService.average(percents);
            rows.add(new ClassStudentViews.RosterRow(
                    student.getId(),
                    student.getUser().getDisplayName(),
                    ClassLiveSessionService.xpLevel(student),
                    snapshot.percent(student.getId()),
                    quizAverage,
                    needsSupport(quizAverage, lastActive),
                    lastActive));
        }
        return rows;
    }

    /** A quiz average under 60 % or no activity for 7 days. */
    private static boolean needsSupport(Integer quizAverage, LocalDateTime lastActive) {
        return (quizAverage != null && quizAverage < SUPPORT_QUIZ_AVERAGE)
                || lastActive == null
                || lastActive.isBefore(LocalDateTime.now().minusDays(INACTIVE_DAYS));
    }

    /** Progress of a class's students, computed once for all of them. */
    private record ClassSnapshot(
            List<Lesson> lessons,
            Map<Long, Map<Long, ClassProgressService.LessonState>> states,
            Map<Long, List<Integer>> attemptPercents,
            Map<Long, LocalDateTime> lastActive) {

        int percent(Long studentId) {
            int completed = (int) states.getOrDefault(studentId, Map.of()).values().stream()
                    .filter(ClassProgressService.LessonState::completed)
                    .count();
            return ClassProgressService.classPercent(completed, lessons.size());
        }
    }

    private ClassSnapshot snapshot(CoursePackage coursePackage, List<Long> studentIds) {
        List<Lesson> lessons = content.lessonsInOrder(coursePackage, false).stream().filter(ClassContentSupport::isLessonLive).toList();
        Map<Long, Map<Long, ClassProgressService.LessonState>> states =
                progress.lessonStates(studentIds, lessons, progress.liveParts(lessons));
        List<Long> allLessonIds = content.lessonsInOrder(coursePackage, true).stream().map(Lesson::getId).toList();
        Map<Long, List<Integer>> percents = new HashMap<>();
        Map<Long, LocalDateTime> lastActive = new HashMap<>();
        if (!allLessonIds.isEmpty()) {
            for (QuizAttempt attempt : quizAttemptRepository
                    .findByStudentIdInAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(studentIds, allLessonIds)) {
                Long studentId = attempt.getStudent().getId();
                percents.computeIfAbsent(studentId, ignored -> new ArrayList<>()).add(ClassProgressService.attemptPercent(attempt));
                noteActivity(lastActive, studentId, attempt.getCompletedAt());
            }
        }
        // A lesson the student has not opened yet has no activity time.
        states.forEach((studentId, lessonStates) -> lessonStates.values().forEach(state ->
                noteActivity(lastActive, studentId, state.lastActivityAt())));
        return new ClassSnapshot(lessons, states, percents, lastActive);
    }

    private static void noteActivity(Map<Long, LocalDateTime> lastActive, Long studentId, LocalDateTime at) {
        if (at != null) {
            lastActive.merge(studentId, at, ClassContentSupport::latest);
        }
    }

    @Transactional(readOnly = true)
    public ClassStudentViews.StudentDetail detail(SecurityUserDetails currentUser, Long studentId, Long classId) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        List<ClassEnrolment> enrolments = enrolmentRepository.findByCoursePackageTeacherIdAndStudentId(teacher.getId(), studentId)
                .stream()
                .sorted(Comparator.comparing(ClassEnrolment::getJoinedAt).reversed())
                .toList();
        if (enrolments.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Student was not found in your classes.");
        }
        ClassEnrolment selected = enrolments.stream()
                .filter(enrolment -> classId == null || enrolment.getCoursePackage().getId().equals(classId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student was not found in your classes."));
        StudentProfile student = selected.getStudent();
        CoursePackage coursePackage = selected.getCoursePackage();
        ClassSnapshot snapshot = snapshot(coursePackage, List.of(studentId));
        Map<Long, ClassProgressService.LessonState> states = snapshot.states().getOrDefault(studentId, Map.of());
        List<ClassStudentViews.LessonProgress> lessons = new ArrayList<>();
        int completed = 0;
        int mastered = 0;
        for (Lesson lesson : snapshot.lessons()) {
            ClassProgressService.LessonState state = states.get(lesson.getId());
            if (state != null && state.completed()) {
                completed++;
            }
            if (state != null && "MASTERED".equals(ClassProgressService.status(state))) {
                mastered++;
            }
            if (state != null && state.started()) {
                lessons.add(new ClassStudentViews.LessonProgress(
                        lesson.getId(),
                        lesson.getTitle(),
                        ClassProgressService.status(state),
                        state.percent(),
                        state.hasVideo() ? state.videoWatched() : null,
                        state.bestQuizPercent()));
            }
        }
        List<Long> lessonIds = content.lessonsInOrder(coursePackage, true).stream().map(Lesson::getId).toList();
        List<QuizAttempt> attempts = lessonIds.isEmpty() ? List.of() : quizAttemptRepository
                .findByStudentIdAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(studentId, lessonIds);
        List<ClassViews.AttemptRow> results = attempts.stream().map(ClassQuizService::attemptRow).toList();
        Integer quizAverage = ClassProgressService.average(results.stream().map(ClassViews.AttemptRow::percent).toList());
        LocalDateTime lastActive = ClassContentSupport.latest(snapshot.lastActive().get(studentId), selected.getJoinedAt());
        List<ClassStudentViews.FeedbackTarget> targets = new ArrayList<>();
        for (QuizAttempt attempt : attempts) {
            targets.add(new ClassStudentViews.FeedbackTarget("QUIZ_ATTEMPT", attempt.getId(), attempt.getQuiz().getTitle(),
                    LessonMasteryCalculator.attemptPercentage(attempt), attempt.getCompletedAt()));
        }
        for (Lesson lesson : snapshot.lessons()) {
            targets.add(new ClassStudentViews.FeedbackTarget("LESSON", lesson.getId(), lesson.getTitle(), null, null));
        }
        return new ClassStudentViews.StudentDetail(
                studentId,
                student.getUser().getDisplayName(),
                ClassLiveSessionService.xpLevel(student),
                enrolments.stream().map(enrolment -> new ClassStudentViews.ClassRef(
                        enrolment.getCoursePackage().getId(), enrolment.getCoursePackage().getTitle())).toList(),
                coursePackage.getId(),
                coursePackage.getTitle(),
                lastActive,
                needsSupport(quizAverage, lastActive),
                snapshot.percent(studentId),
                completed,
                snapshot.lessons().size(),
                mastered,
                quizAverage,
                lessons,
                results,
                weakAreas(attempts),
                feedbackRepository.findByTeacherIdAndStudentIdOrderByCreatedAtDescIdDesc(teacher.getId(), studentId).stream()
                        .filter(feedback -> feedback.getCoursePackage() != null
                                && feedback.getCoursePackage().getId().equals(coursePackage.getId()))
                        .map(ClassFeedbackService::row)
                        .toList(),
                targets);
    }

    /** Question skills within a quiz where fewer than 60 % of the student's answers were right (at least 2 answers). */
    private List<ClassStudentViews.WeakArea> weakAreas(List<QuizAttempt> attempts) {
        if (attempts.isEmpty()) {
            return List.of();
        }
        Map<Long, QuizAttempt> attemptById = attempts.stream().collect(Collectors.toMap(QuizAttempt::getId, attempt -> attempt));
        record Key(Long quizId, String skill) {
        }
        Map<Key, List<QuizAttemptAnswer>> groups = new LinkedHashMap<>();
        for (QuizAttemptAnswer answer : answerRepository.findByQuizAttemptIdIn(attemptById.keySet())) {
            QuizAttempt attempt = attemptById.get(answer.getQuizAttempt().getId());
            groups.computeIfAbsent(new Key(attempt.getQuiz().getId(), ClassQuizService.skill(answer.getQuestion())),
                    ignored -> new ArrayList<>()).add(answer);
        }
        List<ClassStudentViews.WeakArea> areas = new ArrayList<>();
        groups.forEach((key, answers) -> {
            int correct = (int) answers.stream().filter(QuizAttemptAnswer::isCorrect).count();
            int total = answers.size();
            int percent = correct * 100 / total;
            if (total < 2 || percent >= WEAK_AREA_PERCENT) {
                return;
            }
            QuizAttempt sample = attemptById.get(answers.get(0).getQuizAttempt().getId());
            LocalDateTime lastSeen = answers.stream()
                    .map(answer -> attemptById.get(answer.getQuizAttempt().getId()).getCompletedAt())
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            Set<String> examples = new LinkedHashSet<>();
            answers.stream().filter(answer -> !answer.isCorrect()).forEach(answer -> {
                Matcher matcher = HAN.matcher(answer.getQuestion().getQuestionText() + " "
                        + Objects.requireNonNullElse(answer.getQuestion().getCorrectAnswerText(), ""));
                while (matcher.find() && examples.size() < 3) {
                    examples.add(matcher.group());
                }
            });
            areas.add(new ClassStudentViews.WeakArea(
                    key.skill(),
                    key.quizId(),
                    sample.getQuiz().getTitle(),
                    correct,
                    total,
                    percent,
                    lastSeen,
                    List.copyOf(examples)));
        });
        areas.sort(Comparator.comparingInt(ClassStudentViews.WeakArea::percent));
        return areas;
    }
}
