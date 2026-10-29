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

const TAB_STUBS = {
  RouteTabs: true,
  IconGlyph: true,
  AiTalkTab: { name: 'AiTalkTab', props: ['blocked'], template: '<div data-testid="talk-tab">{{ blocked }}</div>' },
  AiAskTab: {
    name: 'AiAskTab',
    props: ['blocked', 'initialQuestion', 'aiNotice'],
    template: '<div data-testid="ask-tab">{{ initialQuestion }}|{{ aiNotice }}</div>',
  },
  AiQuizTab: true,
  AiStoryTab: true,
  AiVoiceTab: true,
}

function status(overrides: Partial<{ available: boolean; usedToday: number; dailyLimit: number }> = {}) {
  return {
    available: true,
    speechCheckAvailable: true,
    handwritingCheckAvailable: true,
    usedToday: 12,
    dailyLimit: 100,
    aiNotice: 'AI can make mistakes. Check important answers with your teacher.',
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

  it('shows today’s requests from the server limiter and opens Talk with Xiaomei', async () => {
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="ai-requests-today"]').text()).toBe('12 of 100 requests today')
    expect(wrapper.find('[data-testid="ai-blocked"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe('')
  })

  it('says SparkBot is unavailable, that lessons still work, and points to Practice', async () => {
    api.status.mockResolvedValue(status({ available: false, usedToday: 0 }))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('[data-testid="ai-blocked"]')
    expect(alert.text()).toContain('SparkBot isn’t available right now.')
    expect(alert.text()).toContain('Lessons, practice and quizzes still work.')
    expect(JSON.parse(alert.get('a').attributes('href')!)).toEqual({ name: 'student-practice' })
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe('unavailable')
  })

  it('blocks the tabs once the daily limit is used up', async () => {
    api.status.mockResolvedValue(status({ usedToday: 100 }))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="ai-blocked"]').text()).toContain('You’ve used all 100 AI requests for today.')
    expect(wrapper.get('[data-testid="ai-requests-today"]').classes()).toContain('is-limit')
    expect(wrapper.get('[data-testid="talk-tab"]').text()).toBe('limit')
  })

  it('passes an Ask question from the link and the AI notice to the Ask tab', async () => {
    router.route = reactive({ params: { tab: 'ask' }, query: { q: '怎么说 thank you?' } })
    api.status.mockResolvedValue(status())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="ask-tab"]').text()).toBe(
      '怎么说 thank you?|AI can make mistakes. Check important answers with your teacher.',
    )
  })

  it('opens My reports from the header, with the admin’s note on an answered report', async () => {
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
    const wrapper = mount(StudentAiHelperPage, { global: { stubs: { ...TAB_STUBS, teleport: true } } })
    await flushPromises()

    const open = wrapper.get('[data-testid="ai-reports-open"]')
    expect(open.text()).toContain('1')
    await open.trigger('click')

    const reports = wrapper.get('[data-testid="ai-my-reports"]')
    expect(reports.text()).toContain('“What is the difference between 再见 and 拜拜?”')
    expect(reports.text()).toContain('Thanks — SparkBot now explains both.')
  })

  it('moves /student/ai to its Talk tab path', async () => {
    router.route = reactive({ params: {}, query: {} })
    api.status.mockResolvedValue(status())
    mountPage()
    await flushPromises()

    expect(router.replace).toHaveBeenCalledWith({ name: 'student-ai', params: { tab: 'talk' }, query: {} })
  })
})
