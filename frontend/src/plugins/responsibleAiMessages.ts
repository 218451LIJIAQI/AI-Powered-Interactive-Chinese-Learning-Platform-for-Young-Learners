/** The Responsible AI guidance page. Namespace `responsibleAi`. */
export const responsibleAiMessages = {
  en: {
    title: 'Responsible AI guidance',
    intro: 'What the AI Helper does and does not do, how its answers are bounded and how your data is protected.',
    roleTitle: 'Optional support for beginners',
    roleCopy:
      'The AI Helper (talk with Xiaomei, ask SparkBot, quick quizzes, stories, pronunciation and handwriting practice) is optional support for Mandarin beginners. Lessons, practice and your teachers stay the main way to learn, and the AI Helper never earns XP.',
    limitsTitle: 'AI can be wrong',
    limitsCopy:
      'AI responses may contain errors and do not replace your teacher. Check important answers against your lessons or ask your teacher. SparkBot never gives official grades.',
    boundsTitle: 'How answers are bounded',
    boundsCopy:
      'Answers stay at beginner level (around HSK 1–2), short and about learning Mandarin. Requests about other things are politely refused. You can ask for explanations in English, Bahasa Melayu or Simplified Chinese, and there is a daily limit of AI requests.',
    voiceTitle: 'Voice, speaking and handwriting',
    voiceCopy:
      'When you practise speaking or handwriting, SparkBot gives a practice score with tips. Scores are for practice only: they are never shown to teachers and never count as grades.',
    dataTitle: 'What is sent to the AI provider',
    dataCopy:
      'Requests go from our server to the OpenAI Responses API (model {model}); your browser never contacts the provider directly. Your name, email and account ID are not sent, and emails, phone numbers and IC-like numbers typed into a request are removed first. Requests are sent with store=false, so the provider does not keep them for training or storage.',
    dataCopyNoModel:
      'Requests go from our server to the OpenAI Responses API; your browser never contacts the provider directly. Your name, email and account ID are not sent, and emails, phone numbers and IC-like numbers typed into a request are removed first. Requests are sent with store=false, so the provider does not keep them for training or storage.',
    safetyTitle: 'Safety checks',
    safetyCopy:
      'When an admin turns it on, a safety check screens requests and answers for unsafe content before they are shown, and a word filter blocks unsafe words in teacher content and AI requests.',
    reportTitle: 'Report an answer',
    reportCopy:
      'Every AI answer and score has a Report option. Choose a reason and add a note if you like. An admin reviews every report and tells you the outcome in your notifications.',
    disclaimerLabel: 'Shown with every AI answer',
    disclaimer: 'AI responses may contain errors and do not replace your teacher.',
    signInForHelper: 'Students: sign in to use the AI Helper',
    openHelper: 'Open the AI Helper',
  },
  ms: {
    title: 'Panduan AI Bertanggungjawab',
    intro:
      'Apa yang dilakukan dan tidak dilakukan oleh Pembantu AI, bagaimana jawapannya dihadkan dan bagaimana data anda dilindungi.',
    roleTitle: 'Sokongan pilihan untuk pemula',
    roleCopy:
      'Pembantu AI (berbual dengan Xiaomei, tanya SparkBot, kuiz ringkas, cerita, latihan sebutan dan tulisan tangan) ialah sokongan pilihan untuk pemula Mandarin. Pelajaran, latihan dan guru anda kekal sebagai cara utama untuk belajar, dan Pembantu AI tidak sekali-kali memberi XP.',
    limitsTitle: 'AI boleh tersilap',
    limitsCopy:
      'Jawapan AI mungkin mengandungi kesilapan dan tidak menggantikan guru anda. Semak jawapan penting dengan pelajaran anda atau tanya guru anda. SparkBot tidak sekali-kali memberi gred rasmi.',
    boundsTitle: 'Bagaimana jawapan dihadkan',
    boundsCopy:
      'Jawapan kekal pada tahap pemula (sekitar HSK 1–2), ringkas dan berkaitan pembelajaran Mandarin. Permintaan tentang perkara lain ditolak dengan sopan. Anda boleh meminta penerangan dalam bahasa Inggeris, Bahasa Melayu atau Bahasa Cina Ringkas, dan terdapat had harian permintaan AI.',
    voiceTitle: 'Suara, pertuturan dan tulisan tangan',
    voiceCopy:
      'Apabila anda berlatih bertutur atau menulis, SparkBot memberi skor latihan beserta tip. Skor hanya untuk latihan: ia tidak sekali-kali ditunjukkan kepada guru dan tidak dikira sebagai gred.',
    dataTitle: 'Apa yang dihantar kepada penyedia AI',
    dataCopy:
      'Permintaan dihantar daripada pelayan kami kepada OpenAI Responses API (model {model}); pelayar anda tidak menghubungi penyedia secara terus. Nama, e-mel dan ID akaun anda tidak dihantar, dan e-mel, nombor telefon serta nombor seperti IC yang ditaip dalam permintaan dibuang terlebih dahulu. Permintaan dihantar dengan store=false, jadi penyedia tidak menyimpannya untuk latihan atau penyimpanan.',
    dataCopyNoModel:
      'Permintaan dihantar daripada pelayan kami kepada OpenAI Responses API; pelayar anda tidak menghubungi penyedia secara terus. Nama, e-mel dan ID akaun anda tidak dihantar, dan e-mel, nombor telefon serta nombor seperti IC yang ditaip dalam permintaan dibuang terlebih dahulu. Permintaan dihantar dengan store=false, jadi penyedia tidak menyimpannya untuk latihan atau penyimpanan.',
    safetyTitle: 'Semakan keselamatan',
    safetyCopy:
      'Apabila pentadbir menghidupkannya, semakan keselamatan menapis permintaan dan jawapan untuk kandungan tidak selamat sebelum ia dipaparkan, dan penapis perkataan menyekat perkataan tidak selamat dalam kandungan guru dan permintaan AI.',
    reportTitle: 'Laporkan jawapan',
    reportCopy:
      'Setiap jawapan dan skor AI mempunyai pilihan Laporkan. Pilih sebab dan tambah nota jika mahu. Pentadbir menyemak setiap laporan dan memaklumkan keputusannya dalam pemberitahuan anda.',
    disclaimerLabel: 'Dipaparkan bersama setiap jawapan AI',
    disclaimer: 'Jawapan AI mungkin mengandungi kesilapan dan tidak menggantikan guru anda.',
    signInForHelper: 'Pelajar: log masuk untuk menggunakan Pembantu AI',
    openHelper: 'Buka Pembantu AI',
  },
  'zh-CN': {
    title: '负责任的 AI 指南',
    intro: 'AI 助手能做什么、不能做什么，它的回答受哪些限制，以及你的数据如何受到保护。',
    roleTitle: '为初学者提供的可选支持',
    roleCopy:
      'AI 助手（和小美聊天、向 SparkBot 提问、小测验、故事、发音和书写练习）是为中文初学者提供的可选支持。课程、练习和老师仍然是主要的学习方式，AI 助手不计 XP。',
    limitsTitle: 'AI 可能出错',
    limitsCopy:
      'AI 的回答可能有误，不能取代你的老师。重要的答案请对照课程内容或询问老师。SparkBot 绝不会给出正式成绩。',
    boundsTitle: '回答受到哪些限制',
    boundsCopy:
      '回答保持初学者水平（约 HSK 1–2），简短并与中文学习相关。与学习无关的请求会被礼貌地拒绝。你可以要求用英语、马来语或简体中文解释，AI 请求每天有使用上限。',
    voiceTitle: '语音、口语和书写',
    voiceCopy: '当你练习口语或书写时，SparkBot 会给出练习评分和建议。评分仅用于练习：绝不会展示给老师，也不计入成绩。',
    dataTitle: '哪些内容会发送给 AI 提供方',
    dataCopy:
      '请求由我们的服务器发送到 OpenAI Responses API（模型 {model}）；你的浏览器不会直接联系提供方。你的姓名、邮箱和账号 ID 不会被发送，请求中输入的邮箱、电话号码和类似身份证号码的数字会先被移除。请求带有 store=false，因此提供方不会将其保存或用于训练。',
    dataCopyNoModel:
      '请求由我们的服务器发送到 OpenAI Responses API；你的浏览器不会直接联系提供方。你的姓名、邮箱和账号 ID 不会被发送，请求中输入的邮箱、电话号码和类似身份证号码的数字会先被移除。请求带有 store=false，因此提供方不会将其保存或用于训练。',
    safetyTitle: '安全检查',
    safetyCopy:
      '当管理员开启时，安全检查会在显示前筛查请求和回答中的不安全内容，敏感词过滤器会拦截老师内容和 AI 请求中的不安全词语。',
    reportTitle: '举报回答',
    reportCopy:
      '每个 AI 回答和评分都有“举报”选项。选择原因，并可添加备注。管理员会审核每条举报，并在通知中告诉你处理结果。',
    disclaimerLabel: '每个 AI 回答都会显示',
    disclaimer: 'AI 的回答可能有误，不能取代你的老师。',
    signInForHelper: '学生：登录后使用 AI 助手',
    openHelper: '打开 AI 助手',
  },
}
