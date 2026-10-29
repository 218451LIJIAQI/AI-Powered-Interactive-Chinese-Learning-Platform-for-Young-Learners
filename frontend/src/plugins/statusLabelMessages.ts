/**
 * Translated labels for enum values the app shows through `useStatusLabels()` (composables/useStatusLabels.ts), so users
 * never see a raw IN_PROGRESS (FRLANG02). Keys are `<group>_<VALUE>`:
 * - `mastery`: lesson mastery (MasteryChip, class lesson pages);
 * - `auditAction`: actions of retired features that older audit log entries still carry. Actions the backend
 *   writes today are named in plugins/auditActionMessages.ts (utils/auditActions.ts), which takes precedence.
 */
export const statusLabelMessages = {
  en: {
    mastery_NOT_STARTED: 'Not started',
    mastery_IN_PROGRESS: 'In progress',
    mastery_COMPLETED: 'Completed',
    mastery_MASTERED: 'Mastered',
    auditAction_TEACHER_ACTIVITY_CREATE: 'Teacher activity created',
    auditAction_TEACHER_ACTIVITY_UPDATE: 'Teacher activity updated',
    auditAction_TEACHER_ACTIVITY_DELETE: 'Teacher activity deleted',
    auditAction_ADMIN_ACTIVITY_CREATE: 'Library activity created',
    auditAction_ADMIN_ACTIVITY_UPDATE: 'Library activity updated',
    auditAction_ADMIN_ACTIVITY_DELETE: 'Library activity deleted',
    auditAction_STUDENT_LIVE_CLASS_ENROL: 'Enrolled in a live class',
    auditAction_STUDENT_LIVE_CLASS_CANCEL_ENROLMENT: 'Live class enrolment cancelled',
    auditAction_STUDENT_LIVE_CLASS_ATTENDED: 'Live class joined',
    auditAction_STUDENT_RECORDED_VIDEO_COMPLETE: 'Recorded video completed',
    auditAction_TEACHER_FEEDBACK_CREATED: 'Teacher feedback written',
    auditAction_TEACHER_FEEDBACK_UPDATED: 'Teacher feedback edited',
    auditAction_TEACHER_PACKAGE_CREATE: 'Course created',
    auditAction_TEACHER_PACKAGE_UPDATE: 'Course updated',
    auditAction_TEACHER_PACKAGE_ARCHIVE: 'Course archived by teacher',
    auditAction_TEACHER_PACKAGE_ITEM_ADD: 'Item added to a course',
    auditAction_TEACHER_PACKAGE_ITEM_REMOVE: 'Item removed from a course',
    auditAction_TEACHER_MODULE_CREATE: 'Module created',
    auditAction_TEACHER_MODULE_UPDATE: 'Module updated',
    auditAction_TEACHER_MODULE_ARCHIVE: 'Module archived by teacher',
    auditAction_TEACHER_LESSON_CREATE: 'Lesson created',
    auditAction_TEACHER_LESSON_UPDATE: 'Lesson updated',
    auditAction_TEACHER_LESSON_ARCHIVE: 'Lesson archived by teacher',
    auditAction_TEACHER_QUIZ_CREATE: 'Quiz created',
    auditAction_TEACHER_QUIZ_UPDATE: 'Quiz updated',
    auditAction_TEACHER_QUIZ_ARCHIVE: 'Quiz archived by teacher',
    auditAction_TEACHER_QUIZ_QUESTION_CREATE: 'Quiz question added',
    auditAction_TEACHER_QUIZ_QUESTION_UPDATE: 'Quiz question edited',
    auditAction_TEACHER_QUIZ_QUESTION_DELETE: 'Quiz question deleted',
    auditAction_TEACHER_RECORDED_VIDEO_CREATE: 'Recorded video added',
    auditAction_TEACHER_RECORDED_VIDEO_UPDATE: 'Recorded video updated',
    auditAction_TEACHER_RECORDED_VIDEO_ARCHIVE: 'Recorded video archived',
    auditAction_TEACHER_LIVE_CLASS_CREATE: 'Live class created',
    auditAction_TEACHER_LIVE_CLASS_UPDATE: 'Live class updated',
    auditAction_TEACHER_LIVE_CLASS_CANCEL: 'Live class cancelled',
  },
  ms: {
    mastery_NOT_STARTED: 'Belum bermula',
    mastery_IN_PROGRESS: 'Sedang berjalan',
    mastery_COMPLETED: 'Selesai',
    mastery_MASTERED: 'Dikuasai',
    auditAction_TEACHER_ACTIVITY_CREATE: 'Aktiviti guru dicipta',
    auditAction_TEACHER_ACTIVITY_UPDATE: 'Aktiviti guru dikemas kini',
    auditAction_TEACHER_ACTIVITY_DELETE: 'Aktiviti guru dipadam',
    auditAction_ADMIN_ACTIVITY_CREATE: 'Aktiviti perpustakaan dicipta',
    auditAction_ADMIN_ACTIVITY_UPDATE: 'Aktiviti perpustakaan dikemas kini',
    auditAction_ADMIN_ACTIVITY_DELETE: 'Aktiviti perpustakaan dipadam',
    auditAction_STUDENT_LIVE_CLASS_ENROL: 'Mendaftar dalam kelas langsung',
    auditAction_STUDENT_LIVE_CLASS_CANCEL_ENROLMENT: 'Pendaftaran kelas langsung dibatalkan',
    auditAction_STUDENT_LIVE_CLASS_ATTENDED: 'Menyertai kelas langsung',
    auditAction_STUDENT_RECORDED_VIDEO_COMPLETE: 'Video rakaman selesai ditonton',
    auditAction_TEACHER_FEEDBACK_CREATED: 'Maklum balas guru ditulis',
    auditAction_TEACHER_FEEDBACK_UPDATED: 'Maklum balas guru disunting',
    auditAction_TEACHER_PACKAGE_CREATE: 'Kursus dicipta',
    auditAction_TEACHER_PACKAGE_UPDATE: 'Kursus dikemas kini',
    auditAction_TEACHER_PACKAGE_ARCHIVE: 'Kursus diarkibkan oleh guru',
    auditAction_TEACHER_PACKAGE_ITEM_ADD: 'Item ditambah ke kursus',
    auditAction_TEACHER_PACKAGE_ITEM_REMOVE: 'Item dibuang daripada kursus',
    auditAction_TEACHER_MODULE_CREATE: 'Modul dicipta',
    auditAction_TEACHER_MODULE_UPDATE: 'Modul dikemas kini',
    auditAction_TEACHER_MODULE_ARCHIVE: 'Modul diarkibkan oleh guru',
    auditAction_TEACHER_LESSON_CREATE: 'Pelajaran dicipta',
    auditAction_TEACHER_LESSON_UPDATE: 'Pelajaran dikemas kini',
    auditAction_TEACHER_LESSON_ARCHIVE: 'Pelajaran diarkibkan oleh guru',
    auditAction_TEACHER_QUIZ_CREATE: 'Kuiz dicipta',
    auditAction_TEACHER_QUIZ_UPDATE: 'Kuiz dikemas kini',
    auditAction_TEACHER_QUIZ_ARCHIVE: 'Kuiz diarkibkan oleh guru',
    auditAction_TEACHER_QUIZ_QUESTION_CREATE: 'Soalan kuiz ditambah',
    auditAction_TEACHER_QUIZ_QUESTION_UPDATE: 'Soalan kuiz disunting',
    auditAction_TEACHER_QUIZ_QUESTION_DELETE: 'Soalan kuiz dipadam',
    auditAction_TEACHER_RECORDED_VIDEO_CREATE: 'Video rakaman ditambah',
    auditAction_TEACHER_RECORDED_VIDEO_UPDATE: 'Video rakaman dikemas kini',
    auditAction_TEACHER_RECORDED_VIDEO_ARCHIVE: 'Video rakaman diarkibkan',
    auditAction_TEACHER_LIVE_CLASS_CREATE: 'Kelas langsung dicipta',
    auditAction_TEACHER_LIVE_CLASS_UPDATE: 'Kelas langsung dikemas kini',
    auditAction_TEACHER_LIVE_CLASS_CANCEL: 'Kelas langsung dibatalkan',
  },
  'zh-CN': {
    mastery_NOT_STARTED: '未开始',
    mastery_IN_PROGRESS: '学习中',
    mastery_COMPLETED: '已完成',
    mastery_MASTERED: '已掌握',
    auditAction_TEACHER_ACTIVITY_CREATE: '教师已创建活动',
    auditAction_TEACHER_ACTIVITY_UPDATE: '教师已更新活动',
    auditAction_TEACHER_ACTIVITY_DELETE: '教师已删除活动',
    auditAction_ADMIN_ACTIVITY_CREATE: '已创建练习库活动',
    auditAction_ADMIN_ACTIVITY_UPDATE: '已更新练习库活动',
    auditAction_ADMIN_ACTIVITY_DELETE: '已删除练习库活动',
    auditAction_STUDENT_LIVE_CLASS_ENROL: '已报名直播课',
    auditAction_STUDENT_LIVE_CLASS_CANCEL_ENROLMENT: '已取消直播课报名',
    auditAction_STUDENT_LIVE_CLASS_ATTENDED: '已加入直播课',
    auditAction_STUDENT_RECORDED_VIDEO_COMPLETE: '已看完录播视频',
    auditAction_TEACHER_FEEDBACK_CREATED: '教师已撰写反馈',
    auditAction_TEACHER_FEEDBACK_UPDATED: '教师已修改反馈',
    auditAction_TEACHER_PACKAGE_CREATE: '已创建课程',
    auditAction_TEACHER_PACKAGE_UPDATE: '已更新课程',
    auditAction_TEACHER_PACKAGE_ARCHIVE: '教师已归档课程',
    auditAction_TEACHER_PACKAGE_ITEM_ADD: '已向课程添加项目',
    auditAction_TEACHER_PACKAGE_ITEM_REMOVE: '已从课程移除项目',
    auditAction_TEACHER_MODULE_CREATE: '已创建单元',
    auditAction_TEACHER_MODULE_UPDATE: '已更新单元',
    auditAction_TEACHER_MODULE_ARCHIVE: '教师已归档单元',
    auditAction_TEACHER_LESSON_CREATE: '已创建课时',
    auditAction_TEACHER_LESSON_UPDATE: '已更新课时',
    auditAction_TEACHER_LESSON_ARCHIVE: '教师已归档课时',
    auditAction_TEACHER_QUIZ_CREATE: '已创建测验',
    auditAction_TEACHER_QUIZ_UPDATE: '已更新测验',
    auditAction_TEACHER_QUIZ_ARCHIVE: '教师已归档测验',
    auditAction_TEACHER_QUIZ_QUESTION_CREATE: '已添加测验题目',
    auditAction_TEACHER_QUIZ_QUESTION_UPDATE: '已修改测验题目',
    auditAction_TEACHER_QUIZ_QUESTION_DELETE: '已删除测验题目',
    auditAction_TEACHER_RECORDED_VIDEO_CREATE: '已添加录播视频',
    auditAction_TEACHER_RECORDED_VIDEO_UPDATE: '已更新录播视频',
    auditAction_TEACHER_RECORDED_VIDEO_ARCHIVE: '已归档录播视频',
    auditAction_TEACHER_LIVE_CLASS_CREATE: '已创建直播课',
    auditAction_TEACHER_LIVE_CLASS_UPDATE: '已更新直播课',
    auditAction_TEACHER_LIVE_CLASS_CANCEL: '已取消直播课',
  },
} as const

type StatusLabelKey = keyof (typeof statusLabelMessages)['en']

/** The `<group>` part of the bundle keys (group names contain no underscore). */
export type StatusLabelGroup = StatusLabelKey extends `${infer Group}_${string}` ? Group : never

const WORD_START_PATTERN = /(^|\s)\w/g

/** Readable fallback for values the bundle does not know yet (e.g. a new backend enum). */
export function humanizeEnumValue(value: string | null | undefined): string {
  if (!value?.trim()) return ''
  return value
    .trim()
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(WORD_START_PATTERN, (match) => match.toUpperCase())
}

/** Returns the bundle key for a value, or null when it is unknown. */
export function statusLabelKey(group: StatusLabelGroup, value: string | null | undefined): StatusLabelKey | null {
  const normalised = value?.trim().toUpperCase()
  if (!normalised) return null
  const key = `${group}_${normalised}`
  return key in statusLabelMessages.en ? (key as StatusLabelKey) : null
}
