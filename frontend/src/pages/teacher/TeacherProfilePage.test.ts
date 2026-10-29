import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { chooseFile } from '@/test/helpers'
import type { TeacherDocument, TeacherDocumentType, TeacherProfileResponse } from '@/types/ops'

const VALID_BIO = 'Mandarin teacher for adult beginners in Malaysia.'

const teacherVerificationApi = vi.hoisted(() => ({
  profile: vi.fn(),
  updateProfile: vi.fn(),
  upload: vi.fn(),
  submit: vi.fn(),
  document: vi.fn(),
}))
const auth = vi.hoisted(() => ({ refreshTeacherApproval: vi.fn(), refreshCurrentUser: vi.fn() }))

vi.mock('@/api/ops', () => ({ teacherVerificationApi }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('vue-router', async () => ({ RouterLink: (await import('@/test/helpers')).routerLinkStub }))

import TeacherProfilePage from './TeacherProfilePage.vue'

function document(type: TeacherDocumentType, status: TeacherDocument['status'] = 'READY'): TeacherDocument {
  return {
    id: type.length,
    documentType: type,
    fileName: `MeiLin_${type}.pdf`,
    fileSize: 2048,
    uploadedAt: '2026-05-14T08:50:00',
    status,
  }
}

function profile(overrides: Partial<TeacherProfileResponse> = {}): TeacherProfileResponse {
  return {
    teacherId: 3,
    fullName: 'Mei Lin',
    email: 'mei.lin@example.com',
    bio: VALID_BIO,
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
  const wrapper = mount(TeacherProfilePage, { global: { stubs: { teleport: true, IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('TeacherProfilePage', () => {
  beforeEach(() => {
    teacherVerificationApi.profile.mockResolvedValue(profile())
  })

  it('keeps Submit disabled until both required documents are uploaded and the consent is ticked', async () => {
    teacherVerificationApi.profile.mockResolvedValue(profile({ documents: [document('CERTIFICATE')] }))
    const wrapper = await mountPage()
    const submit = () => wrapper.find('[data-testid="teaching-submit"]')

    expect(submit().attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Upload your teaching certificate and identity document first.')

    teacherVerificationApi.upload.mockResolvedValue(
      profile({ documents: [document('CERTIFICATE'), document('IDENTITY')] }),
    )
    await chooseFile(
      wrapper.find('[data-testid="teaching-file-IDENTITY"]'),
      new File(['%PDF'], 'MeiLin_IC.pdf', { type: 'application/pdf' }),
    )
    await flushPromises()
    expect(teacherVerificationApi.upload).toHaveBeenCalledWith('IDENTITY', expect.any(File))
    expect(submit().attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="teaching-consent"]').setValue(true)
    expect(submit().attributes('disabled')).toBeUndefined()
  })

  it('submits the application, shows the notice and refreshes the account state', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({ documents: [document('CERTIFICATE'), document('IDENTITY')] }),
    )
    teacherVerificationApi.submit.mockResolvedValue(
      profile({
        verificationState: 'IN_REVIEW',
        submittedAt: '2026-05-14T09:00:00',
        documents: [document('CERTIFICATE', 'IN_REVIEW'), document('IDENTITY', 'IN_REVIEW')],
      }),
    )
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="teaching-consent"]').setValue(true)
    await wrapper.find('form.teaching-submit').trigger('submit')
    await flushPromises()

    expect(teacherVerificationApi.submit).toHaveBeenCalled()
    expect(auth.refreshTeacherApproval).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Submitted for verification.')
    expect(wrapper.find('[data-testid="teaching-state"]').text()).toBe('In review')
  })

  it('makes the documents read-only while the application is in review', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({
        verificationState: 'IN_REVIEW',
        submittedAt: '2026-05-14T09:00:00',
        documents: [document('CERTIFICATE', 'IN_REVIEW'), document('IDENTITY', 'IN_REVIEW')],
      }),
    )
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="teaching-read-only"]').text()).toBe(
      'Documents can’t be changed while they are in review.',
    )
    expect(wrapper.find('[data-testid="teaching-upload-CERTIFICATE"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="teaching-submit"]').exists()).toBe(false)
  })

  it('shows the admin’s reason when changes were requested and offers to resubmit', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({
        verificationState: 'CHANGES_REQUESTED',
        latestDecision: {
          outcome: 'CHANGES_REQUESTED',
          reason: 'The teaching certificate scan is unreadable.',
          reviewerName: 'Olivia Tan',
          decidedAt: '2026-05-18T14:00:00',
        },
        documents: [document('CERTIFICATE', 'READY'), document('IDENTITY', 'SENT')],
      }),
    )
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="teaching-reason"]').text()).toContain('Changes requested by Olivia Tan')
    expect(wrapper.find('[data-testid="teaching-doc-IDENTITY"]').text()).toContain('Already sent')
    expect(wrapper.find('[data-testid="teaching-submit"]').text()).toContain('Submit for verification again')
  })

  it('refuses a document of the wrong type before uploading', async () => {
    const wrapper = await mountPage()

    await chooseFile(
      wrapper.find('[data-testid="teaching-file-CERTIFICATE"]'),
      new File(['x'], 'certificate.docx', { type: 'application/msword' }),
    )

    expect(teacherVerificationApi.upload).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="teaching-doc-CERTIFICATE"]').text()).toContain('Choose a PDF, JPG or PNG file.')
  })

  it('saves the public profile with the bio rule of sign-up', async () => {
    teacherVerificationApi.updateProfile.mockImplementation(async (payload: { fullName: string; bio: string }) =>
      profile(payload),
    )
    const wrapper = await mountPage()

    await wrapper.find('#teaching-bio').setValue('Too short.')
    await wrapper.find('form.form-grid').trigger('submit')
    expect(wrapper.find('#teaching-bio-error').text()).toBe('Teacher bio must be between 20 and 1000 characters.')
    expect(teacherVerificationApi.updateProfile).not.toHaveBeenCalled()

    await wrapper.find('#teaching-bio').setValue(`  ${VALID_BIO}  `)
    await wrapper.find('form.form-grid').trigger('submit')
    await flushPromises()
    expect(teacherVerificationApi.updateProfile).toHaveBeenCalledWith({ fullName: 'Mei Lin', bio: VALID_BIO })
    expect(wrapper.find('[data-testid="toast"]').text()).toBe('Changes saved.')
    expect(auth.refreshCurrentUser).toHaveBeenCalled()
  })

  it('confirms an approved teacher with verified documents and no upload buttons', async () => {
    teacherVerificationApi.profile.mockResolvedValue(
      profile({
        verificationState: 'APPROVED',
        approvedAt: '2026-03-20T10:00:00',
        documents: [document('CERTIFICATE', 'VERIFIED'), document('IDENTITY', 'VERIFIED')],
        publishedClasses: [{ id: 4, title: 'Mandarin for Beginners' }],
      }),
    )
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('All teaching tools are available.')
    expect(wrapper.find('[data-testid="teaching-doc-CERTIFICATE"]').text()).toContain('Verified')
    expect(wrapper.find('[data-testid="teaching-upload-CERTIFICATE"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Mandarin for Beginners')
  })
})
