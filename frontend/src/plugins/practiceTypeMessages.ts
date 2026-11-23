import type { PracticeType } from '@/config/practiceActivities'
import type { AppLanguage } from '@/types/api'

export const practiceTypeMessages = {
  en: {
    FLASHCARDS: 'Flashcards',
    LISTENING: 'Listening',
    TRACING: 'Tracing',
    DRAG_DROP: 'Drag & Drop',
    MATCHING: 'Matching',
    TYPING: 'Typing',
  },
  ms: {
    FLASHCARDS: 'Kad imbas',
    LISTENING: 'Mendengar',
    TRACING: 'Menyurih',
    DRAG_DROP: 'Seret & Lepas',
    MATCHING: 'Padanan',
    TYPING: 'Menaip',
  },
  'zh-CN': {
    FLASHCARDS: '闪卡',
    LISTENING: '听力',
    TRACING: '描红',
    DRAG_DROP: '拖放',
    MATCHING: '配对',
    TYPING: '打字',
  },
} as const satisfies Record<AppLanguage, Record<PracticeType, string>>
