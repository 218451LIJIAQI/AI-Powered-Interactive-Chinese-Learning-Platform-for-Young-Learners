/**
 * Single-source build-time configuration the rest of the app consumes
 * through {@code appEnv}.
 *
 * <h2>Why a wrapper</h2>
 * <p>Reading `import.meta.env.*` directly scatters magic strings across
 * the code and makes typo defects easy. Centralising here gives:</p>
 * <ul>
 *   <li>Boolean parsing that treats {@code "1" / "true" / "yes" / "on"}
 *       interchangeably so devs writing {@code .env} files don't have
 *       to remember the exact spelling.</li>
 *   <li>A safe default `/api/v1` API base so a missing env var lets
 *       the dev server proxy work out of the box.</li>
 *   <li>{@code as const} on the export so consumers get literal types
 *       (e.g. discriminated unions in router guards) without
 *       additional type assertions.</li>
 * </ul>
 */
function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value == null || value.trim() === '') {
    return fallback
  }

  return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase())
}

export const appEnv = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  useDemoData: parseBoolean(import.meta.env.VITE_USE_DEMO_DATA, false),
  enablePreviewMode: parseBoolean(import.meta.env.VITE_ENABLE_PREVIEW_MODE, false),
  sentryDsn: import.meta.env.VITE_SENTRY_DSN || '',
  release: import.meta.env.VITE_APP_RELEASE || import.meta.env.VITE_GIT_SHA || '',
  environment: import.meta.env.MODE || 'local',
  appName: 'MandarinSpark',
} as const
