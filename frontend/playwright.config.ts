import { defineConfig, devices } from '@playwright/test'

import { E2E_ACCOUNTS, E2E_BACKEND_PORT, E2E_FRONTEND_PORT } from './e2e/accounts'

/**
 * End-to-end tests run against the real stack: the Spring Boot backend in its in-memory `demo` profile (H2, the demo
 * personas and content seeded at start-up, AI provider off) and the Vite dev server proxying `/api/v1` to it. Both
 * use their own ports, so a running local copy of the app is not touched. The first start compiles the backend and
 * takes a minute or two.
 */
const isWindows = process.platform === 'win32'
const backendUrl = `http://127.0.0.1:${E2E_BACKEND_PORT}`

export default defineConfig({
  testDir: './e2e',
  expect: { timeout: 15_000 },
  timeout: 90_000,
  // The tests share one seeded backend and some of them change its data, so they run one at a time, in file order.
  workers: 1,
  // The health check answers before the demo data is seeded; this waits for the seed after the servers are up.
  globalSetup: './e2e/global-setup.ts',
  use: {
    ...devices['Desktop Chrome'],
    channel: process.env.PLAYWRIGHT_BROWSER_CHANNEL || undefined,
    baseURL: `http://127.0.0.1:${E2E_FRONTEND_PORT}`,
    // No retries are configured, so keep the trace of every failing test.
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: `${isWindows ? '.\\mvnw.cmd' : './mvnw'} -q spring-boot:run -Dspring-boot.run.profiles=demo`,
      cwd: '../backend',
      url: `${backendUrl}/api/v1/health`,
      timeout: 300_000,
      reuseExistingServer: false,
      env: {
        SERVER_PORT: String(E2E_BACKEND_PORT),
        DEFAULT_ADMIN_EMAIL: E2E_ACCOUNTS.admin.email,
        DEFAULT_ADMIN_PASSWORD: E2E_ACCOUNTS.admin.password,
        DEMO_STUDENT_PASSWORD: E2E_ACCOUNTS.student.password,
        DEMO_TEACHER_PASSWORD: E2E_ACCOUNTS.teacher.password,
        AI_ENABLE_REAL_PROVIDER: 'false',
        // The in-memory demo data is dropped after the run; its uploads go to a build folder, not backend/uploads.
        UPLOAD_DIR: './target/e2e-uploads',
        RECORDED_VIDEO_UPLOAD_DIR: './target/e2e-uploads/recorded-videos',
        APP_CORS_ALLOWED_ORIGINS: `http://127.0.0.1:${E2E_FRONTEND_PORT}`,
      },
    },
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${E2E_FRONTEND_PORT} --strictPort`,
      url: `http://127.0.0.1:${E2E_FRONTEND_PORT}/`,
      reuseExistingServer: false,
      env: {
        VITE_API_BASE_URL: '/api/v1',
        VITE_API_PROXY_TARGET: backendUrl,
      },
    },
  ],
})
