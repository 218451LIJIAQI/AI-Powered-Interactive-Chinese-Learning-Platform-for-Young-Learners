/** Shared AI-provider and data-handling disclosures reused by Privacy Notice and Responsible AI guidance. */
export const aiDisclosureMessages = {
  en: {
    openAi:
      'Responses API requests use store=false to disable storage for later response retrieval. This does not guarantee zero retention: provider safety logs may still retain content. OpenAI does not use API data for training by default; an account can opt in.',
    other: 'How long {provider} keeps requests, and whether it uses them for training, is set by its own terms.',
    off: 'The AI text and photo service is currently unavailable.',
    pronunciation:
      'When you submit a pronunciation check, our server sends the recording and the target text to Tencent Cloud for assessment. MandarinSpark saves the result.',
    liveVideo:
      'When you start a live call with Xiaomei, Tavus receives the call audio and any recent messages from the current conversation. Your camera starts off.',
    providerDeepseek: 'DeepSeek',
    providerCompatible: 'an OpenAI-compatible AI service',
    piiFilter:
      'A filter removes recognizable email addresses, phone numbers and IC-like numbers from text, but may miss personal details.',
  },
  ms: {
    openAi:
      'Permintaan Responses API menggunakan store=false untuk mematikan penyimpanan bagi mendapatkan semula respons. Ini tidak menjamin tiada penyimpanan: log keselamatan penyedia masih boleh menyimpan kandungan. Secara lalai, OpenAI tidak menggunakan data API untuk latihan; pemilik akaun boleh memilih untuk berkongsi data.',
    other:
      'Tempoh {provider} menyimpan permintaan, dan sama ada ia menggunakannya untuk latihan, ditentukan oleh termanya sendiri.',
    off: 'Perkhidmatan AI untuk teks dan foto tidak tersedia buat masa ini.',
    pronunciation:
      'Apabila anda menghantar semakan sebutan, pelayan kami menghantar rakaman dan teks sasaran kepada Tencent Cloud untuk dinilai. MandarinSpark menyimpan hasil penilaian.',
    liveVideo:
      'Apabila anda memulakan panggilan langsung dengan Xiaomei, Tavus menerima audio panggilan dan mesej terkini daripada perbualan semasa. Kamera anda dimatikan apabila panggilan bermula.',
    providerDeepseek: 'DeepSeek',
    providerCompatible: 'perkhidmatan AI yang serasi dengan OpenAI',
    piiFilter:
      'Penapis membuang alamat e-mel, nombor telefon dan nombor seperti IC yang dapat dikenal pasti dalam teks, tetapi mungkin terlepas maklumat peribadi.',
  },
  'zh-CN': {
    openAi:
      'Responses API 请求带有 store=false，以关闭供后续获取的响应存储。这不保证零保留：提供方的安全日志仍可能保留内容。OpenAI 默认不将 API 数据用于训练；账号持有人可以选择加入数据共享。',
    other: '{provider}保存请求的时长以及是否将其用于训练，由其自身条款决定。',
    off: 'AI 文字和照片服务暂不可用。',
    pronunciation: '提交发音评测时，我们的服务器会将录音和目标文字发送给腾讯云进行评测。MandarinSpark 会保存评测结果。',
    liveVideo: '开始和小美实时通话时，Tavus 会收到通话语音和当前聊天的近期消息。通话开始时，你的摄像头默认关闭。',
    providerDeepseek: 'DeepSeek',
    providerCompatible: '兼容 OpenAI 的 AI 服务',
    piiFilter: '过滤器会移除文字中能够识别的邮箱地址、电话号码和类似身份证号码的数字，但可能遗漏个人信息。',
  },
} as const
