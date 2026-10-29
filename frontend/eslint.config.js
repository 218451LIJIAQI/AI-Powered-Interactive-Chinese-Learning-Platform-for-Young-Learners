import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default tseslint.config(
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
      'no-empty': ['error', { allowEmptyCatch: true }],
      // TypeScript and vue-tsc already report undefined names, so no-undef (and a globals list) is not needed.
      'no-undef': 'off',
      'no-control-regex': 'off',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'vue/multi-word-component-names': 'off',
      'vue/attributes-order': 'off',
      'vue/no-v-html': 'error',
    },
  },
  prettier,
)
