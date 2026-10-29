package com.example.aichinese.repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

    /** Options of several questions in one query, in display order. */
    List<QuizOption> findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(Collection<Long> questionIds);

    /** Options of the given questions in one query, grouped by question id in display order; no query for no questions. */
    default Map<Long, List<QuizOption>> findForQuestions(Collection<QuizQuestion> questions) {
        List<Long> questionIds = questions.stream().map(QuizQuestion::getId).distinct().toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(questionIds).stream()
                .collect(Collectors.groupingBy(
                        option -> option.getQuestion().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @Modifying
    @Transactional
    @Query("delete from QuizOption o where o.question.id = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
