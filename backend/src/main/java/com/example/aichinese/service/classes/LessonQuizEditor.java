package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.QuizOptionRequest;
import com.example.aichinese.dto.classes.QuizQuestionRequest;
import com.example.aichinese.dto.classes.QuizRequest;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.service.ContentSafetyService;

import lombok.RequiredArgsConstructor;

/**
 * Authoring mutations for a lesson's quiz and its 5 questions. Callers hold the enclosing class lock and supply the
 * lesson and its editable working copy.
 */
@Component
@RequiredArgsConstructor
class LessonQuizEditor {

    private static final String QUESTION_NOT_FOUND = "Requested quiz question was not found.";
    private static final String QUIZ_FULL = "A lesson quiz has exactly 5 questions. Delete one to add another.";
    private static final String QUESTIONS_LOCKED = "Quiz questions cannot be changed after learners have submitted attempts.";

    private final ClassContentSupport content;
    private final LessonRevisionDiff diff;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final ContentSafetyService safety;

    void requireNoQuiz(Lesson lesson) {
        if (quizRepository.existsByLessonId(lesson.getId())) {
            throw new ResponseStatusException(CONFLICT, "This lesson already has a quiz.");
        }
    }

    String validateQuiz(QuizRequest request) {
        return safety.requireSafeText(request.title(), 200, "Quiz title is required.");
    }

    void addQuiz(Lesson working, String safeTitle, QuizRequest request) {
        Quiz quiz = new Quiz();
        quiz.setLesson(working);
        quiz.setTitle(safeTitle);
        quiz.setHintsAllowed(Boolean.TRUE.equals(request.hintsAllowed()));
        quiz.setContentStatus(ContentStatus.DRAFT);
        quizRepository.save(quiz);
    }

    void updateQuiz(Quiz quiz, ClassRequests.QuizSettings request) {
        quiz.setHintsAllowed(request.hintsAllowed());
    }

    Quiz requireCanAddQuestion(Lesson lesson, Long quizId) {
        Quiz quiz = requireQuiz(lesson, quizId);
        requireQuestionsEditable(quiz);
        if (quizQuestionRepository.countByQuizId(quiz.getId()) >= LessonEditorService.QUIZ_QUESTIONS) {
            throw new ResponseStatusException(BAD_REQUEST, QUIZ_FULL);
        }
        return quiz;
    }

    QuizQuestion requireCanEditQuestion(Lesson lesson, Lesson current, Long questionId) {
        return resolveQuestion(lesson, current, questionId, true);
    }

    QuizQuestion resolveQuestionInRevision(Lesson lesson, Lesson working, Long questionId) {
        return resolveQuestion(lesson, working, questionId, false);
    }

    boolean isUnchanged(QuizQuestion existing, ValidatedQuestion validated) {
        if (existing.isAiDraft()) {
            return false;
        }
        List<QuizOption> existingOptions = quizOptionRepository.findInDisplayOrder(List.of(existing.getId()));
        LessonRevisionDiff.QuestionContent incoming = new LessonRevisionDiff.QuestionContent(
                validated.type(), validated.questionText(), validated.correctAnswerText(),
                validated.explanation(), validated.hint(), validated.points(),
                validated.audioUrl(), validated.imageUrl(), false,
                validated.options().stream()
                        .map(option -> new LessonRevisionDiff.OptionContent(
                                option.optionText(), option.correctOption(), option.feedback()))
                        .toList());
        return LessonRevisionDiff.questionContent(existing, existingOptions).equals(incoming);
    }

    void addQuestion(Quiz quiz, QuizQuestionRequest request, ValidatedQuestion validated) {
        List<QuizQuestion> existing = content.questions(quiz.getId());
        int index = request.position() == null ? existing.size() : Math.clamp(request.position() - 1L, 0, existing.size());
        // Number the questions 1, 2, ... leaving the new question's place free.
        for (int i = 0; i < existing.size(); i++) {
            existing.get(i).setSortOrder(i < index ? i + 1 : i + 2);
        }
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setSortOrder(index + 1);
        question.setAiDraft(Boolean.TRUE.equals(request.aiDraft()));
        applyQuestion(question, validated, true);
    }

    void updateQuestion(QuizQuestion question, ValidatedQuestion validated) {
        question.setAiDraft(false);
        applyQuestion(question, validated, false);
    }

    void keepQuestion(QuizQuestion question) {
        question.setAiDraft(false);
    }

    void deleteQuestion(QuizQuestion question) {
        deleteOptions(question);
        quizQuestionRepository.delete(question);
        quizQuestionRepository.flush();
    }

    int openQuestionSlots(Lesson shown) {
        List<Quiz> quizzes = content.quizzes(shown.getId());
        if (quizzes.isEmpty()) {
            return LessonEditorService.QUIZ_QUESTIONS;
        }
        Quiz quiz = quizzes.get(0);
        requireQuestionsEditable(quiz);
        long count = quizQuestionRepository.countByQuizId(quiz.getId());
        if (count >= LessonEditorService.QUIZ_QUESTIONS) {
            throw new ResponseStatusException(BAD_REQUEST, QUIZ_FULL);
        }
        return LessonEditorService.QUIZ_QUESTIONS - (int) count;
    }

    /** A quiz of the working copy, or the copy of a published one the editor addressed before. */
    Quiz requireQuiz(Lesson working, Long quizId) {
        return content.quizzes(working.getId()).stream()
                .filter(quiz -> quizId.equals(quiz.getId()) || quizId.equals(quiz.getRevisionSourceId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz was not found."));
    }

    /** Published question ids map by position only while the copied questions are still unchanged. */
    private QuizQuestion resolveQuestion(Lesson lesson, Lesson working, Long questionId, boolean checkEditable) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUESTION_NOT_FOUND));
        Quiz quiz = question.getQuiz();
        if (quiz.getLesson().getId().equals(working.getId())) {
            if (checkEditable) {
                requireQuestionsEditable(quiz);
            }
            return question;
        }
        if (working.getRevisionOf() == null || !quiz.getLesson().getId().equals(lesson.getId())) {
            throw new ResponseStatusException(NOT_FOUND, QUESTION_NOT_FOUND);
        }
        if (checkEditable) {
            requireQuestionsEditable(quiz);
        }
        Quiz copy = requireQuiz(working, quiz.getId());
        List<QuizQuestion> publishedQuestions = content.questions(quiz.getId());
        List<QuizQuestion> revisedQuestions = content.questions(copy.getId());
        if (!diff.sameQuestions(publishedQuestions, revisedQuestions)) {
            throw new ResponseStatusException(CONFLICT, "This quiz has changed. Reload the lesson before editing its questions.");
        }
        // The same questions in the same order: the copy at the question's position.
        return revisedQuestions.get(publishedQuestions.indexOf(question));
    }

    private void requireQuestionsEditable(Quiz quiz) {
        if (diff.questionsLocked(quiz)) {
            throw new ResponseStatusException(BAD_REQUEST, QUESTIONS_LOCKED);
        }
    }

    ValidatedQuestion validateQuestion(QuizQuestionRequest request) {
        QuizQuestionType type = EnumLookups.tryFrom(QuizQuestionType.class, request.questionType())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, QuizQuestionType.INVALID_TYPE_MESSAGE));
        String questionText = safety.requireSafeText(request.questionText(), 2000, "Question text is required.");
        String explanation = safety.optionalSafeText(request.explanation(), 2000);
        String hint = safety.optionalSafeText(request.hint(), 1000);
        String audioUrl = type.needsAudio()
                ? requireMediaLink(request.audioUrl(), "A listen-and-choose question needs an audio link.", "Audio")
                : null;
        String imageUrl = type.needsImage()
                ? requireMediaLink(request.imageUrl(), "A word and image question needs an image link.", "Image")
                : null;
        List<ValidatedOption> options = new ArrayList<>();
        String correctAnswerText;
        if (type.isOptionBased()) {
            Set<String> labels = new HashSet<>();
            for (QuizOptionRequest input : request.options()) {
                String label = safety.requireSafeText(input.optionText(), 1000, input.correctOption()
                        ? "Correct answer option text is required." : "Option text is required.");
                if (!labels.add(label)) {
                    throw new ResponseStatusException(BAD_REQUEST, "The answers of a question must all be different.");
                }
                options.add(new ValidatedOption(label, input.correctOption(), safety.optionalSafeText(input.feedback(), 300)));
            }
            correctAnswerText = correctOptionText(type, options);
        } else {
            correctAnswerText = safety.requireSafeText(
                    request.correctAnswerText(), 1000, "Correct answer text is required for this question type.");
        }
        return new ValidatedQuestion(type, questionText, request.points(), explanation, hint, audioUrl, imageUrl,
                correctAnswerText, options);
    }

    private void applyQuestion(QuizQuestion question, ValidatedQuestion validated, boolean isNew) {
        question.setQuestionType(validated.type());
        question.setQuestionText(validated.questionText());
        question.setPoints(validated.points());
        question.setExplanation(validated.explanation());
        question.setHint(validated.hint());
        question.setAudioUrl(validated.audioUrl());
        question.setImageUrl(validated.imageUrl());
        question.setCorrectAnswerText(validated.correctAnswerText());
        List<QuizOption> options = new ArrayList<>(validated.options().size());
        for (ValidatedOption validatedOption : validated.options()) {
            QuizOption option = new QuizOption();
            option.setQuestion(question);
            option.setOptionText(validatedOption.optionText());
            option.setCorrectOption(validatedOption.correctOption());
            option.setFeedback(validatedOption.feedback());
            option.setSortOrder(options.size() + 1);
            options.add(option);
        }
        QuizQuestion saved = quizQuestionRepository.save(question);
        if (!isNew) {
            deleteOptions(saved);
        }
        if (!options.isEmpty()) {
            quizOptionRepository.saveAll(options);
        }
    }

    private void deleteOptions(QuizQuestion question) {
        quizOptionRepository.deleteAll(quizOptionRepository.findInDisplayOrder(List.of(question.getId())));
        // Delete the options before the question or before inserting its replacement options.
        quizOptionRepository.flush();
    }

    private static String correctOptionText(QuizQuestionType type, List<ValidatedOption> options) {
        if (type == QuizQuestionType.TRUE_FALSE && options.size() != 2) {
            throw new ResponseStatusException(BAD_REQUEST, "A true-or-false question needs exactly 2 answer options.");
        }
        if (options.size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "At least 2 answer options are required.");
        }
        List<ValidatedOption> correct = options.stream().filter(ValidatedOption::correctOption).toList();
        if (correct.size() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Exactly one correct answer option is required.");
        }
        return correct.getFirst().optionText();
    }

    record ValidatedQuestion(
            QuizQuestionType type,
            String questionText,
            int points,
            String explanation,
            String hint,
            String audioUrl,
            String imageUrl,
            String correctAnswerText,
            List<ValidatedOption> options) {
    }

    record ValidatedOption(String optionText, boolean correctOption, String feedback) {
    }

    /** The request stripped the link to null when blank. */
    private static String requireMediaLink(String link, String missingMessage, String mediaLabel) {
        if (link == null) {
            throw new ResponseStatusException(BAD_REQUEST, missingMessage);
        }
        if (!isWebLink(link)) {
            throw new ResponseStatusException(BAD_REQUEST, mediaLabel
                    + " link must be a full http:// or https:// web address without a user name or password.");
        }
        return link;
    }

    /**
     * A full http:// or https:// address with a host and no user name or password. Requests arrive stripped, and the URI
     * parser refuses whitespace, backslashes and control characters.
     */
    private static boolean isWebLink(String link) {
        try {
            URI uri = new URI(link);
            int port = uri.getPort();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && (port == -1 || (port >= 1 && port <= 65535));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
