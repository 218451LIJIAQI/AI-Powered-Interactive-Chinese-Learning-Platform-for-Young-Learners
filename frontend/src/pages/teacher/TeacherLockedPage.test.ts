import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { TeacherProfileResponse } from '@/types/ops'

const teacherVerificationApi = vi.hoisted(() => ({ profile: vi.fn() }))
const auth = vi.hoisted(() => ({ isAuthenticated: true, activeRole: 'TEACHER', isApprovedTeacher: false }))
const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('@/api/ops', () => ({ teacherVerificationApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRouter: () => router,
}))

import TeacherLockedPage from './TeacherLockedPage.vue'

function profile(overrides: Partial<TeacherProfileResponse> = {}): TeacherProfileResponse {
  return {
    teacherId: 3,
    fullName: 'Mei Lin',
    email: 'mei.lin@example.com',
    bio: null,
    verificationState: 'NOT_SUBMITTED',
    submittedAt: null,
    approvedAt: null,
    latestDecision: null,
    documents: [],
    publishedClasses: [],
    ...overrides,
  }
}

async function mountPage() {
  const wrapper = mount(TeacherLockedPage, { global: { stubs: { IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('TeacherLockedPage', () => {
  beforeEach(() => {
    auth.isApprovedTeacher = false
  })

  it('tells an applicant who has not submitted how to start', async () => {
    teacherVerificationApi.profile.mockResolvedValue(profile())
    const wrapper = await mountPage()

    expect(wrapper.find('h1').text()).toBe('Teaching tools')
    expect(wrapper.find('[data-testid="teacher-locked-status"]').text()).toContain(
      'Not submitted yet. Upload your documents to start.',
    )
    expect(wrapper.text()).toContain('upload your verification documents')
  })

  it('says since when the application is in review and that documents stay locked', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({ verificationState: 'IN_REVIEW', submittedAt: '2026-05-14T09:00:00' }),
    )
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="teacher-locked-status"]').text()).toContain('In review since')
    expect(wrapper.text()).toContain('Documents stay locked while they are in review.')
  })

  it('repeats the reason when changes were requested', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({
        verificationState: 'CHANGES_REQUESTED',
        latestDecision: {
          outcome: 'CHANGES_REQUESTED',
          reason: 'Please upload a clearer copy.',
          reviewerName: 'Olivia Tan',
          decidedAt: '2026-05-18T14:00:00',
        },
      }),
    )
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="teacher-locked-status"]').text()).toContain(
      'Changes requested: Please upload a clearer copy.',
    )
  })

  it('sends an approved teacher back to the dashboard', async () => {
    auth.isApprovedTeacher = true
    teacherVerificationApi.profile.mockResolvedValue(profile({ verificationState: 'APPROVED' }))
    await mountPage()

    expect(router.replace).toHaveBeenCalledWith({ name: 'teacher-dashboard' })
  })
})
