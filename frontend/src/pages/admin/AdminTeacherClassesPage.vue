<template>
  <div class="admin-teacher-classes-page page-stack">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminClasses.content.title')" :description="t('adminClasses.content.lede')" />
      <AdminContentTabs />
    </div>
    <DataNotice :error="actionError" :success="actionSuccess" />

    <section class="admin-sheet" aria-labelledby="admin-teacher-classes-list">
      <h2 id="admin-teacher-classes-list" class="sr-only">{{ t('adminClasses.content.tableLabel') }}</h2>
      <form class="admin-toolbar" role="search" @submit.prevent>
        <div class="field admin-toolbar-search">
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

      <div v-if="resource.loading.value" class="admin-sheet-body">
        <StatePanel tone="loading" :title="t('classes.loading')" :message="t('adminClasses.content.title')" />
      </div>
      <div v-else-if="resource.error.value && !items.length" class="admin-sheet-body">
        <StatePanel
          tone="error"
          icon="alert"
          :title="t('adminClasses.content.loadErrorTitle')"
          :message="resource.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          </template>
        </StatePanel>
      </div>
      <template v-else-if="items.length">
        <div class="data-table-wrap" role="region" :aria-label="t('adminClasses.content.tableLabel')" tabindex="0">
          <table class="data-table admin-table admin-content-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminClasses.content.colTitle') }}</th>
                <th scope="col">{{ t('adminClasses.content.colType') }}</th>
                <th scope="col">{{ t('adminClasses.content.colTeacher') }}</th>
                <th scope="col">{{ t('adminClasses.content.colStatus') }}</th>
                <th scope="col" class="col-numeric">{{ t('adminClasses.content.colUpdated') }}</th>
                <th scope="col" class="col-actions">
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
                <td>
                  <span class="admin-cell-title">
                    <strong>{{ row.title }}</strong>
                    <span v-if="row.type === 'LESSON'">{{ row.classTitle }}</span>
                  </span>
                </td>
                <td>
                  <span class="tag">{{
                    row.type === 'CLASS' ? t('adminClasses.content.typeClass') : t('adminClasses.content.typeLesson')
                  }}</span>
                </td>
                <td class="admin-teacher-classes-teacher">
                  {{ row.teacherName }}
                </td>
                <td><ClassStatusPill :status="row.status" :revision-status="row.revisionStatus" plain /></td>
                <td class="col-numeric admin-cell-date">
                  {{ formatShortDate(row.updatedAt) }}
                </td>
                <td class="col-actions">
                  <button
                    type="button"
                    class="btn btn-ghost btn-icon btn-sm"
                    :aria-label="t('adminClasses.content.openLabel', { title: row.title })"
                    @click.stop="open(row)"
                  >
                    <IconGlyph name="chevronRight" :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="admin-sheet-foot">
          <PaginationBar
            :current-page="page + 1"
            :total-pages="totalPages"
            :label="t('adminClasses.content.pagination')"
            :info="t('adminClasses.content.showing', range)"
            icon-only
            @update:current-page="goToPage($event - 1)"
          />
        </div>
      </template>
      <div v-else class="admin-sheet-body">
        <StatePanel
          tone="empty"
          icon="search"
          :title="t('adminClasses.content.emptyTitle')"
          :message="t('adminClasses.content.emptyMessage')"
        />
      </div>
    </section>

    <AppOverlay
      variant="drawer"
      :open="detail !== null"
      :title="detail?.row.title ?? ''"
      :subtitle="
        detail
          ? t('adminClasses.content.detail.teacherLine', {
              teacher: detail.row.teacherName,
              date: formatShortDate(detail.row.updatedAt),
            })
          : ''
      "
      :busy="busy"
      test-id="content-detail"
      @close="detail = null"
    >
      <template v-if="detail">
        <div class="admin-reader-tags">
          <span class="tag">{{
            detail.row.type === 'CLASS' ? t('adminClasses.content.typeClass') : t('adminClasses.content.typeLesson')
          }}</span>
          <ClassStatusPill :status="detail.row.status" :revision-status="detail.row.revisionStatus" />
        </div>
        <p class="admin-teacher-classes-facts">
          <template v-if="detail.row.type === 'CLASS'">
            {{
              t('adminClasses.content.detail.classFacts', {
                units: t('classes.units', detail.unitCount),
                lessons: t('classes.lessons', detail.lessonCount),
                students: t('adminClasses.content.detail.students', detail.studentCount),
              })
            }}
          </template>
          <template v-else>
            {{ detail.row.classTitle }} ·
            {{
              t('adminClasses.content.detail.lessonFacts', {
                unit: detail.unitTitle ?? '',
                activities: t('classes.activities', detail.activityCount),
                questions: t('classes.questions', detail.questionCount),
              })
            }}
          </template>
        </p>
        <section v-if="detail.reviewNote" class="admin-drawer-section" aria-labelledby="content-detail-note">
          <h3 id="content-detail-note" class="admin-reader-label">
            {{ t('adminClasses.content.detail.reviewNote') }}
          </h3>
          <p class="callout is-warning">
            <IconGlyph name="info" :size="18" />
            <span
              >{{ detail.reviewNoteBy ? `${detail.reviewNoteBy}, ${formatShortDate(detail.reviewNoteAt)}: ` : ''
              }}{{ detail.reviewNote }}</span
            >
          </p>
        </section>
        <section class="admin-drawer-section" aria-labelledby="content-detail-history">
          <h3 id="content-detail-history" class="admin-reader-label">{{ t('classHistory.title') }}</h3>
          <ContentHistory :events="detail.history" />
        </section>
      </template>
      <template v-if="detail" #footer>
        <RouterLink
          v-if="detail.row.status === 'SUBMITTED' || detail.row.revisionStatus === 'SUBMITTED'"
          class="btn btn-secondary"
          :to="{ name: 'admin-content-review' }"
        >
          {{ t('adminClasses.content.detail.openInReview') }}
        </RouterLink>
        <button v-if="detail.canRestore" type="button" class="btn btn-primary" @click="confirm = 'restore'">
          <IconGlyph name="refresh" :size="16" />{{ t('adminClasses.content.detail.restore') }}
        </button>
        <button
          v-if="detail.canArchive"
          type="button"
          class="btn btn-danger-soft"
          data-testid="admin-archive"
          @click="confirm = 'archive'"
        >
          <IconGlyph name="archive" :size="16" />{{ t('adminClasses.content.detail.archive') }}
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="confirm !== null"
      :title="
        confirm === 'archive'
          ? t('adminClasses.content.archiveTitle', { title: detail?.row.title ?? '' })
          : t('adminClasses.content.restoreTitle', { title: detail?.row.title ?? '' })
      "
      :message="confirmMessage"
      :confirm-label="
        confirm === 'archive' ? t('adminClasses.content.detail.archive') : t('adminClasses.content.detail.restore')
      "
      :tone="confirm === 'archive' ? 'danger' : 'primary'"
      :busy="busy"
      @cancel="confirm = null"
      @confirm="applyConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { adminClassesApi } from '@/api/classes'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import ClassStatusPill from '@/components/classes/ClassStatusPill.vue'
import ContentHistory from '@/components/classes/ContentHistory.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import { classHistoryMessages } from '@/plugins/classHistoryMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { TeacherContentDetail, TeacherContentRow } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import '@/styles/classes.css'
import '@/styles/ops-admin.css'

const { t } = useLocalMessages({
  adminClasses: adminClassesMessages,
  classHistory: classHistoryMessages,
  classes: classesMessages,
})
const route = useRoute()
const { actionError, actionSuccess, runAction, showError } = useActionFeedback()

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

const { searchTerm: keyword, keyword: debouncedKeyword } = useDebouncedSearch()
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
  await runAction(t('adminClasses.content.actionError'), async () => {
    detail.value =
      action === 'archive'
        ? await adminClassesApi.archive(current.row.type, current.row.id)
        : await adminClassesApi.restore(current.row.type, current.row.id)
    await resource.reload()
    const values = { title: current.row.title, teacher: current.row.teacherName }
    return action === 'archive'
      ? t('adminClasses.content.archived', values)
      : t('adminClasses.content.restored', values)
  })
  busy.value = false
  confirm.value = null
}
</script>
