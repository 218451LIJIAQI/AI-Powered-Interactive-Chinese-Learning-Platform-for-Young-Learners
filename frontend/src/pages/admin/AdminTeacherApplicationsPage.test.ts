import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { TeacherApplication, TeacherApplicationSummary } from '@/types/ops'

import { mountAdminPage } from './adminPageTestSetup'

const usersApi = vi.hoisted(() => ({
  applications: vi.fn(),
  application: vi.fn(),
  approve: vi.fn(),
  requestChanges: vi.fn(),
  document: vi.fn(),
}))

const navCounts = vi.hoisted(() => vi.fn())

vi.mock('@/api/ops', () => ({ adminUsersApi: usersApi }))
vi.mock('@/api/shell', () => ({ shellApi: { navCounts } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

import AdminTeacherApplicationsPage from './AdminTeacherApplicationsPage.vue'

const farah: TeacherApplicationSummary = {
  teacherId: 7,
  userId: 70,
  fullName: 'Farah Aziz',
  email: 'farah@example.com',
  submittedAt: '2026-05-13T16:00:00',
  documentCount: 2,
}
const mei: TeacherApplicationSummary = {
  teacherId: 8,
  userId: 80,
  fullName: 'Mei Lin',
  email: 'mei@example.com',
  submittedAt: '2026-05-14T09:00:00',
  documentCount: 1,
}

function applicationOf(summary: TeacherApplicationSummary): TeacherApplication {
  return {
    teacherId: summary.teacherId,
    userId: summary.userId,
    fullName: summary.fullName,
    email: summary.email,
    bio: 'I teach Mandarin to primary pupils.',
    verificationState: 'IN_REVIEW',
    submittedAt: summary.submittedAt,
    documents: [
      {
        id: 31,
        documentType: 'CERTIFICATE',
        fileName: 'certificate.pdf',
        fileSize: 2048,
        uploadedAt: '2026-05-13T15:00:00',
      },
      {
        id: 32,
        documentType: 'IDENTITY',
        fileName: 'ic.png',
        fileSize: 1024,
        uploadedAt: '2026-05-13T15:10:00',
      },
    ],
    history: [
      { event: 'ACCOUNT_CREATED', actorName: null, at: '2026-05-01T09:00:00', note: null },
      { event: 'CHANGES_REQUESTED', actorName: 'Nur Admin', at: '2026-05-10T09:00:00', note: 'Add your certificate.' },
      { event: 'APPLICATION_SUBMITTED', actorName: summary.fullName, at: summary.submittedAt as string, note: null },
    ],
  }
}

describe('AdminTeacherApplicationsPage', () => {
  beforeEach(() => {
    navCounts.mockReset()
    usersApi.applications.mockResolvedValue([farah, mei])
    usersApi.application.mockImplementation(async (id: number) => applicationOf(id === farah.teacherId ? farah : mei))
  })

  it('lists the waiting applications in the server’s oldest-first order and opens the oldest', async () => {
    const { wrapper, router } = await mountAdminPage(AdminTeacherApplicationsPage, '/admin/users/applications')
    await flushPromises()

    const items = wrapper.findAll('[data-testid="admin-applications-item"]')
    expect(items.map((item) => item.find('strong').text())).toEqual(['Farah Aziz', 'Mei Lin'])
    expect(router.currentRoute.value.query.teacher).toBe('7')
    expect(usersApi.application).toHaveBeenCalledWith(7)

    const detail = wrapper.get('[data-testid="admin-applications-detail"]').text()
    expect(detail).toContain('I teach Mandarin to primary pupils.')
    expect(detail).toContain('2 files')
    expect(detail).toContain('Changes requested')
    expect(detail).toContain('Add your certificate.')
    expect(wrapper.findAll('[data-testid="admin-applications-document"]')).toHaveLength(2)
    expect(wrapper.find('a[href="/admin/users?user=70"]').exists()).toBe(true)
    // The applications tab counts the list on the page; no second request for it.
    expect(wrapper.get('[data-testid="admin-users-tab-applications"] .route-tab-count').text()).toBe('2')
    expect(usersApi.applications).toHaveBeenCalledTimes(1)
    expect(navCounts).not.toHaveBeenCalled()

    await items[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.teacher).toBe('8')
    expect(wrapper.get('[data-testid="admin-applications-detail"]').text()).toContain('Mei Lin')
  })

  it('approves an application and moves on to the next one waiting', async () => {
    usersApi.approve.mockResolvedValue(applicationOf(farah))
    const { wrapper, router } = await mountAdminPage(
      AdminTeacherApplicationsPage,
      '/admin/users/applications?teacher=7',
    )
    await flushPromises()

    usersApi.applications.mockResolvedValue([mei])
    await wrapper.get('[data-testid="admin-applications-approve"]').trigger('click')
    expect(wrapper.get('[data-testid="admin-applications-dialog"]').text()).toContain('Approve Farah Aziz?')
    await wrapper.get('[data-testid="admin-applications-confirm"]').trigger('click')
    await flushPromises()

    expect(usersApi.approve).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('Farah Aziz is approved and can use all teaching tools now.')
    expect(router.currentRoute.value.query.teacher).toBe('8')
    expect(wrapper.findAll('[data-testid="admin-applications-item"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="admin-users-tab-applications"] .route-tab-count').text()).toBe('1')
  })

  it('needs a reason to request changes and clears the selection when nothing else waits', async () => {
    usersApi.applications.mockResolvedValue([farah])
    usersApi.requestChanges.mockResolvedValue({ ...applicationOf(farah), verificationState: 'CHANGES_REQUESTED' })
    const { wrapper, router } = await mountAdminPage(
      AdminTeacherApplicationsPage,
      '/admin/users/applications?teacher=7',
    )
    await flushPromises()

    await wrapper.get('[data-testid="admin-applications-request-changes"]').trigger('click')
    await wrapper.get('[data-testid="admin-applications-confirm"]').trigger('click')
    expect(wrapper.get('[data-testid="admin-applications-dialog"]').text()).toContain('Write a reason first.')
    expect(usersApi.requestChanges).not.toHaveBeenCalled()

    usersApi.applications.mockResolvedValue([])
    await wrapper.get('[data-testid="admin-applications-reason"]').setValue('  Upload a clearer IC photo. ')
    await wrapper.get('[data-testid="admin-applications-confirm"]').trigger('click')
    await flushPromises()

    expect(usersApi.requestChanges).toHaveBeenCalledWith(7, 'Upload a clearer IC photo.')
    expect(wrapper.text()).toContain('Changes requested from Farah Aziz.')
    expect(router.currentRoute.value.query.teacher).toBeUndefined()
    expect(wrapper.text()).toContain('No applications are waiting')
    expect(wrapper.find('[data-testid="admin-users-tab-applications"] .route-tab-count').exists()).toBe(false)
  })

  it('does not offer a decision on an application that is no longer waiting', async () => {
    usersApi.application.mockResolvedValue({ ...applicationOf(farah), verificationState: 'APPROVED' })
    const { wrapper } = await mountAdminPage(AdminTeacherApplicationsPage, '/admin/users/applications?teacher=7')
    await flushPromises()

    expect(wrapper.find('[data-testid="admin-applications-approve"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('This application is not waiting for a decision.')
  })

  it('opens a document in a new tab', async () => {
    const tab = { opener: {}, location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    URL.createObjectURL = vi.fn(() => 'blob:certificate')
    URL.revokeObjectURL = vi.fn()
    usersApi.document.mockResolvedValue({ blob: new Blob(['%PDF'], { type: 'application/pdf' }), fileName: null })
    const { wrapper } = await mountAdminPage(AdminTeacherApplicationsPage, '/admin/users/applications?teacher=7')
    await flushPromises()

    await wrapper.get('[data-testid="admin-applications-document"] button').trigger('click')
    await flushPromises()

    expect(usersApi.document).toHaveBeenCalledWith(31)
    expect(tab.opener).toBeNull()
    expect(tab.location.href).toBe('blob:certificate')
  })
})
