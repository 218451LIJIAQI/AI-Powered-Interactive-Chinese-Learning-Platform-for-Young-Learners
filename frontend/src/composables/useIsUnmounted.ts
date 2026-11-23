import { onBeforeUnmount } from 'vue'

/** Ignore a late response once the component that requested it starts unmounting. */
export function useIsUnmounted(): () => boolean {
  let unmounted = false
  onBeforeUnmount(() => {
    unmounted = true
  })
  return () => unmounted
}
