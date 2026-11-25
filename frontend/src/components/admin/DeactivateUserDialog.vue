<template>
  <AppOverlay
    :title="t('adminUsers.deactivateTitle', { name: user.fullName })"
    :subtitle="user.role ? t('adminUsers.dialogSubtitle', { email: user.email, role: t(`roles.${user.role}`) }) : ''"
    :described-by-id="impactId"
    role="alertdialog"
    :busy="busy"
    test-id="admin-users-status-dialog"
    @close="emit('cancel')"
  >
    <div :id="impactId" class="callout" data-testid="admin-users-impact">
      <IconGlyph name="alert" :size="16" />
      <span>
        <strong>{{ t('adminUsers.impactSignedOut', { name: user.fullName }) }}</strong>
        <span>{{ t('adminUsers.impactKept') }}</span>
      </span>
    </div>
    <div class="field">
      <label class="field-label" for="admin-users-reason">
        {{ t('adminUsers.reason') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <textarea
        id="admin-users-reason"
        ref="reasonField"
        v-model="reason"
        class="textarea"
        rows="4"
        maxlength="500"
        :placeholder="t('adminUsers.reasonPlaceholder')"
        required
        data-autofocus
        :disabled="busy"
        :aria-invalid="Boolean(reasonError)"
        :aria-describedby="reasonError ? 'admin-users-reason-error admin-users-reason-hint' : 'admin-users-reason-hint'"
        data-testid="admin-users-reason"
        @blur="validate"
      />
      <p id="admin-users-reason-hint" class="field-hint">{{ t('adminUsers.reasonHint') }}</p>
      <p v-if="reasonError" id="admin-users-reason-error" class="field-error" role="alert">{{ reasonError }}</p>
    </div>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <template #footer>
      <!--
        A press on a footer button leaves focus in the field above: the field's check on blur would add an error line,
        move this footer down and make the press miss the button.
      -->
      <button
        class="btn btn-secondary"
        type="button"
        :disabled="busy"
        data-testid="admin-users-status-dialog-cancel"
        @mousedown.prevent
        @click="emit('cancel')"
      >
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-danger"
        type="button"
        :disabled="busy || Boolean(reasonError)"
        :aria-busy="busy || undefined"
        data-testid="admin-users-status-dialog-confirm"
        @mousedown.prevent
        @click="confirm"
      >
        <IconGlyph name="lock" :size="16" />
        <span>{{ t('adminUsers.deactivate') }}</span>
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { ref, useId, watch } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import type { AdminUserDetail } from '@/types/ops'

const props = defineProps<{ user: AdminUserDetail; busy: boolean; error: string | null }>()
const emit = defineEmits<{ confirm: [reason: string]; cancel: []; clearError: [] }>()
const { t } = useLocalMessages({ adminUsers: adminUsersMessages })
const impactId = `${useId()}-impact`
const reason = ref('')
const reasonField = ref<HTMLTextAreaElement | null>(null)
const { error: reasonError, validate } = useFieldCheck(reason, (text) =>
  text.trim() ? null : t('adminUsers.reasonMissing'),
)

watch(reason, () => {
  if (props.error) emit('clearError')
})

function confirm(): void {
  if (props.busy) return
  if (validate()) emit('confirm', reason.value.trim())
  else reasonField.value?.focus()
}
</script>
