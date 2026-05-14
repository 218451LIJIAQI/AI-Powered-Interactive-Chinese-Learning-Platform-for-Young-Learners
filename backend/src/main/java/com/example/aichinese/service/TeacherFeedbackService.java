package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.communication.CreateTeacherFeedbackRequest;
import com.example.aichinese.dto.communication.FeedbackCandidateResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.dto.communication.UpdateTeacherFeedbackRequest;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.TeacherFeedbackType;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class TeacherFeedbackService {

    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final UserRepository userRepository;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public TeacherFeedbackService(
            TeacherFeedbackRepository teacherFeedbackRepository,
            TeacherProfileRepository teacherProfileRepository,
            StudentProfileRepository studentProfileRepository,
            QuizAttemptRepository quizAttemptRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository lessonProgressRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            UserRepository userRepository,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.teacherFeedbackRepository = teacherFeedbackRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.lessonRepository = lessonRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.liveClassEnrolmentRepository = liveClassEnrolmentRepository;
        this.userRepository = userRepository;
        this.communicationMapper = communicationMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TeacherFeedbackResponse createFeedback(SecurityUserDetails currentUser, CreateTeacherFeedbackRequest request) {
        TeacherProfile teacher = requireTeacher(currentUser.getUserId());
        StudentProfile student = requireStudent(request.studentId());
        Lesson lesson = request.lessonId() != null ? requireTeacherLesson(request.lessonId(), currentUser.getUserId()) : null;
        QuizAttempt quizAttempt = request.quizAttemptId() != null ? requireTeacherQuizAttempt(request.quizAttemptId(), currentUser.getUserId()) : null;

        validateRelationship(student, currentUser.getUserId(), lesson, quizAttempt);

        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(student);
        feedback.setLesson(lesson != null ? lesson : quizAttempt != null && quizAttempt.getQuiz() != null ? quizAttempt.getQuiz().getLesson() : null);
        feedback.setQuizAttempt(quizAttempt);
        feedback.setFeedbackType(parseFeedbackType(request.feedbackType()).name());
        feedback.setFeedbackMessage(request.message().trim());
        TeacherFeedback saved = teacherFeedbackRepository.save(feedback);

        notificationService.safeCreateNotification(
                student.getUser(),
                NotificationType.TEACHER_FEEDBACK,
                "New teacher feedback",
                "Your teacher has shared new learning feedback.",
                "TEACHER_FEEDBACK",
                saved.getId(),
                "/student/feedback",
                teacher.getUser());

        auditLogService.safeRecord(
                teacher.getUser(),
                "TEACHER_FEEDBACK_CREATED",
                "TEACHER_FEEDBACK",
                saved.getId(),
                "Teacher created feedback for student " + student.getUser().getDisplayName() + ".",
                null);

        return communicationMapper.toTeacherFeedbackResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TeacherFeedbackResponse> getTeacherFeedback(SecurityUserDetails currentUser) {
        return teacherFeedbackRepository.findByTeacherUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(communicationMapper::toTeacherFeedbackResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherFeedbackResponse> getStudentFeedback(SecurityUserDetails currentUser) {
        return teacherFeedbackRepository.findByStudentUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(communicationMapper::toTeacherFeedbackResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherFeedbackResponse getStudentFeedbackById(SecurityUserDetails currentUser, Long feedbackId) {
        TeacherFeedback feedback = teacherFeedbackRepository.findByIdAndStudentUserId(feedbackId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Feedback was not found."));
        return communicationMapper.toTeacherFeedbackResponse(feedback);
    }

    @Transactional
    public TeacherFeedbackResponse updateFeedback(SecurityUserDetails currentUser, Long feedbackId, UpdateTeacherFeedbackRequest request) {
        TeacherFeedback feedback = teacherFeedbackRepository.findByIdAndTeacherUserId(feedbackId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Feedback was not found."));
        feedback.setFeedbackType(parseFeedbackType(request.feedbackType()).name());
        feedback.setFeedbackMessage(request.message().trim());
        // Audit-log the edit so that admins reviewing feedback have a record
        // of changes (the create path already audit-logs; this path was a
        // gap that broke the "every teacher mutation is auditable"
        // invariant the rest of the codebase enforces).
        auditLogService.safeRecord(
                feedback.getTeacher() != null ? feedback.getTeacher().getUser() : null,
                "TEACHER_FEEDBACK_UPDATED",
                "TEACHER_FEEDBACK",
                feedback.getId(),
                "Teacher updated feedback content.",
                null);
        return communicationMapper.toTeacherFeedbackResponse(feedback);
    }

    @Transactional(readOnly = true)
    public List<TeacherFeedbackResponse> getAdminFeedback() {
        // Database-side ordering keeps the admin feedback list O(N) in SQL
        // and O(N) once in JVM, rather than O(N) twice (load + sort).
        return teacherFeedbackRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(communicationMapper::toTeacherFeedbackResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackCandidateResponse> getTeacherFeedbackCandidates(SecurityUserDetails currentUser) {
        Long teacherUserId = currentUser.getUserId();
        Long teacherId = requireTeacher(teacherUserId).getId();
        Map<String, FeedbackCandidateResponse> candidates = new LinkedHashMap<>();

        quizAttemptRepository.findTop5ByQuizTeacherIdOrderByCreatedAtDesc(teacherId).forEach(attempt -> {
            if (attempt.getStudent() == null || attempt.getStudent().getUser() == null) {
                return;
            }
            FeedbackCandidateResponse candidate = new FeedbackCandidateResponse(
                    attempt.getStudent().getId(),
                    attempt.getStudent().getUser().getDisplayName(),
                    attempt.getStudent().getUser().getEmail(),
                    attempt.getQuiz() != null && attempt.getQuiz().getLesson() != null ? attempt.getQuiz().getLesson().getId() : null,
                    attempt.getQuiz() != null && attempt.getQuiz().getLesson() != null ? attempt.getQuiz().getLesson().getTitle() : null,
                    attempt.getId(),
                    attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : null);
            candidates.putIfAbsent(candidateKey(candidate), candidate);
        });

        List<FeedbackCandidateResponse> progressCandidates = new ArrayList<>();
        // Fetch only the students linked to this teacher in a single SQL
        // statement. The previous implementation called
        // {@code studentProfileRepository.findAll(...)} (loading every
        // student row in the database) and then ran three {@code exists}
        // probes per student to filter — a 1 + 3N query pattern that
        // scaled with the platform-wide student count even though the
        // candidate list per teacher is typically small.
        for (StudentProfile student : studentProfileRepository.findStudentsLinkedToTeacher(teacherUserId)) {
            if (student.getUser() == null) {
                continue;
            }
            progressCandidates.add(new FeedbackCandidateResponse(
                    student.getId(),
                    student.getUser().getDisplayName(),
                    student.getUser().getEmail(),
                    null,
                    null,
                    null,
                    null));
        }

        progressCandidates.stream()
                .sorted(Comparator.comparing(FeedbackCandidateResponse::studentId))
                .forEach(candidate -> candidates.putIfAbsent(candidateKey(candidate), candidate));

        return candidates.values().stream().limit(25).toList();
    }

    private void validateRelationship(StudentProfile student, Long teacherUserId, Lesson lesson, QuizAttempt quizAttempt) {
        boolean allowed = quizAttempt != null
                ? quizAttempt.getStudent().getId().equals(student.getId())
                : lesson != null
                        ? lessonProgressRepository.existsByStudentIdAndLessonTeacherUserId(student.getId(), teacherUserId)
                        : lessonProgressRepository.existsByStudentIdAndLessonTeacherUserId(student.getId(), teacherUserId)
                                || liveClassEnrolmentRepository.existsByStudentIdAndLiveClassTeacherUserId(student.getId(), teacherUserId)
                                || quizAttemptRepository.existsByStudentIdAndQuizTeacherUserId(student.getId(), teacherUserId);
        if (!allowed) {
            throw new ResponseStatusException(BAD_REQUEST, "Feedback can only be created for students linked to your content.");
        }
    }

    private TeacherProfile requireTeacher(Long teacherUserId) {
        return teacherProfileRepository.findDetailedByUserId(teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
    }

    private StudentProfile requireStudent(Long studentId) {
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    private Lesson requireTeacherLesson(Long lessonId, Long teacherUserId) {
        return lessonRepository.findByIdAndTeacherUserId(lessonId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lesson was not found."));
    }

    private QuizAttempt requireTeacherQuizAttempt(Long attemptId, Long teacherUserId) {
        return quizAttemptRepository.findByIdAndQuizTeacherUserId(attemptId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz attempt was not found."));
    }

    private TeacherFeedbackType parseFeedbackType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return TeacherFeedbackType.GENERAL;
        }
        try {
            return TeacherFeedbackType.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid feedback type.");
        }
    }

    private String candidateKey(FeedbackCandidateResponse candidate) {
        return candidate.studentId()
                + ":"
                + (candidate.lessonId() != null ? candidate.lessonId() : "lesson")
                + ":"
                + (candidate.quizAttemptId() != null ? candidate.quizAttemptId() : "quiz");
    }
}
