<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminUsers.applicationsPageTitle')" hide-title />

    <AdminUsersTabs :applications="applications.data.value?.length ?? null" />

    <DataNotice :error="actionError" :error-title="t('adminUsers.noticeFailed')" />

    <StatePanel
      v-if="applications.loading.value"
      tone="loading"
      spacious
      :title="t('adminUsers.applicationsLoading')"
      :message="t('adminUsers.applicationsLoadingHint')"
    />
    <StatePanel
      v-else-if="applications.error.value && !waiting.length"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminUsers.applicationsError')"
      :message="applications.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="applications.reload">
          {{ t('adminUsers.retry') }}
        </button>
      </template>
    </StatePanel>
    <div v-else-if="!waiting.length && !selectedId" class="panel">
      <StatePanel
        tone="empty"
        icon="check"
        spacious
        :title="t('adminUsers.applicationsEmptyTitle')"
        :message="t('adminUsers.applicationsEmptyHint')"
      />
    </div>
    <div v-else class="list-detail admin-list-detail">
      <section class="panel admin-list" aria-labelledby="admin-applications-waiting">
        <header class="admin-list-head">
          <h2 id="admin-applications-waiting" class="card-title">{{ t('adminUsers.waitingTitle') }}</h2>
          <p class="admin-meta">{{ t('adminUsers.oldestFirst') }}</p>
        </header>
        <ul v-if="waiting.length" class="admin-select-list" :aria-label="t('adminUsers.applicationsListLabel')">
          <li v-for="item in waiting" :key="item.teacherId">
            <button
              class="select-card admin-select-person"
              type="button"
              :aria-current="item.teacherId === selectedId ? 'true' : undefined"
              data-testid="admin-applications-item"
              @click="selectApplication(item.teacherId)"
            >
              <PortraitAvatar :size="36" :name="item.fullName" />
              <span class="admin-select-copy">
                <strong class="admin-select-title">{{ item.fullName }}</strong>
                <span class="admin-meta">{{ item.email }}</span>
                <StatusPill :label="t(verificationStateKey('IN_REVIEW'))" :tone="adminVerificationTone('IN_REVIEW')" />
                <span class="admin-meta">
                  {{ t('adminUsers.submittedOn', { date: formatDateTime(item.submittedAt) }) }}
                </span>
              </span>
            </button>
          </li>
        </ul>
        <p v-else class="admin-meta">{{ t('adminUsers.applicationsEmptyTitle') }}</p>
      </section>

      <div ref="detailRef" class="admin-detail-slot" tabindex="-1">
        <StatePanel
          v-if="application.loading.value || !selectedId"
          tone="loading"
          spacious
          :title="t('adminUsers.applicationLoading')"
          :message="t('adminUsers.applicationsLoadingHint')"
        />
        <StatePanel
          v-else-if="application.error.value || !current"
          tone="error"
          icon="alert"
          spacious
          :title="t('adminUsers.applicationError')"
          :message="application.error.value ?? ''"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="application.reload">
              {{ t('adminUsers.retry') }}
            </button>
          </template>
        </StatePanel>
        <section
          v-else
          class="panel admin-detail"
          aria-labelledby="admin-applications-name"
          data-testid="admin-applications-detail"
        >
          <header class="admin-detail-head">
            <div class="admin-person">
              <PortraitAvatar :size="56" :name="current.fullName" />
              <div class="admin-detail-heading">
                <h2 id="admin-applications-name" class="admin-detail-title">{{ current.fullName }}</h2>
                <p class="admin-detail-sub">{{ current.email }}</p>
                <p v-if="current.submittedAt" class="admin-meta">
                  {{ t('adminUsers.submittedOn', { date: formatDateTime(current.submittedAt) }) }}
                </p>
              </div>
            </div>
            <div class="admin-detail-side">
              <StatusPill
                :label="t(verificationStateKey(current.verificationState))"
                :tone="adminVerificationTone(current.verificationState)"
              />
              <RouterLink
                class="btn btn-ghost btn-sm admin-link-action"
                :to="{ name: 'admin-users', query: { user: current.userId } }"
              >
                {{ t('adminUsers.openAccount') }}
                <IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </div>
          </header>

          <section class="admin-section" aria-labelledby="admin-applications-bio">
            <h3 id="admin-applications-bio" class="admin-section-title">{{ t('adminUsers.bio') }}</h3>
            <p class="admin-sunken admin-applications-bio">{{ current.bio || t('adminUsers.noBio') }}</p>
          </section>

          <section class="admin-section" aria-labelledby="admin-applications-documents">
            <div class="admin-section-head">
              <h3 id="admin-applications-documents" class="admin-section-title">{{ t('adminUsers.documents') }}</h3>
              <span class="admin-meta">
                {{ t('adminUsers.filesCount', { count: current.documents.length }, current.documents.length) }}
              </span>
            </div>
            <ul v-if="current.documents.length" class="row-list">
              <li v-for="document in current.documents" :key="document.id" data-testid="admin-applications-document">
                <span class="admin-file">
                  <span class="admin-file-mark" aria-hidden="true"><IconGlyph name="file" :size="16" /></span>
                  <span class="row-list-copy">
                    <span class="row-list-title">{{ t(documentTypeKey(document.documentType)) }}</span>
                    <span class="row-list-meta">
                      {{ document.fileName }} · {{ formatFileSize(document.fileSize) }}
                    </span>
                  </span>
                </span>
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :aria-label="t('adminUsers.openFileLabel', { name: document.fileName })"
                  :disabled="openingFile === document.id"
                  :aria-busy="openingFile === document.id"
                  @click="openDocument(document.id)"
                >
                  <IconGlyph name="eye" :size="16" />
                  <span>{{ t('adminUsers.openFile') }}</span>
                </button>
              </li>
            </ul>
            <p v-else class="admin-meta">{{ t('adminUsers.noDocuments') }}</p>
            <p class="admin-meta">{{ t('adminUsers.documentsNote') }}</p>
          </section>

          <section class="admin-section" aria-labelledby="admin-applications-history">
            <h3 id="admin-applications-history" class="admin-section-title">{{ t('adminUsers.history') }}</h3>
            <AdminEventList :events="historyRows" />
          </section>

          <footer v-if="current.verificationState === 'IN_REVIEW'" class="card-footer">
            <button
              class="btn btn-secondary"
              type="button"
              data-testid="admin-applications-request-changes"
              @click="openDecision('changes')"
            >
              <IconGlyph name="send" :size="16" />
              <span>{{ t('adminUsers.requestChanges') }}</span>
            </button>
            <button
              class="btn btn-primary"
              type="button"
              data-testid="admin-applications-approve"
              @click="openDecision('approve')"
            >
              <IconGlyph name="check" :size="16" />
              <span>{{ t('adminUsers.approve') }}</span>
            </button>
          </footer>
          <p v-else class="admin-meta">{{ t('adminUsers.decidedNote') }}</p>
        </section>
      </div>
    </div>

    <AdminDecisionDialog
      :open="decision !== null"
      role="dialog"
      :title="decisionTitle"
      :subtitle="current ? t('adminUsers.applicationSubtitle', { name: current.fullName }) : ''"
      :confirm-label="decision === 'approve' ? t('adminUsers.approve') : t('adminUsers.requestChanges')"
      :confirm-icon="decision === 'approve' ? 'check' : 'send'"
      :busy="saving"
      :error="dialogError"
      test-id="admin-applications-dialog"
      confirm-test-id="admin-applications-confirm"
      @cancel="closeDecision"
      @confirm="confirmDecision"
    >
      <div v-if="current && decision === 'approve'" class="callout">
        <IconGlyph name="check" :size="16" />
        <div class="admin-callout-copy">
          <strong>{{ t('adminUsers.approveCopy', { name: current.fullName }) }}</strong>
          <span class="admin-meta">{{ t('adminUsers.approveNotice', { name: current.fullName }) }}</span>
        </div>
      </div>
      <p v-else-if="current" class="callout">
        <IconGlyph name="send" :size="16" />
        <span>{{ t('adminUsers.changesCopy', { name: current.fullName }) }}</span>
      </p>
      <div v-if="decision === 'changes' && current" class="field">
        <label class="field-label" for="admin-applications-reason">
          {{ t('adminUsers.reasonLabel') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          id="admin-applications-reason"
          v-model="reason"
          class="textarea"
          rows="4"
          maxlength="1000"
          required
          data-autofocus
          :aria-invalid="Boolean(reasonError)"
          :aria-describedby="reasonError ? 'admin-applications-reason-error' : 'admin-applications-reason-hint'"
          data-testid="admin-applications-reason"
        />
        <p id="admin-applications-reason-hint" class="field-hint">
          {{ t('adminUsers.changesReasonHint', { name: current.fullName }) }}
        </p>
        <p v-if="reasonError" id="admin-applications-reason-error" class="field-error" role="alert">
          {{ reasonError }}
        </p>
      </div>
    </AdminDecisionDialog>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { adminUsersApi } from '@/api/ops'
import AdminDecisionDialog from '@/components/admin/AdminDecisionDialog.vue'
import AdminEventList from '@/components/admin/AdminEventList.vue'
import AdminUsersTabs from '@/components/admin/AdminUsersTabs.vue'
import { adminVerificationTone } from '@/components/admin/adminDisplay'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { TeacherApplication, TeacherApplicationEvent } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { openFileDownload } from '@/utils/opsFiles'
import { formatDateTime, formatFileSize } from '@/utils/format'
import { documentTypeKey, verificationStateKey } from '@/utils/opsLabels'
import '@/styles/ops-admin.css'

type Decision = 'approve' | 'changes'

const HISTORY_KEYS: Record<TeacherApplicationEvent, string> = {
  ACCOUNT_CREATED: 'adminUsers.historyAccountCreated',
  APPLICATION_SUBMITTED: 'adminUsers.historySubmitted',
  APPROVED: 'adminUsers.historyApproved',
  CHANGES_REQUESTED: 'adminUsers.historyChangesRequested',
}

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ adminUsers: adminUsersMessages, opsLabels: opsLabelMessages })
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()
const detailRef = ref<HTMLElement | null>(null)

const applications = useResource(() => adminUsersApi.applications())
const waiting = computed(() => applications.data.value ?? [])

const selectedId = computed<number | null>(() => {
  const id = Number(route.query.teacher)
  return Number.isInteger(id) && id > 0 ? id : null
})

const application = useResource<TeacherApplication | null>(
  () => (selectedId.value ? adminUsersApi.application(selectedId.value) : Promise.resolve(null)),
  false,
)
const current = computed(() => application.data.value)

watch(
  selectedId,
  (id, previous) => {
    if (id !== previous) application.data.value = null
    void application.reload()
  },
  { immediate: true },
)

// Without a choice in the URL, the oldest application waiting is shown.
watch(waiting, (list) => {
  if (!selectedId.value && list.length) {
    void router.replace({ query: { ...route.query, teacher: String(list[0].teacherId) } })
  }
})

async function selectApplication(teacherId: number): Promise<void> {
  await router.replace({ query: { ...route.query, teacher: String(teacherId) } })
  await nextTick()
  detailRef.value?.focus({ preventScroll: true })
}

const historyRows = computed(() =>
  (current.value?.history ?? []).map((entry, index) => ({
    key: `${entry.event}-${entry.at}-${index}`,
    title: t(HISTORY_KEYS[entry.event]),
    meta: entry.actorName
      ? t('adminUsers.historyMeta', { actor: entry.actorName, date: formatDateTime(entry.at) })
      : formatDateTime(entry.at),
    note: entry.note,
  })),
)

// ----- documents -----

const openingFile = ref<number | null>(null)

async function openDocument(documentId: number): Promise<void> {
  openingFile.value = documentId
  try {
    await openFileDownload(() => adminUsersApi.document(documentId), 'document')
  } catch (failure) {
    showError(resolveApiError(failure, t('adminUsers.fileFailed')))
  } finally {
    openingFile.value = null
  }
}

// ----- decision -----

const decision = ref<Decision | null>(null)
const reason = ref('')
const reasonError = ref<string | null>(null)
const dialogError = ref<string | null>(null)
const saving = ref(false)

const decisionTitle = computed(() => {
  const name = current.value?.fullName ?? ''
  return decision.value === 'changes' ? t('adminUsers.changesTitle') : t('adminUsers.approveTitle', { name })
})

function openDecision(kind: Decision): void {
  reason.value = ''
  reasonError.value = null
  dialogError.value = null
  decision.value = kind
}

function closeDecision(): void {
  if (!saving.value) decision.value = null
}

async function confirmDecision(): Promise<void> {
  const decided = current.value
  const kind = decision.value
  if (!decided || !kind) return
  const text = reason.value.trim()
  if (kind === 'changes') {
    reasonError.value = text ? null : t('adminUsers.reasonMissing')
    if (!text) return
  }

  saving.value = true
  dialogError.value = null
  clearFeedback()
  try {
    if (kind === 'approve') {
      await adminUsersApi.approve(decided.teacherId)
      showToast(t('adminUsers.approved', { name: decided.fullName }))
    } else {
      await adminUsersApi.requestChanges(decided.teacherId, text)
      showToast(t('adminUsers.changesRequested', { name: decided.fullName }))
    }
    decision.value = null
    notifyProgressChanged()
    await applications.reload()
    const next = waiting.value.find((item) => item.teacherId !== decided.teacherId)
    const query = { ...route.query }
    if (next) query.teacher = String(next.teacherId)
    else delete query.teacher
    await router.replace({ query })
  } catch (failure) {
    dialogError.value = resolveApiError(failure, t('adminUsers.decisionFailed'))
  } finally {
    saving.value = false
  }
}
</script>
