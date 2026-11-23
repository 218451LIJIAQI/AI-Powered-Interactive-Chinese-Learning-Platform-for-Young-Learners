import type { MessageRow } from './types'

/** Canonical backend refusal when resending an email change after no pending change remains. */
export const NO_PENDING_EMAIL_CHANGE_MESSAGE =
  'No email change is waiting for confirmation. Use Change email to start again.'

/** Backend messages of the ops module, translated like the rows in utils/serverMessages.ts. */
export const OPS_MESSAGES: readonly MessageRow[] = [
  // Accounts, profiles, e-mail change and deletion
  ['User was not found.', 'Pengguna tidak ditemui.', '找不到该用户。'],
  ['Student profile could not be found.', 'Profil pelajar tidak ditemui.', '找不到学生资料。'],
  ['This is already your email address.', 'Ini sudah alamat e-mel anda.', '这已经是你的邮箱地址。'],
  [
    NO_PENDING_EMAIL_CHANGE_MESSAGE,
    'Tiada pertukaran e-mel yang menunggu pengesahan. Gunakan Tukar e-mel untuk bermula semula.',
    '没有等待确认的邮箱更改。请使用“更改邮箱”重新开始。',
  ],
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
  // Admin invites and users
  [
    'You cannot deactivate your own admin account.',
    'Anda tidak boleh menyahaktifkan akaun pentadbir anda sendiri.',
    '你不能停用自己的管理员账号。',
  ],
  ['Invalid user role filter.', 'Penapis peranan pengguna tidak sah.', '用户角色筛选条件无效。'],
  [
    'This invite link is invalid or has expired. Ask an admin to send a new one.',
    'Pautan jemputan ini tidak sah atau telah tamat tempoh. Minta pentadbir menghantar pautan baharu.',
    '此邀请链接无效或已过期，请让管理员重新发送。',
  ],
  // "Cancel invite"
  ['This invite was not found.', 'Jemputan ini tidak ditemui.', '找不到该邀请。'],
  ['This invite was already accepted.', 'Jemputan ini sudah diterima.', '该邀请已被接受。'],
  [
    'This invite has expired. Send a new invite.',
    'Jemputan ini telah tamat tempoh. Hantar jemputan baharu.',
    '该邀请已过期，请重新发送邀请。',
  ],
  [
    "A deleted account can't be reactivated.",
    'Akaun yang telah dipadam tidak boleh diaktifkan semula.',
    '已删除的账号无法重新启用。',
  ],
  // An account left without a role when teachers were removed (a teacher account).
  [
    "This account has no role any more, so it can't be reactivated.",
    'Akaun ini tidak lagi mempunyai peranan, jadi ia tidak boleh diaktifkan semula.',
    '此账号已没有角色，因此无法重新启用。',
  ],
  ['Audit entry was not found.', 'Entri audit tidak ditemui.', '找不到该审计记录。'],
  // Help & support
  ['Support request was not found.', 'Permintaan sokongan tidak ditemui.', '找不到该支持请求。'],
  ['Invalid support status filter.', 'Penapis status sokongan tidak sah.', '支持请求状态筛选无效。'],
  [
    'This request is solved. Reopen it first if you need to reply.',
    'Permintaan ini telah selesai. Buka semula dahulu jika anda perlu membalas.',
    '此请求已解决。如需回复，请先重新打开。',
  ],
  // The Undo of "Mark as solved".
  [
    'Only a solved request can be reopened.',
    'Hanya permintaan yang telah selesai boleh dibuka semula.',
    '只有已解决的请求才能重新打开。',
  ],

  // Notifications and date filters
  ['Notification was not found.', 'Pemberitahuan tidak ditemui.', '找不到该通知。'],
  // An Undo request with more ids than the endpoint accepts in one batch.
  [
    'Too many notifications to mark as unread at once.',
    'Terlalu banyak pemberitahuan untuk ditanda belum dibaca sekali gus.',
    '一次要标为未读的通知太多。',
  ],
  // The answer to a filter value the backend does not know.
  ['Unknown status.', 'Status tidak dikenali.', '无法识别的状态。'],
  [
    'Status must be UNREAD, READ or ALL.',
    'Status tidak sah. Pilih belum dibaca, sudah dibaca atau semua.',
    '状态无效。请选择未读、已读或全部。',
  ],
  [
    'The from date must be on or before the to date.',
    'Tarikh mula mestilah sama dengan atau sebelum tarikh akhir.',
    '开始日期不能晚于结束日期。',
  ],
]
