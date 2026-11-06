<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminClasses.content.title')" hide-title />
    <AdminContentTabs />
    <DataNotice :error="actionError" />

    <section class="panel admin-table-card" aria-labelledby="admin-teacher-classes-list">
      <h2 id="admin-teacher-classes-list" class="sr-only">{{ t('adminClasses.content.tableLabel') }}</h2>
      <form class="admin-filters" role="search" @submit.prevent>
        <div class="field is-search">
          <label class="field-label" for="content-search">{{ t('adminClasses.content.search') }}</label>
          <div class="input-with-icon">
            <IconGlyph name="search" class="input-icon" :size="16" />
            <input
              id="content-search"
              v-model="keyword"
              class="input"
              type="search"
              maxlength="200"
              :placeholder="t('adminClasses.content.searchPlaceholder')"
            />
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="content-type">{{ t('adminClasses.content.type') }}</label>
          <select id="content-type" v-model="type" class="select">
            <option :value="null">{{ t('adminClasses.content.allTypes') }}</option>
            <option value="CLASS">{{ t('adminClasses.content.typeClass') }}</option>
            <option value="LESSON">{{ t('adminClasses.content.typeLesson') }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="content-status">{{ t('adminClasses.content.status') }}</label>
          <select id="content-status" v-model="status" class="select">
            <option :value="null">{{ t('adminClasses.content.allStatuses') }}</option>
            <option v-for="value in STATUSES" :key="value" :value="value">{{ t(STATUS_KEYS[value]) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="content-teacher">{{ t('adminClasses.content.teacher') }}</label>
          <select id="content-teacher" v-model="teacherId" class="select">
            <option :value="null">{{ t('adminClasses.content.allTeachers') }}</option>
            <option v-for="teacher in teachers.data.value ?? []" :key="teacher.id" :value="teacher.id">
              {{ teacher.name }}
            </option>
          </select>
        </div>
      </form>

      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        :title="t('adminClasses.content.loadingTitle')"
        :message="t('adminClasses.content.title')"
      />
      <StatePanel
        v-else-if="resource.error.value && !items.length"
        tone="error"
        icon="alert"
        :title="t('adminClasses.content.loadErrorTitle')"
        :message="resource.error.value"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="resource.reload">
            {{ t('adminClasses.review.retry') }}
          </button>
        </template>
      </StatePanel>
      <template v-else-if="items.length">
        <div
          class="data-table-wrap admin-table-wrap"
          role="region"
          :aria-label="t('adminClasses.content.tableLabel')"
          tabindex="0"
        >
          <table class="data-table admin-table admin-classes-table">
            <thead>
              <tr>
                <th scope="col" class="admin-classes-col-title">{{ t('adminClasses.content.colTitle') }}</th>
                <th scope="col" class="admin-classes-col-type">{{ t('adminClasses.content.colType') }}</th>
                <th scope="col" class="admin-classes-col-class">{{ t('adminClasses.content.colClass') }}</th>
                <th scope="col" class="admin-classes-col-teacher">{{ t('adminClasses.content.colTeacher') }}</th>
                <th scope="col" class="admin-classes-col-status">{{ t('adminClasses.content.colStatus') }}</th>
                <th scope="col" class="admin-classes-col-updated">{{ t('adminClasses.content.colUpdated') }}</th>
                <th scope="col" class="col-actions admin-col-open">
                  <span class="sr-only">{{ t('adminClasses.content.colActions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in items"
                :key="`${row.type}-${row.id}`"
                :class="{ 'is-selected': detail?.row.type === row.type && detail?.row.id === row.id }"
                @click="open(row)"
              >
                <td class="admin-cell-strong">{{ row.title }}</td>
                <td>
                  <span class="tag is-outline">{{ typeLabel(row.type) }}</span>
                </td>
                <td class="admin-cell-muted">{{ row.classTitle ?? '—' }}</td>
                <td class="admin-cell-muted">{{ row.teacherName }}</td>
                <td><StatusPill v-bind="statusPill(row)" /></td>
                <td class="admin-cell-date">{{ formatShortDate(row.updatedAt) }}</td>
                <td class="col-actions">
                  <button
                    type="button"
                    class="btn btn-secondary btn-sm"
                    :aria-label="t('adminClasses.content.openLabel', { title: row.title })"
                    @click.stop="open(row)"
                  >
                    {{ t('adminClasses.content.open') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          :current-page="page + 1"
          :total-pages="totalPages"
          :label="t('adminClasses.content.pagination')"
          :info="t('adminClasses.content.showing', range)"
          icon-only
          @update:current-page="goToPage($event - 1)"
        />
      </template>
      <StatePanel
        v-else
        tone="empty"
        icon="search"
        spacious
        :title="t('adminClasses.content.emptyTitle')"
        :message="t('adminClasses.content.emptyMessage')"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="clearFilters">
            {{ t('adminClasses.content.clearFilters') }}
          </button>
        </template>
      </StatePanel>
    </section>

    <AppOverlay
      variant="drawer"
      :open="detail !== null"
      :title="detail?.row.title ?? ''"
      :subtitle="drawerSubtitle"
      :busy="busy"
      test-id="content-detail"
      @close="detail = null"
    >
      <template v-if="detail">
        <StatusPill v-bind="statusPill(detail.row)" />
        <div v-if="detail.reviewNote" class="callout">
          <IconGlyph name="edit" :size="16" />
          <div class="admin-callout-copy">
            <strong>{{ t('adminClasses.content.detail.reviewNote') }}</strong>
            <span>{{ reviewNoteText }}</span>
          </div>
        </div>
        <section class="admin-section" aria-labelledby="content-detail-preview">
          <h3 id="content-detail-preview" class="admin-section-title">
            {{ t('adminClasses.content.detail.preview') }}
          </h3>
          <dl class="kv-list">
            <template v-if="detail.row.type === 'CLASS'">
              <dt>{{ t('adminClasses.content.detail.units') }}</dt>
              <dd>{{ detail.unitCount }}</dd>
              <dt>{{ t('adminClasses.content.detail.lessons') }}</dt>
              <dd>{{ detail.lessonCount }}</dd>
              <dt>{{ t('adminClasses.content.detail.studentsLabel') }}</dt>
              <dd>{{ detail.studentCount }}</dd>
            </template>
            <template v-else>
              <dt>{{ t('adminClasses.content.detail.class') }}</dt>
              <dd>{{ detail.row.classTitle }}</dd>
              <dt>{{ t('adminClasses.content.detail.unit') }}</dt>
              <dd>{{ detail.unitTitle ?? '—' }}</dd>
              <dt>{{ t('adminClasses.content.detail.practice') }}</dt>
              <dd>{{ t('adminClasses.review.activities', detail.activityCount) }}</dd>
              <dt>{{ t('adminClasses.content.detail.quiz') }}</dt>
              <dd>{{ t('adminClasses.review.questions', detail.questionCount) }}</dd>
            </template>
            <dt>{{ t('adminClasses.content.detail.teacher') }}</dt>
            <dd>{{ detail.row.teacherName }}</dd>
            <dt>{{ t('adminClasses.content.detail.updated') }}</dt>
            <dd>{{ formatDate(detail.row.updatedAt) }}</dd>
          </dl>
        </section>
        <section class="admin-section" aria-labelledby="content-detail-history">
          <h3 id="content-detail-history" class="admin-section-title">{{ t('classHistory.title') }}</h3>
          <AdminContentHistory :events="detail.history" />
        </section>
      </template>
      <template v-if="detail" #footer>
        <p v-if="detail.canArchive" class="admin-drawer-note">{{ t('adminClasses.content.detail.archiveNote') }}</p>
        <RouterLink
          v-if="detail.row.status === 'SUBMITTED' || detail.row.revisionStatus === 'SUBMITTED'"
          class="btn btn-secondary"
          :to="{ name: 'admin-content-review' }"
        >
          {{ t('adminClasses.content.detail.openInReview') }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
        <button v-if="detail.canRestore" type="button" class="btn btn-secondary" @click="confirm = 'restore'">
          <IconGlyph name="archive" :size="16" />
          <span>{{ t('adminClasses.content.detail.restore') }}</span>
        </button>
        <button
          v-if="detail.canArchive"
          type="button"
          class="btn btn-secondary"
          data-testid="admin-archive"
          @click="confirm = 'archive'"
        >
          <IconGlyph name="archive" :size="16" />
          <span>{{ t('adminClasses.content.detail.archive') }}</span>
        </button>
      </template>
    </AppOverlay>

    <AdminDecisionDialog
      :open="confirm !== null"
      :title="
        confirm === 'archive'
          ? t('adminClasses.content.archiveTitle', { title: detail?.row.title ?? '' })
          : t('adminClasses.content.restoreTitle', { title: detail?.row.title ?? '' })
      "
      :confirm-label="confirmLabel"
      :confirm-icon="confirm === 'archive' ? 'close' : 'archive'"
      :cancel-label="
        detail?.row.type === 'CLASS' ? t('adminClasses.content.keepClass') : t('adminClasses.content.keepLesson')
      "
      :tone="confirm === 'archive' ? 'danger' : 'primary'"
      :busy="busy"
      test-id="content-confirm"
      @cancel="confirm = null"
      @confirm="applyConfirm"
    >
      <p class="overlay-message">{{ confirmMessage }}</p>
    </AdminDecisionDialog>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { adminClassesApi } from '@/api/classes'
import AdminContentHistory from '@/components/admin/AdminContentHistory.vue'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import AdminDecisionDialog from '@/components/admin/AdminDecisionDialog.vue'
import { teacherContentStatus } from '@/components/admin/adminDisplay'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import { classHistoryMessages } from '@/plugins/classHistoryMessages'
import type { TeacherContentDetail, TeacherContentRow } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatDateTime, formatShortDate } from '@/utils/format'
import '@/styles/ops-admin.css'

const { t } = useLocalMessages({ adminClasses: adminClassesMessages, classHistory: classHistoryMessages })
const route = useRoute()
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()

const PAGE_SIZE = 10
const STATUSES = [
  'PUBLISHED',
  'SUBMITTED',
  'DRAFT',
  'REJECTED',
  'ARCHIVED',
  'REVISION_SUBMITTED',
  'REVISION_REJECTED',
] as const
type StatusFilter = (typeof STATUSES)[number]
const STATUS_KEYS: Record<StatusFilter, string> = {
  PUBLISHED: 'adminClasses.content.statuses.PUBLISHED',
  SUBMITTED: 'adminClasses.content.statuses.SUBMITTED',
  DRAFT: 'adminClasses.content.statuses.DRAFT',
  REJECTED: 'adminClasses.content.statuses.REJECTED',
  ARCHIVED: 'adminClasses.content.statuses.ARCHIVED',
  REVISION_SUBMITTED: 'adminClasses.content.statuses.REVISION_SUBMITTED',
  REVISION_REJECTED: 'adminClasses.content.statuses.REVISION_REJECTED',
}

const { searchTerm: keyword, keyword: debouncedKeyword, clear: clearSearch } = useDebouncedSearch()
// The review queue links here with ?keyword=<class title>.
if (typeof route.query.keyword === 'string') {
  keyword.value = route.query.keyword
  debouncedKeyword.value = route.query.keyword.trim()
}
const type = ref<'CLASS' | 'LESSON' | null>(null)
const status = ref<StatusFilter | null>(null)
const teacherId = ref<number | null>(null)
const page = ref(0)

const resource = useResource(() =>
  adminClassesApi.content({
    keyword: debouncedKeyword.value || undefined,
    type: type.value ?? undefined,
    status: status.value ?? undefined,
    teacherId: teacherId.value ?? undefined,
    page: page.value,
    size: PAGE_SIZE,
  }),
)
const teachers = useResource(() => adminClassesApi.teachers())
const { items, totalPages, range, goToPage } = useServerPage(resource, page)

watch([debouncedKeyword, type, status, teacherId], () => {
  page.value = 0
  void resource.reload()
})

function clearFilters(): void {
  clearSearch()
  type.value = null
  status.value = null
  teacherId.value = null
}

function typeLabel(rowType: 'CLASS' | 'LESSON'): string {
  return rowType === 'CLASS' ? t('adminClasses.content.typeClass') : t('adminClasses.content.typeLesson')
}

function statusPill(row: TeacherContentRow) {
  const view = teacherContentStatus(row.status, row.revisionStatus)
  return { label: t(view.key), tone: view.tone }
}

// ----- one item, in a drawer -----

const detail = ref<TeacherContentDetail | null>(null)
const busy = ref(false)
const confirm = ref<'archive' | 'restore' | null>(null)

async function open(row: TeacherContentRow): Promise<void> {
  try {
    detail.value = await adminClassesApi.contentDetail(row.type, row.id)
  } catch (reason) {
    showError(resolveApiError(reason, t('adminClasses.content.loadErrorTitle')))
  }
}

const drawerSubtitle = computed(() => {
  const row = detail.value?.row
  if (!row) return ''
  return row.classTitle ? `${typeLabel(row.type)} · ${row.classTitle}` : typeLabel(row.type)
})

const reviewNoteText = computed(() => {
  const current = detail.value
  if (!current?.reviewNote) return ''
  return current.reviewNoteBy
    ? t('adminClasses.content.detail.reviewNoteBy', {
        name: current.reviewNoteBy,
        date: formatDateTime(current.reviewNoteAt),
        note: current.reviewNote,
      })
    : current.reviewNote
})

const confirmLabel = computed(() => {
  if (confirm.value === 'restore') return t('adminClasses.content.detail.restore')
  return detail.value?.row.type === 'CLASS'
    ? t('adminClasses.content.archiveClass')
    : t('adminClasses.content.archiveLesson')
})

const confirmMessage = computed(() => {
  const current = detail.value
  if (!current || !confirm.value) return ''
  const teacher = current.row.teacherName
  if (confirm.value === 'restore') return t('adminClasses.content.restoreMessage', { teacher })
  return current.row.type === 'CLASS'
    ? t('adminClasses.content.archiveClassMessage', { teacher })
    : t('adminClasses.content.archiveLessonMessage', { teacher })
})

async function applyConfirm(): Promise<void> {
  const current = detail.value
  const action = confirm.value
  if (!current || !action) return
  busy.value = true
  clearFeedback()
  try {
    detail.value =
      action === 'archive'
        ? await adminClassesApi.archive(current.row.type, current.row.id)
        : await adminClassesApi.restore(current.row.type, current.row.id)
    await resource.reload()
    const values = { title: current.row.title, teacher: current.row.teacherName }
    showToast(
      action === 'archive' ? t('adminClasses.content.archived', values) : t('adminClasses.content.restored', values),
    )
  } catch (reason) {
    showError(resolveApiError(reason, t('adminClasses.content.actionError')))
  } finally {
    busy.value = false
    confirm.value = null
  }
}
</script>
