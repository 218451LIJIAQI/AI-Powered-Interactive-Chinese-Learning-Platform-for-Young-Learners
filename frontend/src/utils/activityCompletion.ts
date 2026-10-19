/**
 * Pure helpers for the student practice activity players (W4 contract, section 5).
 *
 * The student GET payloads carry no answer keys, so the players only collect the learner's answers and these helpers
 * turn them into the completion request bodies of `POST /students/activities/{slug}/{id}/complete`. The server grades
 * the attempt and returns per-item results (`key` = left item id / item id / question index / question id).
 */
import type {
  DragDropCompletionRequest,
  ListeningCompletionRequest,
  MatchingCompletionRequest,
  StoryCompletionRequest,
  StudentActivityCompletionResponse,
  StudentActivityItemResult,
  StudentDragDropItemResponse,
  StudentListeningExerciseResponse,
  StudentListeningQuestionResponse,
  StudentMatchingLeftItemResponse,
  StudentMatchingRightOptionResponse,
  StudentStoryQuestionResponse,
  TracingCompletionRequest,
} from '@/types/api'
import { containsHan } from '@/utils/cjkText'

/** What a player hands to the page when the learner submits. */
export type ActivitySubmission =
  | { type: 'FLASHCARD' }
  | { type: 'TRACING_EXERCISE'; body: TracingCompletionRequest }
  | { type: 'MATCHING_GAME'; body: MatchingCompletionRequest }
  | { type: 'DRAG_DROP_ACTIVITY'; body: DragDropCompletionRequest }
  | { type: 'LISTENING_EXERCISE'; body: ListeningCompletionRequest }
  | { type: 'STORY_ACTIVITY'; body: StoryCompletionRequest }

/** `zh-CN` when the text contains Chinese characters (for `lang` + the larger CJK font), otherwise undefined. */
export function cjkLang(text: string | null | undefined): 'zh-CN' | undefined {
  return containsHan(text) ? 'zh-CN' : undefined
}

/* ---------------------------------------------------------------- matching */

/** Left item id -> index into `rightOptions` (options have no ids; the same text may appear twice). */
export type MatchingSelection = Readonly<Record<number, number>>

/** Pairs a left item with a right option; an option already used by another left item moves to this one. */
export function assignMatch(selection: MatchingSelection, leftItemId: number, rightIndex: number): MatchingSelection {
  const next: Record<number, number> = {}
  for (const [key, value] of Object.entries(selection)) {
    if (value !== rightIndex) next[Number(key)] = value
  }
  next[leftItemId] = rightIndex
  return next
}

export function unassignMatch(selection: MatchingSelection, leftItemId: number): MatchingSelection {
  const next: Record<number, number> = { ...selection }
  delete next[leftItemId]
  return next
}

function validRightIndex(rightOptions: readonly StudentMatchingRightOptionResponse[], index: unknown): boolean {
  return typeof index === 'number' && Number.isInteger(index) && index >= 0 && index < rightOptions.length
}

export function isMatchingReady(
  leftItems: readonly StudentMatchingLeftItemResponse[],
  rightOptions: readonly StudentMatchingRightOptionResponse[],
  selection: MatchingSelection,
): boolean {
  return leftItems.length > 0 && leftItems.every((item) => validRightIndex(rightOptions, selection[item.id]))
}

export function buildMatchingCompletion(
  leftItems: readonly StudentMatchingLeftItemResponse[],
  rightOptions: readonly StudentMatchingRightOptionResponse[],
  selection: MatchingSelection,
): MatchingCompletionRequest {
  return {
    matches: leftItems
      .filter((item) => validRightIndex(rightOptions, selection[item.id]))
      .map((item) => ({ leftItemId: item.id, rightText: rightOptions[selection[item.id]].text })),
  }
}

/* ----------------------------------------------------------- drag and drop */

/** Item id -> zone name. */
export type DragDropPlacements = Readonly<Record<number, string>>

/** Places an item in a zone, or takes it back out when `zone` is null/empty. */
export function placeItem(placements: DragDropPlacements, itemId: number, zone: string | null): DragDropPlacements {
  const next: Record<number, string> = { ...placements }
  if (zone) next[itemId] = zone
  else delete next[itemId]
  return next
}

export function isDragDropReady(
  items: readonly StudentDragDropItemResponse[],
  zones: readonly string[],
  placements: DragDropPlacements,
): boolean {
  return items.length > 0 && items.every((item) => zones.includes(placements[item.id] ?? ''))
}

export function buildDragDropCompletion(
  items: readonly StudentDragDropItemResponse[],
  zones: readonly string[],
  placements: DragDropPlacements,
): DragDropCompletionRequest {
  return {
    placements: items
      .filter((item) => zones.includes(placements[item.id] ?? ''))
      .map((item) => ({ itemId: item.id, zone: placements[item.id] })),
  }
}

/* --------------------------------------------------------------- listening */

/** Question index -> typed or chosen answer. */
export type ListeningAnswers = Readonly<Record<number, string>>

type ListeningShape = Pick<StudentListeningExerciseResponse, 'questions' | 'freeResponse'>

export function isListeningReady(exercise: ListeningShape, answers: ListeningAnswers, responseText: string): boolean {
  if (exercise.freeResponse) return responseText.trim().length > 0
  return (
    exercise.questions.length > 0 &&
    exercise.questions.every((question) => (answers[question.index] ?? '').trim().length > 0)
  )
}

export function buildListeningCompletion(
  exercise: ListeningShape,
  answers: ListeningAnswers,
  responseText: string,
): ListeningCompletionRequest {
  if (exercise.freeResponse) return { responseText: responseText.trim() }
  return {
    answers: exercise.questions
      .filter((question) => (answers[question.index] ?? '').trim().length > 0)
      .map((question) => ({ questionIndex: question.index, answerText: answers[question.index].trim() })),
  }
}

/* ------------------------------------------------------------------- story */

/** Question id -> chosen option index. */
export type StoryAnswers = Readonly<Record<number, number>>

function validOptionIndex(question: StudentStoryQuestionResponse, index: unknown): boolean {
  return typeof index === 'number' && question.options.some((option) => option.index === index)
}

export function isStoryReady(questions: readonly StudentStoryQuestionResponse[], answers: StoryAnswers): boolean {
  return questions.length > 0 && questions.every((question) => validOptionIndex(question, answers[question.id]))
}

export function buildStoryCompletion(
  questions: readonly StudentStoryQuestionResponse[],
  answers: StoryAnswers,
): StoryCompletionRequest {
  return {
    answers: questions
      .filter((question) => validOptionIndex(question, answers[question.id]))
      .map((question) => ({ questionId: question.id, optionIndex: answers[question.id] })),
  }
}

/* ----------------------------------------------------------------- tracing */

export function isTracingReady(strokeCount: number): boolean {
  return Number.isFinite(strokeCount) && strokeCount >= 1
}

export function buildTracingCompletion(strokeCount: number): TracingCompletionRequest {
  return { strokeCount: Math.max(1, Math.floor(strokeCount)) }
}

/* ----------------------------------------------------------------- results */

export interface ActivityItemLabel {
  key: string
  label: string
}

export interface ActivityResultRow {
  key: string
  label: string | null
  correct: boolean
  /** Only filled when the attempt passed (the server sends explanations only then). */
  explanation: string | null
}

export function matchingResultLabels(leftItems: readonly StudentMatchingLeftItemResponse[]): ActivityItemLabel[] {
  return leftItems.map((item) => ({ key: String(item.id), label: item.text }))
}

export function dragDropResultLabels(items: readonly StudentDragDropItemResponse[]): ActivityItemLabel[] {
  return items.map((item) => ({ key: String(item.id), label: item.dragText }))
}

export function listeningResultLabels(questions: readonly StudentListeningQuestionResponse[]): ActivityItemLabel[] {
  return questions.map((question) => ({ key: String(question.index), label: question.questionText }))
}

export function storyResultLabels(questions: readonly StudentStoryQuestionResponse[]): ActivityItemLabel[] {
  return questions.map((question) => ({ key: String(question.id), label: question.questionText }))
}

/**
 * Maps the server's per-item results back to the items the learner saw (in the order of `labels`); results with an
 * unknown key are kept at the end with `label: null`. Explanations are dropped unless the attempt passed.
 */
export function mapResultsToItems(
  results: readonly StudentActivityItemResult[],
  labels: readonly ActivityItemLabel[],
  passed: boolean,
): ActivityResultRow[] {
  const byKey = new Map(results.map((entry) => [String(entry.key), entry]))
  const toRow = (entry: StudentActivityItemResult, label: string | null): ActivityResultRow => ({
    key: String(entry.key),
    label,
    correct: Boolean(entry.correct),
    explanation: passed ? entry.explanation?.trim() || null : null,
  })

  const known = labels.flatMap((item) => {
    const entry = byKey.get(item.key)
    return entry ? [toRow(entry, item.label)] : []
  })
  const labelKeys = new Set(labels.map((item) => item.key))
  const unknown = results.filter((entry) => !labelKeys.has(String(entry.key))).map((entry) => toRow(entry, null))
  return [...known, ...unknown]
}

export type CompletionOutcome = 'preview' | 'notYet' | 'earned' | 'completed' | 'alreadyCompleted'

/** How the XP part of a result should be described. */
export function completionOutcome(result: StudentActivityCompletionResponse, preview: boolean): CompletionOutcome {
  if (preview) return 'preview'
  if (!result.passed) return 'notYet'
  if (result.earnedPoints > 0) return 'earned'
  return result.firstCompletion ? 'completed' : 'alreadyCompleted'
}
