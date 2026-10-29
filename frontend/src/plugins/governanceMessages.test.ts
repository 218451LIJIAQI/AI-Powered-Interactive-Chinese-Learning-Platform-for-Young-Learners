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
    expect(Object.keys(classesMessages.en.status)).not.toContain('APPROVED')
  })

  it('says that students keep the published lesson while a revision is open', () => {
    expect(lessonEditorMessages.en.status.revisionSubmitted).toMatch(/published version/)
    expect(lessonEditorMessages.en.submit.revisionMessage).toMatch(/published version/)
  })

  it('uses the agreed stage, unit and lesson terminology', () => {
    expect(classesMessages.en.stage.SCAFFOLDED).toBe('Supported')
    expect(classesMessages.en.stage.INDEPENDENT).toBe('Independent')
    expect(adminClassesMessages['zh-CN'].review.typeLesson).toBe('课时')
    expect(classesMessages['zh-CN'].units).toContain('单元')
  })
})
