import { aiDisclosureMessages } from './aiDisclosureMessages'

const xiaomeiContext = {
  en: ' In Talk with Xiaomei, also your plan goal and the short notes she remembers (forget them in any chat summary).',
  ms: ' Dalam Berbual dengan Xiaomei, juga matlamat pelan anda dan nota ringkas yang diingatinya (lupakan nota itu dalam mana-mana ringkasan perbualan).',
  'zh-CN': '在“和小美聊天”中，还会用到你的计划目标和她记住的简短内容（你可以在任何一次聊天总结中让她忘记）。',
} as const

/** The Responsible AI guidance (the public page and the learner's drawer: useResponsibleAiGuidance). Namespace `responsibleAi`. */
export const responsibleAiMessages = {
  en: {
    title: 'Responsible AI guidance',
    roleTitle: 'Optional support for beginners',
    roleCopy:
      'The AI Helper is optional. You can finish every lesson, practice set and class without it, and you can turn AI hints off on your Account page.',
    limitsTitle: 'AI can be wrong',
    // The first mention of Xiaomei on the public site: it says who she is.
    limitsCopy:
      'SparkBot and Xiaomei (the speaking partner in AI Helper) write their replies with an AI model. A reply can sound sure and still be wrong, most often with tones, names and cultural details. Check important answers against your lessons, and if an answer looks wrong, use Report so an admin can review it.',
    boundsTitle: 'How answers are bounded',
    boundsCopy:
      'Answers stay at beginner level (around HSK 1–2), short, about learning Mandarin and labelled as AI. Requests about other things are politely refused. You can ask for explanations in English, Bahasa Melayu or Simplified Chinese, and there is a daily limit of AI requests.',
    voiceTitle: 'Voice, speaking and handwriting',
    voiceCopy:
      'Pronunciation and handwriting checks give you a practice score and feedback. XP and rank come from your learning progress.',
    dataTitle: 'What is sent to the AI provider',
    // The data section names the provider the server reports (useResponsibleAiGuidance): what is sent, the target, then
    // what that provider does with it.
    // The second sentence: what Talk with Xiaomei adds (S7.14), the same for every provider.
    dataWhat:
      'The request uses the text or handwriting photo you submit, plus relevant context such as your level and the lesson topic.' +
      xiaomeiContext.en,
    dataWhatText:
      'The request uses the text you submit, plus relevant context such as your level and the lesson topic.' +
      xiaomeiContext.en,
    dataTarget: 'Requests go from our server to {target}; your browser never contacts the provider directly.',
    targetOpenAi: 'the OpenAI Responses API',
    targetModel: '{target} (model {model})',
    targetOther: 'the Chat Completions API of {provider}',
    dataMinimise: aiDisclosureMessages.en.piiFilter + ' Avoid including personal information in what you send.',
    dataUnknown:
      'When the AI Helper is connected to an AI provider, requests go from our server to that provider; your browser never contacts it directly.',
    safetyTitle: 'Safety checks',
    safetyCopy:
      'The safety checks are on unless an admin turns them off. Your AI requests and the AI answers are screened against a list of unsafe words: a request that fails is not sent and an answer that fails is not shown.',
    reportTitle: 'Report an answer',
    reportCopy:
      'Use Report on any AI reply, quiz, story or score: choose a reason and add a note if you like. An admin reads every report and replies in AI Helper › Ask SparkBot › My reports.',
    disclaimerLabel: 'Shown with every AI answer:',
  },
  ms: {
    title: 'Panduan AI Bertanggungjawab',
    roleTitle: 'Sokongan pilihan untuk pemula',
    roleCopy:
      'Pembantu AI adalah pilihan. Anda boleh menyelesaikan setiap pelajaran, set latihan dan kelas tanpanya, dan anda boleh mematikan petunjuk AI di halaman Akaun anda.',
    limitsTitle: 'AI boleh tersilap',
    limitsCopy:
      'SparkBot dan Xiaomei (rakan bertutur dalam Pembantu AI) menulis balasan mereka dengan model AI. Balasan boleh kedengaran yakin tetapi masih salah, paling kerap tentang nada, nama dan butiran budaya. Semak jawapan penting dengan pelajaran anda, dan jika jawapan kelihatan salah, gunakan Laporkan supaya pentadbir dapat menyemaknya.',
    boundsTitle: 'Bagaimana jawapan dihadkan',
    boundsCopy:
      'Jawapan kekal pada tahap pemula (sekitar HSK 1–2), ringkas, berkaitan pembelajaran Mandarin dan dilabel sebagai AI. Permintaan tentang perkara lain ditolak dengan sopan. Anda boleh meminta penerangan dalam bahasa Inggeris, Bahasa Melayu atau Bahasa Cina Ringkas, dan terdapat had harian permintaan AI.',
    voiceTitle: 'Suara, pertuturan dan tulisan tangan',
    voiceCopy:
      'Semakan sebutan dan tulisan tangan memberi anda skor latihan dan maklum balas. XP dan pangkat datang daripada kemajuan pembelajaran anda.',
    dataTitle: 'Apa yang dihantar kepada penyedia AI',
    dataWhat:
      'Permintaan menggunakan teks atau foto tulisan tangan yang anda hantar, serta maklumat pembelajaran yang berkaitan seperti tahap anda dan topik pelajaran.' +
      xiaomeiContext.ms,
    dataWhatText:
      'Permintaan menggunakan teks yang anda hantar, serta maklumat pembelajaran yang berkaitan seperti tahap anda dan topik pelajaran.' +
      xiaomeiContext.ms,
    dataTarget:
      'Permintaan dihantar daripada pelayan kami kepada {target}; pelayar anda tidak menghubungi penyedia secara terus.',
    targetOpenAi: 'OpenAI Responses API',
    targetModel: '{target} (model {model})',
    targetOther: 'API Chat Completions {provider}',
    dataMinimise:
      aiDisclosureMessages.ms.piiFilter + ' Elakkan memasukkan maklumat peribadi dalam apa yang anda hantar.',
    dataUnknown:
      'Apabila Pembantu AI disambungkan kepada penyedia AI, permintaan dihantar daripada pelayan kami kepada penyedia itu; pelayar anda tidak menghubunginya secara terus.',
    safetyTitle: 'Semakan keselamatan',
    safetyCopy:
      'Semakan keselamatan dihidupkan melainkan pentadbir mematikannya. Permintaan AI anda dan jawapan AI disemak berdasarkan senarai perkataan tidak selamat: permintaan yang gagal tidak dihantar dan jawapan yang gagal tidak dipaparkan.',
    reportTitle: 'Laporkan jawapan',
    reportCopy:
      'Gunakan Laporkan pada mana-mana balasan, kuiz, cerita atau skor AI: pilih sebab dan tambah nota jika mahu. Pentadbir membaca setiap laporan dan membalas dalam Pembantu AI › Tanya SparkBot › Laporan saya.',
    disclaimerLabel: 'Dipaparkan bersama setiap jawapan AI:',
  },
  'zh-CN': {
    title: '负责任 AI 指南',
    roleTitle: '为初学者提供的可选支持',
    roleCopy: 'AI 助手是可选的。不用它，你也能完成每个课时、每组练习和每个课堂；你还可以在“账号”页面关闭 AI 提示。',
    limitsTitle: 'AI 可能出错',
    limitsCopy:
      'SparkBot 和小美（AI 助手里的口语伙伴）都用 AI 模型撰写回复。回复听起来很肯定，也可能是错的，最常见于声调、名字和文化细节。重要的答案请对照课时内容；如果回答看起来不对，请使用“举报”，让管理员审核。',
    boundsTitle: '回答受到哪些限制',
    boundsCopy:
      '回答保持初学者水平（约 HSK 1–2），简短、与中文学习相关，并标注为 AI 生成。与学习无关的请求会被礼貌地拒绝。你可以要求用英语、马来语或简体中文解释，AI 请求每天有使用上限。',
    voiceTitle: '语音、口语和书写',
    voiceCopy: '发音和书写都会提供练习评分和反馈。你的 XP 和段位根据学习进度计算。',
    dataTitle: '哪些内容会发送给 AI 提供方',
    dataWhat: '请求会使用你提交的文字或手写照片，以及相关学习信息，例如你的水平和课时主题。' + xiaomeiContext['zh-CN'],
    dataWhatText: '请求会使用你提交的文字及相关学习信息，例如你的水平和课时主题。' + xiaomeiContext['zh-CN'],
    dataTarget: '请求由我们的服务器发送到 {target}；你的浏览器不会直接联系提供方。',
    targetOpenAi: 'OpenAI Responses API',
    targetModel: '{target}（模型 {model}）',
    targetOther: '{provider}的 Chat Completions API',
    dataMinimise: aiDisclosureMessages['zh-CN'].piiFilter + '请避免在你发送的内容中提供个人信息。',
    dataUnknown: '当 AI 助手连接到 AI 提供方时，请求会由我们的服务器发送给该提供方；你的浏览器不会直接联系它。',
    safetyTitle: '安全检查',
    safetyCopy:
      '安全检查默认开启，除非管理员将其关闭。你的 AI 请求和 AI 回答都会对照不安全词语列表进行筛查：未通过的请求不会发送，未通过的回答不会显示。',
    reportTitle: '举报回答',
    reportCopy:
      '任何 AI 回复、测验、故事或评分都可以使用“举报”：选择原因，并可添加备注。管理员会阅读每条举报，并在“AI 助手 › 问 SparkBot › 我的举报”中回复。',
    disclaimerLabel: '每个 AI 回答都会显示：',
  },
} as const
