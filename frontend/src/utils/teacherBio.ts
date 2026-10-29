/**
 * Teacher bio rule of the sign-up form and the teacher profile page, the same as the backend RegisterTeacherRequest
 * and UpdateTeacherProfileRequest: required, 20 to 1000 characters after trimming.
 */
const TEACHER_BIO_MIN_LENGTH = 20
export const TEACHER_BIO_MAX_LENGTH = 1000

type TeacherBioIssue = 'required' | 'length'

/** What is wrong with a bio, or null when it meets the rule. */
export function teacherBioIssue(bio: string | null | undefined): TeacherBioIssue | null {
  const length = bio?.trim().length ?? 0
  if (length === 0) return 'required'
  return length < TEACHER_BIO_MIN_LENGTH || length > TEACHER_BIO_MAX_LENGTH ? 'length' : null
}
