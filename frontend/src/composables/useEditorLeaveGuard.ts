import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { useRouter, type RouteLocationNormalized, type RouteLocationRaw } from 'vue-router'

/**
 * Keep edits across an editor's tabs and confirm before leaving the edited record.
 * `savingCurrent` is true only while the exact current form snapshot is already being saved:
 * SPA navigation can proceed (the request completes in the background), while tab close still warns.
 */
export function useEditorLeaveGuard(
  isEditorRoute: (route: RouteLocationNormalized) => boolean,
  dirty: Readonly<Ref<boolean>>,
  savingCurrent: Readonly<Ref<boolean>>,
) {
  const router = useRouter()
  const leaveTarget = ref<string | null>(null)
  let confirmed = false

  watch(dirty, (isDirty) => {
    if (!isDirty) leaveTarget.value = null
  })

  const removeGuard = router.beforeEach((to, from) => {
    if (confirmed || savingCurrent.value || !dirty.value || !isEditorRoute(from) || isEditorRoute(to)) return
    // The app's own redirects (session ended, access denied, service down) never wait for an answer.
    if (to.name === 'auth' || to.name === 'unauthorized' || to.name === 'maintenance') return
    leaveTarget.value = to.fullPath
    return false
  })

  function warnOnUnload(event: BeforeUnloadEvent): void {
    if (dirty.value || savingCurrent.value) event.preventDefault()
  }

  onMounted(() => window.addEventListener('beforeunload', warnOnUnload))
  onBeforeUnmount(() => {
    removeGuard()
    window.removeEventListener('beforeunload', warnOnUnload)
  })

  async function leaveTo(target: RouteLocationRaw): Promise<void> {
    confirmed = true
    try {
      await router.push(target)
    } finally {
      confirmed = false
    }
  }

  async function leaveWithoutSaving(): Promise<void> {
    const target = leaveTarget.value
    leaveTarget.value = null
    if (target) await leaveTo(target)
  }

  return { leaveTarget, leaveTo, leaveWithoutSaving }
}
