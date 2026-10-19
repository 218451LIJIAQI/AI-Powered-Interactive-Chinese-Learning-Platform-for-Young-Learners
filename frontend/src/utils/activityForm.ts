/**
 * Editor state, detail mapping, validation and payload builders for the six practice-activity types.
 * Shared by the admin practice activity library and the teacher's own activities (D5) so both build
 * identical request bodies.
 */
import type {
  ActivityPayloadByType,
  ActivityResponseByType,
  AdminDragDropItemPayload,
  AdminMatchingGameItemPayload,
  AdminStoryQuestionPayload,
} from '@/api/services'
import type { ActivityDifficulty, AdminActivityType, LearningStage } from '@/types/api'
import { normalizeLearningStage } from '@/utils/learningStage'

export const ACTIVITY_TYPES = [
  'FLASHCARD',
  'MATCHING_GAME',
  'LISTENING_EXERCISE',
  'TRACING_EXERCISE',
  'DRAG_DROP_ACTIVITY',
  'STORY_ACTIVITY',
] as const satisfies readonly AdminActivityType[]

const ACTIVITY_TYPE_LABEL_KEYS = {
  FLASHCARD: 'flashcard',
  MATCHING_GAME: 'matchingGame',
  LISTENING_EXERCISE: 'listeningExercise',
  TRACING_EXERCISE: 'tracingExercise',
  DRAG_DROP_ACTIVITY: 'dragDropType',
  STORY_ACTIVITY: 'storyType',
} as const satisfies Record<AdminActivityType, string>

/** Key of the activity-type label in the adminActivityLibrary message bundle. */
export function activityTypeLabelKey(type: AdminActivityType): (typeof ACTIVITY_TYPE_LABEL_KEYS)[AdminActivityType] {
  return ACTIVITY_TYPE_LABEL_KEYS[type]
}

export interface MatchingItemDraft {
  leftText: string
  rightText: string
  leftImageUrl: string
  rightImageUrl: string
  pinyin: string
  sortOrder: number
}

export interface DragItemDraft {
  dragText: string
  targetZone: string
  pinyin: string
  imageUrl: string
  sortOrder: number
}

export interface StoryQuestionDraft {
  questionText: string
  optionsText: string
  explanation: string
  sortOrder: number
  /** 0-based index into the option lines (shown 1-based in the editor); null / missing = not set yet. */
  correctOptionIndex?: number | null
}

export interface ActivityEditorForm {
  type: AdminActivityType
  title: string
  learningStage: LearningStage | ''
  hskLevel: number | null
  difficulty: ActivityDifficulty | ''
  topic: string
  tagsText: string
  xpReward: number | null
  estimatedSeconds: number | null
  frontText: string
  backText: string
  pinyin: string
  englishMeaning: string
  malayMeaning: string
  imageUrl: string
  audioUrl: string
  sortOrder: number | null
  extraPayload: string
  questionPayload: string
  worksheetUrl: string
  targetCharacter: string
  meaning: string
  instructions: string
  storyText: string
  storyPinyin: string
  storyTranslation: string
  matchingItems: MatchingItemDraft[]
  dragItems: DragItemDraft[]
  storyQuestions: StoryQuestionDraft[]
}

/** Message keys of the adminActivityLibrary bundle returned by `validateActivityForm`. */
export type ActivityValidationKey =
  | 'titleRequired'
  | 'flashcardFrontRequired'
  | 'flashcardBackRequired'
  | 'matchingPairRequired'
  | 'dragItemRequired'
  | 'storyTextRequired'
  | 'storyQuestionRequired'

export function createEmptyMatchingItem(index: number): MatchingItemDraft {
  return { leftText: '', rightText: '', leftImageUrl: '', rightImageUrl: '', pinyin: '', sortOrder: index }
}

export function createEmptyDragItem(index: number): DragItemDraft {
  return { dragText: '', targetZone: '', pinyin: '', imageUrl: '', sortOrder: index }
}

export function createEmptyStoryQuestion(index: number): StoryQuestionDraft {
  return { questionText: '', optionsText: '', explanation: '', sortOrder: index, correctOptionIndex: null }
}

export function createEmptyActivityForm(type: AdminActivityType): ActivityEditorForm {
  return {
    type,
    title: '',
    learningStage: '',
    hskLevel: null,
    difficulty: '',
    topic: '',
    tagsText: '',
    xpReward: null,
    estimatedSeconds: null,
    frontText: '',
    backText: '',
    pinyin: '',
    englishMeaning: '',
    malayMeaning: '',
    imageUrl: '',
    audioUrl: '',
    sortOrder: null,
    extraPayload: '',
    questionPayload: '',
    worksheetUrl: '',
    targetCharacter: '',
    meaning: '',
    instructions: '',
    storyText: '',
    storyPinyin: '',
    storyTranslation: '',
    matchingItems: [createEmptyMatchingItem(0)],
    dragItems: [createEmptyDragItem(0)],
    storyQuestions: [createEmptyStoryQuestion(0)],
  }
}

export function optionsPayloadToText(payload: string): string {
  try {
    const parsed = JSON.parse(payload) as unknown
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item)).join('\n')
    }
  } catch {}
  return payload
}

/** The non-empty option lines of a story question, in order (the same list `optionsTextToPayload` stores). */
export function storyOptionList(text: string): string[] {
  return text
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

export function optionsTextToPayload(text: string): string {
  return JSON.stringify(storyOptionList(text))
}

/** The stored 0-based correct option, or null when unset or no longer pointing at an existing option line. */
export function storyCorrectOptionIndex(
  question: Pick<StoryQuestionDraft, 'optionsText' | 'correctOptionIndex'>,
): number | null {
  const index = question.correctOptionIndex
  if (typeof index !== 'number' || !Number.isInteger(index) || index < 0) return null
  return index < storyOptionList(question.optionsText).length ? index : null
}

/** Structured listening payload documented by the W4 contract (`options` optional, `answer` required). */
export const LISTENING_PAYLOAD_EXAMPLE =
  '{"prompt":"...","questions":[{"question":"...","options":["你好","再见"],"answer":"你好"}]}'

export type ListeningPayloadWarning =
  | { key: 'listeningWarningInvalidJson' }
  | { key: 'listeningWarningNoQuestions' }
  | { key: 'listeningWarningAnswerNotOption'; number: number }

/**
 * Non-blocking checks of the listening `questionPayload` (the backend enforces them on submit / publish):
 * it must parse, hold >= 1 question with an answer, and an answer must be one of its options when options are given.
 * An empty payload returns no warning (the help text explains the format).
 */
export function listeningPayloadWarnings(payload: string | null | undefined): ListeningPayloadWarning[] {
  const text = (payload ?? '').trim()
  if (!text) return []

  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    return [{ key: 'listeningWarningInvalidJson' }]
  }

  const questions =
    parsed && typeof parsed === 'object' && Array.isArray((parsed as { questions?: unknown }).questions)
      ? ((parsed as { questions: unknown[] }).questions as unknown[])
      : []
  const warnings: ListeningPayloadWarning[] = []
  let answered = 0

  questions.forEach((entry, index) => {
    if (!entry || typeof entry !== 'object') return
    const { answer, options } = entry as { answer?: unknown; options?: unknown }
    const answerText = typeof answer === 'string' ? answer.trim() : ''
    if (!answerText) return
    answered += 1
    const optionTexts = Array.isArray(options)
      ? options.map((option) => String(option ?? '').trim()).filter(Boolean)
      : []
    if (optionTexts.length && !optionTexts.includes(answerText)) {
      warnings.push({ key: 'listeningWarningAnswerNotOption', number: index + 1 })
    }
  })

  if (!answered) warnings.unshift({ key: 'listeningWarningNoQuestions' })
  return warnings
}

type SharedActivityDetail = {
  title?: string | null
  learningStage?: LearningStage | null
  hskLevel?: number | null
  difficulty?: ActivityDifficulty | null
  topic?: string | null
  tags: string[]
  xpReward?: number | null
  estimatedSeconds?: number | null
}

function sharedFields(type: AdminActivityType, detail: SharedActivityDetail): ActivityEditorForm {
  return {
    ...createEmptyActivityForm(type),
    title: detail.title || '',
    learningStage: normalizeLearningStage(detail.learningStage) ?? '',
    hskLevel: detail.hskLevel ?? null,
    difficulty: detail.difficulty ?? '',
    topic: detail.topic || '',
    tagsText: detail.tags.join(', '),
    xpReward: detail.xpReward ?? null,
    estimatedSeconds: detail.estimatedSeconds ?? null,
  }
}

/** Maps a saved activity record onto the editor state. */
export function activityFormFromDetail<T extends AdminActivityType>(
  type: T,
  detail: ActivityResponseByType[T],
): ActivityEditorForm {
  switch (type) {
    case 'FLASHCARD': {
      const item = detail as ActivityResponseByType['FLASHCARD']
      return {
        ...sharedFields(type, item),
        frontText: item.frontText,
        backText: item.backText,
        pinyin: item.pinyin || '',
        englishMeaning: item.englishMeaning || '',
        malayMeaning: item.malayMeaning || '',
        imageUrl: item.imageUrl || '',
        audioUrl: item.audioUrl || '',
        sortOrder: item.sortOrder ?? null,
        extraPayload: item.extraPayload || '',
      }
    }
    case 'MATCHING_GAME': {
      const item = detail as ActivityResponseByType['MATCHING_GAME']
      return {
        ...sharedFields(type, item),
        matchingItems: item.items.length
          ? item.items.map((pair, index) => ({
              leftText: pair.leftText,
              rightText: pair.rightText,
              leftImageUrl: pair.leftImageUrl || '',
              rightImageUrl: pair.rightImageUrl || '',
              pinyin: pair.pinyin || '',
              sortOrder: pair.sortOrder ?? index,
            }))
          : [createEmptyMatchingItem(0)],
      }
    }
    case 'LISTENING_EXERCISE': {
      const item = detail as ActivityResponseByType['LISTENING_EXERCISE']
      return {
        ...sharedFields(type, item),
        audioUrl: item.audioUrl || '',
        questionPayload: item.questionPayload || '',
      }
    }
    case 'TRACING_EXERCISE': {
      const item = detail as ActivityResponseByType['TRACING_EXERCISE']
      return {
        ...sharedFields(type, item),
        worksheetUrl: item.worksheetUrl || '',
        targetCharacter: item.targetCharacter || '',
        pinyin: item.pinyin || '',
        meaning: item.meaning || '',
        instructions: item.instructions || '',
      }
    }
    case 'DRAG_DROP_ACTIVITY': {
      const item = detail as ActivityResponseByType['DRAG_DROP_ACTIVITY']
      return {
        ...sharedFields(type, item),
        instructions: item.instructions || '',
        dragItems: item.items.length
          ? item.items.map((drag, index) => ({
              dragText: drag.dragText,
              targetZone: drag.targetZone,
              pinyin: drag.pinyin || '',
              imageUrl: drag.imageUrl || '',
              sortOrder: drag.sortOrder ?? index,
            }))
          : [createEmptyDragItem(0)],
      }
    }
    default: {
      const item = detail as ActivityResponseByType['STORY_ACTIVITY']
      return {
        ...sharedFields('STORY_ACTIVITY', item),
        instructions: item.instructions || '',
        storyText: item.storyText,
        storyPinyin: item.pinyin || '',
        storyTranslation: item.translation || '',
        imageUrl: item.imageUrl || '',
        storyQuestions: item.questions.length
          ? item.questions.map((question, index) => ({
              questionText: question.questionText,
              optionsText: optionsPayloadToText(question.optionsPayload),
              explanation: question.explanation || '',
              sortOrder: question.sortOrder ?? index,
              correctOptionIndex: question.correctOptionIndex ?? null,
            }))
          : [createEmptyStoryQuestion(0)],
      }
    }
  }
}

/** Client-side mirror of the save rules; the backend applies the stricter completeness rules on submit/publish. */
export function validateActivityForm(form: ActivityEditorForm): ActivityValidationKey | null {
  if (form.type !== 'FLASHCARD' && !form.title.trim()) return 'titleRequired'

  switch (form.type) {
    case 'FLASHCARD':
      if (!form.frontText.trim()) return 'flashcardFrontRequired'
      if (!form.backText.trim()) return 'flashcardBackRequired'
      return null
    case 'MATCHING_GAME':
      return form.matchingItems.some((item) => item.leftText.trim() && item.rightText.trim())
        ? null
        : 'matchingPairRequired'
    case 'DRAG_DROP_ACTIVITY':
      return form.dragItems.some((item) => item.dragText.trim() && item.targetZone.trim()) ? null : 'dragItemRequired'
    case 'STORY_ACTIVITY':
      if (!form.storyText.trim()) return 'storyTextRequired'
      return form.storyQuestions.some((item) => item.questionText.trim() && item.optionsText.trim())
        ? null
        : 'storyQuestionRequired'
    default:
      return null
  }
}

function optionalString(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function optionalNumber(value: number | null): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function parseTags(tagsText: string): string[] {
  return tagsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function sharedPayload(form: ActivityEditorForm) {
  return {
    learningStage: form.learningStage || null,
    hskLevel: optionalNumber(form.hskLevel),
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(form.tagsText),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
  }
}

/** Builds the request body for the form's activity type. */
export function buildActivityPayload(form: ActivityEditorForm): ActivityPayloadByType[AdminActivityType] {
  switch (form.type) {
    case 'FLASHCARD':
      return {
        title: optionalString(form.title),
        frontText: form.frontText.trim(),
        backText: form.backText.trim(),
        pinyin: optionalString(form.pinyin),
        englishMeaning: optionalString(form.englishMeaning),
        malayMeaning: optionalString(form.malayMeaning),
        imageUrl: optionalString(form.imageUrl),
        audioUrl: optionalString(form.audioUrl),
        sortOrder: optionalNumber(form.sortOrder),
        extraPayload: optionalString(form.extraPayload),
        ...sharedPayload(form),
      }
    case 'MATCHING_GAME':
      return {
        title: form.title.trim(),
        ...sharedPayload(form),
        items: form.matchingItems
          .filter((item) => item.leftText.trim() && item.rightText.trim())
          .map(
            (item): AdminMatchingGameItemPayload => ({
              leftText: item.leftText.trim(),
              rightText: item.rightText.trim(),
              leftImageUrl: optionalString(item.leftImageUrl),
              rightImageUrl: optionalString(item.rightImageUrl),
              pinyin: optionalString(item.pinyin),
              sortOrder: optionalNumber(item.sortOrder),
            }),
          ),
      }
    case 'LISTENING_EXERCISE':
      return {
        title: form.title.trim(),
        audioUrl: optionalString(form.audioUrl),
        questionPayload: optionalString(form.questionPayload),
        ...sharedPayload(form),
      }
    case 'TRACING_EXERCISE':
      return {
        title: form.title.trim(),
        worksheetUrl: optionalString(form.worksheetUrl),
        targetCharacter: optionalString(form.targetCharacter),
        pinyin: optionalString(form.pinyin),
        meaning: optionalString(form.meaning),
        instructions: optionalString(form.instructions),
        ...sharedPayload(form),
      }
    case 'DRAG_DROP_ACTIVITY':
      return {
        title: form.title.trim(),
        instructions: optionalString(form.instructions),
        ...sharedPayload(form),
        items: form.dragItems
          .filter((item) => item.dragText.trim() && item.targetZone.trim())
          .map(
            (item): AdminDragDropItemPayload => ({
              dragText: item.dragText.trim(),
              targetZone: item.targetZone.trim(),
              pinyin: optionalString(item.pinyin),
              imageUrl: optionalString(item.imageUrl),
              sortOrder: optionalNumber(item.sortOrder),
            }),
          ),
      }
    case 'STORY_ACTIVITY':
      return {
        title: form.title.trim(),
        instructions: optionalString(form.instructions),
        storyText: form.storyText.trim(),
        pinyin: optionalString(form.storyPinyin),
        translation: optionalString(form.storyTranslation),
        imageUrl: optionalString(form.imageUrl),
        ...sharedPayload(form),
        questions: form.storyQuestions
          .filter((item) => item.questionText.trim() && item.optionsText.trim())
          .map(
            (item): AdminStoryQuestionPayload => ({
              questionText: item.questionText.trim(),
              optionsPayload: optionsTextToPayload(item.optionsText),
              explanation: optionalString(item.explanation),
              sortOrder: optionalNumber(item.sortOrder),
              correctOptionIndex: storyCorrectOptionIndex(item),
            }),
          ),
      }
  }
}
