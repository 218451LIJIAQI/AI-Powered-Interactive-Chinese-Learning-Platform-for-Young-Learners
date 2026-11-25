<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('adminAi.settingsTitle') }}</h1>
    <AdminAiTabs />
    <DataNotice :error="actionError" />

    <StatePanel
      v-if="settingsResource.loading.value"
      heading-tag="h2"
      tone="loading"
      :title="t('adminAi.settingsLoading')"
      :message="t('adminAi.settingsLoadingHint')"
    />
    <StatePanel
      v-else-if="settingsResource.error.value"
      heading-tag="h2"
      tone="error"
      :title="t('adminAi.settingsError')"
      :message="settingsResource.error.value"
      @retry="settingsResource.reload"
    />

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
          :disabled="saving"
          required
          :maxlength="MAX_NOTICE"
          :aria-invalid="noticeError ? 'true' : undefined"
          :aria-describedby="noticeError ? `${baseId}-notice-error ${baseId}-notice-hint` : `${baseId}-notice-hint`"
          @blur="validateNotice"
        />
        <div class="field-foot">
          <p :id="`${baseId}-notice-hint`" class="field-hint">{{ t('adminAi.noticeHint') }}</p>
          <p class="field-hint field-counter">{{ form.aiNoticeText.length }} / {{ MAX_NOTICE }}</p>
        </div>
        <p v-if="noticeError" :id="`${baseId}-notice-error`" class="field-error" role="alert">{{ noticeError }}</p>
      </div>

      <div class="admin-ai-settings-grid">
        <section class="admin-section" :aria-labelledby="`${baseId}-safety-title`">
          <h3 :id="`${baseId}-safety-title`" class="admin-section-title">{{ t('adminAi.safety') }}</h3>
          <div class="admin-ai-toggles">
            <label v-for="option in SAFETY_SWITCHES" :key="option.field" class="admin-ai-toggle">
              <span class="row-list-copy">
                <span class="row-list-title">{{ t(option.label) }}</span>
                <span class="row-list-meta">{{ t(option.hint) }}</span>
              </span>
              <span class="admin-ai-toggle-state" aria-hidden="true">
                {{ form[option.field] ? t('adminAi.on') : t('adminAi.off') }}
              </span>
              <input v-model="form[option.field]" class="switch" type="checkbox" role="switch" :disabled="saving" />
            </label>
          </div>
        </section>

        <section class="admin-section" :aria-labelledby="`${baseId}-limits-title`">
          <h3 :id="`${baseId}-limits-title`" class="admin-section-title">{{ t('adminAi.limits') }}</h3>
          <div class="callout">
            <span>
              <strong class="row-list-title">
                {{
                  t('adminAi.limitsValue', {
                    perMinute: formatNumber(settings.requestsPerMinute),
                    perDay: formatNumber(settings.requestsPerDay),
                  })
                }}
              </strong>
              <span class="admin-meta">{{ t('adminAi.limitsHint') }}</span>
            </span>
          </div>
        </section>
      </div>

      <footer class="card-footer admin-ai-settings-foot">
        <p class="admin-meta">
          <template v-if="settings.lastChangedAt">
            {{
              settings.lastChangedBy
                ? t('adminAi.lastChanged', {
                    time: formatDateTime(settings.lastChangedAt),
                    name: settings.lastChangedBy,
                  })
                : formatDateTime(settings.lastChangedAt)
            }}
          </template>
          <template v-else>{{ t('adminAi.neverChanged') }}</template>
        </p>
        <RouterLink
          class="btn btn-ghost btn-sm"
          :to="{ name: 'admin-audit-log', query: { action: 'ADMIN_PLATFORM_SETTINGS_UPDATED' } }"
        >
          {{ t('adminAi.viewAuditLog') }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
        <button
          class="btn btn-primary admin-ai-save"
          type="submit"
          :disabled="!changed || saving || Boolean(noticeError)"
          :aria-busy="saving"
          @mousedown.prevent
        >
          {{ t('adminAi.saveSettings') }}
        </button>
      </footer>
    </form>
    <AdminHintRules @saved="showToast" />
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, toRef, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { adminAiApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import AdminHintRules from '@/components/admin/AdminHintRules.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import { normalizeEditableAiNotice } from '@/plugins/aiSafetyMessages'
import type { AdminAiSettingsUpdate } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime, formatNumber } from '@/utils/format'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

/**
 * Admin AI settings: the AI notice shown with every AI answer, the sensitive-word filter, the AI safety check, the
 * request limits from the server configuration (read-only) and who changed the settings last.
 */
const MAX_NOTICE = 200
const SAFETY_SWITCHES = [
  { field: 'sensitiveWordFilter', label: 'adminAi.sensitiveFilter', hint: 'adminAi.sensitiveFilterHint' },
  { field: 'aiSafetyCheck', label: 'adminAi.safetyCheck', hint: 'adminAi.safetyCheckHint' },
] as const

const { t } = useLocalMessages({ adminAi: adminAiMessages })
const baseId = useId()
const actionError = ref<string | null>(null)
const { toast, showToast } = useToast()

const settingsResource = useResource(adminAiApi.settings)
const settings = settingsResource.data
const form = reactive<AdminAiSettingsUpdate>({ aiNoticeText: '', sensitiveWordFilter: true, aiSafetyCheck: true })
const saving = ref(false)
const isUnmounted = useIsUnmounted()
const { error: noticeError, validate: validateNotice } = useFieldCheck(toRef(form, 'aiNoticeText'), (text) =>
  text.trim() ? null : t('adminAi.noticeRequired'),
)

watch(settings, (value) => {
  if (!value) return
  form.aiNoticeText = normalizeEditableAiNotice(value.aiNoticeText)
  form.sensitiveWordFilter = value.sensitiveWordFilter
  form.aiSafetyCheck = value.aiSafetyCheck
})

const changed = computed(() => {
  const saved = settings.value
  return (
    saved !== null &&
    (form.aiNoticeText.trim() !== normalizeEditableAiNotice(saved.aiNoticeText) ||
      form.sensitiveWordFilter !== saved.sensitiveWordFilter ||
      form.aiSafetyCheck !== saved.aiSafetyCheck)
  )
})

async function save(): Promise<void> {
  if (saving.value || !changed.value || !validateNotice()) return
  const authSession = getAuthSessionVersion()
  const isStale = () => isUnmounted() || authSession !== getAuthSessionVersion()
  saving.value = true
  actionError.value = null
  try {
    const updated = await adminAiApi.updateSettings({ ...form, aiNoticeText: form.aiNoticeText.trim() })
    if (isStale()) return
    settingsResource.replace(updated)
    showToast(t('adminAi.saved'))
  } catch (reason) {
    if (isStale()) return
    actionError.value = resolveApiError(reason, t('adminAi.saveError'))
  } finally {
    if (!isStale()) saving.value = false
  }
}
</script>
