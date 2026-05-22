function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value == null || value.trim() === '') {
    return fallback
  }

  return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase())
}

function defaultApiBaseUrl(): string {
  return '/api/v1'
}

export const appEnv = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || defaultApiBaseUrl(),
  useDemoData: parseBoolean(import.meta.env.VITE_USE_DEMO_DATA, false),
  enablePreviewMode: parseBoolean(import.meta.env.VITE_ENABLE_PREVIEW_MODE, false),
  appName: 'MandarinSpark',
} as const
