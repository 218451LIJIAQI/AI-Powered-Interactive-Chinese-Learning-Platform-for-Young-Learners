package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.admin.AdminTeacherDetailResponse;
import com.example.aichinese.dto.admin.AdminTeacherListItemResponse;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.learning.StudentQuizQuestionResponse;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.AdminActivityMapper;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.mapper.QuizMapper;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.service.LiveLearningMapper;

class Batch4MapperContractTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 2, 3, 4);

    private final AdminActivityMapper adminActivityMapper = new AdminActivityMapper();
    private final ContentManagementMapper contentManagementMapper = new ContentManagementMapper();
    private final QuizMapper quizMapper = new QuizMapper();
    private final TeacherManagementMapper teacherManagementMapper = new TeacherManagementMapper();
    private final LiveLearningMapper liveLearningMapper = new LiveLearningMapper();

    @Test
    void adminActivityMapperNormalizesCsvTagsAndNullChildLists() {
        AdminFlashcardRequest request = new AdminFlashcardRequest(
                "Card",
                "\u4F60",
                "you",
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                1,
                AgeBand.LOWER_PRIMARY,
                ActivityDifficulty.EASY,
                "greetings",
                List.of("\u3000tone,\n practice\u3000", "tone practice", "pinyin"),
                10,
                30);

        Flashcard entity = adminActivityMapper.toEntity(request, null);

        assertThat(entity.getTags()).isEqualTo("tone practice,pinyin");
        assertThat(adminActivityMapper.toResponse(entity).tags()).containsExactly("tone practice", "pinyin");

        MatchingGame game = matchingGame();
        AdminMatchingGameResponse response = adminActivityMapper.toResponse(game, null);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void teacherMapperNormalizesCategoriesSummariesAndNullCollections() {
        String joined = teacherManagementMapper.joinCategories(List.of(
                "\u3000HSK,\n 1\u3000",
                "HSK 1",
                "storytelling",
                "\u3000"));

        assertThat(joined).isEqualTo("HSK 1, storytelling");

        TeacherProfile profile = teacherProfile("One two\nthree \uD83D\uDE00 four " + "x".repeat(160));

        AdminTeacherListItemResponse listItem = teacherManagementMapper.toAdminTeacherListItem(
                profile,
                -5,
                null,
                null);
        AdminTeacherDetailResponse detail = teacherManagementMapper.toAdminTeacherDetailResponse(
                profile,
                null,
                null,
                null);

        assertThat(listItem.verificationDocumentCount()).isZero();
        assertThat(listItem.bioSummary()).hasSizeLessThanOrEqualTo(140).endsWith("...");
        assertThat(listItem.bioSummary()).doesNotContain("\n");
        assertThat(detail.verificationDocuments()).isEmpty();
        assertThat(detail.verificationReviewHistory()).isEmpty();
        assertThat(detail.recentAuditLogs()).isEmpty();
        assertThat(detail.teachingCategories()).containsExactly("HSK 1", "storytelling");
    }

    @Test
    void quizMapperKeepsGroupingOrderAndTreatsNullOptionsAsEmpty() {
        QuizQuestion firstQuestion = question(10L, "First");
        QuizQuestion secondQuestion = question(20L, "Second");
        QuizOption firstOption = option(101L, firstQuestion, "A");
        QuizOption secondOption = option(201L, secondQuestion, "B");
        QuizOption orphan = option(999L, null, "Orphan");

        Map<Long, List<QuizOption>> grouped = quizMapper.groupOptionsByQuestionId(
                List.of(secondOption, orphan, firstOption));
        StudentQuizQuestionResponse studentQuestion = quizMapper.toStudentQuizQuestionResponse(firstQuestion, null);

        assertThat(grouped.keySet()).containsExactly(20L, 10L);
        assertThat(grouped.get(20L)).containsExactly(secondOption);
        assertThat(studentQuestion.options()).isEmpty();
    }

    @Test
    void contentMapperFiltersNullPackageItems() {
        CoursePackage coursePackage = coursePackage();
        PackageItem item = packageItem();

        CoursePackageResponse response = contentManagementMapper.toCoursePackageResponse(
                coursePackage,
                packageItemsWithNull(item));

        assertThat(response.packageItems())
                .singleElement()
                .satisfies(packageItem -> assertThat(packageItem.itemId()).isEqualTo(55L));
    }

    @Test
    void liveLearningMapperMapsPersistedVideoProgressAndRejectsBrokenRelations() {
        VideoProgress progress = videoProgress();

        VideoProgressResponse response = liveLearningMapper.toVideoProgressResponse(progress);

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.lessonId()).isEqualTo(88L);
        assertThat(response.recordedVideoId()).isEqualTo(99L);
        assertThat(response.watchedSeconds()).isEqualTo(90);
        assertThat(response.totalSeconds()).isEqualTo(120);
        assertThat(response.progressPercentage()).isEqualTo(75);
        assertThat(response.lastWatchedAt()).isEqualTo(NOW.minusMinutes(2));
        assertThat(response.updatedAt()).isEqualTo(NOW);

        progress.setLesson(null);

        assertThatThrownBy(() -> liveLearningMapper.toVideoProgressResponse(progress))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persisted lesson");
    }

    private static MatchingGame matchingGame() {
        MatchingGame game = new MatchingGame();
        game.setId(1L);
        game.setTitle("Match");
        game.setContentStatus(ContentStatus.DRAFT);
        game.setXpReward(15);
        game.setCreatedAt(NOW);
        game.setUpdatedAt(NOW);
        return game;
    }

    private static TeacherProfile teacherProfile(String bio) {
        User user = new User();
        user.setId(5L);
        user.setDisplayName("Teacher Lin");
        user.setEmail("teacher@example.com");
        user.setCreatedAt(NOW);
        user.setUpdatedAt(NOW);

        TeacherProfile profile = new TeacherProfile();
        profile.setId(7L);
        profile.setUser(user);
        profile.setBio(bio);
        profile.setQualificationSummary("Qualified");
        profile.setTeachingCategories("HSK 1, storytelling");
        profile.setTeacherStatus(TeacherStatus.APPROVED);
        profile.setCreatedAt(NOW);
        profile.setUpdatedAt(NOW);
        return profile;
    }

    private static QuizQuestion question(Long id, String text) {
        QuizQuestion question = new QuizQuestion();
        question.setId(id);
        question.setQuestionType("MULTIPLE_CHOICE");
        question.setQuestionText(text);
        question.setPoints(1);
        question.setSortOrder(0);
        return question;
    }

    private static QuizOption option(Long id, QuizQuestion question, String text) {
        QuizOption option = new QuizOption();
        option.setId(id);
        option.setQuestion(question);
        option.setOptionText(text);
        option.setSortOrder(0);
        return option;
    }

    private static CoursePackage coursePackage() {
        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setId(9L);
        coursePackage.setTitle("Package");
        coursePackage.setLearningType(LearningType.HYBRID);
        coursePackage.setAccessType(AccessType.FREE);
        coursePackage.setContentStatus(ContentStatus.PUBLISHED);
        coursePackage.setCreatedAt(NOW);
        coursePackage.setUpdatedAt(NOW);
        return coursePackage;
    }

    private static PackageItem packageItem() {
        CoursePackage parent = new CoursePackage();
        parent.setId(9L);

        PackageItem item = new PackageItem();
        item.setId(10L);
        item.setCoursePackage(parent);
        item.setItemType(PackageItemType.LESSON);
        item.setItemId(55L);
        item.setSortOrder(1);
        item.setTitleSnapshot("Lesson 55");
        return item;
    }

    private static List<PackageItem> packageItemsWithNull(PackageItem item) {
        return Arrays.asList(null, item);
    }

    private static VideoProgress videoProgress() {
        Lesson lesson = new Lesson();
        lesson.setId(88L);

        RecordedLessonVideo video = new RecordedLessonVideo();
        video.setId(99L);

        VideoProgress progress = new VideoProgress();
        progress.setId(77L);
        progress.setLesson(lesson);
        progress.setRecordedVideo(video);
        progress.setWatchedSeconds(90);
        progress.setTotalSeconds(120);
        progress.setProgressPercentage(75);
        progress.setCompleted(false);
        progress.setLastWatchedAt(NOW.minusMinutes(2));
        progress.setUpdatedAt(NOW);
        return progress;
    }
}
