import { describe, expect, it } from 'vitest'

import { isValidEmail } from './email'

describe('isValidEmail', () => {
  it.each(['mei@example.com', 'mei.ling+tag@mail.example.edu.my', 'a@b.co'])('accepts %s', (email) => {
    expect(isValidEmail(email)).toBe(true)
  })

  // Stricter than the server, which takes an address without a dot in its domain: no sign-up uses one.
  it('refuses an address whose domain has no dot', () => {
    expect(isValidEmail('mei@example')).toBe(false)
  })

  it.each([
    '',
    'mei',
    'mei@@example.com',
    'mei ling@example.com',
    'mei@example..com',
    'mei@.example.com',
    'mei@example.com.',
    'mei.@example.com',
    '.mei@example.com',
    'mei..ling@example.com',
  ])('refuses %j, as the server would', (email) => {
    expect(isValidEmail(email)).toBe(false)
  })
})
