import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173'
const parsedBaseUrl = new URL(baseURL)
const e2eHost = process.env.E2E_HOST || parsedBaseUrl.hostname
const e2ePort = process.env.E2E_PORT || parsedBaseUrl.port || '4173'
const skipWebServer = process.env.E2E_SKIP_WEB_SERVER === 'true'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  webServer: skipWebServer
    ? undefined
    : {
        command: `npm run dev -- --host ${e2eHost} --port ${e2ePort} --strictPort --mode previewqa`,
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
        url: baseURL,
      },
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
