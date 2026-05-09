package com.example.aichinese.dto.ai;

import java.util.List;

public record AiTeacherAssistantResponse(
        String lessonOutline,
        List<AiVocabularyEntryResponse> vocabularyList,
        List<String> exampleSentences,
        List<AiGeneratedQuizQuestionResponse> quizQuestions,
        List<String> flashcardContent,
        List<String> matchingGameContent,
        String storyBasedLessonIdea,
        String teachingNotes,
        String status) {
}
