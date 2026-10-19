import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/plugins/i18n'

import { useTeacherAccess } from './useTeacherAccess'

const mocks = vi.hoisted(() => ({
  auth: {
    activeRole: 'TEACHER' as string | null,
    currentUser: { teacherProfile: { teacherStatus: 'APPROVED' } } as {
      teacherProfile: { teacherStatus: string } | null
    } | null,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.auth,
}))

function mountAccess(): ReturnType<typeof useTeacherAccess> {
  let access: ReturnType<typeof useTeacherAccess> | undefined
  mount(
    defineComponent({
      setup() {
        access = useTeacherAccess()
        return () => null
      },
    }),
    { global: { plugins: [i18n] } },
  )
  if (!access) throw new Error('access did not initialise')
  return access
}

describe('useTeacherAccess (D13)', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    mocks.auth.activeRole = 'TEACHER'
    mocks.auth.currentUser = { teacherProfile: { teacherStatus: 'APPROVED' } }
  })

  it('locks pending and rejected teachers but not approved ones', () => {
    expect(mountAccess().teacherApprovalLocked.value).toBe(false)

    mocks.auth.currentUser = { teacherProfile: { teacherStatus: 'PENDING' } }
    const pending = mountAccess()
    expect(pending.teacherApprovalLocked.value).toBe(true)
    expect(pending.teacherApprovalTitle.value).toBe('Verification pending')

    mocks.auth.currentUser = { teacherProfile: { teacherStatus: 'REJECTED' } }
    expect(mountAccess().teacherApprovalTitle.value).toBe('Verification needs attention')
  })

  it('switches to a friendly locked state when the backend answers 403', async () => {
    const access = mountAccess()
    const forbidden = {
      response: { status: 403, data: { message: 'Only approved teachers can use this teaching feature.' } },
    }

    await expect(access.guardTeacherRequest(() => Promise.reject(forbidden))).rejects.toBe(forbidden)
    expect(access.teacherApprovalLocked.value).toBe(true)
    expect(access.teacherApprovalTitle.value).toBe('Teaching tools are locked')
  })

  it('does not lock on other errors and passes successful results through', async () => {
    const access = mountAccess()
    await expect(access.guardTeacherRequest(() => Promise.resolve(42))).resolves.toBe(42)
    await expect(access.guardTeacherRequest(() => Promise.reject({ response: { status: 500 } }))).rejects.toBeTruthy()
    expect(access.teacherApprovalLocked.value).toBe(false)
  })
})
