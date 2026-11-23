import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'
import type { XiaomeiMemoryNote, XiaomeiSummary } from '@/types/ai'

const api = vi.hoisted(() => ({
  xiaomeiSummary: vi.fn(),
  xiaomeiMemory: vi.fn(),
  forgetXiaomeiNote: vi.fn(),
  restoreXiaomeiNote: vi.fn(),
}))

vi.mock('@/api/ai', () => ({ aiHelperApi: api }))

import { useXiaomeiSummaryMemory } from './useXiaomeiSummaryMemory'

const NOTE_1: XiaomeiMemoryNote = { id: 1, text: 'Likes kopi o', planReason: null }
const NOTE_2: XiaomeiMemoryNote = { id: 2, text: 'Studies engineering', planReason: null }

function makeSummary(overrides: Partial<XiaomeiSummary> = {}): XiaomeiSummary {
  return {
    status: 'SUCCESS',
    goodbye: { chinese: '再见！', pinyin: 'Zàijiàn!', translation: 'Bye!' },
    feedback: 'Good effort!',
    newExpressions: [],
    wordsUsed: [],
    memory: [NOTE_1, NOTE_2],
    ...overrides,
  }
}

describe('useXiaomeiSummaryMemory', () => {
  beforeEach(() => {
    setAccessToken('student-session')
    Object.values(api).forEach((fn) => fn.mockReset())
    api.xiaomeiMemory.mockResolvedValue([NOTE_1, NOTE_2])
  })

  function createMemory(opts: { conversation?: () => number; endedOnScreen?: () => boolean } = {}) {
    const onUsed = vi.fn()
    const onToast = vi.fn()
    const state = useXiaomeiSummaryMemory({
      getConversation: opts.conversation ?? (() => 1),
      getScenario: () => 'CAFE',
      getTurns: () => [{ speaker: 'STUDENT', text: '你好' }],
      isEndedOnScreen: opts.endedOnScreen ?? (() => true),
      hasSummaryView: () => true,
      t: (key) => key,
      onUsed,
      onToast,
    })
    return { state, onUsed, onToast }
  }

  it('aborts summary error fallback, memory fetch, and onUsed when the auth session changes mid-summary', async () => {
    const pending = deferred<XiaomeiSummary>()
    api.xiaomeiSummary.mockReturnValueOnce(pending.promise)
    const { state, onUsed } = createMemory()

    const loading = state.loadSummary()
    setAccessToken(null)
    pending.reject(new Error('canceled'))
    await loading
    await flushPromises()

    expect(state.summaryError.value).toBeNull()
    expect(api.xiaomeiMemory).not.toHaveBeenCalled()
    expect(onUsed).not.toHaveBeenCalled()
  })

  it('serializes queued forget and restore calls, rolls back a failed forget, and routes restore errors by screen state', async () => {
    const firstForget = deferred<XiaomeiMemoryNote[]>()
    const secondForget = deferred<XiaomeiMemoryNote[]>()
    api.xiaomeiSummary.mockResolvedValueOnce(makeSummary())
    api.forgetXiaomeiNote.mockReturnValueOnce(firstForget.promise).mockReturnValueOnce(secondForget.promise)

    let endedOnScreen = true
    const { state, onToast } = createMemory({ endedOnScreen: () => endedOnScreen })
    await state.loadSummary()
    expect(state.memory.value).toEqual([NOTE_1, NOTE_2])

    const f1 = state.forgetNote(NOTE_1)
    const f2 = state.forgetNote(NOTE_2)
    await flushPromises()
    // Optimistically hidden while requests are queued; only the first request hits the server until it settles.
    expect(state.memory.value).toEqual([])
    expect(api.forgetXiaomeiNote).toHaveBeenCalledTimes(1)

    firstForget.resolve([NOTE_2])
    await f1
    expect(onToast).toHaveBeenCalledTimes(1)
    expect(api.forgetXiaomeiNote).toHaveBeenCalledTimes(2)

    secondForget.reject(new Error('offline'))
    await f2

    expect(state.memory.value).toEqual([NOTE_2])
    expect(state.memoryError.value).toBe('aiHelper.memoryForgetError')

    // Undo routes failure inline while the ended summary is on screen, and to a toast once off screen.
    const undo = onToast.mock.calls[0]![1] as { label: string; run: () => void }
    api.restoreXiaomeiNote.mockRejectedValueOnce(new Error('offline'))
    undo.run()
    await flushPromises()
    expect(api.restoreXiaomeiNote).toHaveBeenCalledWith(NOTE_1.id)
    expect(state.memoryError.value).toBe('aiHelper.memoryRestoreError')
    expect(onToast).toHaveBeenCalledTimes(1)

    endedOnScreen = false
    api.restoreXiaomeiNote.mockRejectedValueOnce(new Error('offline'))
    undo.run()
    await flushPromises()
    expect(onToast).toHaveBeenLastCalledWith('aiHelper.memoryRestoreError')

    api.restoreXiaomeiNote.mockResolvedValueOnce([NOTE_1, NOTE_2])
    undo.run()
    await flushPromises()
    expect(state.memory.value).toEqual([NOTE_1, NOTE_2])
  })

  it('discards a summary response if the conversation restarts before it settles and keeps the latest error', async () => {
    let conversation = 1
    const earlier = deferred<XiaomeiSummary>()
    api.xiaomeiSummary.mockReturnValueOnce(earlier.promise)
    const { state, onUsed } = createMemory({ conversation: () => conversation })

    const loading = state.loadSummary()
    conversation = 2
    state.reset()
    api.xiaomeiSummary.mockRejectedValueOnce(new Error('offline'))
    await state.loadSummary()

    earlier.resolve(makeSummary())
    await loading

    expect(state.summary.value).toBeNull()
    expect(state.summaryError.value).toBe('aiHelper.summaryError')
    expect(onUsed).toHaveBeenCalledTimes(2)
  })

  it('stops a queued Forget when the signed-in account changes', async () => {
    const first = deferred<XiaomeiMemoryNote[]>()
    api.xiaomeiSummary.mockResolvedValueOnce(makeSummary())
    api.forgetXiaomeiNote.mockReturnValueOnce(first.promise)
    const { state, onToast } = createMemory()
    await state.loadSummary()

    const f1 = state.forgetNote(NOTE_1)
    const f2 = state.forgetNote(NOTE_2)
    await flushPromises()
    expect(api.forgetXiaomeiNote).toHaveBeenCalledTimes(1)

    setAccessToken(null)
    first.resolve([NOTE_2])
    await Promise.all([f1, f2])

    expect(api.forgetXiaomeiNote).toHaveBeenCalledTimes(1)
    expect(onToast).not.toHaveBeenCalled()
  })

  it('keeps the retried summary’s memory when an earlier fallback notes read returns late', async () => {
    const earlierNotes = deferred<XiaomeiMemoryNote[]>()
    api.xiaomeiSummary.mockRejectedValueOnce(new Error('offline'))
    api.xiaomeiMemory.mockReturnValueOnce(earlierNotes.promise)
    const { state } = createMemory()
    await state.loadSummary()

    api.xiaomeiSummary.mockResolvedValueOnce(makeSummary({ memory: [NOTE_2] }))
    await state.loadSummary()
    earlierNotes.resolve([NOTE_1])
    await flushPromises()

    expect(state.memory.value).toEqual([NOTE_2])
  })

  it('does not restore a forgotten note from an in-flight summary or lose retried facts when Forget returns stale', async () => {
    api.xiaomeiSummary.mockRejectedValueOnce(new Error('offline'))
    api.xiaomeiMemory.mockResolvedValueOnce([NOTE_2]).mockResolvedValueOnce([])
    api.forgetXiaomeiNote.mockResolvedValueOnce([])
    const { state } = createMemory()
    await state.loadSummary()

    const retry = deferred<XiaomeiSummary>()
    api.xiaomeiSummary.mockReturnValueOnce(retry.promise)
    const retryPromise = state.loadSummary()
    await state.forgetNote(NOTE_2)
    retry.resolve(makeSummary({ memory: [NOTE_2] }))
    await retryPromise
    await flushPromises()
    expect(state.memory.value).toEqual([])

    // And if a Forget already in flight returns an older list after a retried summary brought in NOTE_1, keep NOTE_1.
    const forgotten = deferred<XiaomeiMemoryNote[]>()
    api.xiaomeiSummary
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(makeSummary({ memory: [NOTE_2, NOTE_1] }))
    api.xiaomeiMemory.mockResolvedValueOnce([NOTE_2]).mockResolvedValueOnce([NOTE_1])
    api.forgetXiaomeiNote.mockReturnValueOnce(forgotten.promise)
    state.reset()
    await state.loadSummary()
    const forgetPromise = state.forgetNote(NOTE_2)
    await state.loadSummary()
    forgotten.resolve([])
    await forgetPromise
    await flushPromises()

    expect(state.memory.value).toEqual([NOTE_1])
  })

  it('keeps an earlier conversation’s confirmed deletion out of a later summary', async () => {
    let conversation = 1
    const forgotten = deferred<XiaomeiMemoryNote[]>()
    const later = deferred<XiaomeiSummary>()
    api.forgetXiaomeiNote.mockReturnValueOnce(forgotten.promise)
    api.xiaomeiSummary.mockResolvedValueOnce(makeSummary({ memory: [NOTE_2] })).mockReturnValueOnce(later.promise)
    api.xiaomeiMemory.mockResolvedValueOnce([NOTE_1])
    const { state } = createMemory({ conversation: () => conversation })

    await state.loadSummary()
    const forgetPromise = state.forgetNote(NOTE_2)
    conversation = 2
    state.reset()
    const laterPromise = state.loadSummary()

    forgotten.resolve([])
    await forgetPromise
    later.resolve(makeSummary({ memory: [NOTE_2, NOTE_1] }))
    await laterPromise
    await flushPromises()

    expect(state.memory.value).toEqual([NOTE_1])
  })
})
