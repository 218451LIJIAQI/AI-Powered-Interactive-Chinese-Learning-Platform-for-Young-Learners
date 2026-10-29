import { describe, expect, it, vi } from 'vitest'

import {
  detectRecorderSupport,
  MAX_PRACTICE_UPLOAD_BYTES,
  practiceFileIssue,
  recordingFileName,
} from './practiceUpload'

function file(name: string, type: string, size = 1024): Pick<File, 'name' | 'size' | 'type'> {
  return { name, type, size }
}

describe('practiceFileIssue', () => {
  it('accepts the recordings and photos the server accepts', () => {
    expect(practiceFileIssue(file('a.webm', 'audio/webm'), 'audio')).toBeNull()
    expect(practiceFileIssue(file('a.mp3', 'audio/mpeg'), 'audio')).toBeNull()
    expect(practiceFileIssue(file('a.png', 'image/png'), 'image')).toBeNull()
  })

  it('names the wrong type, an empty file and a file over 10 MB', () => {
    expect(practiceFileIssue(file('a.png', 'image/png'), 'audio')).toBe('fileType')
    expect(practiceFileIssue(file('a.gif', 'image/gif'), 'image')).toBe('fileType')
    expect(practiceFileIssue(file('a.webm', 'audio/webm', 0), 'audio')).toBe('fileEmpty')
    expect(practiceFileIssue(file('a.wav', 'audio/wav', MAX_PRACTICE_UPLOAD_BYTES + 1), 'audio')).toBe('fileTooLarge')
  })

  it('requires both an allowed content type and an allowed extension, like the server', () => {
    expect(practiceFileIssue(file('take.WEBM', ''), 'audio')).toBe('fileType')
    expect(practiceFileIssue(file('take.m4a', ''), 'audio')).toBe('fileType')
    expect(practiceFileIssue(file('take.webm', 'audio/webm;codecs=opus'), 'audio')).toBeNull()
    expect(practiceFileIssue(file('take.webm', 'video/webm'), 'audio')).toBeNull()
    expect(practiceFileIssue(file('take.ogg', 'audio/webm'), 'audio')).toBe('fileType')
    expect(practiceFileIssue(file('note.jpg', 'image/jpg'), 'image')).toBe('fileType')
    expect(practiceFileIssue(file('note.jpg', 'image/jpeg'), 'image')).toBeNull()
  })
})

describe('recorder feature detection', () => {
  it('needs getUserMedia and a MediaRecorder that records WEBM/Opus', () => {
    const getUserMedia = vi.fn()
    const isTypeSupported = (type: string) => type === 'audio/webm;codecs=opus'
    expect(detectRecorderSupport({ mediaDevices: { getUserMedia }, MediaRecorder: { isTypeSupported } })).toBe(true)
    expect(detectRecorderSupport({ mediaDevices: undefined, MediaRecorder: { isTypeSupported } })).toBe(false)
    expect(detectRecorderSupport({ mediaDevices: {}, MediaRecorder: { isTypeSupported } })).toBe(false)
    expect(detectRecorderSupport({ mediaDevices: { getUserMedia }, MediaRecorder: null })).toBe(false)
    expect(
      detectRecorderSupport({ mediaDevices: { getUserMedia }, MediaRecorder: { isTypeSupported: () => false } }),
    ).toBe(false)
  })
})

describe('recordingFileName', () => {
  it('stamps the recording with the local date and time', () => {
    expect(recordingFileName(new Date(2026, 4, 18, 3, 5))).toBe('pronunciation-20260518-0305.webm')
  })
})
