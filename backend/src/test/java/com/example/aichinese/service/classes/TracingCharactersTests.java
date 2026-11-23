package com.example.aichinese.service.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.TracingExercise;

class TracingCharactersTests {

    @Test
    void aSeparatorTypedInAValueDoesNotShiftTheRowsAfterIt() {
        TracingExercise exercise = new TracingExercise();

        TracingCharacters.write(exercise, List.of(
                new ClassRequests.TraceCharacter("你", "nǐ|ní", "you|thou"),
                new ClassRequests.TraceCharacter("好", "hǎo", "good")));

        assertThat(TracingCharacters.read(exercise)).containsExactly(
                new LessonViews.TraceCharacter("你", "nǐ/ní", "you/thou"),
                new LessonViews.TraceCharacter("好", "hǎo", "good"));
    }

    @Test
    void anEmptyValueKeepsItsPlace() {
        TracingExercise exercise = new TracingExercise();

        TracingCharacters.write(exercise, List.of(
                new ClassRequests.TraceCharacter("你", null, ""),
                new ClassRequests.TraceCharacter("好", "hǎo", "good")));

        assertThat(TracingCharacters.read(exercise)).containsExactly(
                new LessonViews.TraceCharacter("你", null, null),
                new LessonViews.TraceCharacter("好", "hǎo", "good"));
    }
}
