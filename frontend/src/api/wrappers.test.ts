import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import {
  aiApi,
  adminApi,
  authApi,
  notificationsApi,
  paymentApi,
  publicApi,
  studentApi,
  systemApi,
  teacherApi,
} from './services'
import type {
  AdminDragDropActivityPayload,
  AdminFlashcardPayload,
  AdminListeningExercisePayload,
  AdminMatchingGamePayload,
  AdminStoryActivityPayload,
  AdminTracingExercisePayload,
  TeacherLessonPayload,
  TeacherLiveClassPayload,
  TeacherModulePayload,
  TeacherPackagePayload,
  TeacherQuizPayload,
  TeacherQuizQuestionPayload,
} from './services'
import { http } from './http'
import type { AdminPlatformSettingsUpdateRequest } from '@/types/api'

const originalAdapter = http.defaults.adapter

interface CapturedRequest {
  method: string
  url: string
  data?: unknown
  params?: unknown
  responseType?: unknown
}

interface WrapperCase {
  name: string
  run: () => Promise<unknown>
  method: string
  url: string
}

function parseData(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function response<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse {
  return {
    data: {
      success: true,
      data,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
}

function installCaptureAdapter(data: unknown = { ok: true }) {
  const calls: CapturedRequest[] = []
  http.defaults.adapter = (async (config) => {
    calls.push({
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      data: parseData(config.data),
      params: config.params,
      responseType: config.responseType,
    })

    return response(config, data)
  }) satisfies AxiosAdapter
  return calls
}

async function expectWrapperMappings(cases: WrapperCase[]) {
  const calls = installCaptureAdapter({ items: [] })

  for (const wrapperCase of cases) {
    await wrapperCase.run()
  }

  expect(calls.map(({ method, url }) => ({ method, url }))).toEqual(cases.map(({ method, url }) => ({ method, url })))
}

describe('REST API wrappers', () => {
  afterEach(() => {
    http.defaults.adapter = originalAdapter
  })

  it('unwraps successful envelopes and rejects malformed envelopes', async () => {
    http.defaults.adapter = (async (config) => ({
      data: { unexpected: true },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({
      response: {
        data: {
          message: 'The server returned an unexpected response format.',
        },
      },
    })
  })

  it('turns backend rejection envelopes into friendly errors', async () => {
    http.defaults.adapter = (async (config) => {
      throw new AxiosError('Request failed with status code 400', AxiosError.ERR_BAD_REQUEST, config, undefined, {
        data: { success: false, message: 'Nope', errors: ['Bad input'] },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config,
      })
    }) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({ response: { status: 400 } })
  })

  it('maps auth, public, notification, system, student, payment, teacher, and admin endpoints', async () => {
    const calls = installCaptureAdapter({ items: [{ id: 1 }] })

    await authApi.login({ email: 'learner@example.com', password: 'Password123!' })
    await publicApi.listCourses({ keyword: 'pinyin', page: 1 })
    await notificationsApi.markAllRead()
    await systemApi.health()
    await studentApi.submitQuiz(9, [{ questionId: 3, answerText: 'ni hao' }])
    await paymentApi.status(12)
    await teacherApi.archiveRecordedVideo(2, 5)
    await adminApi.approveTeacher(7, 'Looks good')

    expect(calls).toEqual([
      { method: 'POST', url: '/auth/login', data: { email: 'learner@example.com', password: 'Password123!' } },
      { method: 'GET', url: '/public/courses', params: { keyword: 'pinyin', page: 1 } },
      { method: 'PATCH', url: '/notifications/read-all' },
      { method: 'GET', url: '/health' },
      {
        method: 'POST',
        url: '/students/quizzes/9/attempts',
        data: { answers: [{ questionId: 3, answerText: 'ni hao' }] },
      },
      { method: 'GET', url: '/payments/status/12' },
      { method: 'PATCH', url: '/teachers/lessons/2/recorded-videos/5/archive' },
      { method: 'POST', url: '/admin/teachers/7/approve', data: { note: 'Looks good' } },
    ])
  })

  it('extracts safe download metadata from teacher and admin document responses', async () => {
    const blob = new Blob(['pdf'], { type: 'application/pdf' })
    http.defaults.adapter = (async (config) => ({
      data: blob,
      status: 200,
      statusText: 'OK',
      headers: {
        'content-disposition': "attachment; filename*=UTF-8''teacher%20document.pdf",
        'content-type': 'application/pdf',
      },
      config,
    })) satisfies AxiosAdapter

    await expect(teacherApi.downloadVerificationDocument(4)).resolves.toEqual({
      blob,
      fileName: 'teacher document.pdf',
      contentType: 'application/pdf',
    })
    await expect(adminApi.downloadTeacherDocument(5)).resolves.toEqual({
      blob,
      fileName: 'teacher document.pdf',
      contentType: 'application/pdf',
    })
  })

  it('unwraps paged list endpoints to plain item arrays where wrappers promise arrays', async () => {
    const calls = installCaptureAdapter({ items: [{ id: 11 }, { id: 12 }] })

    await expect(notificationsApi.list({ status: 'UNREAD' })).resolves.toEqual([{ id: 11 }, { id: 12 }])
    await expect(studentApi.badges()).resolves.toEqual([{ id: 11 }, { id: 12 }])
    await expect(teacherApi.liveClasses()).resolves.toEqual([{ id: 11 }, { id: 12 }])
    await expect(adminApi.moderationRecords({ status: 'OPEN' })).resolves.toEqual([{ id: 11 }, { id: 12 }])

    expect(calls).toEqual([
      { method: 'GET', url: '/notifications', params: { status: 'UNREAD' } },
      { method: 'GET', url: '/students/rewards/badges' },
      { method: 'GET', url: '/teachers/live-classes' },
      { method: 'GET', url: '/admin/moderation-records', params: { status: 'OPEN' } },
    ])
  })

  it('maps AI, student commerce, teacher content, and admin moderation commands', async () => {
    const calls = installCaptureAdapter({ items: [] })

    await aiApi.quizGenerator({ topic: 'tones', numberOfQuestions: 3 })
    await studentApi.buyNow({ itemType: 'LESSON', itemId: 42, featureKey: 'recording' })
    await teacherApi.addPackageItem(6, { itemType: 'LESSON', itemId: 9, displayOrder: 2 })
    await adminApi.moderationAction('request-revision', {
      targetType: 'LESSON',
      targetId: 9,
      reason: 'Needs source attribution',
    })
    await adminApi.reviewAiReport(3, { status: 'RESOLVED', reviewerNote: 'Teacher reviewed' })

    expect(calls).toEqual([
      { method: 'POST', url: '/ai/quiz-generator', data: { topic: 'tones', numberOfQuestions: 3 } },
      {
        method: 'POST',
        url: '/students/checkout/buy-now',
        data: { itemType: 'LESSON', itemId: 42, featureKey: 'recording' },
      },
      {
        method: 'POST',
        url: '/teachers/packages/6/items',
        data: { itemType: 'LESSON', itemId: 9, displayOrder: 2 },
      },
      {
        method: 'POST',
        url: '/admin/moderation/request-revision',
        data: { targetType: 'LESSON', targetId: 9, reason: 'Needs source attribution' },
      },
      {
        method: 'POST',
        url: '/admin/ai-response-reports/3/review',
        data: { status: 'RESOLVED', reviewerNote: 'Teacher reviewed' },
      },
    ])
  })

  it('maps auth, public, notification, student, and AI wrapper contracts', async () => {
    const formData = new FormData()
    const cases: WrapperCase[] = [
      {
        name: 'auth refresh',
        run: () => authApi.refresh(),
        method: 'POST',
        url: '/auth/refresh',
      },
      {
        name: 'auth register student',
        run: () =>
          authApi.registerStudent({
            fullName: 'Student User',
            email: 'student@example.com',
            password: 'Password123!',
            confirmPassword: 'Password123!',
          }),
        method: 'POST',
        url: '/auth/register/student',
      },
      {
        name: 'auth register teacher',
        run: () =>
          authApi.registerTeacher({
            fullName: 'Teacher User',
            email: 'teacher@example.com',
            password: 'Password123!',
            confirmPassword: 'Password123!',
            bio: 'Experienced Mandarin teacher.',
          }),
        method: 'POST',
        url: '/auth/register/teacher',
      },
      { name: 'auth me', run: () => authApi.me(), method: 'GET', url: '/auth/me' },
      { name: 'auth logout', run: () => authApi.logout(), method: 'POST', url: '/auth/logout' },
      {
        name: 'auth forgot password',
        run: () => authApi.forgotPassword('student@example.com'),
        method: 'POST',
        url: '/auth/forgot-password',
      },
      {
        name: 'auth reset password',
        run: () => authApi.resetPassword({ token: 'reset', password: 'Password123!', confirmPassword: 'Password123!' }),
        method: 'POST',
        url: '/auth/reset-password',
      },
      { name: 'public levels', run: () => publicApi.getLevels(), method: 'GET', url: '/public/levels' },
      {
        name: 'public categories',
        run: () => publicApi.getCategories(),
        method: 'GET',
        url: '/public/categories',
      },
      {
        name: 'public platform settings',
        run: () => publicApi.platformSettings(),
        method: 'GET',
        url: '/public/platform-settings',
      },
      {
        name: 'public course detail',
        run: () => publicApi.getCourseDetail('course-1'),
        method: 'GET',
        url: '/public/courses/course-1',
      },
      {
        name: 'notifications unread',
        run: () => notificationsApi.unreadCount(),
        method: 'GET',
        url: '/notifications/unread-count',
      },
      {
        name: 'notifications read',
        run: () => notificationsApi.markAsRead(7),
        method: 'PATCH',
        url: '/notifications/7/read',
      },
      { name: 'student dashboard', run: () => studentApi.dashboard(), method: 'GET', url: '/students/dashboard' },
      { name: 'student progress', run: () => studentApi.progress(), method: 'GET', url: '/students/progress' },
      { name: 'student rewards', run: () => studentApi.rewards(), method: 'GET', url: '/students/rewards' },
      {
        name: 'student points',
        run: () => studentApi.pointsHistory(),
        method: 'GET',
        url: '/students/rewards/points-history',
      },
      { name: 'student streak', run: () => studentApi.streak(), method: 'GET', url: '/students/rewards/streak' },
      {
        name: 'student lesson player',
        run: () => studentApi.lessonPlayer(3),
        method: 'GET',
        url: '/students/lessons/3',
      },
      {
        name: 'student live classes',
        run: () => studentApi.liveClasses(),
        method: 'GET',
        url: '/students/live-classes',
      },
      {
        name: 'student join live class',
        run: () => studentApi.joinLiveClass(4),
        method: 'GET',
        url: '/students/live-classes/4/join',
      },
      {
        name: 'student enrol live class',
        run: () => studentApi.enrolLiveClass(4),
        method: 'POST',
        url: '/students/live-classes/4/enrol',
      },
      {
        name: 'student cancel live class',
        run: () => studentApi.cancelLiveClassEnrolment(4),
        method: 'POST',
        url: '/students/live-classes/4/cancel-enrolment',
      },
      {
        name: 'student video progress',
        run: () => studentApi.videoProgress(8),
        method: 'GET',
        url: '/students/video-progress/8',
      },
      {
        name: 'student save video progress',
        run: () => studentApi.saveVideoProgress(8, { watchedSeconds: 12 }),
        method: 'POST',
        url: '/students/video-progress/8',
      },
      {
        name: 'student complete video',
        run: () => studentApi.completeVideo(8),
        method: 'POST',
        url: '/students/video-progress/8/complete',
      },
      { name: 'student quizzes', run: () => studentApi.quizzes({ page: 1 }), method: 'GET', url: '/students/quizzes' },
      { name: 'student quiz', run: () => studentApi.quiz(9), method: 'GET', url: '/students/quizzes/9' },
      {
        name: 'student wrong answers',
        run: () => studentApi.wrongAnswers(),
        method: 'GET',
        url: '/students/wrong-answers',
      },
      { name: 'student feedback', run: () => studentApi.feedback(), method: 'GET', url: '/students/feedback' },
      {
        name: 'student activities',
        run: () => studentApi.activityLibrary({ type: 'flashcard' }),
        method: 'GET',
        url: '/students/activities',
      },
      {
        name: 'student flashcard',
        run: () => studentApi.flashcard(10),
        method: 'GET',
        url: '/students/activities/flashcards/10',
      },
      {
        name: 'student flashcard complete',
        run: () => studentApi.completeFlashcard(10),
        method: 'POST',
        url: '/students/activities/flashcards/10/complete',
      },
      {
        name: 'student matching game',
        run: () => studentApi.matchingGame(11),
        method: 'GET',
        url: '/students/activities/matching-games/11',
      },
      {
        name: 'student matching complete',
        run: () => studentApi.completeMatchingGame(11),
        method: 'POST',
        url: '/students/activities/matching-games/11/complete',
      },
      {
        name: 'student listening',
        run: () => studentApi.listeningExercise(12),
        method: 'GET',
        url: '/students/activities/listening-exercises/12',
      },
      {
        name: 'student listening complete',
        run: () => studentApi.completeListeningExercise(12),
        method: 'POST',
        url: '/students/activities/listening-exercises/12/complete',
      },
      {
        name: 'student tracing',
        run: () => studentApi.tracingExercise(13),
        method: 'GET',
        url: '/students/activities/tracing-exercises/13',
      },
      {
        name: 'student tracing complete',
        run: () => studentApi.completeTracingExercise(13),
        method: 'POST',
        url: '/students/activities/tracing-exercises/13/complete',
      },
      {
        name: 'student drag drop',
        run: () => studentApi.dragDropActivity(14),
        method: 'GET',
        url: '/students/activities/drag-drop-activities/14',
      },
      {
        name: 'student drag drop complete',
        run: () => studentApi.completeDragDropActivity(14),
        method: 'POST',
        url: '/students/activities/drag-drop-activities/14/complete',
      },
      {
        name: 'student story',
        run: () => studentApi.storyActivity(15),
        method: 'GET',
        url: '/students/activities/story-activities/15',
      },
      {
        name: 'student story complete',
        run: () => studentApi.completeStoryActivity(15),
        method: 'POST',
        url: '/students/activities/story-activities/15/complete',
      },
      { name: 'student cart', run: () => studentApi.cart(), method: 'GET', url: '/students/cart' },
      {
        name: 'student add cart item',
        run: () => studentApi.addCartItem({ itemType: 'LESSON', itemId: 6 }),
        method: 'POST',
        url: '/students/cart/items',
      },
      {
        name: 'student remove cart item',
        run: () => studentApi.removeCartItem(16),
        method: 'DELETE',
        url: '/students/cart/items/16',
      },
      { name: 'student clear cart', run: () => studentApi.clearCart(), method: 'DELETE', url: '/students/cart/clear' },
      {
        name: 'student checkout cart',
        run: () => studentApi.checkoutCart(),
        method: 'POST',
        url: '/students/checkout/cart',
      },
      {
        name: 'student payments',
        run: () => studentApi.payments({ page: 0 }),
        method: 'GET',
        url: '/students/payments',
      },
      {
        name: 'student refunds',
        run: () => studentApi.refunds({ page: 0 }),
        method: 'GET',
        url: '/students/refund-requests',
      },
      {
        name: 'student create refund',
        run: () => studentApi.createRefund({ orderId: 4, reason: 'duplicate' }),
        method: 'POST',
        url: '/students/refund-requests',
      },
      { name: 'AI availability', run: () => aiApi.availability(), method: 'GET', url: '/ai/availability' },
      {
        name: 'AI vocabulary',
        run: () => aiApi.vocabularyHelper({ word: '你好' }),
        method: 'POST',
        url: '/ai/vocabulary-helper',
      },
      {
        name: 'AI sentence',
        run: () => aiApi.sentencePractice({ sentence: '你好' }),
        method: 'POST',
        url: '/ai/sentence-practice',
      },
      {
        name: 'AI story',
        run: () => aiApi.storyGenerator({ topic: 'food' }),
        method: 'POST',
        url: '/ai/story-generator',
      },
      {
        name: 'AI recommendation',
        run: () => aiApi.learningRecommendation({ currentLevel: 'Beginner' }),
        method: 'POST',
        url: '/ai/learning-recommendation',
      },
      {
        name: 'AI pronunciation',
        run: () => aiApi.pronunciationFeedback(formData),
        method: 'POST',
        url: '/ai/pronunciation-feedback',
      },
      { name: 'AI writing', run: () => aiApi.writingFeedback(formData), method: 'POST', url: '/ai/writing-feedback' },
      { name: 'AI reports', run: () => aiApi.reports({ status: 'OPEN' }), method: 'GET', url: '/ai/reports' },
      {
        name: 'AI report response',
        run: () => aiApi.reportResponse({ featureKey: 'CHAT', reasonCategory: 'OTHER', description: 'wrong tone' }),
        method: 'POST',
        url: '/ai/reports',
      },
    ]

    await expectWrapperMappings(cases)
  })

  it('maps teacher wrapper contracts across dashboard, content, class, feedback, and payout domains', async () => {
    const formData = new FormData()
    const modulePayload: TeacherModulePayload = {
      title: 'Basics',
      description: 'Intro',
      levelId: 1,
      categoryId: 2,
      accessType: 'FREE',
    }
    const lessonPayload: TeacherLessonPayload = {
      title: 'Greetings',
      description: 'Say hello',
      basicContent: '你好',
      moduleId: 1,
      levelId: 1,
      categoryId: 2,
      learningType: 'RECORDED',
      accessType: 'FREE',
    }
    const packagePayload: TeacherPackagePayload = {
      title: 'Starter',
      description: 'Starter package',
      levelId: 1,
      categoryId: 2,
      learningType: 'HYBRID',
      accessType: 'FREE',
    }
    const liveClassPayload: TeacherLiveClassPayload = {
      title: 'Tone practice',
      description: 'Tone drill',
      startTime: '2026-06-01T10:00:00',
      endTime: '2026-06-01T10:30:00',
      maxParticipants: 8,
      meetingLink: 'https://example.com/live',
      meetingPlatform: 'ZOOM',
    }
    const quizPayload: TeacherQuizPayload = {
      title: 'Tone quiz',
      description: 'Choose the tone',
      timeLimitMinutes: 10,
    }
    const questionPayload: TeacherQuizQuestionPayload = {
      questionType: 'MULTIPLE_CHOICE',
      questionText: 'Pick one',
      correctAnswerText: 'A',
      points: 1,
      options: [{ optionText: 'A', correctOption: true }, { optionText: 'B' }],
      explanation: 'A is correct',
    }
    const cases: WrapperCase[] = [
      { name: 'teacher dashboard', run: () => teacherApi.dashboard(), method: 'GET', url: '/teachers/dashboard' },
      { name: 'teacher analytics', run: () => teacherApi.analytics(), method: 'GET', url: '/teachers/analytics' },
      { name: 'teacher profile', run: () => teacherApi.profile(), method: 'GET', url: '/teachers/me' },
      {
        name: 'teacher update profile',
        run: () => teacherApi.updateProfile({ fullName: 'Teacher', bio: 'Bio' }),
        method: 'PUT',
        url: '/teachers/me/profile',
      },
      {
        name: 'teacher verification status',
        run: () => teacherApi.verificationStatus(),
        method: 'GET',
        url: '/teachers/verification-status',
      },
      {
        name: 'teacher upload verification',
        run: () => teacherApi.uploadVerificationDocument(formData),
        method: 'POST',
        url: '/teachers/verification-documents',
      },
      {
        name: 'teacher resubmit verification',
        run: () => teacherApi.resubmitVerification(),
        method: 'POST',
        url: '/teachers/resubmit-verification',
      },
      { name: 'teacher modules', run: () => teacherApi.modules(), method: 'GET', url: '/teachers/modules' },
      {
        name: 'teacher create module',
        run: () => teacherApi.createModule(modulePayload),
        method: 'POST',
        url: '/teachers/modules',
      },
      {
        name: 'teacher update module',
        run: () => teacherApi.updateModule(2, modulePayload),
        method: 'PUT',
        url: '/teachers/modules/2',
      },
      {
        name: 'teacher archive module',
        run: () => teacherApi.archiveModule(2),
        method: 'PATCH',
        url: '/teachers/modules/2/archive',
      },
      { name: 'teacher lessons', run: () => teacherApi.lessons(), method: 'GET', url: '/teachers/lessons' },
      {
        name: 'teacher create lesson',
        run: () => teacherApi.createLesson(lessonPayload),
        method: 'POST',
        url: '/teachers/lessons',
      },
      {
        name: 'teacher update lesson',
        run: () => teacherApi.updateLesson(3, lessonPayload),
        method: 'PUT',
        url: '/teachers/lessons/3',
      },
      {
        name: 'teacher archive lesson',
        run: () => teacherApi.archiveLesson(3),
        method: 'PATCH',
        url: '/teachers/lessons/3/archive',
      },
      { name: 'teacher packages', run: () => teacherApi.packages(), method: 'GET', url: '/teachers/packages' },
      {
        name: 'teacher create package',
        run: () => teacherApi.createPackage(packagePayload),
        method: 'POST',
        url: '/teachers/packages',
      },
      {
        name: 'teacher update package',
        run: () => teacherApi.updatePackage(4, packagePayload),
        method: 'PUT',
        url: '/teachers/packages/4',
      },
      {
        name: 'teacher archive package',
        run: () => teacherApi.archivePackage(4),
        method: 'PATCH',
        url: '/teachers/packages/4/archive',
      },
      {
        name: 'teacher remove package item',
        run: () => teacherApi.removePackageItem(4, 5),
        method: 'DELETE',
        url: '/teachers/packages/4/items/5',
      },
      {
        name: 'teacher create class',
        run: () => teacherApi.createLiveClass(liveClassPayload),
        method: 'POST',
        url: '/teachers/live-classes',
      },
      {
        name: 'teacher update class',
        run: () => teacherApi.updateLiveClass(6, liveClassPayload),
        method: 'PUT',
        url: '/teachers/live-classes/6',
      },
      {
        name: 'teacher cancel class',
        run: () => teacherApi.cancelLiveClass(6),
        method: 'PATCH',
        url: '/teachers/live-classes/6/cancel',
      },
      {
        name: 'teacher enrolments',
        run: () => teacherApi.enrolments(6),
        method: 'GET',
        url: '/teachers/live-classes/6/enrolments',
      },
      {
        name: 'teacher videos',
        run: () => teacherApi.recordedVideos(7),
        method: 'GET',
        url: '/teachers/lessons/7/recorded-videos',
      },
      {
        name: 'teacher create video',
        run: () => teacherApi.createRecordedVideo(7, formData),
        method: 'POST',
        url: '/teachers/lessons/7/recorded-videos',
      },
      {
        name: 'teacher update video',
        run: () => teacherApi.updateRecordedVideo(7, 8, formData),
        method: 'PUT',
        url: '/teachers/lessons/7/recorded-videos/8',
      },
      {
        name: 'teacher video progress',
        run: () => teacherApi.lessonVideoProgress(7),
        method: 'GET',
        url: '/teachers/lessons/7/video-progress',
      },
      {
        name: 'teacher lesson quizzes',
        run: () => teacherApi.lessonQuizzes(7),
        method: 'GET',
        url: '/teachers/lessons/7/quizzes',
      },
      {
        name: 'teacher create quiz',
        run: () => teacherApi.createQuiz(7, quizPayload),
        method: 'POST',
        url: '/teachers/lessons/7/quizzes',
      },
      {
        name: 'teacher update quiz',
        run: () => teacherApi.updateQuiz(9, quizPayload),
        method: 'PUT',
        url: '/teachers/quizzes/9',
      },
      {
        name: 'teacher delete quiz',
        run: () => teacherApi.deleteQuiz(9),
        method: 'DELETE',
        url: '/teachers/quizzes/9',
      },
      {
        name: 'teacher add quiz question',
        run: () => teacherApi.addQuizQuestion(9, questionPayload),
        method: 'POST',
        url: '/teachers/quizzes/9/questions',
      },
      {
        name: 'teacher update quiz question',
        run: () => teacherApi.updateQuizQuestion(10, questionPayload),
        method: 'PUT',
        url: '/teachers/quiz-questions/10',
      },
      {
        name: 'teacher delete quiz question',
        run: () => teacherApi.deleteQuizQuestion(10),
        method: 'DELETE',
        url: '/teachers/quiz-questions/10',
      },
      {
        name: 'teacher quiz attempts',
        run: () => teacherApi.quizAttempts(9),
        method: 'GET',
        url: '/teachers/quizzes/9/attempts',
      },
      { name: 'teacher feedback list', run: () => teacherApi.feedbackList(), method: 'GET', url: '/teachers/feedback' },
      { name: 'teacher students', run: () => teacherApi.students(), method: 'GET', url: '/teachers/students' },
      {
        name: 'teacher create feedback',
        run: () => teacherApi.createFeedback({ studentId: 1, message: 'Great work' }),
        method: 'POST',
        url: '/teachers/feedback',
      },
      {
        name: 'teacher update feedback',
        run: () => teacherApi.updateFeedback(11, { message: 'Updated' }),
        method: 'PUT',
        url: '/teachers/feedback/11',
      },
      {
        name: 'teacher earnings',
        run: () => teacherApi.earnings({ page: 0 }),
        method: 'GET',
        url: '/teachers/earnings',
      },
      {
        name: 'teacher earnings summary',
        run: () => teacherApi.earningsSummary(),
        method: 'GET',
        url: '/teachers/earnings/summary',
      },
      {
        name: 'teacher payouts',
        run: () => teacherApi.payouts({ status: 'PENDING' }),
        method: 'GET',
        url: '/teachers/payout-requests',
      },
      {
        name: 'teacher create payout',
        run: () => teacherApi.createPayoutRequest({ amount: 25 }),
        method: 'POST',
        url: '/teachers/payout-requests',
      },
    ]

    await expectWrapperMappings(cases)
  })

  it('maps admin wrapper contracts across moderation, activity, finance, and settings domains', async () => {
    const flashcardPayload: AdminFlashcardPayload = {
      title: 'Hello',
      frontText: '你好',
      backText: 'hello',
      pinyin: 'ni hao',
      englishMeaning: 'hello',
    }
    const matchingPayload: AdminMatchingGamePayload = {
      title: 'Match words',
      items: [{ leftText: '你', rightText: 'you' }],
    }
    const listeningPayload: AdminListeningExercisePayload = {
      title: 'Listen',
      audioUrl: 'https://example.com/audio.mp3',
      questionPayload: JSON.stringify({ prompt: 'Choose', options: ['A', 'B'], correctAnswer: 'A' }),
    }
    const tracingPayload: AdminTracingExercisePayload = {
      title: 'Trace',
      targetCharacter: '你',
    }
    const dragDropPayload: AdminDragDropActivityPayload = {
      title: 'Build',
      items: [{ dragText: '你', targetZone: 'first' }],
    }
    const storyPayload: AdminStoryActivityPayload = {
      title: 'Story',
      storyText: '你好。',
      questions: [],
    }
    const platformPayload: AdminPlatformSettingsUpdateRequest = {
      aiDisclaimerText: 'AI can make mistakes.',
      emailNotifications: true,
      inAppNotifications: true,
      dailySummary: false,
      strictContentFiltering: true,
      sensitiveWordFilter: true,
      aiSafetyCheck: true,
    }
    const cases: WrapperCase[] = [
      { name: 'admin dashboard', run: () => adminApi.dashboard(), method: 'GET', url: '/admin/dashboard' },
      { name: 'admin analytics', run: () => adminApi.analytics(), method: 'GET', url: '/admin/analytics' },
      { name: 'admin live classes', run: () => adminApi.liveClasses(), method: 'GET', url: '/admin/live-classes' },
      { name: 'admin users', run: () => adminApi.users({ page: 0 }), method: 'GET', url: '/admin/users' },
      {
        name: 'admin user status',
        run: () => adminApi.updateUserStatus(2, { active: false }),
        method: 'PATCH',
        url: '/admin/users/2/status',
      },
      { name: 'admin students', run: () => adminApi.students({ page: 0 }), method: 'GET', url: '/admin/students' },
      { name: 'admin teachers', run: () => adminApi.teachers({ page: 0 }), method: 'GET', url: '/admin/teachers' },
      {
        name: 'admin pending teachers',
        run: () => adminApi.pendingTeachers({ page: 0 }),
        method: 'GET',
        url: '/admin/teachers/pending',
      },
      { name: 'admin teacher detail', run: () => adminApi.teacherDetail(3), method: 'GET', url: '/admin/teachers/3' },
      {
        name: 'admin reject teacher',
        run: () => adminApi.rejectTeacher(3, 'Incomplete'),
        method: 'POST',
        url: '/admin/teachers/3/reject',
      },
      {
        name: 'admin activity feed',
        run: () => adminApi.activityFeed({ type: 'FLASHCARD' }),
        method: 'GET',
        url: '/admin/activities',
      },
      {
        name: 'admin ai usage',
        run: () => adminApi.aiUsageLogs({ page: 0 }),
        method: 'GET',
        url: '/admin/ai/usage-logs',
      },
      {
        name: 'admin flashcard',
        run: () => adminApi.flashcard(4),
        method: 'GET',
        url: '/admin/activities/flashcards/4',
      },
      {
        name: 'admin create flashcard',
        run: () => adminApi.createFlashcard(flashcardPayload),
        method: 'POST',
        url: '/admin/activities/flashcards',
      },
      {
        name: 'admin update flashcard',
        run: () => adminApi.updateFlashcard(4, flashcardPayload),
        method: 'PUT',
        url: '/admin/activities/flashcards/4',
      },
      {
        name: 'admin flashcard status',
        run: () => adminApi.updateFlashcardStatus(4, 'PUBLISHED'),
        method: 'PATCH',
        url: '/admin/activities/flashcards/4/status',
      },
      {
        name: 'admin delete flashcard',
        run: () => adminApi.deleteFlashcard(4),
        method: 'DELETE',
        url: '/admin/activities/flashcards/4',
      },
      {
        name: 'admin matching game',
        run: () => adminApi.matchingGame(5),
        method: 'GET',
        url: '/admin/activities/matching-games/5',
      },
      {
        name: 'admin create matching',
        run: () => adminApi.createMatchingGame(matchingPayload),
        method: 'POST',
        url: '/admin/activities/matching-games',
      },
      {
        name: 'admin update matching',
        run: () => adminApi.updateMatchingGame(5, matchingPayload),
        method: 'PUT',
        url: '/admin/activities/matching-games/5',
      },
      {
        name: 'admin matching status',
        run: () => adminApi.updateMatchingGameStatus(5, 'ARCHIVED'),
        method: 'PATCH',
        url: '/admin/activities/matching-games/5/status',
      },
      {
        name: 'admin delete matching',
        run: () => adminApi.deleteMatchingGame(5),
        method: 'DELETE',
        url: '/admin/activities/matching-games/5',
      },
      {
        name: 'admin listening',
        run: () => adminApi.listeningExercise(6),
        method: 'GET',
        url: '/admin/activities/listening-exercises/6',
      },
      {
        name: 'admin create listening',
        run: () => adminApi.createListeningExercise(listeningPayload),
        method: 'POST',
        url: '/admin/activities/listening-exercises',
      },
      {
        name: 'admin update listening',
        run: () => adminApi.updateListeningExercise(6, listeningPayload),
        method: 'PUT',
        url: '/admin/activities/listening-exercises/6',
      },
      {
        name: 'admin listening status',
        run: () => adminApi.updateListeningExerciseStatus(6, 'PUBLISHED'),
        method: 'PATCH',
        url: '/admin/activities/listening-exercises/6/status',
      },
      {
        name: 'admin delete listening',
        run: () => adminApi.deleteListeningExercise(6),
        method: 'DELETE',
        url: '/admin/activities/listening-exercises/6',
      },
      {
        name: 'admin tracing',
        run: () => adminApi.tracingExercise(7),
        method: 'GET',
        url: '/admin/activities/tracing-exercises/7',
      },
      {
        name: 'admin create tracing',
        run: () => adminApi.createTracingExercise(tracingPayload),
        method: 'POST',
        url: '/admin/activities/tracing-exercises',
      },
      {
        name: 'admin update tracing',
        run: () => adminApi.updateTracingExercise(7, tracingPayload),
        method: 'PUT',
        url: '/admin/activities/tracing-exercises/7',
      },
      {
        name: 'admin tracing status',
        run: () => adminApi.updateTracingExerciseStatus(7, 'ARCHIVED'),
        method: 'PATCH',
        url: '/admin/activities/tracing-exercises/7/status',
      },
      {
        name: 'admin delete tracing',
        run: () => adminApi.deleteTracingExercise(7),
        method: 'DELETE',
        url: '/admin/activities/tracing-exercises/7',
      },
      {
        name: 'admin drag drop',
        run: () => adminApi.dragDropActivity(8),
        method: 'GET',
        url: '/admin/activities/drag-drop-activities/8',
      },
      {
        name: 'admin create drag drop',
        run: () => adminApi.createDragDropActivity(dragDropPayload),
        method: 'POST',
        url: '/admin/activities/drag-drop-activities',
      },
      {
        name: 'admin update drag drop',
        run: () => adminApi.updateDragDropActivity(8, dragDropPayload),
        method: 'PUT',
        url: '/admin/activities/drag-drop-activities/8',
      },
      {
        name: 'admin drag drop status',
        run: () => adminApi.updateDragDropActivityStatus(8, 'PUBLISHED'),
        method: 'PATCH',
        url: '/admin/activities/drag-drop-activities/8/status',
      },
      {
        name: 'admin delete drag drop',
        run: () => adminApi.deleteDragDropActivity(8),
        method: 'DELETE',
        url: '/admin/activities/drag-drop-activities/8',
      },
      {
        name: 'admin story',
        run: () => adminApi.storyActivity(9),
        method: 'GET',
        url: '/admin/activities/story-activities/9',
      },
      {
        name: 'admin create story',
        run: () => adminApi.createStoryActivity(storyPayload),
        method: 'POST',
        url: '/admin/activities/story-activities',
      },
      {
        name: 'admin update story',
        run: () => adminApi.updateStoryActivity(9, storyPayload),
        method: 'PUT',
        url: '/admin/activities/story-activities/9',
      },
      {
        name: 'admin story status',
        run: () => adminApi.updateStoryActivityStatus(9, 'ARCHIVED'),
        method: 'PATCH',
        url: '/admin/activities/story-activities/9/status',
      },
      {
        name: 'admin delete story',
        run: () => adminApi.deleteStoryActivity(9),
        method: 'DELETE',
        url: '/admin/activities/story-activities/9',
      },
      {
        name: 'admin content',
        run: () => adminApi.content({ status: 'PENDING' }),
        method: 'GET',
        url: '/admin/content/submissions',
      },
      {
        name: 'admin content detail',
        run: () => adminApi.contentDetail('lesson', 10),
        method: 'GET',
        url: '/admin/content/submissions/lesson/10',
      },
      {
        name: 'admin archive content',
        run: () => adminApi.archiveContent('lesson', 10, 'Outdated'),
        method: 'PATCH',
        url: '/admin/content/lesson/10/archive',
      },
      {
        name: 'admin ai reports',
        run: () => adminApi.aiReports('OPEN', { page: 0 }),
        method: 'GET',
        url: '/admin/ai-response-reports',
      },
      {
        name: 'admin notifications',
        run: () => adminApi.notifications({ page: 0 }),
        method: 'GET',
        url: '/admin/notifications',
      },
      { name: 'admin audit logs', run: () => adminApi.auditLogs({ page: 0 }), method: 'GET', url: '/admin/audit-logs' },
      { name: 'admin payments', run: () => adminApi.payments({ page: 0 }), method: 'GET', url: '/admin/payments' },
      {
        name: 'admin refunds',
        run: () => adminApi.refunds({ status: 'REQUESTED' }),
        method: 'GET',
        url: '/admin/refund-requests',
      },
      {
        name: 'admin approve refund',
        run: () => adminApi.approveRefund(11),
        method: 'POST',
        url: '/admin/refund-requests/11/approve',
      },
      {
        name: 'admin reject refund',
        run: () => adminApi.rejectRefund(11, { reason: 'outside policy' }),
        method: 'POST',
        url: '/admin/refund-requests/11/reject',
      },
      {
        name: 'admin mark refunded',
        run: () => adminApi.markRefunded(11),
        method: 'POST',
        url: '/admin/refund-requests/11/mark-refunded',
      },
      { name: 'admin payouts', run: () => adminApi.payouts({ page: 0 }), method: 'GET', url: '/admin/payout-requests' },
      {
        name: 'admin approve payout',
        run: () => adminApi.approvePayout(12),
        method: 'POST',
        url: '/admin/payout-requests/12/approve',
      },
      {
        name: 'admin reject payout',
        run: () => adminApi.rejectPayout(12, { reason: 'missing info' }),
        method: 'POST',
        url: '/admin/payout-requests/12/reject',
      },
      {
        name: 'admin mark payout paid',
        run: () => adminApi.markPayoutPaid(12),
        method: 'POST',
        url: '/admin/payout-requests/12/mark-paid',
      },
      {
        name: 'admin commission setting',
        run: () => adminApi.commissionSetting(),
        method: 'GET',
        url: '/admin/system-settings/commission',
      },
      {
        name: 'admin update commission',
        run: () => adminApi.updateCommissionSetting({ commissionRate: 0.2 }),
        method: 'PUT',
        url: '/admin/system-settings/commission',
      },
      {
        name: 'admin platform settings',
        run: () => adminApi.platformSettings(),
        method: 'GET',
        url: '/admin/system-settings/platform',
      },
      {
        name: 'admin update platform',
        run: () => adminApi.updatePlatformSettings(platformPayload),
        method: 'PUT',
        url: '/admin/system-settings/platform',
      },
    ]

    await expectWrapperMappings(cases)
  })
})
