<template>
  <AppOverlay
    role="alertdialog"
    :title="t('account.deleteAccount')"
    :subtitle="t('account.deleteDialogSubtitle')"
    :described-by-id="warningId"
    :busy="deleting"
    test-id="account-delete-dialog"
    @close="emit('close')"
  >
    <p :id="warningId" class="callout is-danger">
      <IconGlyph name="alert" :size="18" />
      <span>{{ t('account.deleteCopy') }}</span>
    </p>
    <form id="account-delete-form" novalidate @submit.prevent="deleteAccount">
      <div class="field">
        <label for="account-delete-confirm">
          {{ t('account.typeToConfirm', { email: currentEmail })
          }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="account-delete-confirm"
          v-model="confirmText"
          data-autofocus
          :readonly="deleting"
          class="input"
          type="email"
          autocomplete="off"
          spellcheck="false"
          required
        />
      </div>
    </form>
    <div v-if="error" class="callout is-danger" role="alert">
      <IconGlyph name="alert" :size="18" />
      <span>{{ error }}</span>
    </div>
    <template #footer>
      <button class="btn btn-secondary" type="button" :disabled="deleting" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-danger"
        type="submit"
        form="account-delete-form"
        :disabled="!confirmed || deleting"
        :aria-busy="deleting"
        data-testid="account-delete-confirm"
      >
        <IconGlyph name="trash" :size="16" />
        {{ deleting ? t('account.deleting') : t('account.deleteForever') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { accountOpsApi } from '@/api/ops'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { accountMessages } from '@/plugins/accountMessages'
import { useAuthStore } from '@/stores/auth'
import { resolveApiError } from '@/utils/apiError'

const props = defineProps<{
  currentEmail: string
}>()
const emit = defineEmits<{ close: [] }>()

const { t } = useLocalMessages({ account: accountMessages })
const auth = useAuthStore()
const router = useRouter()
const isUnmounted = useIsUnmounted()
const warningId = `${useId()}-warning`

const confirmText = ref('')
const error = ref<string | null>(null)
const deleting = ref(false)
const confirmed = computed(() => confirmText.value.trim().toLowerCase() === props.currentEmail.toLowerCase())

watch(confirmText, () => {
  error.value = null
})

async function deleteAccount(): Promise<void> {
  if (!confirmed.value || deleting.value) return
  deleting.value = true
  error.value = null
  const version = getAuthSessionVersion()
  try {
    await accountOpsApi.deleteAccount(confirmText.value.trim())
    if (version !== getAuthSessionVersion()) return
    emit('close')
    auth.clearSessionInAllTabs()
    await router.replace({ name: 'landing', query: { notice: 'account-deleted' } })
  } catch (reason) {
    if (!isUnmounted() && version === getAuthSessionVersion()) {
      error.value = resolveApiError(reason, t('account.deleteError'))
    }
  } finally {
    if (!isUnmounted()) deleting.value = false
  }
}
</script>
