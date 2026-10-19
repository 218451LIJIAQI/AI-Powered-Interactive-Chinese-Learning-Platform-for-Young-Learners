<template>
  <div class="page-stack admin-settings-page">
    <PageHeaderBlock :title="t('adminSettings.title')" :description="t('adminSettings.description')" icon="settings" />

    <DataNotice :using-fallback="usingFallback" :error="actionError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-split">
        <div class="admin-stack">
          <article class="panel admin-panel">
            <div class="admin-settings-head">
              <span class="admin-icon-badge admin-tone-primary" aria-hidden="true">
                <IconGlyph name="language" :size="20" />
              </span>
              <div>
                <h2 class="card-title">{{ t('adminSettings.interfaceLanguage') }}</h2>
                <p class="card-subtitle">{{ t('adminSettings.interfaceLanguageHint') }}</p>
              </div>
            </div>

            <div class="field">
              <label for="admin-settings-language">{{ t('adminSettings.languageAria') }}</label>
              <select
                id="admin-settings-language"
                v-model="ui.locale"
                class="select"
                :disabled="platformSettingsReadOnly"
                @change="ui.setLocale(ui.locale)"
              >
                <option value="en">English (EN)</option>
                <option value="ms">Bahasa Melayu (BM)</option>
                <option value="zh-CN">简体中文</option>
              </select>
            </div>

            <div class="form-actions">
              <button class="btn btn-primary" type="button" :disabled="platformSettingsReadOnly" @click="saveLocale">
                {{ t('adminSettings.save') }}
              </button>
            </div>
          </article>

          <article class="panel admin-panel">
            <div class="admin-settings-head">
              <span class="admin-icon-badge admin-tone-info" aria-hidden="true">
                <IconGlyph name="safe" :size="20" />
              </span>
              <div>
                <h2 class="card-title">{{ t('adminSettings.disclaimer') }}</h2>
                <p class="card-subtitle">{{ t('adminSettings.disclaimerHint') }}</p>
              </div>
            </div>

            <div class="field">
              <label for="admin-settings-disclaimer">{{ t('adminSettings.disclaimerAria') }}</label>
              <textarea
                id="admin-settings-disclaimer"
                v-model="disclaimerText"
                class="textarea"
                maxlength="200"
                aria-describedby="admin-settings-disclaimer-hint"
                :disabled="platformSettingsFormDisabled"
              />
              <div id="admin-settings-disclaimer-hint" class="admin-settings-hint-row field-hint">
                <span>{{ t('adminSettings.disclaimerStored') }}</span>
                <span class="admin-settings-count">{{ disclaimerText.length }} / 200</span>
              </div>
            </div>

            <div class="form-actions">
              <button
                class="btn btn-primary"
                type="button"
                :disabled="platformSettingsFormDisabled"
                :aria-busy="savingPlatformSettings"
                @click="saveDisclaimer"
              >
                {{ platformSettingsSaveLabel }}
              </button>
            </div>
          </article>

          <article class="panel admin-panel">
            <div class="admin-settings-head">
              <span class="admin-icon-badge admin-tone-success" aria-hidden="true">
                <IconGlyph name="shield" :size="20" />
              </span>
              <div>
                <h2 class="card-title">{{ t('adminSettings.safety') }}</h2>
                <p class="card-subtitle">{{ t('adminSettings.safetyHint') }}</p>
              </div>
            </div>

            <ul class="list-divided admin-feed">
              <li v-for="item in safetyOptions" :key="item.key">
                <label class="admin-settings-switch-row">
                  <span class="admin-feed-copy">
                    <span class="admin-feed-title">{{ item.title }}</span>
                    <span class="admin-feed-text">{{ item.description }}</span>
                  </span>
                  <input
                    class="switch"
                    type="checkbox"
                    role="switch"
                    :checked="localSettings[item.key]"
                    :disabled="platformSettingsFormDisabled"
                    @change="toggleSetting(item.key)"
                  />
                </label>
              </li>
            </ul>

            <div class="form-actions">
              <button
                class="btn btn-primary"
                type="button"
                :disabled="platformSettingsFormDisabled"
                :aria-busy="savingPlatformSettings"
                @click="saveLocalPreferences"
              >
                {{ platformSettingsSaveSettingsLabel }}
              </button>
            </div>
          </article>

          <article class="panel admin-panel">
            <div class="admin-settings-head">
              <span class="admin-icon-badge admin-tone-neutral" aria-hidden="true">
                <IconGlyph name="settings" :size="20" />
              </span>
              <div>
                <h2 class="card-title">{{ t('adminSettings.basic') }}</h2>
                <p class="card-subtitle">{{ t('adminSettings.basicHint') }}</p>
              </div>
            </div>

            <div class="field">
              <label for="admin-settings-platform-name">{{ t('adminSettings.platformName') }}</label>
              <input
                id="admin-settings-platform-name"
                class="input"
                :value="appName"
                readonly
                aria-describedby="admin-settings-platform-name-hint"
              />
              <p id="admin-settings-platform-name-hint" class="field-hint">{{ t('adminSettings.basicNote') }}</p>
            </div>
          </article>
        </div>

        <aside class="admin-stack">
          <article class="panel admin-panel">
            <div class="card-header">
              <h2 class="card-title">{{ t('adminSettings.systemInfo') }}</h2>
              <button class="btn btn-link" type="button" @click="openAnalytics">
                {{ t('adminSettings.viewAnalytics') }}
              </button>
            </div>
            <dl class="kv-list">
              <dt>{{ t('adminSettings.platformVersion') }}</dt>
              <dd>{{ t('adminSettings.repositoryBuild') }}</dd>
              <dt>{{ t('adminSettings.environment') }}</dt>
              <dd>{{ environmentLabel }}</dd>
              <dt>{{ t('adminSettings.lastHealth') }}</dt>
              <dd>{{ lastHealthCheckLabel }}</dd>
              <dt>{{ t('adminSettings.service') }}</dt>
              <dd>{{ serviceLabel }}</dd>
              <dt>{{ t('adminSettings.registeredStudents') }}</dt>
              <dd>{{ formatNumber(registeredStudents) }}</dd>
              <dt>{{ t('adminSettings.databaseStatus') }}</dt>
              <dd>
                <StatusPill
                  :label="databaseStatusLabel"
                  :tone="health.status === 'UP' ? 'success' : 'danger'"
                  icon="status"
                />
              </dd>
            </dl>
          </article>

          <article class="panel admin-panel">
            <h2 class="card-title">{{ t('adminSettings.quickActions') }}</h2>
            <div class="admin-quick-actions">
              <button
                v-for="item in quickActions"
                :key="item.label"
                class="btn btn-secondary"
                type="button"
                @click="item.action"
              >
                <IconGlyph :name="item.icon" :size="16" />
                <span>{{ item.label }}</span>
              </button>
            </div>
          </article>

          <article class="panel admin-panel">
            <div class="card-header">
              <div>
                <h2 class="card-title">{{ t('adminSettings.recentNotifications') }}</h2>
                <p class="card-subtitle">{{ t('adminSettings.notificationsOpenHint') }}</p>
              </div>
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                :disabled="markingNotificationsRead || !hasUnreadNotifications"
                :aria-busy="markingNotificationsRead"
                @click="markAllAdminNotificationsRead"
              >
                {{ markAllNotificationsLabel }}
              </button>
            </div>

            <ul v-if="adminNotifications.length" class="list-divided admin-feed">
              <li v-for="item in adminNotifications" :key="item.id">
                <NotificationLinkButton
                  class="admin-feed-link"
                  :notification-id="item.id"
                  :target-path="item.targetPath"
                  fallback-to="/admin/settings"
                  @read="markAdminNotificationRead"
                >
                  <span class="admin-feed-item">
                    <span class="admin-notification-dot" :class="{ 'is-unread': item.status !== 'READ' }" />
                    <span class="admin-feed-copy">
                      <span class="admin-feed-title">{{ item.title }}</span>
                      <span class="admin-feed-text">{{ item.message }}</span>
                      <span class="admin-feed-meta">{{ formatDateTime(item.createdAt) }}</span>
                    </span>
                  </span>
                </NotificationLinkButton>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="bell"
              :title="t('adminSettings.noNotifications')"
              :message="t('adminSettings.noNotificationsHint')"
            />
          </article>

          <article class="panel admin-panel">
            <div class="admin-settings-head">
              <span class="admin-icon-badge admin-tone-primary" aria-hidden="true">
                <IconGlyph name="help" :size="20" />
              </span>
              <h2 class="card-title">{{ t('adminSettings.note') }}</h2>
            </div>
            <ul class="admin-settings-notes">
              <li>{{ t('adminSettings.retentionNote') }}</li>
              <li>{{ t('adminSettings.timezoneNote') }}</li>
              <li>{{ t('adminSettings.endpointNote') }}</li>
            </ul>
          </article>
        </aside>
      </section>

      <article class="panel admin-panel">
        <div class="card-header">
          <div>
            <h2 class="card-title">{{ t('adminSettings.auditLogs') }}</h2>
            <p class="card-subtitle">{{ t('adminSettings.auditHint') }}</p>
          </div>
        </div>

        <form class="admin-filter-bar" data-testid="audit-filters" @submit.prevent="applyAuditFilters">
          <div class="field">
            <label for="admin-audit-from">{{ t('adminSettings.auditFrom') }}</label>
            <input
              id="admin-audit-from"
              v-model="auditFilters.from"
              class="input"
              type="date"
              :max="auditFilters.to || undefined"
              data-testid="audit-filter-from"
            />
          </div>
          <div class="field">
            <label for="admin-audit-to">{{ t('adminSettings.auditTo') }}</label>
            <input
              id="admin-audit-to"
              v-model="auditFilters.to"
              class="input"
              type="date"
              :min="auditFilters.from || undefined"
              :aria-invalid="auditRangeInvalid"
              :aria-describedby="auditRangeInvalid ? 'admin-audit-range-error' : undefined"
              data-testid="audit-filter-to"
            />
            <p v-if="auditRangeInvalid" id="admin-audit-range-error" class="field-error">
              {{ t('adminSettings.auditDateRangeError') }}
            </p>
          </div>
          <div class="field">
            <label for="admin-audit-action">{{ t('adminSettings.auditActionType') }}</label>
            <input
              id="admin-audit-action"
              v-model="auditFilters.actionType"
              class="input"
              list="admin-audit-action-types"
              maxlength="80"
              :placeholder="t('adminSettings.auditActionTypePlaceholder')"
              data-testid="audit-filter-action"
            />
            <datalist id="admin-audit-action-types">
              <option v-for="type in AUDIT_ACTION_TYPE_SUGGESTIONS" :key="type" :value="type" />
            </datalist>
          </div>
          <div class="field">
            <label for="admin-audit-actor">{{ t('adminSettings.auditActor') }}</label>
            <input
              id="admin-audit-actor"
              v-model="auditFilters.actor"
              class="input"
              maxlength="120"
              :placeholder="t('adminSettings.auditActorPlaceholder')"
              data-testid="audit-filter-actor"
            />
          </div>
          <div class="admin-filter-actions">
            <button class="btn btn-ghost" type="button" @click="resetAuditFilters">
              {{ t('adminSettings.auditReset') }}
            </button>
            <button
              class="btn btn-primary"
              type="submit"
              :disabled="auditResource.loading.value"
              :aria-busy="auditResource.loading.value"
            >
              <IconGlyph name="filter" :size="16" />
              <span>{{ t('adminSettings.auditApply') }}</span>
            </button>
          </div>
        </form>

        <div
          v-if="filteredAuditLogs.length"
          class="data-table-wrap admin-table-wrap"
          role="region"
          :aria-label="t('adminSettings.auditLogs')"
          tabindex="0"
        >
          <table class="data-table admin-table admin-table-static admin-settings-audit-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminSettings.auditEvent') }}</th>
                <th scope="col">{{ t('adminSettings.auditCategory') }}</th>
                <th scope="col">{{ t('adminSettings.auditActorColumn') }}</th>
                <th scope="col">{{ t('adminSettings.auditTime') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredAuditLogs" :key="item.id">
                <td>
                  <div class="admin-table-cell">
                    <span class="admin-icon-badge is-sm" :class="`admin-tone-${item.tone}`" aria-hidden="true">
                      <IconGlyph :name="item.icon" :size="16" />
                    </span>
                    <div class="admin-table-cell-copy">
                      <strong>{{ item.title }}</strong>
                      <span>{{ item.description }}</span>
                    </div>
                  </div>
                </td>
                <td :data-label="t('adminSettings.auditCategory')">
                  <span class="tag">{{ item.tag }}</span>
                </td>
                <td :data-label="t('adminSettings.auditActorColumn')">
                  <div class="admin-table-cell-copy">
                    <strong>{{ item.actor }}</strong>
                    <small>{{ item.trace }}</small>
                  </div>
                </td>
                <td class="admin-table-nowrap" :data-label="t('adminSettings.auditTime')">{{ item.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <StatePanel
          v-else-if="auditResource.loading.value"
          compact
          tone="loading"
          icon="progress"
          :title="t('adminSettings.auditLoading')"
          :message="t('adminSettings.auditHint')"
        />
        <StatePanel
          v-else
          compact
          icon="history"
          :title="t('adminSettings.auditEmpty')"
          :message="t('adminSettings.auditHint')"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="resetAuditFilters">
              {{ t('adminSettings.auditReset') }}
            </button>
          </template>
        </StatePanel>

        <PaginationBar
          :info="`${t('adminSettings.rowsPerPage')} ${AUDIT_PAGE_SIZE} · ${paginationLabel}`"
          :status="t('adminSettings.auditPageOf', { page: currentPage, total: totalPages })"
          :previous-label="t('adminSettings.previousPage')"
          :next-label="t('adminSettings.nextPage')"
          :previous-disabled="currentPage === 1"
          :next-disabled="currentPage === totalPages"
          icon-only
          @previous="goToPreviousPage"
          @next="goToNextPage"
        />
      </article>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { adminApi, notificationsApi, systemApi } from '@/api/services'
import { appEnv } from '@/config/env'
import IconGlyph from '@/components/common/IconGlyph.vue'
import type { AdminTone } from '@/components/admin/adminTone'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { useResource } from '@/composables/useResource'
import { adminAnalytics, adminAuditLogs } from '@/demo/data'
import { adminSettingsMessages } from '@/plugins/adminSettingsMessages'
import { useUiStore } from '@/stores/ui'
import type { AdminPlatformSettingsResponse, AuditLogResponse, PageResponse } from '@/types/api'
import { auditLogQuery, isValidAuditDateRange, type AuditLogFilterForm } from '@/utils/auditLog'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime, formatNumber } from '@/utils/format'
import { announceNotificationsRead } from '@/utils/notifications'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { adminSettings: adminSettingsMessages.en },
    ms: { adminSettings: adminSettingsMessages.ms },
    'zh-CN': { adminSettings: adminSettingsMessages['zh-CN'] },
  },
})
const ui = useUiStore()
const { statusLabel } = useStatusLabels()

type LocalSettingKey = 'sensitiveWordFilter' | 'aiSafetyCheck'

const DEFAULT_PLATFORM_NAME = 'AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia'

const platformSettingsFallback: AdminPlatformSettingsResponse = {
  platformName: DEFAULT_PLATFORM_NAME,
  aiDisclaimerText: t('adminSettings.defaultDisclaimer'),
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
}
const healthFallback = {
  status: 'UP',
  service: 'backend',
  timestamp: new Date().toISOString(),
}

const AUDIT_PAGE_SIZE = 10
const AUDIT_ACTION_TYPE_SUGGESTIONS = [
  'CONTENT_SUBMIT',
  'CONTENT_RESUBMIT_AFTER_EDIT',
  'CONTENT_APPROVE',
  'CONTENT_REJECT',
  'CONTENT_ARCHIVE',
  'CONTENT_RESTORE',
  'TEACHER_ACTIVITY_CREATE',
  'TEACHER_ACTIVITY_UPDATE',
  'TEACHER_ACTIVITY_DELETE',
  'ADMIN_TEACHER_APPROVE',
  'ADMIN_TEACHER_REJECT',
  'ADMIN_CONTENT_ARCHIVE',
  'ADMIN_ACTIVITY_STATUS',
  'ADMIN_PLATFORM_SETTINGS_UPDATED',
] as const

const auditFilters = reactive<AuditLogFilterForm>({ from: '', to: '', actionType: '', actor: '' })
const appliedAuditFilters = ref<AuditLogFilterForm>({ ...auditFilters })
const currentPage = ref(1)
const auditFallback: PageResponse<AuditLogResponse> = {
  items: adminAuditLogs,
  page: 0,
  size: AUDIT_PAGE_SIZE,
  totalItems: adminAuditLogs.length,
  totalPages: 1,
}
const auditResource = useResource(
  () => adminApi.auditLogPage(auditLogQuery(appliedAuditFilters.value, currentPage.value - 1, AUDIT_PAGE_SIZE)),
  auditFallback,
)
const analyticsResource = useResource(() => adminApi.analytics(), adminAnalytics)
const notificationsResource = useResource(() => adminApi.notifications({ size: 8 }), [])
const platformSettingsResource = useResource(() => adminApi.platformSettings(), platformSettingsFallback)
const healthResource = useResource(() => systemApi.health(), healthFallback)
const emptyHealth = {
  status: 'UNKNOWN',
  service: 'backend',
  timestamp: '',
}

const emptyAnalytics = {
  overview: {
    totalStudents: 0,
    totalTeachers: 0,
    pendingTeacherApprovals: 0,
    approvedTeachers: 0,
    rejectedTeachers: 0,
    publishedContentItems: 0,
    moderationActions: 0,
    auditLogEntries: 0,
    adminUnreadNotifications: 0,
    aiRequests: 0,
  },
  userRegistrationTrend: [],
  teacherApprovalBreakdown: [],
  contentStatusBreakdown: [],
  aiStatusBreakdown: [],
}

const auditPage = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? auditFallback : null))
const auditLogs = computed(() => auditPage.value?.items ?? [])
const analytics = computed(
  () => analyticsResource.data.value ?? (analyticsResource.usingFallback.value ? adminAnalytics : emptyAnalytics),
)
const adminNotifications = computed(() => notificationsResource.data.value ?? [])
const platformSettings = computed(() => platformSettingsResource.data.value ?? platformSettingsFallback)
const health = computed(
  () => healthResource.data.value ?? (healthResource.usingFallback.value ? healthFallback : emptyHealth),
)

const pageLoading = computed(
  () =>
    auditResource.loading.value ||
    analyticsResource.loading.value ||
    notificationsResource.loading.value ||
    platformSettingsResource.loading.value ||
    healthResource.loading.value,
)
const pageError = computed(
  () =>
    auditResource.error.value ||
    analyticsResource.error.value ||
    notificationsResource.error.value ||
    platformSettingsResource.error.value ||
    healthResource.error.value,
)
const usingFallback = computed(
  () =>
    auditResource.usingFallback.value ||
    analyticsResource.usingFallback.value ||
    notificationsResource.usingFallback.value ||
    platformSettingsResource.usingFallback.value ||
    healthResource.usingFallback.value,
)

const disclaimerText = ref(platformSettingsFallback.aiDisclaimerText)
const platformSettingsReadOnly = computed(() => platformSettingsResource.usingFallback.value)
const platformSettingsFormDisabled = computed(() => platformSettingsReadOnly.value || savingPlatformSettings.value)
const savingPlatformSettings = ref(false)
const markingNotificationsRead = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const localSettings = ref<Record<LocalSettingKey, boolean>>({
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
})

watch(
  platformSettings,
  (value) => {
    disclaimerText.value = value.aiDisclaimerText
    localSettings.value = {
      sensitiveWordFilter: value.sensitiveWordFilter,
      aiSafetyCheck: value.aiSafetyCheck,
    }
  },
  { immediate: true },
)

const safetyOptions = computed<Array<{ key: LocalSettingKey; title: string; description: string }>>(() => [
  {
    key: 'sensitiveWordFilter',
    title: t('adminSettings.sensitiveWordFilter'),
    description: t('adminSettings.sensitiveWordFilterHint'),
  },
  {
    key: 'aiSafetyCheck',
    title: t('adminSettings.aiSafetyCheck'),
    description: t('adminSettings.aiSafetyCheckHint'),
  },
])

const normalizedAuditLogs = computed(() =>
  auditLogs.value.map((item) => {
    const action = item.actionType.toUpperCase()
    const type =
      action.includes('LOGIN') || action.includes('PASSWORD')
        ? 'SECURITY'
        : action.includes('APPROVE') || action.includes('REJECT') || action.includes('ADMIN')
          ? 'ADMIN_ACTION'
          : action.includes('CONTENT') || action.includes('UPDATE')
            ? 'CONTENT_CHANGE'
            : 'MODERATION'

    const tone: AdminTone =
      type === 'SECURITY'
        ? 'warning'
        : type === 'ADMIN_ACTION'
          ? 'primary'
          : type === 'CONTENT_CHANGE'
            ? 'info'
            : 'success'

    const icon =
      type === 'SECURITY'
        ? 'profile'
        : type === 'ADMIN_ACTION'
          ? 'shield'
          : type === 'CONTENT_CHANGE'
            ? 'content'
            : 'moderation'

    const tag =
      type === 'SECURITY'
        ? t('adminSettings.security')
        : type === 'ADMIN_ACTION'
          ? t('adminSettings.adminAction')
          : type === 'CONTENT_CHANGE'
            ? t('adminSettings.contentChange')
            : t('adminSettings.moderation')

    return {
      id: item.id,
      title: auditActionLabel(action),
      description: item.description,
      actor: item.actorEmail || t('adminSettings.system'),
      time: formatDateTime(item.createdAt),
      trace: item.requestId
        ? t('adminSettings.requestTrace', { id: item.requestId })
        : item.ipAddress
          ? t('adminSettings.ipTrace', { address: item.ipAddress })
          : t('adminSettings.noTrace'),
      tone,
      icon,
      tag,
    }
  }),
)

/** Live check of the date range so the error shows next to the "To" field before the filters are applied. */
const auditRangeInvalid = computed(() => !isValidAuditDateRange(auditFilters))

/** The server filters, orders (newest first) and pages the audit log (D16). */
const filteredAuditLogs = computed(() => normalizedAuditLogs.value)
const totalAuditCount = computed(() => auditPage.value?.totalItems ?? 0)
const totalPages = computed(() => Math.max(1, auditPage.value?.totalPages ?? 1))
const paginationLabel = computed(() => {
  const start = totalAuditCount.value ? (currentPage.value - 1) * AUDIT_PAGE_SIZE + 1 : 0
  const end = Math.min(totalAuditCount.value, (currentPage.value - 1) * AUDIT_PAGE_SIZE + auditLogs.value.length)
  return t('adminSettings.pagination', { start, end, total: totalAuditCount.value })
})
const environmentLabel = computed(() =>
  usingFallback.value ? t('adminSettings.previewEnvironment') : t('adminSettings.liveEnvironment'),
)
const databaseStatusLabel = computed(() =>
  health.value.status === 'UP' ? t('adminSettings.healthy') : t('adminSettings.unavailable'),
)
const serviceLabel = computed(() => health.value.service || '--')
const lastHealthCheckLabel = computed(() => (health.value.timestamp ? formatDateTime(health.value.timestamp) : '--'))
const registeredStudents = computed(() => analytics.value.overview.totalStudents)
const appName = computed(() => platformSettings.value.platformName || appEnv.appName)
const hasUnreadNotifications = computed(() => adminNotifications.value.some((item) => item.status !== 'READ'))
const platformSettingsSaveLabel = computed(() =>
  savingPlatformSettings.value ? t('adminSettings.saving') : t('adminSettings.save'),
)
const platformSettingsSaveSettingsLabel = computed(() =>
  savingPlatformSettings.value ? t('adminSettings.saving') : t('adminSettings.saveSettings'),
)
const markAllNotificationsLabel = computed(() =>
  markingNotificationsRead.value ? t('adminSettings.marking') : t('adminSettings.markAllRead'),
)

const quickActions = computed(() => [
  { label: t('adminSettings.savePlatformSettings'), icon: 'refresh', action: () => saveLocalPreferences() },
  { label: t('adminSettings.viewAnalytics'), icon: 'download', action: () => openAnalytics() },
  { label: t('adminSettings.reviewAuditLogs'), icon: 'history', action: () => reviewAuditLogs() },
])

function markAdminNotificationRead(notificationId: number): void {
  notificationsResource.data.value = adminNotifications.value.map((item) =>
    item.id === notificationId ? { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() } : item,
  )
}

function toggleSetting(key: LocalSettingKey): void {
  if (platformSettingsFormDisabled.value) return
  localSettings.value[key] = !localSettings.value[key]
}

function saveLocale(): void {
  if (platformSettingsReadOnly.value) {
    actionError.value = t('adminSettings.previewSettings')
    actionSuccess.value = null
    return
  }
  ui.setLocale(ui.locale)
  actionError.value = null
  actionSuccess.value = t('adminSettings.localeSaved')
}

async function saveDisclaimer(): Promise<void> {
  await savePlatformSettings()
}

async function saveLocalPreferences(): Promise<void> {
  await savePlatformSettings()
}

async function markAllAdminNotificationsRead(): Promise<void> {
  if (markingNotificationsRead.value) return
  if (notificationsResource.usingFallback.value) {
    actionError.value = t('adminSettings.previewNotifications')
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  markingNotificationsRead.value = true
  try {
    const result = await notificationsApi.markAllRead()
    notificationsResource.data.value = adminNotifications.value.map((item) =>
      item.status === 'READ' ? item : { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() },
    )
    announceNotificationsRead()
    actionSuccess.value =
      result.updatedCount > 0
        ? t('adminSettings.notificationsMarked', { count: result.updatedCount })
        : t('adminSettings.allRead')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminSettings.markReadError'))
  } finally {
    markingNotificationsRead.value = false
  }
}

async function savePlatformSettings(): Promise<void> {
  if (savingPlatformSettings.value) return
  if (platformSettingsResource.usingFallback.value) {
    actionError.value = t('adminSettings.previewSettings')
    actionSuccess.value = null
    return
  }

  const normalizedDisclaimerText = disclaimerText.value.trim()
  if (!normalizedDisclaimerText) {
    actionError.value = t('adminSettings.disclaimerRequired')
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  savingPlatformSettings.value = true
  try {
    await adminApi.updatePlatformSettings({
      aiDisclaimerText: normalizedDisclaimerText,
      sensitiveWordFilter: localSettings.value.sensitiveWordFilter,
      aiSafetyCheck: localSettings.value.aiSafetyCheck,
    })
    await platformSettingsResource.reload()
    actionSuccess.value = t('adminSettings.settingsSaved')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminSettings.settingsSaveError'))
  } finally {
    savingPlatformSettings.value = false
  }
}

function reviewAuditLogs(): void {
  resetAuditFilters()
  actionError.value = null
  actionSuccess.value = t('adminSettings.filtersReset')
}

function applyAuditFilters(): void {
  if (!isValidAuditDateRange(auditFilters)) {
    actionSuccess.value = null
    actionError.value = t('adminSettings.auditDateRangeError')
    return
  }
  actionError.value = null
  appliedAuditFilters.value = { ...auditFilters }
  currentPage.value = 1
  void auditResource.reload()
}

function resetAuditFilters(): void {
  Object.assign(auditFilters, { from: '', to: '', actionType: '', actor: '' })
  applyAuditFilters()
}

function auditActionLabel(action: string): string {
  return statusLabel('auditAction', action)
}

function openAnalytics(): void {
  void router.push('/admin/analytics')
}

function goToPreviousPage(): void {
  if (currentPage.value <= 1) return
  currentPage.value -= 1
  void auditResource.reload()
}

function goToNextPage(): void {
  if (currentPage.value >= totalPages.value) return
  currentPage.value += 1
  void auditResource.reload()
}

function reloadAll(): void {
  void auditResource.reload()
  void analyticsResource.reload()
  void notificationsResource.reload()
  void platformSettingsResource.reload()
  void healthResource.reload()
}
</script>

<style scoped>
.admin-settings-head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
}

.admin-settings-hint-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}

.admin-settings-count {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.admin-settings-switch-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  cursor: pointer;
}

.admin-settings-switch-row .switch {
  margin-top: var(--space-0-5);
}

.admin-settings-notes {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding-left: var(--space-5);
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.admin-settings-audit-table td:first-child {
  min-width: 260px;
}

@media (max-width: 640px) {
  .admin-settings-audit-table td:first-child {
    min-width: 0;
  }
}
</style>
