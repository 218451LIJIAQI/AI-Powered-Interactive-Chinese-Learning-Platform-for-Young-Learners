import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

const readonlyGlobals = (names) => Object.fromEntries(names.map((name) => [name, 'readonly']))

const allowEmptyCatch = ['error', { allowEmptyCatch: true }]
const baseSourceRules = {
  '@typescript-eslint/no-unused-vars': 'error',
  'no-empty': allowEmptyCatch,
  'no-undef': 'off',
}

const browserGlobals = readonlyGlobals([
  'Blob',
  'clearTimeout',
  'console',
  'CustomEvent',
  'document',
  'Event',
  'File',
  'FormData',
  'HTMLInputElement',
  'navigator',
  'setTimeout',
  'SpeechSynthesisUtterance',
  'Storage',
  'URL',
  'URLSearchParams',
  'window',
])

const configGlobals = readonlyGlobals([
  '__dirname',
  'Blob',
  'Buffer',
  'clearTimeout',
  'console',
  'fetch',
  'FormData',
  'process',
  'setTimeout',
  'URL',
  'WebSocket',
])

export default tseslint.config(
  {
    ignores: ['dist/**', 'coverage/**', 'node_modules/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue', '**/*.ts'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
      globals: browserGlobals,
    },
    rules: {
      ...baseSourceRules,
      'no-control-regex': 'off',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      '@typescript-eslint/no-explicit-any': 'error',
      'vue/multi-word-component-names': 'off',
      'vue/attributes-order': 'off',
      'vue/no-v-html': 'error',
    },
  },
  {
    files: ['*.config.*'],
    languageOptions: {
      globals: configGlobals,
    },
    rules: {
      ...baseSourceRules,
      'no-useless-assignment': 'error',
      '@typescript-eslint/no-unused-expressions': 'error',
    },
  },
  prettier,
)
