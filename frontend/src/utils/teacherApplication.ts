/**
 * The teacher's own application (Teaching profile, applicant dashboard, Locked page): the documents in display order,
 * their status labels (namespace `verification`) and whether the application can be sent.
 */
import type { PillTone } from '@/components/common/StatusPill.vue'
import type { TeacherDocument, TeacherDocumentStatus, TeacherDocumentType } from '@/types/ops'

/** The teaching certificate and the identity document are required; the degree certificate is optional. */
export const APPLICATION_DOCUMENTS: readonly { type: TeacherDocumentType; required: boolean }[] = [
  { type: 'CERTIFICATE', required: true },
  { type: 'IDENTITY', required: true },
  { type: 'QUALIFICATION', required: false },
]

const STATUS_KEYS: Record<TeacherDocumentStatus, string> = {
  READY: 'verification.statusReady',
  IN_REVIEW: 'verification.statusInReview',
  VERIFIED: 'verification.statusVerified',
  SENT: 'verification.statusSent',
}

const STATUS_TONES: Record<TeacherDocumentStatus, PillTone> = {
  READY: 'info',
  IN_REVIEW: 'info',
  VERIFIED: 'success',
  SENT: 'neutral',
}

export function documentStatusKey(status: TeacherDocumentStatus): string {
  return STATUS_KEYS[status]
}

export function documentStatusTone(status: TeacherDocumentStatus): PillTone {
  return STATUS_TONES[status]
}

export function documentOfType(
  documents: readonly TeacherDocument[],
  type: TeacherDocumentType,
): TeacherDocument | undefined {
  return documents.find((document) => document.documentType === type)
}

export function hasRequiredDocuments(documents: readonly TeacherDocument[]): boolean {
  return APPLICATION_DOCUMENTS.every(({ type, required }) => !required || Boolean(documentOfType(documents, type)))
}

/** When the newest current document was uploaded (null without documents). */
export function latestUpload(documents: readonly TeacherDocument[]): string | null {
  return documents.reduce<string | null>(
    (latest, document) => (!latest || document.uploadedAt > latest ? document.uploadedAt : latest),
    null,
  )
}
