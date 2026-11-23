import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const saveBlobDownload = vi.hoisted(() => vi.fn())
vi.mock('@/utils/download', () => ({ saveBlobDownload }))

import { canPreviewFile, openFileDownload } from './opsFiles'

describe('canPreviewFile', () => {
  it.each(['application/pdf', 'image/png', 'image/jpeg', 'application/pdf; charset=binary'])(
    'lets %s open in a preview tab',
    (type) => {
      expect(canPreviewFile(new Blob(['file'], { type }))).toBe(true)
    },
  )

  it.each([
    'text/html',
    'image/svg+xml',
    'application/msword',
    'application/pdfx',
    'application/pdf+xml',
    'image/jpeg2000',
    'image/png-extra',
    '',
  ])('keeps "%s" for download only', (type) => {
    expect(canPreviewFile(new Blob(['file'], { type }))).toBe(false)
  })
})

describe('openFileDownload', () => {
  const createObjectURL = vi.fn(() => 'blob:preview')
  const revokeObjectURL = vi.fn()
  let tab: { opener: unknown; location: { href: string }; close: ReturnType<typeof vi.fn> }

  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    tab = { opener: {}, location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
  })

  afterEach(() => vi.useRealTimers())

  it('opens the tab within the click and releases the preview URL after its loading allowance', async () => {
    const download = { blob: new Blob(['photo'], { type: 'image/png' }), fileName: null }
    const load = vi.fn(async () => download)
    await openFileDownload(load, 'screenshot')

    expect(window.open).toHaveBeenCalledWith('', '_blank')
    expect(vi.mocked(window.open).mock.invocationCallOrder[0]).toBeLessThan(load.mock.invocationCallOrder[0])
    expect(tab.opener).toBeNull()
    expect(tab.location.href).toBe('blob:preview')
    expect(saveBlobDownload).not.toHaveBeenCalled()
    expect(revokeObjectURL).not.toHaveBeenCalled()
    vi.advanceTimersByTime(60_000)
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:preview')
  })

  it('also releases an allocated URL if navigating the preview tab fails', async () => {
    Object.defineProperty(tab.location, 'href', {
      set() {
        throw new Error('Preview navigation rejected')
      },
    })
    await expect(
      openFileDownload(
        async () => ({ blob: new Blob(['pdf'], { type: 'application/pdf' }), fileName: null }),
        'document',
      ),
    ).rejects.toThrow('Preview navigation rejected')

    expect(tab.close).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(60_000)
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:preview')
  })

  it('closes the blank tab and propagates a failed authorised download without allocating a URL', async () => {
    await expect(
      openFileDownload(async () => {
        throw new Error('download failed')
      }, 'document'),
    ).rejects.toThrow('download failed')
    expect(tab.close).toHaveBeenCalledOnce()
    expect(createObjectURL).not.toHaveBeenCalled()
    expect(saveBlobDownload).not.toHaveBeenCalled()
  })

  it.each([true, false])('saves a file when a preview is unavailable, popup blocked=%s', async (blocked) => {
    if (blocked) vi.mocked(window.open).mockReturnValue(null)
    const download = {
      blob: new Blob(['file'], { type: blocked ? 'application/pdf' : 'text/plain' }),
      fileName: 'name',
    }
    await openFileDownload(async () => download, 'fallback')
    expect(saveBlobDownload).toHaveBeenCalledExactlyOnceWith(download, 'fallback')
    expect(createObjectURL).not.toHaveBeenCalled()
    expect(tab.close).toHaveBeenCalledTimes(blocked ? 0 : 1)
  })
})
