import { describe, expect, it } from 'vitest'

import { adminUsers } from '@/demo/data'

import { buildAdminUserQuery, clampAdminUserPage, filterDemoAdminUsers } from './adminUserDirectory'

const ALL_FILTERS = { keyword: '', role: 'ALL', active: 'ALL', teacherStatus: 'ALL', page: 0, size: 10 } as const

describe('buildAdminUserQuery', () => {
  it('omits "all" filters and blank keywords', () => {
    expect(buildAdminUserQuery({ ...ALL_FILTERS, keyword: '   ' })).toEqual({ page: 0, size: 10 })
  })

  it('maps keyword, role, teacher status, active, page and size to server parameters', () => {
    expect(
      buildAdminUserQuery({
        keyword: '  Li ',
        role: 'TEACHER',
        active: 'INACTIVE',
        teacherStatus: 'PENDING',
        page: 2,
        size: 20,
      }),
    ).toEqual({ keyword: 'Li', role: 'TEACHER', status: 'PENDING', active: false, page: 2, size: 20 })
    expect(buildAdminUserQuery({ ...ALL_FILTERS, active: 'ACTIVE' }).active).toBe(true)
  })

  it('keeps page and size inside the API limits', () => {
    expect(buildAdminUserQuery({ ...ALL_FILTERS, page: -3, size: 500 })).toEqual({ page: 0, size: 100 })
    expect(buildAdminUserQuery({ ...ALL_FILTERS, size: 0 }).size).toBe(10)
  })
})

describe('filterDemoAdminUsers', () => {
  const users = adminUsers.items

  it('pages the preview directory like the server does', () => {
    const first = filterDemoAdminUsers(users, { page: 0, size: 10 })
    expect(first.items).toHaveLength(10)
    expect(first.totalItems).toBe(users.length)
    expect(first.totalPages).toBe(Math.ceil(users.length / 10))

    const second = filterDemoAdminUsers(users, { page: 1, size: 10 })
    expect(second.items.map((item) => item.userId)).toEqual(users.slice(10).map((item) => item.userId))
  })

  it('matches keyword against display name or email, case-insensitively', () => {
    expect(filterDemoAdminUsers(users, { keyword: 'CHEN', page: 0, size: 10 }).items.map((u) => u.userId)).toEqual([
      110, 202,
    ])
    expect(filterDemoAdminUsers(users, { keyword: 'daniel.admin@', page: 0, size: 10 }).totalItems).toBe(1)
  })

  it('filters by role, teacher status and active flag', () => {
    const teachers = filterDemoAdminUsers(users, { role: 'TEACHER', page: 0, size: 50 })
    expect(teachers.items.every((user) => user.roles.includes('TEACHER'))).toBe(true)

    const pending = filterDemoAdminUsers(users, { role: 'TEACHER', status: 'PENDING', page: 0, size: 50 })
    expect(pending.items.map((user) => user.userId)).toEqual([203])

    const inactive = filterDemoAdminUsers(users, { active: false, page: 0, size: 50 })
    expect(inactive.items.length).toBeGreaterThan(0)
    expect(inactive.items.every((user) => !user.active)).toBe(true)
  })
})

describe('clampAdminUserPage', () => {
  it('steps back to the last page that still exists', () => {
    expect(clampAdminUserPage(3, 2)).toBe(1)
    expect(clampAdminUserPage(1, 2)).toBe(1)
    expect(clampAdminUserPage(2, 0)).toBe(0)
  })
})
