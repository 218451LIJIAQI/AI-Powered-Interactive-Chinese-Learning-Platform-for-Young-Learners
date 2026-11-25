import { onBeforeUnmount, watch, type WatchSource } from 'vue'

interface LessonPanelGuardOptions {
  lessonId: () => number
  saving: WatchSource<boolean>
  onSaving: (saving: boolean) => void
  onReset: () => void
  onCleanup?: () => void
}

/**
 * Coordinates lesson-switch cancellation and synchronous saving notifications for lesson editor tab panels so
 * in-flight writes from a previous lesson or unmounted panel never mutate state or leave parent tab guards locked.
 */
export function useLessonPanelGuard(options: LessonPanelGuardOptions) {
  let version = 0
  let wasSaving = false

  watch(
    options.saving,
    (active) => {
      wasSaving = active
      options.onSaving(active)
    },
    { flush: 'sync' },
  )

  watch(
    options.lessonId,
    () => {
      version += 1
      options.onCleanup?.()
      options.onReset()
    },
    { flush: 'sync' },
  )

  onBeforeUnmount(() => {
    version += 1
    options.onCleanup?.()
    if (wasSaving) options.onSaving(false)
  })

  async function run(
    work: (isCurrent: () => boolean) => Promise<void>,
    hooks?: {
      onError?: (reason: unknown) => void
      onFinally?: () => void
    },
  ): Promise<boolean> {
    const captured = version
    const isCurrent = () => captured === version
    let succeeded = false
    try {
      await work(isCurrent)
      succeeded = isCurrent()
    } catch (reason) {
      if (isCurrent()) hooks?.onError?.(reason)
    } finally {
      if (isCurrent()) hooks?.onFinally?.()
    }
    return succeeded
  }

  return {
    capture: () => version,
    isCurrent: (captured: number) => captured === version,
    run,
  }
}
