package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.service.classes.ClassAuthoringService;
import com.example.aichinese.service.classes.ClassContentSupport;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;

/**
 * Admin › Content › Classes end to end: every admin authors every class, with no review. A class goes live with 2 units
 * holding 4 to 6 published lessons; a lesson is published complete (a video, key phrases, its 3 activity slots and a
 * quiz of exactly 5 questions, none an unchecked AI draft); edits of a published lesson wait in a working copy until
 * they are published or discarded; archiving makes a class or lesson read-only until it is restored; only a draft class
 * is deleted.
 */
class AdminClassAuthoringIntegrationTests extends ApiIntegrationTest {

    private static final String ADMIN = "/api/v1/admin";
    private static final String STUDENT = "/api/v1/students/classes";
    private static final String CLASS_SHAPE = "To publish a class, give it 2 units with 4 to 6 published lessons.";
    private static final String CLASS_ARCHIVED = "Restore the class before you edit it.";
    private static final String QUIZ_FULL = "A lesson quiz has exactly 5 questions. Delete one to add another.";

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private ClassContentSupport classContent;

    @Autowired
    private QuizOptionRepository quizOptionRepository;

    @Autowired
    private UploadProperties uploadProperties;

    @Test
    void newlyAuthoredActivitiesUseTheirLessonSlotsRegardlessOfInsertionOrder() throws Exception {
        String admin = api.adminToken();
        long classId = api.draftClass(admin, "Activity slots " + uniqueSuffix());
        long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit"), "Family");
        String activities = ADMIN + "/lessons/" + lessonId + "/activities";
        mockMvc.perform(api.jsonPost(activities, admin, Map.of(
                        "type", "MATCHING", "title", "Match", "pairs", List.of(
                                Map.of("chinese", "妈妈", "pinyin", "māma", "meaning", "mum"),
                                Map.of("chinese", "爸爸", "pinyin", "bàba", "meaning", "dad")))))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(activities, admin, Map.of("type", "FLASHCARDS", "title", "Cards")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[*].type", contains("FLASHCARDS", "MATCHING")))
                .andExpect(jsonPath("$.data.activities[*].number", contains(1, 3)));
        assertThat(jdbc.queryForObject("select lesson_position from flashcards where lesson_id = ?", Integer.class, lessonId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select lesson_position from matching_games where lesson_id = ?", Integer.class, lessonId))
                .isEqualTo(3);
    }

    @Test
    void aLearnerFollowsTheFixedActivitySequenceInAPreviouslyAuthoredLesson() throws Exception {
        String admin = api.adminToken();
        PublishedClass course = api.publishedClass(admin, "Earlier ordering " + uniqueSuffix());
        long lessonId = course.lessonId();
        jdbc.update("update flashcards set lesson_position = 3 where lesson_id = ?", lessonId);
        jdbc.update("update matching_games set lesson_position = 1 where lesson_id = ?", lessonId);
        String student = api.registerStudent(uniqueEmail("activity-order"));
        api.join(student, course.classId());
        JsonNode lesson = api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[*].type", contains("FLASHCARDS", "TRACING", "MATCHING")))
                .andReturn());
        long flashcards = lesson.path("activities").get(0).path("id").asLong();
        mockMvc.perform(api.authGet(STUDENT + "/activities/FLASHCARDS/" + flashcards, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.next.type").value("TRACING"));
    }

    @Test
    void tiedDisplayPositionsKeepTheSameQuestionAndAnswerWhenEditingAPublishedQuiz() throws Exception {
        String admin = api.adminToken();
        long classId = api.draftClass(admin, "Tied positions " + uniqueSuffix());
        long unitId = api.unit(admin, classId, "Unit");
        JsonNode published = api.publishedLesson(admin, unitId, "Greetings", "Common greetings", true, null);
        String lesson = ADMIN + "/lessons/" + published.path("lessonId").asLong();
        JsonNode quiz = published.path("quizzes").get(0);
        long quizId = quiz.path("id").asLong();
        JsonNode questions = quiz.path("questions");
        long first = questions.get(0).path("id").asLong();
        long second = questions.get(1).path("id").asLong();
        long third = questions.get(2).path("id").asLong();
        JsonNode thirdOptions = questions.get(2).path("options");
        long thirdFirstOption = thirdOptions.get(0).path("id").asLong();
        long thirdLastOption = thirdOptions.get(1).path("id").asLong();

        // Existing content can have equal positions and timestamps. The third question and its last option are older.
        jdbc.update("""
                update quiz_questions set sort_order = 1, created_at = case
                    when id = ? then '2020-01-01' when id in (?, ?) then '2020-01-02' else '2020-01-03' end
                where quiz_id = ?
                """, third, first, second, quizId);
        jdbc.update("""
                update quiz_options set sort_order = 1,
                    created_at = case when id = ? then '2020-01-01' else '2020-01-02' end
                where question_id in (?, ?)
                """, thirdLastOption, first, third);

        assertThat(quizOptionRepository.findInDisplayOrder(List.of(third))).extracting(QuizOption::getId)
                .containsExactly(thirdLastOption, thirdFirstOption);
        mockMvc.perform(api.authGet(lesson, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].id").value(third))
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].options[*].text")
                        .value(contains("你好", "再见")))
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].id").value(first))
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].options[0].correct").value(true))
                .andExpect(jsonPath("$.data.quizzes[0].questions[2].id").value(second));

        Map<String, Object> edit = new HashMap<>(LearningApiTestClient.quizQuestions(null).get(0));
        edit.put("questionText", "Choose hello.");
        JsonNode revision = api.data(mockMvc.perform(api.jsonPut(lesson + "/questions/" + first, admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].text").value("Choose hello."))
                .andReturn()).path("quizzes").get(0);
        long revisionId = revision.path("id").asLong();
        assertThat(classContent.questions(quizId)).extracting(QuizQuestion::getId).startsWith(third, first, second);
        assertThat(classContent.questions(revisionId)).extracting(QuizQuestion::getQuestionText)
                .startsWith("Which word means goodbye?", "Choose hello.", "Complete: nǐ ____ (hello)");
    }

    /**
     * A3.16 and A3.19: a lesson's Flashcards activity has cards of its own (an older one without any reviews the key
     * phrases), and each quiz answer may carry feedback the learner reads on that wrong pick. Changes published to a
     * lesson of a published class tell its learners ("Lesson updated").
     */
    @Test
    void flashcardsHaveTheirOwnCardsAnswersTheirFeedbackAndLearnersHearOfPublishedChanges() throws Exception {
        String admin = api.adminToken();
        String classTitle = "Hawker cards " + uniqueSuffix();
        PublishedClass published = api.publishedClass(admin, classTitle, "Hawker food", "Order at a stall");
        String email = uniqueEmail("cards-student");
        String studentToken = api.registerStudent(email);
        api.join(studentToken, published.classId());
        String lesson = ADMIN + "/lessons/" + published.lessonId();
        JsonNode flashcards = api.data(mockMvc.perform(api.authGet(lesson, admin)).andReturn()).path("activities").get(0);
        assertThat(flashcards.path("type").asText()).isEqualTo("FLASHCARDS");
        assertThat(flashcards.path("cards").size()).isZero();
        String play = STUDENT + "/activities/FLASHCARDS/" + flashcards.path("id").asLong();
        mockMvc.perform(api.authGet(play, studentToken))
                .andExpect(jsonPath("$.data.cards.length()").value(1))
                .andExpect(jsonPath("$.data.cards[0].chinese").value("你好"));

        Map<String, Object> cards = Map.of("type", "FLASHCARDS", "title", "Hawker words",
                "cards", List.of(
                        Map.of("chinese", "鸡饭", "pinyin", "jīfàn", "meaning", "chicken rice"),
                        Map.of("chinese", "面", "pinyin", "miàn", "meaning", "noodles")));
        mockMvc.perform(api.jsonPut(lesson + "/activities/FLASHCARDS/" + flashcards.path("id").asLong(), admin, cards))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true))
                .andExpect(jsonPath("$.data.activities[0].cards.length()").value(2))
                .andExpect(jsonPath("$.data.activities[0].cards[0].meaning").value("chicken rice"));
        // Learners keep the published version until the changes are published.
        mockMvc.perform(api.authGet(play, studentToken)).andExpect(jsonPath("$.data.cards[0].chinese").value("你好"));
        assertThat(notificationsOf(email)).isEmpty();

        mockMvc.perform(api.authPost(lesson + "/publish", admin)).andExpect(status().isOk());
        mockMvc.perform(api.authGet(play, studentToken))
                .andExpect(jsonPath("$.data.cards.length()").value(2))
                .andExpect(jsonPath("$.data.cards[0].chinese").value("鸡饭"))
                .andExpect(jsonPath("$.data.cards[1].pinyin").value("miàn"));
        assertThat(notificationsOf(email)).singleElement().satisfies(notification -> {
            assertThat(notification.getTitle()).isEqualTo("Lesson updated");
            assertThat(notification.getMessage())
                    .isEqualTo("“Hawker food” in " + classTitle + " was updated with new content.");
            assertThat(notification.getTargetPath())
                    .isEqualTo("/student/classes/" + published.classId() + "/lessons/" + published.lessonId());
        });

        // The first question answered wrong: the review shows that answer's feedback.
        JsonNode result = api.data(api.submitQuiz(studentToken, published.quizId(), false, "hǎo"));
        JsonNode questions = result.path("questions");
        JsonNode first = questions.get(0);
        assertThat(first.path("correct").asBoolean()).isFalse();
        assertThat(first.path("optionFeedback").asText()).isEqualTo("That's goodbye.");
        assertThat(questions.get(2).path("optionFeedback").isNull()).isTrue();
    }

    /**
     * A draft class goes live once its 2 units hold 4 to 6 published lessons; it is published once. A class has 2 units:
     * a third is refused.
     */
    @Test
    void aClassIsPublishedWithTwoUnitsOfFourToSixPublishedLessons() throws Exception {
        String admin = api.adminToken();
        Long classId = api.draftClass(admin, "Shape class " + uniqueSuffix());
        Long firstUnit = api.unit(admin, classId, "Unit 1");
        Long secondUnit = api.unit(admin, classId, "Unit 2");
        mockMvc.perform(api.jsonPost(ADMIN + "/classes/" + classId + "/units", admin, Map.of("title", "Unit 3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A class has 2 units."));
        for (int number = 1; number <= 3; number++) {
            api.publishedLesson(admin, firstUnit, "Lesson " + number, "Summary " + number, true, null);
        }
        // 3 published lessons, all in one unit; a draft lesson in the second unit does not count.
        Long draftLesson = api.draftLesson(admin, secondUnit, "Lesson 4");
        assertCannotPublish(admin, classId);

        api.readyLesson(admin, draftLesson, "Summary 4");
        api.publishLesson(admin, draftLesson);
        // A draft lesson beside them does not count either, and does not stop the class.
        api.draftLesson(admin, secondUnit, "Lesson 5");
        mockMvc.perform(api.authGet(ADMIN + "/classes/" + classId, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.lessonCount").value(5))
                .andExpect(jsonPath("$.data.units.length()").value(2))
                .andExpect(jsonPath("$.data.canPublish").value(true));
        mockMvc.perform(get("/api/v1/public/classes/" + classId)).andExpect(status().isNotFound());

        mockMvc.perform(api.authPost(ADMIN + "/classes/" + classId + "/publish", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Class published."))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.canPublish").value(false));
        mockMvc.perform(get("/api/v1/public/classes/" + classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.lessonCount").value(4));
        mockMvc.perform(api.authPost(ADMIN + "/classes/" + classId + "/publish", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft class can be published."));
        // The units added while the class was a draft are draft edits: not audited.
        assertThat(auditActionsAbout("CoursePackage", classId))
                .containsExactlyInAnyOrder("ADMIN_CLASS_CREATE", "ADMIN_CLASS_PUBLISH");
    }

    /**
     * Only a published class is archived (a draft is deleted instead). An archived class leaves the catalogue and is
     * read-only until an admin restores it.
     */
    @Test
    void anArchivedClassIsReadOnlyUntilItIsRestored() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Archive class " + uniqueSuffix());
        Long draftId = api.draftClass(admin, "Archive draft " + uniqueSuffix());
        String classPath = ADMIN + "/classes/" + published.classId();

        mockMvc.perform(api.authPost(ADMIN + "/classes/" + draftId + "/archive", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a published class can be archived. Delete a draft class instead."));
        mockMvc.perform(api.authPost(classPath + "/restore", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only an archived class can be restored."));
        mockMvc.perform(api.authPost(classPath + "/archive", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(api.authPost(classPath + "/archive", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Class has already been archived."));

        mockMvc.perform(get("/api/v1/public/classes/" + published.classId())).andExpect(status().isNotFound());
        mockMvc.perform(api.jsonPut(classPath, admin, details("Renamed while archived", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CLASS_ARCHIVED));
        mockMvc.perform(api.jsonPost(classPath + "/units", admin, Map.of("title", "Unit 3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CLASS_ARCHIVED));
        mockMvc.perform(api.jsonPost(ADMIN + "/units/" + published.unitId() + "/lessons", admin, Map.of("title", "New")))
                .andExpect(status().isConflict());
        mockMvc.perform(api.jsonPut(ADMIN + "/lessons/" + published.lessonId() + "/content", admin, content("Archived", "No")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CLASS_ARCHIVED));
        mockMvc.perform(api.authGet(ADMIN + "/lessons/" + published.lessonId(), admin))
                .andExpect(jsonPath("$.data.classStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.editable").value(false));
        mockMvc.perform(api.authDelete(classPath, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft class can be deleted."));

        mockMvc.perform(api.authPost(classPath + "/restore", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/public/classes/" + published.classId())).andExpect(status().isOk());
        // Editable again: a unit of the published class is renamed (and audited); it still has its 2 units.
        mockMvc.perform(api.jsonPut(ADMIN + "/units/" + published.unitId(), admin, Map.of("title", "First steps")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].title").value("First steps"));
        mockMvc.perform(api.jsonPost(classPath + "/units", admin, Map.of("title", "Unit 3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A class has 2 units."));
        assertThat(auditActionsAbout("CoursePackage", published.classId()))
                .contains("ADMIN_CLASS_ARCHIVE", "ADMIN_CLASS_RESTORE", "ADMIN_CLASS_UNIT_RENAME")
                .doesNotContain("ADMIN_CLASS_UNIT_ADD");
    }

    /** A draft class goes with its units and lessons (with every part of them); a published class never does. */
    @Test
    void onlyADraftClassIsDeleted() throws Exception {
        String admin = api.adminToken();
        Long classId = api.draftClass(admin, "Delete class " + uniqueSuffix());
        Long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit 1"), "Doomed lesson");
        api.readyLesson(admin, lessonId, "Everything in it goes");

        mockMvc.perform(api.authDelete(ADMIN + "/classes/" + classId, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Draft class deleted."));

        mockMvc.perform(api.authGet(ADMIN + "/classes/" + classId, admin)).andExpect(status().isNotFound());
        mockMvc.perform(api.authGet(ADMIN + "/lessons/" + lessonId, admin)).andExpect(status().isNotFound());
        assertThat(lessonRepository.findById(lessonId)).isEmpty();
        assertThat(auditActionsAbout("CoursePackage", classId)).contains("ADMIN_CLASS_DELETE");

        PublishedClass published = api.publishedClass(admin, "Kept class " + uniqueSuffix());
        mockMvc.perform(api.authDelete(ADMIN + "/classes/" + published.classId(), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft class can be deleted."));
    }

    @Test
    @Transactional(isolation = Isolation.READ_COMMITTED)
    void aDraftClassAndItsNewUnitCanBeDeletedInTheSameTransaction(
            @Autowired ClassAuthoringService authoring, @Autowired EntityManager entityManager) {
        var admin = principalFor(LearningApiTestClient.ADMIN_EMAIL);
        long classId = authoring.createClass(admin,
                new ClassRequests.ClassDetails("Temporary class", null, null, null)).id();
        authoring.addUnit(admin, classId, new ClassRequests.UnitTitle("Temporary unit"));
        long linkId = jdbc.queryForObject("select id from package_items where course_package_id = ?", Long.class, classId);
        PackageItem link = entityManager.find(PackageItem.class, linkId);
        assertThat(entityManager.contains(link)).isTrue();

        authoring.deleteClass(admin, classId);
        entityManager.flush();

        assertThat(entityManager.find(PackageItem.class, linkId)).isNull();
    }

    /**
     * The checklist names what a lesson still needs; it is published only when nothing is missing. Its activities are 3
     * fixed slots and it has one quiz of exactly 5 questions.
     */
    @Test
    void aLessonIsPublishedOnlyWhenComplete() throws Exception {
        String admin = api.adminToken();
        Long classId = api.draftClass(admin, "Checklist class " + uniqueSuffix());
        Long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit 1"), "Checklist lesson");
        String lesson = ADMIN + "/lessons/" + lessonId;

        mockMvc.perform(api.authGet(lesson, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.videoUploadMaxBytes").value(3072))
                .andExpect(jsonPath("$.data.checklist.missing").value(
                        contains("VIDEO", "KEY_PHRASES", "ACTIVITIES", "QUIZ")));
        mockMvc.perform(api.authPost(lesson + "/publish", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "To publish, add a video, key phrases, all 3 activities and a 5-question quiz."));

        JsonNode ready = api.readyLesson(admin, lessonId, "Complete");
        assertThat(ready.path("checklist").path("missing")).isEmpty();
        JsonNode quiz = ready.path("quizzes").get(0);
        assertThat(quiz.path("questions").size()).isEqualTo(5);
        String quizQuestionsPath = lesson + "/quizzes/" + quiz.path("id").asLong() + "/questions";
        List<Map<String, ?>> sampleQuestions = LearningApiTestClient.quizQuestions(null);

        // One activity per slot, one quiz, and no sixth question.
        mockMvc.perform(api.jsonPost(lesson + "/activities", admin, Map.of("type", "MATCHING", "title", "Second",
                        "pairs", List.of(Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello"),
                                Map.of("chinese", "再见", "pinyin", "zàijiàn", "meaning", "goodbye")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This lesson already has an activity of this type."));
        mockMvc.perform(api.jsonPost(lesson + "/quizzes", admin, Map.of("title", "Second quiz")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This lesson already has a quiz."));
        mockMvc.perform(api.jsonPost(quizQuestionsPath, admin, sampleQuestions.get(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(QUIZ_FULL));

        // Four questions: the quiz is not ready.
        mockMvc.perform(api.authDelete(lesson + "/questions/" + quiz.path("questions").get(4).path("id").asLong(), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions.length()").value(4))
                .andExpect(jsonPath("$.data.checklist.missing[0]").value("QUIZ"));

        // A question taken from the AI draft is an unchecked AI draft: publishing waits until an admin keeps it.
        Map<String, Object> drafted = new HashMap<>(sampleQuestions.get(4));
        drafted.put("aiDraft", true);
        JsonNode withDraft = api.data(mockMvc.perform(api.jsonPost(quizQuestionsPath, admin, drafted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions[4].aiDraft").value(true))
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].aiDraft").value(false))
                .andExpect(jsonPath("$.data.checklist.missing").value(contains("AI_DRAFTS")))
                .andReturn());
        mockMvc.perform(api.authPost(lesson + "/publish", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Check the AI-drafted quiz questions before you publish: keep or edit each one."));
        long draftedId = withDraft.path("quizzes").get(0).path("questions").get(4).path("id").asLong();
        mockMvc.perform(api.authPost(lesson + "/questions/" + draftedId + "/keep", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Question kept."))
                .andExpect(jsonPath("$.data.quizzes[0].questions[4].aiDraft").value(false))
                .andExpect(jsonPath("$.data.checklist.missing").isEmpty());

        mockMvc.perform(api.authPost(lesson + "/publish", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lesson published."))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));
        // A new draft lesson and its edits are not audited; publishing it is.
        assertThat(auditActionsAbout("Lesson", lessonId)).containsExactly("ADMIN_LESSON_PUBLISH");
    }

    /**
     * Editing an AI draft question checks it too, and a question added back at its old place (the Undo of a deletion)
     * takes that place again.
     */
    @Test
    void anEditedAiDraftIsCheckedAndAQuestionCanBePutBackInItsPlace() throws Exception {
        String admin = api.adminToken();
        Long classId = api.draftClass(admin, "Draft question class " + uniqueSuffix());
        Long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit 1"), "Draft questions");
        JsonNode ready = api.readyLesson(admin, lessonId, "Questions");
        String lesson = ADMIN + "/lessons/" + lessonId;
        JsonNode quiz = ready.path("quizzes").get(0);
        long quizId = quiz.path("id").asLong();
        JsonNode second = quiz.path("questions").get(1);
        Map<String, ?> original = LearningApiTestClient.quizQuestions(null).get(1);

        mockMvc.perform(api.authDelete(lesson + "/questions/" + second.path("id").asLong(), admin))
                .andExpect(status().isOk());
        Map<String, Object> putBack = new HashMap<>(original);
        putBack.put("position", 2);
        putBack.put("aiDraft", true);
        JsonNode editor = api.data(mockMvc.perform(api.jsonPost(lesson + "/quizzes/" + quizId + "/questions", admin, putBack))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].text").value("Complete: nǐ ____ (hello)"))
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].number").value(2))
                .andExpect(jsonPath("$.data.quizzes[0].questions[2].text").value("Which word means goodbye?"))
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].aiDraft").value(true))
                .andReturn());

        Map<String, Object> edited = new HashMap<>(original);
        edited.put("questionText", "Complete: nǐ ____ (hi)");
        edited.put("aiDraft", true);
        mockMvc.perform(api.jsonPut(lesson + "/questions/"
                        + editor.path("quizzes").get(0).path("questions").get(1).path("id").asLong(), admin, edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].text").value("Complete: nǐ ____ (hi)"))
                .andExpect(jsonPath("$.data.quizzes[0].questions[1].aiDraft").value(false))
                .andExpect(jsonPath("$.data.checklist.missing").isEmpty());
    }

    /**
     * Edits of a published lesson wait in a working copy: learners keep the published lesson (and their progress) until
     * the changes are published, the changes can be discarded, and an edit undone leaves nothing to publish.
     */
    @Test
    void editsOfAPublishedLessonWaitInAWorkingCopy() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Working copy " + uniqueSuffix(), "Greetings", "Say hello");
        String studentToken = api.registerStudent(uniqueEmail("working-copy-student"));
        api.join(studentToken, published.classId());
        String studentLesson = STUDENT + "/lessons/" + published.lessonId();
        long flashcardsId = api.data(mockMvc.perform(api.authGet(studentLesson, studentToken)).andReturn())
                .path("activities").get(0).path("id").asLong();
        mockMvc.perform(api.authPost(STUDENT + "/activities/FLASHCARDS/" + flashcardsId + "/complete", studentToken))
                .andExpect(status().isOk());
        String lesson = ADMIN + "/lessons/" + published.lessonId();

        Map<String, Object> twoPhrases = Map.of(
                "title", "Greetings",
                "minutes", 15,
                "summary", "Say hello and goodbye",
                "keyPhrases", List.of(
                        Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello"),
                        Map.of("chinese", "再见", "pinyin", "zàijiàn", "meaning", "goodbye")));
        mockMvc.perform(api.jsonPut(lesson + "/content", admin, twoPhrases))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true))
                .andExpect(jsonPath("$.data.summary").value("Say hello and goodbye"));
        mockMvc.perform(api.authGet(ADMIN + "/classes/" + published.classId(), admin))
                .andExpect(jsonPath("$.data.units[0].lessons[0].hasUnpublishedChanges").value(true));
        mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(jsonPath("$.data.keyPhrases.length()").value(1));

        // Published: applied in place, so the lesson keeps its id and the learner keeps their progress.
        mockMvc.perform(api.authPost(lesson + "/publish", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));
        JsonNode liveLesson = api.data(mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(jsonPath("$.data.keyPhrases.length()").value(2))
                .andExpect(jsonPath("$.data.activities[0].id").value(flashcardsId))
                .andExpect(jsonPath("$.data.activities[0].done").value(true))
                .andExpect(jsonPath("$.data.stepsDone").value(1))
                .andReturn());
        String publishedVideo = liveLesson.path("video").path("streamPath").asText();
        long liveUpload = classContent.video(published.lessonId()).orElseThrow().getUploadedFile().getId();
        mockMvc.perform(api.authPost(lesson + "/publish", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("There are no unpublished changes to publish."));
        // Publishing the changes of a published lesson is "Lesson edited"; saving them was not audited.
        assertThat(auditActionsAbout("Lesson", published.lessonId()))
                .containsExactlyInAnyOrder("ADMIN_LESSON_PUBLISH", "ADMIN_LESSON_EDIT");

        // Discarded: learners never saw the changes.
        mockMvc.perform(api.jsonPut(lesson + "/content", admin, content("Greetings", "A summary to throw away")))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));
        api.uploadLessonVideo(admin, published.lessonId(), "Working copy video");
        long draftId = lessonRepository.findFirstByRevisionOfId(published.lessonId()).orElseThrow().getId();
        long draftUpload = classContent.video(draftId).orElseThrow().getUploadedFile().getId();
        String storedName = jdbc.queryForObject("select stored_file_name from uploaded_files where id = ?",
                String.class, draftUpload);
        Path draftFile = Path.of(uploadProperties.getRecordedVideoDirectory(), storedName);
        assertThat(draftFile).hasBinaryContent(TestUploadFixtures.minimalMp4());
        mockMvc.perform(api.authDelete(lesson + "/revision", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes discarded."))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false))
                .andExpect(jsonPath("$.data.summary").value("Say hello and goodbye"));
        assertThat(jdbc.queryForList("select id from uploaded_files where id = ?", Long.class, draftUpload)).isEmpty();
        assertThat(draftFile).doesNotExist();
        assertThat(classContent.video(published.lessonId()).orElseThrow().getUploadedFile().getId()).isEqualTo(liveUpload);
        assertThat(mockMvc.perform(api.authGet(publishedVideo, studentToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .isEqualTo(TestUploadFixtures.minimalMp4());
        mockMvc.perform(api.authDelete(lesson + "/revision", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This lesson has no changes to discard."));

        // An edit undone leaves nothing to publish.
        mockMvc.perform(api.jsonPut(lesson + "/content", admin, content("Greetings", "Changed")))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));
        mockMvc.perform(api.jsonPut(lesson + "/content", admin, twoPhrases))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));
    }

    /**
     * Moving a lesson to the other unit of its class is structure: it applies at once, also to a published lesson
     * (which is then left with nothing to publish), and the lesson goes last in that unit. Only a unit of its class is
     * taken.
     */
    @Test
    void aLessonMovedToTheOtherUnitGoesLastThereAtOnce() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Move class " + uniqueSuffix(), "Greetings", "Say hello");
        String classPath = ADMIN + "/classes/" + published.classId();
        long secondUnit = api.data(mockMvc.perform(api.authGet(classPath, admin)).andReturn())
                .path("units").get(1).path("id").asLong();
        String lesson = ADMIN + "/lessons/" + published.lessonId();
        // The lesson's own content, in the second unit.
        Map<String, Object> moved = new HashMap<>(content("Greetings", "Say hello"));
        moved.put("unitId", secondUnit);

        mockMvc.perform(api.jsonPut(lesson + "/content", admin, moved))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitId").value(secondUnit))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));
        mockMvc.perform(api.authGet(classPath, admin))
                .andExpect(jsonPath("$.data.units[0].lessons.length()").value(1))
                .andExpect(jsonPath("$.data.units[1].lessons.length()").value(3))
                .andExpect(jsonPath("$.data.units[1].lessons[2].id").value(published.lessonId()));
        assertThat(auditActionsAbout("Lesson", published.lessonId()))
                .containsExactlyInAnyOrder("ADMIN_LESSON_PUBLISH", "ADMIN_LESSON_EDIT");

        moved.put("unitId", api.unit(admin, api.draftClass(admin, "Other class " + uniqueSuffix()), "Unit 1"));
        mockMvc.perform(api.jsonPut(lesson + "/content", admin, moved))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a unit of this class."));
    }

    @Test
    void choiceLabelsRemainDistinctWhenSavingAndPublishingOlderDrafts() throws Exception {
        String admin = api.adminToken();
        long classId = api.draftClass(admin, "Answer labels " + uniqueSuffix());
        long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit"), "Pinyin tones");
        JsonNode editor = api.readyLesson(admin, lessonId, "Tones");
        JsonNode question = editor.path("quizzes").get(0).path("questions").get(0);
        long questionId = question.path("id").asLong();
        String lesson = ADMIN + "/lessons/" + lessonId;
        String questionPath = lesson + "/questions/" + questionId;
        Map<String, Object> edit = new HashMap<>(LearningApiTestClient.quizQuestions(null).getFirst());
        edit.put("options", List.of(Map.of("optionText", "mā", "correctOption", true),
                Map.of("optionText", "m\u200Bā", "correctOption", false)));
        mockMvc.perform(api.jsonPut(questionPath, admin, edit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The answers of a question must all be different."));

        // An older draft could store the same label against two IDs, only one of which was marked correct.
        jdbc.update("update quiz_options set option_text = 'mā' where question_id = ?", questionId);
        mockMvc.perform(api.authGet(lesson, admin))
                .andExpect(jsonPath("$.data.checklist.missing").value(contains("QUIZ")));
        mockMvc.perform(api.authPost(lesson + "/publish", admin)).andExpect(status().isBadRequest());

        edit.put("options", List.of(Map.of("optionText", "mā", "correctOption", true),
                Map.of("optionText", "mǎ", "correctOption", false)));
        mockMvc.perform(api.jsonPut(questionPath, admin, edit)).andExpect(status().isOk());
        mockMvc.perform(api.authPost(lesson + "/publish", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].options[*].text").value(contains("mā", "mǎ")));
    }

    /** Once learners have submitted the quiz, its questions stay as they are; the hint switch still changes. */
    @Test
    void submittedAttemptsLockTheQuestionsButNotTheHintSwitch() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Locked quiz " + uniqueSuffix());
        String studentToken = api.registerStudent(uniqueEmail("locked-quiz-student"));
        api.submitQuiz(studentToken, published.quizId(), true, "hǎo");
        String lesson = ADMIN + "/lessons/" + published.lessonId();
        JsonNode questions = api.data(mockMvc.perform(api.authGet(lesson, admin))
                .andExpect(jsonPath("$.data.quizzes[0].questionsLocked").value(true))
                .andReturn()).path("quizzes").get(0).path("questions");
        String locked = "Quiz questions cannot be changed after learners have submitted attempts.";

        String firstQuestion = lesson + "/questions/" + questions.get(0).path("id").asLong();
        mockMvc.perform(api.authDelete(firstQuestion, admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(locked));
        mockMvc.perform(api.jsonPut(firstQuestion, admin, LearningApiTestClient.quizQuestions(null).get(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(locked));

        mockMvc.perform(api.jsonPut(lesson + "/quizzes/" + published.quizId(), admin, Map.of("hintsAllowed", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizzes[0].hintsAllowed").value(false))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));
        long matchingId = matchingIdOf(studentToken, published.lessonId());
        mockMvc.perform(api.authGet(STUDENT + "/activities/matching/" + matchingId, studentToken))
                .andExpect(jsonPath("$.data.hintsAllowed").value(true));
        mockMvc.perform(api.authPost(lesson + "/publish", admin)).andExpect(status().isOk());
        mockMvc.perform(api.authGet(STUDENT + "/activities/matching/" + matchingId, studentToken))
                .andExpect(jsonPath("$.data.hintsAllowed").value(false));
    }

    /**
     * A working copy whose only edits are to questions that learners have since locked has nothing left to publish: the
     * class page agrees with the lesson editor instead of flagging changes the editor no longer offers to publish.
     */
    @Test
    void lockedQuestionEditsLeaveNoUnpublishedChangesOnTheClassPage() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Stale copy " + uniqueSuffix());
        String lesson = ADMIN + "/lessons/" + published.lessonId();
        String classPath = ADMIN + "/classes/" + published.classId();
        long questionId = api.data(mockMvc.perform(api.authGet(lesson, admin)).andReturn())
                .path("quizzes").get(0).path("questions").get(0).path("id").asLong();
        Map<String, Object> edit = new HashMap<>(LearningApiTestClient.quizQuestions(null).get(0));
        edit.put("questionText", "An edited question " + uniqueSuffix());
        mockMvc.perform(api.jsonPut(lesson + "/questions/" + questionId, admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));
        mockMvc.perform(api.authGet(classPath, admin))
                .andExpect(jsonPath("$.data.units[0].lessons[0].hasUnpublishedChanges").value(true));

        // A learner submits the published quiz: its questions are locked, so the edit can no longer be published.
        String studentToken = api.registerStudent(uniqueEmail("stale-copy-student"));
        api.submitQuiz(studentToken, published.quizId(), true, "hǎo");

        mockMvc.perform(api.authGet(lesson, admin))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));
        mockMvc.perform(api.authGet(classPath, admin))
                .andExpect(jsonPath("$.data.units[0].lessons[0].hasUnpublishedChanges").value(false));
    }

    /** An archived lesson closes for learners (they keep their progress) and is read-only until it is restored. */
    @Test
    void anArchivedLessonClosesForLearnersUntilItIsRestored() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Lesson archive " + uniqueSuffix());
        String studentToken = api.registerStudent(uniqueEmail("lesson-archive-student"));
        api.join(studentToken, published.classId());
        String lesson = ADMIN + "/lessons/" + published.lessonId();
        String studentLesson = STUDENT + "/lessons/" + published.lessonId();

        mockMvc.perform(api.authPost(lesson + "/archive", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.editable").value(false));
        mockMvc.perform(api.authPost(lesson + "/archive", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This lesson is already archived."));
        mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(api.jsonPut(lesson + "/content", admin, content("Archived lesson", "No")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Restore the lesson before you edit it."));

        mockMvc.perform(api.authPost(lesson + "/restore", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(api.authPost(lesson + "/restore", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only an archived lesson can be restored."));
        mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(jsonPath("$.data.available").value(true));
        assertThat(auditActionsAbout("Lesson", published.lessonId()))
                .contains("ADMIN_LESSON_ARCHIVE", "ADMIN_LESSON_RESTORE");
    }

    /** The details of a published class are saved at once: learners see them straight away. */
    @Test
    void theDetailsOfAPublishedClassChangeAtOnce() throws Exception {
        String admin = api.adminToken();
        PublishedClass published = api.publishedClass(admin, "Details class " + uniqueSuffix());
        String renamed = "Renamed class " + uniqueSuffix();

        mockMvc.perform(api.jsonPut(ADMIN + "/classes/" + published.classId(), admin, details(renamed, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Class details saved."))
                .andExpect(jsonPath("$.data.title").value(renamed))
                .andExpect(jsonPath("$.data.bestAfterUnit").value(4))
                .andExpect(jsonPath("$.data.category.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/public/classes/" + published.classId()))
                .andExpect(jsonPath("$.data.summary.title").value(renamed))
                .andExpect(jsonPath("$.data.summary.bestAfterUnit").value(4));
        // The blank option clears "best after".
        mockMvc.perform(api.jsonPut(ADMIN + "/classes/" + published.classId(), admin, details(renamed, null)))
                .andExpect(jsonPath("$.data.bestAfterUnit").doesNotExist());
        Map<String, Object> unknownCategory = new HashMap<>(details(renamed, null));
        unknownCategory.put("categoryId", 999_999);
        mockMvc.perform(api.jsonPut(ADMIN + "/classes/" + published.classId(), admin, unknownCategory))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selected learning category does not exist."));
        // Each saved edit of a published class is audited; the edits of a draft class are not.
        assertThat(auditActionsAbout("CoursePackage", published.classId()))
                .filteredOn("ADMIN_CLASS_EDIT"::equals)
                .hasSize(2);
        Long draftId = api.draftClass(admin, "Details draft " + uniqueSuffix());
        mockMvc.perform(api.jsonPut(ADMIN + "/classes/" + draftId, admin, details("Draft renamed " + uniqueSuffix(), 2)))
                .andExpect(status().isOk());
        api.unit(admin, draftId, "Unit 1");
        assertThat(auditActionsAbout("CoursePackage", draftId)).containsExactly("ADMIN_CLASS_CREATE");
    }

    @Test
    void theClassesListFindsClassesAndLessonsByKeywordTypeAndStatus() throws Exception {
        String admin = api.adminToken();
        String marker = "List" + uniqueSuffix();
        Long classId = api.draftClass(admin, marker + " class");
        Long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit 1"), marker + " lesson");
        String list = ADMIN + "/classes?keyword=" + marker;

        mockMvc.perform(api.authGet(list, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2));
        mockMvc.perform(api.authGet(list + "&type=CLASS", admin))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(classId))
                .andExpect(jsonPath("$.data.items[0].type").value("CLASS"))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
        mockMvc.perform(api.authGet(list + "&type=lesson&status=draft", admin))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(lessonId))
                .andExpect(jsonPath("$.data.items[0].classId").value(classId))
                .andExpect(jsonPath("$.data.items[0].classTitle").value(marker + " class"));
        mockMvc.perform(api.authGet(list + "&status=PUBLISHED", admin))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet(ADMIN + "/classes?type=UNIT", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Type must be CLASS or LESSON."));
        mockMvc.perform(api.authGet(ADMIN + "/classes?status=SUBMITTED", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Status filter is not supported."));
    }

    @Test
    void pagesClassAndLessonRowsWithLiteralPinyinAndPunctuation() throws Exception {
        String admin = api.adminToken();
        String suffix = uniqueSuffix();
        long classId = api.draftClass(admin, "Mā_" + suffix);
        long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit"), "Greetings");
        api.draftClass(admin, "Ma_" + suffix);
        api.draftClass(admin, "MāX" + suffix);
        // uk_package_items_package_item rejects linking the same unit twice to one class.
        assertThatThrownBy(() -> jdbc.update("""
                insert into package_items (course_package_id, item_id, sort_order, created_at, updated_at)
                select course_package_id, item_id, sort_order + 1, created_at, updated_at
                from package_items where course_package_id = ?
                """, classId)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update course_packages set updated_at = '2020-01-01' where id = ?", classId);
        jdbc.update("update lessons set updated_at = '2020-01-01' where id = ?", lessonId);

        String path = ADMIN + "/classes";
        String keyword = "mā_" + suffix;
        mockMvc.perform(api.authGet(path, admin).param("keyword", keyword).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(classId))
                .andExpect(jsonPath("$.data.items[0].type").value("CLASS"));
        mockMvc.perform(api.authGet(path, admin).param("keyword", keyword).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(lessonId))
                .andExpect(jsonPath("$.data.items[0].type").value("LESSON"));
        mockMvc.perform(api.authGet(path, admin).param("keyword", keyword).param("page", "2147483647"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    /**
     * Admins author the Practice library and the class lessons: a lesson's activities stay in its lesson and are never
     * Practice sets, for the admins or the learners.
     */
    @Test
    void theActivitiesOfALessonAreNotPracticeSets() throws Exception {
        String admin = api.adminToken();
        Long classId = api.draftClass(admin, "Practice check " + uniqueSuffix());
        Long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit 1"), "Not a practice set");
        JsonNode editor = api.readyLesson(admin, lessonId, "Its activities stay here");
        api.publishLesson(admin, lessonId);
        String learner = api.registerStudent(uniqueEmail("practice-check-learner"));

        assertThat(editor.path("activities")).hasSize(3);
        for (JsonNode activity : editor.path("activities")) {
            String type = activity.path("type").asText().toLowerCase(Locale.ROOT);
            long id = activity.path("id").asLong();
            mockMvc.perform(api.authGet("/api/v1/admin/practice-sets/" + type + "/" + id, admin))
                    .andExpect(status().isNotFound());
            mockMvc.perform(api.authGet("/api/v1/students/practice/" + type + "/sets/" + id, learner))
                    .andExpect(status().isNotFound());
            mockMvc.perform(api.authGet("/api/v1/students/practice/" + type + "/sets", learner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sets[?(@.id == " + id + ")]").isEmpty());
        }
    }

    /** Learners never author classes; the teacher endpoints are gone. */
    @Test
    void onlyAdminsAuthorClasses() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("authoring-student"));

        mockMvc.perform(api.authGet(ADMIN + "/classes", studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.jsonPost(ADMIN + "/classes", studentToken, details("Mine", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(ADMIN + "/lessons/1", studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet("/api/v1/teachers/classes", api.adminToken())).andExpect(status().isNotFound());
    }

    private void assertCannotPublish(String admin, Long classId) throws Exception {
        mockMvc.perform(api.authGet(ADMIN + "/classes/" + classId, admin))
                .andExpect(jsonPath("$.data.canPublish").value(false));
        mockMvc.perform(api.authPost(ADMIN + "/classes/" + classId + "/publish", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CLASS_SHAPE));
    }

    private long matchingIdOf(String studentToken, Long lessonId) throws Exception {
        for (JsonNode activity : api.data(mockMvc.perform(api.authGet(STUDENT + "/lessons/" + lessonId, studentToken))
                .andReturn()).path("activities")) {
            if (activity.path("type").asText().equals("MATCHING")) {
                return activity.path("id").asLong();
            }
        }
        throw new AssertionError("The lesson has no matching activity.");
    }

    private List<String> auditActionsAbout(String targetType, Long targetId) {
        return auditEntriesAbout(targetType, targetId).stream()
                .map(AuditLog::getActionType)
                .toList();
    }

    private static Map<String, Object> details(String title, Integer bestAfterUnit) {
        Map<String, Object> details = new HashMap<>(Map.of("title", title, "description", "A class", "categoryId", 1));
        if (bestAfterUnit != null) {
            details.put("bestAfterUnit", bestAfterUnit);
        }
        return details;
    }

    private static Map<String, Object> content(String title, String summary) {
        return Map.of("title", title, "minutes", 12, "summary", summary,
                "keyPhrases", List.of(Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello")));
    }
}
