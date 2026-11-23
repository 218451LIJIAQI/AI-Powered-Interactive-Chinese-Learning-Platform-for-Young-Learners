import { readonly, ref } from 'vue'

const version = ref(0)
export const progressVersion = readonly(version)

/**
 * Signals that something the shell shows changed without a change of page (a learner's XP or streak, an admin's queue
 * counts), so the views that show it, such as the dashboard layout, can load it again.
 */
export function notifyProgressChanged(): void {
  version.value += 1
}
