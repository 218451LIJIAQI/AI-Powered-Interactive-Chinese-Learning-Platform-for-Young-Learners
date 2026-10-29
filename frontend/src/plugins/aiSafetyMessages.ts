/** Shared wording for AI output labels, the AI disclaimer and the reasons of an AI report. */
export const aiSafetyMessages = {
  en: {
    aiAssisted: 'AI-assisted',
    disclaimer: 'AI responses may contain errors and do not replace your teacher.',
    guidanceLink: 'Responsible AI Guidance',
    reasonIncorrect: 'Incorrect content',
    reasonUnclear: 'Unclear explanation',
    reasonInappropriate: 'Unsafe or inappropriate',
    reasonScore: 'Score seems wrong',
    reasonOther: 'Other',
  },
  ms: {
    aiAssisted: 'Berbantukan AI',
    disclaimer: 'Jawapan AI mungkin mengandungi kesilapan dan tidak menggantikan guru anda.',
    guidanceLink: 'Panduan AI Bertanggungjawab',
    reasonIncorrect: 'Kandungan tidak tepat',
    reasonUnclear: 'Penerangan tidak jelas',
    reasonInappropriate: 'Tidak selamat atau tidak sesuai',
    reasonScore: 'Markah kelihatan salah',
    reasonOther: 'Lain-lain',
  },
  'zh-CN': {
    aiAssisted: 'AI 辅助',
    disclaimer: 'AI 回答可能有错误，不能替代您的教师。',
    guidanceLink: '负责任 AI 使用指南',
    reasonIncorrect: '内容不正确',
    reasonUnclear: '解释不清楚',
    reasonInappropriate: '不安全或不恰当',
    reasonScore: '分数似乎不对',
    reasonOther: '其他',
  },
} as const

/** Full i18n key (bundle registered as `aiSafety`) for every report reason the backend accepts. */
export const AI_REPORT_REASON_KEYS = {
  INCORRECT_CONTENT: 'aiSafety.reasonIncorrect',
  UNCLEAR_EXPLANATION: 'aiSafety.reasonUnclear',
  INAPPROPRIATE_CONTENT: 'aiSafety.reasonInappropriate',
  SCORE_SEEMS_WRONG: 'aiSafety.reasonScore',
  OTHER: 'aiSafety.reasonOther',
} as const satisfies Record<string, `aiSafety.${keyof (typeof aiSafetyMessages)['en']}`>
