import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

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
      globals: {
        window: 'readonly',
        document: 'readonly',
        CustomEvent: 'readonly',
        FormData: 'readonly',
        Blob: 'readonly',
        Event: 'readonly',
        File: 'readonly',
        HTMLInputElement: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        Storage: 'readonly',
        SpeechSynthesisUtterance: 'readonly',
        navigator: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        console: 'readonly',
      },
    },
    rules: {
      'no-control-regex': 'off',
      'no-empty': 'off',
      'no-undef': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/attributes-order': 'off',
      'vue/no-v-html': 'off',
    },
  },
  {
    files: ['*.config.*', 'scripts/**/*.mjs'],
    languageOptions: {
      globals: {
        Blob: 'readonly',
        Buffer: 'readonly',
        FormData: 'readonly',
        URL: 'readonly',
        WebSocket: 'readonly',
        clearTimeout: 'readonly',
        console: 'readonly',
        fetch: 'readonly',
        process: 'readonly',
        setTimeout: 'readonly',
        __dirname: 'readonly',
      },
    },
    rules: {
      'no-empty': 'off',
      'no-undef': 'off',
      'no-useless-assignment': 'off',
      '@typescript-eslint/no-unused-expressions': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
  prettier,
)
