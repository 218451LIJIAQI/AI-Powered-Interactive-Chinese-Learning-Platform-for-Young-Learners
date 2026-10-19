/** Shared learner context strings: breadcrumb (FR-LRN-02) and lesson links / cover image (FR-CONT-05). */
const en = {
  breadcrumb: 'Where you are studying',
  learn: 'Learn',
  practice: 'Practice',
  course: 'Course',
  module: 'Module',
  lesson: 'Lesson',
  quiz: 'Quiz',
  activity: 'Activity',
  opensInNewTab: '(opens in a new tab)',
  coverImageAlt: 'Cover image for {title}',
}

type LearningContextKey = keyof typeof en

const ms: Record<LearningContextKey, string> = {
  breadcrumb: 'Tempat anda sedang belajar',
  learn: 'Belajar',
  practice: 'Latihan',
  course: 'Kursus',
  module: 'Modul',
  lesson: 'Pelajaran',
  quiz: 'Kuiz',
  activity: 'Aktiviti',
  opensInNewTab: '(dibuka dalam tab baharu)',
  coverImageAlt: 'Imej muka depan untuk {title}',
}

const zhCN: Record<LearningContextKey, string> = {
  breadcrumb: '当前学习位置',
  learn: '学习',
  practice: '练习',
  course: '课程',
  module: '单元',
  lesson: '课时',
  quiz: '测验',
  activity: '活动',
  opensInNewTab: '（在新标签页中打开）',
  coverImageAlt: '{title} 的封面图片',
}

export const learningContextMessages = { en, ms, 'zh-CN': zhCN } as const
