import { describe, expect, it } from 'vitest'

import { xpLevelMessages, type XpLevelKey } from '@/plugins/xpLevelMessages'

import {
  createNotificationMessageLocalizer,
  localizeNotificationMessage,
  placeholderNames,
} from '../notificationMessages'
import { messageKey } from '../serverMessages'
import { ACTIVE_NOTIFICATION_MESSAGES, NOTIFICATION_MESSAGES, RETIRED_NOTIFICATION_MESSAGES } from './notifications'

/**
 * Sample notification texts as the backend stores them (and stored them in survivable retired rows): fixed rows,
 * spacing/case/period normalisation, values kept verbatim, reordered placeholders, and unknown text.
 */
const TRANSLATION_CASES = [
  { en: 'New support request', ms: 'Permintaan sokongan baharu', 'zh-CN': '新的支持请求' },
  { en: '  new   SUPPORT request. ', ms: 'Permintaan sokongan baharu', 'zh-CN': '新的支持请求' },
  {
    en: 'A visitor (mei@example.com) sent #1042 “Login fails”. Reply within 2 working days.',
    ms: 'Seorang pelawat (mei@example.com) menghantar #1042 “Login fails”. Balas dalam 2 hari bekerja.',
    'zh-CN': '一位访客（mei@example.com）发送了 #1042“Login fails”。请在 2 个工作日内回复。',
  },
  {
    en: 'Aina sent #1043 “Audio stops”. Reply within 2 working days.',
    ms: 'Aina menghantar #1043 “Audio stops”. Balas dalam 2 hari bekerja.',
    'zh-CN': 'Aina 发送了 #1043“Audio stops”。请在 2 个工作日内回复。',
  },
  {
    en: 'Aina reported an AI answer (score seems wrong).',
    ms: 'Aina melaporkan jawapan AI (markah kelihatan salah).',
    'zh-CN': 'Aina 举报了一条 AI 回答（分数似乎不对）。',
  },
  {
    en: 'Mei Ling 美玲 (Year 1) replied on #1044 “Campus Life $1 {title}”.',
    ms: 'Mei Ling 美玲 (Year 1) membalas pada #1044 “Campus Life $1 {title}”.',
    'zh-CN': 'Mei Ling 美玲 (Year 1) 回复了 #1044“Campus Life $1 {title}”。',
  },
  { en: 'You reached Rank 3', ms: 'Anda mencapai Pangkat 3', 'zh-CN': '你升到了第 3 段位' },
  {
    en: "You're now Rank 3 · Regular. Rank 4 is next.",
    ms: 'Anda kini Pangkat 3 · Tetap. Seterusnya Pangkat 4.',
    'zh-CN': '你现在是第 3 段位 · 常客。下一个是第 4 段位。',
  },
  {
    en: 'A new listening set is ready: “At the Café”.',
    ms: 'Set mendengar baharu sudah sedia: “At the Café”.',
    'zh-CN': '新的听力练习组已上线：“At the Café”。',
  },
  {
    en: '“Greetings” in Mandarin Foundations was updated with new content.',
    ms: '“Greetings” dalam Mandarin Foundations telah dikemas kini dengan kandungan baharu.',
    'zh-CN': '“Mandarin Foundations”中的“Greetings”已更新，加入了新内容。',
  },
  {
    en: 'You have 6 mistakes saved. Fixing them takes about 3 minutes.',
    ms: 'Anda ada 6 kesilapan yang disimpan. Membetulkannya mengambil masa kira-kira 3 minit.',
    'zh-CN': '你有 6 道错题待复习，改正它们大约需要 3 分钟。',
  },
  { en: 'Something new happened.', ms: 'Something new happened.', 'zh-CN': 'Something new happened.' },
] as const

/**
 * Notification catalogue tests: pins row completeness, template matching, placeholder symmetry, and rank-title
 * alignment with `xpLevelMessages`.
 */
describe('the notification catalogue', () => {
  it('lists active and retired [en, ms, zh-CN] rows with no empty or duplicate entries', () => {
    expect(NOTIFICATION_MESSAGES.length).toBeGreaterThanOrEqual(40)
    expect(NOTIFICATION_MESSAGES).toEqual([...ACTIVE_NOTIFICATION_MESSAGES, ...RETIRED_NOTIFICATION_MESSAGES])
    for (const [en, ms, zh] of NOTIFICATION_MESSAGES) {
      expect(en.trim()).not.toBe('')
      expect(ms.trim(), en).not.toBe('')
      expect(zh.trim(), en).not.toBe('')
      expect(ms, en).not.toBe(en)
      expect(zh, en).not.toBe(en)
    }
    const keys = NOTIFICATION_MESSAGES.map(([en]) => messageKey(en))
    expect(keys.filter((key, index) => keys.indexOf(key) !== index)).toEqual([])
  })

  it('names the same placeholders in every language of every template row', () => {
    const mismatched = NOTIFICATION_MESSAGES.filter(([en, ms, zh]) => {
      const names = placeholderNames(en)
      const same = (text: string) => placeholderNames(text).sort().join() === [...names].sort().join()
      return new Set(names).size !== names.length || !same(ms) || !same(zh)
    })
    expect(mismatched).toEqual([])
    expect(NOTIFICATION_MESSAGES.filter(([en]) => placeholderNames(en).length).length).toBeGreaterThan(20)
  })

  it('translates representative notification cases with localizeNotificationMessage', () => {
    expect(TRANSLATION_CASES.length).toBeGreaterThan(10)
    for (const example of TRANSLATION_CASES) {
      expect(localizeNotificationMessage(example.en, 'en')).toBe(example.en)
      for (const locale of ['ms', 'zh-CN'] as const) {
        expect(localizeNotificationMessage(example.en, locale), `${locale}: ${example.en}`).toBe(example[locale])
      }
    }
  })

  it('keeps all 5 XP rank titles aligned with xpLevelMessages across every language', () => {
    const rankKeys: readonly XpLevelKey[] = ['starter', 'explorer', 'regular', 'committed', 'champion']
    for (const key of rankKeys) {
      const enTitle = xpLevelMessages.en[key]
      const expectedEn = `You're now Rank {rank} · ${enTitle}. Rank {next} is next.`
      const row = ACTIVE_NOTIFICATION_MESSAGES.find(([en]) => en === expectedEn)
      expect(row, `missing rank row for ${key}`).toBeDefined()
      expect(row![1]).toContain(`· ${xpLevelMessages.ms[key]}.`)
      expect(row![2]).toContain(`· ${xpLevelMessages['zh-CN'][key]}。`)
    }
  })

  it('keeps custom template matcher precedence and value fidelity', () => {
    const rows = [
      [
        '{name} moved {lesson} to {class}.',
        '{lesson} dipindahkan oleh {name} ke {class}.',
        '{name} 把“{lesson}”移到了“{class}”。',
      ],
      ['{name} joined {class}.', '{name} menyertai {class}.', '{name} 加入了 {class}。'],
      [
        '{name} joined {class} as a helper.',
        '{name} menyertai {class} sebagai pembantu.',
        '{name} 以助手身份加入了 {class}。',
      ],
      ['{title} approved', '{title} diluluskan', '{title} 已通过'],
      ['Class approved', 'Kelas diluluskan', '课堂已通过审核'],
    ] as const
    const localize = createNotificationMessageLocalizer(rows)
    const reversed = createNotificationMessageLocalizer([...rows].reverse())

    expect(localize('Ali moved Greetings to Campus Life.', 'ms')).toBe('Greetings dipindahkan oleh Ali ke Campus Life.')
    expect(localize('Ali moved Greetings to Campus Life.', 'zh-CN')).toBe('Ali 把“Greetings”移到了“Campus Life”。')
    expect(localize('Ali moved Greetings to Campus Life.', 'en')).toBe('Ali moved Greetings to Campus Life.')
    for (const lookup of [localize, reversed]) {
      expect(lookup('Ali joined Hawker Food as a helper.', 'ms')).toBe('Ali menyertai Hawker Food sebagai pembantu.')
      expect(lookup('Ali joined Hawker Food.', 'ms')).toBe('Ali menyertai Hawker Food.')
    }
    expect(localize('Class approved', 'ms')).toBe('Kelas diluluskan')
    expect(localize('Lesson 3 approved', 'ms')).toBe('Lesson 3 diluluskan')
    expect(localize('Ali joined Bo joined Campus.', 'ms')).toBe('Ali menyertai Bo joined Campus.')
    expect(localize('Ali left Campus.', 'ms')).toBe('Ali left Campus.')

    const typed = 'Mei  Ling 美玲 "$1" $& $$ (A.B) [x]* \\d+? {title}'
    expect(localizeNotificationMessage(`A new flashcards set is ready: “${typed}”.`, 'ms')).toBe(
      `Set kad imbas baharu sudah sedia: “${typed}”.`,
    )
    expect(localizeNotificationMessage(`A new flashcards set is ready: “${typed}”.`, 'zh-CN')).toBe(
      `新的闪卡练习组已上线：“${typed}”。`,
    )

    const multiline = 'Add audio.\nCheck the tones of 你好.'
    expect(localizeNotificationMessage(`Aina sent #7 “${multiline}”. Reply within 2 working days.`, 'zh-CN')).toBe(
      `Aina 发送了 #7“${multiline}”。请在 2 个工作日内回复。`,
    )
    expect(localizeNotificationMessage('  YOU   reached   RANK  4  ', 'ms')).toBe('Anda mencapai Pangkat 4')
    expect(localizeNotificationMessage('You reached Rank 4.', 'ms')).toBe('Anda mencapai Pangkat 4.')
    expect(localizeNotificationMessage('  AINA   reported an AI answer (other) ', 'ms')).toBe(
      'AINA melaporkan jawapan AI (lain-lain).',
    )
    expect(localizeNotificationMessage('You reached Rank  ', 'ms')).toBe('You reached Rank  ')
    expect(localizeNotificationMessage('A new flashcards set is ready: “ ”.', 'zh-CN')).toBe(
      'A new flashcards set is ready: “ ”.',
    )
  })
})
