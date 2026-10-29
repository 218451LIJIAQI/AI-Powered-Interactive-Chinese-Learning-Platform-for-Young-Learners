import { expect, request } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { API_BASE_URL, apiToken, bearer } from './support'

/**
 * Waits until the demo data is in. The backend answers its health check (the webServer `url`) as soon as Tomcat is
 * up, but the demo profile seeds in start-up runners that finish seconds later (config/seed), so the first spec could
 * otherwise meet an empty class catalogue. Signs in once only: sign-in attempts are rate-limited.
 */
export default async function waitForDemoData(): Promise<void> {
  const api = await request.newContext()
  try {
    // DemoClassesSeeder has published the catalogue; the admin account (DataSeeder, run first) exists by then.
    await expect
      .poll(
        async () => {
          const response = await api.get(`${API_BASE_URL}/public/classes?page=0&size=1`)
          return ((await response.json()) as { data: { totalItems: number } }).data.totalItems
        },
        { timeout: 120_000 },
      )
      .toBeGreaterThan(0)

    // DemoOpsSeeder runs last and saves everything in one transaction: once its support requests are there, so is
    // the rest of the demo data.
    const headers = bearer(await apiToken(api, E2E_ACCOUNTS.admin))
    await expect
      .poll(
        async () => {
          const response = await api.get(`${API_BASE_URL}/admin/support?page=0&size=1`, { headers })
          return ((await response.json()) as { data: { requests: { totalItems: number } } }).data.requests.totalItems
        },
        { timeout: 120_000 },
      )
      .toBeGreaterThan(0)
  } finally {
    await api.dispose()
  }
}
