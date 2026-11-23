/** Practice activities in tab and library order. The same slugs identify them in the API and the router. */
export const PRACTICE_ACTIVITIES = {
  FLASHCARDS: { slug: 'flashcards', icon: 'copy', unitKey: 'practice.unitCards' },
  LISTENING: { slug: 'listening', icon: 'listening', unitKey: 'practice.unitQuestions' },
  TRACING: { slug: 'tracing', icon: 'pen', unitKey: 'practice.unitStrokes' },
  DRAG_DROP: { slug: 'drag-drop', icon: 'puzzle', unitKey: 'practice.unitSentences' },
  MATCHING: { slug: 'matching', icon: 'link', unitKey: 'practice.unitPairs' },
  TYPING: { slug: 'typing', icon: 'language', unitKey: 'practice.unitWords' },
} as const

export type PracticeType = keyof typeof PRACTICE_ACTIVITIES
export const PRACTICE_TYPES = Object.keys(PRACTICE_ACTIVITIES) as readonly PracticeType[]
