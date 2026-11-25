import { defineConfig, devices } from '@playwright/test'

import { E2E_ACCOUNTS, E2E_BACKEND_PORT, E2E_FRONTEND_PORT } from './e2e/accounts'

/**
 * End-to-end tests run against the real stack: the Spring Boot backend in its `demo` profile (the demo personas and
 * content seeded at start-up into a rebuilt MySQL database, AI provider off) and the Vite dev server proxying `/api/v1`
 * to it. Both use their own ports and the backend its own database. The backend reaches MySQL through DB_HOST,
 * DB_PORT, DB_USERNAME and DB_PASSWORD. Running the packaged jar also works in Windows paths outside the system code page.
 */
const isWindows = process.platform === 'win32'
const backendUrl = `http://127.0.0.1:${E2E_BACKEND_PORT}`
const frontendUrl = `http://127.0.0.1:${E2E_FRONTEND_PORT}`
const backendBuildDir = 'target-e2e'

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
    baseURL: frontendUrl,
    // No retries are configured, so keep the trace of every failing test.
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: `${isWindows ? '.\\mvnw.cmd' : './mvnw'} -q -Dmaven.test.skip=true -Dmandarinspark.build.directory=${backendBuildDir} package && java -jar ${backendBuildDir}/mandarinspark.jar --spring.profiles.active=demo`,
      cwd: '../backend',
      url: `${backendUrl}/api/v1/health`,
      timeout: 300_000,
      env: {
        SERVER_PORT: String(E2E_BACKEND_PORT),
        DEFAULT_ADMIN_EMAIL: E2E_ACCOUNTS.admin.email,
        DEFAULT_ADMIN_PASSWORD: E2E_ACCOUNTS.admin.password,
        DEMO_STUDENT_PASSWORD: E2E_ACCOUNTS.student.password,
        DEMO_DB_NAME: 'ai_chinese_learning_e2e',
        UPLOAD_DIR: `${backendBuildDir}/uploads`,
        RECORDED_VIDEO_UPLOAD_DIR: `${backendBuildDir}/uploads/recorded-videos`,
        APP_CORS_ALLOWED_ORIGINS: frontendUrl,
        // Never leak real external AI/SMTP calls if the host shell has credentials or flags exported.
        AI_ENABLE_REAL_PROVIDER: 'false',
        APP_AI_ENABLE_REAL_PROVIDER: 'false',
        SMTP_ENABLED: 'false',
        APP_EMAIL_ENABLED: 'false',
        // No scheduled jobs (the admins' daily summary e-mail, learner reminders) in the middle of a run.
        APP_SCHEDULING_ENABLED: 'false',
      },
    },
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${E2E_FRONTEND_PORT} --strictPort`,
      url: `${frontendUrl}/`,
      env: {
        VITE_API_PROXY_TARGET: backendUrl,
        // The tests expect the AI Helper without the live video call, whatever a developer's .env.local names.
        VITE_TAVUS_DEPLOYMENT_ID: '',
      },
    },
  ],
})
