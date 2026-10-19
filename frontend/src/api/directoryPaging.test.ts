import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { http } from './http'
import { adminApi, studentApi } from './services'

const originalAdapter = http.defaults.adapter

interface Captured {
  url: string
  params: unknown
}

function envelope(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data: { success: true, data }, status: 200, statusText: 'OK', headers: {}, config }
}

function install(respond: (config: InternalAxiosRequestConfig) => unknown): Captured[] {
  const calls: Captured[] = []
  http.defaults.adapter = (async (config) => {
    calls.push({ url: config.url ?? '', params: config.params })
    return envelope(config, respond(config))
  }) satisfies AxiosAdapter
  return calls
}

afterEach(() => {
  http.defaults.adapter = originalAdapter
})

describe('studentApi.liveClasses (FR-LIVE-05)', () => {
  it('loads every page of upcoming classes at the maximum page size', async () => {
    const calls = install((config) => {
      const page = (config.params as { page: number }).page
      return { items: [{ id: page * 10 + 1 }, { id: page * 10 + 2 }], page, size: 100, totalItems: 6, totalPages: 3 }
    })

    const items = await studentApi.liveClasses()

    expect(calls.map((call) => call.params)).toEqual([
      { page: 0, size: 100 },
      { page: 1, size: 100 },
      { page: 2, size: 100 },
    ])
    expect(calls.every((call) => call.url === '/students/live-classes')).toBe(true)
    expect(items.map((item) => item.id)).toEqual([1, 2, 11, 12, 21, 22])
  })

  it('stops after one request when everything fits on the first page', async () => {
    const calls = install(() => ({ items: [{ id: 1 }], page: 0, size: 100, totalItems: 1, totalPages: 1 }))
    await expect(studentApi.liveClasses()).resolves.toHaveLength(1)
    expect(calls).toHaveLength(1)
  })
})

describe('adminApi.users (FR-ADM-02)', () => {
  it('sends keyword, role, teacher status, active, page and size to the server', async () => {
    const calls = install(() => ({ items: [], page: 2, size: 20, totalItems: 41, totalPages: 3 }))

    const page = await adminApi.users({
      keyword: 'li',
      role: 'TEACHER',
      status: 'APPROVED',
      active: false,
      page: 2,
      size: 20,
    })

    expect(calls).toEqual([
      {
        url: '/admin/users',
        params: { keyword: 'li', role: 'TEACHER', status: 'APPROVED', active: false, page: 2, size: 20 },
      },
    ])
    expect(page.totalItems).toBe(41)
  })
})
