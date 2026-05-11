import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export type MascotEyeOffset = {
  x: number
  y: number
}

/**
 * Tracks the cursor (or touch) position relative to the mascot's centre and
 * exposes a normalised offset (-1..1 on each axis) that can be applied to
 * the eye pupils via `transform`.
 *
 * Implementation notes (performance):
 *   - Throttled via `requestAnimationFrame` so we update at most once per frame.
 *   - Skips updates when `prefers-reduced-motion: reduce` is set.
 *   - Removes listeners + cancels rAF on unmount.
 */
export function useMascotEyes(
  containerRef: Ref<SVGElement | HTMLElement | null>,
  options?: { maxOffset?: number },
) {
  const maxOffset = options?.maxOffset ?? 2.4
  const offset = ref<MascotEyeOffset>({ x: 0, y: 0 })

  let frame = 0
  let pendingX = 0
  let pendingY = 0
  let hasPending = false
  let reduceMotion = false
  let motionQuery: MediaQueryList | null = null

  function handleMotionChange(event: MediaQueryListEvent) {
    reduceMotion = event.matches
    if (reduceMotion) {
      offset.value = { x: 0, y: 0 }
    }
  }

  function flush() {
    frame = 0
    if (!hasPending) return
    hasPending = false
    offset.value = { x: pendingX, y: pendingY }
  }

  function handleMove(clientX: number, clientY: number) {
    if (reduceMotion) return
    const el = containerRef.value
    if (!el) return
    const rect = el.getBoundingClientRect()
    if (rect.width === 0 || rect.height === 0) return
    const cx = rect.left + rect.width / 2
    const cy = rect.top + rect.height / 2
    const dx = (clientX - cx) / rect.width
    const dy = (clientY - cy) / rect.height
    // Clamp to [-1, 1] then scale by maxOffset
    const clampedX = Math.max(-1, Math.min(1, dx * 2))
    const clampedY = Math.max(-1, Math.min(1, dy * 2))
    pendingX = clampedX * maxOffset
    pendingY = clampedY * maxOffset
    hasPending = true
    if (!frame) {
      frame = requestAnimationFrame(flush)
    }
  }

  function onPointerMove(event: PointerEvent) {
    handleMove(event.clientX, event.clientY)
  }

  onMounted(() => {
    motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    reduceMotion = motionQuery.matches
    motionQuery.addEventListener('change', handleMotionChange)
    window.addEventListener('pointermove', onPointerMove, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('pointermove', onPointerMove)
    motionQuery?.removeEventListener('change', handleMotionChange)
    motionQuery = null
    if (frame) cancelAnimationFrame(frame)
  })

  return { offset }
}
