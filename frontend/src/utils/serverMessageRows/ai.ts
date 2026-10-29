/**
 * Backend messages of the ai module, translated like the rows in utils/serverMessages.ts:
 * [English exactly as the backend sends it, Bahasa Melayu, Simplified Chinese].
 */
export const AI_MESSAGES: readonly (readonly [en: string, ms: string, zh: string])[] = [
  // Request limits (AiUsageLimiter) and an empty recording (AiGateway)
  [
    'AI request limit reached. Please wait a minute before trying again.',
    'Had permintaan AI telah dicapai. Sila tunggu seminit sebelum mencuba lagi.',
    'AI 请求过于频繁，请等一分钟后再试。',
  ],
  ['AI usage quota could not be verified.', 'Kuota penggunaan AI tidak dapat disahkan.', '暂时无法核对 AI 使用次数。'],
  [
    "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.",
    'SparkBot tidak dapat mendengar sebarang pertuturan dalam rakaman ini. Rakam sekali lagi lebih dekat dengan mikrofon.',
    'SparkBot 没有在这段录音里听到说话声。请靠近麦克风再录一次。',
  ],

  // Talk with Xiaomei (XiaomeiReplyRequest, XiaomeiConversationRequest, XiaomeiTurn)
  ['Scenario is required.', 'Situasi perbualan diperlukan.', '请选择对话情景。'],
  ['The conversation has no turns yet.', 'Perbualan ini belum bermula.', '对话还没有开始。'],
  [
    'A conversation sends at most 24 earlier turns.',
    'Perbualan hanya boleh menghantar paling banyak 24 giliran sebelumnya.',
    '一次对话最多发送之前的 24 轮内容。',
  ],
  [
    'Message must not exceed 200 characters.',
    'Mesej tidak boleh melebihi 200 aksara.',
    '消息内容不能超过 200 个字符。',
  ],
  ['Turn speaker is required.', 'Penutur bagi setiap giliran diperlukan.', '每轮对话都必须注明说话人。'],
  ['Turn text is required.', 'Teks bagi setiap giliran diperlukan.', '每轮对话都必须有内容。'],
  [
    'Turn text must not exceed 300 characters.',
    'Teks setiap giliran tidak boleh melebihi 300 aksara.',
    '每轮对话内容不能超过 300 个字符。',
  ],

  // Pronunciation and handwriting checks (AiController, AiPracticeCheckService, FileStorageService)
  ['Check type is required.', 'Jenis semakan diperlukan.', '请选择检查类型。'],
  [
    'Check type must not exceed 20 characters.',
    'Jenis semakan tidak boleh melebihi 20 aksara.',
    '检查类型不能超过 20 个字符。',
  ],
  [
    'Check type must be RECORDING or HANDWRITING.',
    'Jenis semakan tidak sah. Pilih rakaman atau tulisan tangan.',
    '检查类型无效。请选择录音或手写。',
  ],
  [
    'Target text must not exceed 100 characters.',
    'Teks sasaran tidak boleh melebihi 100 aksara.',
    '目标文本不能超过 100 个字符。',
  ],
  [
    'Recording length must be at least 1 second.',
    'Panjang rakaman mestilah sekurang-kurangnya 1 saat.',
    '录音时长至少为 1 秒。',
  ],
  [
    'Recording length must not exceed 600 seconds.',
    'Panjang rakaman tidak boleh melebihi 600 saat.',
    '录音时长不能超过 600 秒。',
  ],
  ['Practice check was not found.', 'Semakan latihan tidak ditemui.', '找不到该练习检查。'],
  ['Uploaded file could not be read.', 'Fail yang dimuat naik tidak dapat dibaca.', '无法读取上传的文件。'],

  // AI answer reports and the admin usage log (CreateAiResponseReportRequest, AiResponseReportService, AiLogService)
  ['Chat message id must be positive.', 'ID mesej sembang mestilah nombor positif.', '聊天消息 ID 必须为正数。'],
  ['Practice check id must be positive.', 'ID semakan latihan mestilah nombor positif.', '练习检查 ID 必须为正数。'],
  ['AI chat message was not found.', 'Mesej sembang AI tidak ditemui.', '找不到该 AI 聊天消息。'],
  ['Invalid AI feature filter.', 'Penapis ciri AI tidak sah.', 'AI 功能筛选条件无效。'],
  ['Invalid AI result filter.', 'Penapis keputusan AI tidak sah.', 'AI 结果筛选条件无效。'],
]
