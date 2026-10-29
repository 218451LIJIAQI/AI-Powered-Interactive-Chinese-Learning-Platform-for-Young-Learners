import type { AppLanguage } from '@/types/api'

interface DailyWord {
  readonly hanzi: string
  readonly pinyin: string
  readonly meaning: Readonly<Record<AppLanguage, string>>
}

/** Beginner (HSK 1) words for the sidebar's "Today's word" card; one per day, the same for everyone. */
const DAILY_WORDS: readonly DailyWord[] = [
  { hanzi: '学', pinyin: 'xué', meaning: { en: 'learn', ms: 'belajar', 'zh-CN': '学习' } },
  { hanzi: '你好', pinyin: 'nǐ hǎo', meaning: { en: 'hello', ms: 'helo', 'zh-CN': '问好' } },
  { hanzi: '谢谢', pinyin: 'xièxie', meaning: { en: 'thank you', ms: 'terima kasih', 'zh-CN': '感谢' } },
  { hanzi: '朋友', pinyin: 'péngyou', meaning: { en: 'friend', ms: 'kawan', 'zh-CN': '友人' } },
  { hanzi: '吃', pinyin: 'chī', meaning: { en: 'eat', ms: 'makan', 'zh-CN': '进食' } },
  { hanzi: '喝', pinyin: 'hē', meaning: { en: 'drink', ms: 'minum', 'zh-CN': '饮用' } },
  { hanzi: '茶', pinyin: 'chá', meaning: { en: 'tea', ms: 'teh', 'zh-CN': '茶水' } },
  { hanzi: '书', pinyin: 'shū', meaning: { en: 'book', ms: 'buku', 'zh-CN': '书本' } },
  { hanzi: '家', pinyin: 'jiā', meaning: { en: 'home', ms: 'rumah', 'zh-CN': '家庭' } },
  { hanzi: '水', pinyin: 'shuǐ', meaning: { en: 'water', ms: 'air', 'zh-CN': '水' } },
  { hanzi: '大', pinyin: 'dà', meaning: { en: 'big', ms: 'besar', 'zh-CN': '大的' } },
  { hanzi: '小', pinyin: 'xiǎo', meaning: { en: 'small', ms: 'kecil', 'zh-CN': '小的' } },
  { hanzi: '好', pinyin: 'hǎo', meaning: { en: 'good', ms: 'baik', 'zh-CN': '好的' } },
  { hanzi: '去', pinyin: 'qù', meaning: { en: 'go', ms: 'pergi', 'zh-CN': '前往' } },
  { hanzi: '来', pinyin: 'lái', meaning: { en: 'come', ms: 'datang', 'zh-CN': '到来' } },
  { hanzi: '看', pinyin: 'kàn', meaning: { en: 'look, read', ms: 'lihat, baca', 'zh-CN': '观看' } },
  { hanzi: '听', pinyin: 'tīng', meaning: { en: 'listen', ms: 'dengar', 'zh-CN': '聆听' } },
  { hanzi: '说', pinyin: 'shuō', meaning: { en: 'speak', ms: 'bercakap', 'zh-CN': '说话' } },
  { hanzi: '写', pinyin: 'xiě', meaning: { en: 'write', ms: 'tulis', 'zh-CN': '书写' } },
  { hanzi: '中文', pinyin: 'zhōngwén', meaning: { en: 'Chinese', ms: 'bahasa Cina', 'zh-CN': '汉语' } },
  { hanzi: '老师', pinyin: 'lǎoshī', meaning: { en: 'teacher', ms: 'guru', 'zh-CN': '教师' } },
  { hanzi: '学生', pinyin: 'xuésheng', meaning: { en: 'student', ms: 'pelajar', 'zh-CN': '学员' } },
  { hanzi: '今天', pinyin: 'jīntiān', meaning: { en: 'today', ms: 'hari ini', 'zh-CN': '今日' } },
  { hanzi: '明天', pinyin: 'míngtiān', meaning: { en: 'tomorrow', ms: 'esok', 'zh-CN': '明日' } },
  { hanzi: '米饭', pinyin: 'mǐfàn', meaning: { en: 'cooked rice', ms: 'nasi', 'zh-CN': '饭' } },
  { hanzi: '钱', pinyin: 'qián', meaning: { en: 'money', ms: 'wang', 'zh-CN': '货币' } },
  { hanzi: '火', pinyin: 'huǒ', meaning: { en: 'fire, spark', ms: 'api', 'zh-CN': '火焰' } },
  { hanzi: '心', pinyin: 'xīn', meaning: { en: 'heart', ms: 'hati', 'zh-CN': '内心' } },
  { hanzi: '月', pinyin: 'yuè', meaning: { en: 'moon, month', ms: 'bulan', 'zh-CN': '月亮' } },
  { hanzi: '日', pinyin: 'rì', meaning: { en: 'sun, day', ms: 'matahari, hari', 'zh-CN': '太阳' } },
]

const MS_PER_DAY = 86_400_000

/** The word of the given day (local calendar date), cycling through the list. */
export function dailyWord(date: Date = new Date()): DailyWord {
  const dayNumber = Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / MS_PER_DAY)
  return DAILY_WORDS[((dayNumber % DAILY_WORDS.length) + DAILY_WORDS.length) % DAILY_WORDS.length]
}
