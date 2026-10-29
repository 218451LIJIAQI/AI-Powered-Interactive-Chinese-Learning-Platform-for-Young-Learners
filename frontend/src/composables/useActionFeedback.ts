import { ref } from 'vue'

import { resolveApiError } from '@/utils/apiError'

/**
 * The outcome of a page's last action, shown in its notice: an error or a success message, never both.
 * `clearFeedback()` empties both before a new action; `showError` / `showSuccess` replace the other message.
 * `runAction` runs one write: it clears both messages, then shows the success text the action resolves to, or the
 * request error (`fallback` when the server sent no readable message). It never throws and resolves to whether the
 * write succeeded.
 */
export function useActionFeedback() {
  const actionError = ref<string | null>(null)
  const actionSuccess = ref<string | null>(null)

  function clearFeedback(): void {
    actionError.value = null
    actionSuccess.value = null
  }

  function showError(message: string): void {
    actionError.value = message
    actionSuccess.value = null
  }

  function showSuccess(message: string): void {
    actionSuccess.value = message
    actionError.value = null
  }

  async function runAction(fallback: string, action: () => Promise<string>): Promise<boolean> {
    clearFeedback()
    try {
      actionSuccess.value = await action()
      return true
    } catch (reason) {
      actionError.value = resolveApiError(reason, fallback)
      return false
    }
  }

  return { actionError, actionSuccess, clearFeedback, showError, showSuccess, runAction }
}
