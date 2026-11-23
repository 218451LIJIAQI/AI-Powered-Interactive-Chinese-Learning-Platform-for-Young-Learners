import { describe, expect, it } from 'vitest'

import type { EditorQuestion } from '@/types/adminClasses'

import { questionPayload } from './lessonEditor'

describe('questionPayload', () => {
  it('sends a question back as it was, so Undo restores it unchanged, an unchecked AI draft as one', () => {
    const question: EditorQuestion = {
      id: 30,
      number: 5,
      type: 'LISTEN_CHOOSE',
      text: 'Listen: which word do you hear?',
      points: 3,
      correctAnswerText: null,
      explanation: '每天 (měitiān) means every day.',
      hint: 'Listen for the tone.',
      audioUrl: 'https://example.com/meitian.mp3',
      imageUrl: null,
      options: [
        { id: 1, text: '每天', correct: true, feedback: null },
        { id: 2, text: '明天', correct: false, feedback: '明天 (míngtiān) is tomorrow.' },
      ],
      aiDraft: true,
    }

    expect(questionPayload(question)).toEqual({
      questionType: 'LISTEN_CHOOSE',
      questionText: 'Listen: which word do you hear?',
      points: 3,
      correctAnswerText: null,
      explanation: '每天 (měitiān) means every day.',
      hint: 'Listen for the tone.',
      audioUrl: 'https://example.com/meitian.mp3',
      imageUrl: null,
      options: [
        { optionText: '每天', correctOption: true, feedback: null },
        { optionText: '明天', correctOption: false, feedback: '明天 (míngtiān) is tomorrow.' },
      ],
      aiDraft: true,
    })
  })
})
