/**
 * Backend messages of the learning plan module, translated like the rows in utils/serverMessages.ts:
 * [English exactly as the backend sends it, Bahasa Melayu, Simplified Chinese].
 */
export const PLAN_MESSAGES: readonly (readonly [en: string, ms: string, zh: string])[] = [
  // The goal and the level check of the setup (PlanRequests)
  ['Pick your main reason.', 'Pilih sebab utama anda.', '请选择你的主要原因。'],
  ['Keep your reason to 120 characters.', 'Hadkan sebab anda kepada 120 aksara.', '原因请不要超过 120 个字符。'],
  ['Pick fewer goals.', 'Pilih lebih sedikit matlamat.', '请少选几个目标。'],
  ['Keep your goal to 200 characters.', 'Hadkan matlamat anda kepada 200 aksara.', '目标请不要超过 200 个字符。'],
  ['Pick how much time you can give.', 'Pilih berapa banyak masa yang boleh anda berikan.', '请选择你能投入的时间。'],
  [
    'Pick up to 2 things to get better at.',
    'Pilih sehingga 2 perkara yang mahu anda perbaiki.',
    '最多选择 2 个想加强的方面。',
  ],
  [
    'Tell us how much Mandarin you know.',
    'Beritahu kami berapa banyak bahasa Mandarin yang anda tahu.',
    '请告诉我们你会多少中文。',
  ],
  ['Tell us about your goal.', 'Beritahu kami tentang matlamat anda.', '请告诉我们你的目标。'],

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
  ['Build your learning plan first.', 'Bina pelan pembelajaran anda dahulu.', '请先生成你的学习计划。'],
  [
    'This student is not in any of your classes.',
    'Pelajar ini tiada dalam mana-mana kelas anda.',
    '这名学生不在你的任何课程中。',
  ],

  // E-mail confirmation of a new account (EmailConfirmationService)
  ['Your email address is already confirmed.', 'Alamat e-mel anda sudah disahkan.', '你的邮箱地址已经确认过了。'],
  [
    'We just sent a link. Please wait a minute before asking for another one.',
    'Kami baru sahaja menghantar pautan. Sila tunggu seminit sebelum meminta pautan lain.',
    '我们刚刚发送了链接。请等一分钟再申请新的链接。',
  ],
  [
    'You asked for several links today. Please try again tomorrow.',
    'Anda telah meminta beberapa pautan hari ini. Sila cuba lagi esok.',
    '你今天已经申请了多次链接。请明天再试。',
  ],
  [
    'This confirmation link has expired. Send a new one from Home.',
    'Pautan pengesahan ini telah tamat tempoh. Hantar pautan baharu dari Laman Utama.',
    '这个确认链接已过期。请在主页重新发送。',
  ],
]
