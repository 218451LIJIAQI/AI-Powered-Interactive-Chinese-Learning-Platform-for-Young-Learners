package com.example.aichinese.mapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.learning.QuizOptionResponse;
import com.example.aichinese.dto.learning.StudentQuizQuestionResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.StudentQuizSummaryResponse;
import com.example.aichinese.dto.learning.TeacherQuizOptionResponse;
import com.example.aichinese.dto.learning.TeacherQuizQuestionResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;

/**
 * Entity-to-DTO translation for quiz objects.
 *
 * <p>The mapper emits two distinct response shapes from the same
 * underlying entity graph:</p>
 * <ul>
 *   <li><b>Teacher view</b> ({@link TeacherQuizResponse},
 *       {@link TeacherQuizQuestionResponse},
 *       {@link TeacherQuizOptionResponse}) includes the correct-answer
 *       text, the per-option correctness flag and the explanation — all
 *       fields a teacher needs to author or grade questions.</li>
 *   <li><b>Student view</b> ({@link StudentQuizResponse},
 *       {@link StudentQuizQuestionResponse},
 *       {@link QuizOptionResponse}) deliberately omits every
 *       correctness-leak field, so a student cannot inspect the
 *       network response to skip the assessment. This is a security
 *       boundary: the student-facing service path must always invoke
 *       the {@code toStudent*} methods on this class, never the
 *       teacher-facing ones.</li>
 * </ul>
 *
 * <p>Options are joined to questions via a pre-computed
 * {@code Map<questionId, List<QuizOption>>} (see
 * {@link #groupOptionsByQuestionId(List)}) so a single batched fetch
 * feeds N questions instead of triggering one option SELECT per
 * question. The service layer queries options through repository
 * methods that already {@code ORDER BY sort_order, created_at}, so
 * {@link Collectors#groupingBy(java.util.function.Function)} — which
 * preserves encounter order within each value list — yields
 * stably-sorted option lists without an extra in-memory sort.</p>
 */
@Component
public class QuizMapper {

    public TeacherQuizResponse toTeacherQuizResponse(
            Quiz quiz,
            List<QuizQuestion> questions,
            Map<Long, List<QuizOption>> optionsByQuestionId) {
        Objects.requireNonNull(quiz, "quiz");
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(optionsByQuestionId, "optionsByQuestionId");
        Lesson lesson = quiz.getLesson();
        return new TeacherQuizResponse(
                quiz.getId(),
                lesson == null ? null : lesson.getId(),
                lesson == null ? null : lesson.getTitle(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getContentStatus(),
                quiz.getTimeLimitMinutes(),
                quiz.getTotalPoints(),
                questions.stream()
                        .map(question -> toTeacherQuizQuestionResponse(
                                question,
                                optionsByQuestionId.getOrDefault(question.getId(), List.of())))
                        .toList(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt());
    }

    public TeacherQuizQuestionResponse toTeacherQuizQuestionResponse(QuizQuestion question, List<QuizOption> options) {
        Objects.requireNonNull(question, "question");
        List<QuizOption> safeOptions = options == null ? List.of() : options;
        return new TeacherQuizQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getCorrectAnswerText(),
                question.getPoints(),
                question.getSortOrder(),
                question.getExplanation(),
                safeOptions.stream().map(QuizMapper::toTeacherQuizOption).toList());
    }

    public StudentQuizResponse toStudentQuizResponse(
            Quiz quiz,
            List<QuizQuestion> questions,
            Map<Long, List<QuizOption>> optionsByQuestionId) {
        Objects.requireNonNull(quiz, "quiz");
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(optionsByQuestionId, "optionsByQuestionId");
        Lesson lesson = quiz.getLesson();
        return new StudentQuizResponse(
                quiz.getId(),
                lesson == null ? null : lesson.getId(),
                lesson == null ? null : lesson.getTitle(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTimeLimitMinutes(),
                quiz.getTotalPoints(),
                questions.stream()
                        .map(question -> toStudentQuizQuestionResponse(
                                question,
                                optionsByQuestionId.getOrDefault(question.getId(), List.of())))
                        .toList());
    }

    public StudentQuizQuestionResponse toStudentQuizQuestionResponse(QuizQuestion question, List<QuizOption> options) {
        Objects.requireNonNull(question, "question");
        List<QuizOption> safeOptions = options == null ? List.of() : options;
        return new StudentQuizQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getPoints(),
                question.getSortOrder(),
                safeOptions.stream().map(QuizMapper::toStudentQuizOption).toList());
    }

    public StudentQuizSummaryResponse toStudentQuizSummaryResponse(Quiz quiz, int questionCount) {
        Objects.requireNonNull(quiz, "quiz");
        Lesson lesson = quiz.getLesson();
        return new StudentQuizSummaryResponse(
                quiz.getId(),
                lesson == null ? null : lesson.getId(),
                lesson == null ? null : lesson.getTitle(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTimeLimitMinutes(),
                quiz.getTotalPoints(),
                questionCount);
    }

    /**
     * Indexes a flat list of {@link QuizOption} rows by their owning
     * question id so subsequent question-level mapping calls can fetch
     * their options in O(1) without re-scanning the list.
     *
     * <p>Options whose owning {@link QuizQuestion} is unexpectedly
     * {@code null} (e.g. partial test fixtures) are skipped rather than
     * triggering a {@link NullPointerException} from the grouping key
     * extractor — the caller can detect orphans by comparing input and
     * grouped sizes if strict validation is required.</p>
     */
    public Map<Long, List<QuizOption>> groupOptionsByQuestionId(List<QuizOption> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        return options.stream()
                .filter(option -> option.getQuestion() != null && option.getQuestion().getId() != null)
                .collect(Collectors.groupingBy(
                        option -> option.getQuestion().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private static TeacherQuizOptionResponse toTeacherQuizOption(QuizOption option) {
        return new TeacherQuizOptionResponse(
                option.getId(),
                option.getOptionText(),
                option.isCorrectOption(),
                option.getSortOrder());
    }

    private static QuizOptionResponse toStudentQuizOption(QuizOption option) {
        return new QuizOptionResponse(
                option.getId(),
                option.getOptionText(),
                option.getSortOrder());
    }
}
