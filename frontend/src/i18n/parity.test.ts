import { describe, expect, it } from 'vitest'

import { i18n, type SupportedLocale } from './index'

/**
 * i18n parity audit.
 *
 * The existing {@code i18n.test.ts} explicitly enumerates a sample of "demo
 * facing" keys, which catches regressions on the most user-visible surface
 * but has no defence against a key being added to {@code en.ts} without an
 * accompanying entry in {@code ms.ts} or {@code zh.ts}. As the catalog
 * grew past 60 KB per locale this gap became real.
 *
 * This test walks the entire English message tree (which is treated as the
 * source of truth) and asserts that every leaf-string path is also present
 * and non-empty in Bahasa Melayu and Simplified Chinese. To keep the
 * assertion useful and actionable we:
 *
 * <ul>
 *   <li>Group missing keys per locale, list the first ~25 in the failure
 *       message, and report the total count so engineers can fix in batches.</li>
 *   <li>Allow array values (vue-i18n plural / list forms) — they are checked
 *       for presence only, not item-by-item equality.</li>
 *   <li>Tolerate a small allow-list of keys that are intentionally
 *       English-only (e.g. brand names, AI provider labels) so adding new
 *       English-only strings is a deliberate decision rather than an
 *       accident.</li>
 * </ul>
 */

type MessageArray = readonly MessageNode[]
type MessageNode = string | MessageArray | { [key: string]: MessageNode }
type MessageTree = { [key: string]: MessageNode }

const LOCALES_TO_CHECK: SupportedLocale[] = ['ms', 'zh']

/**
 * Keys that are deliberately allowed to appear only in English. Use sparingly
 * — every entry should have a comment explaining why translation is not
 * appropriate for that key. Empty by default; this is the documented escape
 * hatch for any future intentional English-only string.
 */
const ENGLISH_ONLY_ALLOWLIST = new Set<string>([])

function flattenLeafKeys(tree: MessageTree, prefix = ''): string[] {
  const keys: string[] = []
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string' || Array.isArray(value)) {
      keys.push(path)
    } else if (value && typeof value === 'object') {
      keys.push(...flattenLeafKeys(value as MessageTree, path))
    }
  }
  return keys
}

function resolvePath(tree: MessageTree, path: string): MessageNode | undefined {
  return path
    .split('.')
    .reduce<MessageNode | undefined>((node, segment) => {
      if (node && typeof node === 'object' && !Array.isArray(node)) {
        const record = node as { [key: string]: MessageNode }
        if (segment in record) {
          return record[segment]
        }
      }
      return undefined
    }, tree as MessageNode)
}

function isPresentAndNonEmpty(node: MessageNode | undefined): boolean {
  if (typeof node === 'string') {
    return node.trim().length > 0
  }
  if (Array.isArray(node)) {
    // Arrays in the i18n tree are used for two distinct purposes:
    //   1. Plural forms / list-of-strings → must all be non-empty strings.
    //   2. Structured content collections such as FAQ items or legal
    //      sections → arrays of {question, answer} or {title, points}
    //      records. For these we only enforce "non-empty array at the
    //      same path" as the parity contract; validating the internal
    //      shape would require a schema per key and is out of scope for
    //      this safety-net test.
    if (node.length === 0) return false
    const firstItem = node[0]
    if (typeof firstItem === 'string') {
      return node.every((item) => typeof item === 'string' && item.trim().length > 0)
    }
    return true
  }
  return false
}

describe('i18n parity between en and other locales', () => {
  const enMessages = i18n.global.getLocaleMessage('en') as MessageTree
  const englishLeafKeys = flattenLeafKeys(enMessages)

  it('English message tree contains a meaningful number of keys', () => {
    // Sentinel: if `en.ts` is loaded incorrectly the keys array will be empty
    // and every parity check would silently pass. Guard against that.
    expect(englishLeafKeys.length).toBeGreaterThan(500)
  })

  for (const locale of LOCALES_TO_CHECK) {
    it(`locale "${locale}" has a translation for every English leaf key`, () => {
      const messages = i18n.global.getLocaleMessage(locale) as MessageTree
      const missing: string[] = []

      for (const key of englishLeafKeys) {
        if (ENGLISH_ONLY_ALLOWLIST.has(key)) {
          continue
        }
        const node = resolvePath(messages, key)
        if (!isPresentAndNonEmpty(node)) {
          missing.push(key)
        }
      }

      // Compose an actionable error: show the first batch of missing keys
      // and the total count so engineers can fix incrementally without
      // having to scroll through hundreds of lines of failure output.
      const PREVIEW_LIMIT = 25
      const preview = missing.slice(0, PREVIEW_LIMIT).join('\n  ')
      const more = missing.length > PREVIEW_LIMIT ? `\n  ... and ${missing.length - PREVIEW_LIMIT} more` : ''
      const errorMessage = missing.length === 0
        ? ''
        : `Missing ${missing.length} key(s) in locale "${locale}":\n  ${preview}${more}\n\n`
          + `Add the missing translations to src/i18n/messages/${locale}.ts. `
          + `If a key is intentionally English-only, add it to ENGLISH_ONLY_ALLOWLIST in this test.`

      expect(missing, errorMessage).toEqual([])
    })
  }
})
