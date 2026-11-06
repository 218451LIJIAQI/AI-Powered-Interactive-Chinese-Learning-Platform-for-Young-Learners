import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'

const api = vi.hoisted(() => ({ status: vi.fn(), reports: vi.fn() }))
const router = vi.hoisted(() => ({
  route: { params: {} as Record<string, string>, query: {} as Record<string, string> },
  replace: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkHrefStub,
  useRoute: () => router.route,
  useRouter: () => ({ replace: router.replace }),
}))

import StudentAiHelperPage from './StudentAiHelperPage.vue'

const NOTICE = 'AI responses may contain errors and do not replace your teacher.'

const TAB_STUBS = {
  RouteTabs: { template: '<div data-testid="tabs"><slot /></div>' },
  IconGlyph: true,
  teleport: true,
  AiTalkTab: {
    name: 'AiTalkTab',
    props: ['blocked', 'aiNotice'],
    template: '<div data-testid="talk-tab">{{ blocked }}|{{ aiNotice }}</div>',
  },
  AiAskTab: {
    name: 'AiAskTab',
    props: ['blocked', 'initialQuestion', 'aiNotice'],
    emits: ['reported'],
    template: '<div data-testid="ask-tab">{{ initialQuestion }}|{{ aiNotice }}<slot name="rail" /></div>',
  },
  AiQuizTab: true,
  AiStoryTab: true,
  AiVoiceTab: true,
}

function status(
  overrides: Partial<{ available: boolean; usedToday: number; dailyLimit: number; aiNotice: string }> = {},
) {
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
    router.route = reactive({ params: { tab: 'talk' }, query: {} })
    api.reports.mockResolvedValue(pageOf([]))
  })

  it('keeps one h1 for assistive technology and the practice note and today’s requests on the tab row', async () => {
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('h1').classes()).toContain('sr-only')
    expect(wrapper.get('[data-testid="tabs"]').text()).toContain('Practice only · no XP')
    expect(wrapper.get('[data-testid="ai-requests-today"]').text()).toBe('12 of 100 requests today')
    expect(wrapper.find('[data-testid="ai-blocked"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe(`|${NOTICE}`)
  })

  it('says SparkBot is unavailable in one line and points to Practice', async () => {
    api.status.mockResolvedValue(status({ available: false, usedToday: 0 }))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('[data-testid="ai-blocked"]')
    expect(alert.text()).toContain('SparkBot isn’t available right now.')
    expect(alert.text()).toContain('Lessons, practice and quizzes still work.')
    expect(alert.findAll('a')).toHaveLength(1)
    expect(JSON.parse(alert.get('a').attributes('href')!)).toEqual({ name: 'student-practice' })
    expect(wrapper.get('[data-testid="tabs"]').text()).toContain('Unavailable')
    expect(wrapper.get('[data-testid="tabs"]').text()).not.toContain('Practice only')
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toContain('unavailable')
  })

  it('blocks the tabs once the daily limit is used up', async () => {
    api.status.mockResolvedValue(status({ usedToday: 100 }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="ai-blocked"]').text()).toContain('You’ve used all 100 AI requests today.')
    expect(wrapper.get('[data-testid="ai-requests-today"]').classes()).toContain('is-strong')
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toContain('limit')
  })

  it('still gives the tabs a note when the admin left the AI notice empty', async () => {
    api.status.mockResolvedValue(status({ aiNotice: '' }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe('|AI can make mistakes.')
  })

  it('passes an Ask question from the link and the AI notice to the Ask tab', async () => {
    router.route = reactive({ params: { tab: 'ask' }, query: { q: '怎么说 thank you?' } })
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="ask-tab"]').text()).toContain(`怎么说 thank you?|${NOTICE}`)
  })

  it('shows My reports as the rail of Ask, with the admin’s note on an answered report', async () => {
    router.route = reactive({ params: { tab: 'ask' }, query: {} })
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
      ]),
    )
    const wrapper = mountPage()
    await flushPromises()

    const reports = wrapper.get('[data-testid="ai-my-reports"]')
    expect(reports.text()).toContain('1 report')
    expect(reports.text()).toContain('“What is the difference between 再见 and 拜拜?”')
    expect(reports.text()).toContain('Thanks — SparkBot now explains both.')
  })

  it('has no My reports on the other tabs', async () => {
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-my-reports"]').exists()).toBe(false)
  })

  it('confirms a report with a toast and refreshes My reports', async () => {
    router.route = reactive({ params: { tab: 'ask' }, query: {} })
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()
    api.reports.mockClear()

    wrapper.findComponent({ name: 'AiAskTab' }).vm.$emit('reported')
    await flushPromises()

    expect(api.reports).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Report sent. An admin replies in My reports.')
  })

  it('moves /student/ai to its Talk tab path', async () => {
    router.route = reactive({ params: {}, query: {} })
    api.status.mockResolvedValue(status())
    mountPage()
    await flushPromises()

    expect(router.replace).toHaveBeenCalledWith({ name: 'student-ai', params: { tab: 'talk' }, query: {} })
  })
})
