import { useI18n } from 'vue-i18n'

import { studentPracticeMessages } from '@/plugins/studentPracticeMessages'

/** Local i18n scope with the student practice bundle under `studentPractice.*` (shared by the activity players). */
export function usePracticeI18n() {
  return useI18n({
    messages: {
      en: { studentPractice: studentPracticeMessages.en },
      ms: { studentPractice: studentPracticeMessages.ms },
      'zh-CN': { studentPractice: studentPracticeMessages['zh-CN'] },
    },
  })
}
