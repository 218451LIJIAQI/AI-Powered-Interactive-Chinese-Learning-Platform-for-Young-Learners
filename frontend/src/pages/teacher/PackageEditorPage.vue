<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { useNumericRouteParam } from '../../composables/useRouteParam'
import { publicContentService } from '../../services/publicContentService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type {
  CoursePackageFormPayload,
  CoursePackageItem,
  LearningCategory,
  LearningLevel,
  LearningModuleItem,
  LessonItem,
  PackageItemFormPayload,
} from '../../types/contentManagement'

const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()

const routePackageId = useNumericRouteParam('id')
const packageId = computed(() => (Number.isFinite(routePackageId.value) ? routePackageId.value : null))
const isEditMode = computed(() => packageId.value !== null)
const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const loading = ref(true)
const saving = ref(false)
const itemSaving = ref(false)
const formRef = ref<FormInstance | null>(null)
const levels = ref<LearningLevel[]>([])
const categories = ref<LearningCategory[]>([])
const modules = ref<LearningModuleItem[]>([])
const lessons = ref<LessonItem[]>([])
const currentPackage = ref<CoursePackageItem | null>(null)

const form = reactive<CoursePackageFormPayload>({
  title: '',
  description: '',
  levelId: 1,
  categoryId: 1,
  learningType: 'RECORDED',
  accessType: 'FREE',
  price: 0,
  coverImageUrl: '',
})

const rules = computed<FormRules>(() => ({
  title: [
    { required: true, message: t('content.validation.titleRequired'), trigger: 'blur' },
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

const newItem = reactive<PackageItemFormPayload>({
  itemType: 'LESSON',
  itemId: 0,
  displayOrder: 0,
})

const availableItemOptions = computed(() =>
  newItem.itemType === 'MODULE' ? modules.value : lessons.value,
)

watch(
  availableItemOptions,
  (options) => {
    if (!options.some((item) => item.id === newItem.itemId)) {
      newItem.itemId = options[0]?.id ?? 0
    }
  },
  { immediate: true },
)

async function loadEditor() {
  loading.value = true
  try {
    const [levelList, categoryList, moduleList, lessonList] = await Promise.all([
      publicContentService.getLevels(),
      publicContentService.getCategories(),
      teacherContentService.getModules(),
      teacherContentService.getLessons(),
    ])

    levels.value = levelList
    categories.value = categoryList
    modules.value = moduleList.filter((item) => item.contentStatus !== 'ARCHIVED')
    lessons.value = lessonList.filter((item) => item.contentStatus !== 'ARCHIVED')

    if (isEditMode.value && packageId.value) {
      currentPackage.value = await teacherContentService.getPackage(packageId.value)
      form.title = currentPackage.value.title
      form.description = currentPackage.value.description ?? ''
      form.levelId = currentPackage.value.level?.id ?? levelList[0]?.id ?? 1
      form.categoryId = currentPackage.value.category?.id ?? categoryList[0]?.id ?? 1
      form.learningType = currentPackage.value.learningType
      form.accessType = currentPackage.value.accessType
      form.price = currentPackage.value.price ?? 0
      form.coverImageUrl = currentPackage.value.coverImageUrl ?? ''
      newItem.displayOrder = currentPackage.value.packageItems.length + 1
    } else {
      form.levelId = levelList[0]?.id ?? 1
      form.categoryId = categoryList[0]?.id ?? 1
      currentPackage.value = null
    }
  } finally {
    loading.value = false
  }
}

async function savePackage() {
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
    const payload: CoursePackageFormPayload = {
      title: form.title.trim(),
      description: form.description?.trim() || undefined,
      levelId: form.levelId,
      categoryId: form.categoryId,
      learningType: form.learningType,
      accessType: form.accessType,
      price: form.accessType === 'PAID' ? Number(form.price ?? 0) : 0,
      coverImageUrl: form.coverImageUrl?.trim() || undefined,
    }

    const saved = isEditMode.value && packageId.value
      ? await teacherContentService.updatePackage(packageId.value, payload)
      : await teacherContentService.createPackage(payload)

    ElMessage.success(t('content.messages.saved'))

    if (!isEditMode.value) {
      await router.replace({ name: 'teacher-package-edit', params: { id: saved.id } })
    }

    await loadEditor()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function addItem() {
  if (!packageId.value) {
    ElMessage.warning(t('content.messages.savePackageFirst'))
    return
  }

  itemSaving.value = true
  try {
    currentPackage.value = await teacherContentService.addPackageItem(packageId.value, {
      itemType: newItem.itemType,
      itemId: newItem.itemId,
      displayOrder: newItem.displayOrder,
    })
    newItem.displayOrder = currentPackage.value.packageItems.length + 1
    ElMessage.success(t('content.messages.itemAdded'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    itemSaving.value = false
  }
}

async function removeItem(itemId: number) {
  if (!packageId.value) {
    return
  }

  try {
    currentPackage.value = await teacherContentService.removePackageItem(packageId.value, itemId)
    ElMessage.success(t('content.messages.itemRemoved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
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
              <p class="section-label">{{ t('content.packageEditorEyebrow') }}</p>
              <h1>{{ isEditMode ? t('content.editPackage') : t('content.createPackage') }}</h1>
              <p>{{ t('content.packageEditorDescription') }}</p>
            </div>
            <div class="teacher-profile-actions">
              <el-button plain @click="router.push({ name: 'teacher-packages' })">{{ t('content.backToPackages') }}</el-button>
              <el-button type="primary" :loading="saving" :disabled="teacherLocked" @click="savePackage">
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
              <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit :disabled="teacherLocked" />
            </el-form-item>
            <div class="content-form-grid">
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
              <el-form-item :label="t('content.fields.coverImage')">
                <el-input v-model="form.coverImageUrl" :disabled="teacherLocked" />
              </el-form-item>
            </div>
          </el-form>
        </el-card>

        <el-card class="dashboard-panel" shadow="hover">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('content.packageItems') }}</p>
              <h2>{{ t('content.packageItemsTitle') }}</h2>
            </div>
          </div>

          <div class="content-inline-form">
            <el-select v-model="newItem.itemType" :disabled="teacherLocked">
              <el-option label="MODULE" value="MODULE" />
              <el-option label="LESSON" value="LESSON" />
            </el-select>
            <el-select v-model="newItem.itemId" :disabled="teacherLocked || !availableItemOptions.length">
              <el-option v-for="item in availableItemOptions" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
            <el-input-number v-model="newItem.displayOrder" :min="0" :disabled="teacherLocked" />
            <el-button type="primary" :loading="itemSaving" :disabled="teacherLocked || !isEditMode" @click="addItem">
              {{ t('content.addItem') }}
            </el-button>
          </div>

          <el-empty v-if="!currentPackage?.packageItems.length" :description="t('content.noPackageItems')" />
          <div v-else class="stacked-summary-list">
            <article v-for="item in currentPackage?.packageItems" :key="item.id" class="summary-row">
              <div>
                <strong>{{ item.titleSnapshot || `${item.itemType} #${item.itemId}` }}</strong>
                <p>{{ item.itemType }} - {{ t('content.displayOrder') }} {{ item.displayOrder }}</p>
              </div>
              <el-button type="danger" plain :disabled="teacherLocked" @click="removeItem(item.id)">
                {{ t('content.removeItem') }}
              </el-button>
            </article>
          </div>
        </el-card>
      </template>
    </el-skeleton>
  </div>
</template>
