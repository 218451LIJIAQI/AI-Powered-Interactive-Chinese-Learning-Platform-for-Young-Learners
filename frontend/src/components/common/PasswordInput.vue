<template>
  <div class="auth-password">
    <input
      :id="id"
      v-model="model"
      class="input"
      :type="visible ? 'text' : 'password'"
      spellcheck="false"
      autocapitalize="off"
      autocorrect="off"
      v-bind="$attrs"
    />
    <!-- One name, and the pressed state says whether the password shows (a name that flips would say it twice). -->
    <button
      class="btn btn-ghost btn-icon btn-sm auth-password-toggle"
      type="button"
      :aria-label="t('common.showPassword')"
      :title="t('common.showPassword')"
      :aria-pressed="visible"
      :aria-controls="id"
      @mousedown.prevent
      @click="visible = !visible"
    >
      <IconGlyph :name="visible ? 'eyeOff' : 'eye'" :size="16" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { onDeactivated, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * A password field with its show / hide button (sign-in, create account, reset, admin invite and account settings).
 * Every other attribute (`autocomplete`, `placeholder`, `aria-*`, `required`, listeners such as `blur`) goes to the input.
 */
defineOptions({ inheritAttrs: false })
defineProps<{ id: string }>()

const model = defineModel<string>({ required: true })
const visible = ref(false)
onDeactivated(() => {
  visible.value = false
})

const { t } = useI18n()
</script>

<style scoped>
.input::-ms-reveal,
.input::-ms-clear {
  display: none;
}
</style>
