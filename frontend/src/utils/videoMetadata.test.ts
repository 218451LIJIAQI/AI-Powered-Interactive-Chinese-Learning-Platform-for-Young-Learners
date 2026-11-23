import { describe, expect, it, vi } from 'vitest'

import { probeVideoDuration } from './videoMetadata'

function stubObjectUrls() {
  const createObjectURL = vi.fn(() => 'blob:video-probe')
  const revokeObjectURL = vi.fn()
  vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
  return { createObjectURL, revokeObjectURL }
}

describe('probeVideoDuration', () => {
  it('resolves rounded duration on loadedmetadata and revokes the Blob URL even if load() throws', async () => {
    const { revokeObjectURL } = stubObjectUrls()
    const video = document.createElement('video')
    Object.defineProperty(video, 'duration', { value: 42.6, configurable: true })
    vi.spyOn(video, 'load').mockImplementation(() => {
      throw new Error('not implemented')
    })
    vi.spyOn(document, 'createElement').mockReturnValue(video)

    const pending = probeVideoDuration(new File(['mp4'], 'lesson.mp4', { type: 'video/mp4' }))
    video.onloadedmetadata?.(new Event('loadedmetadata'))

    await expect(pending).resolves.toBe(43)
    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:video-probe')
  })

  it.each([Number.NaN, Number.POSITIVE_INFINITY, -1, 2_147_483_648])(
    'returns null for invalid duration %s',
    async (invalid) => {
      const { revokeObjectURL } = stubObjectUrls()
      const video = document.createElement('video')
      Object.defineProperty(video, 'duration', { value: invalid, configurable: true })
      vi.spyOn(video, 'load').mockImplementation(() => undefined)
      vi.spyOn(document, 'createElement').mockReturnValue(video)

      const pending = probeVideoDuration(new File(['mp4'], 'lesson.mp4'))
      video.onloadedmetadata?.(new Event('loadedmetadata'))

      await expect(pending).resolves.toBeNull()
      expect(revokeObjectURL).toHaveBeenCalledTimes(1)
    },
  )

  it('returns null on video error, cancels the timeout, and revokes the Blob URL once', async () => {
    vi.useFakeTimers()
    const { revokeObjectURL } = stubObjectUrls()
    const video = document.createElement('video')
    vi.spyOn(video, 'load').mockImplementation(() => undefined)
    vi.spyOn(document, 'createElement').mockReturnValue(video)

    const pending = probeVideoDuration(new File(['mp4'], 'lesson.mp4'), 200)
    expect(video.onerror).toBeTypeOf('function')
    video.onerror!(new Event('error'))
    vi.advanceTimersByTime(200)

    await expect(pending).resolves.toBeNull()
    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
  })

  it('returns null on timeout and ignores a late error callback without revoking twice', async () => {
    vi.useFakeTimers()
    const { revokeObjectURL } = stubObjectUrls()
    const video = document.createElement('video')
    vi.spyOn(video, 'load').mockImplementation(() => undefined)
    vi.spyOn(document, 'createElement').mockReturnValue(video)

    const pending = probeVideoDuration(new File(['mp4'], 'lesson.mp4'), 200)
    const onError = video.onerror
    expect(onError).toBeTypeOf('function')
    vi.advanceTimersByTime(200)
    onError!(new Event('error'))

    await expect(pending).resolves.toBeNull()
    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
  })

  it('returns null if URL.createObjectURL throws', async () => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => {
        throw new Error('unsupported')
      }),
      revokeObjectURL: vi.fn(),
    })

    await expect(probeVideoDuration(new File(['mp4'], 'lesson.mp4'))).resolves.toBeNull()
  })
})
