<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { TracingExercisePlay } from '../../types/learning'

const route = useRoute()
const { t } = useI18n()
const loading = ref(true)
const completing = ref(false)
const exercise = ref<TracingExercisePlay | null>(null)
const errorMessage = ref('')
const canvasRef = ref<HTMLCanvasElement | null>(null)
const isDrawing = ref(false)
let context: CanvasRenderingContext2D | null = null

const exerciseId = computed(() => Number(route.params.exerciseId))

function startDrawing(event: MouseEvent | TouchEvent) {
  if (!context || !canvasRef.value) {
    return
  }
  isDrawing.value = true
  const { x, y } = getCoordinates(event)
  context.beginPath()
  context.moveTo(x, y)
}

function draw(event: MouseEvent | TouchEvent) {
  if (!isDrawing.value || !context) {
    return
  }
  const { x, y } = getCoordinates(event)
  context.lineTo(x, y)
  context.stroke()
}

function stopDrawing() {
  isDrawing.value = false
}

function getCoordinates(event: MouseEvent | TouchEvent) {
  const rect = canvasRef.value!.getBoundingClientRect()
  const point = 'touches' in event ? event.touches[0] ?? event.changedTouches[0] : event
  return {
    x: point.clientX - rect.left,
    y: point.clientY - rect.top,
  }
}

function clearCanvas() {
  if (!context || !canvasRef.value) {
    return
  }
  context.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
}

async function loadExercise() {
  loading.value = true
  try {
    exercise.value = await studentLearningService.getTracingExercise(exerciseId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

async function completeTracing() {
  completing.value = true
  try {
    const imageUrl = canvasRef.value?.toDataURL('image/png')
    const result = await studentLearningService.completeTracingExercise(exerciseId.value, {
      canvasImageUrl: imageUrl,
    })
    ElMessage.success(t('learning.messages.tracingCompleted', { points: result.earnedPoints }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('learning.messages.tracingSubmitFailed'))
  } finally {
    completing.value = false
  }
}

onMounted(async () => {
  await loadExercise()
  if (canvasRef.value) {
    context = canvasRef.value.getContext('2d')
    if (context) {
      context.lineWidth = 8
      context.lineCap = 'round'
      context.strokeStyle = '#ff8a65'
    }
  }
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.tracingEyebrow')"
      :title="exercise?.title || t('learning.tracingPlayTitle')"
      :description="t('learning.tracingPlayDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="6">
      <template #default>
        <el-card v-if="exercise" class="dashboard-panel" shadow="hover">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('learning.targetCharacterLabel') }}</p>
              <h2>{{ exercise.targetCharacter }}</h2>
              <p>{{ exercise.pinyin }} - {{ exercise.meaning }}</p>
            </div>
          </div>

          <p v-if="exercise.instructions" class="student-helper-text">{{ exercise.instructions }}</p>

          <canvas
            ref="canvasRef"
            class="tracing-canvas"
            width="720"
            height="360"
            @mousedown="startDrawing"
            @mousemove="draw"
            @mouseup="stopDrawing"
            @mouseleave="stopDrawing"
            @touchstart.prevent="startDrawing"
            @touchmove.prevent="draw"
            @touchend.prevent="stopDrawing"
          />

          <div class="student-practice-card__actions">
            <el-button round @click="clearCanvas">{{ t('learning.clearCanvas') }}</el-button>
            <el-button type="primary" round :loading="completing" @click="completeTracing">{{ t('learning.markCompleted') }}</el-button>
          </div>
        </el-card>
      </template>
    </el-skeleton>
  </div>
</template>
