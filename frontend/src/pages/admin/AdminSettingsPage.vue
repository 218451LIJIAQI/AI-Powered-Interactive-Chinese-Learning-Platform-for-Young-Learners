<template>
  <div class="page-stack admin-settings-page">
    <PageHeaderBlock :title="t('adminSettings.title')" :description="t('adminSettings.description')" icon="settings" />

    <DataNotice :using-fallback="usingFallback" :error="actionError || pageError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-settings-layout">
        <div class="admin-settings-left">
          <article class="panel admin-settings-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-purple">
                <IconGlyph name="language" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.interfaceLanguage') }}</strong>
                <span>{{ t('adminSettings.interfaceLanguageHint') }}</span>
              </div>
            </div>

            <div class="admin-settings-form-row">
              <select
                v-model="ui.locale"
                class="admin-settings-select"
                :aria-label="t('adminSettings.languageAria')"
                :disabled="platformSettingsReadOnly"
                @change="ui.setLocale(ui.locale)"
              >
                <option value="en">English (EN)</option>
                <option value="ms">Bahasa Melayu (BM)</option>
                <option value="zh">简体中文</option>
              </select>

              <button
                class="btn btn-primary admin-settings-save"
                type="button"
                :disabled="platformSettingsReadOnly"
                @click="saveLocale"
              >
                {{ t('adminSettings.save') }}
              </button>
            </div>
          </article>

          <article class="panel admin-settings-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-blue">
                <IconGlyph name="safe" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.disclaimer') }}</strong>
                <span>{{ t('adminSettings.disclaimerHint') }}</span>
              </div>
            </div>

            <div class="admin-settings-form-row admin-settings-form-row--stacked">
              <div class="admin-settings-textarea-wrap">
                <textarea
                  v-model="disclaimerText"
                  class="admin-settings-textarea"
                  :aria-label="t('adminSettings.disclaimerAria')"
                  maxlength="200"
                  :disabled="platformSettingsFormDisabled"
                />
                <small>{{ disclaimerText.length }} / 200</small>
              </div>
              <button
                class="btn btn-primary admin-settings-save"
                type="button"
                :disabled="platformSettingsFormDisabled"
                @click="saveDisclaimer"
              >
                {{ platformSettingsSaveLabel }}
              </button>
            </div>

            <small class="admin-settings-note-inline">
              {{ t('adminSettings.disclaimerStored') }}
            </small>
          </article>

          <article class="panel admin-settings-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-gold">
                <IconGlyph name="bell" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.notifications') }}</strong>
                <span>{{ t('adminSettings.notificationsHint') }}</span>
              </div>
            </div>

            <div class="admin-settings-switch-list">
              <article v-for="item in notificationOptions" :key="item.key" class="admin-settings-switch-row">
                <div class="stack u-gap-3">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.description }}</span>
                </div>
                <button
                  class="admin-settings-switch"
                  :class="{ 'is-on': localSettings[item.key] }"
                  type="button"
                  :disabled="platformSettingsFormDisabled"
                  :aria-label="`${item.title}: ${localSettings[item.key] ? t('adminSettings.on') : t('adminSettings.off')}`"
                  :aria-pressed="localSettings[item.key]"
                  @click="toggleSetting(item.key)"
                >
                  <span />
                </button>
              </article>
            </div>

            <div class="admin-settings-actions">
              <button
                class="btn btn-primary admin-settings-save"
                type="button"
                :disabled="platformSettingsFormDisabled"
                @click="saveLocalPreferences"
              >
                {{ platformSettingsSaveSettingsLabel }}
              </button>
            </div>
          </article>

          <article class="panel admin-settings-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-green">
                <IconGlyph name="shield" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.safety') }}</strong>
                <span>{{ t('adminSettings.safetyHint') }}</span>
              </div>
            </div>

            <div class="admin-settings-switch-list">
              <article v-for="item in safetyOptions" :key="item.key" class="admin-settings-switch-row">
                <div class="stack u-gap-3">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.description }}</span>
                </div>
                <button
                  class="admin-settings-switch"
                  :class="{ 'is-on': localSettings[item.key] }"
                  type="button"
                  :disabled="platformSettingsFormDisabled"
                  :aria-label="`${item.title}: ${localSettings[item.key] ? t('adminSettings.on') : t('adminSettings.off')}`"
                  :aria-pressed="localSettings[item.key]"
                  @click="toggleSetting(item.key)"
                >
                  <span />
                </button>
              </article>
            </div>

            <div class="admin-settings-actions">
              <button
                class="btn btn-primary admin-settings-save"
                type="button"
                :disabled="platformSettingsFormDisabled"
                @click="saveLocalPreferences"
              >
                {{ platformSettingsSaveSettingsLabel }}
              </button>
            </div>
          </article>

          <article class="panel admin-settings-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-gray">
                <IconGlyph name="settings" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.basic') }}</strong>
                <span>{{ t('adminSettings.basicHint') }}</span>
              </div>
            </div>

            <div class="admin-settings-basic-list">
              <article class="admin-settings-basic-row">
                <span>{{ t('adminSettings.platformName') }}</span>
                <input
                  class="admin-settings-input"
                  :value="appName"
                  :aria-label="t('adminSettings.platformName')"
                  readonly
                />
              </article>
              <article class="admin-settings-basic-row">
                <span>{{ t('adminSettings.supportEmail') }}</span>
                <input
                  class="admin-settings-input"
                  :value="t('adminSettings.backendConfigured')"
                  :aria-label="t('adminSettings.supportEmail')"
                  readonly
                />
              </article>
              <article class="admin-settings-basic-row">
                <span>{{ t('adminSettings.commissionRate') }}</span>
                <div class="admin-settings-commission">
                  <input
                    v-model.number="commissionRate"
                    class="admin-settings-input"
                    type="number"
                    min="0"
                    max="1"
                    step="0.01"
                    :aria-label="t('adminSettings.commissionRate')"
                    :disabled="commissionFormDisabled"
                  />
                  <button
                    class="btn btn-primary admin-settings-save"
                    type="button"
                    :disabled="commissionReadOnly || savingCommission"
                    @click="saveCommission"
                  >
                    {{ commissionSaveLabel }}
                  </button>
                </div>
              </article>
            </div>

            <small class="admin-settings-note-inline">
              {{ t('adminSettings.basicNote') }}
            </small>
          </article>
        </div>

        <article class="panel admin-settings-center">
          <div class="admin-settings-center-head">
            <div class="student-section-heading">
              <strong>{{ t('adminSettings.auditLogs') }}</strong>
            </div>
            <span>{{ t('adminSettings.auditHint') }}</span>
          </div>

          <div class="admin-settings-filters">
            <button class="teacher-outline-button admin-settings-filter" type="button" @click="cycleTypeFilter">
              {{ typeFilterLabel }}
            </button>
            <button class="teacher-outline-button admin-settings-filter" type="button" @click="cycleActorFilter">
              {{ actorFilterLabel }}
            </button>
            <div class="teacher-outline-button admin-settings-filter is-icon" role="status" aria-live="polite">
              <IconGlyph name="calendar" :size="16" />
              {{ selectedDateLabel }}
            </div>
            <button class="teacher-outline-button admin-settings-filter is-icon" type="button" @click="cycleSort">
              <IconGlyph name="filter" :size="16" />
              {{ sortLabel }}
            </button>
          </div>

          <div class="admin-settings-audit-list">
            <article v-for="item in filteredAuditLogs" :key="item.id" class="admin-settings-audit-item">
              <div class="admin-settings-audit-main">
                <span class="admin-settings-audit-icon" :class="`is-${item.tone}`"
                  ><IconGlyph :name="item.icon" :size="18"
                /></span>
                <div class="admin-settings-audit-copy">
                  <div class="row u-gap-10 u-wrap">
                    <strong>{{ item.title }}</strong>
                    <span class="admin-settings-tag" :class="`is-${item.tone}`">{{ item.tag }}</span>
                  </div>
                  <span>{{ item.description }}</span>
                </div>
              </div>

              <div class="admin-settings-audit-meta">
                <strong>{{ item.actor }}</strong>
                <span>{{ item.time }}</span>
                <small>{{ item.trace }}</small>
              </div>
            </article>
          </div>

          <div class="admin-settings-pagination">
            <div class="row admin-settings-pagination-left">
              <span>{{ t('adminSettings.rowsPerPage') }}</span>
              <span
                class="teacher-outline-button admin-settings-page-size"
                :aria-label="t('adminSettings.fixedRowsAria')"
                >10</span
              >
            </div>

            <span>{{ paginationLabel }}</span>

            <div class="row admin-settings-pagination-right">
              <button
                class="admin-settings-page-button"
                type="button"
                :aria-label="t('adminSettings.previousPage')"
                :disabled="currentPage === 1"
                @click="goToPreviousPage"
              >
                <IconGlyph name="chevronLeft" :size="16" />
              </button>

              <button
                v-for="page in totalPages"
                :key="page"
                class="admin-settings-page-button"
                :class="{ 'is-active': currentPage === page }"
                type="button"
                @click="goToPage(page)"
              >
                {{ page }}
              </button>

              <button
                class="admin-settings-page-button"
                type="button"
                :aria-label="t('adminSettings.nextPage')"
                :disabled="currentPage === totalPages"
                @click="goToNextPage"
              >
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>
          </div>
        </article>

        <div class="admin-settings-right">
          <article class="panel admin-settings-side-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-sky">
                <IconGlyph name="monitor" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.systemInfo') }}</strong>
              </div>
            </div>

            <div class="admin-settings-info-list">
              <div>
                <span>{{ t('adminSettings.platformVersion') }}</span
                ><strong>{{ t('adminSettings.repositoryBuild') }}</strong>
              </div>
              <div>
                <span>{{ t('adminSettings.environment') }}</span
                ><strong>{{ environmentLabel }}</strong>
              </div>
              <div>
                <span>{{ t('adminSettings.lastHealth') }}</span
                ><strong>{{ lastHealthCheckLabel }}</strong>
              </div>
              <div>
                <span>{{ t('adminSettings.service') }}</span
                ><strong>{{ serviceLabel }}</strong>
              </div>
              <div>
                <span>{{ t('adminSettings.registeredStudents') }}</span
                ><strong>{{ formatNumber(registeredStudents) }}</strong>
              </div>
              <div>
                <span>{{ t('adminSettings.databaseStatus') }}</span>
                <strong class="admin-settings-healthy"><i /> {{ databaseStatusLabel }}</strong>
              </div>
            </div>

            <button class="teacher-outline-button admin-settings-health-button" type="button" @click="openAnalytics">
              {{ t('adminSettings.viewAnalytics') }}
            </button>
          </article>

          <article class="panel admin-settings-side-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-gold">
                <IconGlyph name="flash" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.quickActions') }}</strong>
              </div>
            </div>

            <div class="admin-settings-quick-list">
              <button
                v-for="item in quickActions"
                :key="item.label"
                class="admin-settings-quick-item"
                type="button"
                @click="item.action"
              >
                <span class="row u-gap-10">
                  <IconGlyph :name="item.icon" :size="18" />
                  <span>{{ item.label }}</span>
                </span>
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>
          </article>

          <article class="panel admin-settings-side-card">
            <div class="row-between admin-settings-card-head">
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.recentNotifications') }}</strong>
                <span>{{ t('adminSettings.notificationsOpenHint') }}</span>
              </div>
              <button
                class="teacher-outline-button admin-settings-health-button"
                type="button"
                :disabled="markingNotificationsRead || !hasUnreadNotifications"
                @click="markAllAdminNotificationsRead"
              >
                {{ markAllNotificationsLabel }}
              </button>
            </div>

            <div v-if="adminNotifications.length" class="admin-settings-notification-list">
              <NotificationLinkButton
                v-for="item in adminNotifications"
                :key="item.id"
                :notification-id="item.id"
                :target-path="item.targetPath"
                fallback-to="/admin/settings"
                @read="markAdminNotificationRead"
              >
                <article class="admin-settings-notification-item">
                  <span class="admin-settings-notification-dot" :class="{ 'is-unread': item.status !== 'READ' }" />
                  <div class="admin-settings-notification-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.message }}</span>
                    <small>{{ formatDateTime(item.createdAt) }}</small>
                  </div>
                </article>
              </NotificationLinkButton>
            </div>

            <div v-else class="stack u-gap-6">
              <strong>{{ t('adminSettings.noNotifications') }}</strong>
              <span class="muted">{{ t('adminSettings.noNotificationsHint') }}</span>
            </div>
          </article>

          <article class="panel admin-settings-side-card admin-settings-note-card">
            <div class="admin-settings-card-head">
              <div class="admin-settings-card-icon is-purple">
                <IconGlyph name="help" :size="22" />
              </div>
              <div class="stack u-gap-4">
                <strong>{{ t('adminSettings.note') }}</strong>
              </div>
            </div>

            <div class="stack u-gap-10">
              <span>{{ t('adminSettings.retentionNote') }}</span>
              <span>{{ t('adminSettings.timezoneNote') }}</span>
              <span>{{ t('adminSettings.endpointNote') }}</span>
            </div>
          </article>
        </div>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { adminApi, notificationsApi, systemApi } from '@/api/services'
import { appEnv } from '@/config/env'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminAnalytics, adminAuditLogs, commissionSetting } from '@/demo/data'
import { adminSettingsMessages } from '@/plugins/adminSettingsMessages'
import { useUiStore } from '@/stores/ui'
import { resolveApiError } from '@/utils/apiError'
import { clampNumber, formatDate, formatDateTime, formatNumber } from '@/utils/format'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { adminSettings: adminSettingsMessages.en },
    ms: { adminSettings: adminSettingsMessages.ms },
    zh: { adminSettings: adminSettingsMessages.zh },
  },
})
const ui = useUiStore()

type AuditTypeFilter = 'ALL' | 'SECURITY' | 'ADMIN_ACTION' | 'CONTENT_CHANGE' | 'MODERATION'
type AuditActorFilter = 'ALL' | 'ADMINS' | 'SYSTEM'
type SortOrder = 'NEWEST' | 'OLDEST'
type LocalSettingKey =
  | 'emailNotifications'
  | 'inAppNotifications'
  | 'dailySummary'
  | 'strictContentFiltering'
  | 'sensitiveWordFilter'
  | 'aiSafetyCheck'

const platformSettingsFallback = {
  platformName: appEnv.appName,
  aiDisclaimerText: t('adminSettings.defaultDisclaimer'),
  emailNotifications: true,
  inAppNotifications: true,
  dailySummary: false,
  strictContentFiltering: true,
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
}
const healthFallback = {
  status: 'UP',
  service: 'backend',
  timestamp: new Date().toISOString(),
}

const auditResource = useResource(() => adminApi.auditLogs(), adminAuditLogs)
const analyticsResource = useResource(() => adminApi.analytics(), adminAnalytics)
const commissionResource = useResource(() => adminApi.commissionSetting(), commissionSetting)
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
    totalRevenue: 0,
    pendingRefunds: 0,
    pendingPayouts: 0,
    moderationActions: 0,
    auditLogEntries: 0,
    adminUnreadNotifications: 0,
    aiRequests: 0,
  },
  userRegistrationTrend: [],
  teacherApprovalBreakdown: [],
  contentStatusBreakdown: [],
  paymentStatusBreakdown: [],
  aiStatusBreakdown: [],
}

const auditLogs = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? adminAuditLogs : []))
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
    commissionResource.loading.value ||
    notificationsResource.loading.value ||
    platformSettingsResource.loading.value ||
    healthResource.loading.value,
)
const pageError = computed(
  () =>
    auditResource.error.value ||
    analyticsResource.error.value ||
    commissionResource.error.value ||
    notificationsResource.error.value ||
    platformSettingsResource.error.value ||
    healthResource.error.value,
)
const usingFallback = computed(
  () =>
    auditResource.usingFallback.value ||
    analyticsResource.usingFallback.value ||
    commissionResource.usingFallback.value ||
    notificationsResource.usingFallback.value ||
    platformSettingsResource.usingFallback.value ||
    healthResource.usingFallback.value,
)

const disclaimerText = ref(platformSettingsFallback.aiDisclaimerText)
const commissionRate = ref(0)
const commissionReadOnly = computed(() => commissionResource.usingFallback.value)
const platformSettingsReadOnly = computed(() => platformSettingsResource.usingFallback.value)
const platformSettingsFormDisabled = computed(() => platformSettingsReadOnly.value || savingPlatformSettings.value)
const commissionFormDisabled = computed(() => commissionReadOnly.value || savingCommission.value)
const savingCommission = ref(false)
const savingPlatformSettings = ref(false)
const markingNotificationsRead = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

watch(
  () => commissionResource.data.value,
  (value) => {
    if (value) {
      commissionRate.value = clampCommissionRate(value.commissionRate)
    }
  },
  { immediate: true },
)

const localSettings = ref<Record<LocalSettingKey, boolean>>({
  emailNotifications: true,
  inAppNotifications: true,
  dailySummary: false,
  strictContentFiltering: true,
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
})

watch(
  platformSettings,
  (value) => {
    disclaimerText.value = value.aiDisclaimerText
    localSettings.value = {
      emailNotifications: value.emailNotifications,
      inAppNotifications: value.inAppNotifications,
      dailySummary: value.dailySummary,
      strictContentFiltering: value.strictContentFiltering,
      sensitiveWordFilter: value.sensitiveWordFilter,
      aiSafetyCheck: value.aiSafetyCheck,
    }
  },
  { immediate: true },
)

const notificationOptions = computed<Array<{ key: LocalSettingKey; title: string; description: string }>>(() => [
  {
    key: 'emailNotifications',
    title: t('adminSettings.emailNotifications'),
    description: t('adminSettings.emailNotificationsHint'),
  },
  {
    key: 'inAppNotifications',
    title: t('adminSettings.inAppNotifications'),
    description: t('adminSettings.inAppNotificationsHint'),
  },
  { key: 'dailySummary', title: t('adminSettings.dailySummary'), description: t('adminSettings.dailySummaryHint') },
])

const safetyOptions = computed<Array<{ key: LocalSettingKey; title: string; description: string }>>(() => [
  {
    key: 'strictContentFiltering',
    title: t('adminSettings.strictFiltering'),
    description: t('adminSettings.strictFilteringHint'),
  },
  {
    key: 'sensitiveWordFilter',
    title: t('adminSettings.profanityFilter'),
    description: t('adminSettings.profanityFilterHint'),
  },
  {
    key: 'aiSafetyCheck',
    title: t('adminSettings.aiSafety'),
    description: t('adminSettings.aiSafetyHint'),
  },
])

const typeFilter = ref<AuditTypeFilter>('ALL')
const actorFilter = ref<AuditActorFilter>('ALL')
const sortOrder = ref<SortOrder>('NEWEST')
const currentPage = ref(1)
const pageSize = 10
const selectedDateLabel = computed(() => {
  const timestamps = auditLogs.value
    .map((item) => new Date(item.createdAt).getTime())
    .filter((value) => !Number.isNaN(value))
  if (!timestamps.length) return t('adminSettings.allDates')
  const latest = new Date(Math.max(...timestamps))
  return formatDate(latest.toISOString())
})

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

    const tone =
      type === 'SECURITY' ? 'orange' : type === 'ADMIN_ACTION' ? 'purple' : type === 'CONTENT_CHANGE' ? 'blue' : 'green'

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

    const actorKind: 'SYSTEM' | 'ADMIN' = item.actorEmail ? 'ADMIN' : 'SYSTEM'

    return {
      id: item.id,
      title: auditActionLabel(action),
      description: item.description,
      actor: item.actorEmail || t('adminSettings.system'),
      actorKind,
      time: formatDateTime(item.createdAt),
      trace: item.requestId
        ? t('adminSettings.requestTrace', { id: item.requestId })
        : item.ipAddress
          ? t('adminSettings.ipTrace', { address: item.ipAddress })
          : t('adminSettings.noTrace'),
      type,
      tone,
      icon,
      tag,
      createdAt: item.createdAt,
    }
  }),
)

const filteredAuditLogs = computed(() => {
  const result = normalizedAuditLogs.value.filter(auditMatchesActiveFilters)

  result.sort((a, b) => {
    if (sortOrder.value === 'NEWEST') {
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    }
    return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
  })

  const start = (currentPage.value - 1) * pageSize
  return result.slice(start, start + pageSize)
})

const totalAuditCount = computed(() => normalizedAuditLogs.value.filter(auditMatchesActiveFilters).length)

const totalPages = computed(() => Math.max(1, Math.ceil(totalAuditCount.value / pageSize)))
const paginationLabel = computed(() => {
  const start = totalAuditCount.value ? (currentPage.value - 1) * pageSize + 1 : 0
  const end = Math.min(totalAuditCount.value, currentPage.value * pageSize)
  return t('adminSettings.pagination', { start, end, total: totalAuditCount.value })
})

watch([typeFilter, actorFilter], () => {
  currentPage.value = 1
})

const typeFilterLabel = computed(() => {
  if (typeFilter.value === 'ALL') return t('adminSettings.allTypes')
  if (typeFilter.value === 'ADMIN_ACTION') return t('adminSettings.adminAction')
  if (typeFilter.value === 'CONTENT_CHANGE') return t('adminSettings.contentChange')
  if (typeFilter.value === 'SECURITY') return t('adminSettings.security')
  return t('adminSettings.moderation')
})

const actorFilterLabel = computed(() => {
  if (actorFilter.value === 'ALL') return t('adminSettings.allUsers')
  return actorFilter.value === 'ADMINS' ? t('adminSettings.admins') : t('adminSettings.system')
})

const sortLabel = computed(() =>
  sortOrder.value === 'NEWEST' ? t('adminSettings.newestFirst') : t('adminSettings.oldestFirst'),
)
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
const commissionSaveLabel = computed(() =>
  savingCommission.value ? t('adminSettings.saving') : t('adminSettings.save'),
)
const markAllNotificationsLabel = computed(() =>
  markingNotificationsRead.value ? t('adminSettings.marking') : t('adminSettings.markAllRead'),
)

const quickActions = computed(() => [
  { label: t('adminSettings.savePlatformSettings'), icon: 'refresh', action: () => saveLocalPreferences() },
  { label: t('adminSettings.viewAnalytics'), icon: 'download', action: () => openAnalytics() },
  { label: t('adminSettings.reviewAuditLogs'), icon: 'history', action: () => reviewAuditLogs() },
  { label: t('adminSettings.openFinance'), icon: 'database', action: () => openFinance() },
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
    window.dispatchEvent(new CustomEvent('mandarin-spark:notification-read', { detail: { all: true } }))
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
      emailNotifications: localSettings.value.emailNotifications,
      inAppNotifications: localSettings.value.inAppNotifications,
      dailySummary: localSettings.value.dailySummary,
      strictContentFiltering: localSettings.value.strictContentFiltering,
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
  typeFilter.value = 'ALL'
  actorFilter.value = 'ALL'
  sortOrder.value = 'NEWEST'
  currentPage.value = 1
  actionError.value = null
  actionSuccess.value = t('adminSettings.filtersReset')
}

async function saveCommission(): Promise<void> {
  if (savingCommission.value) return
  if (commissionReadOnly.value) {
    actionError.value = t('adminSettings.previewCommission')
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  savingCommission.value = true
  try {
    const normalizedRate = clampCommissionRate(commissionRate.value)
    commissionRate.value = normalizedRate
    await adminApi.updateCommissionSetting({ commissionRate: normalizedRate })
    await commissionResource.reload()
    actionSuccess.value = t('adminSettings.commissionSaved')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminSettings.commissionError'))
  } finally {
    savingCommission.value = false
  }
}

function cycleTypeFilter(): void {
  const order: AuditTypeFilter[] = ['ALL', 'SECURITY', 'ADMIN_ACTION', 'CONTENT_CHANGE', 'MODERATION']
  typeFilter.value = order[(order.indexOf(typeFilter.value) + 1) % order.length]
}

function cycleActorFilter(): void {
  const order: AuditActorFilter[] = ['ALL', 'ADMINS', 'SYSTEM']
  actorFilter.value = order[(order.indexOf(actorFilter.value) + 1) % order.length]
}

function cycleSort(): void {
  sortOrder.value = sortOrder.value === 'NEWEST' ? 'OLDEST' : 'NEWEST'
}

function auditMatchesActiveFilters(item: { type: string; actorKind: 'SYSTEM' | 'ADMIN' }): boolean {
  if (typeFilter.value !== 'ALL' && item.type !== typeFilter.value) return false
  if (actorFilter.value === 'ADMINS' && item.actorKind !== 'ADMIN') return false
  if (actorFilter.value === 'SYSTEM' && item.actorKind !== 'SYSTEM') return false
  return true
}

function auditActionLabel(action: string): string {
  const labels: Record<string, string> = {
    ADMIN_LOGIN: t('adminSettings.actionAdminLogin'),
    TEACHER_APPROVED: t('adminSettings.actionTeacherApproved'),
    TEACHER_REJECTED: t('adminSettings.actionTeacherRejected'),
    CONTENT_ARCHIVED: t('adminSettings.actionContentArchived'),
    CONTENT_UPDATED: t('adminSettings.actionContentUpdated'),
  }
  return labels[action] ?? action.replaceAll('_', ' ')
}

function clampCommissionRate(value: number): number {
  return clampNumber(value, 0, 1)
}

function openAnalytics(): void {
  void router.push('/admin/analytics')
}

function openFinance(): void {
  void router.push('/admin/finance')
}

function goToPreviousPage(): void {
  currentPage.value = Math.max(1, currentPage.value - 1)
}

function goToPage(page: number): void {
  currentPage.value = page
}

function goToNextPage(): void {
  currentPage.value = Math.min(totalPages.value, currentPage.value + 1)
}

function reloadAll(): void {
  void auditResource.reload()
  void analyticsResource.reload()
  void commissionResource.reload()
  void notificationsResource.reload()
  void platformSettingsResource.reload()
  void healthResource.reload()
}
</script>

<style scoped>
.admin-settings-page {
  position: relative;
}

.admin-settings-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.08fr) 320px;
  gap: 18px;
  align-items: start;
}

.admin-settings-left,
.admin-settings-right {
  display: grid;
  gap: 18px;
}

.admin-settings-card,
.admin-settings-center,
.admin-settings-side-card {
  border-radius: 28px;
}

.admin-settings-card,
.admin-settings-side-card {
  display: grid;
  gap: 18px;
}

.admin-settings-card-head,
.admin-settings-center-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.admin-settings-center-head {
  flex-direction: column;
  gap: 4px;
}

.admin-settings-card-head span,
.admin-settings-center-head span,
.admin-settings-switch-row span,
.admin-settings-note-inline,
.admin-settings-audit-copy span,
.admin-settings-audit-meta span,
.admin-settings-audit-meta small,
.admin-settings-info-list span,
.admin-settings-note-card span {
  color: #72809d;
}

.admin-settings-card-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 16px;
}

.admin-settings-card-icon.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-settings-card-icon.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-settings-card-icon.is-gold {
  background: rgba(255, 181, 68, 0.14);
  color: #f29d16;
}

.admin-settings-card-icon.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #49a347;
}

.admin-settings-card-icon.is-gray {
  background: rgba(211, 217, 235, 0.36);
  color: #6f7c99;
}

.admin-settings-card-icon.is-sky {
  background: rgba(63, 144, 255, 0.12);
  color: #3990ff;
}

.admin-settings-form-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-settings-form-row--stacked {
  align-items: flex-end;
}

.admin-settings-select,
.admin-settings-input,
.admin-settings-textarea {
  width: 100%;
  border: 1px solid rgba(227, 232, 244, 0.96);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.98);
  color: var(--text);
  font: inherit;
}

.admin-settings-select,
.admin-settings-input {
  min-height: 46px;
  padding: 0 14px;
}

.admin-settings-textarea {
  min-height: 92px;
  padding: 14px;
  resize: vertical;
}

.admin-settings-textarea-wrap {
  flex: 1;
  display: grid;
  gap: 8px;
}

.admin-settings-textarea-wrap small {
  justify-self: end;
  color: #7d88a0;
}

.admin-settings-save {
  min-width: 86px;
  min-height: 46px;
  border-radius: 14px;
}

.admin-settings-switch-list,
.admin-settings-basic-list,
.admin-settings-quick-list {
  display: grid;
  gap: 14px;
}

.admin-settings-switch-row,
.admin-settings-basic-row,
.admin-settings-quick-item,
.admin-settings-info-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.admin-settings-switch {
  width: 46px;
  height: 26px;
  border: 0;
  border-radius: 999px;
  padding: 3px;
  background: rgba(210, 216, 231, 0.96);
}

.admin-settings-switch span {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 8px 18px rgba(40, 52, 79, 0.18);
  transform: translateX(0);
  transition: transform 0.2s ease;
}

.admin-settings-switch.is-on {
  background: linear-gradient(135deg, #8a63ff, #6a49ff);
}

.admin-settings-switch.is-on span {
  transform: translateX(20px);
}

.admin-settings-actions {
  display: flex;
  justify-content: flex-end;
}

.admin-settings-basic-row {
  align-items: flex-start;
}

.admin-settings-basic-row > span {
  min-width: 128px;
  color: #6b7894;
  font-weight: 700;
}

.admin-settings-commission {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.admin-settings-center {
  display: grid;
  gap: 18px;
}

.admin-settings-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-settings-filter {
  min-height: 44px;
  border-radius: 14px;
}

.admin-settings-filter.is-icon {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.admin-settings-audit-list {
  display: grid;
  gap: 14px;
}

.admin-settings-audit-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-settings-audit-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.admin-settings-audit-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}

.admin-settings-audit-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  font-size: 1.05rem;
}

.admin-settings-audit-icon.is-orange,
.admin-settings-tag.is-orange {
  background: rgba(255, 181, 68, 0.14);
  color: #eb9317;
}

.admin-settings-audit-icon.is-purple,
.admin-settings-tag.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-settings-audit-icon.is-blue,
.admin-settings-tag.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-settings-audit-icon.is-green,
.admin-settings-tag.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #49a347;
}

.admin-settings-audit-copy {
  display: grid;
  gap: 5px;
}

.admin-settings-audit-meta {
  min-width: 154px;
  display: grid;
  gap: 5px;
  justify-items: end;
  text-align: right;
}

.admin-settings-tag {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
}

.admin-settings-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(236, 239, 247, 0.96);
  color: #75819b;
}

.admin-settings-pagination-left,
.admin-settings-pagination-right {
  align-items: center;
  gap: 8px;
}

.admin-settings-page-size {
  min-width: 56px;
}

.admin-settings-page-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  border: 1px solid rgba(228, 232, 244, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #7d89a4;
  font-weight: 700;
}

.admin-settings-page-button.is-active {
  background: linear-gradient(135deg, #7d58ff, #6d49f8);
  color: white;
  border-color: transparent;
}

.admin-settings-info-list {
  display: grid;
  gap: 14px;
}

.admin-settings-healthy {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4aa746;
}

.admin-settings-healthy i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: currentColor;
}

.admin-settings-health-button {
  min-height: 46px;
  border-radius: 14px;
}

.admin-settings-quick-item {
  min-height: 48px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #334260;
  font: inherit;
}

.admin-settings-notification-list {
  display: grid;
  gap: 4px;
}

.admin-settings-notification-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
  text-align: left;
}

.admin-settings-notification-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.admin-settings-notification-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  flex-shrink: 0;
  background: rgba(148, 163, 184, 0.35);
}

.admin-settings-notification-dot.is-unread {
  background: #7a56ff;
  box-shadow: 0 0 0 6px rgba(122, 86, 255, 0.12);
}

.admin-settings-notification-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.admin-settings-notification-copy span,
.admin-settings-notification-copy small {
  color: #72809d;
  overflow-wrap: anywhere;
}

.admin-settings-note-card {
  min-height: 204px;
}

@media (max-width: 1420px) {
  .admin-settings-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .admin-settings-form-row,
  .admin-settings-audit-item,
  .admin-settings-pagination,
  .admin-settings-commission {
    grid-template-columns: 1fr;
    display: grid;
  }

  .admin-settings-audit-meta {
    min-width: 0;
    justify-items: start;
    text-align: left;
  }
}

@media (min-width: 900px) {
  .admin-settings-layout {
    grid-template-columns: minmax(280px, 0.98fr) minmax(0, 1.22fr) minmax(240px, 0.74fr);
    gap: 18px;
  }

  .admin-settings-left,
  .admin-settings-right {
    gap: 18px;
  }

  .admin-settings-card,
  .admin-settings-center,
  .admin-settings-side-card {
    padding: 18px;
    border-radius: 24px;
  }

  .admin-settings-card,
  .admin-settings-side-card,
  .admin-settings-center {
    gap: 16px;
  }

  .admin-settings-card-head,
  .admin-settings-center-head {
    gap: 14px;
  }

  .admin-settings-card-icon {
    width: 42px;
    height: 42px;
    border-radius: 15px;
  }

  .admin-settings-card-head strong,
  .admin-settings-center-head strong,
  .admin-settings-side-card strong {
    font-size: 0.94rem;
  }

  .admin-settings-card-head span,
  .admin-settings-center-head span,
  .admin-settings-switch-row span,
  .admin-settings-note-inline,
  .admin-settings-audit-copy span,
  .admin-settings-audit-meta span,
  .admin-settings-audit-meta small,
  .admin-settings-info-list span,
  .admin-settings-note-card span {
    font-size: 0.8rem;
  }

  .admin-settings-form-row {
    gap: 14px;
  }

  .admin-settings-select,
  .admin-settings-input {
    min-height: 44px;
    padding: 0 14px;
    border-radius: 14px;
    font-size: 0.86rem;
  }

  .admin-settings-textarea {
    min-height: 96px;
    padding: 14px;
    border-radius: 14px;
    font-size: 0.86rem;
  }

  .admin-settings-save,
  .admin-settings-health-button,
  .admin-settings-filter {
    min-height: 42px;
    border-radius: 14px;
    font-size: 0.82rem;
  }

  .admin-settings-switch-list,
  .admin-settings-basic-list,
  .admin-settings-quick-list,
  .admin-settings-info-list,
  .admin-settings-notification-list,
  .admin-settings-audit-list {
    gap: 12px;
  }

  .admin-settings-switch-row,
  .admin-settings-basic-row,
  .admin-settings-quick-item,
  .admin-settings-info-list div {
    gap: 12px;
    font-size: 0.84rem;
  }

  .admin-settings-switch {
    width: 42px;
    height: 24px;
    padding: 2px;
  }

  .admin-settings-switch span {
    width: 20px;
    height: 20px;
  }

  .admin-settings-switch.is-on span {
    transform: translateX(18px);
  }

  .admin-settings-filters {
    gap: 10px;
  }

  .admin-settings-audit-item {
    gap: 14px;
    padding-bottom: 14px;
  }

  .admin-settings-audit-main {
    gap: 12px;
  }

  .admin-settings-audit-icon {
    width: 38px;
    height: 38px;
    border-radius: 14px;
    font-size: 1.05rem;
  }

  .admin-settings-audit-copy strong,
  .admin-settings-audit-meta {
    font-size: 0.84rem;
  }

  .admin-settings-audit-meta {
    min-width: 148px;
    gap: 6px;
  }

  .admin-settings-tag {
    padding: 5px 10px;
    font-size: 0.7rem;
  }

  .admin-settings-pagination {
    gap: 14px;
    padding-top: 12px;
    font-size: 0.82rem;
  }

  .admin-settings-page-button {
    width: 34px;
    height: 34px;
    border-radius: 12px;
    font-size: 0.82rem;
  }

  .admin-settings-quick-item {
    min-height: 44px;
  }

  .admin-settings-note-card {
    min-height: 204px;
  }
}
</style>
