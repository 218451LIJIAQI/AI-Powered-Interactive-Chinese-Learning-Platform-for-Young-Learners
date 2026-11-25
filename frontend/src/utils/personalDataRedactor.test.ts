import { describe, expect, it } from 'vitest'

import { redactPersonalData } from './personalDataRedactor'

/** Parity coverage with backend PersonalDataRedactorTests.java (NFRSEC06). */
describe('redactPersonalData', () => {
  it('redacts email addresses including CJK-adjacent and digit-adjacent addresses', () => {
    expect(redactPersonalData('Email me at amy.tan+fyp@student.upm.edu.my please')).toBe('Email me at [EMAIL] please')
    expect(redactPersonalData('我的邮箱是abc_1@example.com。')).toBe('我的邮箱是[EMAIL]。')
    expect(redactPersonalData('alice@gmail.com0123456789')).toBe('[EMAIL][PHONE]')
    expect(redactPersonalData('alice@example.com_work')).toBe('[EMAIL]_work')
  })

  it('redacts Malaysian NRIC-like numbers including full-width IME digits', () => {
    expect(redactPersonalData('My IC is 990101-14-5678.')).toBe('My IC is [ID_NUMBER].')
    expect(redactPersonalData('IC 990101145678')).toBe('IC [ID_NUMBER]')
    expect(redactPersonalData('证件号９９０１０１－１４－５６７８已登记')).toBe('证件号[ID_NUMBER]已登记')
    expect(redactPersonalData('My IC is 990101 14 5678.')).toBe('My IC is [ID_NUMBER].')
    expect(redactPersonalData('My IC is 990101\u201314\u20135678.')).toBe('My IC is [ID_NUMBER].')
  })

  it('redacts phone numbers in common Malaysian and full-width formats', () => {
    expect(redactPersonalData('Call 012-345 6789')).toBe('Call [PHONE]')
    expect(redactPersonalData('Call 0123456789')).toBe('Call [PHONE]')
    expect(redactPersonalData('WhatsApp +60 12-345 6789 now')).toBe('WhatsApp [PHONE] now')
    expect(redactPersonalData('Call +60 3-8946 6000')).toBe('Call [PHONE]')
    expect(redactPersonalData('Call +60 (3) 8946 6000')).toBe('Call [PHONE]')
    expect(redactPersonalData('Call +601112345678')).toBe('Call [PHONE]')
    expect(redactPersonalData('Call +65 9123 4567')).toBe('Call [PHONE]')
    expect(redactPersonalData('Office (03) 8946 6000')).toBe('Office [PHONE]')
    expect(redactPersonalData('Office 012\u00A0345\u20116789')).toBe('Office [PHONE]')
    expect(redactPersonalData('我的电话是0123456789')).toBe('我的电话是[PHONE]')
    expect(redactPersonalData('请拨＋６０　（０３）８９４６－６０００转接')).toBe('请拨[PHONE]转接')
  })

  it('preserves ordinary learning numbers, dates, prices, and Chinese text', () => {
    const learningText =
      '我有3个苹果, it costs RM12.50, the class is at 10:30 on 2026-10-18, room 1203, count 1 2 3 4 5 6 7 8 9 10. 你好 (nǐ hǎo)! ２０２６年'
    expect(redactPersonalData(learningText)).toBe(learningText)
    expect(redactPersonalData('')).toBe('')
  })

  it('redacts several identifiers in one message', () => {
    expect(redactPersonalData('Name: Ali, email ali@example.com, phone 019-876 5432, IC 010203-10-1234')).toBe(
      'Name: Ali, email [EMAIL], phone [PHONE], IC [ID_NUMBER]',
    )
  })
})
