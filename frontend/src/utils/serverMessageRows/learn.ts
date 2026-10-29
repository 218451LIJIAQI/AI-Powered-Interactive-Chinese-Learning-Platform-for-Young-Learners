/**
 * Backend messages of the learn module, translated like the rows in utils/serverMessages.ts:
 * [English exactly as the backend sends it, Bahasa Melayu, Simplified Chinese].
 */
export const LEARN_MESSAGES: readonly (readonly [en: string, ms: string, zh: string])[] = [
  // Request parameters (LearnParams)
  ['Unknown practice type.', 'Jenis latihan tidak dikenali.', '未知的练习类型。'],
  ['Unknown stage.', 'Peringkat tidak dikenali.', '未知的阶段。'],
  ['Unknown mistake source.', 'Sumber kesilapan tidak dikenali.', '未知的错题来源。'],
  ['Month must look like 2026-05.', 'Bulan mesti dalam bentuk 2026-05.', '月份格式应为 2026-05。'],

  // Learn path and lesson player (LearnPathService)
  ['This unit is not on the learning path.', 'Unit ini tiada dalam laluan pembelajaran.', '这个单元不在学习路径上。'],
  [
    'This lesson is not on the learning path.',
    'Pelajaran ini tiada dalam laluan pembelajaran.',
    '这节课不在学习路径上。',
  ],
  [
    'This lesson is still locked. Finish the lessons before it first.',
    'Pelajaran ini masih dikunci. Selesaikan pelajaran sebelumnya dahulu.',
    '这节课仍未解锁。请先完成前面的课程。',
  ],
  ['This question is not part of the lesson.', 'Soalan ini bukan sebahagian daripada pelajaran.', '这道题不属于本课。'],
  ['Answers are required.', 'Jawapan diperlukan.', '请提交答案。'],
  ['Duration must not exceed one day.', 'Tempoh tidak boleh melebihi satu hari.', '用时不能超过一天。'],
  ['Answer must not exceed 300 characters.', 'Jawapan tidak boleh melebihi 300 aksara.', '答案不能超过 300 个字符。'],
  ['Too many strokes.', 'Terlalu banyak strok.', '笔画数太多。'],
  ['Strokes must not be negative.', 'Bilangan strok tidak boleh negatif.', '笔画数不能为负数。'],

  // Mistakes review (MistakeService)
  [
    'This mistake is not on your list any more.',
    'Kesilapan ini sudah tiada dalam senarai anda.',
    '这道错题已不在你的列表中。',
  ],
  [
    'Answer at least one mistake before you finish the review.',
    'Jawab sekurang-kurangnya satu kesilapan sebelum menamatkan ulang kaji.',
    '请至少回答一道错题再结束复习。',
  ],

  // Daily goal (XpService)
  [
    'Daily goal must be 10, 20 or 30 XP.',
    'Matlamat harian mestilah 10, 20 atau 30 XP.',
    '每日目标必须是 10、20 或 30 XP。',
  ],

  // Practice sets (PracticeService, PracticeCatalog)
  ['This practice set is not available.', 'Set latihan ini tidak tersedia.', '这个练习组不可用。'],
  ['Practice set not found.', 'Set latihan tidak ditemui.', '找不到该练习组。'],
  ['This item is not part of the set.', 'Item ini bukan sebahagian daripada set.', '这一项不属于该练习组。'],
  [
    'This activity has no answers to check.',
    'Aktiviti ini tiada jawapan untuk disemak.',
    '这个活动没有需要检查的答案。',
  ],
  ['Draw the character before you finish.', 'Tulis aksara itu sebelum anda selesai.', '请先写出这个汉字再完成。'],
  ['Item is required.', 'Item diperlukan.', '请选择一项。'],
  [
    'Add at least one item to this set.',
    'Tambah sekurang-kurangnya satu item pada set ini.',
    '请至少为这个练习组添加一项。',
  ],
  [
    'Every card needs Chinese and a meaning.',
    'Setiap kad memerlukan teks Cina dan maksud.',
    '每张卡片都需要中文和意思。',
  ],
  ['Every question needs the question text.', 'Setiap soalan memerlukan teks soalan.', '每道题都需要题目内容。'],
  [
    'Every question needs at least 2 answers with the right one ticked.',
    'Setiap soalan memerlukan sekurang-kurangnya 2 jawapan dengan jawapan betul ditanda.',
    '每道题至少需要 2 个答案，并勾选正确答案。',
  ],
  [
    'Every listening question needs the Chinese that is read aloud.',
    'Setiap soalan mendengar memerlukan teks Cina yang dibacakan.',
    '每道听力题都需要朗读的中文。',
  ],
  ['Add the characters to write.', 'Tambah aksara yang perlu ditulis.', '请添加要书写的汉字。'],
  ['Enter how many strokes the characters have.', 'Masukkan bilangan strok aksara itu.', '请输入这些汉字的笔画数。'],
  [
    'Every sentence needs at least 2 tiles and a meaning.',
    'Setiap ayat memerlukan sekurang-kurangnya 2 jubin dan maksud.',
    '每个句子至少需要 2 个词块和意思。',
  ],
  ['Add at least 2 pairs.', 'Tambah sekurang-kurangnya 2 pasangan.', '请至少添加 2 组配对。'],
  [
    'Every pair needs Chinese and a meaning.',
    'Setiap pasangan memerlukan teks Cina dan maksud.',
    '每组配对都需要中文和意思。',
  ],
  [
    'Each pair needs a different meaning.',
    'Setiap pasangan memerlukan maksud yang berbeza.',
    '每组配对的意思必须不同。',
  ],
  ['Add the story text.', 'Tambah teks cerita.', '请添加故事内容。'],
  [
    'Add at least one question about the story.',
    'Tambah sekurang-kurangnya satu soalan tentang cerita.',
    '请至少添加一道关于故事的问题。',
  ],

  // Practice library (PracticeLibraryService)
  ['This set is already published.', 'Set ini sudah diterbitkan.', '这个练习组已发布。'],
  [
    'Restore this set instead of publishing it.',
    'Pulihkan set ini dan bukan menerbitkannya.',
    '请恢复这个练习组，而不是发布它。',
  ],
  [
    'Only a published set can be unpublished.',
    'Hanya set yang diterbitkan boleh dinyahterbit.',
    '只有已发布的练习组才能取消发布。',
  ],
  [
    'Only a published set can be archived.',
    'Hanya set yang diterbitkan boleh diarkibkan.',
    '只有已发布的练习组才能归档。',
  ],
  [
    'Only an archived set can be restored.',
    'Hanya set yang diarkibkan boleh dipulihkan.',
    '只有已归档的练习组才能恢复。',
  ],
  ['XP must be between 0 and 100.', 'XP mestilah antara 0 dan 100.', 'XP 必须在 0 到 100 之间。'],
  ['A set has at most 30 items.', 'Satu set mempunyai paling banyak 30 item.', '一个练习组最多 30 项。'],
  ['A story has at most 20 lines.', 'Satu cerita mempunyai paling banyak 20 baris.', '一个故事最多 20 行。'],
  ['Strokes must be between 1 and 60.', 'Bilangan strok mestilah antara 1 dan 60.', '笔画数必须在 1 到 60 之间。'],
  ['Every story line needs Chinese text.', 'Setiap baris cerita memerlukan teks Cina.', '故事的每一行都需要中文。'],
  [
    'A story line must not exceed 300 characters.',
    'Baris cerita tidak boleh melebihi 300 aksara.',
    '故事的每一行不能超过 300 个字符。',
  ],
  [
    'Story pinyin must not exceed 500 characters.',
    'Pinyin cerita tidak boleh melebihi 500 aksara.',
    '故事拼音不能超过 500 个字符。',
  ],
  [
    'Story translation must not exceed 500 characters.',
    'Terjemahan cerita tidak boleh melebihi 500 aksara.',
    '故事翻译不能超过 500 个字符。',
  ],
  ['Meaning must not exceed 200 characters.', 'Maksud tidak boleh melebihi 200 aksara.', '意思不能超过 200 个字符。'],
  ['Example must not exceed 200 characters.', 'Contoh tidak boleh melebihi 200 aksara.', '例句不能超过 200 个字符。'],
  [
    'Example meaning must not exceed 300 characters.',
    'Maksud contoh tidak boleh melebihi 300 aksara.',
    '例句意思不能超过 300 个字符。',
  ],
  [
    'Extra tiles must not exceed 255 characters.',
    'Jubin tambahan tidak boleh melebihi 255 aksara.',
    '额外词块不能超过 255 个字符。',
  ],
  [
    'Explanation must not exceed 500 characters.',
    'Penerangan tidak boleh melebihi 500 aksara.',
    '解释不能超过 500 个字符。',
  ],
  ['A question has at most 6 answers.', 'Satu soalan mempunyai paling banyak 6 jawapan.', '一道题最多 6 个答案。'],
  [
    'An answer must not exceed 200 characters.',
    'Jawapan tidak boleh melebihi 200 aksara.',
    '答案不能超过 200 个字符。',
  ],
  ['Pinyin must not exceed 200 characters.', 'Pinyin tidak boleh melebihi 200 aksara.', '拼音不能超过 200 个字符。'],
  ['Tick the right answer.', 'Tandakan jawapan yang betul.', '请勾选正确答案。'],

  // Learning path admin and the level check (LearningPathAdminService, LevelCheckService)
  ['This unit is already published.', 'Unit ini sudah diterbitkan.', '这个单元已发布。'],
  ['This unit is already a draft.', 'Unit ini sudah menjadi draf.', '这个单元已是草稿。'],
  ['Level check question not found.', 'Soalan semakan tahap tidak ditemui.', '找不到该水平测试题。'],
  ['Tick exactly one right answer.', 'Tandakan tepat satu jawapan yang betul.', '请只勾选一个正确答案。'],
  ['Question is required.', 'Soalan diperlukan.', '请填写题目。'],
  ['Question must not exceed 300 characters.', 'Soalan tidak boleh melebihi 300 aksara.', '题目不能超过 300 个字符。'],
  [
    'Chinese text must not exceed 100 characters.',
    'Teks Cina tidak boleh melebihi 100 aksara.',
    '中文不能超过 100 个字符。',
  ],
  [
    'The Chinese read aloud must not exceed 200 characters.',
    'Teks Cina yang dibacakan tidak boleh melebihi 200 aksara.',
    '朗读的中文不能超过 200 个字符。',
  ],
  ['Skill is required.', 'Kemahiran diperlukan.', '请选择技能。'],
  ['Difficulty is required.', 'Tahap kesukaran diperlukan.', '请选择难度。'],
  ['Status is required.', 'Status diperlukan.', '请选择状态。'],
  ['Add at least 2 answers.', 'Tambah sekurang-kurangnya 2 jawapan.', '请至少添加 2 个答案。'],
  ['A question needs 2 to 6 answers.', 'Satu soalan memerlukan 2 hingga 6 jawapan.', '一道题需要 2 到 6 个答案。'],
  ['Every answer needs text.', 'Setiap jawapan memerlukan teks.', '每个答案都需要内容。'],
  [
    'Explanation must not exceed 300 characters.',
    'Penerangan tidak boleh melebihi 300 aksara.',
    '解释不能超过 300 个字符。',
  ],
  [
    'This question is not part of the level check.',
    'Soalan ini bukan sebahagian daripada semakan tahap.',
    '这道题不属于水平测试。',
  ],
  [
    'The level check has at most 6 questions.',
    'Semakan tahap mempunyai paling banyak 6 soalan.',
    '水平测试最多 6 道题。',
  ],
]
