/**
 * Preview-only helpers for the demo practice activities.
 *
 * The demo fixtures are authored with their answer keys (like the teacher/admin editor), then converted to the
 * student shapes of the W4 contract, which never carry answer keys. `gradeDemoActivity` mirrors the server-side
 * completion rules so preview mode can show a realistic result; nothing is saved and no XP is awarded.
 */
import type {
  DragDropCompletionRequest,
  ListeningCompletionRequest,
  MatchingCompletionRequest,
  StoryCompletionRequest,
  StudentActivityCompletionResponse,
  StudentDragDropActivityResponse,
  StudentListeningExerciseResponse,
  StudentMatchingGameResponse,
  StudentStoryActivityResponse,
  TracingCompletionRequest,
} from '@/types/api'

type StudentMeta<T> = Omit<T, 'leftItems' | 'rightOptions' | 'items' | 'zones' | 'questions'>

export interface DemoMatchingGameSource extends StudentMeta<StudentMatchingGameResponse> {
  items: Array<{
    id: number
    leftText: string
    rightText: string
    leftImageUrl?: string | null
    rightImageUrl?: string | null
    pinyin?: string | null
    sortOrder?: number | null
  }>
}

export interface DemoDragDropSource extends StudentMeta<StudentDragDropActivityResponse> {
  items: Array<{
    id: number
    dragText: string
    targetZone: string
    pinyin?: string | null
    imageUrl?: string | null
    sortOrder?: number | null
  }>
}

export interface DemoListeningSource extends Omit<
  StudentListeningExerciseResponse,
  'questions' | 'prompt' | 'freeResponse'
> {
  prompt: string | null
  questions: Array<{ question: string; options?: string[]; answer: string }>
}

export interface DemoStorySource extends StudentMeta<StudentStoryActivityResponse> {
  questions: Array<{
    id: number
    questionText: string
    optionsPayload: string
    explanation?: string | null
    sortOrder?: number | null
    /** 0-based index into the authored options (defaults to 0). */
    correctOptionIndex?: number
  }>
}

/** Deterministic "shuffle" (rotate by the activity id) so previews and tests stay stable. */
function rotate<T>(values: readonly T[], seed: number): T[] {
  if (values.length < 2) return [...values]
  const offset = (seed % (values.length - 1)) + 1
  return [...values.slice(offset), ...values.slice(0, offset)]
}

function splitOptions(payload: string): string[] {
  return payload
    .split(/\r?\n/)
    .map((option) => option.trim())
    .filter(Boolean)
}

export function toStudentMatchingGame(source: DemoMatchingGameSource): StudentMatchingGameResponse {
  const { items, ...meta } = source
  return {
    ...meta,
    leftItems: items.map((item) => ({
      id: item.id,
      text: item.leftText,
      pinyin: item.pinyin ?? null,
      imageUrl: item.leftImageUrl ?? null,
      sortOrder: item.sortOrder ?? null,
    })),
    rightOptions: rotate(
      items.map((item) => ({ text: item.rightText, imageUrl: item.rightImageUrl ?? null })),
      source.id,
    ),
  }
}

export function toStudentDragDrop(source: DemoDragDropSource): StudentDragDropActivityResponse {
  const { items, ...meta } = source
  return {
    ...meta,
    items: rotate(
      items.map((item) => ({
        id: item.id,
        dragText: item.dragText,
        pinyin: item.pinyin ?? null,
        imageUrl: item.imageUrl ?? null,
        sortOrder: item.sortOrder ?? null,
      })),
      source.id,
    ),
    zones: Array.from(new Set(items.map((item) => item.targetZone))).sort((a, b) => a.localeCompare(b)),
  }
}

export function toStudentListening(source: DemoListeningSource): StudentListeningExerciseResponse {
  return {
    ...source,
    questions: source.questions.map((question, index) => ({
      index,
      questionText: question.question,
      options: question.options ?? [],
    })),
    freeResponse: source.questions.length === 0,
  }
}

export function toStudentStory(source: DemoStorySource): StudentStoryActivityResponse {
  const { questions, ...meta } = source
  return {
    ...meta,
    questions: questions.map((question) => ({
      id: question.id,
      questionText: question.questionText,
      options: splitOptions(question.optionsPayload).map((text, index) => ({ index, text })),
      sortOrder: question.sortOrder ?? null,
    })),
  }
}

export interface DemoActivitySources {
  matching: readonly DemoMatchingGameSource[]
  dragDrop: readonly DemoDragDropSource[]
  listening: readonly DemoListeningSource[]
  story: readonly DemoStorySource[]
}

export type DemoGradeRequest =
  | { type: 'FLASHCARD' }
  | { type: 'TRACING_EXERCISE'; body?: TracingCompletionRequest }
  | { type: 'MATCHING_GAME'; body: MatchingCompletionRequest }
  | { type: 'DRAG_DROP_ACTIVITY'; body: DragDropCompletionRequest }
  | { type: 'LISTENING_EXERCISE'; body: ListeningCompletionRequest }
  | { type: 'STORY_ACTIVITY'; body: StoryCompletionRequest }

function normalise(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function result(
  activityId: number,
  type: string,
  xp: number,
  results: StudentActivityCompletionResponse['results'],
): StudentActivityCompletionResponse {
  const correctCount = results.filter((item) => item.correct).length
  const passed = results.length > 0 && correctCount === results.length
  return {
    activityId,
    type,
    passed,
    correctCount,
    totalCount: results.length,
    earnedPoints: 0,
    totalPoints: xp,
    firstCompletion: false,
    results: passed ? results : results.map((item) => ({ ...item, explanation: null })),
  }
}

/** Grades a preview attempt like the server would. Returns null when the activity is not a demo fixture. */
export function gradeDemoActivity(
  sources: DemoActivitySources,
  activityId: number,
  request: DemoGradeRequest,
): StudentActivityCompletionResponse | null {
  if (request.type === 'FLASHCARD') {
    return result(activityId, request.type, 0, [{ key: 'completed', correct: true }])
  }

  if (request.type === 'TRACING_EXERCISE') {
    // Tracing is practice only: any finished stroke completes it (never graded).
    const strokeCount = request.body?.strokeCount ?? 1
    return result(activityId, request.type, 0, [{ key: 'completed', correct: strokeCount >= 1 }])
  }

  if (request.type === 'MATCHING_GAME') {
    const source = sources.matching.find((item) => item.id === activityId)
    if (!source) return null
    return result(
      activityId,
      request.type,
      source.xpReward ?? 0,
      source.items.map((item) => {
        const match = request.body.matches.find((entry) => entry.leftItemId === item.id)
        return { key: String(item.id), correct: normalise(match?.rightText) === normalise(item.rightText) }
      }),
    )
  }

  if (request.type === 'DRAG_DROP_ACTIVITY') {
    const source = sources.dragDrop.find((item) => item.id === activityId)
    if (!source) return null
    return result(
      activityId,
      request.type,
      source.xpReward ?? 0,
      source.items.map((item) => {
        const placement = request.body.placements.find((entry) => entry.itemId === item.id)
        return { key: String(item.id), correct: normalise(placement?.zone) === normalise(item.targetZone) }
      }),
    )
  }

  if (request.type === 'LISTENING_EXERCISE') {
    const source = sources.listening.find((item) => item.id === activityId)
    if (!source) return null
    const body = request.body
    if ('responseText' in body) {
      return result(activityId, request.type, source.xpReward ?? 0, [
        { key: 'response', correct: Boolean(body.responseText.trim()) },
      ])
    }
    return result(
      activityId,
      request.type,
      source.xpReward ?? 0,
      source.questions.map((question, index) => {
        const answer = body.answers.find((entry) => entry.questionIndex === index)
        return { key: String(index), correct: normalise(answer?.answerText) === normalise(question.answer) }
      }),
    )
  }

  if (request.type !== 'STORY_ACTIVITY') return null
  const source = sources.story.find((item) => item.id === activityId)
  if (!source) return null
  const body = request.body
  return result(
    activityId,
    request.type,
    source.xpReward ?? 0,
    source.questions.map((question) => {
      const answer = body.answers.find((entry) => entry.questionId === question.id)
      return {
        key: String(question.id),
        correct: answer?.optionIndex === (question.correctOptionIndex ?? 0),
        explanation: question.explanation ?? null,
      }
    }),
  )
}
