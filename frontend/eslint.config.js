import { defineConfig } from 'eslint/config'
import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default defineConfig(
  {
    ignores: ['dist/**', 'test-results/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue', '**/*.ts'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
    rules: {
      // TypeScript and vue-tsc already report undefined names, so no-undef (and a globals list) is not needed.
      'no-undef': 'off',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'vue/no-v-html': 'error',
    },
  },
  prettier,
)
