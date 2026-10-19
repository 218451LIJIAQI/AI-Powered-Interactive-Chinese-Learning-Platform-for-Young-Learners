import { useI18n } from 'vue-i18n'

import { studentPracticeSubmissionMessages } from '@/plugins/studentPracticeSubmissionMessages'
import {
  formatFileSize,
  MAX_PINYIN_LENGTH,
  MAX_PRACTICE_UPLOAD_BYTES,
  MAX_TARGET_TEXT_LENGTH,
  MAX_TONE_LENGTH,
  type UploadIssue,
  type UploadKind,
} from '@/utils/practiceUpload'

/** Local i18n scope for the practice-submission components plus translated validation messages. */
export function usePracticeSubmissionText() {
  const { t } = useI18n({
    messages: {
      en: { practiceSubmissions: studentPracticeSubmissionMessages.en },
      ms: { practiceSubmissions: studentPracticeSubmissionMessages.ms },
      'zh-CN': { practiceSubmissions: studentPracticeSubmissionMessages['zh-CN'] },
    },
  })

  const maxSizeLabel = formatFileSize(MAX_PRACTICE_UPLOAD_BYTES)

  function issueMessage(issue: UploadIssue, kind: UploadKind): string {
    switch (issue.code) {
      case 'fileRequired':
        return t(kind === 'audio' ? 'practiceSubmissions.fileRequiredAudio' : 'practiceSubmissions.fileRequiredImage')
      case 'fileType':
        return t(kind === 'audio' ? 'practiceSubmissions.fileTypeAudio' : 'practiceSubmissions.fileTypeImage')
      case 'fileEmpty':
        return t('practiceSubmissions.fileEmpty')
      case 'fileTooLarge':
        return t('practiceSubmissions.fileTooLarge', { size: maxSizeLabel })
      case 'targetRequired':
        return t(
          kind === 'audio' ? 'practiceSubmissions.targetRequiredAudio' : 'practiceSubmissions.targetRequiredImage',
        )
      case 'targetTooLong':
        return t('practiceSubmissions.targetTooLong', { max: MAX_TARGET_TEXT_LENGTH })
      case 'pinyinTooLong':
        return t('practiceSubmissions.pinyinTooLong', { max: MAX_PINYIN_LENGTH })
      case 'toneTooLong':
        return t('practiceSubmissions.toneTooLong', { max: MAX_TONE_LENGTH })
      default:
        return t('practiceSubmissions.lessonInvalid')
    }
  }

  /** First message per field. */
  function fieldErrors(issues: UploadIssue[], kind: UploadKind): Partial<Record<UploadIssue['field'], string>> {
    const errors: Partial<Record<UploadIssue['field'], string>> = {}
    for (const issue of issues) errors[issue.field] ??= issueMessage(issue, kind)
    return errors
  }

  return { t, maxSizeLabel, fieldErrors }
}
