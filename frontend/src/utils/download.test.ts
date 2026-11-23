import { afterEach, describe, expect, it, vi } from 'vitest'

import { saveBlobDownload } from './download'

function installObjectUrlMocks() {
  const createObjectURL = vi.fn(() => 'blob:test-download-url')
  const revokeObjectURL = vi.fn()
  vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
  return { createObjectURL, revokeObjectURL }
}

/** The name the browser is asked to save the download as, read from the link that is clicked. */
function savedName(fileName: string, fallbackName: string): string {
  vi.useFakeTimers()
  installObjectUrlMocks()
  let name = ''
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    name = this.download
  })
  saveBlobDownload({ blob: new Blob(['hello']), fileName }, fallbackName)
  return name
}

afterEach(() => {
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('download file name', () => {
  it('uses a clean provided filename before the fallback', () => {
    expect(savedName(' lesson-notes.pdf ', 'fallback.pdf')).toBe('lesson-notes.pdf')
  })

  it('removes path separators, reserved filename characters, controls, and bidi format controls', () => {
    expect(savedName('../notes\u0000\u202ereport:1?.pdf', 'fallback.pdf')).toBe('_notes_report_1_.pdf')
  })

  it('strips leading dots (even separated by spaces or invisible characters) and C1 controls', () => {
    expect(savedName('.env', 'fallback.txt')).toBe('env')
    expect(savedName('. .env', 'fallback.txt')).toBe('env')
    expect(savedName('\u200b .env', 'fallback.txt')).toBe('env')
    expect(savedName('note\u0085.txt', 'fallback.txt')).toBe('note_.txt')
    expect(savedName('..', 'fallback.txt')).toBe('fallback.txt')
    expect(savedName('..', '..')).toBe('download')
    expect(savedName('', ' .hidden-fallback ')).toBe('hidden-fallback')
  })

  it('removes trailing dots/spaces and rejects Windows reserved device names', () => {
    expect(savedName('lesson-notes. ', 'fallback.txt')).toBe('lesson-notes')
    expect(savedName('CON.txt', 'fallback.txt')).toBe('fallback.txt')
    expect(savedName('lpt1', 'fallback.txt')).toBe('fallback.txt')
    expect(savedName('CON.txt', 'lpt1')).toBe('download')
  })

  it('limits very long names while preserving a short extension', () => {
    const fileName = `${'a'.repeat(240)}.pdf`
    const sanitized = savedName(fileName, 'fallback.txt')

    expect(sanitized).toHaveLength(180)
    expect(sanitized.endsWith('.pdf')).toBe(true)
  })

  it('falls back to a generic download name when both inputs are blank', () => {
    expect(savedName('   ', '   ')).toBe('download')
  })
})

describe('saveBlobDownload', () => {
  it('clicks a hidden download link and revokes the object URL after the browser has started the save', () => {
    vi.useFakeTimers()
    const { createObjectURL, revokeObjectURL } = installObjectUrlMocks()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const blob = new Blob(['hello'], { type: 'text/plain' })

    saveBlobDownload({ blob, fileName: '../lesson?.txt' }, 'fallback.txt')

    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(click).toHaveBeenCalledTimes(1)
    expect(document.body.querySelector('a')).toBeNull()

    vi.advanceTimersByTime(1000)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test-download-url')
  })

  it('cleans up the temporary link and object URL even if the browser click throws', () => {
    vi.useFakeTimers()
    const { revokeObjectURL } = installObjectUrlMocks()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(() => saveBlobDownload({ blob: new Blob(['hello']) }, 'fallback.txt')).toThrow('blocked')
    expect(document.body.querySelector('a')).toBeNull()

    vi.advanceTimersByTime(1000)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test-download-url')
  })
})
