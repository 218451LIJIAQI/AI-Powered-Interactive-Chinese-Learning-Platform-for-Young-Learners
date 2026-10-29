import { describe, expect, it } from 'vitest'

import { TEACHER_BIO_MAX_LENGTH, teacherBioIssue } from './teacherBio'

describe('teacher bio rule (sign-up and profile, same as the server)', () => {
  it('requires a bio', () => {
    expect(teacherBioIssue('')).toBe('required')
    expect(teacherBioIssue('   ')).toBe('required')
    expect(teacherBioIssue(null)).toBe('required')
    expect(teacherBioIssue(undefined)).toBe('required')
  })

  it('accepts 20 to 1000 characters after trimming', () => {
    expect(teacherBioIssue(`  ${'a'.repeat(19)}  `)).toBe('length')
    expect(teacherBioIssue(`  ${'a'.repeat(20)}  `)).toBeNull()
    expect(teacherBioIssue('a'.repeat(TEACHER_BIO_MAX_LENGTH))).toBeNull()
    expect(teacherBioIssue('a'.repeat(TEACHER_BIO_MAX_LENGTH + 1))).toBe('length')
  })
})
