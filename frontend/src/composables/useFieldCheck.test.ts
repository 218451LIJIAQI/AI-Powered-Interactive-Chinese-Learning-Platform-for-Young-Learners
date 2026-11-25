import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'

import { useFieldCheck } from './useFieldCheck'

function required(value: string): string | null {
  if (!value.trim()) return 'Enter a reason.'
  return value.trim().length < 3 ? 'Write at least three letters.' : null
}

describe('useFieldCheck', () => {
  it('shows no error until the field is left or the form is sent', async () => {
    const value = ref('')
    const field = useFieldCheck(value, required)

    value.value = ' '
    await nextTick()
    expect(field.error.value).toBeNull()

    field.validate()
    expect(field.error.value).toBe('Enter a reason.')
  })

  it('checks again on every change while the error shows, and stops once it is fixed', async () => {
    const value = ref('')
    const field = useFieldCheck(value, required)
    expect(field.validate()).toBe(false)

    value.value = 'ab'
    await nextTick()
    expect(field.error.value).toBe('Write at least three letters.')

    value.value = 'abc'
    await nextTick()
    expect(field.error.value).toBeNull()

    // Fixed: a later change is not checked until the field is left or the form is sent again.
    value.value = ''
    await nextTick()
    expect(field.error.value).toBeNull()
    expect(field.validate()).toBe(false)
  })

  it('updates a displayed error when a reactive dependency such as locale changes', async () => {
    const value = ref('')
    const message = ref('Enter a reason before you deactivate this account.')
    const field = useFieldCheck(value, (text) => (text.trim() ? null : message.value))
    field.validate()
    expect(field.error.value).toBe('Enter a reason before you deactivate this account.')

    message.value = 'Sila masukkan sebab.'
    await nextTick()

    expect(field.error.value).toBe('Sila masukkan sebab.')
  })

  it('forgets the error on reset', () => {
    const value = ref('')
    const field = useFieldCheck(value, required)
    field.validate()

    field.reset()

    expect(field.error.value).toBeNull()
    expect(field.validate()).toBe(false)
  })
})
