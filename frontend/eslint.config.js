import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

// TypeScript and vue-tsc already report undefined names, so no-undef (and a globals list) is not needed.
const baseSourceRules = {
  'no-empty': ['error', { allowEmptyCatch: true }],
  'no-undef': 'off',
}

export default tseslint.config(
  {
    ignores: ['dist/**', 'coverage/**', 'node_modules/**', 'playwright-report/**', 'test-results/**'],
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
    rules: baseSourceRules,
  },
  prettier,
)
