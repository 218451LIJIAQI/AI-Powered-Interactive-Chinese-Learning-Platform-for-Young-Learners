<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { FlashcardItem } from '../../types/learning'

const route = useRoute()
const { t } = useI18n()

const loading = ref(true)
const errorMessage = ref('')
const cards = ref<FlashcardItem[]>([])
const currentIndex = ref(0)
const flipped = ref(false)
const completing = ref(false)
const completionMessage = ref('')

const lessonId = computed(() => Number(route.params.lessonId))
const currentCard = computed(() => cards.value[currentIndex.value] ?? null)
const progressPercent = computed(() =>
  cards.value.length ? Math.round(((currentIndex.value + 1) / cards.value.length) * 100) : 0,
)

async function loadCards() {
  loading.value = true
  try {
    cards.value = await studentLearningService.getFlashcards(lessonId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

function previousCard() {
  flipped.value = false
  currentIndex.value = Math.max(0, currentIndex.value - 1)
}

function nextCard() {
  flipped.value = false
  currentIndex.value = Math.min(cards.value.length - 1, currentIndex.value + 1)
}

async function completeCurrentCard() {
  if (!currentCard.value) {
    return
  }

  completing.value = true
  try {
    const result = await studentLearningService.practiceFlashcard(currentCard.value.id)
    if (result.earnedPoints > 0) {
      ElMessage.success(t('learning.messages.pointsEarned', { points: result.earnedPoints }))
    } else {
      ElMessage.success(t('dashboard.completed'))
    }

    completionMessage.value = result.firstCompletion
      ? t('learning.flashcardCelebration')
      : t('learning.practiceSaved')

    if (currentIndex.value < cards.value.length - 1) {
      nextCard()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    completing.value = false
  }
}

onMounted(() => {
  loadCards()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.flashcardsEyebrow')"
      :title="t('learning.flashcardPracticeTitle')"
      :description="t('learning.flashcardPracticeDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="6">
      <template #default>
        <el-card class="student-practice-card" shadow="hover">
          <div class="student-practice-card__header">
            <div>
              <p class="section-label">{{ t('learning.flashcards') }}</p>
              <h2>{{ currentCard?.chineseText }}</h2>
            </div>
            <el-progress :percentage="progressPercent" :stroke-width="14" color="#3bb3c3" />
          </div>

          <button class="flashcard-surface" type="button" @click="flipped = !flipped">
            <div v-if="!flipped" class="flashcard-face">
              <strong>{{ currentCard?.chineseText }}</strong>
              <span>{{ t('learning.tapToFlip') }}</span>
            </div>
            <div v-else class="flashcard-face flashcard-face--back">
              <strong>{{ currentCard?.englishMeaning }}</strong>
              <p>{{ currentCard?.pinyin }}</p>
              <p v-if="currentCard?.malayMeaning">{{ currentCard?.malayMeaning }}</p>
            </div>
          </button>

          <div class="student-practice-card__actions">
            <el-button round :disabled="currentIndex === 0" @click="previousCard">{{ t('learning.previous') }}</el-button>
            <el-button type="primary" round :loading="completing" @click="completeCurrentCard">
              {{ t('learning.markPractised') }}
            </el-button>
            <el-button round :disabled="currentIndex >= cards.length - 1" @click="nextCard">{{ t('learning.next') }}</el-button>
          </div>

          <el-alert v-if="completionMessage" :title="completionMessage" type="success" show-icon :closable="false" />
        </el-card>

        <el-empty v-if="!cards.length" :description="t('learning.noFlashcardsAvailable')" />
      </template>
    </el-skeleton>
  </div>
</template>
