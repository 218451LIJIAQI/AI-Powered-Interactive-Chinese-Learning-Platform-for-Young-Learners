import { describe, expect, it } from 'vitest'

import { xpLevelMessages, type XpLevelKey } from '@/plugins/xpLevelMessages'

import {
  createNotificationMessageLocalizer,
  localizeNotificationMessage,
  placeholderNames,
} from '../notificationMessages'
import { messageKey } from '../serverMessages'
import { ACTIVE_NOTIFICATION_MESSAGES, NOTIFICATION_MESSAGES, RETIRED_NOTIFICATION_MESSAGES } from './notifications'
import examples from './notifications.cases.json'

/**
 * Notification catalogue tests: pins row completeness, template matching (including legacy live-session timestamps),
 * placeholder symmetry, and rank-title alignment with `xpLevelMessages`.
 */
describe('the notification catalogue', () => {
  it('lists active and retired [en, ms, zh-CN] rows with no empty or duplicate entries', () => {
    expect(NOTIFICATION_MESSAGES.length).toBeGreaterThan(50)
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
    expect(NOTIFICATION_MESSAGES.filter(([en]) => placeholderNames(en).length).length).toBeGreaterThan(30)
  })

  it('translates the shared cases with localizeNotificationMessage', () => {
    expect(examples.cases.length).toBeGreaterThan(10)
    for (const example of examples.cases) {
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

  it('keeps custom template matcher precedence, value fidelity, and session-start localisation', () => {
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
    expect(localizeNotificationMessage(`${typed} has been cancelled.`, 'ms')).toBe(`"${typed}" telah dibatalkan.`)
    expect(localizeNotificationMessage(`${typed} has been cancelled.`, 'zh-CN')).toBe(`“${typed}”已取消。`)

    const reason = 'Add audio.\nCheck the tones of 你好.'
    expect(localizeNotificationMessage(`An admin asked for changes to "Unit 1": ${reason}`, 'zh-CN')).toBe(
      `管理员要求修改“Unit 1”：${reason}`,
    )
    expect(localizeNotificationMessage('  GREETINGS   has been   CANCELLED  ', 'ms')).toBe(
      '"GREETINGS" telah dibatalkan.',
    )
    expect(localizeNotificationMessage('New question from Ali.', 'ms')).toBe('Soalan baharu daripada Ali.')
    expect(localizeNotificationMessage(' has been cancelled.', 'zh-CN')).toBe(' has been cancelled.')
    expect(localizeNotificationMessage('New question from  ', 'ms')).toBe('New question from  ')

    // Invalid live-session weekday or non-standard format stays verbatim instead of shifting dates.
    expect(
      localizeNotificationMessage('"A" (B) starts at 3:00 PM on Friday 14 November 2026, UTC time.', 'zh-CN'),
    ).toBe('“A”（B）将于 3:00 PM on Friday 14 November 2026（UTC 时间）开始。')
    expect(localizeNotificationMessage('"A" (B) starts at 15:00, UTC time.', 'ms')).toBe(
      '"A" (B) bermula 15:00, waktu UTC.',
    )
  })
})
