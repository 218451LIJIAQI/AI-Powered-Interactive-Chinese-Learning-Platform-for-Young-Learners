import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  workers: 1,
  use: {
    ...devices['Desktop Chrome'],
    channel: process.env.PLAYWRIGHT_BROWSER_CHANNEL || undefined,
    baseURL: 'http://127.0.0.1:5185',
    // No retries are configured, so keep the trace of every failing test.
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5185 --strictPort',
    url: 'http://127.0.0.1:5185/',
    timeout: 60_000,
    reuseExistingServer: false,
    env: {
      VITE_API_BASE_URL: '/api/v1',
      VITE_USE_DEMO_DATA: 'true',
      VITE_ENABLE_PREVIEW_MODE: 'true',
    },
  },
})
