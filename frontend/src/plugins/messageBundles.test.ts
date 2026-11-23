import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'

import { APP_LANGUAGES, type AppLanguage } from '@/types/api'

import { classPlayerMessages } from './classPlayerMessages'
import { i18n } from './i18n'

const LOCALES = APP_LANGUAGES
type Locale = AppLanguage
type LocaleBundle = Partial<Record<Locale, unknown>>

/** Every module of this folder whose name ends in `Messages.ts`, so a new bundle is checked without extra code. */
const modules = import.meta.glob<Record<string, unknown>>('./*Messages.ts', { eager: true })

/** Recognise a bundle even when one of its locales is accidentally removed. */
function isLocaleBundle(value: unknown): value is LocaleBundle {
  return typeof value === 'object' && value !== null && LOCALES.some((locale) => locale in value)
}

/** Nested sections (e.g. legal terms / privacy) become dotted keys: `terms.title`. Empty sections fail validation. */
function flatten(value: unknown, prefix = '', result: Record<string, unknown> = {}): Record<string, unknown> {
  if (typeof value !== 'object' || value === null) {
    result[prefix] = value
  } else {
    const entries = Object.entries(value)
    if (entries.length === 0) {
      result[prefix || '<empty>'] = ''
    } else {
      for (const [key, child] of entries) {
        flatten(child, prefix ? `${prefix}.${key}` : key, result)
      }
    }
  }
  return result
}

function placeholders(message: string): string[] {
  return [...new Set(Array.from(message.matchAll(/\{(\w+)\}/g), (match) => match[1] ?? ''))].sort()
}

/** The globbed bundles, plus the global messages of i18n.ts. */
const bundles = [
  ['i18n.ts global messages', i18n.global.messages.value as LocaleBundle] as const,
  ...Object.entries(modules).flatMap(([path, exports]) =>
    Object.entries(exports)
      .filter((entry): entry is [string, LocaleBundle] => isLocaleBundle(entry[1]))
      .map(([name, bundle]) => [`${path.replace('./', '')} ${name}`, bundle] as const),
  ),
]

describe('EN / MS / zh-CN message bundles (FRLANG01, FRLANG02)', () => {
  it('finds a message bundle in every message module', () => {
    expect(Object.keys(modules).length).toBeGreaterThan(0)
    for (const [path, exports] of Object.entries(modules)) {
      expect(Object.values(exports).some(isLocaleBundle), path).toBe(true)
    }
  })

  it.each(bundles)('%s has the same keys and placeholders in every locale, with no empty text', (_name, bundle) => {
    expect(Object.keys(bundle).sort(), 'locales').toEqual([...LOCALES].sort())
    const english = flatten(bundle.en)
    const englishKeys = Object.keys(english).sort()
    expect(englishKeys.length, 'English message keys').toBeGreaterThan(0)
    for (const locale of LOCALES) {
      const messages = flatten(bundle[locale])
      expect(Object.keys(messages).sort(), locale).toEqual(englishKeys)
      for (const [key, message] of Object.entries(messages)) {
        expect(typeof message === 'string' && message.trim() !== '', `${locale}.${key} is empty`).toBe(true)
        const text = String(message)
        const reference = String(english[key])
        expect(placeholders(text), `${locale}.${key}`).toEqual(placeholders(reference))
        // vue-i18n splits plural choices on any unescaped '|', so require canonical ' | ' separators.
        expect(text, `${locale}.${key} unspaced pipe`).not.toMatch(/(?<! )\||\|(?! )/)
        // Reject stray linked-message syntax (`@:key`, `@.lower:key`, `@{'...'}`) that vue-i18n compiles silently.
        expect(text, `${locale}.${key} linked-message syntax`).not.toMatch(/@[:.{]/)
        // Malay and Chinese may use one form where English has singular | plural, but never different forms.
        const branches = text.split('|')
        const englishBranches = reference.split('|')
        const forms = branches.length
        expect(forms === englishBranches.length || forms === 1, `${locale}.${key} plural forms`).toBe(true)
        // Multi-form translations must preserve every placeholder in the matching English branch (catches dropped {count}).
        if (forms === englishBranches.length && forms > 1) {
          for (let index = 0; index < forms; index++) {
            const branchPlaceholders = placeholders(branches[index] ?? '')
            for (const required of placeholders(englishBranches[index] ?? '')) {
              expect(
                branchPlaceholders,
                `${locale}.${key} branch ${index} "${(branches[index] ?? '').trim()}"`,
              ).toContain(required)
            }
          }
        }
      }
    }
  })

  /**
   * Bundles that components mount together under one namespace (useLocalMessages deep-merges them, and a later bundle
   * silently wins): a message key, or a key that another bundle uses as a section, must live in only one of them.
   */
  it.each([
    ['aiHelper', ['aiHelperMessages', 'aiAskMessages', 'aiGeneratorMessages', 'aiTalkMessages', 'aiVoiceMessages']],
    ['plan', ['planPathMessages', 'planSetupMessages', 'planSummaryMessages']],
    ['answer', ['hintChromeMessages', 'practiceAnswerMessages']],
    ['learn', ['learnSharedMessages', 'learnBadgeMessages', 'learnMessages']],
    ['adminLearning', ['adminLearningSharedMessages', 'adminLearningPathMessages', 'adminPracticeLibraryMessages']],
  ] as const)('the %s bundles merged under one namespace never define the same key twice', (_namespace, names) => {
    for (const locale of LOCALES) {
      const owners = new Map<string, string>()
      for (const name of names) {
        const bundle = modules[`./${name}.ts`]?.[name] as LocaleBundle | undefined
        expect(bundle, name).toBeDefined()
        for (const key of Object.keys(flatten(bundle?.[locale]))) {
          const clash = [...owners].find(
            ([other]) => other === key || other.startsWith(`${key}.`) || key.startsWith(`${other}.`),
          )
          expect(clash, `${locale}.${key} of ${name}`).toBeUndefined()
          owners.set(key, name)
        }
      }
    }
  })

  it.each(bundles)('%s compiles in every locale (no stray linked-message or placeholder syntax)', (_name, bundle) => {
    const messages = Object.fromEntries(
      LOCALES.map((locale) => [locale, { bundle: bundle[locale] as Record<string, unknown> }]),
    )
    const instance = createI18n({ legacy: false, locale: 'en', messages, missingWarn: false, fallbackWarn: false })
    for (const locale of LOCALES) {
      instance.global.locale.value = locale
      for (const key of Object.keys(flatten(bundle[locale]))) {
        expect(() => instance.global.t(`bundle.${key}`, { count: 2 }), `${locale}.${key}`).not.toThrow()
      }
    }
  })

  it('classPlayerMessages has a zero form of result.toReview in every locale for a perfect score', () => {
    const instance = createI18n({ legacy: false, locale: 'en', messages: classPlayerMessages }).global

    expect(instance.t('result.toReview', 0)).toBe('No answers to review')
    expect(instance.t('result.toReview', 0, { locale: 'ms' })).toBe('Tiada jawapan untuk disemak')
    expect(instance.t('result.toReview', 0, { locale: 'zh-CN' })).toBe('没有需要回顾的题目')
    expect(instance.t('result.toReview', 3, { locale: 'zh-CN' })).toBe('有 3 题需要回顾')
  })
})
