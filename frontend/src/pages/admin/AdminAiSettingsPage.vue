<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminAi.settingsTitle')" hide-title />
    <AdminAiTabs />
    <DataNotice :error="actionError" />

    <StatePanel
      v-if="settingsResource.loading.value"
      tone="loading"
      :title="t('adminAi.settingsLoading')"
      :message="t('adminAi.settingsLoadingHint')"
    />
    <StatePanel
      v-else-if="settingsResource.error.value && !settings"
      tone="error"
      icon="alert"
      :title="t('adminAi.settingsError')"
      :message="settingsResource.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="settingsResource.reload">{{ t('adminAi.retry') }}</button>
      </template>
    </StatePanel>

    <form
      v-else-if="settings"
      class="panel admin-detail"
      :aria-labelledby="`${baseId}-title`"
      novalidate
      @submit.prevent="save"
    >
      <h2 :id="`${baseId}-title`" class="card-title">{{ t('adminAi.settingsTitle') }}</h2>

      <div class="field">
        <label class="field-label" :for="`${baseId}-notice`">
          {{ t('adminAi.noticeText') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          :id="`${baseId}-notice`"
          v-model="form.aiNoticeText"
          class="textarea"
          rows="3"
          required
          :maxlength="MAX_NOTICE"
          :aria-invalid="noticeMissing || undefined"
          :aria-describedby="`${baseId}-notice-hint`"
        />
        <div class="field-foot">
          <p :id="`${baseId}-notice-hint`" class="field-hint">{{ t('adminAi.noticeHint') }}</p>
          <p class="field-hint field-counter">{{ form.aiNoticeText.length }} / {{ MAX_NOTICE }}</p>
        </div>
        <p v-if="noticeMissing" class="field-error" role="alert">{{ t('adminAi.noticeRequired') }}</p>
      </div>

      <div class="admin-ai-settings-grid">
        <section class="admin-section" :aria-labelledby="`${baseId}-safety-title`">
          <h3 :id="`${baseId}-safety-title`" class="admin-section-title">{{ t('adminAi.safety') }}</h3>
          <div class="admin-ai-toggles">
            <label class="admin-ai-toggle">
              <span class="row-list-copy">
                <span class="row-list-title">{{ t('adminAi.sensitiveFilter') }}</span>
                <span class="row-list-meta">{{ t('adminAi.sensitiveFilterHint') }}</span>
              </span>
              <span class="admin-ai-toggle-state" aria-hidden="true">
                {{ form.sensitiveWordFilter ? t('adminAi.on') : t('adminAi.off') }}
              </span>
              <input v-model="form.sensitiveWordFilter" class="switch" type="checkbox" role="switch" />
            </label>
            <label class="admin-ai-toggle">
              <span class="row-list-copy">
                <span class="row-list-title">{{ t('adminAi.safetyCheck') }}</span>
                <span class="row-list-meta">{{ t('adminAi.safetyCheckHint') }}</span>
              </span>
              <span class="admin-ai-toggle-state" aria-hidden="true">
                {{ form.aiSafetyCheck ? t('adminAi.on') : t('adminAi.off') }}
              </span>
              <input v-model="form.aiSafetyCheck" class="switch" type="checkbox" role="switch" />
            </label>
          </div>
        </section>

        <section class="admin-section" :aria-labelledby="`${baseId}-limits-title`">
          <h3 :id="`${baseId}-limits-title`" class="admin-section-title">{{ t('adminAi.limits') }}</h3>
          <div class="admin-sunken">
            <p class="row-list-title">
              {{
                t('adminAi.limitsValue', {
                  perMinute: formatNumber(settings.requestsPerMinute),
                  perDay: formatNumber(settings.requestsPerDay),
                })
              }}
            </p>
            <p class="admin-meta">{{ t('adminAi.limitsHint') }}</p>
          </div>
        </section>
      </div>

      <footer class="card-footer admin-ai-settings-foot">
        <p class="admin-meta">
          <template v-if="settings.lastChangedAt">
            {{
              t('adminAi.lastChanged', {
                time: formatDateTime(settings.lastChangedAt),
                name: settings.lastChangedBy ?? '',
              })
            }}
          </template>
          <template v-else>{{ t('adminAi.neverChanged') }}</template>
        </p>
        <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'admin-audit-log' }">
          {{ t('adminAi.viewAuditLog') }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
        <button class="btn btn-primary admin-ai-save" type="submit" :disabled="!changed || saving" :aria-busy="saving">
          {{ t('adminAi.saveSettings') }}
        </button>
      </footer>
    </form>
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { adminAiApi } from '@/api/ai'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import type { AdminAiSettingsUpdate } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime, formatNumber } from '@/utils/format'
import '@/styles/ops-admin.css'

/**
 * Admin AI settings: the AI notice shown with every AI answer, the sensitive-word filter, the AI safety check, the
 * request limits from the server configuration (read-only) and who changed the settings last.
 */
const MAX_NOTICE = 200

const { t } = useLocalMessages({ adminAi: adminAiMessages })
const baseId = useId()
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()

const settingsResource = useResource(() => adminAiApi.settings())
const settings = computed(() => settingsResource.data.value)
const form = reactive<AdminAiSettingsUpdate>({ aiNoticeText: '', sensitiveWordFilter: true, aiSafetyCheck: true })
const saving = ref(false)
const noticeMissing = ref(false)

watch(
  settings,
  (value) => {
    if (!value) return
    form.aiNoticeText = value.aiNoticeText
    form.sensitiveWordFilter = value.sensitiveWordFilter
    form.aiSafetyCheck = value.aiSafetyCheck
  },
  { immediate: true },
)

const changed = computed(
  () =>
    Boolean(settings.value) &&
    (form.aiNoticeText.trim() !== settings.value!.aiNoticeText ||
      form.sensitiveWordFilter !== settings.value!.sensitiveWordFilter ||
      form.aiSafetyCheck !== settings.value!.aiSafetyCheck),
)

async function save(): Promise<void> {
  noticeMissing.value = !form.aiNoticeText.trim()
  if (noticeMissing.value || saving.value) return
  saving.value = true
  clearFeedback()
  try {
    settingsResource.data.value = await adminAiApi.updateSettings({ ...form, aiNoticeText: form.aiNoticeText.trim() })
    showToast(t('adminAi.saved'))
  } catch (reason) {
    showError(resolveApiError(reason, t('adminAi.saveError')))
  } finally {
    saving.value = false
  }
}
</script>
