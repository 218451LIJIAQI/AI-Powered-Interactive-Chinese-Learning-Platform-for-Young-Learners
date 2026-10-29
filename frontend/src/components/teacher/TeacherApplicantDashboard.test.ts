import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { TeacherProfileResponse } from '@/types/ops'

const teacherVerificationApi = vi.hoisted(() => ({ profile: vi.fn() }))

vi.mock('@/api/ops', () => ({ teacherVerificationApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true, activeRole: 'TEACHER' }) }))
vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import TeacherApplicantDashboard from './TeacherApplicantDashboard.vue'

function profile(overrides: Partial<TeacherProfileResponse> = {}): TeacherProfileResponse {
  return {
    teacherId: 3,
    fullName: 'Mei Lin',
    email: 'mei.lin@example.com',
    bio: 'Mandarin teacher for adult beginners.',
    verificationState: 'NOT_SUBMITTED',
    submittedAt: null,
    approvedAt: null,
    latestDecision: null,
    documents: [],
    publishedClasses: [],
    ...overrides,
  }
}

const documents = ['CERTIFICATE', 'IDENTITY', 'QUALIFICATION'].map((type, index) => ({
  id: index + 1,
  documentType: type as 'CERTIFICATE' | 'IDENTITY' | 'QUALIFICATION',
  fileName: `${type}.pdf`,
  fileSize: 1024,
  uploadedAt: '2026-05-14T08:50:00',
  status: 'IN_REVIEW' as const,
}))

async function mountDashboard() {
  const wrapper = mount(TeacherApplicantDashboard, { global: { stubs: { IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('TeacherApplicantDashboard', () => {
  beforeEach(() => {
    teacherVerificationApi.profile.mockReset()
  })

  it('asks a new applicant to upload documents and lists the locked tools', async () => {
    teacherVerificationApi.profile.mockResolvedValue(profile())
    const wrapper = await mountDashboard()

    expect(wrapper.text()).toContain('Verification: Not submitted')
    expect(wrapper.text()).toContain('Teacher applicant · Mei Lin')
    expect(wrapper.find('[data-testid="applicant-upload"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Starts when you submit')
    expect(wrapper.text()).toContain('Live sessions')
    expect(wrapper.findAll('h1')).toHaveLength(1)
  })

  it('explains what happens next while the application is in review', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({ verificationState: 'IN_REVIEW', submittedAt: '2026-05-14T09:00:00', documents }),
    )
    const wrapper = await mountDashboard()

    expect(wrapper.text()).toContain('Verification: In review')
    expect(wrapper.text()).toContain('What happens next')
    expect(wrapper.text()).toContain('3 documents uploaded')
    expect(wrapper.text()).toContain('View verification status')
  })

  it('shows the admin’s reason when changes are requested', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({
        verificationState: 'CHANGES_REQUESTED',
        documents,
        latestDecision: {
          outcome: 'CHANGES_REQUESTED',
          reason: 'The teaching certificate scan is unreadable.',
          reviewerName: 'Olivia Tan',
          decidedAt: '2026-05-18T14:00:00',
        },
      }),
    )
    const wrapper = await mountDashboard()

    const reason = wrapper.find('[data-testid="applicant-reason"]')
    expect(reason.text()).toContain('Reason from Olivia Tan')
    expect(reason.text()).toContain('The teaching certificate scan is unreadable.')
    expect(wrapper.text()).toContain('Fix and resubmit')
  })
})
