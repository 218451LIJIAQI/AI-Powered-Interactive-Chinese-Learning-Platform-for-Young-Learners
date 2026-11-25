import { onMounted } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { useToast } from '@/composables/useToast'

/** A confirmation a classes page leaves for the page it opens next. */
interface ClassNotice {
  readonly message: string
  /** Route name this notice belongs to so it cannot leak onto an unrelated classes page. */
  readonly targetRoute: string
  /** The class just joined: the next page offers Undo, which leaves it again. */
  readonly joinedClassId?: number
  /** Title of the joined class so Undo errors can still name it before the list finishes loading. */
  readonly joinedClassTitle?: string
}

let pending: (ClassNotice & { sessionVersion: number }) | null = null

/** Leaves a confirmation for the next classes page ("… is in My classes."), for an action that ends on another page. */
export function queueClassNotice(notice: ClassNotice): void {
  pending = { ...notice, sessionVersion: getAuthSessionVersion() }
}

/** Discards any unconsumed class notice (e.g. if the navigation it was queued for fails or is cancelled). */
export function clearClassNotice(): void {
  pending = null
}

/**
 * Router transition hook: clears a queued notice when the navigation fails/aborts or lands on a route other than its
 * intended `targetRoute`, so abandoned transitions cannot leak a stale toast onto a later visit.
 */
export function reconcileClassNoticeNavigation(
  destinationName: string | symbol | null | undefined,
  failed: boolean,
): void {
  if (!pending) return
  if (failed || pending.targetRoute !== String(destinationName ?? '')) {
    pending = null
  }
}

/**
 * A page's toast that first shows what the previous page left for it, if anything; `showToast` is for its own actions.
 * `routeName` scopes notices to their intended destination; a page that can undo a join also passes `undoJoin`.
 */
export function useClassNotice(
  routeName: string,
  undoJoin?: { label: string; run: (classId: number, title?: string) => void },
) {
  const { toast, toastAction, showToast } = useToast()

  onMounted(() => {
    const notice = pending
    if (!notice) return
    pending = null
    if (notice.sessionVersion !== getAuthSessionVersion() || notice.targetRoute !== routeName) return
    const classId = notice.joinedClassId
    if (classId === undefined || !undoJoin) {
      showToast(notice.message)
      return
    }
    showToast(notice.message, {
      label: undoJoin.label,
      run: () => {
        if (notice.sessionVersion === getAuthSessionVersion()) undoJoin.run(classId, notice.joinedClassTitle)
      },
    })
  })

  return { toast, toastAction, showToast }
}
