import { readonly, ref } from 'vue'

/**
 * Signals that the learner earned points or finished something without changing page (a quiz result, a completed
 * activity), so views that show the reward summary, such as the dashboard sidebar, can load it again.
 */
const progressVersion = ref(0)

export function notifyProgressChanged(): void {
  progressVersion.value += 1
}

export function useProgressVersion() {
  return readonly(progressVersion)
}
