<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LessonActivityPlaceholderSection from '../../components/content/LessonActivityPlaceholderSection.vue'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { categoryService } from '../../services/categoryService'
import { levelService } from '../../services/levelService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LearningCategory, LearningLevel, LearningModuleItem, LessonActivityItem, LessonFormPayload } from '../../types/contentManagement'

const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()

const routeLessonId = useNumericRouteParam('id')
const lessonId = computed(() => (Number.isFinite(routeLessonId.value) ? routeLessonId.value : null))
const isEditMode = computed(() => lessonId.value !== null)
const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const loading = ref(true)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)
const levels = ref<LearningLevel[]>([])
const categories = ref<LearningCategory[]>([])
const modules = ref<LearningModuleItem[]>([])
const activities = ref<LessonActivityItem[]>([])

const form = reactive<LessonFormPayload>({
  title: '',
  description: '',
  moduleId: null,
  levelId: 1,
  categoryId: 1,
  learningType: 'RECORDED',
  accessType: 'FREE',
  price: 0,
  estimatedDurationMinutes: 10,
  basicContent: '',
  coverImageUrl: '',
})

const rules = computed<FormRules>(() => ({
  title: [
    { required: true, message: t('content.validation.titleRequired'), trigger: 'blur' },
    { max: 200, message: t('content.validation.titleRequired'), trigger: 'blur' },
  ],
  price: [
    {
      validator: (_rule, value: unknown, callback) => {
        if (form.accessType === 'PAID' && (typeof value !== 'number' || value < 0)) {
          callback(new Error(t('content.validation.priceRequired')))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}))

async function loadEditor() {
  loading.value = true
  try {
    const [levelList, categoryList, moduleList] = await Promise.all([
      levelService.getLevels(),
      categoryService.getCategories(),
      teacherContentService.getModules(),
    ])
    levels.value = levelList
    categories.value = categoryList
    modules.value = moduleList.filter((item) => item.contentStatus !== 'ARCHIVED')

    if (isEditMode.value && lessonId.value) {
      const lesson = await teacherContentService.getLesson(lessonId.value)
      form.title = lesson.title
      form.description = lesson.description ?? ''
      form.moduleId = lesson.moduleId
      form.levelId = lesson.level?.id ?? levelList[0]?.id ?? 1
      form.categoryId = lesson.category?.id ?? categoryList[0]?.id ?? 1
      form.learningType = lesson.learningType
      form.accessType = lesson.accessType
      form.price = lesson.price ?? 0
      form.estimatedDurationMinutes = lesson.estimatedDurationMinutes ?? 10
      form.basicContent = lesson.basicContent ?? ''
      form.coverImageUrl = lesson.coverImageUrl ?? ''
      activities.value = lesson.activities
    } else {
      form.levelId = levelList[0]?.id ?? 1
      form.categoryId = categoryList[0]?.id ?? 1
      activities.value = []
    }
  } finally {
    loading.value = false
  }
}

async function saveLesson() {
  if (teacherLocked.value) {
    ElMessage.warning(t('content.teacherApprovalRequired'))
    return
  }

  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const payload: LessonFormPayload = {
      title: form.title.trim(),
      description: form.description?.trim() || undefined,
      moduleId: form.moduleId || null,
      levelId: form.levelId,
      categoryId: form.categoryId,
      learningType: form.learningType,
      accessType: form.accessType,
      price: form.accessType === 'PAID' ? Number(form.price ?? 0) : 0,
      estimatedDurationMinutes: form.estimatedDurationMinutes ?? 0,
      basicContent: form.basicContent?.trim() || undefined,
      coverImageUrl: form.coverImageUrl?.trim() || undefined,
    }

    const saved = isEditMode.value && lessonId.value
      ? await teacherContentService.updateLesson(lessonId.value, payload)
      : await teacherContentService.createLesson(payload)

    ElMessage.success(t('content.messages.saved'))

    if (!isEditMode.value) {
      await router.replace({ name: 'teacher-lesson-edit', params: { id: saved.id } })
    }

    await loadEditor()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function refreshActivities() {
  if (!lessonId.value) {
    return
  }

  activities.value = await teacherContentService.getLessonActivities(lessonId.value)
}

onMounted(() => {
  loadEditor()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="10">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('content.lessonEditorEyebrow') }}</p>
              <h1>{{ isEditMode ? t('content.editLesson') : t('content.createLesson') }}</h1>
              <p>{{ t('content.lessonEditorDescription') }}</p>
            </div>
            <div class="teacher-profile-actions">
              <el-button plain @click="router.push({ name: 'teacher-lessons' })">{{ t('content.backToLessons') }}</el-button>
              <el-button type="primary" :loading="saving" :disabled="teacherLocked" @click="saveLesson">
                {{ t('content.save') }}
              </el-button>
            </div>
          </div>
        </el-card>

        <el-alert v-if="teacherLocked" :title="t('content.teacherApprovalRequired')" type="warning" show-icon :closable="false" />

        <el-card class="dashboard-panel" shadow="hover">
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <el-form-item :label="t('content.fields.title')" prop="title">
              <el-input v-model="form.title" maxlength="200" :disabled="teacherLocked" />
            </el-form-item>

            <el-form-item :label="t('content.fields.description')">
              <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit :disabled="teacherLocked" />
            </el-form-item>

            <div class="content-form-grid">
              <el-form-item :label="t('content.module')">
                <el-select v-model="form.moduleId" clearable :disabled="teacherLocked">
                  <el-option v-for="item in modules" :key="item.id" :label="item.title" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('content.level')">
                <el-select v-model="form.levelId" :disabled="teacherLocked">
                  <el-option v-for="level in levels" :key="level.id" :label="level.name" :value="level.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('content.category')">
                <el-select v-model="form.categoryId" :disabled="teacherLocked">
                  <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('content.learningType')">
                <el-select v-model="form.learningType" :disabled="teacherLocked">
                  <el-option label="LIVE" value="LIVE" />
                  <el-option label="RECORDED" value="RECORDED" />
                  <el-option label="HYBRID" value="HYBRID" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('content.accessType')">
                <el-select v-model="form.accessType" :disabled="teacherLocked">
                  <el-option label="FREE" value="FREE" />
                  <el-option label="PAID" value="PAID" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('content.price')" prop="price">
                <el-input-number v-model="form.price" :min="0" :disabled="teacherLocked || form.accessType === 'FREE'" />
              </el-form-item>
              <el-form-item :label="t('content.fields.duration')">
                <el-input-number v-model="form.estimatedDurationMinutes" :min="0" :disabled="teacherLocked" />
              </el-form-item>
              <el-form-item :label="t('content.fields.coverImage')">
                <el-input v-model="form.coverImageUrl" :disabled="teacherLocked" />
              </el-form-item>
            </div>

            <el-form-item :label="t('content.fields.basicContent')">
              <el-input v-model="form.basicContent" type="textarea" :rows="6" :disabled="teacherLocked" />
            </el-form-item>
          </el-form>
        </el-card>

        <LessonActivityPlaceholderSection
          v-if="lessonId"
          :lesson-id="lessonId"
          :activities="activities"
          :disabled="teacherLocked"
          @refresh="refreshActivities"
        />
      </template>
    </el-skeleton>
  </div>
</template>
