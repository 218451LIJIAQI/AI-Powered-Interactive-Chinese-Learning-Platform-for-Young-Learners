package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.dto.classes.AdminClassViews;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * What the admin lesson editor shows: a version of the lesson (the lesson itself, or its working copy while it has
 * unpublished changes) with its parts, and the checklist of what publishing needs (Library · UI rules, Classes): a
 * video, key phrases, the 3 activities, a quiz of exactly 5 questions each with a correct answer and none of them an
 * unchecked AI draft, and pinyin for every new word.
 */
@Component
@RequiredArgsConstructor
class LessonEditorAssembler {

    // Publish blockers of the checklist that have their own message.
    private static final String MISSING_AI_DRAFTS = "AI_DRAFTS";
    private static final String MISSING_PINYIN = "PINYIN";

    private final ClassContentSupport content;
    private final LessonRevisionDiff diff;
    private final QuizOptionRepository quizOptionRepository;
    private final UploadProperties uploadProperties;

    /** The editor of the lesson, showing its working copy while it has one. */
    AdminClassViews.LessonEditor view(CoursePackage coursePackage, Lesson lesson) {
        Lesson shown = content.revisionOf(lesson).orElse(lesson);
        return view(coursePackage, lesson, shown, shown.getRevisionOf() != null && diff.hasChanges(shown));
    }

    /** Overload used when the caller has already resolved {@code shown} and whether it has unpublished changes. */
    AdminClassViews.LessonEditor view(
            CoursePackage coursePackage, Lesson lesson, Lesson shown, boolean hasUnpublishedChanges) {
        boolean editable = coursePackage.getContentStatus() != ContentStatus.ARCHIVED
                && lesson.getContentStatus() != ContentStatus.ARCHIVED;
        List<LearningModule> units = content.units(coursePackage);
        EditorContent parts = readEditorContent(shown);
        return new AdminClassViews.LessonEditor(
                lesson.getId(),
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getContentStatus().name(),
                lesson.getModule().getId(),
                lesson.getModule().getTitle(),
                units.stream().map(unit -> new AdminClassViews.UnitOption(unit.getId(), unit.getTitle())).toList(),
                lesson.getContentStatus().name(),
                hasUnpublishedChanges,
                editable,
                shown.getTitle(),
                parts.keyPhrases(),
                parts.video(),
                uploadProperties.getMaxVideoFileSize().toBytes(),
                parts.activities(),
                parts.quizzes(),
                parts.checklist());
    }

    /** The publish checks, on the version the editor shows (the working copy of a published lesson). */
    void requireComplete(Lesson shown) {
        List<String> missing = readEditorContent(shown).checklist().missing();
        if (missing.stream().anyMatch(part -> !part.equals(MISSING_AI_DRAFTS) && !part.equals(MISSING_PINYIN))) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "To publish, add a video, key phrases, all 3 activities and a 5-question quiz.");
        }
        if (missing.contains(MISSING_AI_DRAFTS)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Check the AI-drafted quiz questions before you publish: keep or edit each one.");
        }
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Add pinyin to every new word before you publish.");
        }
    }

    private EditorContent readEditorContent(Lesson lesson) {
        Long lessonId = lesson.getId();
        AdminClassViews.Video video = content.video(lessonId)
                .map(found -> new AdminClassViews.Video(found.getTitle(), found.getDurationSeconds()))
                .orElse(null);
        List<LessonViews.KeyPhrase> keyPhrases = content.keyPhraseViews(lessonId);
        List<AdminClassViews.Activity> activities = content.activities(lessonId).stream().map(this::activityView).toList();
        List<AdminClassViews.Quiz> quizzes = content.quizzes(lessonId).stream().map(this::quizView).toList();
        return new EditorContent(video, keyPhrases, activities, quizzes);
    }

    private AdminClassViews.Activity activityView(LessonActivity activity) {
        ClassContentSupport.ActivityParts parts = content.activityParts(activity);
        ClassActivityType type = ClassContentSupport.typeOf(activity);
        return new AdminClassViews.Activity(
                type.name(),
                activity.getId(),
                activity.getTitle(),
                type.lessonPosition(),
                parts.characters(),
                parts.pairs(),
                parts.cards());
    }

    private AdminClassViews.Quiz quizView(Quiz quiz) {
        boolean locked = diff.questionsLocked(quiz);
        // A locked quiz's published questions can no longer change, so show the live questions even on a working copy.
        Long questionsQuizId = locked && quiz.getRevisionSourceId() != null ? quiz.getRevisionSourceId() : quiz.getId();
        List<QuizQuestion> questions = content.questions(questionsQuizId);
        Map<Long, List<QuizOption>> options = quizOptionRepository.findForQuestions(questions);
        List<AdminClassViews.Question> questionViews = new ArrayList<>();
        int number = 1;
        for (QuizQuestion question : questions) {
            questionViews.add(new AdminClassViews.Question(
                    question.getId(),
                    number++,
                    question.getQuestionType().name(),
                    question.getQuestionText(),
                    question.getPoints(),
                    question.getCorrectAnswerText(),
                    question.getExplanation(),
                    question.getHint(),
                    question.getAudioUrl(),
                    question.getImageUrl(),
                    options.getOrDefault(question.getId(), List.of()).stream()
                            .map(option -> new AdminClassViews.QuestionOption(option.getId(), option.getOptionText(),
                                    option.isCorrectOption(), option.getFeedback()))
                            .toList(),
                    question.isAiDraft()));
        }
        return new AdminClassViews.Quiz(quiz.getId(), quiz.isHintsAllowed(), locked, questionViews);
    }

    private record EditorContent(
            AdminClassViews.Video video,
            List<LessonViews.KeyPhrase> keyPhrases,
            List<AdminClassViews.Activity> activities,
            List<AdminClassViews.Quiz> quizzes) {

        /**
         * What publishing needs, from the parts of the version the editor shows. The questions of a quiz learners have
         * already answered (locked) count as they are: they can no longer change.
         */
        AdminClassViews.Checklist checklist() {
            boolean videoAdded = video != null;
            boolean keyPhrasesAdded = !keyPhrases.isEmpty();
            boolean activitiesComplete = activities.stream()
                    .filter(activity -> activityHasMaterial(activity, keyPhrasesAdded))
                    .map(AdminClassViews.Activity::type)
                    .distinct()
                    .count() == ClassActivityType.values().length;
            boolean quizzesReady = !quizzes.isEmpty() && quizzes.stream().allMatch(quiz -> quiz.questionsLocked()
                    || (quiz.questions().size() == LessonEditorService.QUIZ_QUESTIONS
                            && quiz.questions().stream().allMatch(LessonEditorAssembler::hasCorrectAnswer)));
            boolean aiDraftsChecked = quizzes.stream()
                    .filter(quiz -> !quiz.questionsLocked())
                    .flatMap(quiz -> quiz.questions().stream())
                    .noneMatch(AdminClassViews.Question::aiDraft);
            boolean pinyinComplete = keyPhrases.stream().allMatch(phrase -> notBlank(phrase.pinyin()))
                    && activities.stream().allMatch(activity ->
                            activity.cards().stream().allMatch(card -> notBlank(card.pinyin()))
                                    && activity.characters().stream().allMatch(character -> notBlank(character.pinyin()))
                                    && activity.pairs().stream().allMatch(pair -> notBlank(pair.pinyin())));
            List<String> missing = new ArrayList<>();
            if (!videoAdded) {
                missing.add("VIDEO");
            }
            if (!keyPhrasesAdded) {
                missing.add("KEY_PHRASES");
            }
            if (!activitiesComplete) {
                missing.add("ACTIVITIES");
            }
            if (!quizzesReady) {
                missing.add("QUIZ");
            }
            if (!aiDraftsChecked) {
                missing.add(MISSING_AI_DRAFTS);
            }
            if (!pinyinComplete) {
                missing.add(MISSING_PINYIN);
            }
            return new AdminClassViews.Checklist(
                    videoAdded,
                    keyPhrasesAdded,
                    activitiesComplete,
                    quizzesReady,
                    pinyinComplete,
                    List.copyOf(missing));
        }
    }

    private static boolean hasCorrectAnswer(AdminClassViews.Question question) {
        QuizQuestionType type = QuizQuestionType.valueOf(question.type());
        if (!type.isOptionBased()) {
            return notBlank(question.correctAnswerText());
        }
        List<AdminClassViews.QuestionOption> options = question.options();
        if (type == QuizQuestionType.TRUE_FALSE ? options.size() != 2 : options.size() < 2) {
            return false;
        }
        Set<String> labels = options.stream()
                .map(option -> TextUtils.stripFormSpaces(option.text()))
                .collect(Collectors.toSet());
        return options.stream().filter(AdminClassViews.QuestionOption::correct).count() == 1
                && labels.size() == options.size() && !labels.contains("");
    }

    private static boolean activityHasMaterial(AdminClassViews.Activity activity, boolean keyPhrasesAdded) {
        return switch (ClassActivityType.valueOf(activity.type())) {
            case FLASHCARDS -> !activity.cards().isEmpty() || keyPhrasesAdded;
            case TRACING -> !activity.characters().isEmpty();
            case MATCHING -> !activity.pairs().isEmpty();
        };
    }

    private static boolean notBlank(String value) {
        return !TextUtils.isBlankOrSpaces(value);
    }
}
