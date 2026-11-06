<template>
  <div class="page-stack teacher-live">
    <PageHeaderBlock :title="t('teacherSessions.title')" hide-title />
    <AppToast :message="toast" />

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="resource.reload">
      <!-- Nothing yet: the page is one empty card with the next step. -->
      <section v-if="hasNoSessions" class="panel teacher-empty-page">
        <StatePanel
          tone="empty"
          icon="live"
          spacious
          :title="t('teacherSessions.emptyTitle')"
          :message="hasClasses ? t('teacherSessions.emptyWithClass') : t('teacherSessions.emptyNoClass')"
        >
          <template #actions>
            <button
              v-if="hasClasses"
              type="button"
              class="btn btn-primary"
              data-testid="schedule-session"
              @click="openForm(null)"
            >
              <IconGlyph name="calendar" :size="16" />{{ t('teacherSessions.schedule') }}
            </button>
            <RouterLink v-else class="btn btn-primary" :to="{ name: 'teacher-classes' }">
              {{ t('teacherSessions.goToClasses') }}
            </RouterLink>
          </template>
        </StatePanel>
      </section>

      <div v-else class="page-stack">
        <RouteTabs :tabs="tabs" :label="t('teacherSessions.tabsLabel')">
          <button type="button" class="btn btn-primary" data-testid="schedule-session" @click="openForm(null)">
            <IconGlyph name="calendar" :size="16" />{{ t('teacherSessions.schedule') }}
          </button>
        </RouteTabs>
        <DataNotice :error="actionError" />

        <section
          class="panel teacher-card"
          :aria-label="tab === 'past' ? t('teacherSessions.past') : t('teacherSessions.upcoming')"
        >
          <StatePanel
            v-if="!rows.length"
            tone="empty"
            icon="calendar"
            :title="tab === 'past' ? t('teacherSessions.emptyPastTitle') : t('teacherSessions.emptyUpcomingTitle')"
            :message="
              tab === 'past' ? t('teacherSessions.emptyPastMessage') : t('teacherSessions.emptyUpcomingMessage')
            "
          />
          <ul v-else class="row-list">
            <li v-for="session in rows" :key="session.id" :data-testid="`teacher-session-${session.id}`">
              <div class="teacher-session-row" :class="{ 'is-cancelled': session.status === 'CANCELLED' }">
                <SessionDateTile :at="session.startsAt" :muted="session.status === 'CANCELLED'" />
                <div class="row-list-copy">
                  <span class="row-list-title cjk-text">{{ session.title }}</span>
                  <span class="row-list-meta">{{ session.classTitle }}</span>
                  <span class="row-list-meta">{{ sessionWhen(session) }}</span>
                </div>
              </div>
              <div class="row-list-actions">
                <StatusPill
                  v-if="session.status === 'CANCELLED'"
                  :label="t('teacherSessions.cancelled')"
                  tone="outline"
                />
                <button
                  v-else-if="tab === 'upcoming'"
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :aria-label="t('teacherSessions.editLabel', { title: session.title })"
                  @click="openForm(session)"
                >
                  <IconGlyph name="edit" :size="16" />{{ t('teacherSessions.edit') }}
                </button>
                <button
                  v-else-if="session.status === 'COMPLETED'"
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :aria-label="
                    t(
                      session.recordingUrl
                        ? 'teacherSessions.recording.editLabel'
                        : 'teacherSessions.recording.addLabel',
                      {
                        title: session.title,
                      },
                    )
                  "
                  @click="openRecording(session)"
                >
                  <template v-if="session.recordingUrl">
                    <IconGlyph name="edit" :size="16" />{{ t('teacherSessions.recording.edit') }}
                  </template>
                  <template v-else>
                    <IconGlyph name="plus" :size="16" />{{ t('teacherSessions.recording.add') }}
                  </template>
                </button>
              </div>
            </li>
          </ul>
        </section>
      </div>
    </ResourceSection>

    <LiveSessionDrawer
      :open="formOpen"
      :session="editing"
      :options="options.data.value"
      :busy="busy"
      :error="formError"
      @close="formOpen = false"
      @save="saveSession"
      @cancel-session="cancelOpen = true"
    />

    <AppOverlay
      :open="recordingFor !== null"
      :title="recordingFor?.title ?? ''"
      :subtitle="recordingFor ? sessionWhen(recordingFor) : ''"
      :busy="busy"
      test-id="session-recording"
      @close="recordingFor = null"
    >
      <form :id="recordingFormId" class="form-grid" @submit.prevent="saveRecording">
        <div class="field">
          <label class="field-label" for="session-recording">{{ t('teacherSessions.recording.link') }}</label>
          <input
            id="session-recording"
            v-model="recordingUrl"
            class="input"
            type="url"
            required
            maxlength="500"
            placeholder="https://"
            data-autofocus
          />
        </div>
        <p v-if="recordingError" class="field-error" role="alert">{{ recordingError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn btn-secondary" :disabled="busy" @click="recordingFor = null">
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :form="recordingFormId"
          :disabled="busy || !recordingUrl.trim()"
          :aria-busy="busy"
          data-testid="session-recording-save"
        >
          {{ t('teacherSessions.recording.save') }}
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="cancelOpen"
      :title="t('teacherSessions.cancel.title')"
      :message="
        editing
          ? t('teacherSessions.cancel.message', {
              title: editing.title,
              day: formatWeekdayDate(editing.startsAt),
              time: formatTime(editing.startsAt),
            })
          : ''
      "
      :confirm-label="t('teacherSessions.cancel.confirm')"
      :cancel-label="t('teacherSessions.cancel.keep')"
      tone="danger"
      :busy="busy"
      :error="cancelError"
      @cancel="cancelOpen = false"
      @confirm="cancelSession"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { teacherSessionsApi } from '@/api/classes'
import LiveSessionDrawer from '@/components/classes/LiveSessionDrawer.vue'
import SessionDateTile from '@/components/classes/SessionDateTile.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { commonMessages } from '@/plugins/commonMessages'
import { teacherSessionsMessages } from '@/plugins/teacherSessionsMessages'
import type { LiveSessionPayload, TeacherSession } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatTime, formatWeekdayDate } from '@/utils/format'
import { sessionWhen } from '@/utils/teacherSessions'
import '@/styles/classes.css'
import '@/styles/classes-teacher.css'

const { t } = useLocalMessages({ teacherSessions: teacherSessionsMessages, common: commonMessages })
const route = useRoute()
const { actionError, showError } = useActionFeedback()
const { toast, showToast } = useToast()

const tab = computed(() => (route.params.tab === 'past' ? 'past' : 'upcoming'))
const resource = useResource(() => teacherSessionsApi.list())
const options = useResource(() => teacherSessionsApi.options())
const rows = computed(() => (tab.value === 'past' ? resource.data.value?.past : resource.data.value?.upcoming) ?? [])
const activeUpcoming = computed(
  () => resource.data.value?.upcoming.filter((session) => session.status !== 'CANCELLED') ?? [],
)
const hasNoSessions = computed(() => {
  const data = resource.data.value
  return Boolean(data) && !data!.upcoming.length && !data!.past.length
})
const hasClasses = computed(() => Boolean(options.data.value?.classes.length))

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('teacherSessions.upcoming'),
    to: { name: 'teacher-live-sessions' },
    icon: 'calendar',
    count: resource.data.value ? activeUpcoming.value.length : null,
  },
  {
    label: t('teacherSessions.past'),
    to: { name: 'teacher-live-sessions', params: { tab: 'past' } },
    icon: 'history',
    count: resource.data.value?.past.length ?? null,
  },
])

const busy = ref(false)

// ----- schedule, edit, cancel -----

const formOpen = ref(false)
const editing = ref<TeacherSession | null>(null)
const formError = ref<string | null>(null)
const cancelOpen = ref(false)
const cancelError = ref<string | null>(null)

function openForm(session: TeacherSession | null): void {
  editing.value = session
  formError.value = null
  formOpen.value = true
  if (!options.data.value) void options.reload()
}

async function saveSession(payload: LiveSessionPayload): Promise<void> {
  busy.value = true
  formError.value = null
  try {
    const saved = editing.value
      ? await teacherSessionsApi.update(editing.value.id, payload)
      : await teacherSessionsApi.schedule(payload)
    formOpen.value = false
    await resource.reload()
    showToast(
      editing.value
        ? t('teacherSessions.form.updated')
        : t('teacherSessions.form.scheduled', {
            title: saved.title,
            day: formatWeekdayDate(saved.startsAt),
            time: formatTime(saved.startsAt),
          }),
    )
  } catch (reason) {
    formError.value = resolveApiError(reason, t('teacherSessions.form.error'))
  } finally {
    busy.value = false
  }
}

watch(cancelOpen, () => {
  cancelError.value = null
})

async function cancelSession(): Promise<void> {
  if (!editing.value) return
  busy.value = true
  cancelError.value = null
  try {
    await teacherSessionsApi.cancel(editing.value.id)
    cancelOpen.value = false
    formOpen.value = false
    await resource.reload()
    showToast(t('teacherSessions.cancel.done'))
  } catch (reason) {
    cancelError.value = resolveApiError(reason, t('teacherSessions.cancel.error'))
  } finally {
    busy.value = false
  }
}

// ----- recording link -----

const recordingFormId = useId()
const recordingFor = ref<TeacherSession | null>(null)
const recordingUrl = ref('')
const recordingError = ref<string | null>(null)

function openRecording(session: TeacherSession): void {
  recordingFor.value = session
  recordingUrl.value = session.recordingUrl ?? ''
  recordingError.value = null
}

async function saveRecording(): Promise<void> {
  if (!recordingFor.value) return
  busy.value = true
  recordingError.value = null
  try {
    await teacherSessionsApi.saveRecording(recordingFor.value.id, recordingUrl.value.trim())
    recordingFor.value = null
    await resource.reload()
    showToast(t('teacherSessions.recording.saved'))
  } catch (reason) {
    recordingError.value = resolveApiError(reason, t('teacherSessions.recording.error'))
  } finally {
    busy.value = false
  }
}

// A failed reload of the options leaves the form to say that no class can be chosen.
watch(
  () => options.error.value,
  (message) => {
    if (message) showError(message)
  },
)
</script>
