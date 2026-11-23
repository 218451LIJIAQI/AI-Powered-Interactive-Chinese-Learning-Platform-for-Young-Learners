/**
 * Maps an AI-assisted quiz draft (FRAI05, the admin lesson editor) onto quiz-question fields. The lesson editor saves
 * each mapped question at once as an unchecked AI draft, which the admin checks or edits before publishing the lesson.
 */
import type { AiQuizGeneratorResponse, QuizQuestionType } from '@/types/api'
import { draftAnswerIndex } from '@/utils/aiPracticeQuiz'
import { isQuestionType } from '@/utils/classes'

/** The type the AI wrote ("fill blank", "true-false"), or multiple choice for one the builder does not have. */
function normalizeQuizQuestionType(value: string): QuizQuestionType {
  const normalized = value
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  return isQuestionType(normalized) ? normalized : 'MULTIPLE_CHOICE'
}

/** Turns AI quiz questions into the fields of new quiz questions. */
export function quizDraftToQuestionDrafts(response: AiQuizGeneratorResponse) {
  return response.questions.map((question) => {
    const correctIndex = draftAnswerIndex(question.options, question.correctAnswer)
    return {
      questionType: normalizeQuizQuestionType(question.questionType),
      questionText: question.questionText,
      correctAnswerText: correctIndex < 0 ? question.correctAnswer : question.options[correctIndex],
      explanation: [question.explanation, question.pinyin, question.translation]
        .map((part) => part?.trim())
        .filter((part): part is string => Boolean(part))
        .join(' · '),
      options: question.options.map((optionText, index) => ({ optionText, correctOption: index === correctIndex })),
    }
  })
}
