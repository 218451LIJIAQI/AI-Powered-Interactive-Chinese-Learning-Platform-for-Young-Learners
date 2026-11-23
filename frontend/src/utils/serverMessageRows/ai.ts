import type { MessageRow } from './types'

/** Backend messages of the ai module, translated like the rows in utils/serverMessages.ts. */
export const AI_MESSAGES: readonly MessageRow[] = [
  // Pronunciation and handwriting check failures (TencentPronunciationClient, AiPracticeCheckService)
  [
    "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.",
    'SparkBot tidak dapat mendengar sebarang pertuturan dalam rakaman ini. Rakam sekali lagi lebih dekat dengan mikrofon.',
    'SparkBot 没有在这段录音里听到说话声。请靠近麦克风再录一次。',
  ],
  [
    'Enter the Mandarin target in Chinese characters.',
    'Masukkan teks sasaran Mandarin dalam aksara Cina.',
    '请用汉字填写要朗读的普通话词句。',
  ],
  [
    'This audio file could not be read. Choose another file or record again.',
    'Fail audio ini tidak dapat dibaca. Pilih fail lain atau rakam semula.',
    '无法读取这段音频。请选择其他文件，或重新录音。',
  ],
  [
    'Choose a recording of five minutes or less.',
    'Pilih rakaman yang berdurasi lima minit atau kurang.',
    '请选择五分钟以内的录音。',
  ],
  [
    'We could not match this recording to the target. Read the target aloud and try again.',
    'Rakaman ini tidak dapat dipadankan dengan teks sasaran. Baca teks itu dengan kuat dan cuba lagi.',
    '这段录音无法与目标文字匹配。请朗读目标文字后再试一次。',
  ],
  [
    'We could not read the handwriting clearly. Retake the photo in good light with the whole character in frame.',
    'Tulisan tangan tidak cukup jelas. Ambil semula foto dalam cahaya yang baik dan pastikan seluruh aksara kelihatan.',
    '照片里的手写字看不清。请在光线充足的地方重拍，让整个字完整入镜。',
  ],

  // Pronunciation and handwriting checks (AiPracticeCheckService, FileStorageService)
  [
    'Check type must be RECORDING or HANDWRITING.',
    'Jenis semakan tidak sah. Pilih rakaman atau tulisan tangan.',
    '检查类型无效。请选择录音或手写。',
  ],
  ['Practice check was not found.', 'Semakan latihan tidak ditemui.', '找不到该练习检查。'],
  ['Uploaded file could not be read.', 'Fail yang dimuat naik tidak dapat dibaca.', '无法读取上传的文件。'],

  // What Xiaomei remembers: "Forget" and its Undo (XiaomeiMemoryService), and conversation summary (XiaomeiService)
  ['Xiaomei no longer remembers this.', 'Xiaomei tidak lagi mengingat perkara ini.', '小美已经不记得这一条了。'],
  ['The conversation has no turns yet.', 'Perbualan ini belum mempunyai giliran lagi.', '对话还没有任何内容。'],

  // AI answer reports, chat sessions and admin usage log (AiResponseReportService, AiLogService, AiService, AiPracticeCheckService)
  ['Message is required.', 'Mesej diperlukan.', '请填写消息内容。'],
  ['Topic is required.', 'Topik diperlukan.', '请填写主题。'],
  ['Target text is required.', 'Teks sasaran diperlukan.', '请填写目标文本。'],
  ['AI chat session was not found.', 'Sesi sembang AI tidak ditemui.', '找不到该 AI 聊天会话。'],
  ['AI chat message was not found.', 'Mesej sembang AI tidak ditemui.', '找不到该 AI 聊天消息。'],
  [
    'AI response snapshot is required when no server-side AI chat message can be resolved.',
    'Salinan jawapan AI diperlukan apabila mesej sembang AI tidak dapat dikenal pasti.',
    '无法找到对应的 AI 聊天消息时，需要提供 AI 回答内容。',
  ],
  ['AI response report was not found.', 'Laporan jawapan AI tidak ditemui.', '找不到该 AI 回答举报。'],
  ['This AI report is already closed.', 'Laporan AI ini sudah ditutup.', '该 AI 举报已关闭。'],
  ['Reviewer note is required.', 'Nota penyemak diperlukan.', '请填写审核备注。'],
  ['Invalid AI feature key.', 'Kunci ciri AI tidak sah.', 'AI 功能标识无效。'],
  ['Invalid AI feature filter.', 'Penapis ciri AI tidak sah.', 'AI 功能筛选条件无效。'],
  ['Invalid AI result filter.', 'Penapis keputusan AI tidak sah.', 'AI 结果筛选条件无效。'],
  ['Invalid AI report review status.', 'Status semakan laporan AI tidak sah.', 'AI 举报的审核状态无效。'],
  ['Invalid AI report reason category.', 'Kategori sebab laporan AI tidak sah.', 'AI 举报的原因类别无效。'],
]
