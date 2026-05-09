<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { MatchingGamePlay } from '../../types/learning'

const route = useRoute()
const { t } = useI18n()
const loading = ref(true)
const submitting = ref(false)
const game = ref<MatchingGamePlay | null>(null)
const selections = reactive<Record<number, string>>({})
const resultMessage = ref('')
const errorMessage = ref('')

const gameId = computed(() => Number(route.params.gameId))

async function loadGame() {
  loading.value = true
  try {
    game.value = await studentLearningService.getMatchingGame(gameId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

async function submitGame() {
  if (!game.value) {
    return
  }

  submitting.value = true
  try {
    const result = await studentLearningService.submitMatchingGame(gameId.value, {
      pairs: game.value.items.map((item) => ({
        itemId: item.id,
        submittedRightText: selections[item.id] ?? '',
      })),
    })
    resultMessage.value = t('learning.matchingResultMessage', {
      correct: result.correctPairs,
      total: result.totalPairs,
      points: result.earnedPoints,
    })
    ElMessage.success(result.completed ? t('learning.greatJob') : t('learning.keepTrying'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('learning.messages.matchingSubmitFailed'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadGame()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.matchingEyebrow')"
      :title="game?.title || t('learning.matchingPlayTitle')"
      :description="t('learning.matchingPlayDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <div v-if="game" class="dashboard-stack">
          <el-card v-for="item in game.items" :key="item.id" class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('learning.matchThis') }}</p>
                <h2>{{ item.leftText }}</h2>
              </div>
            </div>
            <el-input v-model="selections[item.id]" :placeholder="t('learning.typeMatchingMeaning')" />
          </el-card>

          <div class="student-practice-card__actions">
            <el-button type="primary" round :loading="submitting" @click="submitGame">{{ t('learning.submitMatches') }}</el-button>
          </div>

          <el-alert v-if="resultMessage" :title="resultMessage" type="success" show-icon :closable="false" />
        </div>
      </template>
    </el-skeleton>
  </div>
</template>
