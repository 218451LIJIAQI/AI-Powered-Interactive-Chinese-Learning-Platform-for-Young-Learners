import type { PracticeType } from '@/config/practiceActivities'
import { practiceTypeMessages } from '@/plugins/practiceTypeMessages'
import { xpLevelMessages, type XpLevelKey } from '@/plugins/xpLevelMessages'

import type { MessageRow } from './types'

const RANK_ROWS: readonly MessageRow[] = (Object.keys(xpLevelMessages.en) as XpLevelKey[]).map((key) => [
  `You're now Rank {rank} · ${xpLevelMessages.en[key]}. Rank {next} is next.`,
  `Anda kini Pangkat {rank} · ${xpLevelMessages.ms[key]}. Seterusnya Pangkat {next}.`,
  `你现在是第 {rank} 段位 · ${xpLevelMessages['zh-CN'][key]}。下一个是第 {next} 段位。`,
])

const PRACTICE_TYPE_ROWS: readonly MessageRow[] = (Object.keys(practiceTypeMessages.en) as PracticeType[]).map(
  (type) => [
    `A new ${practiceTypeMessages.en[type].toLowerCase()} set is ready: “{title}”.`,
    `Set ${practiceTypeMessages.ms[type].toLowerCase()} baharu sudah sedia: “{title}”.`,
    `新的${practiceTypeMessages['zh-CN'][type]}练习组已上线：“{title}”。`,
  ],
)

/**
 * Titles and messages of in-app notifications currently written by the backend (SupportService, AiResponseReportService,
 * LearnerNotifications, LearnerReminderJobs, AdminUserService, DemoOpsSeeder).
 *
 * Placeholders in braces are kept unchanged from the backend text in each language; rank titles and Practice activity
 * names are derived directly from `xpLevelMessages` and `practiceTypeMessages`.
 */
export const ACTIVE_NOTIFICATION_MESSAGES: readonly MessageRow[] = [
  // Help & support (SupportService)
  ['New support request', 'Permintaan sokongan baharu', '新的支持请求'],
  [
    '{name} sent #{number} “{subject}”. Reply within 2 working days.',
    '{name} menghantar #{number} “{subject}”. Balas dalam 2 hari bekerja.',
    '{name} 发送了 #{number}“{subject}”。请在 2 个工作日内回复。',
  ],
  [
    'A visitor ({email}) sent #{number} “{subject}”. Reply within 2 working days.',
    'Seorang pelawat ({email}) menghantar #{number} “{subject}”. Balas dalam 2 hari bekerja.',
    '一位访客（{email}）发送了 #{number}“{subject}”。请在 2 个工作日内回复。',
  ],

  // AI answer reports (AiResponseReportService)
  ['AI report needs review', 'Laporan AI perlu disemak', 'AI 举报待审核'],
  [
    '{name} reported an AI answer (incorrect content).',
    '{name} melaporkan jawapan AI (kandungan tidak tepat).',
    '{name} 举报了一条 AI 回答（内容不正确）。',
  ],
  [
    '{name} reported an AI answer (unclear explanation).',
    '{name} melaporkan jawapan AI (penerangan tidak jelas).',
    '{name} 举报了一条 AI 回答（解释不清楚）。',
  ],
  [
    '{name} reported an AI answer (inappropriate content).',
    '{name} melaporkan jawapan AI (tidak selamat atau tidak sesuai).',
    '{name} 举报了一条 AI 回答（不安全或不恰当）。',
  ],
  [
    '{name} reported an AI answer (score seems wrong).',
    '{name} melaporkan jawapan AI (markah kelihatan salah).',
    '{name} 举报了一条 AI 回答（分数似乎不对）。',
  ],
  [
    '{name} reported an AI answer (does not match input).',
    '{name} melaporkan jawapan AI (tidak sepadan dengan apa yang ditulis).',
    '{name} 举报了一条 AI 回答（与所写内容不符）。',
  ],
  [
    '{name} reported an AI answer (other).',
    '{name} melaporkan jawapan AI (lain-lain).',
    '{name} 举报了一条 AI 回答（其他）。',
  ],
  ['An admin replied to your AI report', 'Pentadbir telah membalas laporan AI anda', '管理员回复了你的 AI 举报'],
  [
    "Your report was resolved. Read the admin's note in AI Helper › Ask SparkBot › My reports.",
    'Laporan anda telah diselesaikan. Baca nota pentadbir dalam Pembantu AI › Tanya SparkBot › Laporan saya.',
    '你的举报已处理。请在“AI 助手 › 问 SparkBot › 我的举报”中查看管理员的备注。',
  ],
  [
    "Your report was checked and dismissed. Read the admin's note in AI Helper › Ask SparkBot › My reports.",
    'Laporan anda telah disemak dan ditolak. Baca nota pentadbir dalam Pembantu AI › Tanya SparkBot › Laporan saya.',
    '你的举报已核查，未予采纳。请在“AI 助手 › 问 SparkBot › 我的举报”中查看管理员的备注。',
  ],

  // Learners (LearnerNotifications, LearnerReminderJobs)
  ['You reached Rank {rank}', 'Anda mencapai Pangkat {rank}', '你升到了第 {rank} 段位'],
  ...RANK_ROWS,
  ['New in Practice', 'Baharu dalam Latihan', '练习有新内容'],
  ...PRACTICE_TYPE_ROWS,
  ['Lesson updated', 'Pelajaran dikemas kini', '课时已更新'],
  [
    '“{lesson}” in {class} was updated with new content.',
    '“{lesson}” dalam {class} telah dikemas kini dengan kandungan baharu.',
    '“{class}”中的“{lesson}”已更新，加入了新内容。',
  ],
  ['Daily goal reminder', 'Peringatan matlamat harian', '每日目标提醒'],
  [
    "You're {xp} XP away from today's goal.",
    'Anda hanya {xp} XP lagi daripada matlamat hari ini.',
    '离今天的目标还差 {xp} XP。',
  ],
  ['Mistakes to review', 'Kesilapan untuk diulang kaji', '待复习的错题'],
  [
    'You have {count} mistakes saved. Fixing them takes about {minutes} minutes.',
    'Anda ada {count} kesilapan yang disimpan. Membetulkannya mengambil masa kira-kira {minutes} minit.',
    '你有 {count} 道错题待复习，改正它们大约需要 {minutes} 分钟。',
  ],

  // Accounts (AdminUserService)
  ['Your account is active again', 'Akaun anda aktif semula', '你的账号已重新启用'],
  [
    'An admin reactivated your account. Welcome back.',
    'Pentadbir telah mengaktifkan semula akaun anda. Selamat kembali.',
    '管理员已重新启用你的账号。欢迎回来。',
  ],
]

/**
 * Retired notification wordings no longer written by the backend that can still exist in post-V3 databases (admin
 * support replies, admin teacher-application queue entries, and earlier AI-report reply wordings).
 * `serverMessages.test.ts` asserts the backend writes none of these.
 */
export const RETIRED_NOTIFICATION_MESSAGES: readonly MessageRow[] = [
  ['Reply on a support request', 'Balasan pada permintaan sokongan', '支持请求有新回复'],
  [
    '{name} replied on #{number} “{subject}”.',
    '{name} membalas pada #{number} “{subject}”.',
    '{name} 回复了 #{number}“{subject}”。',
  ],
  ['New teacher application', 'Permohonan guru baharu', '新的教师申请'],
  [
    '{name} submitted verification documents.',
    '{name} telah menghantar dokumen pengesahan.',
    '{name} 提交了认证文件。',
  ],
  ['Teacher application resubmitted', 'Permohonan guru dihantar semula', '教师申请已重新提交'],
  [
    '{name} sent updated verification documents.',
    '{name} telah menghantar dokumen pengesahan yang dikemas kini.',
    '{name} 提交了更新后的认证文件。',
  ],
  [
    "Your report was resolved. Read the admin's note in AI Helper, My reports.",
    'Laporan anda telah diselesaikan. Baca nota pentadbir dalam Pembantu AI, Laporan saya.',
    '你的举报已处理。请在“AI 助手”的“我的举报”中查看管理员的备注。',
  ],
  [
    "Your report was checked and dismissed. Read the admin's note in AI Helper, My reports.",
    'Laporan anda telah disemak dan ditolak. Baca nota pentadbir dalam Pembantu AI, Laporan saya.',
    '你的举报已核查，未予采纳。请在“AI 助手”的“我的举报”中查看管理员的备注。',
  ],
]

export const NOTIFICATION_MESSAGES: readonly MessageRow[] = [
  ...ACTIVE_NOTIFICATION_MESSAGES,
  ...RETIRED_NOTIFICATION_MESSAGES,
]
