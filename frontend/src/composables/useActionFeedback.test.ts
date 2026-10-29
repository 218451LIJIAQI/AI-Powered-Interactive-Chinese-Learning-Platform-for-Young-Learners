import { describe, expect, it } from 'vitest'

import { useActionFeedback } from './useActionFeedback'

describe('useActionFeedback', () => {
  it('runAction clears the old outcome and shows the success text the action resolves to', async () => {
    const feedback = useActionFeedback()
    feedback.showError('Old error')
    let errorWhileRunning: string | null = 'unset'

    const succeeded = await feedback.runAction('Save failed', async () => {
      errorWhileRunning = feedback.actionError.value
      return 'Saved'
    })

    expect(succeeded).toBe(true)
    expect(errorWhileRunning).toBeNull()
    expect(feedback.actionSuccess.value).toBe('Saved')
    expect(feedback.actionError.value).toBeNull()
  })

  it('runAction shows the server message of a failed request instead of throwing', async () => {
    const feedback = useActionFeedback()
    feedback.showSuccess('Old success')

    const succeeded = await feedback.runAction('Save failed', async () => {
      throw { response: { status: 400, data: { message: 'Title is required' } } }
    })

    expect(succeeded).toBe(false)
    expect(feedback.actionError.value).toBe('Title is required')
    expect(feedback.actionSuccess.value).toBeNull()
  })

  it('runAction falls back to the given text when the error has no readable message', async () => {
    const feedback = useActionFeedback()

    await feedback.runAction('Save failed', () => Promise.reject(new Error()))

    expect(feedback.actionError.value).toBe('Save failed')
  })
})
