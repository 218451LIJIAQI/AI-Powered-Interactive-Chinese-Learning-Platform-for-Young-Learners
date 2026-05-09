export type FlashcardItem = {
  id: number
  chineseText: string
  pinyin: string | null
  englishMeaning: string
  malayMeaning: string | null
  imageUrl: string | null
  audioUrl: string | null
  displayOrder: number | null
}

export type FlashcardFormPayload = {
  chineseText: string
  pinyin?: string
  englishMeaning: string
  malayMeaning?: string
  imageUrl?: string
  audioUrl?: string
  extraPayload?: string
  displayOrder?: number
}

export type TeacherQuizOption = {
  id: number
  optionText: string
  correctOption: boolean
  displayOrder: number
}

export type TeacherQuizQuestion = {
  id: number
  questionType: string
  questionText: string
  correctAnswerText: string | null
  points: number
  displayOrder: number
  explanation: string | null
  options: TeacherQuizOption[]
}

export type TeacherQuiz = {
  id: number
  lessonId: number | null
  lessonTitle: string | null
  title: string
  description: string | null
  contentStatus: string
  timeLimitMinutes: number | null
  totalPoints: number | null
  questions: TeacherQuizQuestion[]
  createdAt: string
  updatedAt: string
}

export type QuizOptionFormPayload = {
  optionText: string
  correctOption: boolean
  displayOrder?: number
}

export type QuizFormPayload = {
  title: string
  description?: string
  timeLimitMinutes?: number | null
}

export type QuizQuestionFormPayload = {
  questionType: string
  questionText: string
  correctAnswerText?: string
  points: number
  displayOrder?: number
  options?: QuizOptionFormPayload[]
  explanation?: string
}

export type TeacherQuizAttemptSummary = {
  attemptId: number
  studentId: number
  studentName: string
  score: number | null
  maxScore: number | null
  submittedAt: string | null
}

export type MatchingGameItem = {
  id: number
  leftText: string
  rightText: string
  leftImageUrl: string | null
  rightImageUrl: string | null
  pinyin: string | null
  displayOrder: number
}

export type MatchingGame = {
  id: number
  lessonId: number | null
  lessonTitle: string | null
  title: string
  contentStatus: string
  items: MatchingGameItem[]
  createdAt: string
  updatedAt: string
}

export type MatchingGameItemFormPayload = {
  leftText: string
  rightText: string
  leftImageUrl?: string
  rightImageUrl?: string
  pinyin?: string
  displayOrder?: number
}

export type MatchingGameFormPayload = {
  title: string
  items: MatchingGameItemFormPayload[]
}

export type ListeningOption = {
  optionText: string
  displayOrder: number
}

export type TeacherListeningOption = {
  optionText: string
  correctOption: boolean
  displayOrder: number
}

export type ListeningExercise = {
  id: number
  lessonId: number | null
  lessonTitle: string | null
  title: string
  chineseText: string | null
  pinyin: string | null
  audioUrl: string | null
  contentStatus: string
  options: TeacherListeningOption[]
  explanation: string | null
  createdAt: string
  updatedAt: string
}

export type ListeningOptionFormPayload = {
  optionText: string
  correctOption: boolean
  displayOrder?: number
}

export type ListeningExerciseFormPayload = {
  title: string
  chineseText: string
  pinyin?: string
  audioUrl?: string
  options: ListeningOptionFormPayload[]
  explanation?: string
}

export type TracingExercise = {
  id: number
  lessonId: number | null
  lessonTitle: string | null
  title: string
  targetCharacter: string
  pinyin: string | null
  meaning: string | null
  worksheetUrl: string | null
  instructions: string | null
  contentStatus: string
  createdAt: string
  updatedAt: string
}

export type TracingExerciseFormPayload = {
  title: string
  targetCharacter: string
  pinyin?: string
  meaning?: string
  worksheetUrl?: string
  instructions?: string
}

export type StudentLessonActivitySummary = {
  activityType: string
  sourceId: number | null
  placeholderId: number | null
  title: string
  description: string | null
  available: boolean
  completed: boolean
  locked: boolean
}

export type StudentLessonPlayer = {
  lessonId: number
  title: string
  description: string | null
  basicContent: string | null
  estimatedDurationMinutes: number | null
  completionPercent: number
  completed: boolean
  locked: boolean
  learningType: string | null
  activities: StudentLessonActivitySummary[]
  liveClasses: import('./liveLearning').LiveClass[]
  recordedVideos: import('./liveLearning').RecordedLessonVideo[]
}

export type FlashcardPracticeResponse = {
  flashcardId: number
  completed: boolean
  firstCompletion: boolean
  totalPoints: number
  earnedPoints: number
  lessonCompletionPercent: number
}

export type StudentQuizOption = {
  id: number
  optionText: string
  displayOrder: number
}

export type StudentQuizQuestion = {
  id: number
  questionType: string
  questionText: string
  points: number
  displayOrder: number
  options: StudentQuizOption[]
}

export type StudentQuiz = {
  id: number
  lessonId: number | null
  lessonTitle: string | null
  title: string
  description: string | null
  timeLimitMinutes: number | null
  totalPoints: number | null
  questions: StudentQuizQuestion[]
}

export type QuizSubmissionPayload = {
  answers: {
    questionId: number
    answerText: string
  }[]
}

export type QuizAttemptResultQuestion = {
  questionId: number
  questionType: string
  questionText: string
  studentAnswer: string
  correctAnswer: string
  correct: boolean
  points: number
  explanation: string | null
}

export type QuizAttemptResult = {
  attemptId: number
  quizId: number
  quizTitle: string
  score: number
  maxScore: number
  totalQuestions: number
  correctCount: number
  wrongCount: number
  earnedPoints: number
  totalPoints: number
  passed: boolean
  startedAt: string | null
  submittedAt: string | null
  answers: QuizAttemptResultQuestion[]
}

export type WrongAnswerItem = {
  attemptId: number
  questionId: number
  quizTitle: string
  questionText: string
  questionType: string
  studentAnswer: string
  correctAnswer: string
  explanation: string | null
  submittedAt: string | null
}

export type MatchingGamePlay = {
  id: number
  lessonId: number | null
  title: string
  items: MatchingGameItem[]
}

export type MatchingGameSubmissionPayload = {
  pairs: {
    itemId: number
    submittedRightText: string
  }[]
}

export type MatchingGameSubmissionResult = {
  gameId: number
  completed: boolean
  firstCompletion: boolean
  correctPairs: number
  totalPairs: number
  earnedPoints: number
  totalPoints: number
  lessonCompletionPercent: number
}

export type ListeningExercisePlay = {
  id: number
  lessonId: number | null
  title: string
  chineseText: string | null
  pinyin: string | null
  audioUrl: string | null
  options: ListeningOption[]
}

export type ListeningExerciseSubmissionPayload = {
  answerText: string
}

export type ListeningExerciseSubmissionResult = {
  exerciseId: number
  correct: boolean
  firstCompletion: boolean
  correctAnswer: string
  explanation: string | null
  earnedPoints: number
  totalPoints: number
  lessonCompletionPercent: number
}

export type TracingExercisePlay = {
  id: number
  lessonId: number | null
  title: string
  targetCharacter: string
  pinyin: string | null
  meaning: string | null
  worksheetUrl: string | null
  instructions: string | null
}

export type TracingExerciseCompletionPayload = {
  canvasImageUrl?: string
}

export type TracingExerciseCompletionResult = {
  exerciseId: number
  completed: boolean
  firstCompletion: boolean
  earnedPoints: number
  totalPoints: number
  lessonCompletionPercent: number
}

export type StudentProgressLesson = {
  lessonId: number
  lessonTitle: string
  completionPercent: number
  completed: boolean
  lastAccessedAt: string | null
}

export type StudentProgressSummary = {
  totalLessonsStarted: number
  completedLessons: number
  quizAverage: number
  overallCompletionPercent: number
  weakAreas: string[]
  lastActivitySummary: string | null
  recentLessons: StudentProgressLesson[]
}

export type BadgeSummary = {
  badgeId: number
  name: string
  description: string | null
  iconKey: string | null
  awardedAt: string
}

export type PointsTransactionSummary = {
  id: number
  pointsDelta: number
  reason: string
  sourceType: string | null
  sourceId: number | null
  createdAt: string
}

export type DailyStreakSummary = {
  currentStreak: number
  bestStreak: number
  lastLearningDate: string | null
}

export type RewardSummary = {
  totalPoints: number
  currentLevelNumber: number
  progressToNextLevelPercent: number
  streak: DailyStreakSummary
  badges: BadgeSummary[]
  recentPoints: PointsTransactionSummary[]
}
