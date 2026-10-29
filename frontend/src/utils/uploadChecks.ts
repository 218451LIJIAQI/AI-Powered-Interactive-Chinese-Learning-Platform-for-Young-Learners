/** Client-side checks of the ops uploads; the server repeats them (type, content signature and size). */

/** Support screenshots and verification documents may be up to 5 MB. */
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024

type UploadIssue = 'type' | 'size'

const IMAGE_TYPES = new Set(['image/png', 'image/jpeg'])
const IMAGE_EXTENSIONS = /\.(png|jpe?g)$/i
const DOCUMENT_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg'])
const DOCUMENT_EXTENSIONS = /\.(pdf|png|jpe?g)$/i

function issueOf(file: File, types: ReadonlySet<string>, extensions: RegExp): UploadIssue | null {
  const typeAllowed = file.type ? types.has(file.type) : extensions.test(file.name)
  if (!typeAllowed || !extensions.test(file.name)) return 'type'
  return file.size > MAX_UPLOAD_BYTES ? 'size' : null
}

/** A support screenshot: PNG or JPG, up to 5 MB. */
export function screenshotIssue(file: File): UploadIssue | null {
  return issueOf(file, IMAGE_TYPES, IMAGE_EXTENSIONS)
}

/** A verification document: PDF, JPG or PNG, up to 5 MB. */
export function documentIssue(file: File): UploadIssue | null {
  return issueOf(file, DOCUMENT_TYPES, DOCUMENT_EXTENSIONS)
}
