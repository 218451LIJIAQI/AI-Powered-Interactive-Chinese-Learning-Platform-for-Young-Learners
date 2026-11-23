import type { MessageRow } from './types'

/**
 * Titles and messages of in-app notifications currently written by the backend (SupportService, AiResponseReportService,
 * LearnerNotifications, LearnerReminderJobs, AdminUserService, DemoOpsSeeder).
 *
 * Placeholders in braces are kept unchanged from the backend text in each language; rank titles and Practice activity
 * names stay aligned with `xpLevelMessages` and `practiceTypeMessages`.
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
  [
    "You're now Rank {rank} · Starter. Rank {next} is next.",
    'Anda kini Pangkat {rank} · Permulaan. Seterusnya Pangkat {next}.',
    '你现在是第 {rank} 段位 · 新手。下一个是第 {next} 段位。',
  ],
  [
    "You're now Rank {rank} · Explorer. Rank {next} is next.",
    'Anda kini Pangkat {rank} · Penjelajah. Seterusnya Pangkat {next}.',
    '你现在是第 {rank} 段位 · 探索者。下一个是第 {next} 段位。',
  ],
  [
    "You're now Rank {rank} · Regular. Rank {next} is next.",
    'Anda kini Pangkat {rank} · Tetap. Seterusnya Pangkat {next}.',
    '你现在是第 {rank} 段位 · 常客。下一个是第 {next} 段位。',
  ],
  [
    "You're now Rank {rank} · Committed. Rank {next} is next.",
    'Anda kini Pangkat {rank} · Komited. Seterusnya Pangkat {next}.',
    '你现在是第 {rank} 段位 · 坚持者。下一个是第 {next} 段位。',
  ],
  [
    "You're now Rank {rank} · Spark Champion. Rank {next} is next.",
    'Anda kini Pangkat {rank} · Juara Spark. Seterusnya Pangkat {next}.',
    '你现在是第 {rank} 段位 · 星火冠军。下一个是第 {next} 段位。',
  ],
  ['New in Practice', 'Baharu dalam Latihan', '练习有新内容'],
  [
    'A new flashcards set is ready: “{title}”.',
    'Set kad imbas baharu sudah sedia: “{title}”.',
    '新的闪卡练习组已上线：“{title}”。',
  ],
  [
    'A new listening set is ready: “{title}”.',
    'Set mendengar baharu sudah sedia: “{title}”.',
    '新的听力练习组已上线：“{title}”。',
  ],
  [
    'A new tracing set is ready: “{title}”.',
    'Set menyurih baharu sudah sedia: “{title}”.',
    '新的描红练习组已上线：“{title}”。',
  ],
  [
    'A new drag & drop set is ready: “{title}”.',
    'Set seret & lepas baharu sudah sedia: “{title}”.',
    '新的拖放练习组已上线：“{title}”。',
  ],
  [
    'A new matching set is ready: “{title}”.',
    'Set padanan baharu sudah sedia: “{title}”.',
    '新的配对练习组已上线：“{title}”。',
  ],
  [
    'A new typing set is ready: “{title}”.',
    'Set menaip baharu sudah sedia: “{title}”.',
    '新的打字练习组已上线：“{title}”。',
  ],
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
 * Retired notification wordings no longer written by the backend, kept so previously stored notifications still render
 * in the reader's language. `serverMessages.test.ts` asserts the backend writes none of these.
 */
export const RETIRED_NOTIFICATION_MESSAGES: readonly MessageRow[] = [
  ['Live session starts soon', 'Sesi langsung akan bermula sebentar lagi', '直播课即将开始'],
  [
    '"{title}" ({class}) starts at {start}, {zone} time.',
    '"{title}" ({class}) bermula {start}, waktu {zone}.',
    '“{title}”（{class}）将于 {start}（{zone} 时间）开始。',
  ],
  ['Live session rescheduled', 'Sesi langsung dijadualkan semula', '直播课已改期'],
  ['{title} has a new time.', 'Masa "{title}" telah ditukar.', '“{title}”的时间已更改。'],
  ['Live session updated', 'Sesi langsung dikemas kini', '直播课已更新'],
  ['{title} has new details.', 'Butiran "{title}" telah dikemas kini.', '“{title}”的信息已更新。'],
  ['Live session cancelled', 'Sesi langsung dibatalkan', '直播课已取消'],
  ['{title} has been cancelled.', '"{title}" telah dibatalkan.', '“{title}”已取消。'],
  ['Recording ready', 'Rakaman sudah tersedia', '录像已可观看'],
  [
    'The recording of {title} is ready to watch.',
    'Rakaman "{title}" sudah boleh ditonton.',
    '“{title}”的录像已可观看。',
  ],
  ['New student', 'Pelajar baharu', '新学生'],
  ['{name} joined {class}.', '{name} telah menyertai {class}.', '{name} 加入了“{class}”。'],
  ['New question from {name}', 'Soalan baharu daripada {name}', '{name} 提了一个新问题'],
  ['{name} answered your question', '{name} telah menjawab soalan anda', '{name} 回答了你的问题'],
  ['About {title}.', 'Mengenai {title}.', '关于“{title}”。'],
  ['New feedback from {name}', 'Maklum balas baharu daripada {name}', '{name} 给了你新的反馈'],
  [
    'Your teacher wrote to you about {class}.',
    'Guru anda menulis kepada anda mengenai {class}.',
    '老师就“{class}”给你写了反馈。',
  ],
  ['Class approved', 'Kelas diluluskan', '课堂已通过审核'],
  [
    '"{title}" is approved and live for students.',
    '"{title}" telah diluluskan dan kini tersedia untuk pelajar.',
    '“{title}”已通过审核，学生现在可以看到。',
  ],
  ['Class details approved', 'Butiran kelas diluluskan', '课堂信息已通过审核'],
  [
    'Your changes to "{title}" are approved and live.',
    'Perubahan anda pada "{title}" telah diluluskan dan kini berkuat kuasa.',
    '你对“{title}”的修改已通过审核并已生效。',
  ],
  ['Lesson approved', 'Pelajaran diluluskan', '课时已通过审核'],
  ['Lesson changes approved', 'Perubahan pelajaran diluluskan', '课时修改已通过审核'],
  [
    'Your changes to "{title}" are approved and live for students.',
    'Perubahan anda pada "{title}" telah diluluskan dan kini tersedia untuk pelajar.',
    '你对“{title}”的修改已通过审核，学生现在可以看到。',
  ],
  ['Changes requested', 'Perubahan diminta', '需要修改'],
  [
    'An admin asked for changes to "{title}": {reason}',
    'Pentadbir meminta perubahan pada "{title}": {reason}',
    '管理员要求修改“{title}”：{reason}',
  ],
  [
    'An admin asked for changes to the details of "{title}": {reason}',
    'Pentadbir meminta perubahan pada butiran "{title}": {reason}',
    '管理员要求修改“{title}”的课堂信息：{reason}',
  ],
  ['Class archived by an admin', 'Kelas diarkibkan oleh pentadbir', '课堂已被管理员归档'],
  [
    'An admin archived "{title}". New students can\'t join it; students who joined keep their progress.',
    'Pentadbir telah mengarkibkan "{title}". Pelajar baharu tidak boleh menyertainya; pelajar yang telah menyertai mengekalkan kemajuan mereka.',
    '管理员已归档“{title}”。新学生不能再加入，已加入的学生保留学习进度。',
  ],
  ['Lesson archived by an admin', 'Pelajaran diarkibkan oleh pentadbir', '课时已被管理员归档'],
  [
    'An admin archived "{title}". Students can\'t open it any more; their progress is kept.',
    'Pentadbir telah mengarkibkan "{title}". Pelajar tidak boleh membukanya lagi; kemajuan mereka disimpan.',
    '管理员已归档“{title}”。学生不能再打开它，学习进度会保留。',
  ],
  ['Class restored by an admin', 'Kelas dipulihkan oleh pentadbir', '课堂已被管理员恢复'],
  ['Lesson restored by an admin', 'Pelajaran dipulihkan oleh pentadbir', '课时已被管理员恢复'],
  ['An admin restored "{title}".', 'Pentadbir telah memulihkan "{title}".', '管理员已恢复“{title}”。'],
  ['Reply on a support request', 'Balasan pada permintaan sokongan', '支持请求有新回复'],
  [
    '{name} replied on #{number} “{subject}”.',
    '{name} membalas pada #{number} “{subject}”.',
    '{name} 回复了 #{number}“{subject}”。',
  ],
  ['Support replied to your request', 'Sokongan telah membalas permintaan anda', '客服回复了你的请求'],
  [
    '{name} answered #{number} “{subject}”. Reply, or mark it solved.',
    '{name} telah menjawab #{number} “{subject}”. Balas, atau tandakan sebagai selesai.',
    '{name} 回复了 #{number}“{subject}”。你可以继续回复，或将其标记为已解决。',
  ],
  ['Your request was marked as solved', 'Permintaan anda telah ditandakan sebagai selesai', '你的请求已标记为已解决'],
  [
    '#{number} “{subject}” is solved. If it happens again, send a new request.',
    '#{number} “{subject}” telah selesai. Jika ia berlaku lagi, hantar permintaan baharu.',
    '#{number}“{subject}”已解决。如果问题再次出现，请发送新的请求。',
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
  ['Your teacher account is approved', 'Akaun guru anda telah diluluskan', '你的教师账号已通过审核'],
  [
    'Every teaching tool is now open. Start by creating your first class.',
    'Semua alat mengajar kini dibuka. Mulakan dengan mencipta kelas pertama anda.',
    '所有教学工具现已开放。先创建你的第一个课堂吧。',
  ],
  ['Changes requested on your application', 'Perubahan diminta pada permohonan anda', '你的申请需要修改'],
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
