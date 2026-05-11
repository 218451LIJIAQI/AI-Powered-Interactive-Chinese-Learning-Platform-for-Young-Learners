<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import KidConfetti from '../../components/common/KidConfetti.vue'
import PageHero from '../../components/common/PageHero.vue'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { studentLearningService } from '../../services/studentLearningService'
import type { FlashcardItem } from '../../types/learning'

const { t } = useI18n()

const loading = ref(true)
const errorMessage = ref('')
const cards = ref<FlashcardItem[]>([])
const currentIndex = ref(0)
const flipped = ref(false)
const completing = ref(false)
const completionMessage = ref('')
const confettiTrigger = ref(0)
let advanceTimeoutId: number | null = null

const lessonId = useNumericRouteParam('lessonId')
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

    confettiTrigger.value += 1

    if (currentIndex.value < cards.value.length - 1) {
      if (advanceTimeoutId !== null) {
        window.clearTimeout(advanceTimeoutId)
      }
      advanceTimeoutId = window.setTimeout(() => {
        advanceTimeoutId = null
        nextCard()
      }, 350)
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

onBeforeUnmount(() => {
  if (advanceTimeoutId !== null) {
    window.clearTimeout(advanceTimeoutId)
    advanceTimeoutId = null
  }
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <KidConfetti :trigger="confettiTrigger" />
    <PageHero
      kid
      show-mascot
      :eyebrow="t('learning.flashcardsEyebrow')"
      :title="t('learning.flashcardPracticeTitle')"
      :description="t('learning.flashcardPracticeDescription')"
      :mascot-message="t('kid.flashcardFlipHint')"
      mascot-holding="字"
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

          <button
            class="flashcard-surface flashcard-surface--3d"
            :class="{ 'is-flipped': flipped }"
            type="button"
            @click="flipped = !flipped"
          >
            <div class="flashcard-3d">
              <div class="flashcard-face flashcard-face--front">
                <strong>{{ currentCard?.chineseText }}</strong>
                <span>👆 {{ t('learning.tapToFlip') }}</span>
              </div>
              <div class="flashcard-face flashcard-face--back">
                <strong>{{ currentCard?.englishMeaning }}</strong>
                <p>🔤 {{ currentCard?.pinyin }}</p>
                <p v-if="currentCard?.malayMeaning">🌏 {{ currentCard?.malayMeaning }}</p>
              </div>
            </div>
          </button>

          <div class="student-practice-card__actions">
            <el-button round :disabled="currentIndex === 0" @click="previousCard">⬅️ {{ t('kid.previousFun') }}</el-button>
            <el-button type="primary" round :loading="completing" @click="completeCurrentCard">
              🌟 {{ t('learning.markPractised') }}
            </el-button>
            <el-button round :disabled="currentIndex >= cards.length - 1" @click="nextCard">{{ t('kid.nextFun') }} ➡️</el-button>
          </div>

          <el-alert v-if="completionMessage" :title="completionMessage" type="success" show-icon :closable="false" />
        </el-card>

        <el-empty v-if="!cards.length" :description="t('learning.noFlashcardsAvailable')" />
      </template>
    </el-skeleton>
  </div>
</template>

<style scoped>
.flashcard-surface--3d {
  perspective: 1400px;
  background: transparent;
  border: none;
  cursor: pointer;
  width: 100%;
  padding: 0;
}

.flashcard-3d {
  position: relative;
  width: 100%;
  min-height: 320px;
  transform-style: preserve-3d;
  transition: transform 0.7s cubic-bezier(0.4, 0.2, 0.2, 1);
}

.flashcard-surface--3d.is-flipped .flashcard-3d {
  transform: rotateY(180deg);
}

.flashcard-face--front,
.flashcard-face--back {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  display: grid;
  place-items: center;
  text-align: center;
  padding: 32px;
  gap: 14px;
}

.flashcard-face--back {
  transform: rotateY(180deg);
}
</style>
