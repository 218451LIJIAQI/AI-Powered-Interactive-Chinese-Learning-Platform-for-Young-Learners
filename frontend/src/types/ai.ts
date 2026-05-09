import type { PageResponse } from './adminManagement'

export type AiAvailability = {
  available: boolean
  provider: string
  model: string | null
  status: string
  message: string
}

export type AiChatRequest = {
  message: string
  languagePreference?: string
  sessionId?: number | null
  context?: string
  relatedLessonId?: number | null
}

export type AiChatResponse = {
  sessionId: number
  userMessage: string
  aiResponse: string
  language: string
  createdAt: string
  status: string
}

export type AiVocabularyHelperRequest = {
  word: string
  inputLanguage?: string
  outputLanguage?: string
  learnerLevel?: string
  languagePreference?: string
}

export type AiVocabularyHelperResponse = {
  chineseWord: string | null
  pinyin: string | null
  englishMeaning: string | null
  bahasaMelayuMeaning: string | null
  simpleExplanation: string
  exampleSentence: string | null
  exampleSentencePinyin: string | null
  exampleSentenceTranslation: string | null
  imagePromptOrDescription: string | null
  miniPracticeQuestion: string | null
  encouragement: string
  status: string
}

export type AiSentencePracticeRequest = {
  sentence: string
  learnerLevel?: string
  languagePreference?: string
}

export type AiSentencePracticeResponse = {
  originalSentence: string
  correctedSentence: string
  pinyin: string | null
  englishMeaning: string | null
  bahasaMelayuMeaning: string | null
  improvementExplanation: string
  naturalSentenceVersion: string
  practiceTask: string
  encouragement: string
  status: string
}

export type AiGeneratedQuizQuestion = {
  questionText: string
  questionType: string
  options: string[]
  correctAnswer: string | null
  explanation: string | null
  pinyin: string | null
  translation: string | null
}

export type AiQuizGeneratorRequest = {
  topic: string
  level?: string
  category?: string
  numberOfQuestions: number
  questionTypes?: string[]
  targetRoleContext?: 'STUDENT' | 'TEACHER' | 'ADMIN'
  languagePreference?: string
}

export type AiQuizGeneratorResponse = {
  title: string
  topic: string
  level: string | null
  category: string | null
  usageMode: string
  questions: AiGeneratedQuizQuestion[]
  status: string
}

export type AiStoryVocabularyItem = {
  chineseWord: string | null
  pinyin: string | null
  englishMeaning: string | null
  bahasaMelayuMeaning: string | null
}

export type AiStoryComprehensionQuestion = {
  question: string | null
  options: string[]
  answer: string | null
  explanation: string | null
}

export type AiStoryGeneratorRequest = {
  topic: string
  level?: string
  languagePreference?: string
  lengthPreference?: string
}

export type AiStoryGeneratorResponse = {
  title: string
  chineseStory: string
  pinyin: string | null
  englishTranslation: string | null
  bahasaMelayuTranslation: string | null
  vocabularyList: AiStoryVocabularyItem[]
  comprehensionQuestions: AiStoryComprehensionQuestion[]
  encouragement: string
  usageMode: string
  status: string
}

export type AiLearningRecommendationRequest = {
  studentId?: number
  currentLevel?: string
  recentQuizResults?: string[]
  wrongAnswers?: string[]
  weakAreas?: string[]
  completedLessons?: string[]
  languagePreference?: string
}

export type AiLearningRecommendationResponse = {
  recommendedNextStep: string
  recommendedLessonOrActivity: string
  weakAreaSummary: string
  revisionSuggestion: string
  shortEncouragement: string
  suggestedPracticeTypes: string[]
  status: string
}

export type AiProgressInsightRequest = {
  studentId?: number
  progressSummary?: string
  quizHistory?: string[]
  weakAreas?: string[]
  languagePreference?: string
  audience: 'STUDENT' | 'TEACHER' | 'ADMIN'
}

export type AiProgressInsightResponse = {
  performanceSummary: string
  weakAreas: string[]
  nextStepSuggestions: string[]
  studentEncouragement: string
  teacherSuggestion: string | null
  status: string
}

export type AiTeacherAssistantRequest = {
  topic: string
  level?: string
  category?: string
  learningObjective?: string
  activityTypes?: string[]
  languagePreference?: string
}

export type AiVocabularyEntry = {
  chineseWord: string | null
  pinyin: string | null
  englishMeaning: string | null
  bahasaMelayuMeaning: string | null
}

export type AiTeacherAssistantResponse = {
  lessonOutline: string
  vocabularyList: AiVocabularyEntry[]
  exampleSentences: string[]
  quizQuestions: AiGeneratedQuizQuestion[]
  flashcardContent: string[]
  matchingGameContent: string[]
  storyBasedLessonIdea: string | null
  teachingNotes: string
  status: string
}

export type AiPronunciationFeedbackResponse = {
  feedback: string
  pronunciationComment: string
  toneComment: string
  improvementTips: string
  encouragement: string
  status: string
}

export type AiWritingFeedbackResponse = {
  feedback: string
  clarityComment: string
  improvementTips: string
  encouragement: string
  status: string
}

export type AdminAiUsageLogItem = {
  id: number
  userId: number | null
  userName: string | null
  userEmail: string | null
  userRole: string | null
  featureKey: string
  providerName: string | null
  modelName: string | null
  status: string
  tokenCount: number | null
  languagePreference: string | null
  inputSummary: string | null
  outputSummary: string | null
  errorSummary: string | null
  relatedEntityType: string | null
  relatedEntityId: number | null
  createdAt: string
}

export type AdminAiUsageLogPage = PageResponse<AdminAiUsageLogItem>
