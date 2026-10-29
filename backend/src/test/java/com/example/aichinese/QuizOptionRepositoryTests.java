package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.repository.QuizOptionRepository;

class QuizOptionRepositoryTests {

    private final QuizOptionRepository repository = mock(QuizOptionRepository.class, CALLS_REAL_METHODS);

    @Test
    void findForQuestionsRunsNoQueryForNoQuestions() {
        assertThat(repository.findForQuestions(List.of())).isEmpty();
        verify(repository, never()).findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(anyCollection());
    }

    @Test
    void findForQuestionsGroupsOptionsByQuestionInQueryOrder() {
        QuizQuestion first = question(10L);
        QuizQuestion second = question(20L);
        QuizOption secondA = option(201L, second);
        QuizOption firstA = option(101L, first);
        QuizOption secondB = option(202L, second);
        doReturn(List.of(secondA, firstA, secondB))
                .when(repository).findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(List.of(10L, 20L));

        Map<Long, List<QuizOption>> grouped = repository.findForQuestions(List.of(first, second, first));

        assertThat(grouped.keySet()).containsExactly(20L, 10L);
        assertThat(grouped.get(20L)).containsExactly(secondA, secondB);
        assertThat(grouped.get(10L)).containsExactly(firstA);
    }

    private static QuizQuestion question(Long id) {
        QuizQuestion question = new QuizQuestion();
        question.setId(id);
        return question;
    }

    private static QuizOption option(Long id, QuizQuestion question) {
        QuizOption option = new QuizOption();
        option.setId(id);
        option.setQuestion(question);
        return option;
    }
}
