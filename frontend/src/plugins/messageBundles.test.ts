import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'

const LOCALES = ['en', 'ms', 'zh-CN'] as const

type Locale = (typeof LOCALES)[number]
type LocaleBundle = Partial<Record<Locale, unknown>> & { en: unknown }

/** Every module of this folder whose name ends in `Messages.ts`, so a new bundle is checked without extra code. */
const modules = import.meta.glob<Record<string, unknown>>('./*Messages.ts', { eager: true })

/** Any exported object with English messages is a bundle; a missing Malay or Chinese part then fails the test. */
function isLocaleBundle(value: unknown): value is LocaleBundle {
  return typeof value === 'object' && value !== null && 'en' in value
}

/** Nested sections (e.g. legal terms / privacy) become dotted keys: `terms.title`. */
function flatten(value: unknown, prefix = ''): Record<string, unknown> {
  if (typeof value !== 'object' || value === null) return { [prefix]: value }
  return Object.assign(
    {},
    ...Object.entries(value).map(([key, child]) => flatten(child, prefix ? `${prefix}.${key}` : key)),
  )
}

function placeholders(message: string): string[] {
  return [...new Set(Array.from(message.matchAll(/\{(\w+)\}/g), (match) => match[1] ?? ''))].sort()
}

function pluralForms(message: string): number {
  return message.split(' | ').length
}

const bundles = Object.entries(modules).flatMap(([path, exports]) =>
  Object.entries(exports)
    .filter((entry): entry is [string, LocaleBundle] => isLocaleBundle(entry[1]))
    .map(([name, bundle]) => [`${path.replace('./', '')} ${name}`, bundle] as const),
)

describe('EN / MS / zh-CN message bundles (FRLANG01, FRLANG02)', () => {
  it('finds the message bundles', () => {
    expect(bundles.length).toBeGreaterThan(40)
  })

  it.each(bundles)('%s has the same keys and placeholders in every locale, with no empty text', (_name, bundle) => {
    expect(
      LOCALES.filter((locale) => !(locale in bundle)),
      'missing locales',
    ).toEqual([])
    const english = flatten(bundle.en)
    for (const locale of LOCALES) {
      const messages = flatten(bundle[locale])
      expect(Object.keys(messages).sort(), locale).toEqual(Object.keys(english).sort())
      for (const [key, message] of Object.entries(messages)) {
        expect(typeof message === 'string' && message.trim() !== '', `${locale}.${key} is empty`).toBe(true)
        expect(placeholders(String(message)), `${locale}.${key}`).toEqual(placeholders(String(english[key])))
        // Malay and Chinese may use one form where English has singular | plural, but never different forms.
        const forms = pluralForms(String(message))
        expect(forms === pluralForms(String(english[key])) || forms === 1, `${locale}.${key} plural forms`).toBe(true)
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
})
