<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { categoryService } from '../../services/categoryService'
import { levelService } from '../../services/levelService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LearningCategory, LearningLevel, LearningModuleItem, ModuleFormPayload } from '../../types/contentManagement'

const { t } = useI18n()
const authStore = useAuthStore()

const loading = ref(true)
const saving = ref(false)
const showDialog = ref(false)
const editingId = ref<number | null>(null)
const modules = ref<LearningModuleItem[]>([])
const levels = ref<LearningLevel[]>([])
const categories = ref<LearningCategory[]>([])

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')

const form = reactive<ModuleFormPayload>({
  title: '',
  description: '',
  levelId: 1,
  categoryId: 1,
  accessType: 'FREE',
  price: 0,
  coverImageUrl: '',
})

function resetForm() {
  editingId.value = null
  form.title = ''
  form.description = ''
  form.levelId = levels.value[0]?.id ?? 1
  form.categoryId = categories.value[0]?.id ?? 1
  form.accessType = 'FREE'
  form.price = 0
  form.coverImageUrl = ''
}

function openCreateDialog() {
  resetForm()
  showDialog.value = true
}

function openEditDialog(item: LearningModuleItem) {
  editingId.value = item.id
  form.title = item.title
  form.description = item.description ?? ''
  form.levelId = item.level?.id ?? levels.value[0]?.id ?? 1
  form.categoryId = item.category?.id ?? categories.value[0]?.id ?? 1
  form.accessType = item.accessType
  form.price = item.price ?? 0
  form.coverImageUrl = item.coverImageUrl ?? ''
  showDialog.value = true
}

async function loadPage() {
  loading.value = true
  try {
    const [moduleList, levelList, categoryList] = await Promise.all([
      teacherContentService.getModules(),
      levelService.getLevels(),
      categoryService.getCategories(),
    ])
    modules.value = moduleList
    levels.value = levelList
    categories.value = categoryList
    if (!editingId.value) {
      resetForm()
    }
  } finally {
    loading.value = false
  }
}

async function saveModule() {
  if (teacherLocked.value) {
    ElMessage.warning(t('content.teacherApprovalRequired'))
    return
  }

  if (!form.title.trim()) {
    ElMessage.error(t('content.validation.titleRequired'))
    return
  }

  saving.value = true
  try {
    const payload: ModuleFormPayload = {
      title: form.title.trim(),
      description: form.description?.trim() || undefined,
      levelId: form.levelId,
      categoryId: form.categoryId,
      accessType: form.accessType,
      price: form.accessType === 'PAID' ? Number(form.price ?? 0) : 0,
      coverImageUrl: form.coverImageUrl?.trim() || undefined,
    }

    if (editingId.value) {
      await teacherContentService.updateModule(editingId.value, payload)
    } else {
      await teacherContentService.createModule(payload)
    }

    ElMessage.success(t('content.messages.saved'))
    showDialog.value = false
    await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function submitModule(id: number) {
  try {
    await teacherContentService.submitModule(id)
    ElMessage.success(t('content.messages.submitted'))
    await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function archiveModule(id: number) {
  try {
    await teacherContentService.archiveModule(id)
    ElMessage.success(t('content.messages.archived'))
    await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

onMounted(() => {
  loadPage()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('content.moduleManagementEyebrow') }}</p>
          <h1>{{ t('content.moduleManagementTitle') }}</h1>
          <p>{{ t('content.moduleManagementDescription') }}</p>
        </div>
        <div class="teacher-profile-actions">
          <el-button type="primary" :disabled="teacherLocked" @click="openCreateDialog">
            {{ t('content.createModule') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="teacherLocked"
      :title="t('content.teacherApprovalRequired')"
      type="warning"
      show-icon
      :closable="false"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="modules" class="phase-table">
        <el-table-column prop="title" :label="t('content.fields.title')" min-width="220" />
        <el-table-column :label="t('content.level')" min-width="140">
          <template #default="{ row }">{{ row.level?.name || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('content.category')" min-width="140">
          <template #default="{ row }">{{ row.category?.name || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('content.accessType')" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.accessType === 'PAID' ? 'warning' : 'success'" round>{{ row.accessType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round>{{ row.contentStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.rejectionReason')" min-width="220">
          <template #default="{ row }">{{ row.rejectionReason || '-' }}</template>
        </el-table-column>
        <el-table-column width="260">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="openEditDialog(row)">
                {{ t('content.edit') }}
              </el-button>
              <el-button plain type="primary" size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="submitModule(row.id)">
                {{ t('content.submitForReview') }}
              </el-button>
              <el-button plain type="danger" size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="archiveModule(row.id)">
                {{ t('content.archive') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !modules.length" :description="t('content.noModules')" />
    </el-card>

    <el-dialog v-model="showDialog" :title="editingId ? t('content.editModule') : t('content.createModule')" width="680px">
      <el-form label-position="top">
        <el-form-item :label="t('content.fields.title')">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item :label="t('content.fields.description')">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <div class="content-form-grid">
          <el-form-item :label="t('content.level')">
            <el-select v-model="form.levelId">
              <el-option v-for="level in levels" :key="level.id" :label="level.name" :value="level.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('content.category')">
            <el-select v-model="form.categoryId">
              <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('content.accessType')">
            <el-select v-model="form.accessType">
              <el-option label="FREE" value="FREE" />
              <el-option label="PAID" value="PAID" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('content.price')">
            <el-input-number v-model="form.price" :min="0" :disabled="form.accessType === 'FREE'" />
          </el-form-item>
        </div>
        <el-form-item :label="t('content.fields.coverImage')">
          <el-input v-model="form.coverImageUrl" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveModule">{{ t('content.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
