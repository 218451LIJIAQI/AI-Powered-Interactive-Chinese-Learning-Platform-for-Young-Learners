import { nextTick } from 'vue'

/**
 * Restores keyboard focus after an async control disables/re-enables or unmounts and drops focus to `document.body`.
 * Never steals focus if the user already moved it to another connected control.
 */
export async function restoreFocusIfDropped(
  target: () => HTMLElement | null | undefined,
  isValid: () => boolean = () => true,
): Promise<void> {
  await nextTick()
  if (!isValid()) return
  const active = document.activeElement
  if (!active || active === document.body || !active.isConnected) target()?.focus()
}
