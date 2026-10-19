import { describe, expect, it, vi } from 'vitest'

import { studentPracticeSubmissions } from '@/demo/data'

import {
  buildPronunciationFormData,
  buildWritingFormData,
  detectRecorderSupport,
  formatFileSize,
  formatRecordingTime,
  isAcceptedUploadType,
  MAX_PRACTICE_UPLOAD_BYTES,
  pagePracticeSubmissions,
  parseLessonId,
  pickRecorderMimeType,
  recordingFileName,
  validateUpload,
} from './practiceUpload'

function file(name: string, type: string, size = 1024): File {
  const value = new File(['x'], name, { type })
  Object.defineProperty(value, 'size', { value: size })
  return value
}

describe('validateUpload', () => {
  it('requires a file and a target', () => {
    expect(validateUpload({ kind: 'audio', file: null, target: '  ' })).toEqual([
      { field: 'file', code: 'fileRequired' },
      { field: 'target', code: 'targetRequired' },
    ])
  })

  it('accepts server-supported audio and image files', () => {
    expect(validateUpload({ kind: 'audio', file: file('a.webm', 'audio/webm'), target: '你好' })).toEqual([])
    expect(validateUpload({ kind: 'audio', file: file('a.mp3', 'audio/mpeg'), target: '你好' })).toEqual([])
    expect(validateUpload({ kind: 'image', file: file('a.png', 'image/png'), target: '好' })).toEqual([])
  })

  it('rejects the wrong type, empty and oversized files', () => {
    expect(validateUpload({ kind: 'audio', file: file('a.png', 'image/png'), target: '你好' })).toEqual([
      { field: 'file', code: 'fileType' },
    ])
    expect(validateUpload({ kind: 'image', file: file('a.gif', 'image/gif'), target: '好' })[0]?.code).toBe('fileType')
    expect(validateUpload({ kind: 'audio', file: file('a.webm', 'audio/webm', 0), target: '你好' })[0]?.code).toBe(
      'fileEmpty',
    )
    expect(
      validateUpload({
        kind: 'audio',
        file: file('a.webm', 'audio/webm', MAX_PRACTICE_UPLOAD_BYTES + 1),
        target: '你',
      })[0]?.code,
    ).toBe('fileTooLarge')
  })

  it('limits the target text to 200 characters and checks the lesson id', () => {
    const issues = validateUpload({
      kind: 'audio',
      file: file('a.wav', 'audio/wav'),
      target: '你'.repeat(201),
      lessonId: 'abc',
    })
    expect(issues).toEqual([
      { field: 'target', code: 'targetTooLong' },
      { field: 'lesson', code: 'lessonInvalid' },
    ])
    expect(validateUpload({ kind: 'audio', file: file('a.wav', 'audio/wav'), target: '你', lessonId: '' })).toEqual([])
  })
})

describe('isAcceptedUploadType / parseLessonId', () => {
  it('falls back to the extension when the browser gives no type', () => {
    expect(isAcceptedUploadType({ name: 'take.WEBM', type: '' }, 'audio')).toBe(true)
    expect(isAcceptedUploadType({ name: 'take.m4a', type: '' }, 'audio')).toBe(false)
    expect(isAcceptedUploadType({ name: 'take.webm', type: 'audio/webm;codecs=opus' }, 'audio')).toBe(true)
  })

  it('parses positive integer lesson ids only', () => {
    expect(parseLessonId('12')).toBe(12)
    expect(parseLessonId(7)).toBe(7)
    expect(parseLessonId('0')).toBeNull()
    expect(parseLessonId('1.5')).toBeNull()
    expect(parseLessonId('')).toBeNull()
    expect(parseLessonId(null)).toBeNull()
  })
})

describe('form data builders', () => {
  it('builds the pronunciation multipart body with optional fields only when present', () => {
    const recording = file('take.webm', 'audio/webm')
    const formData = buildPronunciationFormData({
      file: recording,
      targetText: ' 你好 ',
      pinyin: ' nǐ hǎo ',
      tone: '',
      languagePreference: 'ms',
      lessonId: '10',
    })
    expect(formData.get('targetText')).toBe('你好')
    expect(formData.get('pinyin')).toBe('nǐ hǎo')
    expect(formData.has('tone')).toBe(false)
    expect(formData.get('languagePreference')).toBe('ms')
    expect(formData.get('lessonId')).toBe('10')
    expect((formData.get('file') as File).name).toBe('take.webm')
  })

  it('omits the lesson when none is chosen', () => {
    const formData = buildPronunciationFormData({
      file: file('take.webm', 'audio/webm'),
      targetText: '你好',
      languagePreference: 'en',
      lessonId: null,
    })
    expect(formData.has('lessonId')).toBe(false)
    expect(formData.has('pinyin')).toBe(false)
  })

  it('builds the writing multipart body', () => {
    const formData = buildWritingFormData({
      file: file('hao.png', 'image/png'),
      targetCharacter: ' 好 ',
      languagePreference: 'zh-CN',
      lessonId: 12,
    })
    expect(formData.get('targetCharacter')).toBe('好')
    expect(formData.get('languagePreference')).toBe('zh-CN')
    expect(formData.get('lessonId')).toBe('12')
    expect((formData.get('file') as File).name).toBe('hao.png')
  })
})

describe('recorder feature detection', () => {
  it('picks the first server-accepted format the browser can record', () => {
    expect(pickRecorderMimeType((type) => type.startsWith('audio/webm'))).toBe('audio/webm;codecs=opus')
    expect(pickRecorderMimeType((type) => type === 'audio/webm')).toBe('audio/webm')
    expect(pickRecorderMimeType((type) => type === 'audio/mp4')).toBeNull()
    expect(pickRecorderMimeType(undefined)).toBeNull()
    expect(
      pickRecorderMimeType(() => {
        throw new Error('unsupported')
      }),
    ).toBeNull()
  })

  it('needs getUserMedia, MediaRecorder and a recordable format', () => {
    const getUserMedia = vi.fn()
    const isTypeSupported = (type: string) => type === 'audio/webm'
    expect(detectRecorderSupport({ mediaDevices: { getUserMedia }, MediaRecorder: { isTypeSupported } })).toBe(true)
    expect(detectRecorderSupport({ mediaDevices: undefined, MediaRecorder: { isTypeSupported } })).toBe(false)
    expect(detectRecorderSupport({ mediaDevices: {}, MediaRecorder: { isTypeSupported } })).toBe(false)
    expect(detectRecorderSupport({ mediaDevices: { getUserMedia }, MediaRecorder: null })).toBe(false)
    expect(
      detectRecorderSupport({ mediaDevices: { getUserMedia }, MediaRecorder: { isTypeSupported: () => false } }),
    ).toBe(false)
  })
})

describe('formatting helpers', () => {
  it('formats timer, size and file name', () => {
    expect(formatRecordingTime(7)).toBe('0:07')
    expect(formatRecordingTime(75)).toBe('1:15')
    expect(formatFileSize(48_213)).toBe('47 KB')
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2.0 MB')
    expect(recordingFileName('audio/webm', new Date(2026, 4, 18, 3, 5))).toBe('pronunciation-20260518-0305.webm')
  })
})

describe('pagePracticeSubmissions', () => {
  it('filters by type and pages newest first', () => {
    const audio = pagePracticeSubmissions(studentPracticeSubmissions, 'PRONUNCIATION_AUDIO', 0, 1)
    expect(audio.totalItems).toBe(2)
    expect(audio.totalPages).toBe(2)
    expect(audio.items.map((item) => item.id)).toEqual([1])

    const all = pagePracticeSubmissions(studentPracticeSubmissions, null, 5, 10)
    expect(all.page).toBe(0)
    expect(all.items.map((item) => item.id)).toEqual([1, 2, 3])
  })
})
