package com.example.aichinese.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.learning.FlashcardOptionResponse;
import com.example.aichinese.dto.learning.ListeningExercisePayload;
import com.example.aichinese.dto.learning.ListeningExercisePlayResponse;
import com.example.aichinese.dto.learning.ListeningExerciseResponse;
import com.example.aichinese.dto.learning.ListeningOptionResponse;
import com.example.aichinese.dto.learning.MatchingGameItemResponse;
import com.example.aichinese.dto.learning.MatchingGamePlayResponse;
import com.example.aichinese.dto.learning.MatchingGameResponse;
import com.example.aichinese.dto.learning.QuizOptionResponse;
import com.example.aichinese.dto.learning.StudentQuizQuestionResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.TeacherListeningOptionResponse;
import com.example.aichinese.dto.learning.TeacherQuizOptionResponse;
import com.example.aichinese.dto.learning.TeacherQuizQuestionResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.dto.learning.TracingExercisePlayResponse;
import com.example.aichinese.dto.learning.TracingExerciseResponse;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.TracingExercise;

@Component
public class LearningActivityMapper {

    public FlashcardOptionResponse toFlashcardResponse(Flashcard flashcard) {
        return new FlashcardOptionResponse(
                flashcard.getId(),
                flashcard.getFrontText(),
                flashcard.getPinyin(),
                flashcard.getEnglishMeaning(),
                flashcard.getMalayMeaning(),
                flashcard.getImageUrl(),
                flashcard.getAudioUrl(),
                flashcard.getSortOrder());
    }

    public TeacherQuizResponse toTeacherQuizResponse(Quiz quiz, List<QuizQuestion> questions, Map<Long, List<QuizOption>> optionsByQuestionId) {
        return new TeacherQuizResponse(
                quiz.getId(),
                quiz.getLesson() != null ? quiz.getLesson().getId() : null,
                quiz.getLesson() != null ? quiz.getLesson().getTitle() : null,
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
        return new TeacherQuizQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getCorrectAnswerText(),
                question.getPoints(),
                question.getSortOrder(),
                question.getExplanation(),
                options.stream()
                        .map(option -> new TeacherQuizOptionResponse(
                                option.getId(),
                                option.getOptionText(),
                                option.isCorrectOption(),
                                option.getSortOrder()))
                        .toList());
    }

    public StudentQuizResponse toStudentQuizResponse(Quiz quiz, List<QuizQuestion> questions, Map<Long, List<QuizOption>> optionsByQuestionId) {
        return new StudentQuizResponse(
                quiz.getId(),
                quiz.getLesson() != null ? quiz.getLesson().getId() : null,
                quiz.getLesson() != null ? quiz.getLesson().getTitle() : null,
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
        return new StudentQuizQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getPoints(),
                question.getSortOrder(),
                options.stream()
                        .map(option -> new QuizOptionResponse(
                                option.getId(),
                                option.getOptionText(),
                                option.getSortOrder()))
                        .toList());
    }

    public MatchingGameResponse toMatchingGameResponse(MatchingGame game, List<MatchingGameItem> items) {
        return new MatchingGameResponse(
                game.getId(),
                game.getLesson() != null ? game.getLesson().getId() : null,
                game.getLesson() != null ? game.getLesson().getTitle() : null,
                game.getTitle(),
                game.getContentStatus(),
                items.stream().map(this::toMatchingGameItemResponse).toList(),
                game.getCreatedAt(),
                game.getUpdatedAt());
    }

    public MatchingGamePlayResponse toMatchingGamePlayResponse(MatchingGame game, List<MatchingGameItem> items) {
        return new MatchingGamePlayResponse(
                game.getId(),
                game.getLesson() != null ? game.getLesson().getId() : null,
                game.getTitle(),
                items.stream().map(this::toMatchingGameItemResponse).toList());
    }

    public MatchingGameItemResponse toMatchingGameItemResponse(MatchingGameItem item) {
        return new MatchingGameItemResponse(
                item.getId(),
                item.getLeftText(),
                item.getRightText(),
                item.getLeftImageUrl(),
                item.getRightImageUrl(),
                item.getPinyin(),
                item.getSortOrder());
    }

    public ListeningExerciseResponse toListeningExerciseResponse(ListeningExercise exercise, ListeningExercisePayload payload) {
        return new ListeningExerciseResponse(
                exercise.getId(),
                exercise.getLesson() != null ? exercise.getLesson().getId() : null,
                exercise.getLesson() != null ? exercise.getLesson().getTitle() : null,
                exercise.getTitle(),
                payload.chineseText(),
                payload.pinyin(),
                exercise.getAudioUrl(),
                exercise.getContentStatus(),
                payload.options().stream()
                        .map(option -> new TeacherListeningOptionResponse(
                                option.optionText(),
                                option.correctOption(),
                                option.displayOrder()))
                        .toList(),
                payload.explanation(),
                exercise.getCreatedAt(),
                exercise.getUpdatedAt());
    }

    public ListeningExercisePlayResponse toListeningExercisePlayResponse(ListeningExercise exercise, ListeningExercisePayload payload) {
        return new ListeningExercisePlayResponse(
                exercise.getId(),
                exercise.getLesson() != null ? exercise.getLesson().getId() : null,
                exercise.getTitle(),
                payload.chineseText(),
                payload.pinyin(),
                exercise.getAudioUrl(),
                payload.options().stream()
                        .map(option -> new ListeningOptionResponse(option.optionText(), option.displayOrder()))
                        .toList());
    }

    public TracingExerciseResponse toTracingExerciseResponse(TracingExercise exercise) {
        return new TracingExerciseResponse(
                exercise.getId(),
                exercise.getLesson() != null ? exercise.getLesson().getId() : null,
                exercise.getLesson() != null ? exercise.getLesson().getTitle() : null,
                exercise.getTitle(),
                exercise.getTargetCharacter(),
                exercise.getPinyin(),
                exercise.getMeaning(),
                exercise.getWorksheetUrl(),
                exercise.getInstructions(),
                exercise.getContentStatus(),
                exercise.getCreatedAt(),
                exercise.getUpdatedAt());
    }

    public TracingExercisePlayResponse toTracingExercisePlayResponse(TracingExercise exercise) {
        return new TracingExercisePlayResponse(
                exercise.getId(),
                exercise.getLesson() != null ? exercise.getLesson().getId() : null,
                exercise.getTitle(),
                exercise.getTargetCharacter(),
                exercise.getPinyin(),
                exercise.getMeaning(),
                exercise.getWorksheetUrl(),
                exercise.getInstructions());
    }

    public Map<Long, List<QuizOption>> groupOptionsByQuestionId(List<QuizOption> options) {
        return options.stream().collect(Collectors.groupingBy(option -> option.getQuestion().getId()));
    }
}
