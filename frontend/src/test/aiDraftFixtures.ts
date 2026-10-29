import type { AiQuizGeneratorResponse } from '@/types/api'

/** An AI quiz draft as the teacher AI endpoint returns it, for the teacher AI-draft tests. */
export const teacherAiQuizDraft: AiQuizGeneratorResponse = {
  title: 'Ordering Food at a Mamak Stall',
  topic: 'Ordering food',
  questions: [
    {
      questionText: 'Which phrase means "I want one cup of tea"?',
      questionType: 'MULTIPLE_CHOICE',
      options: ['我要一杯茶。', '我喝了很多茶。', '我不要咖啡。'],
      correctAnswer: '我要一杯茶。',
      explanation: '要 means "want" and 一杯 is "one cup".',
      pinyin: 'Wǒ yào yì bēi chá.',
      translation: 'I want one cup of tea.',
    },
    {
      questionText: 'How do you ask "How much is it?"',
      questionType: 'MULTIPLE_CHOICE',
      options: ['多少钱？', '几点了？', '你好吗？'],
      correctAnswer: '多少钱？',
      explanation: '多少钱 is the usual way to ask for a price.',
      pinyin: 'Duōshao qián?',
      translation: 'How much money?',
    },
    {
      questionText: '"谢谢" means "thank you".',
      questionType: 'TRUE_FALSE',
      options: ['True', 'False'],
      correctAnswer: 'True',
      explanation: '谢谢 (xièxie) is "thank you".',
      pinyin: 'xièxie',
      translation: 'thank you',
    },
  ],
  status: 'SUCCESS',
}
