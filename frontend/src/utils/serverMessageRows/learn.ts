import type { MessageRow } from './types'

export const RESTART_LEVEL_CHECK_MESSAGE = 'Start the level check again.'

/** Backend messages of the learn module, translated like the rows in utils/serverMessages.ts. */
export const LEARN_MESSAGES: readonly MessageRow[] = [
  // Request parameters (LearnParams)
  ['Unknown practice type.', 'Jenis latihan tidak dikenali.', '未知的练习类型。'],
  ['Unknown mistake source.', 'Sumber kesilapan tidak dikenali.', '未知的错题来源。'],
  ['Month must look like 2026-05.', 'Bulan mesti dalam bentuk 2026-05.', '月份格式应为 2026-05。'],

  // Learn path and lesson player (LearnPathService)
  ['This lesson has no questions yet.', 'Pelajaran ini belum mempunyai soalan.', '这节课还没有题目。'],
  ['This unit is not on the learning path.', 'Unit ini tiada dalam laluan pembelajaran.', '这个单元不在学习路径上。'],
  [
    'This lesson is not on the learning path.',
    'Pelajaran ini tiada dalam laluan pembelajaran.',
    '这节课不在学习路径上。',
  ],
  [
    'This lesson is still locked. Finish the lessons before it first.',
    'Pelajaran ini masih dikunci. Selesaikan pelajaran sebelumnya dahulu.',
    '这节课仍未解锁。请先完成前面的课时。',
  ],
  ['This question is not part of the lesson.', 'Soalan ini bukan sebahagian daripada pelajaran.', '这道题不属于本课。'],

  // Mistakes review (MistakeService)
  [
    'This mistake is not on your list any more.',
    'Kesilapan ini sudah tiada dalam senarai anda.',
    '这道错题已不在你的列表中。',
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
  [
    'Rate each card Got it or Still learning.',
    'Nilaikan setiap kad sebagai Sudah faham atau Masih belajar.',
    '请把每张卡片标为“记住了”或“还在学”。',
  ],
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
  [
    'Add at least 2 cards with different meanings.',
    'Tambah sekurang-kurangnya 2 kad dengan maksud yang berbeza.',
    '请至少添加 2 张意思不同的卡片。',
  ],
  [
    'Every question needs at least 2 answers with the right one ticked.',
    'Setiap soalan memerlukan sekurang-kurangnya 2 jawapan dengan jawapan betul ditanda.',
    '每道题至少需要 2 个答案，并勾选正确答案。',
  ],
  [
    'The answers of a question must all be different.',
    'Jawapan bagi sesuatu soalan mestilah berbeza antara satu sama lain.',
    '同一道题的答案不能重复。',
  ],
  [
    'Every listening question needs the Chinese that is read aloud.',
    'Setiap soalan mendengar memerlukan teks Cina yang dibacakan.',
    '每道听力题都需要朗读的中文。',
  ],
  ['Add the characters to write.', 'Tambah aksara yang perlu ditulis.', '请添加要书写的汉字。'],
  ['Enter how many strokes the characters have.', 'Masukkan bilangan strok aksara itu.', '请输入这些汉字的笔画数。'],
  ['Strokes must be between 1 and 60.', 'Bilangan strok mestilah antara 1 hingga 60.', '笔画数必须在 1 到 60 之间。'],
  ['Each character needs 1 to 60 strokes.', 'Setiap aksara memerlukan 1 hingga 60 strok.', '每个汉字需要 1 到 60 笔。'],
  [
    'Total strokes must be between 1 and 600.',
    'Jumlah strok mestilah antara 1 hingga 600.',
    '总笔画数必须在 1 到 600 之间。',
  ],
  [
    'Give the strokes of each character, adding up to the total.',
    'Berikan bilangan strok setiap aksara, yang jumlahnya sama dengan jumlah keseluruhan.',
    '请填写每个汉字的笔画数，加起来要等于总笔画数。',
  ],
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
  [
    'Each pair needs different Chinese.',
    'Setiap pasangan memerlukan teks Cina yang berbeza.',
    '每组配对的中文必须不同。',
  ],
  [
    'Put the characters of a tracing set in one row.',
    'Masukkan semua aksara bagi set menyurih dalam satu baris.',
    '请把同一组描红练习的汉字放在一行。',
  ],
  [
    'A tracing set has at most 10 characters.',
    'Set menyurih mempunyai paling banyak 10 aksara.',
    '描红练习组最多 10 个汉字。',
  ],

  // Practice library (PracticeLibraryService)
  ['This set is already published.', 'Set ini sudah diterbitkan.', '这个练习组已发布。'],
  ['Restore this set before you edit it.', 'Pulihkan set ini sebelum anda mengeditnya.', '编辑前请先恢复这个练习组。'],
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

  // Learning path admin and the level check (LearningPathAdminService, LevelCheckService)
  ['This unit is already published.', 'Unit ini sudah diterbitkan.', '这个单元已发布。'],
  ['This unit is already a draft.', 'Unit ini sudah menjadi draf.', '这个单元已是草稿。'],
  ['Level check question not found.', 'Soalan semakan tahap tidak ditemui.', '找不到该水平测试题。'],
  ['This question is already published.', 'Soalan ini sudah diterbitkan.', '这道题已发布。'],
  ['Question is required.', 'Soalan diperlukan.', '请填写题目。'],
  ['A question needs 2 to 6 answers.', 'Setiap soalan memerlukan 2 hingga 6 jawapan.', '每道题需要 2 至 6 个答案。'],
  ['Every answer needs text.', 'Setiap jawapan memerlukan teks.', '每个答案都需要文字。'],
  ['Tick exactly one right answer.', 'Tandakan tepat satu jawapan yang betul.', '请只勾选一个正确答案。'],
  [
    'This question is not part of the level check.',
    'Soalan ini bukan sebahagian daripada semakan tahap.',
    '这道题不属于水平测试。',
  ],
  [
    'Answer each level-check skill only once.',
    'Jawab setiap kemahiran semakan tahap sekali sahaja.',
    '水平测试的每项技能只能作答一次。',
  ],
  ['The level check is already complete.', 'Semakan tahap sudah selesai.', '水平测试已完成。'],
  [RESTART_LEVEL_CHECK_MESSAGE, 'Mulakan semakan tahap semula.', '请重新开始水平测试。'],

  // Hints and pinyin typing (PracticeService, HintPolicyService)
  ['Checkpoints have no hints.', 'Titik semak tiada petunjuk.', '关卡没有提示。'],
  ['Hints are off in your settings.', 'Petunjuk dimatikan dalam tetapan anda.', '你已在设置中关闭了提示。'],
  [
    'Answer or skip every question before you finish the lesson.',
    'Jawab atau langkau setiap soalan sebelum anda menamatkan pelajaran.',
    '请先回答或跳过每一道题，再完成本课。',
  ],
  [
    'Answer or skip every item before you finish the set.',
    'Jawab atau langkau setiap soalan sebelum menamatkan set latihan.',
    '请先回答或跳过每一道题，再完成这组练习。',
  ],
  ['There is no further hint for this item.', 'Tiada petunjuk lagi untuk item ini.', '这道题没有更多提示了。'],
  ['There are no hints left in this set.', 'Tiada petunjuk lagi dalam set ini.', '这组练习的提示已经用完了。'],
  [
    'Open the questions again to get a hint.',
    'Buka semula soalan untuk mendapatkan petunjuk.',
    '请重新打开题目再获取提示。',
  ],
  [
    'Only typing sets offer words to choose from.',
    'Hanya set menaip menawarkan perkataan untuk dipilih.',
    '只有打字练习组会提供候选词。',
  ],
  [
    'Add one rule for each level: HSK 1, HSK 2 and HSK 3.',
    'Tambah satu peraturan untuk setiap tahap: HSK 1, HSK 2 dan HSK 3.',
    '请为每个水平各添加一条规则：HSK 1、HSK 2 和 HSK 3。',
  ],
  [
    'Every word needs Chinese, pinyin and a meaning.',
    'Setiap perkataan memerlukan tulisan Cina, pinyin dan maksud.',
    '每个词都需要中文、拼音和意思。',
  ],
  [
    'Write the pinyin in letters, for example xièxie.',
    'Tulis pinyin dengan huruf, contohnya xièxie.',
    '请用字母写拼音，例如 xièxie。',
  ],
  [
    'Every word needs at least 2 look-alike words that are not the answer.',
    'Setiap perkataan memerlukan sekurang-kurangnya 2 perkataan serupa yang bukan jawapannya.',
    '每个词至少需要 2 个不是答案的相似词。',
  ],
]
