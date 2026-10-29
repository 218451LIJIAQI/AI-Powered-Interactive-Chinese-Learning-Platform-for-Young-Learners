<template>
  <div class="admin-applications page-stack">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminUsers.applicationsPageTitle')" :description="t('adminUsers.applicationsLede')" />

      <AdminUsersTabs :applications="applications.data.value?.length ?? null" />
    </div>

    <DataNotice
      :error="actionError"
      :success="actionSuccess"
      :success-title="t('adminUsers.noticeDone')"
      :error-title="t('adminUsers.noticeFailed')"
    />

    <div class="admin-inbox">
      <section class="admin-sheet admin-queue" aria-labelledby="admin-applications-waiting">
        <div class="admin-sheet-head">
          <div>
            <h2 id="admin-applications-waiting" class="admin-sheet-title">{{ t('adminUsers.waitingTitle') }}</h2>
            <p class="admin-sheet-caption">{{ t('adminUsers.oldestFirst') }}</p>
          </div>
        </div>

        <div v-if="applications.loading.value" class="admin-sheet-body">
          <StatePanel
            tone="loading"
            :title="t('adminUsers.applicationsLoading')"
            :message="t('adminUsers.applicationsLoadingHint')"
          />
        </div>
        <div v-else-if="applications.error.value && !waiting.length" class="admin-sheet-body">
          <StatePanel
            tone="error"
            icon="alert"
            :title="t('adminUsers.applicationsError')"
            :message="applications.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="applications.reload">
                {{ t('adminUsers.retry') }}
              </button>
            </template>
          </StatePanel>
        </div>
        <ul v-else-if="waiting.length" class="admin-queue-list" :aria-label="t('adminUsers.applicationsListLabel')">
          <li v-for="item in waiting" :key="item.teacherId">
            <button
              class="admin-queue-item"
              type="button"
              :aria-current="item.teacherId === selectedId ? 'true' : undefined"
              data-testid="admin-applications-item"
              @click="selectApplication(item.teacherId)"
            >
              <PortraitAvatar :size="32" :name="item.fullName" />
              <span class="admin-queue-item-copy">
                <strong>{{ item.fullName }}</strong>
                <span class="admin-queue-item-meta">{{ item.email }}</span>
                <span class="admin-queue-item-tags">
                  <StatusPill :label="t(verificationStateKey('IN_REVIEW'))" :tone="verificationTone('IN_REVIEW')" />
                  <span class="admin-queue-item-meta">
                    {{ t('adminUsers.submittedOn', { date: formatDateTime(item.submittedAt) }) }}
                  </span>
                </span>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="admin-sheet-body">
          <StatePanel
            tone="empty"
            icon="check"
            :title="t('adminUsers.applicationsEmptyTitle')"
            :message="t('adminUsers.applicationsEmptyHint')"
          />
        </div>
      </section>

      <div ref="detailRef" class="admin-reader-slot" tabindex="-1">
        <StatePanel
          v-if="!selectedId"
          tone="empty"
          icon="verification"
          spacious
          :title="t('adminUsers.chooseApplication')"
          :message="t('adminUsers.chooseApplicationHint')"
        />
        <StatePanel
          v-else-if="application.loading.value"
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
          class="panel admin-reader"
          aria-labelledby="admin-applications-name"
          data-testid="admin-applications-detail"
        >
          <header class="admin-reader-head">
            <PortraitAvatar :size="56" :name="current.fullName" />
            <div class="admin-reader-heading">
              <h2 id="admin-applications-name">{{ current.fullName }}</h2>
              <p>
                {{ current.email
                }}<template v-if="current.submittedAt">
                  · {{ t('adminUsers.submittedOn', { date: formatDateTime(current.submittedAt) }) }}</template
                >
              </p>
              <div class="admin-reader-tags">
                <StatusPill
                  :label="t(verificationStateKey(current.verificationState))"
                  :tone="verificationTone(current.verificationState)"
                />
              </div>
            </div>
            <RouterLink class="btn btn-link" :to="{ name: 'admin-users', query: { user: current.userId } }">
              {{ t('adminUsers.openAccount') }}
              <IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
          </header>

          <section class="admin-reader-section" aria-labelledby="admin-applications-bio">
            <h3 id="admin-applications-bio" class="admin-reader-label">{{ t('adminUsers.bio') }}</h3>
            <p class="admin-applications-bio">{{ current.bio || t('adminUsers.noBio') }}</p>
          </section>

          <section class="admin-reader-section" aria-labelledby="admin-applications-documents">
            <div class="admin-applications-section-head">
              <h3 id="admin-applications-documents" class="admin-reader-label">{{ t('adminUsers.documents') }}</h3>
              <span class="type-caption">
                {{ t('adminUsers.filesCount', { count: current.documents.length }, current.documents.length) }}
              </span>
            </div>
            <ul v-if="current.documents.length" class="admin-applications-documents">
              <li v-for="document in current.documents" :key="document.id" data-testid="admin-applications-document">
                <span class="admin-applications-document-icon" aria-hidden="true">
                  <IconGlyph name="file" :size="18" />
                </span>
                <span class="admin-cell-title">
                  <strong>{{ t(documentTypeKey(document.documentType)) }}</strong>
                  <span>{{ document.fileName }} · {{ formatFileSize(document.fileSize) }}</span>
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
            <p v-else class="type-caption">{{ t('adminUsers.noDocuments') }}</p>
            <p class="type-caption">{{ t('adminUsers.documentsNote') }}</p>
          </section>

          <section class="admin-reader-section" aria-labelledby="admin-applications-history">
            <h3 id="admin-applications-history" class="admin-reader-label">{{ t('adminUsers.history') }}</h3>
            <ol class="admin-timeline">
              <li v-for="(entry, index) in current.history" :key="`${entry.event}-${entry.at}-${index}`">
                <span class="admin-timeline-mark" aria-hidden="true">
                  <IconGlyph :name="HISTORY_ICONS[entry.event]" :size="14" />
                </span>
                <span class="admin-timeline-copy">
                  <strong>{{ t(HISTORY_KEYS[entry.event]) }}</strong>
                  <span>
                    {{
                      entry.actorName
                        ? t('adminUsers.historyMeta', { actor: entry.actorName, date: formatDateTime(entry.at) })
                        : formatDateTime(entry.at)
                    }}
                  </span>
                  <q v-if="entry.note">{{ entry.note }}</q>
                </span>
              </li>
            </ol>
          </section>

          <footer v-if="current.verificationState === 'IN_REVIEW'" class="admin-reader-decision">
            <p>{{ t('adminUsers.decisionNote', { name: current.fullName }) }}</p>
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
          <p v-else class="admin-reader-section type-caption">{{ t('adminUsers.decidedNote') }}</p>
        </section>
      </div>
    </div>

    <AppOverlay
      :open="decision !== null"
      :title="decisionTitle"
      :subtitle="current ? t('adminUsers.applicationSubtitle', { name: current.fullName }) : ''"
      test-id="admin-applications-dialog"
      @close="closeDecision"
    >
      <div v-if="current" class="callout" :class="{ 'is-warning': decision === 'changes' }">
        <IconGlyph :name="decision === 'approve' ? 'check' : 'info'" :size="18" />
        <ul>
          <li>
            <strong>
              {{
                decision === 'approve'
                  ? t('adminUsers.approveCopy', { name: current.fullName })
                  : t('adminUsers.changesCopy', { name: current.fullName })
              }}
            </strong>
          </li>
          <li>
            {{
              decision === 'approve'
                ? t('adminUsers.approveNotice', { name: current.fullName })
                : t('adminUsers.changesNotice', { name: current.fullName })
            }}
          </li>
        </ul>
      </div>
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
      <p v-if="dialogError" class="alert alert-danger" role="alert">{{ dialogError }}</p>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="closeDecision">{{ t('adminUsers.cancel') }}</button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="saving"
          :aria-busy="saving"
          data-testid="admin-applications-confirm"
          @click="confirmDecision"
        >
          {{ decision === 'approve' ? t('adminUsers.approve') : t('adminUsers.requestChanges') }}
        </button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { adminUsersApi } from '@/api/ops'
import AdminUsersTabs from '@/components/admin/AdminUsersTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { TeacherApplication, TeacherApplicationEvent } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { openFileDownload } from '@/utils/opsFiles'
import { formatDateTime, formatFileSize } from '@/utils/format'
import { documentTypeKey, verificationStateKey, verificationTone } from '@/utils/opsLabels'
import '@/styles/ops-admin.css'

type Decision = 'approve' | 'changes'

const HISTORY_KEYS: Record<TeacherApplicationEvent, string> = {
  ACCOUNT_CREATED: 'adminUsers.historyAccountCreated',
  APPLICATION_SUBMITTED: 'adminUsers.historySubmitted',
  APPROVED: 'adminUsers.historyApproved',
  CHANGES_REQUESTED: 'adminUsers.historyChangesRequested',
}
const HISTORY_ICONS: Record<TeacherApplicationEvent, string> = {
  ACCOUNT_CREATED: 'profile',
  APPLICATION_SUBMITTED: 'upload',
  APPROVED: 'check',
  CHANGES_REQUESTED: 'edit',
}

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ adminUsers: adminUsersMessages, opsLabels: opsLabelMessages })
const { actionError, actionSuccess, showError, showSuccess } = useActionFeedback()
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
  return decision.value === 'changes' ? t('adminUsers.changesTitle', { name }) : t('adminUsers.approveTitle', { name })
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
  try {
    if (kind === 'approve') {
      await adminUsersApi.approve(decided.teacherId)
      showSuccess(t('adminUsers.approved', { name: decided.fullName }))
    } else {
      await adminUsersApi.requestChanges(decided.teacherId, text)
      showSuccess(t('adminUsers.changesRequested', { name: decided.fullName }))
    }
    decision.value = null
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
