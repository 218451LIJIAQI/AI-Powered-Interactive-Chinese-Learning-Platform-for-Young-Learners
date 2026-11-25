import type { EditorQuestion, EditorQuiz, LessonEditor, QuestionPayload } from '@/types/adminClasses'
import type { AiQuizGeneratorResponse, QuizQuestionType } from '@/types/api'
import type { KeyPhrase } from '@/types/classes'
import { draftAnswerIndex } from '@/utils/aiPracticeQuiz'
import { isTypedQuestion, normalizeQuestionType } from '@/utils/classes'
import { truncateText } from '@/utils/truncateText'

const MAX_EXPLANATION_LENGTH = 2000

export interface LessonContentForm {
  title: string
  unitId: number | null
  keyPhrases: KeyPhrase[]
}

/** A lesson quiz has exactly this many questions: at this many, adding and AI drafts stop until one is deleted. */
export const QUIZ_SIZE = 5

/** Admins do not set marks: every new question is worth the same, and an edited one keeps what it had. */
export const DEFAULT_QUESTION_POINTS = 2

/** The quiz of a lesson: a lesson has one, made with its first question or its hint setting. */
export function lessonQuiz(editor: LessonEditor): EditorQuiz | null {
  return editor.quizzes[0] ?? null
}

/** Trims each phrase field and drops blank rows so Save draft and dirty detection compare the same payload. */
function normalizeKeyPhrases(phrases: readonly KeyPhrase[]): KeyPhrase[] {
  return phrases
    .map((phrase) => ({
      chinese: phrase.chinese.trim(),
      pinyin: phrase.pinyin.trim(),
      meaning: phrase.meaning.trim(),
    }))
    .filter((phrase) => phrase.chinese || phrase.pinyin || phrase.meaning)
}

export function savedLessonContent(lesson: LessonEditor): LessonContentForm {
  return {
    title: lesson.title,
    unitId: lesson.unitId,
    keyPhrases: lesson.keyPhrases.map((phrase) => ({ ...phrase })),
  }
}

/** Moving a unit takes effect immediately. Only the title and phrases belong to the publishable draft. */
export function lessonDraftFields(form: Pick<LessonContentForm, 'title' | 'keyPhrases'>) {
  return {
    title: form.title.trim(),
    keyPhrases: normalizeKeyPhrases(form.keyPhrases),
  }
}

/** Mirrors backend `LessonEditorAssembler.EditorContent.checklist().pinyinComplete()` against unsaved form edits. */
export function isLessonPinyinReady(editor: LessonEditor, draftPhrases: readonly KeyPhrase[], dirty: boolean): boolean {
  if (!dirty) return editor.checklist.pinyinComplete
  const hasPinyin = (pinyin: string | null) => Boolean(pinyin?.trim())
  return (
    draftPhrases.every((phrase) => hasPinyin(phrase.pinyin)) &&
    editor.activities.every(
      (activity) =>
        activity.cards.every((card) => hasPinyin(card.pinyin)) &&
        activity.characters.every((character) => hasPinyin(character.pinyin)) &&
        activity.pairs.every((pair) => hasPinyin(pair.pinyin)),
    )
  )
}

export function uncheckedAiDraftQuestionNumbers(quiz: EditorQuiz | null): number[] {
  if (!quiz || quiz.questionsLocked) return []
  return quiz.questions.filter((question) => question.aiDraft).map((question) => question.number)
}

/** A question as the editor would send it again: Undo of a delete puts it back with this. */
export function questionPayload(question: EditorQuestion): QuestionPayload {
  return {
    questionType: normalizeQuestionType(question.type),
    questionText: question.text,
    points: question.points,
    correctAnswerText: question.correctAnswerText,
    explanation: question.explanation,
    hint: question.hint,
    audioUrl: question.audioUrl,
    imageUrl: question.imageUrl,
    options: question.options.map((option) => ({
      optionText: option.text,
      correctOption: option.correct,
      feedback: option.feedback,
    })),
    aiDraft: question.aiDraft,
  }
}

/** The type the AI wrote ("fill blank", "true-false"), or multiple choice when options or media do not fit. */
function normalizeQuizQuestionType(value: string, optionCount: number): QuizQuestionType {
  const type = normalizeQuestionType(
    value
      .trim()
      .toUpperCase()
      .replace(/[\s-]+/g, '_'),
  )
  if (type === 'LISTEN_CHOOSE' || type === 'MATCH_WORD_IMAGE') return 'MULTIPLE_CHOICE'
  if (type === 'TRUE_FALSE' && optionCount !== 2) return 'MULTIPLE_CHOICE'
  return type
}

/** Maps an AI-assisted quiz draft (FRAI05) directly onto unchecked AI-draft question payloads ready to save. */
export function quizDraftToQuestionPayloads(response: AiQuizGeneratorResponse): QuestionPayload[] {
  return response.questions.map((question) => {
    const questionType = normalizeQuizQuestionType(question.questionType, question.options.length)
    const correctIndex = draftAnswerIndex(question.options, question.correctAnswer)
    const answerText = correctIndex < 0 ? question.correctAnswer : question.options[correctIndex]
    const explanation = truncateText(
      [question.explanation, question.pinyin, question.translation]
        .map((part) => part?.trim())
        .filter((part): part is string => Boolean(part))
        .join(' · '),
      MAX_EXPLANATION_LENGTH,
    )
    return {
      questionType,
      questionText: question.questionText,
      points: DEFAULT_QUESTION_POINTS,
      correctAnswerText: answerText || null,
      explanation: explanation || null,
      options: isTypedQuestion(questionType)
        ? []
        : question.options.map((optionText, index) => ({ optionText, correctOption: index === correctIndex })),
      aiDraft: true,
    }
  })
}
