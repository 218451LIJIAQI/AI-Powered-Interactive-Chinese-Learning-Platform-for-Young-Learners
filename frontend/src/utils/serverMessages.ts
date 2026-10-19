/**
 * Localised versions of the most common backend error messages (FR-I18N-05).
 *
 * The backend answers in English (FriendlyErrorMessageResolver + service messages). When the interface
 * language is Malay or Simplified Chinese, a known message is replaced by its translation; an unknown
 * message is shown unchanged, so the backend text always remains the fallback.
 */
export type ServerMessageLocale = 'en' | 'ms' | 'zh-CN'

type ServerMessageKey =
  | 'invalidCredentials'
  | 'accountUnavailable'
  | 'emailRegistered'
  | 'passwordMismatch'
  | 'passwordLength'
  | 'currentPasswordIncorrect'
  | 'newPasswordSame'
  | 'resetTokenExpired'
  | 'resetTokenInvalid'
  | 'resetTokenRequired'
  | 'classFull'
  | 'alreadyEnrolled'
  | 'teacherApprovalRequired'
  | 'hintsUnavailable'
  | 'sessionExpired'
  | 'permissionDenied'
  | 'notFound'
  | 'validationFailed'
  | 'invalidRequest'
  | 'uploadFailed'
  | 'uploadTooLarge'
  | 'serverUnavailable'
  | 'tooManyRequests'
  | 'aiDailyLimit'
  | 'conflict'
  | 'operationFailed'

/** Normalised English backend message -> key. */
const BACKEND_MESSAGES: Readonly<Record<string, ServerMessageKey>> = {
  'invalid email or password.': 'invalidCredentials',
  'this account is currently unavailable. please contact the platform administrator.': 'accountUnavailable',
  'email is already registered.': 'emailRegistered',
  'confirm password does not match.': 'passwordMismatch',
  'password must be between 8 and 72 characters.': 'passwordLength',
  'current password is incorrect.': 'currentPasswordIncorrect',
  'new password must be different from the current password.': 'newPasswordSame',
  'reset token has expired. please request a new one.': 'resetTokenExpired',
  'reset token has already been used.': 'resetTokenInvalid',
  'reset token is invalid or has already been used.': 'resetTokenInvalid',
  'reset token is required.': 'resetTokenRequired',
  'this live class is already full.': 'classFull',
  'you are already enrolled in this live class.': 'alreadyEnrolled',
  'only approved teachers can create or submit learning content.': 'teacherApprovalRequired',
  'only approved teachers can use this teaching feature.': 'teacherApprovalRequired',
  'hints are not available for independent practice.': 'hintsUnavailable',
  'your session has expired. please sign in again to continue.': 'sessionExpired',
  'you do not have permission to perform this action. contact an administrator if needed.': 'permissionDenied',
  'we could not find the item you requested. please refresh and try again.': 'notFound',
  'the information you entered is incomplete or invalid. please check it and try again.': 'validationFailed',
  'the request could not be processed. please check the input and try again.': 'invalidRequest',
  'the file upload did not work. please check the file type and size, then try again.': 'uploadFailed',
  'the file is too large. please choose a smaller file and try again.': 'uploadTooLarge',
  'the service is temporarily unavailable. please try again later.': 'serverUnavailable',
  'you are doing that too often. please wait a moment and try again.': 'tooManyRequests',
  'daily ai request limit reached. please try again tomorrow.': 'aiDailyLimit',
  'the request conflicts with the current state. please refresh and try again.': 'conflict',
  'we could not complete that action. please try again shortly.': 'operationFailed',
}

const TRANSLATIONS: Readonly<Record<Exclude<ServerMessageLocale, 'en'>, Record<ServerMessageKey, string>>> = {
  ms: {
    invalidCredentials: 'E-mel atau kata laluan tidak sah.',
    accountUnavailable: 'Akaun ini tidak tersedia buat masa ini. Sila hubungi pentadbir platform.',
    emailRegistered: 'E-mel ini sudah didaftarkan.',
    passwordMismatch: 'Pengesahan kata laluan tidak sepadan.',
    passwordLength: 'Kata laluan mestilah antara 8 hingga 72 aksara.',
    currentPasswordIncorrect: 'Kata laluan semasa tidak betul.',
    newPasswordSame: 'Kata laluan baharu mestilah berbeza daripada kata laluan semasa.',
    resetTokenExpired: 'Pautan tetapan semula telah tamat tempoh. Sila minta pautan baharu.',
    resetTokenInvalid: 'Pautan tetapan semula tidak sah atau telah digunakan.',
    resetTokenRequired: 'Pautan tetapan semula diperlukan.',
    classFull: 'Kelas langsung ini sudah penuh.',
    alreadyEnrolled: 'Anda sudah mendaftar dalam kelas langsung ini.',
    teacherApprovalRequired: 'Hanya guru yang diluluskan boleh menggunakan fungsi pengajaran ini.',
    hintsUnavailable: 'Petunjuk tidak tersedia untuk latihan berdikari.',
    sessionExpired: 'Sesi anda telah tamat. Sila log masuk semula untuk meneruskan.',
    permissionDenied: 'Anda tiada kebenaran untuk melakukan tindakan ini. Hubungi pentadbir jika perlu.',
    notFound: 'Item yang anda minta tidak ditemui. Sila muat semula dan cuba lagi.',
    validationFailed: 'Maklumat yang dimasukkan tidak lengkap atau tidak sah. Sila semak dan cuba lagi.',
    invalidRequest: 'Permintaan tidak dapat diproses. Sila semak input dan cuba lagi.',
    uploadFailed: 'Muat naik fail tidak berjaya. Sila semak jenis dan saiz fail, kemudian cuba lagi.',
    uploadTooLarge: 'Fail terlalu besar. Sila pilih fail yang lebih kecil dan cuba lagi.',
    serverUnavailable: 'Perkhidmatan tidak tersedia buat sementara waktu. Sila cuba sebentar lagi.',
    tooManyRequests: 'Anda melakukannya terlalu kerap. Sila tunggu sebentar dan cuba lagi.',
    aiDailyLimit: 'Had permintaan AI harian telah dicapai. Sila cuba lagi esok.',
    conflict: 'Permintaan bercanggah dengan keadaan semasa. Sila muat semula dan cuba lagi.',
    operationFailed: 'Tindakan itu tidak dapat diselesaikan. Sila cuba sebentar lagi.',
  },
  'zh-CN': {
    invalidCredentials: '邮箱或密码不正确。',
    accountUnavailable: '此账号目前无法使用，请联系平台管理员。',
    emailRegistered: '该邮箱已注册。',
    passwordMismatch: '两次输入的密码不一致。',
    passwordLength: '密码长度必须为 8 到 72 个字符。',
    currentPasswordIncorrect: '当前密码不正确。',
    newPasswordSame: '新密码必须与当前密码不同。',
    resetTokenExpired: '重置链接已过期，请重新申请。',
    resetTokenInvalid: '重置链接无效或已被使用。',
    resetTokenRequired: '缺少重置链接。',
    classFull: '该直播课名额已满。',
    alreadyEnrolled: '你已报名该直播课。',
    teacherApprovalRequired: '只有通过审核的教师才能使用此教学功能。',
    hintsUnavailable: '独立练习不提供提示。',
    sessionExpired: '登录已过期，请重新登录后继续。',
    permissionDenied: '你无权执行此操作，如有需要请联系管理员。',
    notFound: '找不到你请求的内容，请刷新后重试。',
    validationFailed: '输入的信息不完整或无效，请检查后重试。',
    invalidRequest: '无法处理该请求，请检查输入后重试。',
    uploadFailed: '文件上传失败，请检查文件类型和大小后重试。',
    uploadTooLarge: '文件过大，请选择较小的文件后重试。',
    serverUnavailable: '服务暂时不可用，请稍后重试。',
    tooManyRequests: '操作过于频繁，请稍候再试。',
    aiDailyLimit: '今天的 AI 使用次数已达上限，请明天再试。',
    conflict: '该请求与当前状态冲突，请刷新后重试。',
    operationFailed: '无法完成该操作，请稍后重试。',
  },
}

function normalize(message: string): string {
  return message.replace(/\s+/g, ' ').trim().toLowerCase()
}

/** Translates a known backend message; returns the original message for English or unknown text. */
export function localizeServerMessage(message: string, locale: ServerMessageLocale): string {
  if (locale === 'en') return message
  const key = BACKEND_MESSAGES[normalize(message)]
  return key ? TRANSLATIONS[locale][key] : message
}

/** Exposed for tests: every mapped key must have a translation in every non-English locale. */
export const serverMessageCatalogue = { BACKEND_MESSAGES, TRANSLATIONS } as const
