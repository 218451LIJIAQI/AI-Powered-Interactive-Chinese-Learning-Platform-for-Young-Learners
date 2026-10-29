import { readonly, ref } from 'vue'

import { adminAiApi } from '@/api/ai'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import type { AdminAiReportCounts } from '@/types/ai'

/** One shared copy, so the tab label and the Reports page agree after a decision. */
const counts = ref<AdminAiReportCounts | null>(null)

/**
 * Open / resolved / dismissed AI report counts for the "Reports (N open)" tab. After a decision the page reloads them
 * and asks the layout to refresh the sidebar badges (the same signal the student pages use after earning XP).
 */
export function useAiReportCounts() {
  async function reloadCounts(): Promise<void> {
    try {
      counts.value = await adminAiApi.reportCounts()
    } catch {
      // The tab then reads "Reports" without a count; the page shows its own loading errors.
    }
  }

  async function afterDecision(): Promise<void> {
    await reloadCounts()
    notifyProgressChanged()
  }

  return { counts: readonly(counts), reloadCounts, afterDecision }
}
