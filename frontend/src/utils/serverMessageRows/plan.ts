import type { MessageRow } from './types'

/** Canonical backend refusal when updating or confirming without an existing learning plan. */
export const BUILD_PLAN_FIRST_MESSAGE = 'Build your learning plan first.'

/** Canonical backend refusal when confirming a draft whose goal date has already passed. */
export const GOAL_DATE_PASSED_MESSAGE = "This plan's goal date has passed. Build it again."

/** Canonical backend refusal when resending confirmation for an already confirmed learner address. */
export const EMAIL_ALREADY_CONFIRMED_MESSAGE = 'Your email address is already confirmed.'

/** Backend messages of the learning plan module, translated like the rows in utils/serverMessages.ts. */
export const PLAN_MESSAGES: readonly MessageRow[] = [
  // Building and changing the plan (LearningPlanService, LevelCheckService)
  ['Finish the level check first.', 'Selesaikan semakan tahap dahulu.', '请先完成水平测试。'],
  [
    'Tell us your reason in your own words.',
    'Beritahu sebab anda dengan kata-kata anda sendiri.',
    '请用你自己的话说说原因。',
  ],
  [
    'Pick a date in the future, within 2 years.',
    'Pilih tarikh pada masa hadapan, dalam tempoh 2 tahun.',
    '请选择未来 2 年内的日期。',
  ],
  [BUILD_PLAN_FIRST_MESSAGE, 'Bina pelan pembelajaran anda dahulu.', '请先生成你的学习计划。'],
  ['Pick a class from your plan.', 'Pilih kelas daripada pelan anda.', '请从你的计划中选择一个课堂。'],
  [
    'This plan was built more than a day ago. Build it again.',
    'Pelan ini dibina lebih daripada sehari yang lalu. Bina semula pelan.',
    '这个计划是一天多以前生成的。请重新生成。',
  ],
  [
    GOAL_DATE_PASSED_MESSAGE,
    'Tarikh sasaran pelan ini telah berlalu. Bina semula pelan.',
    '这个计划的目标日期已过。请重新生成。',
  ],
  [
    'Your learning plan changed while this request was running. Reload it and try again.',
    'Pelan pembelajaran anda berubah semasa permintaan ini diproses. Muat semula pelan dan cuba lagi.',
    '处理此请求期间，你的学习计划已更新。请重新加载计划后再试。',
  ],

  // E-mail confirmation of a new account (EmailConfirmationService)
  [EMAIL_ALREADY_CONFIRMED_MESSAGE, 'Alamat e-mel anda sudah disahkan.', '你的邮箱地址已经确认过了。'],
  [
    'This confirmation link has expired. Send a new one from Home.',
    'Pautan pengesahan ini telah tamat tempoh. Hantar pautan baharu dari Laman Utama.',
    '这个确认链接已过期。请在首页重新发送。',
  ],
  // "Send the link again": the two limits (FriendlyErrorMessageResolver lets both through).
  [
    'We just sent a link. Please wait a minute before asking for another one.',
    'Kami baru sahaja menghantar pautan. Sila tunggu seminit sebelum meminta pautan lain.',
    '我们刚刚发送了链接。请等一分钟后再申请新的链接。',
  ],
  [
    'You asked for several links today. Please try again tomorrow.',
    'Anda telah meminta beberapa pautan hari ini. Sila cuba lagi esok.',
    '你今天已经申请了多次链接。请明天再试。',
  ],
]
