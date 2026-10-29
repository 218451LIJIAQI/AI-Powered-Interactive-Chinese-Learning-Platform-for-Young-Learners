<template>
  <div class="page-stack admin-ai-page">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminAi.settingsTitle')" :description="t('adminAi.aiSettingsSubtitle')" />
      <AdminAiTabs />
    </div>
    <DataNotice :success="actionSuccess" :error="actionError" />

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
      class="admin-sheet admin-ai-settings"
      :aria-label="t('adminAi.settingsTitle')"
      novalidate
      @submit.prevent="save"
    >
      <section class="admin-ai-setting" :aria-labelledby="`${baseId}-notice-title`">
        <div class="admin-ai-setting-copy">
          <h2 :id="`${baseId}-notice-title`">{{ t('adminAi.noticeTitle') }}</h2>
          <p>{{ t('adminAi.noticeHint') }}</p>
        </div>
        <div class="admin-ai-setting-control">
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
              :aria-describedby="`${baseId}-notice-count`"
            />
            <div class="field-foot">
              <p :id="`${baseId}-notice-count`" class="field-hint field-counter">
                {{ form.aiNoticeText.length }} / {{ MAX_NOTICE }}
              </p>
            </div>
            <p v-if="noticeMissing" class="field-error" role="alert">{{ t('adminAi.noticeRequired') }}</p>
          </div>
          <div class="admin-ai-preview">
            <p class="admin-reader-label">{{ t('adminAi.preview') }}</p>
            <AiNotice :text="form.aiNoticeText.trim() || t('adminAi.previewEmpty')" />
          </div>
        </div>
      </section>

      <section class="admin-ai-setting" :aria-labelledby="`${baseId}-safety-title`">
        <div class="admin-ai-setting-copy">
          <h2 :id="`${baseId}-safety-title`">{{ t('adminAi.safety') }}</h2>
          <p>{{ t('adminAi.safetyIntro') }}</p>
        </div>
        <div class="admin-ai-setting-control admin-ai-toggles">
          <label class="admin-ai-toggle">
            <span class="admin-ai-toggle-copy">
              <strong>{{ t('adminAi.sensitiveFilter') }}</strong>
              <small>{{ t('adminAi.sensitiveFilterHint') }}</small>
            </span>
            <span class="admin-ai-toggle-state" :class="{ 'is-on': form.sensitiveWordFilter }" aria-hidden="true">
              {{ form.sensitiveWordFilter ? t('adminAi.on') : t('adminAi.off') }}
            </span>
            <input v-model="form.sensitiveWordFilter" class="switch" type="checkbox" role="switch" />
          </label>
          <label class="admin-ai-toggle">
            <span class="admin-ai-toggle-copy">
              <strong>{{ t('adminAi.safetyCheck') }}</strong>
              <small>{{ t('adminAi.safetyCheckHint') }}</small>
            </span>
            <span class="admin-ai-toggle-state" :class="{ 'is-on': form.aiSafetyCheck }" aria-hidden="true">
              {{ form.aiSafetyCheck ? t('adminAi.on') : t('adminAi.off') }}
            </span>
            <input v-model="form.aiSafetyCheck" class="switch" type="checkbox" role="switch" />
          </label>
        </div>
      </section>

      <section class="admin-ai-setting" :aria-labelledby="`${baseId}-limits-title`">
        <div class="admin-ai-setting-copy">
          <h2 :id="`${baseId}-limits-title`">{{ t('adminAi.limits') }}</h2>
          <p>{{ t('adminAi.limitsHint') }}</p>
        </div>
        <dl class="admin-ai-setting-control admin-figures admin-ai-limits-box">
          <div class="admin-figure">
            <dt>{{ t('adminAi.perMinute') }}</dt>
            <dd>
              <strong class="stat-figure">{{ formatNumber(settings.requestsPerMinute) }}</strong>
              <span>{{ t('adminAi.requestsPerUser') }}</span>
            </dd>
          </div>
          <div class="admin-figure">
            <dt>{{ t('adminAi.perDay') }}</dt>
            <dd>
              <strong class="stat-figure">{{ formatNumber(settings.requestsPerDay) }}</strong>
              <span>{{ t('adminAi.requestsPerUser') }}</span>
            </dd>
          </div>
        </dl>
      </section>

      <footer class="admin-ai-settings-foot">
        <p>
          <template v-if="settings.lastChangedAt">
            {{
              t('adminAi.lastChanged', {
                time: formatDateTime(settings.lastChangedAt),
                name: settings.lastChangedBy ?? '',
              })
            }}
          </template>
          <template v-else>{{ t('adminAi.neverChanged') }}</template>
          <RouterLink class="btn btn-link" :to="{ name: 'admin-audit-log' }">
            {{ t('adminAi.viewAuditLog') }}
            <IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </p>
        <span v-if="changed" class="admin-ai-unsaved" role="status">{{ t('adminAi.unsaved') }}</span>
        <button class="btn btn-primary" type="submit" :disabled="!changed || saving" :aria-busy="saving">
          <IconGlyph v-if="!saving" name="check" :size="16" />
          {{ t('adminAi.saveSettings') }}
        </button>
      </footer>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { adminAiApi } from '@/api/ai'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import AiNotice from '@/components/ai/AiNotice.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import type { AdminAiSettingsUpdate } from '@/types/ai'
import { formatDateTime, formatNumber } from '@/utils/format'
import '@/styles/ops-admin.css'

/**
 * Admin AI settings: the AI notice shown with every AI answer (with a live preview), the sensitive-word filter, the
 * AI safety check, the request limits from the server configuration (read-only) and who changed the settings last.
 */
const MAX_NOTICE = 200

const { t } = useLocalMessages({ adminAi: adminAiMessages })
const baseId = useId()
const { actionError, actionSuccess, runAction } = useActionFeedback()

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
  await runAction(t('adminAi.saveError'), async () => {
    settingsResource.data.value = await adminAiApi.updateSettings({ ...form, aiNoticeText: form.aiNoticeText.trim() })
    return t('adminAi.saved')
  })
  saving.value = false
}
</script>
