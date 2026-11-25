import { aiDisclosureMessages } from './aiDisclosureMessages'

/** The Terms of Service and Privacy Notice, and the layout strings every legal page shares. Namespace `legal`. */
export const legalMessages = {
  en: {
    onThisPage: 'On this page',
    lastUpdated: 'Last updated {date}',
    terms: {
      title: 'Terms of Service',
      accountsTitle: 'Accounts and roles',
      accountsCopy:
        'MandarinSpark is for Mandarin beginners aged 18 and over. When you create an account you confirm that you are 18 or older. You need an account to save your progress. Learners learn, and admins create classes and look after accounts. Keep your sign-in details private.',
      learningTitle: 'Learning content and AI support',
      learningCopy:
        'Lessons, practice sets and classes teach beginner Mandarin; the MandarinSpark team makes and checks every class. SparkBot and the other AI features are optional support: their answers and practice scores can be wrong, so check important answers in your lessons. A practice score is never an official grade.',
      freeTitle: 'Classes are free',
      freeCopy:
        'Learn, Practice, the AI Helper and every class are free. There are no purchases, subscriptions or paid tiers, and we do not sell learning content.',
      safetyTitle: 'Safe use',
      safetyCopy:
        'Be respectful in messages to support and in AI chats. Do not share other people’s personal details, upload harmful content, share your account, try to get into other accounts, or misuse the AI Helper or get around its safety checks. Admins may deactivate an account that breaks these rules; the reason is recorded.',
      supportTitle: 'Support and changes',
      supportCopy:
        'Contact us from the Help page if something goes wrong. MandarinSpark is a Final Year Project prototype, and these terms may change while it is being evaluated; when they do, the date at the top changes.',
    },
    privacy: {
      title: 'Privacy Notice',
      dataTitle: 'Data we collect',
      dataCopy:
        'Account data (name, email, role, language and notification choices); learning data (lessons, practice, quiz attempts, XP, streaks, badges and your learning plan); the classes you join; AI Helper requests, practice scores and the reports you send; your messages to support; notifications; and an audit log of admin actions.',
      purposeTitle: 'Why we use it',
      purposeCopy:
        'To run your account, deliver lessons, practice and classes, build your learning plan and show your progress, answer support requests and AI reports, keep the platform safe and evaluate the prototype.',
      aiTitle: 'AI provider',
      // One of these, by the provider the server reports (PrivacyPage).
      aiOpenAi:
        'When you use the AI Helper, the text or handwriting photo you submit, plus your level and the lesson topic, is sent to the OpenAI Responses API through our server, never straight from your browser. ' +
        aiDisclosureMessages.en.openAi,
      aiOther:
        'When you use the AI Helper, the text you submit, plus your level and the lesson topic, is sent to {provider} through our server, never straight from your browser. ' +
        aiDisclosureMessages.en.other,
      aiUnknown:
        'When the AI Helper is connected to an AI provider, what you submit, plus your level and the lesson topic, is sent to that provider through our server, never straight from your browser.',
      // After the provider's paragraph whenever requests can leave the server (S7.14).
      aiXiaomei:
        'Talk with Xiaomei also sends your plan goal and the short notes she remembers from earlier chats; you can forget them in any chat summary.',
      minimisationTitle: 'Data minimisation',
      minimisationCopy:
        'We ask only for what a feature needs, and we never attach your name, email address or account profile to AI requests. ' +
        aiDisclosureMessages.en.piiFilter +
        ' Avoid including personal information in text, recordings or images. A screenshot you add to a problem report is seen only by support.',
      retentionTitle: 'Retention',
      retentionCopy:
        'Data is kept for the prototype’s evaluation period and then deleted. When you delete your account, your name, email, support requests (with their screenshots) and notifications are removed straight away, and your learning records stay only without your name.',
      rightsTitle: 'Your rights',
      rightsCopy:
        'You can see and correct your details on the Account page, change your email by confirming the new address, and delete your account there at any time. Admin accounts are deactivated by another admin.',
      lawTitle: 'Applicable law',
      lawCopy: 'Personal data is handled with reference to Malaysia’s Personal Data Protection Act 2010 (PDPA).',
      thirdPartiesTitle: 'Third-party services',
      thirdPartiesCopy:
        'The web app loads fonts from Google Fonts. The configured email service delivers account emails, support replies and the optional daily summary for admins.',
      contactTitle: 'Contact',
      contactCopy:
        'Questions about privacy, or a deletion request? Use the Contact us form on the Help page. Never send passwords.',
    },
  },
  ms: {
    onThisPage: 'Dalam halaman ini',
    lastUpdated: 'Kemas kini terakhir: {date}',
    terms: {
      title: 'Terma Perkhidmatan',
      accountsTitle: 'Akaun dan peranan',
      accountsCopy:
        'MandarinSpark adalah untuk pemula Mandarin berumur 18 tahun ke atas. Apabila mencipta akaun, anda mengesahkan bahawa anda berumur 18 tahun atau lebih. Anda memerlukan akaun untuk menyimpan kemajuan anda. Pelajar belajar, dan pentadbir mencipta kelas serta menguruskan akaun. Rahsiakan butiran log masuk anda.',
      learningTitle: 'Kandungan pembelajaran dan bantuan AI',
      learningCopy:
        'Pelajaran, set latihan dan kelas mengajar Mandarin peringkat pemula; pasukan MandarinSpark membuat dan menyemak setiap kelas. SparkBot dan ciri AI lain ialah sokongan pilihan: jawapan dan skor latihannya boleh tersilap, jadi semak jawapan penting dalam pelajaran anda. Skor latihan tidak sekali-kali menjadi gred rasmi.',
      freeTitle: 'Kelas adalah percuma',
      freeCopy:
        'Belajar, Latihan, Pembantu AI dan setiap kelas adalah percuma. Tiada pembelian, langganan atau peringkat berbayar, dan kami tidak menjual kandungan pembelajaran.',
      safetyTitle: 'Penggunaan selamat',
      safetyCopy:
        'Bersikap hormat dalam mesej kepada pasukan sokongan dan dalam sembang AI. Jangan berkongsi butiran peribadi orang lain, memuat naik kandungan berbahaya, berkongsi akaun anda, cuba memasuki akaun lain, atau menyalahgunakan Pembantu AI atau memintas semakan keselamatannya. Pentadbir boleh menyahaktifkan akaun yang melanggar peraturan ini; sebabnya direkodkan.',
      supportTitle: 'Sokongan dan perubahan',
      supportCopy:
        'Hubungi kami dari halaman Bantuan jika ada masalah. MandarinSpark ialah prototaip Projek Tahun Akhir, dan terma ini mungkin berubah semasa ia dinilai; apabila ia berubah, tarikh di bahagian atas turut berubah.',
    },
    privacy: {
      title: 'Notis Privasi',
      dataTitle: 'Data yang kami kumpul',
      dataCopy:
        'Data akaun (nama, e-mel, peranan, bahasa dan pilihan pemberitahuan); data pembelajaran (pelajaran, latihan, cubaan kuiz, XP, rentetan, lencana dan pelan pembelajaran anda); kelas yang anda sertai; permintaan Pembantu AI, skor latihan dan laporan yang anda hantar; mesej anda kepada pasukan sokongan; pemberitahuan; dan log audit tindakan pentadbir.',
      purposeTitle: 'Mengapa kami menggunakannya',
      purposeCopy:
        'Untuk mengendalikan akaun anda, menyampaikan pelajaran, latihan dan kelas, membina pelan pembelajaran dan menunjukkan kemajuan anda, menjawab permintaan sokongan dan laporan AI, memastikan platform selamat dan menilai prototaip.',
      aiTitle: 'Penyedia AI',
      aiOpenAi:
        'Apabila anda menggunakan Pembantu AI, teks atau foto tulisan tangan yang anda hantar, serta tahap anda dan topik pelajaran, dihantar kepada OpenAI Responses API melalui pelayan kami, tidak terus daripada pelayar anda. ' +
        aiDisclosureMessages.ms.openAi,
      aiOther:
        'Apabila anda menggunakan Pembantu AI, teks yang anda hantar, serta tahap anda dan topik pelajaran, dihantar kepada {provider} melalui pelayan kami, tidak terus daripada pelayar anda. ' +
        aiDisclosureMessages.ms.other,
      aiUnknown:
        'Apabila Pembantu AI disambungkan kepada penyedia AI, apa yang anda hantar, serta tahap anda dan topik pelajaran, dihantar kepada penyedia itu melalui pelayan kami, tidak terus daripada pelayar anda.',
      aiXiaomei:
        'Berbual dengan Xiaomei juga menghantar matlamat pelan anda dan nota ringkas yang diingatinya daripada perbualan terdahulu; anda boleh melupakannya dalam mana-mana ringkasan perbualan.',
      minimisationTitle: 'Peminimuman data',
      minimisationCopy:
        'Kami hanya meminta apa yang diperlukan oleh sesuatu ciri, dan kami tidak sekali-kali melampirkan nama, alamat e-mel atau profil akaun anda pada permintaan AI. ' +
        aiDisclosureMessages.ms.piiFilter +
        ' Elakkan memasukkan maklumat peribadi dalam teks, rakaman atau imej. Tangkapan skrin yang anda tambah pada laporan masalah hanya dilihat oleh pasukan sokongan.',
      retentionTitle: 'Penyimpanan',
      retentionCopy:
        'Data disimpan sepanjang tempoh penilaian prototaip dan kemudian dipadam. Apabila anda memadam akaun, nama, e-mel, permintaan sokongan (bersama tangkapan skrinnya) dan pemberitahuan anda dibuang serta-merta, dan rekod pembelajaran anda hanya kekal tanpa nama anda.',
      rightsTitle: 'Hak anda',
      rightsCopy:
        'Anda boleh melihat dan membetulkan butiran anda di halaman Akaun, menukar e-mel dengan mengesahkan alamat baharu, dan memadam akaun anda di sana pada bila-bila masa. Akaun pentadbir dinyahaktifkan oleh pentadbir lain.',
      lawTitle: 'Undang-undang yang terpakai',
      lawCopy: 'Data peribadi dikendalikan dengan merujuk kepada Akta Perlindungan Data Peribadi 2010 (PDPA) Malaysia.',
      thirdPartiesTitle: 'Perkhidmatan pihak ketiga',
      thirdPartiesCopy:
        'Aplikasi web memuatkan fon daripada Google Fonts. Perkhidmatan e-mel yang dikonfigurasikan menghantar e-mel akaun, balasan sokongan dan ringkasan harian pilihan untuk pentadbir.',
      contactTitle: 'Hubungi',
      contactCopy:
        'Ada soalan tentang privasi, atau permintaan pemadaman? Gunakan borang Hubungi kami di halaman Bantuan. Jangan sekali-kali menghantar kata laluan.',
    },
  },
  'zh-CN': {
    onThisPage: '本页内容',
    lastUpdated: '最后更新：{date}',
    terms: {
      title: '服务条款',
      accountsTitle: '账号与角色',
      accountsCopy:
        'MandarinSpark 面向 18 岁及以上的中文初学者。创建账号即表示你确认自己已年满 18 岁。你需要账号来保存学习进度。学习者在这里学习，管理员负责制作课堂和管理账号。请妥善保管你的登录信息。',
      learningTitle: '学习内容与 AI 支持',
      learningCopy:
        '课时、练习和课堂教授初级中文；每个课堂都由 MandarinSpark 团队制作和检查。SparkBot 和其他 AI 功能是可选的辅助：它们的回答和练习评分可能有误，重要的答案请对照课时内容。练习评分绝不是正式成绩。',
      freeTitle: '课堂免费',
      freeCopy: '“学习”、“练习”、AI 助手以及所有课堂都免费。没有任何购买、订阅或付费等级，我们也不出售学习内容。',
      safetyTitle: '安全使用',
      safetyCopy:
        '在给支持团队的留言和 AI 对话中请保持尊重。不得分享他人的个人信息、上传有害内容、共享账号、试图进入他人账号，或滥用 AI 助手、绕过其安全检查。管理员可以停用违反这些规则的账号，并会记录原因。',
      supportTitle: '支持与变更',
      supportCopy:
        '遇到问题时，请在帮助页面联系我们。MandarinSpark 是一个毕业设计原型，在评估期间这些条款可能会更新；更新时，页面顶部的日期也会随之更改。',
    },
    privacy: {
      title: '隐私声明',
      dataTitle: '我们收集的数据',
      dataCopy:
        '账号数据（姓名、邮箱、角色、语言和通知设置）；学习数据（课时、练习、测验记录、XP、连续学习记录、徽章和学习计划）；你加入的课堂；AI 助手请求、练习评分以及你提交的举报；你给支持团队的留言；通知；以及管理员操作的审计日志。',
      purposeTitle: '使用目的',
      purposeCopy:
        '用于运行你的账号、提供课时、练习和课堂、制定学习计划并展示学习进度、回复支持请求和 AI 举报、保障平台安全以及评估原型。',
      aiTitle: 'AI 服务提供方',
      aiOpenAi:
        '当你使用 AI 助手时，你提交的文字或书写照片，以及你的水平和课时主题，会通过我们的服务器（而不是直接从你的浏览器）发送到 OpenAI Responses API。' +
        aiDisclosureMessages['zh-CN'].openAi,
      aiOther:
        '当你使用 AI 助手时，你提交的文字，以及你的水平和课时主题，会通过我们的服务器（而不是直接从你的浏览器）发送到{provider}。' +
        aiDisclosureMessages['zh-CN'].other,
      aiUnknown:
        '当 AI 助手连接到 AI 提供方时，你提交的内容，以及你的水平和课时主题，会通过我们的服务器（而不是直接从你的浏览器）发送给该提供方。',
      aiXiaomei:
        '“和小美聊天”还会发送你的计划目标和她从之前的聊天中记住的简短内容；你可以在任何一次聊天总结中让她忘记。',
      minimisationTitle: '数据最小化',
      minimisationCopy:
        '我们只收集功能所需的数据，绝不会把你的姓名、邮箱地址或账号资料附加到 AI 请求中。' +
        aiDisclosureMessages['zh-CN'].piiFilter +
        '请避免在文字、录音或图片中提供个人信息。你在问题报告中添加的截图只有支持团队能看到。',
      retentionTitle: '保存期限',
      retentionCopy:
        '数据会在原型评估期间保存，之后删除。当你删除账号时，你的姓名、邮箱、支持请求（连同其中的截图）和通知会立即删除，学习记录只会以不含姓名的形式保留。',
      rightsTitle: '你的权利',
      rightsCopy:
        '你可以在“账号”页面查看并更正个人信息，通过确认新地址来更改邮箱，并随时在那里删除账号。管理员账号由其他管理员停用。',
      lawTitle: '适用法律',
      lawCopy: '个人数据的处理参照马来西亚《2010 年个人数据保护法》（PDPA）。',
      thirdPartiesTitle: '第三方服务',
      thirdPartiesCopy:
        '网页应用从 Google Fonts 加载字体。平台通过配置的邮件服务发送账号邮件、支持团队的回复，以及管理员可选择接收的每日汇总。',
      contactTitle: '联系方式',
      contactCopy: '对隐私有疑问，或想申请删除数据？请使用帮助页面上的“联系我们”表单。请勿发送密码。',
    },
  },
} as const
