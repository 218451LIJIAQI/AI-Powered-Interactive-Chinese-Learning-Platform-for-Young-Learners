import type { MessageRow } from './types'

export const JOIN_CLASS_TO_OPEN_LESSONS_MESSAGE = 'Join this class to open its lessons.'

/** Backend messages of the classes module, translated like the rows in utils/serverMessages.ts. */
export const CLASSES_MESSAGES: readonly MessageRow[] = [
  // Classes and units (admin class authoring)
  [
    'Selected learning category does not exist.',
    'Kategori pembelajaran yang dipilih tidak wujud.',
    '所选学习分类不存在。',
  ],
  ['Class was not found.', 'Kelas tidak ditemui.', '找不到该课堂。'],
  ['Unit was not found.', 'Unit tidak ditemui.', '找不到该单元。'],
  ['Lesson was not found.', 'Pelajaran tidak ditemui.', '找不到该课时。'],
  ['Activity was not found.', 'Aktiviti tidak ditemui.', '找不到该活动。'],
  ['Title is required.', 'Tajuk diperlukan.', '请填写标题。'],
  ['A class has 2 units.', 'Sebuah kelas mempunyai 2 unit.', '一个课堂有 2 个单元。'],
  [
    'This unit belongs to multiple classes. Resolve its ownership before editing it.',
    'Unit ini tergolong dalam beberapa kelas. Selesaikan pemilikannya sebelum menyuntingnya.',
    '该单元属于多个课堂。请先确认其归属，再进行编辑。',
  ],
  ['Class has already been archived.', 'Kelas sudah diarkibkan.', '该课堂已归档。'],
  ['Restore the class before you edit it.', 'Pulihkan kelas ini sebelum menyuntingnya.', '请先恢复该课堂再编辑。'],
  [
    'Only an archived class can be restored.',
    'Hanya kelas yang diarkibkan boleh dipulihkan.',
    '只有已归档的课堂才能恢复。',
  ],
  ['Only a draft class can be published.', 'Hanya draf kelas boleh diterbitkan.', '只有草稿课堂才能发布。'],
  [
    'To publish a class, give it 2 units with 4 to 6 published lessons.',
    'Untuk menerbitkan kelas, berikan 2 unit dengan 4 hingga 6 pelajaran yang diterbitkan.',
    '要发布课堂，需要有 2 个单元，共 4 到 6 节已发布的课时。',
  ],
  [
    'Only a published class can be archived. Delete a draft class instead.',
    'Hanya kelas yang diterbitkan boleh diarkibkan. Padamkan draf kelas sebaliknya.',
    '只有已发布的课堂才能归档。草稿课堂请直接删除。',
  ],
  ['Only a draft class can be deleted.', 'Hanya draf kelas boleh dipadam.', '只能删除草稿课堂。'],
  ['Choose a unit of this class.', 'Pilih unit daripada kelas ini.', '请选择本课堂中的单元。'],
  ['Type must be CLASS or LESSON.', 'Jenis mestilah CLASS atau LESSON.', '类型必须为 CLASS 或 LESSON。'],
  ['Status filter is not supported.', 'Penapis status ini tidak disokong.', '不支持该状态筛选。'],
  ['Class title is required.', 'Tajuk kelas diperlukan.', '请填写课堂标题。'],
  ['Unit title is required.', 'Tajuk unit diperlukan.', '请填写单元标题。'],

  // Lessons and the lesson editor
  ['This lesson is already archived.', 'Pelajaran ini sudah diarkibkan.', '该课时已归档。'],
  [
    'Only an archived lesson can be restored.',
    'Hanya pelajaran yang diarkibkan boleh dipulihkan.',
    '只有已归档的课时才能恢复。',
  ],
  ['Restore the lesson before you edit it.', 'Pulihkan pelajaran ini sebelum menyuntingnya.', '请先恢复该课时再编辑。'],
  [
    'This lesson has no changes to discard.',
    'Pelajaran ini tiada perubahan untuk dibuang.',
    '该课时没有可放弃的更改。',
  ],
  [
    'There are no unpublished changes to publish.',
    'Tiada perubahan yang belum diterbitkan untuk diterbitkan.',
    '没有尚未发布的更改可以发布。',
  ],
  ['Lesson title is required.', 'Tajuk pelajaran diperlukan.', '请填写课时标题。'],
  [
    'Chinese is required for every key phrase.',
    'Teks Cina diperlukan untuk setiap frasa utama.',
    '每条重点短语都必须填写中文。',
  ],
  [
    'Pinyin is required for every key phrase.',
    'Pinyin diperlukan untuk setiap frasa utama.',
    '每条重点短语都必须填写拼音。',
  ],
  [
    'Meaning is required for every key phrase.',
    'Maksud diperlukan untuk setiap frasa utama.',
    '每条重点短语都必须填写释义。',
  ],
  [
    'A published class can have at most 6 published lessons.',
    'Kelas yang diterbitkan boleh mempunyai paling banyak 6 pelajaran yang diterbitkan.',
    '已发布的课堂最多只能有 6 节已发布的课时。',
  ],
  [
    'A published class needs at least one published lesson. Archive the class instead.',
    'Kelas yang diterbitkan memerlukan sekurang-kurangnya satu pelajaran yang diterbitkan. Arkibkan kelas itu sebaliknya.',
    '已发布的课堂至少需要保留一节已发布的课时。请直接归档该课堂。',
  ],
  ['Choose a video file to upload.', 'Pilih fail video untuk dimuat naik.', '请选择要上传的视频文件。'],
  // The publish checks of the lesson editor (its checklist names the missing parts).
  [
    'To publish, add a video, key phrases, all 3 activities and a 5-question quiz.',
    'Untuk menerbitkan, tambah video, frasa utama, kesemua 3 aktiviti dan kuiz 5 soalan.',
    '发布前请添加视频、重点短语、全部 3 个活动和一个 5 道题的测验。',
  ],
  [
    'Check the AI-drafted quiz questions before you publish: keep or edit each one.',
    'Semak soalan kuiz yang dirangka oleh AI sebelum menerbitkan: simpan atau sunting setiap satu.',
    '发布前请检查 AI 起草的测验题：逐题保留或修改。',
  ],
  [
    'Add pinyin to every new word before you publish.',
    'Tambah pinyin pada setiap perkataan baharu sebelum menerbitkan.',
    '发布前请为每个生词添加拼音。',
  ],

  // Lesson activities (the 3 slots of a lesson, and playing them)
  ["The activity type can't be changed.", 'Jenis aktiviti tidak boleh diubah.', '活动类型不能更改。'],
  [
    'This lesson already has an activity of this type.',
    'Pelajaran ini sudah mempunyai aktiviti jenis ini.',
    '该课时已经有这种类型的活动。',
  ],
  [
    'Add at least one character to trace.',
    'Tambah sekurang-kurangnya satu aksara untuk disurih.',
    '请至少添加一个要描红的汉字。',
  ],
  ['Enter the character to trace.', 'Masukkan aksara untuk disurih.', '请输入要描红的汉字。'],
  ['Pinyin is required for every character.', 'Pinyin diperlukan untuk setiap aksara.', '每个汉字都必须填写拼音。'],
  ['Enter one character per row.', 'Masukkan satu aksara bagi setiap baris.', '每行只能输入一个汉字。'],
  ['Chinese is required for every card.', 'Teks Cina diperlukan untuk setiap kad.', '每张闪卡都必须填写中文。'],
  ['Pinyin is required for every card.', 'Pinyin diperlukan untuk setiap kad.', '每张闪卡都必须填写拼音。'],
  ['Meaning is required for every card.', 'Maksud diperlukan untuk setiap kad.', '每张闪卡都必须填写释义。'],
  [
    'A matching activity needs at least 2 pairs.',
    'Aktiviti padanan memerlukan sekurang-kurangnya 2 pasangan.',
    '配对活动至少需要 2 组配对。',
  ],
  ['Chinese is required for every pair.', 'Teks Cina diperlukan untuk setiap pasangan.', '每组配对都必须填写中文。'],
  ['Pinyin is required for every pair.', 'Pinyin diperlukan untuk setiap pasangan.', '每组配对都必须填写拼音。'],
  ['Meaning is required for every pair.', 'Maksud diperlukan untuk setiap pasangan.', '每组配对都必须填写释义。'],
  [
    'Each Chinese word can only be used once.',
    'Setiap perkataan Cina hanya boleh digunakan sekali.',
    '每个中文词语只能使用一次。',
  ],
  ['Each meaning can only be used once.', 'Setiap maksud hanya boleh digunakan sekali.', '每个释义只能使用一次。'],
  ['Choose one meaning per word.', 'Pilih satu maksud untuk setiap perkataan.', '每个词只能选择一个释义。'],
  [
    'This activity has no learning material yet.',
    'Aktiviti ini belum mempunyai bahan pembelajaran.',
    '此活动尚无学习内容。',
  ],
  [
    'Activity type must be FLASHCARDS, TRACING or MATCHING.',
    'Jenis aktiviti mestilah FLASHCARDS, TRACING atau MATCHING.',
    '活动类型必须为 FLASHCARDS、TRACING 或 MATCHING。',
  ],
  ['Activity title is required.', 'Tajuk aktiviti diperlukan.', '请填写活动标题。'],
  [
    'Check your pairs to finish a matching activity.',
    'Semak pasangan anda untuk menamatkan aktiviti padanan.',
    '请检查配对以完成配对活动。',
  ],
  [
    'Pair the words of this activity only.',
    'Padankan perkataan dalam aktiviti ini sahaja.',
    '只能配对本活动中的词语。',
  ],
  ['Hints are not available in this activity.', 'Petunjuk tidak tersedia dalam aktiviti ini.', '该活动不提供提示。'],

  // Lesson quizzes
  ['Quiz was not found.', 'Kuiz tidak ditemui.', '找不到该测验。'],
  ['Quiz attempt was not found.', 'Cubaan kuiz tidak ditemui.', '找不到该测验作答记录。'],
  ['Requested quiz was not found.', 'Kuiz yang diminta tidak ditemui.', '找不到请求的测验。'],
  ['Requested quiz question was not found.', 'Soalan kuiz yang diminta tidak ditemui.', '找不到请求的测验题目。'],
  ['Quiz title is required.', 'Tajuk kuiz diperlukan.', '请填写测验标题。'],
  ['Question text is required.', 'Teks soalan diperlukan.', '请填写题目文本。'],
  ['Option text is required.', 'Teks pilihan diperlukan.', '请填写选项文本。'],
  [
    'A listen-and-choose question needs an audio link.',
    'Soalan dengar dan pilih memerlukan pautan audio.',
    '听音选择题需要提供音频链接。',
  ],
  [
    'A word and image question needs an image link.',
    'Soalan perkataan dan gambar memerlukan pautan imej.',
    '词图配对题需要提供图片链接。',
  ],
  [
    'A true-or-false question needs exactly 2 answer options.',
    'Soalan benar atau salah memerlukan tepat 2 pilihan jawapan.',
    '判断题必须正好有 2 个答案选项。',
  ],
  [
    'At least 2 answer options are required.',
    'Sekurang-kurangnya 2 pilihan jawapan diperlukan.',
    '至少需要 2 个答案选项。',
  ],
  [
    'Exactly one correct answer option is required.',
    'Mesti ada tepat satu pilihan jawapan yang betul.',
    '必须有且只有一个正确答案选项。',
  ],
  [
    'Correct answer option text is required.',
    'Teks pilihan jawapan yang betul diperlukan.',
    '请填写正确答案选项的文本。',
  ],
  [
    'Correct answer text is required for this question type.',
    'Teks jawapan betul diperlukan untuk jenis soalan ini.',
    '该题型需要填写正确答案。',
  ],
  ['Answer is required.', 'Jawapan diperlukan.', '请填写答案。'],
  [
    'Choose one of the listed options for every multiple-choice question.',
    'Pilih salah satu pilihan yang disenaraikan untuk setiap soalan pelbagai pilihan.',
    '每道单项选择题都请从所列选项中选择一个。',
  ],
  ['This question has no hint.', 'Soalan ini tiada petunjuk.', '这道题没有提示。'],
  ['Recorded video was not found.', 'Video rakaman tidak ditemui.', '找不到该录播视频。'],
  ['Recorded video file is required.', 'Fail video rakaman diperlukan.', '请上传录播视频文件。'],
  ['This lesson already has a quiz.', 'Pelajaran ini sudah mempunyai kuiz.', '该课时已经有一个测验。'],
  [
    'A lesson quiz has exactly 5 questions. Delete one to add another.',
    'Kuiz pelajaran mempunyai tepat 5 soalan. Padamkan satu untuk menambah yang lain.',
    '课时测验正好有 5 道题。请先删除一道再添加。',
  ],
  [
    'Quiz questions cannot be changed after learners have submitted attempts.',
    'Soalan kuiz tidak boleh diubah selepas pelajar menghantar cubaan.',
    '学习者提交作答后，测验题目不能再修改。',
  ],
  [
    'This quiz has changed. Reload the lesson before editing its questions.',
    'Kuiz ini telah berubah. Muat semula pelajaran sebelum menyunting soalannya.',
    '该测验已更改。请重新加载课时，再编辑题目。',
  ],
  [
    'Invalid question type. Allowed values are MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK, MATCH_WORD_MEANING, MATCH_WORD_IMAGE, LISTEN_CHOOSE, REARRANGE_SENTENCE, CHOOSE_PINYIN, CHOOSE_TONE, or CHARACTER_MEANING.',
    'Jenis soalan tidak sah. Nilai yang dibenarkan ialah MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK, MATCH_WORD_MEANING, MATCH_WORD_IMAGE, LISTEN_CHOOSE, REARRANGE_SENTENCE, CHOOSE_PINYIN, CHOOSE_TONE atau CHARACTER_MEANING.',
    '题型无效。允许的值为 MULTIPLE_CHOICE、TRUE_FALSE、FILL_BLANK、MATCH_WORD_MEANING、MATCH_WORD_IMAGE、LISTEN_CHOOSE、REARRANGE_SENTENCE、CHOOSE_PINYIN、CHOOSE_TONE 或 CHARACTER_MEANING。',
  ],
  ['This quiz has no questions yet.', 'Kuiz ini belum mempunyai soalan.', '该测验还没有题目。'],
  ['Hints are not available in this quiz.', 'Petunjuk tidak tersedia dalam kuiz ini.', '该测验不提供提示。'],
  ['This quiz attempt has not been submitted yet.', 'Cubaan kuiz ini belum dihantar.', '该测验作答尚未提交。'],
  ['This quiz attempt has already been submitted.', 'Cubaan kuiz ini sudah dihantar.', '该测验作答已提交。'],
  ['Answer every question before you submit.', 'Jawab setiap soalan sebelum menghantar.', '提交前请回答所有题目。'],
  ['Question does not belong to this quiz.', 'Soalan ini bukan daripada kuiz ini.', '该题目不属于这个测验。'],
  // The media link of a question: the backend builds the message with the word "Audio" or "Image".
  [
    'Audio link must be a full http:// or https:// web address without a user name or password.',
    'Pautan audio mestilah alamat web penuh yang bermula dengan http:// atau https://, tanpa nama pengguna atau kata laluan.',
    '音频链接必须是以 http:// 或 https:// 开头的完整网址，且不能包含用户名或密码。',
  ],
  [
    'Image link must be a full http:// or https:// web address without a user name or password.',
    'Pautan imej mestilah alamat web penuh yang bermula dengan http:// atau https://, tanpa nama pengguna atau kata laluan.',
    '图片链接必须是以 http:// 或 https:// 开头的完整网址，且不能包含用户名或密码。',
  ],

  // Joining, leaving and archived classes (learners)
  [
    JOIN_CLASS_TO_OPEN_LESSONS_MESSAGE,
    'Sertai kelas ini untuk membuka pelajarannya.',
    '加入这个课堂后才能打开其课时。',
  ],
  [
    'This class is archived. You can still open its lessons and your results.',
    'Kelas ini telah diarkibkan. Anda masih boleh membuka pelajarannya dan keputusan anda.',
    '该课堂已归档。你仍可以打开其课时并查看自己的成绩。',
  ],
  [
    "An archived class can't be left. You can still open its lessons and your results.",
    'Kelas yang diarkibkan tidak boleh ditinggalkan. Anda masih boleh membuka pelajarannya dan keputusan anda.',
    '已归档的课堂无法退出。你仍可以打开其课时并查看自己的成绩。',
  ],
]
