import type { QuestionView } from '@/types/learn'

/**
 * A question answered by ear: one of Learn's listening types, or a Practice listening item, which has no type, only
 * its audio and no characters. The server's `HintBuilder` reads a Practice listening item the same way.
 */
export function isListeningQuestion(question: QuestionView): boolean {
  const { type } = question
  if (type === 'LISTEN_CHOOSE' || type === 'LISTEN_BUILD') return true
  return type === null && Boolean(question.audioText) && !question.hanzi
}
