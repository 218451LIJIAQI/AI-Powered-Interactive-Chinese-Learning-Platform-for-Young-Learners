<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherContentService } from '../../services/teacherContentService'
import type { LessonActivityFormPayload, LessonActivityItem, LessonActivityType } from '../../types/contentManagement'

const props = defineProps<{
  lessonId: number
  activities: LessonActivityItem[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const { t } = useI18n()
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<LessonActivityFormPayload>({
  title: '',
  activityType: 'TEXT_CONTENT',
  instruction: '',
  description: '',
  displayOrder: 0,
  placeholderContent: '',
})

const activityTypes: LessonActivityType[] = [
  'FLASHCARD',
  'QUIZ',
  'MATCHING',
  'LISTENING',
  'TRACING',
  'DRAG_DROP',
  'STORY',
  'AI_HELP',
  'TEXT_CONTENT',
  'VIDEO_PLACEHOLDER',
]

function resetForm() {
  editingId.value = null
  form.title = ''
  form.activityType = 'TEXT_CONTENT'
  form.instruction = ''
  form.description = ''
  form.displayOrder = props.activities.length + 1
  form.placeholderContent = ''
}

function startEdit(activity: LessonActivityItem) {
  editingId.value = activity.id
  form.title = activity.title
  form.activityType = activity.activityType
  form.instruction = activity.instruction ?? ''
  form.description = activity.description ?? ''
  form.displayOrder = activity.displayOrder
  form.placeholderContent = activity.placeholderContent ?? ''
}

async function saveActivity() {
  if (props.disabled) {
    ElMessage.warning(t('content.teacherApprovalRequired'))
    return
  }

  if (!form.title.trim()) {
    ElMessage.error(t('content.validation.titleRequired'))
    return
  }

  saving.value = true
  try {
    const payload: LessonActivityFormPayload = {
      title: form.title.trim(),
      activityType: form.activityType,
      instruction: form.instruction?.trim() || undefined,
      description: form.description?.trim() || undefined,
      displayOrder: form.displayOrder ?? 0,
      placeholderContent: form.placeholderContent?.trim() || undefined,
    }

    if (editingId.value) {
      await teacherContentService.updateLessonActivity(editingId.value, payload)
      ElMessage.success(t('content.messages.saved'))
    } else {
      await teacherContentService.createLessonActivity(props.lessonId, payload)
      ElMessage.success(t('content.messages.activityCreated'))
    }

    resetForm()
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function archiveActivity(activityId: number) {
  if (props.disabled) {
    ElMessage.warning(t('content.teacherApprovalRequired'))
    return
  }

  try {
    await teacherContentService.archiveLessonActivity(activityId)
    ElMessage.success(t('content.messages.activityArchived'))
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

resetForm()
</script>

<template>
  <div class="dashboard-panel lesson-activity-section">
    <div class="panel-header">
      <div>
        <p class="section-label">{{ t('content.activityPlaceholders') }}</p>
        <h2>{{ t('content.activityPlaceholderTitle') }}</h2>
      </div>
    </div>

    <div class="content-inline-form">
      <el-input v-model="form.title" :placeholder="t('content.fields.activityTitle')" :disabled="disabled" />
      <el-select v-model="form.activityType" :disabled="disabled">
        <el-option v-for="type in activityTypes" :key="type" :label="type" :value="type" />
      </el-select>
      <el-input v-model="form.instruction" :placeholder="t('content.fields.instruction')" :disabled="disabled" />
      <el-input-number v-model="form.displayOrder" :min="0" :disabled="disabled" />
      <el-button type="primary" :loading="saving" :disabled="disabled" @click="saveActivity">
        {{ editingId ? t('content.save') : t('content.addActivity') }}
      </el-button>
      <el-button v-if="editingId" plain :disabled="disabled" @click="resetForm">
        {{ t('common.cancel') }}
      </el-button>
    </div>

    <el-input
      v-model="form.description"
      type="textarea"
      :rows="2"
      :placeholder="t('content.fields.activityDescription')"
      :disabled="disabled"
    />
    <el-input
      v-model="form.placeholderContent"
      type="textarea"
      :rows="3"
      :placeholder="t('content.fields.placeholderContent')"
      :disabled="disabled"
    />

    <el-empty v-if="!activities.length" :description="t('content.noActivities')" />

    <div v-else class="stacked-summary-list">
      <article v-for="activity in activities" :key="activity.id" class="summary-row">
        <div>
          <strong>{{ activity.title }}</strong>
          <p>{{ activity.activityType }} - {{ activity.instruction || t('content.noInstruction') }}</p>
        </div>
        <div class="table-actions">
          <el-button plain :disabled="disabled" @click="startEdit(activity)">
            {{ t('content.edit') }}
          </el-button>
          <el-button type="danger" plain :disabled="disabled" @click="archiveActivity(activity.id)">
            {{ t('content.archive') }}
          </el-button>
        </div>
      </article>
    </div>
  </div>
</template>
