<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherActivityService } from '../../services/teacherActivityService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LessonItem } from '../../types/contentManagement'
import type { MatchingGame, MatchingGameFormPayload, MatchingGameItemFormPayload } from '../../types/learning'

const { t } = useI18n()
const authStore = useAuthStore()

const loadingLessons = ref(true)
const loadingGames = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const selectedLessonId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const games = ref<MatchingGame[]>([])
const items = ref<MatchingGameItemFormPayload[]>([])

const form = reactive<MatchingGameFormPayload>({
  title: '',
  items: [],
})

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const lessonEditable = computed(() =>
  selectedLesson.value ? ['DRAFT', 'REJECTED'].includes(selectedLesson.value.contentStatus) : false,
)

function createItem(order: number): MatchingGameItemFormPayload {
  return {
    leftText: '',
    rightText: '',
    leftImageUrl: '',
    rightImageUrl: '',
    pinyin: '',
    displayOrder: order,
  }
}

function resetForm() {
  editingId.value = null
  form.title = ''
  items.value = [createItem(1), createItem(2)]
}

async function loadLessons() {
  loadingLessons.value = true

  try {
    lessons.value = (await teacherContentService.getLessons()).filter((lesson) => lesson.contentStatus !== 'ARCHIVED')
    if (!selectedLessonId.value && lessons.value.length) {
      selectedLessonId.value = lessons.value.find((lesson) => ['DRAFT', 'REJECTED'].includes(lesson.contentStatus))?.id
        ?? lessons.value[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingLessons.value = false
  }
}

async function loadGames() {
  if (!selectedLessonId.value) {
    games.value = []
    return
  }

  loadingGames.value = true

  try {
    games.value = await teacherActivityService.getMatchingGames(selectedLessonId.value)
  } catch (error) {
    games.value = []
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingGames.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(game: MatchingGame) {
  editingId.value = game.id
  form.title = game.title
  items.value = game.items.map((item) => ({
    leftText: item.leftText,
    rightText: item.rightText,
    leftImageUrl: item.leftImageUrl ?? '',
    rightImageUrl: item.rightImageUrl ?? '',
    pinyin: item.pinyin ?? '',
    displayOrder: item.displayOrder,
  }))
  dialogVisible.value = true
}

function addItem() {
  items.value.push(createItem(items.value.length + 1))
}

function removeItem(index: number) {
  if (items.value.length <= 2) {
    return
  }

  items.value.splice(index, 1)
  items.value.forEach((item, itemIndex) => {
    item.displayOrder = itemIndex + 1
  })
}

async function saveGame() {
  if (!selectedLessonId.value || teacherLocked.value || !lessonEditable.value) {
    ElMessage.warning(t('learning.teacherActivityLocked'))
    return
  }

  if (!form.title.trim()) {
    ElMessage.error(t('content.validation.titleRequired'))
    return
  }

  if (items.value.some((item) => !item.leftText.trim() || !item.rightText.trim())) {
    ElMessage.error(t('learning.validation.matchingRequired'))
    return
  }

  saving.value = true

  try {
    const payload: MatchingGameFormPayload = {
      title: form.title.trim(),
      items: items.value.map((item, index) => ({
        leftText: item.leftText.trim(),
        rightText: item.rightText.trim(),
        leftImageUrl: item.leftImageUrl?.trim() || undefined,
        rightImageUrl: item.rightImageUrl?.trim() || undefined,
        pinyin: item.pinyin?.trim() || undefined,
        displayOrder: Number(item.displayOrder ?? index + 1),
      })),
    }

    if (editingId.value) {
      await teacherActivityService.updateMatchingGame(editingId.value, payload)
    } else {
      await teacherActivityService.createMatchingGame(selectedLessonId.value, payload)
    }

    ElMessage.success(t('content.messages.saved'))
    dialogVisible.value = false
    resetForm()
    await loadGames()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function deleteGame(gameId: number) {
  try {
    await teacherActivityService.deleteMatchingGame(gameId)
    ElMessage.success(t('learning.messages.matchingDeleted'))
    await loadGames()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

watch(selectedLessonId, async () => {
  dialogVisible.value = false
  resetForm()
  await loadGames()
})

onMounted(async () => {
  resetForm()
  await loadLessons()
  await loadGames()
})
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('learning.matchingEyebrow') }}</p>
          <h1>{{ t('learning.matchingTitle') }}</h1>
          <p>{{ t('learning.matchingDescription') }}</p>
        </div>
        <div class="teacher-profile-actions">
          <el-select
            v-model="selectedLessonId"
            class="content-filter"
            :placeholder="t('learning.selectLesson')"
            :loading="loadingLessons"
          >
            <el-option
              v-for="lesson in lessons"
              :key="lesson.id"
              :label="`${lesson.title} (${lesson.contentStatus})`"
              :value="lesson.id"
            />
          </el-select>
          <el-button type="primary" :disabled="teacherLocked || !lessonEditable || !selectedLessonId" @click="openCreateDialog">
            {{ t('learning.addMatchingGame') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert v-if="teacherLocked" :title="t('content.teacherApprovalRequired')" type="warning" show-icon :closable="false" />
    <el-alert
      v-else-if="selectedLesson && !lessonEditable"
      :title="t('learning.lessonStatusLocked')"
      type="info"
      show-icon
      :closable="false"
    />

    <section class="dashboard-grid">
      <el-card v-for="game in games" :key="game.id" class="dashboard-panel" shadow="hover">
        <div class="panel-header">
          <div>
            <p class="section-label">{{ t('learning.matchingGames') }}</p>
            <h2>{{ game.title }}</h2>
          </div>
          <el-tag round>{{ game.items.length }} {{ t('learning.pairs') }}</el-tag>
        </div>

        <div class="stacked-summary-list">
          <article v-for="item in game.items.slice(0, 3)" :key="item.id" class="summary-row">
            <div>
              <strong>{{ item.leftText }}</strong>
              <p>{{ item.rightText }}</p>
            </div>
            <el-tag round>{{ item.displayOrder }}</el-tag>
          </article>
        </div>

        <div class="table-actions">
          <el-button plain size="small" :disabled="teacherLocked || !lessonEditable" @click="openEditDialog(game)">
            {{ t('content.edit') }}
          </el-button>
          <el-button plain type="danger" size="small" :disabled="teacherLocked || !lessonEditable" @click="deleteGame(game.id)">
            {{ t('content.archive') }}
          </el-button>
        </div>
      </el-card>
    </section>

    <el-empty v-if="!loadingGames && !games.length" :description="t('learning.noMatchingGames')" />

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('learning.editMatchingGame') : t('learning.addMatchingGame')"
      width="820px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('content.fields.title')">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>

        <div class="stacked-summary-list">
          <section v-for="(item, index) in items" :key="`${index}-${item.displayOrder}`" class="workspace-placeholder__panel">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('learning.pair') }} {{ index + 1 }}</p>
                <h2>{{ t('learning.fields.matchingLeftText') }} / {{ t('learning.fields.matchingRightText') }}</h2>
              </div>
              <el-button plain type="danger" :disabled="items.length <= 2" @click="removeItem(index)">
                {{ t('learning.removePair') }}
              </el-button>
            </div>

            <div class="content-form-grid">
              <el-form-item :label="t('learning.fields.matchingLeftText')">
                <el-input v-model="item.leftText" maxlength="255" />
              </el-form-item>
              <el-form-item :label="t('learning.fields.matchingRightText')">
                <el-input v-model="item.rightText" maxlength="255" />
              </el-form-item>
              <el-form-item :label="t('learning.fields.pinyin')">
                <el-input v-model="item.pinyin" maxlength="255" />
              </el-form-item>
              <el-form-item :label="t('content.displayOrder')">
                <el-input-number v-model="item.displayOrder" :min="0" />
              </el-form-item>
              <el-form-item :label="t('learning.fields.leftImageUrl')">
                <el-input v-model="item.leftImageUrl" maxlength="255" />
              </el-form-item>
              <el-form-item :label="t('learning.fields.rightImageUrl')">
                <el-input v-model="item.rightImageUrl" maxlength="255" />
              </el-form-item>
            </div>
          </section>
        </div>

        <el-button plain @click="addItem">{{ t('learning.addPair') }}</el-button>
      </el-form>

      <template #footer>
        <div class="table-actions">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="saveGame">{{ t('content.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
