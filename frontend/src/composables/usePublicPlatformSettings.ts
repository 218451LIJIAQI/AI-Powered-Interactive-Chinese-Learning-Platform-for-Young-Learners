import { computed, readonly } from 'vue'

import { publicApi } from '@/api/services'
import { useResource } from '@/composables/useResource'

/**
 * What the privacy and responsible-AI pages may say about the AI provider. `unknown` until the public settings answer
 * (or when they cannot be loaded): only what is true for every provider may be said then. `off`: no AI request can
 * reach the text/photo provider. `openai`: the OpenAI Responses API (store=false). `other`: a text-only Chat
 * Completions provider. Pronunciation uses a separately configured speech service.
 */
type PublicAiProvider =
  | { kind: 'unknown' }
  | { kind: 'off' }
  | { kind: 'openai'; model: string | null }
  | { kind: 'other'; provider: string; model: string | null }

/** The build-time deployment used by the live call and by its privacy disclosures. */
export function configuredTavusDeploymentId(): string {
  return (import.meta.env.VITE_TAVUS_DEPLOYMENT_ID ?? '').trim()
}

/** The public platform settings (`GET /public/platform-settings`), loaded once the page is mounted. */
export function usePublicPlatformSettings() {
  const { data: settings } = useResource(() => publicApi.platformSettings().catch(() => null))

  const aiProvider = computed<PublicAiProvider>(() => {
    const loaded = settings.value
    // A server that does not send the field says nothing about its provider.
    if (!loaded || loaded.aiProvider === undefined) return { kind: 'unknown' }
    const provider = loaded.aiProvider?.trim().toLowerCase()
    if (!provider) return { kind: 'off' }
    const model = loaded.aiModelName?.trim() || null
    return provider === 'openai' ? { kind: 'openai', model } : { kind: 'other', provider, model }
  })

  return { settings: readonly(settings), aiProvider }
}

/** The suffix naming a Chat Completions provider inside `aiDisclosureMessages`, or null for a name shown as configured. */
export function aiProviderNameKey(provider: string): 'providerDeepseek' | 'providerCompatible' | null {
  if (provider === 'deepseek') return 'providerDeepseek'
  if (provider === 'openai-compatible') return 'providerCompatible'
  return null
}
