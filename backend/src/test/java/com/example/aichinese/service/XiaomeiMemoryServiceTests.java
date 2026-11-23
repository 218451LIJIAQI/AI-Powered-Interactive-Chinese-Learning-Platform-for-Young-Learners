package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.XiaomeiMemoryNote;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.XiaomeiMemoryNoteRepository;
import com.example.aichinese.repository.projection.XiaomeiPlanGoal;
import com.example.aichinese.service.account.CurrentAccount;

/**
 * Unit coverage of what Xiaomei may remember (S7.14): a short fact the learner shared, never one with the learner's name,
 * a contact detail, an unsafe word or more than 80 characters; and of what she is told in a chat: the goal of the active
 * plan unless the learner forgot it, and the facts she still remembers. {@code XiaomeiMemoryIntegrationTests} covers the
 * stored notes, "Forget" and its Undo through the API.
 */
class XiaomeiMemoryServiceTests {

    private static final long USER_ID = 7L;
    private static final String LEARNER = "Amira Hassan";

    private final XiaomeiMemoryNoteRepository notes = mock(XiaomeiMemoryNoteRepository.class);
    private final LearningPlanRepository plans = mock(LearningPlanRepository.class);
    private final XiaomeiMemoryService service = new XiaomeiMemoryService(notes, plans,
            new ContentSafetyService(mock(PlatformSettingsService.class)), mock(CurrentAccount.class));

    private User learner;

    @BeforeEach
    void signedInLearner() {
        learner = new User();
        learner.setId(USER_ID);
        learner.setDisplayName(LEARNER);
    }

    @Test
    void keepsAShortFactTheLearnerShared() {
        assertThat(service.usableFact("  Studies   Chinese at school ", LEARNER)).isEqualTo("Studies Chinese at school");
        assertThat(service.usableFact("在学校学中文", LEARNER)).isEqualTo("在学校学中文");
        assertThat(service.usableFact("２０２６年开始学中文", LEARNER)).isEqualTo("２０２６年开始学中文");
        // A part of a name is found as a whole word only: "Li" is not in "Likes".
        assertThat(service.usableFact("Likes noodles", "Li Na")).isEqualTo("Likes noodles");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Her email is amira.h@example.com",
            "Call her on 012-345 6789",
            "Lives near 50450 Kuala Lumpur",
            "她住在邮区５０４５０附近",
            "Follows www.example.com",
            "Shared her [PHONE] for later",
            "Her name is Amira",
            "hassan family likes tea",
            "Wants to watch porn",
            "   "
    })
    void dropsAFactWithTheLearnersNameAContactDetailOrAnUnsafeWord(String fact) {
        assertThat(service.usableFact(fact, LEARNER)).isNull();
    }

    @Test
    void dropsAFactLongerThanANote() {
        assertThat(service.usableFact("a".repeat(XiaomeiMemoryNote.MAX_TEXT_LENGTH), LEARNER)).isNotNull();
        assertThat(service.usableFact("a".repeat(XiaomeiMemoryNote.MAX_TEXT_LENGTH + 1), LEARNER)).isNull();
    }

    @Test
    void detectsChineseNamesAndNamePartsAcrossUnicodeSpaces() {
        assertThat(service.usableFact("李小明在学校学中文", "李小明")).isNull();
        assertThat(service.usableFact("小明喜欢喝茶", "李小明")).isNull();
        assertThat(service.usableFact("喜欢喝茶", "李小明")).isEqualTo("喜欢喝茶");
        assertThat(service.usableFact("Alice likes tea.", "Alice\u3000Ng")).isNull();
        assertThat(service.usableFact("Alice likes tea.", "Alice\u00a0Ng")).isNull();
        // Latin display names next to Chinese characters, punctuated/bilingual names, fullwidth forms, and punctuation-only text.
        assertThat(service.usableFact("Amira在学校学中文", LEARNER)).isNull();
        assertThat(service.usableFact("我是Amira", LEARNER)).isNull();
        assertThat(service.usableFact("Ａｍｉｒａ喜欢喝茶", LEARNER)).isNull();
        assertThat(service.usableFact("联系 a＠b.com", LEARNER)).isNull();
        assertThat(service.usableFact("Tan likes noodles", "Tan, Wei Ming")).isNull();
        assertThat(service.usableFact("Jane studies Mandarin", "Mary-Jane")).isNull();
        assertThat(service.usableFact("Ming喜欢喝茶", "李小明(Ming)")).isNull();
        assertThat(service.usableFact("!!!", LEARNER)).isNull();
    }

    @Test
    void aChatIsToldThePlanGoalAndTheFactsXiaomeiStillRemembers() {
        activePlan("Travel to Shanghai", PlanReason.TRAVEL);
        storedNotes(
                note("Travel to Shanghai", PlanReason.TRAVEL, null),
                note("Studies Chinese at school", null, null),
                note("Likes bubble tea", null, LocalDateTime.now()));

        assertThat(service.chatContext(learner))
                .contains("the goal of their learning plan is \"Travel to Shanghai\"; \"Studies Chinese at school\".")
                .doesNotContain("bubble tea");
    }

    @Test
    void aNewPlanDisplacesTheOldestFactEvenBeforeItsGoalIsStored() {
        activePlan("Travel to Shanghai", PlanReason.TRAVEL);
        storedNotes(note("Oldest fact", null, null), note("Second fact", null, null),
                note("Third fact", null, null), note("Fourth fact", null, null), note("Newest fact", null, null));

        assertThat(service.chatContext(learner))
                .contains("Travel to Shanghai", "Second fact", "Third fact", "Fourth fact", "Newest fact")
                .doesNotContain("Oldest fact");
    }

    @Test
    void aGoalTheLearnerForgotIsNotToldAndARulePlanIsToldByItsReason() {
        activePlan(null, PlanReason.WORK);
        storedNotes(note(null, PlanReason.WORK, LocalDateTime.now()));
        assertThat(service.chatContext(learner)).isEmpty();

        // A goal not noted yet is not forgotten: it is told, by what the reason means.
        storedNotes();
        assertThat(service.chatContext(learner)).contains("the goal of their learning plan is \"learning Chinese for work\"");

        // "Something else" without SparkBot's title says nothing worth telling.
        activePlan(null, PlanReason.OTHER);
        assertThat(service.chatContext(learner)).isEmpty();
    }

    private void activePlan(String goalTitle, PlanReason reason) {
        when(plans.findFirstByStudentUserIdAndActiveTrueOrderByVersionDesc(USER_ID))
                .thenReturn(Optional.of(new XiaomeiPlanGoal(goalTitle, reason)));
    }

    private void storedNotes(XiaomeiMemoryNote... stored) {
        when(notes.findByStudentUserIdOrderByCreatedAtAscIdAsc(USER_ID)).thenReturn(List.of(stored));
    }

    private XiaomeiMemoryNote note(String text, PlanReason planReason, LocalDateTime forgottenAt) {
        XiaomeiMemoryNote note = new XiaomeiMemoryNote();
        note.setStudentUser(learner);
        note.setText(text);
        note.setPlanReason(planReason);
        note.setForgottenAt(forgottenAt);
        return note;
    }
}
