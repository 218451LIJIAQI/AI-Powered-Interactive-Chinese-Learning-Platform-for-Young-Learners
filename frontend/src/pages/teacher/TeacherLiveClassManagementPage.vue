<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import PageHero from '../../components/common/PageHero.vue'
import { liveClassService } from '../../services/liveClassService'
import { teacherContentService } from '../../services/teacherContentService'
import type { CoursePackageItem, LessonItem } from '../../types/contentManagement'
import type { LiveClass, LiveClassEnrolment, LiveClassFormPayload, MeetingPlatform } from '../../types/liveLearning'

const { t } = useI18n()
const route = useRoute()

const loading = ref(true)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const classes = ref<LiveClass[]>([])
const lessons = ref<LessonItem[]>([])
const packages = ref<CoursePackageItem[]>([])
const enrolmentDialogVisible = ref(false)
const enrolmentLoading = ref(false)
const selectedLiveClass = ref<LiveClass | null>(null)
const enrolments = ref<LiveClassEnrolment[]>([])

const form = reactive<LiveClassFormPayload>({
  title: '',
  description: '',
  lessonId: null,
  packageId: null,
  startTime: '',
  endTime: '',
  meetingLink: '',
  meetingPlatform: 'GOOGLE_MEET',
  status: 'SCHEDULED',
  maxParticipants: null,
})

const dialogTitle = computed(() =>
  editingId.value ? t('phase10.editLiveClass') : t('phase10.createLiveClass'),
)

function resetForm() {
  form.title = ''
  form.description = ''
  form.lessonId = null
  form.packageId = null
  form.startTime = ''
  form.endTime = ''
  form.meetingLink = ''
  form.meetingPlatform = 'GOOGLE_MEET'
  form.status = 'SCHEDULED'
  form.maxParticipants = null
  editingId.value = null
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('en-MY') : '-'
}

async function loadData() {
  loading.value = true
  try {
    const [liveClasses, lessonList, packageList] = await Promise.all([
      liveClassService.getTeacherLiveClasses(),
      teacherContentService.getLessons(),
      teacherContentService.getPackages(),
    ])
    classes.value = liveClasses
    lessons.value = lessonList
    packages.value = packageList
    syncRouteState()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(item: LiveClass) {
  editingId.value = item.id
  form.title = item.title
  form.description = item.description || ''
  form.lessonId = item.lesson?.lessonId ?? null
  form.packageId = item.coursePackage?.packageId ?? null
  form.startTime = item.startTime || ''
  form.endTime = item.endTime || ''
  form.meetingLink = item.meetingLink || ''
  form.meetingPlatform = (item.meetingPlatform || 'GOOGLE_MEET') as MeetingPlatform
  form.status = item.liveClassStatus || 'SCHEDULED'
  form.maxParticipants = item.maxParticipants
  dialogVisible.value = true
}

async function openEnrolments(item: LiveClass) {
  selectedLiveClass.value = item
  enrolmentDialogVisible.value = true
  enrolmentLoading.value = true
  try {
    enrolments.value = await liveClassService.getLiveClassEnrolments(item.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    enrolmentLoading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      await liveClassService.updateLiveClass(editingId.value, form)
      ElMessage.success(t('phase10.messages.liveClassUpdated'))
    } else {
      await liveClassService.createLiveClass(form)
      ElMessage.success(t('phase10.messages.liveClassCreated'))
    }
    dialogVisible.value = false
    resetForm()
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function cancelClass(item: LiveClass) {
  try {
    await liveClassService.cancelLiveClass(item.id)
    ElMessage.success(t('phase10.messages.liveClassCancelled'))
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

function syncRouteState() {
  if (route.name === 'teacher-live-class-create') {
    openCreate()
    return
  }

  if (route.name === 'teacher-live-class-edit' && route.params.id) {
    const liveClass = classes.value.find((item) => item.id === Number(route.params.id))
    if (liveClass) {
      openEdit(liveClass)
    }
  }
}

watch(
  () => [route.name, route.params.id, classes.value.length],
  () => {
    if (!classes.value.length && route.name !== 'teacher-live-class-create') {
      return
    }
    syncRouteState()
  },
)

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase10.teacherLiveClassesEyebrow')"
      :title="t('phase10.teacherLiveClassesTitle')"
      :description="t('phase10.teacherLiveClassesDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('phase10.liveClasses') }}</p>
          <h2>{{ t('phase10.teacherLiveClassesTitle') }}</h2>
        </div>
        <el-button type="primary" @click="openCreate">{{ t('phase10.createLiveClass') }}</el-button>
      </div>

      <el-table v-loading="loading" :data="classes" class="phase-table">
        <el-table-column prop="title" :label="t('phase10.liveClasses')" min-width="220" />
        <el-table-column :label="t('phase10.startTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('phase10.endTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="liveClassStatus" :label="t('commerce.status')" min-width="140" />
        <el-table-column prop="enrolledCount" :label="t('phase10.enrolledStudents')" min-width="140" />
        <el-table-column width="360">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain type="primary" size="small" @click="openEdit(row)">{{ t('content.edit') }}</el-button>
              <el-button plain size="small" @click="openEnrolments(row)">{{ t('phase10.viewEnrolments') }}</el-button>
              <el-button plain type="danger" size="small" @click="cancelClass(row)">{{ t('phase10.cancelClass') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
      <el-form label-position="top">
        <el-form-item :label="t('content.fields.title')">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item :label="t('content.fields.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('content.lesson')">
          <el-select v-model="form.lessonId" class="w-full" clearable>
            <el-option v-for="lesson in lessons" :key="lesson.id" :label="lesson.title" :value="lesson.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('content.package')">
          <el-select v-model="form.packageId" class="w-full" clearable>
            <el-option v-for="item in packages" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="content-toolbar">
          <el-form-item :label="t('phase10.startTime')" class="content-filter content-filter--wide">
            <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="w-full" />
          </el-form-item>
          <el-form-item :label="t('phase10.endTime')" class="content-filter content-filter--wide">
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="w-full" />
          </el-form-item>
        </div>
        <div class="content-toolbar">
          <el-form-item :label="t('phase10.meetingPlatform')" class="content-filter">
            <el-select v-model="form.meetingPlatform" class="w-full">
              <el-option label="ZOOM" value="ZOOM" />
              <el-option label="GOOGLE_MEET" value="GOOGLE_MEET" />
              <el-option label="MICROSOFT_TEAMS" value="MICROSOFT_TEAMS" />
              <el-option label="OTHER" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('commerce.status')" class="content-filter">
            <el-select v-model="form.status" class="w-full">
              <el-option label="SCHEDULED" value="SCHEDULED" />
              <el-option label="ONGOING" value="ONGOING" />
              <el-option label="COMPLETED" value="COMPLETED" />
              <el-option label="CANCELLED" value="CANCELLED" />
              <el-option label="RESCHEDULED" value="RESCHEDULED" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('phase10.maxParticipants')" class="content-filter">
            <el-input-number v-model="form.maxParticipants" :min="1" />
          </el-form-item>
        </div>
        <el-form-item :label="t('phase10.meetingLink')">
          <el-input v-model="form.meetingLink" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="table-actions">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="save">{{ t('content.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="enrolmentDialogVisible" :title="t('phase10.enrolmentListTitle')" width="760px">
      <el-table v-loading="enrolmentLoading" :data="enrolments" class="phase-table">
        <el-table-column prop="studentName" :label="t('phase10.studentLabel')" min-width="220" />
        <el-table-column prop="studentEmail" :label="t('auth.fields.email')" min-width="220" />
        <el-table-column prop="enrolmentStatus" :label="t('phase10.enrolment')" min-width="140" />
        <el-table-column prop="enrolledAt" :label="t('phase10.enrolledAt')" min-width="180" />
      </el-table>
      <el-empty v-if="!enrolmentLoading && !enrolments.length" :description="t('phase10.emptyEnrolments')" />
    </el-dialog>
  </div>
</template>
