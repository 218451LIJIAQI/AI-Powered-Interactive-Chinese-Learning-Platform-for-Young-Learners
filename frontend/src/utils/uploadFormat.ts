type UploadKind = 'audio' | 'image' | 'video'

/** Why the server would refuse a chosen file. */
export type UploadFileIssue = 'fileType' | 'fileEmpty' | 'fileTooLarge'

interface UploadFormat {
  extensions: string[]
  mediaTypes: string[]
}

/** Extension and media type pairs accepted by the corresponding backend upload policy. */
const formats: Record<UploadKind, UploadFormat[]> = {
  audio: [
    { extensions: ['.webm'], mediaTypes: ['audio/webm', 'video/webm'] },
    { extensions: ['.wav'], mediaTypes: ['audio/wav', 'audio/x-wav'] },
    { extensions: ['.mp3', '.mpeg'], mediaTypes: ['audio/mpeg', 'audio/mp3'] },
  ],
  image: [
    { extensions: ['.png'], mediaTypes: ['image/png'] },
    { extensions: ['.jpg', '.jpeg'], mediaTypes: ['image/jpeg'] },
  ],
  video: [
    { extensions: ['.mp4'], mediaTypes: ['video/mp4'] },
    { extensions: ['.webm'], mediaTypes: ['video/webm'] },
    { extensions: ['.mov'], mediaTypes: ['video/quicktime'] },
  ],
}

export function uploadAccept(kind: UploadKind): string {
  return formats[kind].flatMap(({ extensions, mediaTypes }) => [...extensions, ...mediaTypes]).join(',')
}

function matchesUploadFormat(file: Pick<File, 'name' | 'type'>, kind: UploadKind): boolean {
  const mediaType = file.type.split(';', 1)[0].trim().toLowerCase()
  const dotIndex = file.name.lastIndexOf('.')
  if (dotIndex <= 0) return false
  // Match backend FileNames.sanitize: reject stems made solely of dots and whitespace such as "..mp4" or ". ..mp4".
  const stem = file.name.slice(0, dotIndex).trim()
  if (!/[^.\s]/.test(stem)) return false
  const extension = file.name.slice(dotIndex).toLowerCase()
  return formats[kind].some((format) => format.extensions.includes(extension) && format.mediaTypes.includes(mediaType))
}

/** Check a file's format, non-empty size, and optional max byte limit before uploading. */
export function uploadFileIssue(
  file: Pick<File, 'name' | 'size' | 'type'>,
  kind: UploadKind,
  maxBytes?: number,
): UploadFileIssue | null {
  if (!matchesUploadFormat(file, kind)) return 'fileType'
  if (file.size <= 0) return 'fileEmpty'
  if (maxBytes !== undefined && file.size > maxBytes) return 'fileTooLarge'
  return null
}
