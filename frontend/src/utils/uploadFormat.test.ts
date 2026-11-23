import { describe, expect, it } from 'vitest'

import { uploadAccept, uploadFileIssue } from './uploadFormat'

function file(name: string, type: string, size = 1024): Pick<File, 'name' | 'size' | 'type'> {
  return { name, type, size }
}

describe('uploadFormat', () => {
  it('builds accept lists containing extensions and media types for every upload kind', () => {
    expect(uploadAccept('audio')).toContain('.webm')
    expect(uploadAccept('audio')).toContain('audio/mpeg')
    expect(uploadAccept('image')).toContain('.png')
    expect(uploadAccept('image')).toContain('image/jpeg')
    expect(uploadAccept('video')).toContain('.mp4')
    expect(uploadAccept('video')).toContain('video/quicktime')
  })

  it('accepts the recordings, photos, and lesson videos the server accepts', () => {
    expect(uploadFileIssue(file('a.webm', 'audio/webm'), 'audio')).toBeNull()
    expect(uploadFileIssue(file('a.mp3', 'audio/mpeg'), 'audio')).toBeNull()
    expect(uploadFileIssue(file('a.png', 'image/png'), 'image')).toBeNull()
    expect(uploadFileIssue(file('lesson.mp4', 'video/mp4'), 'video')).toBeNull()
    expect(uploadFileIssue(file('lesson.mov', 'video/quicktime'), 'video')).toBeNull()
  })

  it('checks type and empty files even when no size limit is supplied', () => {
    expect(uploadFileIssue(file('a.png', 'image/png'), 'audio')).toBe('fileType')
    expect(uploadFileIssue(file('a.gif', 'image/gif'), 'image')).toBe('fileType')
    expect(uploadFileIssue(file('a.avi', 'video/x-msvideo'), 'video')).toBe('fileType')
    expect(uploadFileIssue(file('a.webm', 'audio/webm', 0), 'audio')).toBe('fileEmpty')
    expect(uploadFileIssue(file('a.wav', 'audio/wav', 12 * 1024 * 1024), 'audio')).toBeNull()
  })

  it.each([
    ['audio', 'a.wav', 'audio/wav', 1024],
    ['image', 'a.png', 'image/png', 20 * 1024 * 1024],
    ['video', 'clip.mp4', 'video/mp4', 200 * 1024 * 1024],
  ] as const)('accepts exactly the configured %s limit and refuses one byte more', (kind, name, type, maxBytes) => {
    expect(uploadFileIssue(file(name, type, maxBytes), kind, maxBytes)).toBeNull()
    expect(uploadFileIssue(file(name, type, maxBytes + 1), kind, maxBytes)).toBe('fileTooLarge')
  })

  it('requires both an allowed content type and an allowed extension, rejecting dot-only stems', () => {
    expect(uploadFileIssue(file('take.WEBM', ''), 'audio')).toBe('fileType')
    expect(uploadFileIssue(file('take.m4a', ''), 'audio')).toBe('fileType')
    expect(uploadFileIssue(file('take.webm', 'audio/webm;codecs=opus'), 'audio')).toBeNull()
    expect(uploadFileIssue(file('take.webm', 'video/webm'), 'audio')).toBeNull()
    expect(uploadFileIssue(file('take.ogg', 'audio/webm'), 'audio')).toBe('fileType')
    expect(uploadFileIssue(file('take.wav', 'audio/mpeg'), 'audio')).toBe('fileType')
    expect(uploadFileIssue(file('note.jpg', 'image/png'), 'image')).toBe('fileType')
    expect(uploadFileIssue(file('note.jpg', 'image/jpg'), 'image')).toBe('fileType')
    expect(uploadFileIssue(file('note.jpg', 'image/jpeg'), 'image')).toBeNull()
    expect(uploadFileIssue(file('..mp4', 'video/mp4'), 'video')).toBe('fileType')
    expect(uploadFileIssue(file('. ..mp4', 'video/mp4'), 'video')).toBe('fileType')
    expect(uploadFileIssue(file('   .png', 'image/png'), 'image')).toBe('fileType')
  })
})
