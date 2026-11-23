import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'

import { i18n } from '@/plugins/i18n'
import { pageOf } from '@/test/helpers'
import type { AiHelperStatus } from '@/types/ai'

const api = vi.hoisted(() => ({ status: vi.fn(), reports: vi.fn() }))
const router = vi.hoisted(() => ({
  route: { params: {} as Record<string, string>, query: {} as Record<string, string> },
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => router.route,
}))

import StudentAiHelperPage from './StudentAiHelperPage.vue'

/** PlatformSettingsService.DEFAULT_AI_DISCLAIMER_TEXT: the platform default, sent in English. */
const NOTICE = 'AI can make mistakes.'
/** The earlier default, still stored where an admin saved the settings without changing the notice. */
const RETIRED_NOTICE = 'AI responses may contain errors and do not replace your teacher.'

const TAB_STUBS = {
  RouteTabs: {
    props: ['tabs', 'label'],
    template:
      '<div data-testid="tabs"><a v-for="item in tabs" :key="item.label" :href="JSON.stringify(item.to)">{{ item.label }}</a><slot /></div>',
  },
  IconGlyph: true,
  teleport: true,
  AiTalkTab: {
    name: 'AiTalkTab',
    props: ['blocked', 'aiNotice', 'tavusDeploymentId'],
    emits: ['toast', 'used', 'reported'],
    template: '<div data-testid="talk-tab" :data-deployment-id="tavusDeploymentId">{{ blocked }}|{{ aiNotice }}</div>',
  },
  AiAskTab: {
    name: 'AiAskTab',
    props: ['blocked', 'initialQuestion', 'aiNotice'],
    emits: ['reported', 'used'],
    template: '<div data-testid="ask-tab">{{ initialQuestion }}|{{ aiNotice }}<slot name="rail" /></div>',
  },
  AiGeneratorTab: true,
  AiVoiceTab: true,
}

function status(overrides: Partial<AiHelperStatus> = {}): AiHelperStatus {
  return {
    available: true,
    speechCheckAvailable: true,
    handwritingCheckAvailable: true,
    usedToday: 12,
    dailyLimit: 100,
    aiNotice: NOTICE,
    ...overrides,
  }
}

function mountPage() {
  return mount(StudentAiHelperPage, { global: { stubs: TAB_STUBS } })
}

describe('StudentAiHelperPage', () => {
  beforeEach(() => {
    api.status.mockReset()
    api.reports.mockReset()
    router.route = { params: { tab: 'talk' }, query: {} }
    api.reports.mockResolvedValue(pageOf([]))
  })

  it('keeps one h1 for assistive technology and the practice note on the tab row, without a request count', async () => {
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('h1').classes()).toContain('sr-only')
    expect(wrapper.get('[data-testid="tabs"] .route-tabs-note').text()).toBe('Practice only · no XP')
    expect(wrapper.findAll('[data-testid="tabs"] a').map((link) => link.text())).toEqual([
      'Talk with Xiaomei',
      'Ask SparkBot',
      'Quick quiz',
      'Story',
      'Pronunciation & handwriting',
    ])
    // The count is shown only once it stops the AI (S9.13).
    expect(wrapper.find('[data-testid="ai-requests-today"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-blocked"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe(`|${NOTICE}`)
  })

  it('passes the Tavus deployment to Talk even when the text provider is unavailable', async () => {
    vi.stubEnv('VITE_TAVUS_DEPLOYMENT_ID', '  demo-deployment  ')
    api.status.mockResolvedValue(status({ available: false }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="talk-tab"]').attributes('data-deployment-id')).toBe('demo-deployment')
    expect(wrapper.get('[data-testid="ai-blocked"]').text()).toContain('Tap the mic to talk with Xiaomei live.')
  })

  it.each(['ask', 'talk'])(
    'says on the %s tab that SparkBot and Xiaomei can’t answer, that it is on our side, and points to Practice',
    async (tab) => {
      router.route = { params: { tab }, query: {} }
      api.status.mockResolvedValue(status({ available: false, usedToday: 0 }))
      const wrapper = mountPage()
      await flushPromises()

      const alert = wrapper.get('[data-testid="ai-blocked"]')
      expect(alert.get('strong').text()).toBe('SparkBot and Xiaomei can’t answer right now.')
      expect(alert.text()).toContain(
        'It’s a problem on our side; try again in a few minutes. Lessons, practice and lesson quizzes still work.',
      )
      expect(alert.findAll('a')).toHaveLength(1)
      expect(alert.get('a').text()).toBe('Go to Practice')
      expect(JSON.parse(alert.get('a').attributes('href')!)).toEqual({ name: 'student-practice' })
      expect(wrapper.get('[data-testid="tabs"] .route-tabs-note').text()).toBe('Practice only · no XP')
    },
  )

  it('tells the Talk tab it is blocked when the AI is unavailable', async () => {
    api.status.mockResolvedValue(status({ available: false, usedToday: 0 }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="talk-tab"]').text()).toContain('unavailable')
  })

  it.each([
    [
      { speechCheckAvailable: false, handwritingCheckAvailable: false },
      'Pronunciation and handwriting checks are unavailable right now.',
    ],
    [{ usedToday: 100 }, 'You’ve used all 100 AI requests for today, across every AI Helper tool.'],
  ])(
    'tells the Pronunciation tab when both checks are unavailable or the shared limit is reached',
    async (overrides, title) => {
      router.route = { params: { tab: 'voice' }, query: {} }
      api.status.mockResolvedValue(status(overrides))
      const wrapper = mountPage()
      await flushPromises()

      const alert = wrapper.get('[data-testid="ai-blocked"]')
      expect(alert.text()).toContain(title)
      expect(alert.get('a').text()).toBe('Go to Practice')
    },
  )

  it('keeps pronunciation available while the general AI provider is off', async () => {
    router.route = { params: { tab: 'voice' }, query: {} }
    api.status.mockResolvedValue(
      status({ available: false, speechCheckAvailable: true, handwritingCheckAvailable: false }),
    )
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-blocked"]').exists()).toBe(false)
  })

  it('blocks the tabs once the daily limit is used up, says it covers every tool, and only then shows the count', async () => {
    api.status.mockResolvedValue(status({ usedToday: 60, dailyLimit: 60 }))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('[data-testid="ai-blocked"]')
    expect(alert.get('strong').text()).toBe('You’ve used all 60 AI requests for today, across every AI Helper tool.')
    expect(alert.text()).toContain('They reset at midnight.')
    expect(alert.get('a').text()).toBe('Go to Practice')
    const count = wrapper.get('[data-testid="ai-requests-today"]')
    expect(count.text()).toBe('60 of 60 requests today')
    expect(count.classes()).toContain('is-strong')
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toContain('limit')
  })

  it('shows a tab’s confirmation in the page’s toast, with its Undo', async () => {
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()
    const undo = vi.fn()

    wrapper.findComponent({ name: 'AiTalkTab' }).vm.$emit('toast', 'Xiaomei will forget this.', {
      label: 'Undo',
      run: undo,
    })
    await flushPromises()

    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.text()).toContain('Xiaomei will forget this.')
    await toast.get('button').trigger('click')
    expect(undo).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('still gives the tabs the default notice when the server sends no AI notice', async () => {
    api.status.mockResolvedValue(status({ aiNotice: '  ' }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe(`|${NOTICE}`)
  })

  it.each([
    ['ms', NOTICE, 'AI boleh membuat kesilapan.'],
    ['zh-CN', NOTICE, 'AI 可能会出错。'],
    ['ms', RETIRED_NOTICE, 'AI boleh membuat kesilapan.'],
    ['zh-CN', RETIRED_NOTICE, 'AI 可能会出错。'],
  ] as const)(
    'shows the platform default notice in the learner’s language (%s, from “%s”)',
    async (locale, serverNotice, translated) => {
      i18n.global.locale.value = locale
      api.status.mockResolvedValue(status({ aiNotice: serverNotice }))
      const wrapper = mountPage()
      await flushPromises()

      expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe(`|${translated}`)
    },
  )

  it('shows an admin’s own notice as written, whatever the interface language', async () => {
    i18n.global.locale.value = 'ms'
    api.status.mockResolvedValue(status({ aiNotice: 'Semak jawapan AI dengan pelajaran anda.' }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe('|Semak jawapan AI dengan pelajaran anda.')
  })

  it('passes an Ask question from the link and the AI notice to the Ask tab', async () => {
    router.route = { params: { tab: 'ask' }, query: { q: '怎么说 thank you?' } }
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="ask-tab"]').text()).toContain(`怎么说 thank you?|${NOTICE}`)
  })

  it('shows My reports as the rail of Ask, with the admin’s note on an answered report', async () => {
    router.route = { params: { tab: 'ask' }, query: {} }
    api.status.mockResolvedValue(status())
    api.reports.mockResolvedValue(
      pageOf([
        {
          id: 4,
          featureKey: 'CHAT_TUTOR',
          reasonCategory: 'UNCLEAR_EXPLANATION',
          promptSnapshot: 'What is the difference between 再见 and 拜拜?',
          status: 'RESOLVED',
          createdAt: '2026-10-25T12:10:00Z',
          reviewedAt: '2026-10-28T01:00:00Z',
          reviewerNote: 'Thanks — SparkBot now explains both.',
        },
        {
          id: 3,
          featureKey: 'QUIZ_DRAFT',
          reasonCategory: 'UNCLEAR_EXPLANATION',
          status: 'PENDING_REVIEW',
          createdAt: '2025-05-01T12:00:00Z',
        },
      ]),
    )
    const wrapper = mountPage()
    await flushPromises()

    const reports = wrapper.get('[data-testid="ai-my-reports"]')
    expect(reports.text()).toContain('2 reports')
    expect(reports.text()).toContain('Quiz drafts')
    expect(reports.text()).toContain('“What is the difference between 再见 and 拜拜?”')
    expect(reports.text()).toContain('Thanks — SparkBot now explains both.')
  })

  it('has no My reports on the other tabs', async () => {
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-my-reports"]').exists()).toBe(false)
    expect(api.reports).not.toHaveBeenCalled()
  })

  it('counts all reports, retries an older page and returns to the newest after a report is sent', async () => {
    router.route = { params: { tab: 'ask' }, query: {} }
    api.status.mockResolvedValue(status())
    const report = {
      id: 1,
      featureKey: 'CHAT_TUTOR',
      reasonCategory: 'UNCLEAR_EXPLANATION',
      status: 'RESOLVED',
      createdAt: '2026-10-25T12:10:00Z',
      reviewedAt: '2026-10-28T01:00:00Z',
      reviewerNote: 'Here is the explanation for your first report.',
    }
    const recent = Array.from({ length: 20 }, (_, i) => ({ ...report, id: 21 - i, reviewerNote: 'Recent reply.' }))
    api.reports
      .mockResolvedValue(pageOf(recent, 0, 21, 20))
      .mockResolvedValueOnce(pageOf(recent, 0, 21, 20))
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(pageOf([report], 1, 21, 20))
    const wrapper = mountPage()
    await flushPromises()
    const rail = wrapper.get('[data-testid="ai-my-reports"]')
    expect(rail.text()).toContain('21 reports')
    expect(rail.text()).not.toContain(report.reviewerNote)

    await rail.get('button[aria-label="Next"]').trigger('click')
    await flushPromises()
    await rail.get('button').trigger('click')
    await flushPromises()
    expect(api.reports.mock.calls).toEqual([[0], [1], [1]])
    expect(rail.text()).toContain(report.reviewerNote)
    expect(rail.text()).toContain('21 reports')

    wrapper.findComponent({ name: 'AiAskTab' }).vm.$emit('reported')
    await flushPromises()
    expect(api.reports).toHaveBeenLastCalledWith(0)
    expect(rail.text()).toContain('Recent reply.')
  })

  it('keeps separate topics for the quiz and story when switching between them', async () => {
    router.route = reactive({ params: { tab: 'quiz' }, query: {} })
    api.status.mockResolvedValue(status())
    const wrapper = mount(StudentAiHelperPage, {
      global: { stubs: { ...TAB_STUBS, AiGeneratorTab: false } },
    })
    await flushPromises()

    const topic = () => wrapper.get<HTMLInputElement>('.ai-maker-form input')
    await topic().setValue('Food')
    router.route.params.tab = 'story'
    await flushPromises()
    expect(topic().element.value).toBe('')
    await topic().setValue('A rainy day')

    router.route.params.tab = 'quiz'
    await flushPromises()
    expect(topic().element.value).toBe('Food')
    router.route.params.tab = 'story'
    await flushPromises()
    expect(topic().element.value).toBe('A rainy day')
  })

  it('confirms a report with a toast and refreshes My reports', async () => {
    router.route = { params: { tab: 'ask' }, query: {} }
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()
    api.reports.mockClear()

    wrapper.findComponent({ name: 'AiAskTab' }).vm.$emit('reported')
    await flushPromises()

    expect(api.reports).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Report sent. An admin replies in My reports.')
  })

  it('retries a failed status load, refreshes status on used, and refreshes My reports after a non-Ask report', async () => {
    router.route = reactive({ params: { tab: 'ask' }, query: {} })
    api.status.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    // Visit Ask first so AiMyReports is mounted and cached inside KeepAlive before leaving for Talk.
    expect(api.reports).toHaveBeenCalledExactlyOnceWith(0)
    api.reports.mockClear()

    router.route.params.tab = 'talk'
    await flushPromises()

    const retry = wrapper.get('.data-notice.is-error button')
    await retry.trigger('click')
    await flushPromises()
    expect(api.status).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.data-notice.is-error').exists()).toBe(false)

    wrapper.findComponent({ name: 'AiTalkTab' }).vm.$emit('used')
    await flushPromises()
    expect(api.status).toHaveBeenCalledTimes(3)

    // Reporting from Talk shows the general AI safety confirmation toast and refreshes cached My reports when returning to Ask.
    wrapper.findComponent({ name: 'AiTalkTab' }).vm.$emit('reported')
    await flushPromises()
    expect(wrapper.get('[data-testid="toast"]').text()).toBe(
      'Report sent. An admin replies in Ask SparkBot › My reports.',
    )

    router.route.params.tab = 'ask'
    await flushPromises()
    expect(api.reports).toHaveBeenCalledExactlyOnceWith(0)
  })
})
