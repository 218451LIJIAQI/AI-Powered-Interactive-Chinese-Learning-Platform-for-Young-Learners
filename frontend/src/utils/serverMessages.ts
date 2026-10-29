/**
 * Localised versions of the backend error and notice messages a user can see (FRLANG02).
 *
 * The backend answers in English (FriendlyErrorMessageResolver + service messages). When the interface
 * language is Malay or Simplified Chinese, a known message is replaced by its translation; an unknown
 * message is shown unchanged, so the backend text always remains the fallback.
 *
 * The catalogue has two parts, looked up in this order:
 * - fixed messages: GENERAL_MESSAGES, REQUEST_MESSAGES (reasons of failed requests) and VALIDATION_MESSAGES (Bean
 *   Validation rules, shown as `fieldErrors` or as an activity request's message);
 * - MESSAGE_PATTERNS: messages the backend builds from parts, such as "{content} was not found.". A fixed word in a
 *   placeholder (a content label, a status) is translated too; user content (a value the user typed) is kept as sent.
 *   A fixed message that a pattern already translates the same way is not listed again as a row.
 * Matching ignores case, repeated spaces and a final full stop. serverMessages.test.ts reads the backend sources: it
 * checks that the messages of ResponseStatusException and IllegalArgumentException throws and of Bean Validation
 * annotations are translated (messages passed through EnumLookups.fromValue are not detected), and that no row is
 * left for a message the backend no longer sends.
 */
import type { AppLanguage } from '@/types/api'
import { documentLanguage } from '@/utils/locale'

import { AI_MESSAGES } from './serverMessageRows/ai'
import { CLASSES_MESSAGES } from './serverMessageRows/classes'
import { LEARN_MESSAGES } from './serverMessageRows/learn'
import { OPS_MESSAGES } from './serverMessageRows/ops'
import { PLAN_MESSAGES } from './serverMessageRows/plan'

type TranslatedLocale = Exclude<AppLanguage, 'en'>

/** One fixed backend message: [English as the backend sends it, Bahasa Melayu, Simplified Chinese]. */
type MessageRow = readonly [en: string, ms: string, zh: string]

/**
 * The generic answers of FriendlyErrorMessageResolver and the sign-in, password, live class and AI limit messages a
 * user meets most.
 */
const GENERAL_MESSAGES: readonly MessageRow[] = [
  ['Invalid email or password.', 'E-mel atau kata laluan tidak sah.', '邮箱或密码不正确。'],
  [
    'This account is currently unavailable. Please contact the platform administrator.',
    'Akaun ini tidak tersedia buat masa ini. Sila hubungi pentadbir platform.',
    '此账号目前无法使用，请联系平台管理员。',
  ],
  ['Email is already registered.', 'E-mel ini sudah didaftarkan.', '该邮箱已注册。'],
  ['Confirm password does not match.', 'Pengesahan kata laluan tidak sepadan.', '两次输入的密码不一致。'],
  [
    'Password must be between 8 and 72 characters.',
    'Kata laluan mestilah antara 8 hingga 72 aksara.',
    '密码长度必须为 8 到 72 个字符。',
  ],
  ['Current password is incorrect.', 'Kata laluan semasa tidak betul.', '当前密码不正确。'],
  [
    'New password must be different from the current password.',
    'Kata laluan baharu mestilah berbeza daripada kata laluan semasa.',
    '新密码必须与当前密码不同。',
  ],
  [
    'Reset token has expired. Please request a new one.',
    'Pautan tetapan semula telah tamat tempoh. Sila minta pautan baharu.',
    '重置链接已过期，请重新申请。',
  ],
  [
    'Reset token has already been used.',
    'Pautan tetapan semula tidak sah atau telah digunakan.',
    '重置链接无效或已被使用。',
  ],
  [
    'Reset token is invalid or has already been used.',
    'Pautan tetapan semula tidak sah atau telah digunakan.',
    '重置链接无效或已被使用。',
  ],
  ['Reset token is required', 'Pautan tetapan semula diperlukan.', '缺少重置链接。'],
  [
    'Only approved teachers can create or submit learning content.',
    'Hanya guru yang diluluskan boleh menggunakan fungsi pengajaran ini.',
    '只有通过审核的教师才能使用此教学功能。',
  ],
  [
    'Only approved teachers can use this teaching feature.',
    'Hanya guru yang diluluskan boleh menggunakan fungsi pengajaran ini.',
    '只有通过审核的教师才能使用此教学功能。',
  ],
  [
    'Hints are not available for independent practice.',
    'Petunjuk tidak tersedia untuk latihan berdikari.',
    '独立练习不提供提示。',
  ],
  [
    'Your session has expired. Please sign in again to continue.',
    'Sesi anda telah tamat. Sila log masuk semula untuk meneruskan.',
    '登录已过期，请重新登录后继续。',
  ],
  ['Please sign in to continue.', 'Sila log masuk untuk meneruskan.', '请先登录后继续。'],
  [
    'You do not have permission to perform this action. Contact an administrator if needed.',
    'Anda tiada kebenaran untuk melakukan tindakan ini. Hubungi pentadbir jika perlu.',
    '你无权执行此操作，如有需要请联系管理员。',
  ],
  [
    'We could not find the item you requested. Please refresh and try again.',
    'Item yang anda minta tidak ditemui. Sila muat semula dan cuba lagi.',
    '找不到你请求的内容，请刷新后重试。',
  ],
  [
    'The information you entered is incomplete or invalid. Please check it and try again.',
    'Maklumat yang dimasukkan tidak lengkap atau tidak sah. Sila semak dan cuba lagi.',
    '输入的信息不完整或无效，请检查后重试。',
  ],
  [
    'The request could not be processed. Please check the input and try again.',
    'Permintaan tidak dapat diproses. Sila semak input dan cuba lagi.',
    '无法处理该请求，请检查输入后重试。',
  ],
  [
    'The file upload did not work. Please check the file type and size, then try again.',
    'Muat naik fail tidak berjaya. Sila semak jenis dan saiz fail, kemudian cuba lagi.',
    '文件上传失败，请检查文件类型和大小后重试。',
  ],
  [
    'The file is too large. Please choose a smaller file and try again.',
    'Fail terlalu besar. Sila pilih fail yang lebih kecil dan cuba lagi.',
    '文件过大，请选择较小的文件后重试。',
  ],
  [
    'The service is temporarily unavailable. Please try again later.',
    'Perkhidmatan tidak tersedia buat sementara waktu. Sila cuba sebentar lagi.',
    '服务暂时不可用，请稍后重试。',
  ],
  [
    'You are doing that too often. Please wait a moment and try again.',
    'Anda melakukannya terlalu kerap. Sila tunggu sebentar dan cuba lagi.',
    '操作过于频繁，请稍候再试。',
  ],
  [
    'Daily AI request limit reached. Please try again tomorrow.',
    'Had permintaan AI harian telah dicapai. Sila cuba lagi esok.',
    '今天的 AI 使用次数已达上限，请明天再试。',
  ],
  [
    'AI tutor is currently unavailable. Please try again later.',
    'Pembantu AI tidak tersedia buat masa ini. Sila cuba sebentar lagi.',
    'AI 助手暂时不可用，请稍后再试。',
  ],
  [
    'This request was not sent to the AI helper because it may contain unsafe content. Please keep AI requests to Mandarin learning.',
    'Permintaan ini tidak dihantar kepada pembantu AI kerana mungkin mengandungi kandungan tidak selamat. Sila hadkan permintaan AI kepada pembelajaran bahasa Mandarin.',
    '此请求可能包含不安全内容，未发送给 AI 助手。请只就中文学习提问。',
  ],
  [
    'The AI reply was withheld by the safety check. Please rephrase your Mandarin-learning question.',
    'Jawapan AI telah ditahan oleh semakan keselamatan. Sila tulis semula soalan pembelajaran Mandarin anda.',
    'AI 回复未通过安全检查，已被隐藏。请换一种方式提出中文学习问题。',
  ],
  [
    'The request conflicts with the current state. Please refresh and try again.',
    'Permintaan bercanggah dengan keadaan semasa. Sila muat semula dan cuba lagi.',
    '该请求与当前状态冲突，请刷新后重试。',
  ],
  [
    'We could not complete that action. Please try again shortly.',
    'Tindakan itu tidak dapat diselesaikan. Sila cuba sebentar lagi.',
    '无法完成该操作，请稍后重试。',
  ],
  // Teacher profile bio rule (fieldErrors.bio of PUT /teachers/me/profile, the same rule as the sign-up form).
  ['Teacher bio is required', 'Biografi guru diperlukan.', '教师简介为必填项。'],
  [
    'Teacher bio must be between 20 and 1000 characters',
    'Biografi guru mestilah antara 20 hingga 1000 aksara.',
    '教师简介必须为 20 到 1000 个字符。',
  ],
  // Live classes: a new class starting in the past (judged in its own time zone) and a hidden or ended class.
  ['Start time must be in the future.', 'Masa mula mestilah pada masa hadapan.', '开始时间必须晚于当前时间。'],
  ['This live class is not available.', 'Kelas langsung ini tidak tersedia.', '该直播课目前不可用。'],
]

/**
 * Reasons of failed requests (ResponseStatusException and IllegalArgumentException messages that
 * FriendlyErrorMessageResolver passes on to the user), by area.
 */
const REQUEST_MESSAGES: readonly MessageRow[] = [
  // Accounts, sign-in and profiles
  ['Authenticated user could not be found.', 'Pengguna yang log masuk tidak ditemui.', '找不到当前登录的用户。'],
  ['User could not be found.', 'Pengguna tidak ditemui.', '找不到该用户。'],
  ['User was not found.', 'Pengguna tidak ditemui.', '找不到该用户。'],
  ['Admin user could not be found.', 'Pengguna pentadbir tidak ditemui.', '找不到该管理员用户。'],
  ['Student profile could not be found.', 'Profil pelajar tidak ditemui.', '找不到学生资料。'],
  ['Teacher profile could not be found.', 'Profil guru tidak ditemui.', '找不到教师资料。'],
  ['Teacher profile was not found.', 'Profil guru tidak ditemui.', '找不到教师资料。'],
  [
    'Time zone must be a valid IANA time zone.',
    'Zon waktu mestilah zon waktu IANA yang sah.',
    '时区必须是有效的 IANA 时区。',
  ],

  // Teacher verification and user management (admin)
  ['Verification document file is required.', 'Fail dokumen pengesahan diperlukan.', '请上传认证文件。'],
  [
    'Requested verification document was not found.',
    'Dokumen pengesahan yang diminta tidak ditemui.',
    '找不到请求的认证文件。',
  ],
  ['Requested teacher document was not found.', 'Dokumen guru yang diminta tidak ditemui.', '找不到请求的教师文件。'],
  [
    'You cannot deactivate your own admin account.',
    'Anda tidak boleh menyahaktifkan akaun pentadbir anda sendiri.',
    '你不能停用自己的管理员账号。',
  ],
  ['Invalid user role filter.', 'Penapis peranan pengguna tidak sah.', '用户角色筛选条件无效。'],

  // Courses (packages), modules and lessons
  [
    'Selected learning category does not exist.',
    'Kategori pembelajaran yang dipilih tidak wujud.',
    '所选学习分类不存在。',
  ],
  ['Selected learning level does not exist.', 'Tahap pembelajaran yang dipilih tidak wujud.', '所选学习级别不存在。'],

  // Content review and moderation
  [
    'Content contains language that is not suitable for this learning platform.',
    'Kandungan ini mengandungi bahasa yang tidak sesuai untuk platform pembelajaran ini.',
    '内容包含不适合本学习平台的用语。',
  ],

  // Quizzes
  ['Quiz attempt was not found.', 'Cubaan kuiz tidak ditemui.', '找不到该测验作答记录。'],
  ['Requested quiz was not found.', 'Kuiz yang diminta tidak ditemui.', '找不到请求的测验。'],
  ['Requested quiz question was not found.', 'Soalan kuiz yang diminta tidak ditemui.', '找不到请求的测验题目。'],
  [
    'Quiz questions cannot be changed after students have submitted attempts.',
    'Soalan kuiz tidak boleh diubah selepas pelajar menghantar cubaan.',
    '学生提交作答后，测验题目不能再修改。',
  ],
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
    'At least one answer option is required.',
    'Sekurang-kurangnya satu pilihan jawapan diperlukan.',
    '至少需要一个答案选项。',
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

  // Live classes and recorded videos
  ['End time must be after start time.', 'Masa tamat mestilah selepas masa mula.', '结束时间必须晚于开始时间。'],
  [
    'Invalid meeting platform. Allowed values are ZOOM, GOOGLE_MEET, MICROSOFT_TEAMS, or OTHER.',
    'Platform mesyuarat tidak sah. Pilih Zoom, Google Meet, Microsoft Teams atau platform lain.',
    '会议平台无效。请选择 Zoom、Google Meet、Microsoft Teams 或其他平台。',
  ],
  ['Meeting link must be a valid URL.', 'Pautan mesyuarat mestilah URL yang sah.', '会议链接必须是有效的网址。'],
  [
    'Meeting link host does not match the selected meeting platform.',
    'Pautan mesyuarat tidak sepadan dengan platform mesyuarat yang dipilih.',
    '会议链接与所选会议平台不一致。',
  ],
  ['Recorded video was not found.', 'Video rakaman tidak ditemui.', '找不到该录播视频。'],
  ['Recorded video file is required.', 'Fail video rakaman diperlukan.', '请上传录播视频文件。'],
  ['Recorded video source type is required.', 'Jenis sumber video rakaman diperlukan.', '请选择录播视频来源类型。'],
  ['Recorded video source type is invalid.', 'Jenis sumber video rakaman tidak sah.', '录播视频来源类型无效。'],
  ['External video URL is required.', 'URL video luar diperlukan.', '请填写外部视频地址。'],
  ['External video URL must be valid.', 'URL video luar mestilah sah.', '外部视频地址必须有效。'],

  // Progress, feedback and notifications
  ['Feedback was not found.', 'Maklum balas tidak ditemui.', '找不到该反馈。'],
  ['Notification was not found.', 'Pemberitahuan tidak ditemui.', '找不到该通知。'],
  [
    'Status must be UNREAD, READ or ALL.',
    'Status tidak sah. Pilih belum dibaca, sudah dibaca atau semua.',
    '状态无效。请选择未读、已读或全部。',
  ],
  ['Notification recipient must not be null.', 'Penerima pemberitahuan diperlukan.', '通知必须有接收人。'],
  ['Notification type must not be null.', 'Jenis pemberitahuan diperlukan.', '通知必须有类型。'],
  [
    'The from date must be on or before the to date.',
    'Tarikh mula mestilah sama dengan atau sebelum tarikh akhir.',
    '开始日期不能晚于结束日期。',
  ],

  // AI chat, usage logs and AI answer reports
  ['AI chat session was not found.', 'Sesi sembang AI tidak ditemui.', '找不到该 AI 聊天会话。'],
  [
    'AI response snapshot is required when no server-side AI chat message can be resolved.',
    'Salinan jawapan AI diperlukan apabila mesej sembang AI tidak dapat dikenal pasti.',
    '无法找到对应的 AI 聊天消息时，需要提供 AI 回答内容。',
  ],
  ['AI response report was not found.', 'Laporan jawapan AI tidak ditemui.', '找不到该 AI 回答举报。'],
  ['This AI report is already closed.', 'Laporan AI ini sudah ditutup.', '该 AI 报告已关闭。'],
  ['Invalid AI feature key.', 'Kunci ciri AI tidak sah.', 'AI 功能标识无效。'],
  ['Invalid AI report status.', 'Status laporan AI tidak sah.', 'AI 报告状态无效。'],
  ['Invalid AI report review status.', 'Status semakan laporan AI tidak sah.', 'AI 报告的审核状态无效。'],
  ['Invalid AI report reason category.', 'Kategori sebab laporan AI tidak sah.', 'AI 报告的原因类别无效。'],

  // Uploads and files
  ['Uploaded file is required.', 'Sila muat naik fail.', '请上传文件。'],
  ['Uploaded recorded video file is required.', 'Sila muat naik fail video rakaman.', '请上传录播视频文件。'],
  ['Uploaded file name is invalid.', 'Nama fail yang dimuat naik tidak sah.', '上传的文件名无效。'],
  ['Invalid upload target path.', 'Lokasi simpanan fail tidak sah.', '文件保存位置无效。'],
  [
    'Uploaded file must include a valid extension.',
    'Fail yang dimuat naik mesti mempunyai sambungan nama fail yang sah.',
    '上传的文件必须带有有效的扩展名。',
  ],
  [
    'Uploaded file must include a valid extension. Allowed types are PDF, JPG, JPEG, and PNG.',
    'Fail yang dimuat naik mesti mempunyai sambungan nama fail yang sah. Jenis yang dibenarkan ialah PDF, JPG, JPEG dan PNG.',
    '上传的文件必须带有有效的扩展名。允许的类型为 PDF、JPG、JPEG 和 PNG。',
  ],
  [
    'Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.',
    'Video yang dimuat naik mesti mempunyai sambungan nama fail yang sah. Jenis yang dibenarkan ialah MP4, WEBM dan MOV.',
    '上传的视频必须带有有效的扩展名。允许的类型为 MP4、WEBM 和 MOV。',
  ],
  [
    'Unsupported file type. Allowed types are PDF, JPG, JPEG, and PNG.',
    'Jenis fail tidak disokong. Jenis yang dibenarkan ialah PDF, JPG, JPEG dan PNG.',
    '不支持该文件类型。允许的类型为 PDF、JPG、JPEG 和 PNG。',
  ],
  [
    'Unsupported file content type. Allowed types are application/pdf, image/jpeg, and image/png.',
    'Jenis kandungan fail tidak disokong. Jenis yang dibenarkan ialah application/pdf, image/jpeg dan image/png.',
    '不支持该文件内容类型。允许的类型为 application/pdf、image/jpeg 和 image/png。',
  ],
  [
    'Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.',
    'Jenis fail audio tidak disokong. Jenis yang dibenarkan ialah WEBM, WAV, MPEG dan MP3.',
    '不支持该音频文件类型。允许的类型为 WEBM、WAV、MPEG 和 MP3。',
  ],
  [
    'Unsupported image file type. Allowed types are PNG, JPG, and JPEG.',
    'Jenis fail imej tidak disokong. Jenis yang dibenarkan ialah PNG, JPG dan JPEG.',
    '不支持该图片文件类型。允许的类型为 PNG、JPG 和 JPEG。',
  ],
  [
    'Unsupported video type. Allowed types are MP4, WEBM, and MOV.',
    'Jenis video tidak disokong. Jenis yang dibenarkan ialah MP4, WEBM dan MOV.',
    '不支持该视频类型。允许的类型为 MP4、WEBM 和 MOV。',
  ],
  [
    'Unsupported video content type. Allowed types are video/mp4, video/webm, and video/quicktime.',
    'Jenis kandungan video tidak disokong. Jenis yang dibenarkan ialah video/mp4, video/webm dan video/quicktime.',
    '不支持该视频内容类型。允许的类型为 video/mp4、video/webm 和 video/quicktime。',
  ],
  [
    'Uploaded file content does not match the declared PDF/JPG/PNG type.',
    'Kandungan fail yang dimuat naik tidak sepadan dengan jenis PDF/JPG/PNG yang dinyatakan.',
    '上传的文件内容与声明的 PDF/JPG/PNG 类型不符。',
  ],
  [
    'Uploaded audio content does not match the declared WEBM/WAV/MP3 type.',
    'Kandungan audio yang dimuat naik tidak sepadan dengan jenis WEBM/WAV/MP3 yang dinyatakan.',
    '上传的音频内容与声明的 WEBM/WAV/MP3 类型不符。',
  ],
  [
    'Uploaded image content does not match the declared PNG/JPG/JPEG type.',
    'Kandungan imej yang dimuat naik tidak sepadan dengan jenis PNG/JPG/JPEG yang dinyatakan.',
    '上传的图片内容与声明的 PNG/JPG/JPEG 类型不符。',
  ],
  [
    'Uploaded video content does not match the declared MP4/WEBM/MOV type.',
    'Kandungan video yang dimuat naik tidak sepadan dengan jenis MP4/WEBM/MOV yang dinyatakan.',
    '上传的视频内容与声明的 MP4/WEBM/MOV 类型不符。',
  ],
  ['Requested file could not be found.', 'Fail yang diminta tidak ditemui.', '找不到请求的文件。'],
  ['Requested video could not be found.', 'Video yang diminta tidak ditemui.', '找不到请求的视频。'],
]

/**
 * Bean Validation messages of the request records: the `fieldErrors` of a 400 answer (apiFieldError) and, for an
 * activity request, the main message. Rules that are also request messages above are listed there only once.
 */
const VALIDATION_MESSAGES: readonly MessageRow[] = [
  // Sign-in, registration and profiles
  ['Email is required', 'E-mel diperlukan.', '请填写电子邮箱。'],
  ['Email format is invalid', 'Format e-mel tidak sah.', '电子邮箱格式不正确。'],
  ['Email must be at most 150 characters', 'E-mel tidak boleh melebihi 150 aksara.', '电子邮箱不能超过 150 个字符。'],
  ['Password is required', 'Kata laluan diperlukan.', '请输入密码。'],
  ['Password must be at most 72 characters', 'Kata laluan tidak boleh melebihi 72 aksara.', '密码不能超过 72 个字符。'],
  [
    'Password must be at most 72 bytes when encoded as UTF-8',
    'Kata laluan terlalu panjang (had 72 bait). Sila gunakan kata laluan yang lebih pendek.',
    '密码过长（上限为 72 字节），请使用更短的密码。',
  ],
  [
    'Password and confirm password must be at most 72 bytes when encoded as UTF-8',
    'Kata laluan dan pengesahan kata laluan terlalu panjang (had 72 bait). Sila gunakan kata laluan yang lebih pendek.',
    '密码和确认密码过长（上限为 72 字节），请使用更短的密码。',
  ],
  ['Confirm password is required', 'Pengesahan kata laluan diperlukan.', '请再次输入密码。'],
  [
    'Confirm password must be at most 72 characters',
    'Pengesahan kata laluan tidak boleh melebihi 72 aksara.',
    '确认密码不能超过 72 个字符。',
  ],
  ['Current password is required.', 'Kata laluan semasa diperlukan.', '请输入当前密码。'],
  [
    'Current password must be at most 72 characters.',
    'Kata laluan semasa tidak boleh melebihi 72 aksara.',
    '当前密码不能超过 72 个字符。',
  ],
  ['New password is required.', 'Kata laluan baharu diperlukan.', '请输入新密码。'],
  [
    'Reset token must be at most 128 characters',
    'Pautan tetapan semula tidak sah. Sila gunakan pautan penuh daripada e-mel anda.',
    '重置链接无效，请使用邮件中的完整链接。',
  ],
  ['Full name is required', 'Nama penuh diperlukan.', '请填写姓名。'],
  [
    'Full name must be 120 characters or fewer.',
    'Nama penuh tidak boleh melebihi 120 aksara.',
    '姓名不能超过 120 个字符。',
  ],
  [
    'Full name must be at most 120 characters',
    'Nama penuh tidak boleh melebihi 120 aksara.',
    '姓名不能超过 120 个字符。',
  ],
  ['Display name is required.', 'Nama paparan diperlukan.', '请填写显示名称。'],
  [
    'Display name must be 120 characters or fewer.',
    'Nama paparan tidak boleh melebihi 120 aksara.',
    '显示名称不能超过 120 个字符。',
  ],
  [
    'Preferred language must be en, ms or zh-CN.',
    'Bahasa pilihan mestilah bahasa Inggeris, bahasa Melayu atau bahasa Cina Ringkas.',
    '偏好语言必须是英语、马来语或简体中文。',
  ],
  [
    'Preferred language must be one of en, ms, or zh-CN',
    'Bahasa pilihan mestilah bahasa Inggeris, bahasa Melayu atau bahasa Cina Ringkas.',
    '偏好语言必须是英语、马来语或简体中文。',
  ],
  [
    'Language preference must not exceed 20 characters.',
    'Pilihan bahasa tidak boleh melebihi 20 aksara.',
    '语言偏好不能超过 20 个字符。',
  ],
  [
    'Time zone must be 64 characters or fewer.',
    'Zon waktu tidak boleh melebihi 64 aksara.',
    '时区不能超过 64 个字符。',
  ],

  // Admin: teachers, users, settings, moderation and logs
  ['Rejection reason is required.', 'Sebab penolakan diperlukan.', '请填写拒绝原因。'],
  [
    'Rejection reason must be 1000 characters or fewer.',
    'Sebab penolakan tidak boleh melebihi 1000 aksara.',
    '拒绝原因不能超过 1000 个字符。',
  ],
  [
    'Approval note must be 1000 characters or fewer.',
    'Nota kelulusan tidak boleh melebihi 1000 aksara.',
    '批准备注不能超过 1000 个字符。',
  ],
  ['Reviewer note is required.', 'Nota penyemak diperlukan.', '请填写审核备注。'],
  [
    'Reviewer note must be 1000 characters or fewer.',
    'Nota penyemak tidak boleh melebihi 1000 aksara.',
    '审核备注不能超过 1000 个字符。',
  ],
  [
    'Document type must not exceed 40 characters.',
    'Jenis dokumen tidak boleh melebihi 40 aksara.',
    '文件类型不能超过 40 个字符。',
  ],
  ['Role must not exceed 40 characters.', 'Peranan tidak boleh melebihi 40 aksara.', '角色不能超过 40 个字符。'],
  [
    'User role must not exceed 40 characters.',
    'Peranan pengguna tidak boleh melebihi 40 aksara.',
    '用户角色不能超过 40 个字符。',
  ],
  ['AI disclaimer text is required.', 'Teks penafian AI diperlukan.', '请填写 AI 免责声明。'],
  [
    'AI disclaimer text must be 200 characters or fewer.',
    'Teks penafian AI tidak boleh melebihi 200 aksara.',
    'AI 免责声明不能超过 200 个字符。',
  ],
  ['AI safety check setting is required.', 'Tetapan semakan keselamatan AI diperlukan.', '请设置 AI 安全检查。'],
  [
    'Sensitive word filter setting is required.',
    'Tetapan penapis perkataan sensitif diperlukan.',
    '请设置敏感词过滤。',
  ],
  [
    'Action type must not exceed 80 characters.',
    'Jenis tindakan tidak boleh melebihi 80 aksara.',
    '操作类型不能超过 80 个字符。',
  ],
  [
    'Actor must not exceed 200 characters.',
    'Nama pelaku tidak boleh melebihi 200 aksara.',
    '操作人不能超过 200 个字符。',
  ],
  [
    'Feature type must not exceed 80 characters.',
    'Jenis ciri tidak boleh melebihi 80 aksara.',
    '功能类型不能超过 80 个字符。',
  ],
  ['Status must not exceed 40 characters.', 'Status tidak boleh melebihi 40 aksara.', '状态不能超过 40 个字符。'],
  [
    'Keyword must not exceed 200 characters.',
    'Kata kunci tidak boleh melebihi 200 aksara.',
    '关键词不能超过 200 个字符。',
  ],

  // Courses, modules and lessons
  ['Title is required.', 'Tajuk diperlukan.', '请填写标题。'],
  ['Title must be 200 characters or fewer.', 'Tajuk tidak boleh melebihi 200 aksara.', '标题不能超过 200 个字符。'],
  ['Title must not exceed 200 characters.', 'Tajuk tidak boleh melebihi 200 aksara.', '标题不能超过 200 个字符。'],
  [
    'Description must be 1000 characters or fewer.',
    'Penerangan tidak boleh melebihi 1000 aksara.',
    '说明不能超过 1000 个字符。',
  ],
  ['Level is required.', 'Tahap diperlukan.', '请选择级别。'],
  ['Level must not exceed 100 characters.', 'Tahap tidak boleh melebihi 100 aksara.', '级别不能超过 100 个字符。'],
  ['Level id must be positive.', 'ID tahap mestilah nombor positif.', '级别 ID 必须为正数。'],
  ['HSK level must be between 1 and 6.', 'Tahap HSK mestilah antara 1 hingga 6.', 'HSK 等级必须在 1 到 6 之间。'],
  ['Lesson id must be positive.', 'ID pelajaran mestilah nombor positif.', '课时 ID 必须为正数。'],
  ['Duration must not be negative.', 'Tempoh tidak boleh negatif.', '时长不能为负数。'],

  // Quizzes
  ['Quiz title is required.', 'Tajuk kuiz diperlukan.', '请填写测验标题。'],
  [
    'Quiz title must not exceed 200 characters.',
    'Tajuk kuiz tidak boleh melebihi 200 aksara.',
    '测验标题不能超过 200 个字符。',
  ],
  [
    'Quiz description must not exceed 1000 characters.',
    'Penerangan kuiz tidak boleh melebihi 1000 aksara.',
    '测验说明不能超过 1000 个字符。',
  ],
  ['Time limit must be greater than or equal to 0.', 'Had masa tidak boleh negatif.', '时间限制不能为负数。'],
  ['Question text is required.', 'Teks soalan diperlukan.', '请填写题目文本。'],
  [
    'Question text must be 2000 characters or fewer.',
    'Teks soalan tidak boleh melebihi 2000 aksara.',
    '题目文本不能超过 2000 个字符。',
  ],
  ['Question type is required.', 'Jenis soalan diperlukan.', '请选择题型。'],
  [
    'Question type must be 50 characters or fewer.',
    'Jenis soalan tidak boleh melebihi 50 aksara.',
    '题型不能超过 50 个字符。',
  ],
  ['Question points are required.', 'Mata soalan diperlukan.', '请填写题目分值。'],
  ['Question points must be greater than 0.', 'Mata soalan mestilah lebih daripada 0.', '题目分值必须大于 0。'],
  ['Question id is required.', 'ID soalan diperlukan.', '题目 ID 为必填项。'],
  ['Question id must be positive.', 'ID soalan mestilah nombor positif.', '题目 ID 必须为正数。'],
  ['Option text is required.', 'Teks pilihan diperlukan.', '请填写选项文本。'],
  [
    'Option text must be 1000 characters or fewer.',
    'Teks pilihan tidak boleh melebihi 1000 aksara.',
    '选项文本不能超过 1000 个字符。',
  ],
  ['Option id must be positive.', 'ID pilihan mestilah nombor positif.', '选项 ID 必须为正数。'],
  [
    'Correct answer text must be 1000 characters or fewer.',
    'Teks jawapan betul tidak boleh melebihi 1000 aksara.',
    '正确答案不能超过 1000 个字符。',
  ],
  [
    'Explanation must be 2000 characters or fewer.',
    'Penerangan tidak boleh melebihi 2000 aksara.',
    '解析不能超过 2000 个字符。',
  ],
  [
    'Hint must be 1000 characters or fewer.',
    'Petunjuk tidak boleh melebihi 1000 aksara.',
    '提示不能超过 1000 个字符。',
  ],
  ['Quiz option must not be null.', 'Pilihan kuiz diperlukan.', '测验选项不能为空。'],
  [
    'A quiz question cannot contain more than 200 options.',
    'Soalan kuiz tidak boleh mengandungi lebih daripada 200 pilihan.',
    '一道测验题最多只能包含 200 个选项。',
  ],
  [
    'Answer must be 2000 characters or fewer.',
    'Jawapan tidak boleh melebihi 2000 aksara.',
    '答案不能超过 2000 个字符。',
  ],
  ['Quiz attempt id must be positive.', 'ID cubaan kuiz mestilah nombor positif.', '测验作答 ID 必须为正数。'],

  // Practice activities (activity library)
  ['Topic is required.', 'Topik diperlukan.', '请填写主题。'],
  ['Topic must not exceed 200 characters.', 'Topik tidak boleh melebihi 200 aksara.', '主题不能超过 200 个字符。'],
  ['Too many answers.', 'Terlalu banyak jawapan.', '答案数量过多。'],
  [
    'Audio link must be 500 characters or fewer.',
    'Pautan audio tidak boleh melebihi 500 aksara.',
    '音频链接不能超过 500 个字符。',
  ],
  [
    'Audio link must start with http:// or https:// and contain no spaces.',
    'Pautan audio mestilah bermula dengan http:// atau https:// dan tidak mengandungi ruang kosong.',
    '音频链接必须以 http:// 或 https:// 开头，且不能包含空格。',
  ],
  [
    'Image link must be 500 characters or fewer.',
    'Pautan imej tidak boleh melebihi 500 aksara.',
    '图片链接不能超过 500 个字符。',
  ],
  [
    'Image link must start with http:// or https:// and contain no spaces.',
    'Pautan imej mestilah bermula dengan http:// atau https:// dan tidak mengandungi ruang kosong.',
    '图片链接必须以 http:// 或 https:// 开头，且不能包含空格。',
  ],

  // Live classes, recorded videos and progress
  ['Start time is required.', 'Masa mula diperlukan.', '请选择开始时间。'],
  ['End time is required.', 'Masa tamat diperlukan.', '请选择结束时间。'],
  ['Meeting platform is required.', 'Platform mesyuarat diperlukan.', '请选择会议平台。'],
  ['Meeting link is required.', 'Pautan mesyuarat diperlukan.', '请填写会议链接。'],
  [
    'Meeting link must be 500 characters or fewer.',
    'Pautan mesyuarat tidak boleh melebihi 500 aksara.',
    '会议链接不能超过 500 个字符。',
  ],
  [
    'External video URL must be 500 characters or fewer.',
    'URL video luar tidak boleh melebihi 500 aksara.',
    '外部视频地址不能超过 500 个字符。',
  ],
  [
    'External video URL must start with http:// or https:// and contain no whitespace.',
    'URL video luar mestilah bermula dengan http:// atau https:// dan tidak mengandungi ruang kosong.',
    '外部视频地址必须以 http:// 或 https:// 开头，且不能包含空格。',
  ],
  ['Source type is required.', 'Jenis sumber diperlukan.', '请选择来源类型。'],
  [
    'Source type must be 30 characters or fewer.',
    'Jenis sumber tidak boleh melebihi 30 aksara.',
    '来源类型不能超过 30 个字符。',
  ],

  // Teacher feedback and student lists
  ['Feedback message is required.', 'Mesej maklum balas diperlukan.', '请填写反馈内容。'],
  ['Message is required.', 'Mesej diperlukan.', '请填写消息内容。'],
  [
    'Message must not exceed 2000 characters.',
    'Mesej tidak boleh melebihi 2000 aksara.',
    '消息内容不能超过 2000 个字符。',
  ],
  ['Review status is required.', 'Status semakan diperlukan.', '请选择审核状态。'],
  [
    'Review status must be 30 characters or fewer.',
    'Status semakan tidak boleh melebihi 30 aksara.',
    '审核状态不能超过 30 个字符。',
  ],
  ['Target text is required.', 'Teks sasaran diperlukan.', '请填写目标文本。'],
  ['Student id is required.', 'ID pelajar diperlukan.', '学生 ID 为必填项。'],
  ['Student id must be positive.', 'ID pelajar mestilah nombor positif.', '学生 ID 必须为正数。'],

  // AI features and AI answer reports
  ['Number of questions must be at least 1.', 'Bilangan soalan mestilah sekurang-kurangnya 1.', '题目数量至少为 1。'],
  ['Number of questions must not exceed 10.', 'Bilangan soalan tidak boleh melebihi 10.', '题目数量不能超过 10。'],
  ['Session id must be positive.', 'ID sesi mestilah nombor positif.', '会话 ID 必须为正数。'],
  ['AI feature key is required.', 'Kunci ciri AI diperlukan.', 'AI 功能标识为必填项。'],
  [
    'AI feature key must be 100 characters or fewer.',
    'Kunci ciri AI tidak boleh melebihi 100 aksara.',
    'AI 功能标识不能超过 100 个字符。',
  ],
  [
    'AI response snapshot must be 4000 characters or fewer.',
    'Salinan jawapan AI tidak boleh melebihi 4000 aksara.',
    'AI 回答内容不能超过 4000 个字符。',
  ],
  [
    'Prompt snapshot must be 4000 characters or fewer.',
    'Salinan soalan kepada AI tidak boleh melebihi 4000 aksara.',
    '提问内容不能超过 4000 个字符。',
  ],
  ['Report reason category is required.', 'Kategori sebab laporan diperlukan.', '请选择举报原因类别。'],
  [
    'Report reason category must be 40 characters or fewer.',
    'Kategori sebab laporan tidak boleh melebihi 40 aksara.',
    '举报原因类别不能超过 40 个字符。',
  ],
]

/** Messages of the modules added with the prototype alignment, one file per module in serverMessageRows/. */
const MODULE_MESSAGES: readonly MessageRow[] = [
  ...LEARN_MESSAGES,
  ...CLASSES_MESSAGES,
  ...AI_MESSAGES,
  ...OPS_MESSAGES,
  ...PLAN_MESSAGES,
]

/** The words a template puts in a placeholder, keyed by the lower-case English word, with their translations. */
type Vocabulary = Readonly<Record<string, Readonly<Record<TranslatedLocale, string>>>>

/**
 * The content names of "{content} was not found." ("Lesson was not found."). Malay words are lower case; a Malay
 * sentence that starts with one gets a capital letter.
 */
const CONTENT_LABELS: Vocabulary = {
  lesson: { ms: 'pelajaran', 'zh-CN': '课时' },
  quiz: { ms: 'kuiz', 'zh-CN': '测验' },
  activity: { ms: 'aktiviti', 'zh-CN': '活动' },
  'teacher profile': { ms: 'profil guru', 'zh-CN': '教师资料' },
}

/** The media of a quiz question link (TeacherLessonEditorService). */
const MEDIA_LABELS: Vocabulary = {
  audio: { ms: 'audio', 'zh-CN': '音频' },
  image: { ms: 'imej', 'zh-CN': '图片' },
}

/** The notification fields NotificationService checks. */
const NOTIFICATION_FIELDS: Vocabulary = {
  'notification title': { ms: 'tajuk pemberitahuan', 'zh-CN': '通知标题' },
  'notification message': { ms: 'mesej pemberitahuan', 'zh-CN': '通知内容' },
}

/**
 * A placeholder's values: 'text' is user content (a value the user typed or chose, kept exactly as sent), and a
 * vocabulary is a fixed word that is translated with the message.
 */
type Slot = 'text' | Vocabulary

/** A message the backend builds from parts; `{name}` placeholders are filled from `slots`. */
type MessagePattern = Readonly<Record<'en' | TranslatedLocale, string>> & {
  readonly slots: Readonly<Record<string, Slot>>
}

const MESSAGE_PATTERNS: readonly MessagePattern[] = [
  {
    en: '{content} was not found.',
    ms: '{content} tidak ditemui.',
    'zh-CN': '找不到该{content}。',
    slots: { content: CONTENT_LABELS },
  },
  // The lesson editor's media links and NotificationService's required fields
  {
    en: '{media} link must be a full http:// or https:// web address without a user name or password.',
    ms: 'Pautan {media} mestilah alamat web penuh yang bermula dengan http:// atau https://, tanpa nama pengguna atau kata laluan.',
    'zh-CN': '{media}链接必须是以 http:// 或 https:// 开头的完整网址，且不能包含用户名或密码。',
    slots: { media: MEDIA_LABELS },
  },
  {
    en: '{field} must not be blank.',
    ms: '{field} tidak boleh kosong.',
    'zh-CN': '{field}不能为空。',
    slots: { field: NOTIFICATION_FIELDS },
  },
  // A filter value the backend does not know and an unknown setting key (PlatformSettingsService): kept as sent.
  {
    en: 'Unknown status: {value}',
    ms: 'Status tidak dikenali: {value}',
    'zh-CN': '无法识别的状态：{value}',
    slots: { value: 'text' },
  },
  {
    en: 'Unsupported system setting key: {value}',
    ms: 'Tetapan sistem tidak disokong: {value}',
    'zh-CN': '不支持的系统设置：{value}',
    slots: { value: 'text' },
  },
]

function collapseWhitespace(message: string): string {
  return message.replace(/\s+/g, ' ').trim()
}

/** Lookup key: case, repeated spaces and one final full stop do not matter ("Email is required" = "Email is required."). */
function messageKey(message: string): string {
  return collapseWhitespace(message).toLowerCase().replace(/[.]$/, '')
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const ROW_MESSAGES: ReadonlyMap<string, Readonly<Record<TranslatedLocale, string>>> = new Map(
  [...GENERAL_MESSAGES, ...REQUEST_MESSAGES, ...VALIDATION_MESSAGES, ...MODULE_MESSAGES].map(([en, ms, zh]) => [
    messageKey(en),
    { ms, 'zh-CN': zh },
  ]),
)

const PLACEHOLDER = /[{]([A-Za-z]+)[}]/g

type CompiledPattern = { pattern: MessagePattern; names: string[]; regex: RegExp }

function slotSource(slot: Slot): string {
  if (slot === 'text') return '(.+?)'
  // Longest word first, so "drag-and-drop activity" is not read as "activity".
  const words = Object.keys(slot).sort((a, b) => b.length - a.length)
  return `(${words.map(escapeRegExp).join('|')})`
}

/** A safe, anchored regex built from the English template: fixed text is escaped, each placeholder is one group. */
function compilePattern(pattern: MessagePattern): CompiledPattern {
  // A final full stop of the template is optional; a template that ends with user content keeps that content whole.
  const endsWithStop = pattern.en.endsWith('.')
  const template = endsWithStop ? pattern.en.slice(0, -1) : pattern.en
  const names: string[] = []
  let source = ''
  let last = 0
  for (const match of template.matchAll(PLACEHOLDER)) {
    source += escapeRegExp(template.slice(last, match.index)) + slotSource(pattern.slots[match[1]])
    names.push(match[1])
    last = match.index + match[0].length
  }
  source += escapeRegExp(template.slice(last))
  return { pattern, names, regex: new RegExp(`^${source}${endsWithStop ? '[.]?' : ''}$`, 'i') }
}

const COMPILED_PATTERNS = MESSAGE_PATTERNS.map(compilePattern)

/** The message in Malay or Simplified Chinese when it matches a template; null otherwise. */
function translatePattern(message: string, locale: TranslatedLocale): string | null {
  const text = collapseWhitespace(message)
  for (const { pattern, names, regex } of COMPILED_PATTERNS) {
    const match = regex.exec(text)
    if (!match) continue
    const values = new Map(names.map((name, index) => [name, match[index + 1]]))
    const translated = pattern[locale].replace(PLACEHOLDER, (_placeholder, name: string) => {
      const slot = pattern.slots[name]
      const value = values.get(name) ?? ''
      return slot === 'text' ? value : (slot[value.toLowerCase()]?.[locale] ?? value)
    })
    // A sentence that starts with a (lower-case) label gets a capital letter; user content is never changed.
    const first = /^[{]([A-Za-z]+)[}]/.exec(pattern[locale])
    const startsWithLabel = first !== null && pattern.slots[first[1]] !== 'text'
    return startsWithLabel ? translated.charAt(0).toUpperCase() + translated.slice(1) : translated
  }
  return null
}

/** Shows a known backend message in the interface language; unknown text is returned unchanged. */
export function localizeServerMessage(message: string, locale: AppLanguage): string {
  if (locale === 'en') return message
  return ROW_MESSAGES.get(messageKey(message))?.[locale] ?? translatePattern(message, locale) ?? message
}

/** A message the server sent (e.g. why an AI request was not answered) in the interface language, or `fallback`. */
export function localizedServerMessage(message: string | null | undefined, fallback: string): string {
  return message ? localizeServerMessage(message, documentLanguage()) : fallback
}

/** Exposed for tests: the whole catalogue, so tests can check that every entry is translated and consistent. */
export const serverMessageCatalogue = {
  GENERAL_MESSAGES,
  REQUEST_MESSAGES,
  VALIDATION_MESSAGES,
  MODULE_MESSAGES,
  MESSAGE_PATTERNS,
} as const
