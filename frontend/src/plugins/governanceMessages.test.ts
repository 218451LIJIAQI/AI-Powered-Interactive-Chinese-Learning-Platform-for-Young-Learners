import { describe, expect, it } from 'vitest'

import { adminClassesMessages } from './adminClassesMessages'
import { classesMessages } from './classesMessages'
import { lessonEditorMessages } from './lessonEditorMessages'

// Key parity and compilation are checked for every bundle in messageBundles.test.ts.
describe('content review wording', () => {
  it('never tells teachers or admins that content is published directly', () => {
    const copy = [
      ...Object.values(lessonEditorMessages.en.status),
      ...Object.values(adminClassesMessages.en.review.approveMessage),
    ].join(' ')
    expect(copy).not.toMatch(/publish(es)? directly|goes live immediately/i)
  })

  it('says that students keep the published lesson while a revision is open', () => {
    expect(lessonEditorMessages.en.status.revisionSubmitted).toMatch(/published version/)
    expect(lessonEditorMessages.en.submit.revisionMessage).toMatch(/published version/)
  })

  it('uses the agreed unit and lesson terminology', () => {
    expect(adminClassesMessages['zh-CN'].review.typeLesson).toBe('课时')
    expect(classesMessages['zh-CN'].units).toContain('单元')
  })
})
