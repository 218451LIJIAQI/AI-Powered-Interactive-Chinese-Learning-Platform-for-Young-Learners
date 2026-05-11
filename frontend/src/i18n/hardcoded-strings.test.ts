import { describe, expect, it } from 'vitest'

/**
 * Regression test: catches hardcoded user-facing English strings that should
 * be wrapped in `t('...')` so they translate when the user switches the app
 * language to Simplified Chinese or Bahasa Melayu.
 *
 * The test inspects every `.vue` file under `src/` and asserts that certain
 * common UI props are *bound* (prefixed with `:` so they evaluate an
 * expression like `t('...')`) instead of being passed a literal string.
 *
 * It also catches a separate class of subtle bug: route param parsing using
 * `Number(route.params.id)` directly. The codebase has a typed
 * `useNumericRouteParam('id')` composable that guards against `NaN`-induced
 * 400 responses; new code should use it instead.
 */

// `import.meta.glob` with `as: 'raw'` gives us the file contents as strings.
const vueFiles = import.meta.glob<string>('/src/**/*.vue', {
  query: '?raw',
  import: 'default',
  eager: true,
})

const tsFiles = import.meta.glob<string>('/src/**/*.ts', {
  query: '?raw',
  import: 'default',
  eager: true,
})

interface HardcodeRule {
  /** Human-readable rule label included in failure messages. */
  label: string
  /** Pattern that must NOT match anywhere in any template. */
  pattern: RegExp
}

const HARDCODE_RULES: HardcodeRule[] = [
  {
    label: 'Unbound mascot-message (use :mascot-message="t(...)" instead)',
    pattern: /\smascot-message="(?!\s*")[^"]+"/,
  },
  {
    label: 'el-alert with hardcoded English title (use :title="t(...)" instead)',
    // matches `title="Word word..."`-style English titles ≥ 12 chars on an
    // <el-alert>. Bound titles use `:title=` and are skipped automatically.
    pattern: /<el-alert\b[^>]*\stitle="[A-Z][A-Za-z][A-Za-z ,.'!?-]{10,}"/,
  },
  {
    label: 'el-empty with hardcoded English description (use :description="t(...)")',
    pattern: /<el-empty\b[^>]*\sdescription="[A-Z][A-Za-z][A-Za-z ,.'!?-]{10,}"/,
  },
]

/**
 * Files that are explicitly allowed to use `Number(route.params.*)` directly,
 * usually because they intentionally accept the `NaN` fallback inside an
 * `Array.find(...)` whose result is null-checked downstream.
 */
const ROUTE_PARAM_ALLOWLIST = new Set<string>([
  // Looks up an existing live class by id; `find` returns undefined for NaN
  // and the caller already null-checks the result.
  '/src/pages/teacher/TeacherLiveClassManagementPage.vue',
])

const ROUTE_PARAM_PATTERN = /Number\(\s*route\.params\./

describe('i18n hardcoded strings', () => {
  it('loads at least one Vue file via import.meta.glob', () => {
    // sanity check: if the glob returns nothing, the rules silently pass
    expect(Object.keys(vueFiles).length).toBeGreaterThan(20)
  })

  for (const rule of HARDCODE_RULES) {
    it(rule.label, () => {
      const offenders: string[] = []
      for (const [path, content] of Object.entries(vueFiles)) {
        const match = rule.pattern.exec(content)
        if (match) {
          offenders.push(`${path} — matched: ${match[0].slice(0, 120)}`)
        }
      }
      expect(offenders, `Found ${offenders.length} hardcoded strings:\n${offenders.join('\n')}`).toEqual([])
    })
  }

  it('does not parse route params with raw Number() (use useNumericRouteParam instead)', () => {
    const offenders: string[] = []
    const allFiles: Record<string, string> = { ...vueFiles, ...tsFiles }
    for (const [path, content] of Object.entries(allFiles)) {
      if (ROUTE_PARAM_ALLOWLIST.has(path)) {
        continue
      }
      // Skip the composable file itself — it is the canonical implementation.
      if (path.endsWith('/composables/useRouteParam.ts')) {
        continue
      }
      if (ROUTE_PARAM_PATTERN.test(content)) {
        offenders.push(path)
      }
    }
    expect(
      offenders,
      `Found ${offenders.length} files using Number(route.params.*) directly:\n${offenders.join('\n')}`,
    ).toEqual([])
  })

  it('has no malformed attribute syntax like `="value"identifier="value2"`', () => {
    /*
     * Catches typos like:
     *
     *   <el-select v-model="form.studentId"lessonCessonCclass="w-full">
     *
     * where two attributes were accidentally concatenated. Vue's lenient
     * template parser silently treats `lessonCessonCclass` as a no-op custom
     * attribute, so these slip through without runtime errors.
     */
    const malformedAttrPattern = /="[^"]*"[A-Za-z][A-Za-z0-9_-]*=/
    const offenders: string[] = []
    for (const [path, content] of Object.entries(vueFiles)) {
      // Only inspect template blocks; script setup TS may legitimately have
      // patterns like type narrowing that look similar. We split on the
      // `<template>` tag and only scan its body.
      const templateMatch = content.match(/<template>([\s\S]*?)<\/template>/)
      if (!templateMatch) continue
      const templateBody = templateMatch[1]
      const match = malformedAttrPattern.exec(templateBody)
      if (match) {
        offenders.push(`${path} — matched: ${match[0].slice(0, 120)}`)
      }
    }
    expect(
      offenders,
      `Found ${offenders.length} malformed attribute concatenations:\n${offenders.join('\n')}`,
    ).toEqual([])
  })
})
