/**
 * Backend messages of the ops module, translated like the rows in utils/serverMessages.ts:
 * [English exactly as the backend sends it, Bahasa Melayu, Simplified Chinese].
 */
export const OPS_MESSAGES: readonly (readonly [en: string, ms: string, zh: string])[] = [
  // Account: e-mail change and deletion
  ['This is already your email address.', 'Ini sudah alamat e-mel anda.', '这已经是你的邮箱地址。'],
  [
    'This confirmation link is invalid or has already been used.',
    'Pautan pengesahan ini tidak sah atau sudah digunakan.',
    '此确认链接无效或已被使用。',
  ],
  [
    'This confirmation link has expired. Ask for a new one in Account.',
    'Pautan pengesahan ini telah tamat tempoh. Minta pautan baharu di Akaun.',
    '此确认链接已过期，请在“账号”中重新申请。',
  ],
  [
    "The email address you typed doesn't match your account.",
    'Alamat e-mel yang anda taip tidak sepadan dengan akaun anda.',
    '你输入的邮箱地址与账号不符。',
  ],
  [
    "Admin accounts can't be deleted. Another admin can deactivate this account.",
    'Akaun pentadbir tidak boleh dipadam. Pentadbir lain boleh menyahaktifkan akaun ini.',
    '管理员账号不能删除，可由其他管理员停用此账号。',
  ],
  [
    'Archive your published classes before you delete your account.',
    'Arkibkan kelas anda yang telah diterbitkan sebelum memadam akaun anda.',
    '删除账号前，请先归档你已发布的课程。',
  ],
  ['Type your email address to confirm.', 'Taip alamat e-mel anda untuk mengesahkan.', '请输入你的邮箱地址以确认。'],
  // Admin invites and users
  [
    'This invite link is invalid or has expired. Ask an admin to send a new one.',
    'Pautan jemputan ini tidak sah atau telah tamat tempoh. Minta pentadbir menghantar pautan baharu.',
    '此邀请链接无效或已过期，请让管理员重新发送。',
  ],
  [
    "A deleted account can't be reactivated.",
    'Akaun yang telah dipadam tidak boleh diaktifkan semula.',
    '已删除的账号无法重新启用。',
  ],
  ['A reason is required.', 'Sebab diperlukan.', '请填写原因。'],
  [
    'The reason must be 500 characters or fewer.',
    'Sebab mestilah 500 aksara atau kurang.',
    '原因不能超过 500 个字符。',
  ],
  ['Audit entry was not found.', 'Entri audit tidak ditemui.', '找不到该审计记录。'],
  // Teacher verification
  [
    'Upload a teaching certificate, an identity document or a degree certificate.',
    'Muat naik sijil mengajar, dokumen pengenalan atau sijil ijazah.',
    '请上传教师资格证书、身份证件或学位证书。',
  ],
  [
    'Upload your teaching certificate and identity document before you submit.',
    'Muat naik sijil mengajar dan dokumen pengenalan anda sebelum menghantar.',
    '提交前请上传教师资格证书和身份证件。',
  ],
  ['Your application is already in review.', 'Permohonan anda sudah dalam semakan.', '你的申请已在审核中。'],
  [
    "Documents can't be changed while they are in review.",
    'Dokumen tidak boleh diubah semasa dalam semakan.',
    '审核期间不能更改文件。',
  ],
  ['Your documents are already verified.', 'Dokumen anda sudah disahkan.', '你的文件已通过认证。'],
  [
    'Only applications in review can be approved.',
    'Hanya permohonan dalam semakan boleh diluluskan.',
    '只有审核中的申请才能批准。',
  ],
  [
    'Agree to follow what MandarinSpark expects from teachers before you submit.',
    'Bersetuju untuk mematuhi jangkaan MandarinSpark terhadap guru sebelum menghantar.',
    '提交前，请同意遵守 MandarinSpark 对教师的要求。',
  ],
  // Help & support
  ['Support request was not found.', 'Permintaan sokongan tidak ditemui.', '找不到该支持请求。'],
  ['Invalid support status filter.', 'Penapis status sokongan tidak sah.', '支持请求状态筛选无效。'],
  [
    'This request is solved. Send a new request if you need more help.',
    'Permintaan ini telah selesai. Hantar permintaan baharu jika anda memerlukan bantuan lagi.',
    '此请求已解决。如需更多帮助，请发送新的请求。',
  ],
  ['Choose a topic.', 'Pilih topik.', '请选择主题。'],
  ['Add a subject.', 'Tambah subjek.', '请填写主题。'],
  [
    'The subject must be 150 characters or fewer.',
    'Subjek mestilah 150 aksara atau kurang.',
    '主题不能超过 150 个字符。',
  ],
  ['Tell us what happened.', 'Beritahu kami apa yang berlaku.', '请告诉我们发生了什么。'],
  [
    'The message must be 4000 characters or fewer.',
    'Mesej mestilah 4000 aksara atau kurang.',
    '内容不能超过 4000 个字符。',
  ],
  ['Write a reply first.', 'Tulis balasan dahulu.', '请先填写回复。'],
]
