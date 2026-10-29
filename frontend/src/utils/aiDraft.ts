/**
 * Maps AI-assisted teacher drafts (FRAI05) onto editable form state. Nothing here saves anything: the teacher
 * reviews the filled form and saves it through the normal create / submit-for-review flow.
 */
import type { AiQuizGeneratorResponse, QuizQuestionType } from '@/types/api'
import { clean, resolveAnswerKey } from '@/utils/aiPracticeQuiz'
import { QUESTION_TYPES } from '@/utils/classes'

interface QuizOptionDraft {
  optionText: string
  correctOption: boolean
}

interface QuizQuestionDraft {
  questionType: QuizQuestionType
  questionText: string
  correctAnswerText: string
  explanation: string
  options: QuizOptionDraft[]
}

function normalizeQuizQuestionType(value: string | null | undefined): QuizQuestionType {
  const normalized = clean(value)
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  return QUESTION_TYPES.includes(normalized) ? (normalized as QuizQuestionType) : 'MULTIPLE_CHOICE'
}

/** Turns AI quiz questions into question-builder rows; questions without text are dropped. */
export function quizDraftToQuestionDrafts(response: AiQuizGeneratorResponse): QuizQuestionDraft[] {
  return (response.questions ?? [])
    .filter((question) => clean(question.questionText))
    .map((question) => {
      const optionTexts = (question.options ?? []).map((option) => clean(option)).filter(Boolean)
      // Normalised match, or a single-letter key (A-D) pointing at an option; the teacher still reviews it.
      const { indexes: correctIndexes, display: correctAnswer } = resolveAnswerKey(
        optionTexts,
        clean(question.correctAnswer),
      )
      const options = optionTexts.map((optionText, index) => ({
        optionText,
        correctOption: correctIndexes.includes(index),
      }))
      const explanation = [clean(question.explanation), clean(question.pinyin), clean(question.translation)]
        .filter(Boolean)
        .join(' · ')

      return {
        questionType: normalizeQuizQuestionType(question.questionType),
        questionText: clean(question.questionText),
        correctAnswerText: correctAnswer,
        explanation,
        options,
      }
    })
}
