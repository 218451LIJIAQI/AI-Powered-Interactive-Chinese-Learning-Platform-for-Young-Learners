<template>
  <div class="auth-password">
    <input :id="id" v-model="model" class="input" :type="visible ? 'text' : 'password'" v-bind="$attrs" />
    <!-- One name, and the pressed state says whether the password shows (a name that flips would say it twice). -->
    <button
      class="btn btn-ghost btn-icon btn-sm auth-password-toggle"
      type="button"
      :aria-label="t('common.showPassword')"
      :aria-pressed="visible"
      :aria-controls="id"
      @click="visible = !visible"
    >
      <IconGlyph :name="visible ? 'eyeOff' : 'eye'" :size="16" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * A password field with its show / hide button (sign-in, create account, reset and admin invite). Every other attribute
 * (`autocomplete`, `placeholder`, `aria-*`, `required`, listeners such as `blur`) goes to the input.
 */
defineOptions({ inheritAttrs: false })
defineProps<{ id: string }>()

const model = defineModel<string>({ required: true })
const visible = ref(false)

const { t } = useI18n()
</script>
