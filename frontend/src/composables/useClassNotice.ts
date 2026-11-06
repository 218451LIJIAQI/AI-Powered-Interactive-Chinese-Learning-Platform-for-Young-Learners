import { onMounted } from 'vue'

import { useToast } from '@/composables/useToast'

let pending: string | null = null

/** Leaves a confirmation for the next classes page ("… is in My classes."), for an action that ends on another page. */
export function leaveClassNotice(message: string): void {
  pending = message
}

/** A page's toast that first shows what the previous page left for it, if anything. */
export function useClassNotice() {
  const { toast, showToast } = useToast()

  onMounted(() => {
    if (pending) showToast(pending)
    pending = null
  })

  return { toast, showToast }
}
