import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ClassMatching from '@/components/classes/ClassMatching.vue'
import ClassTracing from '@/components/classes/ClassTracing.vue'
import { stubSpeech } from '@/test/fakeSpeech'
import { buttonNamed, deferred } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { ActivityOutcome, ActivityPlay } from '@/types/classes'

const api = vi.hoisted(() => ({ activity: vi.fn(), completeActivity: vi.fn() }))
const queueClassNotice = vi.hoisted(() => vi.fn())

vi.mock('@/api/classes', () => ({
  studentClassesApi: api,
}))
vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  hintApi: { settings: vi.fn().mockResolvedValue({ mode: 'OFF', delaySeconds: 10 }) },
}))
vi.mock('@/composables/useClassNotice', () => ({ queueClassNotice }))

import StudentClassActivityPage from './StudentClassActivityPage.vue'

const play: ActivityPlay = {
  classId: 1,
  lessonId: 1,
  type: 'FLASHCARDS',
  id: 6,
  title: 'Classmate Phrases',
  hintsAllowed: true,
  number: 1,
  activityCount: 3,
  lessonTitle: 'Meeting Classmates',
  next: { type: 'TRACING', id: 6 },
  cards: [
    { chinese: '你好', pinyin: 'nǐ hǎo', meaning: 'Hello' },
    { chinese: '再见', pinyin: 'zàijiàn', meaning: 'Goodbye' },
  ],
  characters: [],
  words: [],
  meanings: [],
}

const outcome: ActivityOutcome = { done: true, pairs: [], lessonCompletedNow: false }
const PATH = '/student/classes/1/lessons/1/activities/flashcards/6'

function mountActivity() {
  return mountAtRoute(StudentClassActivityPage, PATH)
}

/** Next through the cards: Next on the last one finishes the set. */
async function reviewEveryCard(wrapper: Awaited<ReturnType<typeof mountActivity>>['wrapper']) {
  await wrapper.get('[data-testid="flashcard-next"]').trigger('click')
  await wrapper.get('[data-testid="activity-finish"]').trigger('click')
  await flushPromises()
}

describe('StudentClassActivityPage', () => {
  beforeEach(() => {
    api.activity.mockReset()
    api.completeActivity.mockReset()
    queueClassNotice.mockReset()
    api.activity.mockResolvedValue(play)
    api.completeActivity.mockResolvedValue(outcome)
  })

  it('hides flashcard audio when the last Mandarin voice is removed', async () => {
    const speech = stubSpeech([{ lang: 'cmn' }])
    const { wrapper } = await mountActivity()
    const bar = wrapper.get('.class-player-row')
    await bar.get('button').trigger('click')
    expect(speech.utterance().text).toBe('你好')
    speech.setVoices([{ lang: 'yue' }])
    await flushPromises()
    expect(bar.find('button').exists()).toBe(false)
  })

  it('goes through the cards, then shows the done band with Practise again and Next activity', async () => {
    const completion = deferred<ActivityOutcome>()
    api.completeActivity.mockReturnValueOnce(completion.promise)
    const { wrapper } = await mountActivity()
    expect(wrapper.get('h1').text()).toBe('Classmate Phrases')
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Meeting Classmates')
    expect(wrapper.text()).toContain('Card 1 of 2')
    expect(buttonNamed(wrapper, 'Previous').attributes('aria-disabled')).toBe('true')

    await reviewEveryCard(wrapper)
    const previous = buttonNamed(wrapper, 'Previous')
    expect(previous.element.disabled).toBe(true)
    await previous.trigger('click')
    expect(wrapper.text()).toContain('Card 2 of 2')
    completion.resolve(outcome)
    await flushPromises()

    expect(api.completeActivity).toHaveBeenCalledWith('FLASHCARDS', 6)
    const band = wrapper.get('[data-testid="activity-done"]')
    expect(band.text()).toContain('Completed')
    expect(band.text()).toContain('All 2 cards reviewed.')
    expect(wrapper.find('[data-testid="activity-finish"]').exists()).toBe(false)
    expect(
      wrapper
        .findAll('a')
        .find((link) => link.text() === 'Next activity')!
        .attributes('href'),
    ).toBe('/student/classes/1/lessons/1/activities/tracing/6')

    await buttonNamed(wrapper, 'Practise again').trigger('click')
    expect(wrapper.find('[data-testid="activity-done"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Card 1 of 2')
  })

  it('names the traced characters in the house list style', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    api.activity.mockResolvedValue({
      ...play,
      type: 'TRACING',
      cards: [],
      characters: ['你', '好', '吗'].map((character) => ({ character, pinyin: '', meaning: '' })),
    })
    const { wrapper } = await mountActivity()

    wrapper.findComponent(ClassTracing).vm.$emit('finish')
    await flushPromises()

    expect(wrapper.get('[data-testid="activity-done"]').text()).toContain('You traced 你, 好 and 吗.')
  })

  it('uses the actual lesson and class on a link with mismatched parents', async () => {
    const { wrapper, router } = await mountAtRoute(
      StudentClassActivityPage,
      '/student/classes/9/lessons/8/activities/flashcards/6?from=saved#activity',
    )
    expect(router.currentRoute.value.fullPath).toBe(`${PATH}?from=saved#activity`)
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/student/classes/1/lessons/1')
  })

  it('never takes the progress bar back when practising again', async () => {
    const { wrapper } = await mountActivity()
    const bar = () => Number(wrapper.get('.focus-topbar [role="progressbar"]').attributes('aria-valuenow'))
    const before = bar()

    await reviewEveryCard(wrapper)
    const done = bar()
    expect(done).toBeGreaterThan(before)
    await buttonNamed(wrapper, 'Practise again').trigger('click')
    expect(bar()).toBe(done)
  })

  it('ends the last activity with Finish lesson, which confirms a completed lesson on the lesson page', async () => {
    api.activity.mockResolvedValue({ ...play, next: null })
    api.completeActivity.mockResolvedValue({ ...outcome, lessonCompletedNow: true })
    const { wrapper } = await mountActivity()

    await reviewEveryCard(wrapper)
    const finish = wrapper.get('[data-testid="finish-lesson"]')
    expect(finish.attributes('href')).toBe('/student/classes/1/lessons/1')
    await finish.trigger('click')

    expect(queueClassNotice).toHaveBeenCalledWith({
      message: 'Lesson completed.',
      targetRoute: 'student-class-lesson',
    })
  })

  it('leaves without asking: each finished activity is already saved', async () => {
    const { wrapper, router } = await mountActivity()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('student-class-lesson')
  })

  it('says so when the progress cannot be saved', async () => {
    api.completeActivity.mockRejectedValue(new Error('nope'))
    const { wrapper } = await mountActivity()

    await reviewEveryCard(wrapper)

    expect(wrapper.text()).toContain('Your progress could not be saved.')
    expect(wrapper.find('[data-testid="activity-done"]').exists()).toBe(false)
  })

  it('shows Open lesson without re-queuing the completion notice on replay or when the lesson is still in progress', async () => {
    api.activity.mockResolvedValue({ ...play, next: null })
    api.completeActivity.mockResolvedValueOnce({ ...outcome })
    const inProgress = await mountActivity()

    await reviewEveryCard(inProgress.wrapper)
    const inProgressCta = inProgress.wrapper.get('[data-testid="finish-lesson"]')
    expect(inProgressCta.text()).toBe('Open lesson')
    await inProgressCta.trigger('click')
    expect(queueClassNotice).not.toHaveBeenCalled()
    inProgress.wrapper.unmount()

    // Completing the lesson shows "Finish lesson", then replaying it switches back to "Open lesson".
    api.completeActivity
      .mockResolvedValueOnce({ ...outcome, lessonCompletedNow: true })
      .mockResolvedValueOnce({ ...outcome, lessonCompletedNow: false })
    const completed = await mountActivity()
    await reviewEveryCard(completed.wrapper)
    expect(completed.wrapper.get('[data-testid="finish-lesson"]').text()).toBe('Finish lesson')

    await buttonNamed(completed.wrapper, 'Practise again').trigger('click')
    await reviewEveryCard(completed.wrapper)
    const replayCta = completed.wrapper.get('[data-testid="finish-lesson"]')
    expect(replayCta.text()).toBe('Open lesson')
    await replayCta.trigger('click')
    expect(queueClassNotice).not.toHaveBeenCalled()
    completed.wrapper.unmount()

    // Reopening an already-completed lesson on a fresh visit returns lessonCompletedNow: false.
    api.completeActivity.mockResolvedValue({ ...outcome, lessonCompletedNow: false })
    const revisit = await mountActivity()
    await reviewEveryCard(revisit.wrapper)
    const revisitCta = revisit.wrapper.get('[data-testid="finish-lesson"]')
    expect(revisitCta.text()).toBe('Open lesson')
    await revisitCta.trigger('click')
    expect(queueClassNotice).not.toHaveBeenCalled()
  })

  it('resets state and discards an in-flight completion when navigating in-place to another activity', async () => {
    const firstSave = deferred<ActivityOutcome>()
    api.completeActivity.mockReturnValueOnce(firstSave.promise)
    const nextPlay: ActivityPlay = {
      ...play,
      type: 'MATCHING',
      id: 9,
      title: 'Match Phrases',
      number: 2,
      next: null,
      cards: [],
      words: [{ id: 1, chinese: '你好', pinyin: 'nǐ hǎo' }],
      meanings: ['Hello'],
    }
    const { wrapper, router } = await mountActivity()

    await reviewEveryCard(wrapper)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="focus-close"]').element.disabled).toBe(true)

    api.activity.mockResolvedValueOnce(nextPlay)
    await router.push('/student/classes/1/lessons/1/activities/matching/9')
    await flushPromises()

    firstSave.resolve({ ...outcome })
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Match Phrases')
    expect(wrapper.find('[data-testid="activity-done"]').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="focus-close"]').element.disabled).toBe(false)

    // ClassMatching busy, error and outcome wiring
    const matching = wrapper.findComponent(ClassMatching)
    matching.vm.$emit('busy', true)
    await flushPromises()
    expect(wrapper.get<HTMLButtonElement>('[data-testid="focus-close"]').element.disabled).toBe(true)
    matching.vm.$emit('busy', false)
    matching.vm.$emit('error', 'Could not check your match.')
    await flushPromises()
    expect(wrapper.text()).toContain('Could not check your match.')
    matching.vm.$emit('outcome', { done: true, pairs: [], lessonCompletedNow: true })
    await flushPromises()
    expect(wrapper.get('[data-testid="activity-done"]').text()).toContain('1 pair is correct.')
  })

  it('retries loading when the activity request fails', async () => {
    api.activity.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(play)
    const { wrapper } = await mountActivity()

    expect(wrapper.text()).toContain('This activity could not be loaded')
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Classmate Phrases')
  })

  it('redirects to unauthorized with the activity deep link when the class has not been joined (409)', async () => {
    api.activity.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'Join this class to open its lessons.' } },
    })
    const { router } = await mountActivity()

    expect(router.currentRoute.value.name).toBe('unauthorized')
    expect(router.currentRoute.value.query.redirect).toBe(PATH)
  })

  it('shows the archived conflict message without a Retry button when an archived class rejects the activity (409)', async () => {
    api.activity.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { message: 'This class is archived. You can still open its lessons and your results.' },
      },
    })
    const { wrapper, router } = await mountActivity()

    expect(router.currentRoute.value.name).toBe('student-class-activity')
    expect(wrapper.text()).toContain('This class is archived.')
    expect(wrapper.findAll('button').filter((btn) => btn.text() === 'Try again')).toHaveLength(0)
  })
})
