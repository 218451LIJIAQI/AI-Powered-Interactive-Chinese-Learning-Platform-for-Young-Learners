<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiWritingFeedbackResponse } from '../../types/ai'

const { t } = useI18n()
const authStore = useAuthStore()
const translateEnum = useEnumLabel()

const loading = ref(false)
const errorMessage = ref('')
const result = ref<AiWritingFeedbackResponse | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const isDrawing = ref(false)
const selectedFile = ref<File | null>(null)
let context: CanvasRenderingContext2D | null = null

const form = reactive({
  targetCharacter: '',
  languagePreference: authStore.currentUser?.preferredLanguage ?? 'en',
})

const hasCanvas = computed(() => Boolean(canvasRef.value))

function handleImageChange(file: { raw?: File }) {
  selectedFile.value = file.raw ?? null
}

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

async function submitFeedback() {
  if (!form.targetCharacter.trim()) {
    ElMessage.error(t('ai.validation.targetCharacterRequired'))
    return
  }

  const formData = new FormData()
  if (selectedFile.value) {
    formData.append('file', selectedFile.value)
  } else if (canvasRef.value) {
    const dataUrl = canvasRef.value.toDataURL('image/png')
    const response = await fetch(dataUrl)
    const blob = await response.blob()
    formData.append('file', new File([blob], 'writing-feedback.png', { type: 'image/png' }))
  } else {
    ElMessage.error(t('ai.validation.imageFileRequired'))
    return
  }

  formData.append('targetCharacter', form.targetCharacter.trim())
  formData.append('languagePreference', form.languagePreference)

  loading.value = true
  errorMessage.value = ''

  try {
    result.value = await aiService.writingFeedback(formData)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('ai.messages.unavailable')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (canvasRef.value) {
    context = canvasRef.value.getContext('2d')
    if (context) {
      context.lineWidth = 8
      context.lineCap = 'round'
      context.strokeStyle = '#567eff'
    }
  }
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('ai.writingEyebrow')"
      :title="t('ai.writingTitle')"
      :description="t('ai.writingDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-form-grid">
        <el-input v-model="form.targetCharacter" :placeholder="t('ai.targetCharacterPlaceholder')" maxlength="10" />
      </div>

      <canvas
        ref="canvasRef"
        class="tracing-canvas"
        width="720"
        height="320"
        @mousedown="startDrawing"
        @mousemove="draw"
        @mouseup="stopDrawing"
        @mouseleave="stopDrawing"
        @touchstart.prevent="startDrawing"
        @touchmove.prevent="draw"
        @touchend.prevent="stopDrawing"
      />

      <el-upload
        :auto-upload="false"
        :limit="1"
        accept=".png,.jpg,.jpeg,image/*"
        :show-file-list="true"
        :on-change="handleImageChange"
      >
        <template #trigger>
          <el-button plain type="primary">{{ t('ai.selectImageFile') }}</el-button>
        </template>
      </el-upload>

      <div class="student-practice-card__actions">
        <el-button round @click="clearCanvas">{{ t('learning.clearCanvas') }}</el-button>
        <el-button type="primary" round :loading="loading" @click="submitFeedback">
          {{ t('ai.submitForFeedback') }}
        </el-button>
      </div>

      <p class="student-helper-text" v-if="hasCanvas">{{ t('ai.canvasHelperText') }}</p>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card v-if="result" class="dashboard-panel ai-result-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('ai.feedbackLabel') }}</p>
          <h2>{{ result.feedback }}</h2>
        </div>
        <el-tag round>{{ translateEnum('aiStatus', result.status) }}</el-tag>
      </div>

      <div class="stacked-summary-list">
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.clarityComment') }}</strong>
            <p>{{ result.clarityComment }}</p>
          </div>
        </article>
        <article class="summary-row">
          <div>
            <strong>{{ t('ai.improvementTips') }}</strong>
            <p>{{ result.improvementTips }}</p>
          </div>
        </article>
      </div>

      <el-alert :title="result.encouragement" type="success" show-icon :closable="false" />
    </el-card>
  </div>
</template>
