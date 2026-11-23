import type { AiFeatureKey } from '@/types/api'

/**
 * Labels shared by Help/report dialogs, the admin support queue, and learner/admin AI reports and usage screens.
 * Mount under the `opsLabels` namespace; `utils/opsLabels.ts` returns topic and queue-status keys while
 * `components/admin/adminDisplay.ts` (`aiFeatureLabel`) translates the `opsLabels.aiFeatures.*` keys.
 */
export const opsLabelMessages = {
  en: {
    topicAccount: 'Account & sign-in',
    topicClasses: 'Classes',
    topicLearning: 'Learn & Practice',
    topicAiHelper: 'AI Helper',
    topicVerification: 'Verification',
    topicSafety: 'Safety concern',
    topicOther: 'Something else',
    queueOpen: 'Open',
    queueAnswered: 'Answered',
    queueSolved: 'Solved',
    aiFeatures: {
      CHAT_TUTOR: 'Ask SparkBot',
      XIAOMEI_CONVERSATION: 'Talk with Xiaomei',
      QUIZ_GENERATOR: 'Quick quiz',
      STORY_GENERATOR: 'Story',
      PRACTICE_CHECK: 'Pronunciation & handwriting',
      QUIZ_DRAFT: 'Quiz drafts',
      LEARNING_PLAN: 'Learning plans',
      HINT: 'Hints',
    } satisfies Record<AiFeatureKey, string>,
  },
  ms: {
    topicAccount: 'Akaun & log masuk',
    topicClasses: 'Kelas',
    topicLearning: 'Belajar & Latihan',
    topicAiHelper: 'Pembantu AI',
    topicVerification: 'Pengesahan',
    topicSafety: 'Kebimbangan keselamatan',
    topicOther: 'Perkara lain',
    queueOpen: 'Dibuka',
    queueAnswered: 'Dijawab',
    queueSolved: 'Selesai',
    aiFeatures: {
      CHAT_TUTOR: 'Tanya SparkBot',
      XIAOMEI_CONVERSATION: 'Berbual dengan Xiaomei',
      QUIZ_GENERATOR: 'Kuiz pantas',
      STORY_GENERATOR: 'Cerita',
      PRACTICE_CHECK: 'Sebutan & tulisan tangan',
      QUIZ_DRAFT: 'Draf kuiz',
      LEARNING_PLAN: 'Pelan pembelajaran',
      HINT: 'Petunjuk',
    } satisfies Record<AiFeatureKey, string>,
  },
  'zh-CN': {
    topicAccount: '账号与登录',
    topicClasses: '课堂',
    topicLearning: '学习与练习',
    topicAiHelper: 'AI 助手',
    topicVerification: '认证',
    topicSafety: '安全问题',
    topicOther: '其他问题',
    queueOpen: '待处理',
    queueAnswered: '已回复',
    queueSolved: '已解决',
    aiFeatures: {
      CHAT_TUTOR: '问 SparkBot',
      XIAOMEI_CONVERSATION: '和小美聊天',
      QUIZ_GENERATOR: '快速测验',
      STORY_GENERATOR: '故事',
      PRACTICE_CHECK: '发音和书写',
      QUIZ_DRAFT: '测验草稿',
      LEARNING_PLAN: '学习计划',
      HINT: '提示',
    } satisfies Record<AiFeatureKey, string>,
  },
}
