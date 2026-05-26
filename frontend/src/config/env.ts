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

const UNSAFE_URL_CODE_POINT_PATTERN = /[\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff]/
const SAFE_API_PROTOCOLS = new Set(['http:', 'https:'])

/**
 * Normalises the API base URL used by axios and streaming `fetch`
 * calls. The value is build-time configuration, so invalid input
 * should fail fast instead of causing vague runtime network errors.
 */
export function normalizeApiBaseUrl(value: string | undefined): string {
  const trimmed = value?.trim() || '/api/v1'

  if (UNSAFE_URL_CODE_POINT_PATTERN.test(trimmed) || trimmed.includes('\\')) {
    throw new Error('VITE_API_BASE_URL contains unsafe URL characters.')
  }

  if (trimmed.startsWith('//')) {
    throw new Error('VITE_API_BASE_URL must not be protocol-relative.')
  }

  if (trimmed.startsWith('/')) {
    return trimmed.replace(/\/+$/, '') || '/'
  }

  try {
    const url = new URL(trimmed)
    if (!SAFE_API_PROTOCOLS.has(url.protocol)) {
      throw new Error('VITE_API_BASE_URL must use http or https.')
    }
    if (url.username || url.password) {
      throw new Error('VITE_API_BASE_URL must not contain credentials.')
    }
    if (url.search || url.hash) {
      throw new Error('VITE_API_BASE_URL must not contain query strings or fragments.')
    }
    return url.href.replace(/\/+$/, '')
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('VITE_API_BASE_URL')) {
      throw error
    }
    throw new Error('VITE_API_BASE_URL must be an absolute http(s) URL or a same-origin path.', { cause: error })
  }
}

export const appEnv = {
  apiBaseUrl: normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
  useDemoData: parseBoolean(import.meta.env.VITE_USE_DEMO_DATA, false),
  enablePreviewMode: parseBoolean(import.meta.env.VITE_ENABLE_PREVIEW_MODE, false),
  sentryDsn: import.meta.env.VITE_SENTRY_DSN || '',
  release: import.meta.env.VITE_APP_RELEASE || import.meta.env.VITE_GIT_SHA || '',
  environment: import.meta.env.MODE || 'local',
  appName: 'MandarinSpark',
} as const
