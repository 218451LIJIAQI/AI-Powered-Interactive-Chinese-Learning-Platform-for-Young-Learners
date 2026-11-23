-- Each question has an INT weight. The total for a quiz can exceed one INT.
ALTER TABLE quiz_attempts
    DROP CHECK chk_quiz_attempts_score_bounds,
    MODIFY score BIGINT NULL,
    MODIFY max_score BIGINT NULL,
    ADD CONSTRAINT chk_quiz_attempts_wide_score_bounds CHECK (
        (score IS NULL AND max_score IS NULL) OR
        (score IS NOT NULL AND max_score IS NOT NULL AND score >= 0 AND max_score >= score)
    );

-- Repair a historical overflow only when the stored answers account for the whole
-- quiz and both stored totals match the low 32 bits of those exact sums.
UPDATE quiz_attempts a
JOIN (
    SELECT answer.quiz_attempt_id, q.quiz_id, SUM(q.points) AS possible,
           SUM(CASE WHEN answer.correct THEN q.points ELSE 0 END) AS earned
    FROM quiz_attempt_answers answer
    JOIN quiz_questions q ON q.id = answer.question_id
    GROUP BY answer.quiz_attempt_id, q.quiz_id
    HAVING SUM(q.points) > 2147483647
) totals ON totals.quiz_attempt_id = a.id AND totals.quiz_id = a.quiz_id
SET a.score = totals.earned, a.max_score = totals.possible
WHERE a.submitted = 1
  AND a.score = MOD(totals.earned, 4294967296)
  AND a.max_score = MOD(totals.possible, 4294967296)
  AND totals.possible = (SELECT SUM(points) FROM quiz_questions WHERE quiz_id = a.quiz_id);
