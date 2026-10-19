/**
 * Maps AI-assisted teacher drafts (D6) onto editable form state. Nothing here saves anything: the teacher
 * reviews the filled form and saves it through the normal create / submit-for-review flow.
 */
import { teacherAiDraftMessages } from '@/plugins/teacherAiDraftMessages'
import type { AiQuizGeneratorResponse, AiStoryGeneratorResponse, AppLanguage, QuizQuestionType } from '@/types/api'
import { createEmptyStoryQuestion, type ActivityEditorForm, type StoryQuestionDraft } from '@/utils/activityForm'
import { answersMatch, correctOptionIndexes, storyTranslations, vocabularyMeaning } from '@/utils/aiPracticeQuiz'

/** Backend limit of the story activity `instructions` field (AdminStoryActivityRequest). */
export const STORY_INSTRUCTIONS_MAX_LENGTH = 1000

export interface QuizQuestionDraft {
  questionType: QuizQuestionType
  questionText: string
  correctAnswerText: string
  points: number
  explanation: string
  options: Array<{ optionText: string; correctOption: boolean; displayOrder: number }>
}

const QUIZ_QUESTION_TYPES: readonly QuizQuestionType[] = [
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'FILL_BLANK',
  'MATCH_WORD_MEANING',
  'MATCH_WORD_IMAGE',
  'LISTEN_CHOOSE',
  'REARRANGE_SENTENCE',
  'CHOOSE_PINYIN',
  'CHOOSE_TONE',
  'CHARACTER_MEANING',
]

function clean(value: string | null | undefined): string {
  return (value ?? '').trim()
}

export function normalizeQuizQuestionType(value: string | null | undefined): QuizQuestionType {
  const normalized = clean(value)
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  return (QUIZ_QUESTION_TYPES as readonly string[]).includes(normalized)
    ? (normalized as QuizQuestionType)
    : 'MULTIPLE_CHOICE'
}

/** Turns AI quiz questions into question-builder rows; questions without text are dropped. */
export function quizDraftToQuestionDrafts(response: AiQuizGeneratorResponse): QuizQuestionDraft[] {
  return (response.questions ?? [])
    .filter((question) => clean(question.questionText))
    .map((question) => {
      const optionTexts = (question.options ?? []).map((option) => clean(option)).filter(Boolean)
      // Normalised match, or a single-letter key (A-D) pointing at an option; the teacher still reviews it.
      const correctIndexes = correctOptionIndexes(optionTexts, question.correctAnswer)
      const rawAnswer = clean(question.correctAnswer)
      const letterResolved = correctIndexes.length === 1 && !answersMatch(optionTexts[correctIndexes[0]], rawAnswer)
      const correctAnswer = letterResolved ? optionTexts[correctIndexes[0]] : rawAnswer
      const options = optionTexts.map((optionText, index) => ({
        optionText,
        correctOption: correctIndexes.includes(index),
        displayOrder: index,
      }))
      const explanation = [clean(question.explanation), clean(question.pinyin), clean(question.translation)]
        .filter(Boolean)
        .join(' · ')

      return {
        questionType: normalizeQuizQuestionType(question.questionType),
        questionText: clean(question.questionText),
        correctAnswerText: correctAnswer,
        points: 1,
        explanation,
        options,
      }
    })
}

/** One line per AI vocabulary item: `食堂 (shítáng) - canteen`, meaning in the interface language. */
export function storyVocabularyLines(response: AiStoryGeneratorResponse, language: AppLanguage): string[] {
  return (response.vocabularyList ?? [])
    .filter((item) => clean(item.chineseWord))
    .map((item) => {
      const pinyin = clean(item.pinyin)
      const meaning = vocabularyMeaning(item, language)
      return [clean(item.chineseWord), pinyin ? `(${pinyin})` : '', meaning ? `- ${meaning}` : '']
        .filter(Boolean)
        .join(' ')
    })
}

/**
 * Key-vocabulary block for the story `instructions` field: a heading line plus one line per word, cut at a whole
 * line so it stays within the backend limit. Empty when the draft has no vocabulary.
 */
export function storyVocabularyBlock(
  response: AiStoryGeneratorResponse,
  language: AppLanguage,
  heading: string,
): string {
  const lines = storyVocabularyLines(response, language)
  if (!lines.length) return ''
  let block = heading.trim()
  for (const line of lines) {
    const next = `${block}\n${line}`
    if (next.length > STORY_INSTRUCTIONS_MAX_LENGTH) break
    block = next
  }
  return block === heading.trim() ? '' : block
}

/**
 * Keeps instructions the teacher wrote; only empty instructions, or a block from an earlier AI draft (starting with the
 * vocabulary heading in any interface language), are replaced by the new vocabulary block.
 */
function storyInstructions(current: string, vocabularyBlock: string): string {
  const existing = clean(current)
  if (!vocabularyBlock) return current
  const headings = Object.values(teacherAiDraftMessages).map((messages) => messages.vocabularyHeading)
  if (!existing || headings.some((heading) => existing.startsWith(heading))) return vocabularyBlock
  return current
}

/**
 * Fills a story-activity form from an AI story draft. The story question format has no answer field, so the
 * suggested answer is written into the explanation (prefixed with `answerLabel`) for the teacher to check. The AI
 * vocabulary list goes into the instructions field (saved with the story) unless the teacher already wrote some.
 */
export function applyStoryDraft(
  form: ActivityEditorForm,
  response: AiStoryGeneratorResponse,
  options: { language: AppLanguage; answerLabel: string; vocabularyHeading?: string },
): ActivityEditorForm {
  const heading = options.vocabularyHeading ?? teacherAiDraftMessages[options.language].vocabularyHeading
  const questions: StoryQuestionDraft[] = (response.comprehensionQuestions ?? [])
    .filter((question) => clean(question.question))
    .map((question, index) => {
      const answer = clean(question.answer)
      const explanation = [answer ? `${options.answerLabel} ${answer}` : '', clean(question.explanation)]
        .filter(Boolean)
        .join(' — ')
      const optionList = (question.options ?? []).map((option) => clean(option)).filter(Boolean)
      // Pre-select the AI's suggested answer as the correct option (0-based); the teacher still reviews it.
      const answerIndex = answer ? (correctOptionIndexes(optionList, answer)[0] ?? -1) : -1
      return {
        questionText: clean(question.question),
        optionsText: optionList.join('\n'),
        explanation,
        sortOrder: index,
        correctOptionIndex: answerIndex >= 0 ? answerIndex : null,
      }
    })

  return {
    ...form,
    type: 'STORY_ACTIVITY',
    title: clean(response.title) || form.title,
    instructions: storyInstructions(form.instructions, storyVocabularyBlock(response, options.language, heading)),
    storyText: clean(response.chineseStory),
    storyPinyin: clean(response.pinyin),
    storyTranslation: storyTranslations(response, options.language)[0]?.text ?? '',
    storyQuestions: questions.length ? questions : [createEmptyStoryQuestion(0)],
  }
}
