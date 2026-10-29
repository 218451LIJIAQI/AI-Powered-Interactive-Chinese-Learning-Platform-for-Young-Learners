import { onMounted, onUnmounted, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { useI18n } from 'vue-i18n'

import { withSetup } from './helpers'

describe('withSetup', () => {
  it('returns the composable result from a mounted component and runs its lifecycle hooks', () => {
    const events: string[] = []
    const { result, wrapper } = withSetup(() => {
      onMounted(() => events.push('mounted'))
      onUnmounted(() => events.push('unmounted'))
      return ref(7)
    })

    expect(result.value).toBe(7)
    expect(events).toEqual(['mounted'])

    wrapper.unmount()
    expect(events).toEqual(['mounted', 'unmounted'])
  })

  it('provides the app plugins installed by the test setup, such as i18n', () => {
    const { result } = withSetup(() => useI18n({ useScope: 'global' }))

    expect(result.locale.value).toBe('en')
  })
})
