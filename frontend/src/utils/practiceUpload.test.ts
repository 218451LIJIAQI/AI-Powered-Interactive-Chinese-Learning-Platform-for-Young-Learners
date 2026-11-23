import { describe, expect, it } from 'vitest'

import { recordingFileName } from './practiceUpload'

describe('recordingFileName', () => {
  it('stamps the recording with the local date and time', () => {
    expect(recordingFileName(new Date(2026, 4, 18, 3, 5))).toBe('pronunciation-20260518-0305.webm')
  })
})
