/**
 * Backend messages of the classes module, translated like the rows in utils/serverMessages.ts:
 * [English exactly as the backend sends it, Bahasa Melayu, Simplified Chinese].
 */
export const CLASSES_MESSAGES: readonly (readonly [en: string, ms: string, zh: string])[] = [
  // Classes, units and their review
  ['Class was not found.', 'Kelas tidak ditemui.', '找不到该课堂。'],
  ['Unit was not found.', 'Unit tidak ditemui.', '找不到该单元。'],
  ['Class has already been archived.', 'Kelas sudah diarkibkan.', '该课堂已归档。'],
  [
    'Class cannot be archived while it is under review.',
    'Kelas tidak boleh diarkibkan semasa disemak.',
    '课堂在审核期间不能归档。',
  ],
  [
    "This class is in review. You can edit it again after the admin's decision.",
    'Kelas ini sedang disemak. Anda boleh menyuntingnya semula selepas keputusan pentadbir.',
    '该课堂正在审核中。管理员作出决定后才能再次编辑。',
  ],
  ['Restore the class before you edit it.', 'Pulihkan kelas ini sebelum menyuntingnya.', '请先恢复该课堂再编辑。'],
  [
    'A new class is reviewed together with the first lesson you submit.',
    'Kelas baharu disemak bersama pelajaran pertama yang anda hantar.',
    '新课堂会与你提交的第一节课时一起审核。',
  ],
  ['There are no saved changes to submit.', 'Tiada perubahan tersimpan untuk dihantar.', '没有可提交的已保存更改。'],
  [
    'Only an archived class can be restored.',
    'Hanya kelas yang diarkibkan boleh dipulihkan.',
    '只有已归档的课堂才能恢复。',
  ],
  [
    'Only a draft class that students never saw can be deleted.',
    'Hanya draf kelas yang belum pernah dilihat pelajar boleh dipadam.',
    '只能删除学生从未看到过的草稿课堂。',
  ],
  [
    'Archive or move the lessons of this unit first.',
    'Arkibkan atau pindahkan pelajaran dalam unit ini dahulu.',
    '请先归档或移走该单元中的课时。',
  ],
  [
    'The new order must list every unit and lesson of the class once.',
    'Susunan baharu mesti menyenaraikan setiap unit dan pelajaran kelas ini sekali sahaja.',
    '新的顺序必须将课堂中的每个单元和课时各列出一次。',
  ],
  ['This unit is archived.', 'Unit ini telah diarkibkan.', '该单元已归档。'],
  ['Choose a unit of this class.', 'Pilih unit daripada kelas ini.', '请选择本课堂中的单元。'],
  [
    'Learning type must be RECORDED, LIVE or HYBRID.',
    'Jenis pembelajaran mestilah RECORDED, LIVE atau HYBRID.',
    '学习方式必须为 RECORDED、LIVE 或 HYBRID。',
  ],
  ['Sort must be RECENT or TITLE.', 'Susunan mestilah RECENT atau TITLE.', '排序方式必须为 RECENT 或 TITLE。'],
  [
    'Review type must be ALL, CLASS, LESSON or QUIZ.',
    'Jenis semakan mestilah ALL, CLASS, LESSON atau QUIZ.',
    '审核类型必须为 ALL、CLASS、LESSON 或 QUIZ。',
  ],
  [
    'Nothing of this is waiting for review.',
    'Tiada apa-apa daripada ini yang menunggu semakan.',
    '这里没有待审核的内容。',
  ],
  ['Type must be CLASS or LESSON.', 'Jenis mestilah CLASS atau LESSON.', '类型必须为 CLASS 或 LESSON。'],
  ['Status filter is not supported.', 'Penapis status ini tidak disokong.', '不支持该状态筛选。'],
  ['Class title is required.', 'Tajuk kelas diperlukan.', '请填写课堂标题。'],
  [
    'Class title must be 200 characters or fewer.',
    'Tajuk kelas mestilah 200 aksara atau kurang.',
    '课堂标题不能超过 200 个字符。',
  ],
  [
    'Description must be 2000 characters or fewer.',
    'Penerangan mestilah 2000 aksara atau kurang.',
    '描述不能超过 2000 个字符。',
  ],
  ['Topic id must be positive.', 'ID topik mestilah nombor positif.', '主题 ID 必须为正数。'],
  ['The note must be 500 characters or fewer.', 'Nota mestilah 500 aksara atau kurang.', '备注不能超过 500 个字符。'],
  ['Unit title is required.', 'Tajuk unit diperlukan.', '请填写单元标题。'],
  [
    'Unit title must be 200 characters or fewer.',
    'Tajuk unit mestilah 200 aksara atau kurang.',
    '单元标题不能超过 200 个字符。',
  ],
  ['Units are required.', 'Unit diperlukan.', '请提供单元。'],
  [
    'A class cannot have more than 100 units.',
    'Kelas tidak boleh mempunyai lebih daripada 100 unit.',
    '一个课堂最多只能有 100 个单元。',
  ],
  ['Unit must not be null.', 'Unit tidak boleh kosong.', '单元不能为空。'],
  ['Unit id is required.', 'ID unit diperlukan.', '请提供单元 ID。'],
  ['Unit id must be positive.', 'ID unit mestilah nombor positif.', '单元 ID 必须为正数。'],
  [
    'A unit cannot have more than 200 lessons.',
    'Unit tidak boleh mempunyai lebih daripada 200 pelajaran.',
    '一个单元最多只能有 200 节课时。',
  ],
  ['Class id is required.', 'ID kelas diperlukan.', '请提供课堂 ID。'],
  ['Class id must be positive.', 'ID kelas mestilah nombor positif.', '课堂 ID 必须为正数。'],

  // Lessons and the lesson editor
  ['This lesson is already archived.', 'Pelajaran ini sudah diarkibkan.', '该课时已归档。'],
  [
    'Only an archived lesson can be restored.',
    'Hanya pelajaran yang diarkibkan boleh dipulihkan.',
    '只有已归档的课时才能恢复。',
  ],
  [
    "This lesson is in review. You can edit it again after the admin's decision.",
    'Pelajaran ini sedang disemak. Anda boleh menyuntingnya semula selepas keputusan pentadbir.',
    '该课时正在审核中。管理员作出决定后才能再次编辑。',
  ],
  ['Restore the lesson before you edit it.', 'Pulihkan pelajaran ini sebelum menyuntingnya.', '请先恢复该课时再编辑。'],
  [
    'This lesson has no changes to discard.',
    'Pelajaran ini tiada perubahan untuk dibuang.',
    '该课时没有可放弃的更改。',
  ],
  ['Lesson title is required.', 'Tajuk pelajaran diperlukan.', '请填写课时标题。'],
  [
    'Lesson title must be 200 characters or fewer.',
    'Tajuk pelajaran mestilah 200 aksara atau kurang.',
    '课时标题不能超过 200 个字符。',
  ],
  ['Lesson id must not be null.', 'ID pelajaran tidak boleh kosong.', '课时 ID 不能为空。'],
  ['Minutes must be between 1 and 240.', 'Minit mestilah antara 1 hingga 240.', '分钟数必须在 1 到 240 之间。'],
  [
    'Summary must be 1000 characters or fewer.',
    'Ringkasan mestilah 1000 aksara atau kurang.',
    '摘要不能超过 1000 个字符。',
  ],
  [
    'A lesson can have at most 30 key phrases.',
    'Pelajaran boleh mempunyai paling banyak 30 frasa utama.',
    '每节课时最多只能有 30 个重点短语。',
  ],
  ['Key phrase must not be null.', 'Frasa utama tidak boleh kosong.', '重点短语不能为空。'],
  [
    'Chinese is required for every key phrase.',
    'Bahasa Cina diperlukan untuk setiap frasa utama.',
    '每个重点短语都需要填写中文。',
  ],
  [
    'Chinese must be 100 characters or fewer.',
    'Bahasa Cina mestilah 100 aksara atau kurang.',
    '中文不能超过 100 个字符。',
  ],
  [
    'Pinyin is required for every key phrase.',
    'Pinyin diperlukan untuk setiap frasa utama.',
    '每个重点短语都需要填写拼音。',
  ],
  ['Pinyin must be 200 characters or fewer.', 'Pinyin mestilah 200 aksara atau kurang.', '拼音不能超过 200 个字符。'],
  [
    'Meaning is required for every key phrase.',
    'Maksud diperlukan untuk setiap frasa utama.',
    '每个重点短语都需要填写释义。',
  ],
  ['Meaning must be 300 characters or fewer.', 'Maksud mestilah 300 aksara atau kurang.', '释义不能超过 300 个字符。'],
  ['Choose a video file to upload.', 'Pilih fail video untuk dimuat naik.', '请选择要上传的视频文件。'],
  [
    'Add at least one practice activity before you submit.',
    'Tambah sekurang-kurangnya satu aktiviti latihan sebelum menghantar.',
    '提交前请至少添加一个练习活动。',
  ],
  [
    'Add a quiz with at least 3 questions, each with a correct answer, before you submit.',
    'Tambah kuiz dengan sekurang-kurangnya 3 soalan, setiap satu dengan jawapan yang betul, sebelum menghantar.',
    '提交前请添加至少含 3 道题且每题都有正确答案的测验。',
  ],
  [
    'Add pinyin to every new word before you submit.',
    'Tambah pinyin pada setiap perkataan baharu sebelum menghantar.',
    '提交前请为每个生词添加拼音。',
  ],

  // Lesson activities
  ["The activity type can't be changed.", 'Jenis aktiviti tidak boleh diubah.', '活动类型不能更改。'],
  [
    'The new order must list every activity of the lesson once.',
    'Susunan baharu mesti menyenaraikan setiap aktiviti pelajaran ini sekali sahaja.',
    '新的顺序必须将该课时的每个活动各列出一次。',
  ],
  [
    'Add at least one character to trace.',
    'Tambah sekurang-kurangnya satu aksara untuk disurih.',
    '请至少添加一个要描写的汉字。',
  ],
  ['Enter one character per row.', 'Masukkan satu aksara bagi setiap baris.', '每行只能输入一个汉字。'],
  ['Enter the character to trace.', 'Masukkan aksara untuk disurih.', '请输入要描写的汉字。'],
  ['Pinyin is required for every character.', 'Pinyin diperlukan untuk setiap aksara.', '每个汉字都需要填写拼音。'],
  ['Pinyin must be 40 characters or fewer.', 'Pinyin mestilah 40 aksara atau kurang.', '拼音不能超过 40 个字符。'],
  ['Meaning must be 80 characters or fewer.', 'Maksud mestilah 80 aksara atau kurang.', '释义不能超过 80 个字符。'],
  [
    'A matching activity needs at least 2 pairs.',
    'Aktiviti padanan memerlukan sekurang-kurangnya 2 pasangan.',
    '配对活动至少需要 2 组配对。',
  ],
  ['Each meaning can only be used once.', 'Setiap maksud hanya boleh digunakan sekali.', '每个释义只能使用一次。'],
  ['Chinese is required for every pair.', 'Bahasa Cina diperlukan untuk setiap pasangan.', '每组配对都需要填写中文。'],
  ['Pinyin is required for every pair.', 'Pinyin diperlukan untuk setiap pasangan.', '每组配对都需要填写拼音。'],
  ['Meaning is required for every pair.', 'Maksud diperlukan untuk setiap pasangan.', '每组配对都需要填写释义。'],
  [
    'Activity type must be FLASHCARDS, TRACING or MATCHING.',
    'Jenis aktiviti mestilah FLASHCARDS, TRACING atau MATCHING.',
    '活动类型必须为 FLASHCARDS、TRACING 或 MATCHING。',
  ],
  ['Activity type is required.', 'Jenis aktiviti diperlukan.', '请选择活动类型。'],
  [
    'Activity type must be 20 characters or fewer.',
    'Jenis aktiviti mestilah 20 aksara atau kurang.',
    '活动类型不能超过 20 个字符。',
  ],
  ['Activity title is required.', 'Tajuk aktiviti diperlukan.', '请填写活动标题。'],
  [
    'Activity title must be 200 characters or fewer.',
    'Tajuk aktiviti mestilah 200 aksara atau kurang.',
    '活动标题不能超过 200 个字符。',
  ],
  [
    'An activity can have at most 8 characters to trace.',
    'Aktiviti boleh mempunyai paling banyak 8 aksara untuk disurih.',
    '每个活动最多只能有 8 个描写汉字。',
  ],
  ['Character must not be null.', 'Aksara tidak boleh kosong.', '汉字不能为空。'],
  [
    'A matching activity can have at most 12 pairs.',
    'Aktiviti padanan boleh mempunyai paling banyak 12 pasangan.',
    '配对活动最多只能有 12 组配对。',
  ],
  ['Pair must not be null.', 'Pasangan tidak boleh kosong.', '配对不能为空。'],
  ['Activity id is required.', 'ID aktiviti diperlukan.', '请提供活动 ID。'],
  ['Activities are required.', 'Aktiviti diperlukan.', '请提供活动。'],
  ['Too many activities.', 'Terlalu banyak aktiviti.', '活动数量过多。'],
  ['Activity must not be null.', 'Aktiviti tidak boleh kosong.', '活动不能为空。'],
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
  ['Pairs are required.', 'Pasangan diperlukan.', '请提供配对。'],
  ['Too many pairs.', 'Terlalu banyak pasangan.', '配对数量过多。'],
  ['Word id is required.', 'ID perkataan diperlukan.', '请提供词语 ID。'],

  // Lesson quizzes
  [
    'The new order must list every question of the quiz once.',
    'Susunan baharu mesti menyenaraikan setiap soalan kuiz ini sekali sahaja.',
    '新的顺序必须将测验中的每道题各列出一次。',
  ],
  ['Questions are required.', 'Soalan diperlukan.', '请提供题目。'],
  ['Too many questions.', 'Terlalu banyak soalan.', '题目数量过多。'],
  ['Question id must not be null.', 'ID soalan tidak boleh kosong.', '题目 ID 不能为空。'],
  ['This quiz has no questions yet.', 'Kuiz ini belum mempunyai soalan.', '该测验还没有题目。'],
  ['Hints are not available in this quiz.', 'Petunjuk tidak tersedia dalam kuiz ini.', '该测验不提供提示。'],
  ['This quiz attempt has not been submitted yet.', 'Cubaan kuiz ini belum dihantar.', '该测验作答尚未提交。'],
  ['This quiz attempt has already been submitted.', 'Cubaan kuiz ini sudah dihantar.', '该测验作答已提交。'],
  [
    'Time is up. Your quiz was submitted with the answers you saved.',
    'Masa sudah tamat. Kuiz anda telah dihantar dengan jawapan yang disimpan.',
    '时间到了。测验已按你保存的答案提交。',
  ],
  ['Question does not belong to this quiz.', 'Soalan ini bukan daripada kuiz ini.', '该题目不属于这个测验。'],

  // Joining and students
  [
    'Join this class to open its lessons.',
    'Sertai kelas ini untuk membuka pelajarannya.',
    '加入这个课堂后才能打开其课时。',
  ],
  [
    'This class is not open for new students.',
    'Kelas ini tidak dibuka kepada pelajar baharu.',
    '这个课堂不接受新学生加入。',
  ],
  ['Student was not found in your classes.', 'Pelajar tidak ditemui dalam kelas anda.', '在你的课堂中找不到该学生。'],

  // Feedback
  ['This student has not joined the class.', 'Pelajar ini belum menyertai kelas ini.', '该学生尚未加入这个课堂。'],
  [
    'Choose a quiz attempt of this student in this class.',
    'Pilih cubaan kuiz pelajar ini dalam kelas ini.',
    '请选择该学生在本课堂中的测验作答记录。',
  ],
  ['Choose a lesson of this class.', 'Pilih pelajaran daripada kelas ini.', '请选择本课堂中的课时。'],
  [
    'Feedback type must be ENCOURAGING, LESSON, WEAK_AREA, CORRECTIVE or PROGRESS.',
    'Jenis maklum balas mestilah ENCOURAGING, LESSON, WEAK_AREA, CORRECTIVE atau PROGRESS.',
    '反馈类型必须为 ENCOURAGING、LESSON、WEAK_AREA、CORRECTIVE 或 PROGRESS。',
  ],
  ['Feedback type is required.', 'Jenis maklum balas diperlukan.', '请选择反馈类型。'],
  [
    'Feedback type must be 50 characters or fewer.',
    'Jenis maklum balas mestilah 50 aksara atau kurang.',
    '反馈类型不能超过 50 个字符。',
  ],
  [
    'Feedback must be between 5 and 2,000 characters.',
    'Maklum balas mestilah antara 5 hingga 2,000 aksara.',
    '反馈内容必须为 5 到 2,000 个字符。',
  ],

  // Live sessions
  ['Live session was not found.', 'Sesi langsung tidak ditemui.', '找不到该直播课。'],
  [
    'Live sessions can only be scheduled for published classes.',
    'Sesi langsung hanya boleh dijadualkan untuk kelas yang diterbitkan.',
    '只能为已发布的课堂安排直播课。',
  ],
  [
    "A cancelled or past session can't be edited.",
    'Sesi yang dibatalkan atau telah berlalu tidak boleh disunting.',
    '已取消或已结束的直播课不能编辑。',
  ],
  [
    "Capacity can't be lower than the seats already booked.",
    'Kapasiti tidak boleh kurang daripada tempat yang sudah ditempah.',
    '名额不能少于已预约的人数。',
  ],
  ["A past session can't be cancelled.", 'Sesi yang telah berlalu tidak boleh dibatalkan.', '已结束的直播课不能取消。'],
  [
    'A recording can be added once the session is over.',
    'Rakaman boleh ditambah selepas sesi tamat.',
    '直播课结束后才能添加录像。',
  ],
  ['This live session was cancelled.', 'Sesi langsung ini telah dibatalkan.', '该直播课已取消。'],
  ['This live session has already ended.', 'Sesi langsung ini sudah tamat.', '该直播课已结束。'],
  ['You already booked this live session.', 'Anda sudah menempah sesi langsung ini.', '你已预约这节直播课。'],
  ['This live session is full.', 'Sesi langsung ini sudah penuh.', '该直播课名额已满。'],
  [
    'Only a booking for a session that has not started can be cancelled.',
    'Hanya tempahan untuk sesi yang belum bermula boleh dibatalkan.',
    '只能取消尚未开始的直播课预约。',
  ],
  [
    'Book a seat to join this live session.',
    'Tempah tempat untuk menyertai sesi langsung ini.',
    '请先预约名额再加入这节直播课。',
  ],
  [
    'You can join from 15 minutes before the start.',
    'Anda boleh menyertai mulai 15 minit sebelum sesi bermula.',
    '开始前 15 分钟起可以加入。',
  ],
  ['Choose a class for the session.', 'Pilih kelas untuk sesi ini.', '请为这节直播课选择课堂。'],
  ['Capacity must be between 1 and 500.', 'Kapasiti mestilah antara 1 hingga 500.', '名额必须在 1 到 500 之间。'],
  [
    'Meeting platform must be 40 characters or fewer.',
    'Platform mesyuarat mestilah 40 aksara atau kurang.',
    '会议平台不能超过 40 个字符。',
  ],
  ['Recording link is required.', 'Pautan rakaman diperlukan.', '请填写录像链接。'],
  [
    'Recording link must be 500 characters or fewer.',
    'Pautan rakaman mestilah 500 aksara atau kurang.',
    '录像链接不能超过 500 个字符。',
  ],
  [
    'Recording link must be a valid http:// or https:// link.',
    'Pautan rakaman mestilah pautan http:// atau https:// yang sah.',
    '录像链接必须是有效的 http:// 或 https:// 链接。',
  ],
]
