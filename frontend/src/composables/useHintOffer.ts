import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

import { useHintSettings } from '@/composables/useHintSettings'

interface HintOfferOptions {
  /** The item on screen (a question, the matching board's next pair): a new item waits again. */
  readonly itemKey: Readonly<Ref<string | number | null>>
  /** A hint can be offered now: the item has one to give and is still waiting for an answer. */
  readonly open: Readonly<Ref<boolean>>
}

/**
 * When a SparkBot hint is offered, by the learner's hint setting (Account › AI hints): Automatic waits for their
 * level's delay without progress on the item (`poke` starts the wait again), "Only when I ask" offers at once, Off
 * never. A failed or pending setting offers nothing.
 */
export function useHintOffer(options: HintOfferOptions) {
  const { settings } = useHintSettings()
  const offered = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined

  function schedule(): void {
    clearTimeout(timer)
    offered.value = false
    const current = settings.value
    if (!current || current.mode === 'OFF' || !options.open.value) return
    if (current.mode === 'ON_REQUEST') {
      offered.value = true
      return
    }
    timer = setTimeout(() => {
      if (options.open.value) offered.value = true
    }, current.delaySeconds * 1000)
  }

  /** Progress on the item: the learner is not stuck, so an Automatic offer waits again. */
  function poke(): void {
    if (settings.value?.mode === 'AUTOMATIC' && !offered.value) schedule()
  }

  watch([options.itemKey, options.open, settings], schedule, { immediate: true })
  onBeforeUnmount(() => clearTimeout(timer))

  return { offered, poke }
}
