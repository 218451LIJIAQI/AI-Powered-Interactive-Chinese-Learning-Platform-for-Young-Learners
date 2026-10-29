<template>
  <div class="page-stack teaching-profile">
    <PageHeaderBlock :title="t('verification.profileTitle')" :description="t('verification.profileLede')" />
    <AppToast :message="toast" />

    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      spacious
      :title="t('verification.loadingTitle')"
      :message="t('verification.loadingHint')"
    />
    <StatePanel
      v-else-if="!profile"
      tone="error"
      icon="alert"
      spacious
      :title="t('verification.loadErrorTitle')"
      :message="resource.error.value ?? ''"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('verification.retry') }}</button>
      </template>
    </StatePanel>

    <template v-else>
      <DataNotice :success="submittedNotice" :success-title="t('verification.submittedTitle')" />

      <div class="teaching-profile-layout">
        <div class="teaching-profile-column">
          <section class="panel teaching-profile-card" aria-labelledby="teaching-public-title">
            <div class="teaching-card-head">
              <h2 id="teaching-public-title" class="card-title">{{ t('verification.publicProfile') }}</h2>
              <p class="type-caption">{{ t('verification.publicProfileHint') }}</p>
            </div>
            <form class="form-grid" novalidate @submit.prevent="saveProfile">
              <div class="field">
                <label for="teaching-name">
                  {{ t('verification.fullName') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teaching-name"
                  v-model="form.fullName"
                  class="input"
                  autocomplete="name"
                  maxlength="120"
                  aria-required="true"
                  :aria-invalid="Boolean(nameError)"
                  :aria-describedby="nameError ? 'teaching-name-error' : undefined"
                />
                <p v-if="nameError" id="teaching-name-error" class="field-error">{{ nameError }}</p>
              </div>
              <div class="field">
                <label for="teaching-bio">
                  {{ t('verification.bio') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <textarea
                  id="teaching-bio"
                  v-model="form.bio"
                  class="textarea"
                  rows="4"
                  :maxlength="TEACHER_BIO_MAX_LENGTH"
                  aria-required="true"
                  :aria-invalid="Boolean(bioError)"
                  :aria-describedby="bioError ? 'teaching-bio-error teaching-bio-rule' : 'teaching-bio-rule'"
                />
                <div class="field-foot">
                  <p id="teaching-bio-rule" class="field-hint">{{ t('verification.bioRule') }}</p>
                  <p class="field-hint field-counter" aria-live="polite">
                    {{ t('verification.bioCounter', { count: bioLength }) }}
                  </p>
                </div>
                <p v-if="bioError" id="teaching-bio-error" class="field-error">{{ bioError }}</p>
              </div>
              <p v-if="saveError" class="alert alert-danger" role="alert">
                <IconGlyph name="alert" :size="18" />
                <span>{{ saveError }}</span>
              </p>
              <div class="form-actions">
                <button
                  class="btn btn-secondary"
                  type="submit"
                  :disabled="saving"
                  :aria-busy="saving"
                  data-testid="teaching-save"
                >
                  <IconGlyph name="check" :size="16" />
                  {{ saving ? t('verification.saving') : t('verification.saveProfile') }}
                </button>
              </div>
            </form>
          </section>

          <section
            class="panel teaching-profile-card teaching-verification"
            aria-labelledby="teaching-verification-title"
          >
            <div class="teaching-verification-head">
              <span
                class="seal teaching-verification-seal"
                :class="{ 'is-outline': state !== 'APPROVED' }"
                lang="zh-CN"
                aria-hidden="true"
                >证</span
              >
              <div class="teaching-card-head">
                <h2 id="teaching-verification-title" class="card-title">{{ t('verification.verification') }}</h2>
                <p class="type-caption">{{ t('verification.adminsOnly') }}</p>
              </div>
              <StatusPill
                :label="t(verificationStateKey(state))"
                :tone="verificationTone(state)"
                data-testid="teaching-state"
              />
            </div>

            <div
              v-if="state === 'CHANGES_REQUESTED' && decision"
              class="callout is-warning"
              data-testid="teaching-reason"
            >
              <IconGlyph name="alert" :size="18" />
              <div class="teaching-reason">
                <strong>
                  {{
                    decision.reviewerName
                      ? t('verification.changesBy', { name: decision.reviewerName })
                      : t('verification.changesByAdmin')
                  }}
                </strong>
                <p>{{ decision.reason }}</p>
                <span class="type-caption">{{ formatDateTime(decision.decidedAt) }}</span>
              </div>
            </div>
            <p class="teaching-verification-copy">{{ stateCopy }}</p>

            <ul class="teaching-documents" :aria-label="t('verification.documentsList')">
              <li
                v-for="row in documentRows"
                :key="row.type"
                class="teaching-document"
                :data-testid="`teaching-doc-${row.type}`"
              >
                <span class="teaching-document-icon" aria-hidden="true"><IconGlyph name="file" :size="18" /></span>
                <div class="teaching-document-copy">
                  <strong>
                    {{ t(documentTypeKey(row.type)) }}
                    <span class="teaching-document-need"
                      >({{ row.required ? t('verification.required') : t('verification.optional') }})</span
                    >
                  </strong>
                  <button
                    v-if="row.document"
                    class="teaching-document-file"
                    type="button"
                    :aria-label="t('verification.download', { name: row.document.fileName })"
                    @click="download(row.document.id, row.document.fileName)"
                  >
                    {{ row.document.fileName }}
                  </button>
                  <p v-if="uploadErrors[row.type]" class="field-error" role="alert">{{ uploadErrors[row.type] }}</p>
                </div>
                <StatusPill
                  v-if="row.document"
                  :label="t(documentStatusKey(row.document.status))"
                  :tone="documentStatusTone(row.document.status)"
                />
                <StatusPill
                  v-else
                  :label="t('verification.statusMissing')"
                  :tone="row.required ? 'warning' : 'neutral'"
                />
                <template v-if="editable">
                  <input
                    :ref="(element) => setFileInput(row.type, element)"
                    class="sr-only"
                    type="file"
                    tabindex="-1"
                    aria-hidden="true"
                    accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                    :data-testid="`teaching-file-${row.type}`"
                    @change="upload(row.type, $event)"
                  />
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :disabled="uploading !== null"
                    :aria-busy="uploading === row.type"
                    :aria-label="
                      t(row.document ? 'verification.replaceLabel' : 'verification.uploadLabel', {
                        document: t(documentTypeKey(row.type)),
                      })
                    "
                    :data-testid="`teaching-upload-${row.type}`"
                    @click="fileInputs[row.type]?.click()"
                  >
                    <IconGlyph name="upload" :size="14" />
                    {{
                      uploading === row.type
                        ? t('verification.uploading')
                        : row.document
                          ? t('verification.replace')
                          : t('verification.upload')
                    }}
                  </button>
                </template>
              </li>
            </ul>
            <p class="teaching-file-rule">
              <IconGlyph name="info" :size="14" />
              <span>{{ t('verification.fileRule') }}</span>
            </p>

            <p v-if="state === 'IN_REVIEW'" class="callout is-compact" data-testid="teaching-read-only">
              <IconGlyph name="lock" :size="18" />
              <span>{{ t('verification.readOnly') }}</span>
            </p>

            <form v-if="editable" class="teaching-submit" novalidate @submit.prevent="submit">
              <label class="choice">
                <input v-model="consent" type="checkbox" data-testid="teaching-consent" />
                <span>{{ t('verification.consent') }}</span>
              </label>
              <RouterLink class="teaching-consent-link" to="/safety#teacher-expectations">
                {{ t('verification.consentLink') }} <IconGlyph name="chevronRight" :size="14" />
              </RouterLink>
              <p v-if="submitBlocker" id="teaching-submit-hint" class="field-hint">{{ submitBlocker }}</p>
              <p v-if="submitError" class="alert alert-danger" role="alert">
                <IconGlyph name="alert" :size="18" />
                <span>{{ submitError }}</span>
              </p>
              <div class="form-actions">
                <button
                  class="btn btn-primary"
                  type="submit"
                  :disabled="Boolean(submitBlocker) || submitting"
                  :aria-busy="submitting"
                  :aria-describedby="submitBlocker ? 'teaching-submit-hint' : undefined"
                  data-testid="teaching-submit"
                >
                  <IconGlyph name="send" :size="16" />
                  {{
                    submitting
                      ? t('verification.submitting')
                      : state === 'CHANGES_REQUESTED'
                        ? t('verification.resubmit')
                        : t('verification.submit')
                  }}
                </button>
              </div>
            </form>
          </section>
        </div>

        <aside class="teaching-preview-aside" aria-labelledby="teaching-preview-title">
          <h2 id="teaching-preview-title" class="teaching-preview-label">
            <IconGlyph name="eye" :size="14" />{{ t('verification.howStudentsSee') }}
          </h2>
          <article class="teaching-preview">
            <div class="teaching-preview-head">
              <PortraitAvatar :size="64" :name="form.fullName || profile.fullName" />
              <div>
                <strong class="teaching-preview-name">{{ form.fullName || profile.fullName }}</strong>
                <span class="type-caption teaching-preview-role">
                  {{ t('verification.teacherRole') }}
                  <template v-if="state === 'APPROVED'">
                    · <IconGlyph name="verified" :size="14" />{{ t('verification.statusVerified') }}
                  </template>
                </span>
              </div>
            </div>
            <p class="teaching-preview-bio">{{ form.bio || profile.bio }}</p>
            <ul v-if="profile.publishedClasses.length" class="teaching-preview-classes">
              <li v-for="published in profile.publishedClasses" :key="published.id">
                <IconGlyph name="classes" :size="14" />{{ published.title }}
              </li>
            </ul>
            <p v-else class="type-caption">{{ t('verification.noClassesYet') }}</p>
          </article>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, type ComponentPublicInstance } from 'vue'
import { RouterLink } from 'vue-router'

import { teacherVerificationApi } from '@/api/ops'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppToast from '@/components/common/AppToast.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { teacherVerificationMessages } from '@/plugins/teacherVerificationMessages'
import { useAuthStore } from '@/stores/auth'
import type { TeacherVerificationState } from '@/types/api'
import type { TeacherDocumentType } from '@/types/ops'
import { apiFieldError, resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDate, formatDateTime } from '@/utils/format'
import { documentTypeKey, verificationStateKey, verificationTone } from '@/utils/opsLabels'
import {
  APPLICATION_DOCUMENTS,
  documentOfType,
  documentStatusKey,
  documentStatusTone,
  hasRequiredDocuments,
} from '@/utils/teacherApplication'
import { TEACHER_BIO_MAX_LENGTH, teacherBioIssue } from '@/utils/teacherBio'
import { documentIssue } from '@/utils/uploadChecks'
import '@/styles/ops-account.css'

const { t } = useLocalMessages({ verification: teacherVerificationMessages, opsLabels: opsLabelMessages })
const auth = useAuthStore()
const { toast, showToast } = useToast()

const resource = useResource(() => teacherVerificationApi.profile())
const profile = resource.data
const form = reactive({ fullName: '', bio: '' })
// The form starts from the loaded profile; later answers (upload, submit) keep what the teacher has typed.
watch(
  profile,
  (value) => {
    form.fullName = value?.fullName ?? ''
    form.bio = value?.bio ?? ''
  },
  { once: true },
)

const state = computed<TeacherVerificationState>(() => profile.value?.verificationState ?? 'NOT_SUBMITTED')
const decision = computed(() => profile.value?.latestDecision ?? null)
const editable = computed(() => state.value === 'NOT_SUBMITTED' || state.value === 'CHANGES_REQUESTED')
const documentRows = computed(() =>
  APPLICATION_DOCUMENTS.map(({ type, required }) => ({
    type,
    required,
    document: documentOfType(profile.value?.documents ?? [], type),
  })),
)

const stateCopy = computed(() => {
  const documents = profile.value?.documents ?? []
  switch (state.value) {
    case 'IN_REVIEW':
      return t('verification.inReviewProfileCopy', { date: formatDate(profile.value?.submittedAt) })
    case 'APPROVED':
      return t('verification.approvedCopy', { date: formatDate(profile.value?.approvedAt) })
    case 'CHANGES_REQUESTED':
      return documents.some((document) => document.status === 'READY')
        ? t('verification.newFileAdded')
        : t('verification.replaceAndResubmit')
    default:
      return hasRequiredDocuments(documents) ? t('verification.readyCopy') : t('verification.uploadCopy')
  }
})

// ----- public profile -----

const saving = ref(false)
const nameError = ref<string | null>(null)
const bioError = ref<string | null>(null)
const saveError = ref<string | null>(null)
const bioLength = computed(() => form.bio.trim().length)

watch(
  () => form.bio,
  () => {
    bioError.value = null
  },
)

async function saveProfile(): Promise<void> {
  if (saving.value) return
  const issue = teacherBioIssue(form.bio)
  nameError.value = form.fullName.trim() ? null : t('verification.nameRequired')
  bioError.value =
    issue === 'required' ? t('verification.bioRequired') : issue === 'length' ? t('verification.bioLength') : null
  if (nameError.value || bioError.value) return

  saving.value = true
  saveError.value = null
  try {
    profile.value = await teacherVerificationApi.updateProfile({ fullName: form.fullName.trim(), bio: form.bio.trim() })
    form.fullName = profile.value.fullName
    form.bio = profile.value.bio ?? ''
    showToast(t('verification.changesSaved'))
    // The header and sidebar show the name from the signed-in account.
    void auth.refreshCurrentUser()
  } catch (reason) {
    const fieldError = apiFieldError(reason, 'bio')
    if (fieldError) bioError.value = fieldError
    else saveError.value = resolveApiError(reason, t('verification.saveError'))
  } finally {
    saving.value = false
  }
}

// ----- documents -----

const fileInputs: Partial<Record<TeacherDocumentType, HTMLInputElement>> = {}
const uploading = ref<TeacherDocumentType | null>(null)
const uploadErrors = reactive<Partial<Record<TeacherDocumentType, string>>>({})

function setFileInput(type: TeacherDocumentType, element: Element | ComponentPublicInstance | null): void {
  if (element instanceof HTMLInputElement) fileInputs[type] = element
}

async function upload(type: TeacherDocumentType, event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const issue = documentIssue(file)
  delete uploadErrors[type]
  if (issue) {
    uploadErrors[type] = issue === 'type' ? t('verification.uploadType') : t('verification.uploadSize')
    return
  }
  uploading.value = type
  try {
    profile.value = await teacherVerificationApi.upload(type, file)
    showToast(t('verification.uploaded', { document: t(documentTypeKey(type)) }))
  } catch (reason) {
    uploadErrors[type] = resolveApiError(reason, t('verification.uploadError'))
  } finally {
    uploading.value = null
  }
}

async function download(id: number, fileName: string): Promise<void> {
  try {
    saveBlobDownload(await teacherVerificationApi.document(id), fileName)
  } catch (reason) {
    saveError.value = resolveApiError(reason)
  }
}

// ----- submission -----

const consent = ref(false)
const submitting = ref(false)
const submitError = ref<string | null>(null)
const submittedNotice = ref<string | null>(null)

const submitBlocker = computed(() => {
  if (!hasRequiredDocuments(profile.value?.documents ?? [])) return t('verification.submitNeedsDocuments')
  return consent.value ? null : t('verification.submitNeedsConsent')
})

async function submit(): Promise<void> {
  if (submitBlocker.value || submitting.value) return
  const resubmission = state.value === 'CHANGES_REQUESTED'
  submitting.value = true
  submitError.value = null
  try {
    profile.value = await teacherVerificationApi.submit()
    consent.value = false
    submittedNotice.value = resubmission ? t('verification.resubmitted') : t('verification.submitted')
    // The sidebar's verification card reads the account's state.
    await auth.refreshTeacherApproval()
  } catch (reason) {
    submitError.value = resolveApiError(reason, t('verification.submitError'))
  } finally {
    submitting.value = false
  }
}
</script>
