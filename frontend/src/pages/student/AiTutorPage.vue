<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { aiService } from '../../services/aiService'
import { useAuthStore } from '../../stores/authStore'
import type { AiAvailability, AiChatResponse } from '../../types/ai'

type ChatMessage = {
  role: 'user' | 'assistant'
  text: string
  status: string
  createdAt: string
}

const { t } = useI18n()
const authStore = useAuthStore()
const route = useRoute()

const loadingAvailability = ref(true)
const sending = ref(false)
const availability = ref<AiAvailability | null>(null)
const errorMessage = ref('')
const sessionId = ref<number | null>(null)
const draftMessage = ref('')
const messages = ref<ChatMessage[]>([])

const languagePreference = computed(() => authStore.currentUser?.preferredLanguage ?? 'en')

async function loadAvailability() {
  loadingAvailability.value = true
  try {
    availability.value = await aiService.getAvailability()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('ai.messages.unavailable')
  } finally {
    loadingAvailability.value = false
  }
}

function appendExchange(result: AiChatResponse) {
  messages.value.push(
    {
      role: 'user',
      text: result.userMessage,
      status: result.status,
      createdAt: result.createdAt,
    },
    {
      role: 'assistant',
      text: result.aiResponse,
      status: result.status,
      createdAt: result.createdAt,
    },
  )
}

async function sendMessage() {
  if (!draftMessage.value.trim()) {
    ElMessage.error(t('ai.validation.messageRequired'))
    return
  }

  sending.value = true
  errorMessage.value = ''

  try {
    const result = await aiService.chat({
      message: draftMessage.value.trim(),
      languagePreference: languagePreference.value,
      sessionId: sessionId.value,
      context: typeof route.query.context === 'string' ? route.query.context : 'Student AI tutor conversation',
      relatedLessonId: route.query.lessonId ? Number(route.query.lessonId) : undefined,
    })
    sessionId.value = result.sessionId
    appendExchange(result)
    draftMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('ai.messages.unavailable')
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  loadAvailability()
})

watch(
  () => route.query,
  (query) => {
    if (!draftMessage.value.trim() && typeof query.topic === 'string' && query.topic.trim()) {
      draftMessage.value = t('ai.prefillTopicPrompt', { topic: query.topic.trim() })
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      kid
      show-mascot
      :eyebrow="t('ai.tutorEyebrow')"
      :title="t('ai.tutorTitle')"
      :description="t('ai.tutorDescription')"
      :mascot-message="t('kid.aiTutorWelcome')"
      mascot-holding="智"
    />

    <el-alert
      v-if="availability && !availability.available"
      :title="availability.message"
      type="warning"
      show-icon
      :closable="false"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card class="dashboard-panel ai-chat-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.aiTutor') }}</p>
          <h2>{{ t('ai.chatWithTutor') }}</h2>
        </div>
        <el-tag round>{{ availability?.provider ?? 'deepseek' }}</el-tag>
      </div>

      <el-skeleton v-if="loadingAvailability" animated :rows="6" />

      <template v-else>
        <el-empty v-if="!messages.length" :description="t('ai.emptyTutorState')" />

        <div v-else class="ai-chat-thread">
          <article
            v-for="(message, index) in messages"
            :key="`${message.createdAt}-${index}`"
            class="ai-chat-bubble"
            :class="`ai-chat-bubble--${message.role}`"
          >
            <strong>{{ message.role === 'assistant' ? t('ai.tutorName') : t('ai.youLabel') }}</strong>
            <p>{{ message.text }}</p>
          </article>
        </div>

        <div class="ai-composer">
          <el-input
            v-model="draftMessage"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            :placeholder="t('ai.tutorPlaceholder')"
          />
          <div class="student-practice-card__actions ai-composer__actions">
            <el-button type="primary" round size="large" :loading="sending" @click="sendMessage">
              🚀 {{ t('ai.sendMessage') }}
            </el-button>
          </div>
        </div>
      </template>
    </el-card>
  </div>
</template>
