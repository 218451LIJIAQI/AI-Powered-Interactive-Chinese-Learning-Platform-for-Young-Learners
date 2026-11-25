import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { stubSpeech } from '@/test/fakeSpeech'
import { deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { StudentLesson } from '@/types/classes'

const api = vi.hoisted(() => ({
  lesson: vi.fn(),
  markVideoWatched: vi.fn(),
  videoBlob: vi.fn(),
}))
const support = vi.hoisted(() => ({ create: vi.fn() }))

vi.mock('@/api/classes', () => ({
  studentClassesApi: { lesson: api.lesson, markVideoWatched: api.markVideoWatched },
  videoBlob: api.videoBlob,
}))
vi.mock('@/api/ops', () => ({ supportApi: support }))

import StudentClassLessonPage from './StudentClassLessonPage.vue'

const flashcards = {
  type: 'FLASHCARDS',
  id: 6,
  title: 'Classmate Phrases',
  number: 1,
  done: true,
} satisfies StudentLesson['activities'][number]
const quiz = {
  id: 9,
  questionCount: 5,
  bestPercent: 80,
  attemptCount: 2,
  bestAttemptId: 3,
  inProgress: false,
} satisfies StudentLesson['quizzes'][number]

function lesson(overrides: Partial<StudentLesson> = {}): StudentLesson {
  return {
    classId: 1,
    classTitle: 'Mandarin for Campus Life',
    title: 'Meeting Classmates',
    available: true,
    video: {
      title: 'Meeting a classmate',
      externalUrl: null,
      streamPath: '/api/v1/students/classes/lessons/1/video/3/stream',
      durationSeconds: 375,
      watched: false,
    },
    keyPhrases: [{ chinese: '你叫什么名字？', pinyin: 'nǐ jiào shénme míngzi?', meaning: 'What’s your name?' }],
    activities: [flashcards, { type: 'MATCHING', id: 7, title: 'Match the Answers', number: 2, done: false }],
    quizzes: [quiz],
    stepsDone: 2,
    stepsTotal: 4,
    status: 'IN_PROGRESS',
    archived: false,
    ...overrides,
  }
}

function mountLesson() {
  return mountAtRoute(StudentClassLessonPage, '/student/classes/1/lessons/1')
}

async function openVideoReport() {
  api.videoBlob.mockRejectedValue(new Error('offline'))
  const { wrapper } = await mountLesson()
  await wrapper.get('[data-testid="video-play"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="video-report"]').trigger('click')
  return wrapper
}

describe('StudentClassLessonPage', () => {
  const createObjectURL = vi.fn(() => 'blob:current-video')
  const revokeObjectURL = vi.fn()

  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(api).forEach((fn) => fn.mockReset())
    support.create.mockReset()
    createObjectURL.mockClear()
    revokeObjectURL.mockClear()
    api.lesson.mockResolvedValue(lesson())
    api.markVideoWatched.mockResolvedValue(undefined)
    vi.stubGlobal(
      'URL',
      class extends URL {
        static createObjectURL = createObjectURL
        static revokeObjectURL = revokeObjectURL
      },
    )
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined)
  })

  it('shows the state, the next step, the content, the activities and the quiz with its best result', async () => {
    const { wrapper } = await mountLesson()

    expect(wrapper.get('h1').text()).toBe('Meeting Classmates')
    expect(wrapper.get('.class-avatar').text()).toBe('MC')
    expect(wrapper.get('.class-page-header .page-title-copy p').text()).toBe('In progress · 2 of 4 steps')
    expect(wrapper.get('[data-testid="lesson-next"]').text()).toBe('Continue: Match the Answers')
    expect(wrapper.get('.lesson-video-row').text()).toContain('Meeting a classmate')
    expect(wrapper.get('.lesson-video-row').text()).toContain('6:15')
    expect(wrapper.text()).toContain('你叫什么名字？')
    expect(wrapper.text()).toContain('5 questions')
    expect(wrapper.text()).toContain('80%')
    expect(wrapper.get('[data-testid="quiz-start"]').text()).toBe('Retake quiz')
    // The best attempt, not the latest one.
    expect(wrapper.get('[data-testid="quiz-best"]').text()).toBe('See best result')
    expect(wrapper.get('[data-testid="quiz-best"]').attributes('href')).toBe('/student/quiz/9/attempts/3')
    expect(wrapper.text()).not.toMatch(/Ask Li Wei|teacher/i)
  })

  it('offers key phrase audio when a Mandarin voice becomes available', async () => {
    const speech = stubSpeech([{ lang: 'zh-HK' }])
    const { wrapper } = await mountLesson()
    const phrases = wrapper.get('.key-phrase-rows')
    expect(phrases.find('button').exists()).toBe(false)
    speech.setVoices([{ lang: 'zh-TW' }])
    await flushPromises()
    await phrases.get('button').trigger('click')
    expect(speech.utterance().text).toBe('你叫什么名字？')
  })

  it('makes each activity row one link: a chevron when done, Start when not', async () => {
    const { wrapper } = await mountLesson()

    const rows = wrapper.findAll('.student-lesson-activity-row')
    expect(rows.map((row) => row.findAll('a, button'))).toEqual([[expect.anything()], [expect.anything()]])
    expect(rows.map((row) => row.get('a').attributes('href'))).toEqual([
      '/student/classes/1/lessons/1/activities/flashcards/6',
      '/student/classes/1/lessons/1/activities/matching/7',
    ])
    expect(rows.map((row) => row.get('a').attributes('title'))).toEqual([
      'Practise again: Classmate Phrases',
      'Start Match the Answers',
    ])
    expect(rows[0]!.find('.class-row-chevron').exists()).toBe(true)
    expect(rows[1]!.get('.btn').text()).toBe('Start')
    expect(rows.map((row) => row.get('.row-list-meta').text().replace(/\s+/g, ' '))).toEqual([
      'Flashcards · Done',
      'Matching',
    ])
  })

  it('restores the actual class on a saved lesson link without reading the lesson twice', async () => {
    const { wrapper, router } = await mountAtRoute(
      StudentClassLessonPage,
      '/student/classes/8/lessons/1?from=saved#quiz',
    )
    expect(router.currentRoute.value.fullPath).toBe('/student/classes/1/lessons/1?from=saved#quiz')
    expect(wrapper.get('[data-testid="lesson-next"]').attributes('href')).toBe(
      '/student/classes/1/lessons/1/activities/matching/7',
    )
    expect(api.lesson).toHaveBeenCalledTimes(1)
  })

  it('offers the class as the next step once the lesson is completed', async () => {
    api.lesson.mockResolvedValue(
      lesson({
        status: 'COMPLETED',
        stepsDone: 4,
        activities: [flashcards],
      }),
    )
    const { wrapper } = await mountLesson()

    expect(wrapper.text()).toContain('Completed · 4 of 4 steps')
    expect(wrapper.get('[data-testid="lesson-next"]').text()).toBe('Open class')
    expect(wrapper.get('[data-testid="lesson-next"]').attributes('href')).toBe('/student/classes/1')
  })

  it('sends a learner who finished the activities to the quiz', async () => {
    api.lesson.mockResolvedValue(
      lesson({
        activities: [flashcards],
        quizzes: [{ ...quiz, attemptCount: 0, bestPercent: null, bestAttemptId: null }],
      }),
    )
    const { wrapper } = await mountLesson()

    expect(wrapper.get('[data-testid="lesson-next"]').text()).toBe('Continue: Lesson quiz')
    expect(wrapper.get('[data-testid="quiz-start"]').text()).toBe('Start quiz')
    expect(wrapper.find('[data-testid="quiz-best"]').exists()).toBe(false)
  })

  it('says Start instead of Continue on the header CTA when the lesson has not started yet', async () => {
    api.lesson.mockResolvedValue(
      lesson({
        status: 'NOT_STARTED',
        stepsDone: 0,
        activities: [{ ...flashcards, done: false }],
      }),
    )
    const { wrapper } = await mountLesson()

    expect(wrapper.get('[data-testid="lesson-next"]').text()).toBe('Start: Classmate Phrases')
  })

  it('plays the video under its own bar, pauses it, and closes it', async () => {
    api.videoBlob.mockResolvedValue(new Blob(['video']))
    const { wrapper } = await mountLesson()

    await wrapper.get('[data-testid="video-play"]').trigger('click')
    await flushPromises()
    const video = wrapper.get('video')
    expect(video.attributes('src')).toBe('blob:current-video')
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalled()

    await video.trigger('play')
    expect(wrapper.get('[data-testid="video-toggle"]').text()).toBe('Pause')
    await video.trigger('pause')
    expect(wrapper.get('[data-testid="video-toggle"]').text()).toBe('Play')
    expect(wrapper.text()).toContain('6:15')

    await video.trigger('ended')
    expect(api.markVideoWatched).toHaveBeenCalledWith(1)

    await wrapper.get('[data-testid="video-close"]').trigger('click')
    expect(wrapper.find('video').exists()).toBe(false)
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:current-video')
    expect(wrapper.find('[data-testid="video-play"]').exists()).toBe(true)
  })

  it('says when the video did not load, retries it, and the file itself failing is the same state', async () => {
    api.videoBlob.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(new Blob(['video']))
    const { wrapper } = await mountLesson()

    await wrapper.get('[data-testid="video-play"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="video-error"]').text()).toBe('Video didn’t load')
    expect(wrapper.text()).toContain('Still not loading?')

    await wrapper.get('[data-testid="video-retry"]').trigger('click')
    await flushPromises()
    expect(api.videoBlob).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="video-error"]').exists()).toBe(false)

    await wrapper.get('video').trigger('error')
    expect(wrapper.find('[data-testid="video-error"]').exists()).toBe(true)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:current-video')
  })

  it('retries a failed video progress save and starts the lesson only once', async () => {
    api.lesson.mockResolvedValue(lesson({ status: 'NOT_STARTED', stepsDone: 0 }))
    api.videoBlob.mockResolvedValue(new Blob(['video']))
    api.markVideoWatched.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountLesson()
    await wrapper.get('[data-testid="video-play"]').trigger('click')
    await flushPromises()
    await wrapper.get('video').trigger('ended')
    await flushPromises()
    const error = wrapper.get('.data-notice')
    expect(error.text()).toContain('Your video progress could not be saved.')
    await error.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.class-page-header .page-title-copy p').text()).toBe('In progress · 0 of 4 steps')
    expect(wrapper.find('.data-notice').exists()).toBe(false)
    await wrapper.get('video').trigger('ended')
    expect(api.markVideoWatched).toHaveBeenCalledTimes(2)
  })

  it('offers Play when autoplay is blocked and Retry when manual playback fails', async () => {
    api.videoBlob.mockResolvedValue(new Blob(['video']))
    vi.mocked(HTMLMediaElement.prototype.play)
      .mockRejectedValueOnce(new DOMException('Click to play', 'NotAllowedError'))
      .mockRejectedValueOnce(new DOMException('Unsupported recording', 'NotSupportedError'))
    const { wrapper } = await mountLesson()

    await wrapper.get('[data-testid="video-play"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="video-toggle"]').text()).toBe('Play')
    await wrapper.get('[data-testid="video-toggle"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="video-error"]').text()).toBe('Video didn’t load')
    await wrapper.get('[data-testid="video-retry"]').trigger('click')
    await flushPromises()
    expect(api.videoBlob).toHaveBeenCalledTimes(2)
    expect(wrapper.find('video').exists()).toBe(true)
  })

  it('reports a video that does not load to the team, the topic and message filled in', async () => {
    support.create.mockResolvedValue({})
    const wrapper = await openVideoReport()
    const dialog = wrapper.get('[data-testid="report-problem"]')
    expect(dialog.get('h2').text()).toBe('Contact support')
    expect(dialog.get<HTMLSelectElement>('select').element.value).toBe('CLASSES')
    const message = dialog.get<HTMLTextAreaElement>('[data-testid="report-problem-message"]')
    expect(message.element.value).toBe('The video “Meeting a classmate” in the Meeting Classmates lesson doesn’t load.')

    await message.setValue('It stops after 10 seconds.')
    await dialog.get('form').trigger('submit')
    await flushPromises()

    const sent = support.create.mock.calls[0]![0]
    expect(sent).toMatchObject({ topic: 'CLASSES', subject: 'Lesson video won’t play' })
    expect(sent.message).toMatch(
      /^It stops after 10 seconds\.\n\nLesson: Meeting Classmates · Mandarin for Campus Life\n/,
    )
    expect(sent.message).toContain('Browser: ')
    expect(wrapper.find('[data-testid="report-problem"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe(
      'Message sent. We’ll reply by email within 2 working days.',
    )
  })

  it('keeps the longest allowed multi-line description and its diagnostics within the support ticket limit', async () => {
    const wrapper = await openVideoReport()
    const dialog = wrapper.get('[data-testid="report-problem"]')
    const field = dialog.get<HTMLTextAreaElement>('textarea')
    const firstLine = 'Line 1\nLine 2\n'
    const description = firstLine + '声'.repeat(field.element.maxLength - firstLine.length)
    await field.setValue(description)
    await dialog.get('form').trigger('submit')

    const sent = support.create.mock.calls[0]![0].message
    expect(sent.length).toBe(4000)
    expect(sent.split('\n')).toEqual([
      ...description.split('\n'),
      '',
      'Lesson: Meeting Classmates · Mandarin for Campus Life',
      `Page: ${window.location.pathname}${window.location.search}`,
      `Browser: ${navigator.userAgent}`,
    ])
  })

  it('keeps the message and says why when sending the report fails, and needs a message to send', async () => {
    support.create.mockRejectedValue(new Error('offline'))
    const wrapper = await openVideoReport()

    const message = wrapper.get<HTMLTextAreaElement>('[data-testid="report-problem-message"]')
    await message.setValue('  ')
    await message.trigger('blur')
    expect(wrapper.text()).toContain('Tell us what went wrong.')
    expect(wrapper.get('[data-testid="report-problem-send"]').attributes('disabled')).toBeDefined()

    await message.setValue('Still broken.')
    await wrapper.get('[data-testid="report-problem"] form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="report-problem-error"]').text()).toBe(
      'Couldn’t send. Check your connection and try again. Your message is kept.',
    )
    expect(message.element.value).toBe('Still broken.')
  })

  it('shows a lesson of an archived class read-only: phrases and best score, nothing to play or retake', async () => {
    api.lesson.mockResolvedValue(
      lesson({
        status: 'COMPLETED',
        stepsDone: 4,
        archived: true,
        video: { title: 'Saying hello', externalUrl: null, streamPath: null, durationSeconds: 280, watched: true },
        activities: [{ type: 'FLASHCARDS', id: 6, title: 'Greeting Phrases', number: 1, done: true }],
      }),
    )
    const { wrapper } = await mountLesson()

    expect(wrapper.get('.class-page-header .page-title-copy p').text()).toBe('Completed · archived class')
    expect(wrapper.get('[data-testid="lesson-next"]').text()).toBe('Back to class')
    expect(wrapper.get('.lesson-video-row').text()).toContain('Saying hello')
    expect(wrapper.get('.lesson-video-row').text()).toContain('4:40')
    expect(wrapper.find('.lesson-video-row .btn').exists()).toBe(false)
    expect(wrapper.find('.student-lesson-activities-card a').exists()).toBe(false)
    expect(wrapper.get('[data-testid="quiz-start"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="quiz-best"]').attributes('href')).toBe('/student/quiz/9/attempts/3')
    expect(wrapper.text()).toContain('你叫什么名字？')
  })

  it('sends a learner who has not joined the class to the refused page, which offers the class', async () => {
    api.lesson.mockRejectedValue({
      response: { status: 409, data: { message: 'Join this class to open its lessons.' } },
    })
    const { router } = await mountLesson()

    expect(router.currentRoute.value.name).toBe('unauthorized')
    expect(router.currentRoute.value.query.redirect).toBe('/student/classes/1/lessons/1')
  })

  it('stays on the new page if the previous lesson is refused after leaving', async () => {
    const pending = deferred<StudentLesson>()
    api.lesson.mockReturnValueOnce(pending.promise)
    const { wrapper, router } = await mountLesson()
    wrapper.unmount()
    await router.push('/student/classes')
    pending.reject({ response: { status: 409, data: {} } })
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('student-classes')
  })

  it('stops the last lesson’s video download when another lesson opens', async () => {
    const oldVideo = deferred<Blob>()
    api.videoBlob.mockReturnValueOnce(oldVideo.promise)
    const { wrapper, router } = await mountLesson()
    await wrapper.get('[data-testid="video-play"]').trigger('click')
    const oldSignal = api.videoBlob.mock.calls[0]![1] as AbortSignal

    await router.push('/student/classes/1/lessons/2')
    await flushPromises()
    expect(api.lesson).toHaveBeenLastCalledWith(2)
    expect(oldSignal.aborted).toBe(true)

    oldVideo.resolve(new Blob(['old']))
    await flushPromises()
    expect(createObjectURL).not.toHaveBeenCalled()
    expect(wrapper.find('video').exists()).toBe(false)
  })

  it('does not allocate a video URL when its request finishes after leaving', async () => {
    const video = deferred<Blob>()
    api.videoBlob.mockReturnValueOnce(video.promise)
    const { wrapper } = await mountLesson()
    await wrapper.get('[data-testid="video-play"]').trigger('click')
    wrapper.unmount()

    video.resolve(new Blob(['late']))
    await flushPromises()
    expect(createObjectURL).not.toHaveBeenCalled()
  })
})
