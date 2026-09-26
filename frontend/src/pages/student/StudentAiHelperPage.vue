<template>
  <div class="page-stack student-ai-helper-page">
    <DataNotice
      :using-fallback="usingFallback"
      :error="pageError"
      :success="actionSuccess"
      :fallback-title="t('studentAi.previewTitle')"
      :fallback-message="t('studentAi.previewMessage')"
      :success-title="t('studentAi.successTitle')"
    />

    <section class="student-ai-helper-shell">
      <div class="student-ai-helper-main">
        <article class="student-ai-helper-hero">
          <span class="student-ai-helper-cloud is-left" />
          <span class="student-ai-helper-cloud is-right" />
          <span class="student-ai-helper-pagoda" />
          <span class="student-ai-helper-hill" />
          <PandaIllustration
            class="student-ai-helper-panda"
            :size="190"
            outfit="#7d56ff"
            accent="#f6d056"
            pose="wave"
          />

          <div class="student-ai-helper-prompt-card">
            <strong>{{ t('studentAi.hero') }}</strong>
            <p>{{ t('studentAi.tryAsking') }}</p>
            <div class="student-ai-helper-suggestions">
              <button type="button" @click="fillChatSuggestion(t('studentAi.helloSuggestion'))">
                <IconGlyph name="moderation" :size="16" />
                {{ t('studentAi.helloSuggestion') }}
              </button>
              <button type="button" @click="fillWordSuggestion('苹果')">
                <IconGlyph name="learn" :size="16" />
                {{ t('studentAi.appleSuggestion') }}
              </button>
              <button type="button" @click="fillSentenceSuggestion('妈妈 máma')">
                <IconGlyph name="live" :size="16" />
                {{ t('studentAi.readSentence') }}
              </button>
            </div>
          </div>

          <form class="student-ai-helper-chatbar" @submit.prevent="runChat">
            <input
              v-model="chatForm.message"
              :aria-label="t('studentAi.askAria')"
              autocomplete="off"
              :placeholder="t('studentAi.askPlaceholder')"
            />
            <button type="submit" :disabled="submitting.chat" :aria-label="chatSubmitAriaLabel">
              <span v-if="submitting.chat">{{ t('studentAi.thinking') }}</span>
              <IconGlyph v-else name="chevronRight" :size="18" />
            </button>
          </form>
        </article>

        <div v-if="chatReply" class="student-ai-helper-reply">
          <div class="student-ai-helper-reply-header">
            <strong>SparkBot</strong>
            <button type="button" :disabled="reportDisabled" @click="submitAiReport">
              {{ reportButtonLabel }}
            </button>
          </div>
          <p>{{ displayedChatReply }}</p>
          <div class="student-ai-helper-report-controls">
            <label for="student-ai-report-reason">{{ t('studentAi.reason') }}</label>
            <select
              id="student-ai-report-reason"
              v-model="reportForm.reasonCategory"
              class="select"
              :disabled="reportDisabled"
            >
              <option value="INCORRECT_CONTENT">{{ t('studentAi.incorrect') }}</option>
              <option value="HALLUCINATION">{{ t('studentAi.hallucination') }}</option>
              <option value="UNSAFE_CONTENT">{{ t('studentAi.unsafe') }}</option>
              <option value="AGE_INAPPROPRIATE">{{ t('studentAi.ageInappropriate') }}</option>
              <option value="OTHER">{{ t('studentAi.other') }}</option>
            </select>
            <textarea
              v-model="reportForm.description"
              class="textarea"
              maxlength="1000"
              :disabled="reportDisabled"
              :aria-label="t('studentAi.reportAria')"
              :placeholder="t('studentAi.reportPlaceholder')"
            />
          </div>
        </div>

        <section class="student-ai-helper-tool-grid">
          <article class="student-ai-helper-tool-card is-purple">
            <span><IconGlyph name="moderation" :size="24" /></span>
            <strong>{{ t('studentAi.chatTutor') }}</strong>
            <p>{{ t('studentAi.chatHint') }}</p>
            <small>“为什么天空是蓝色的?”</small>
            <button type="button" @click="runChat" :disabled="submitting.chat">
              {{ t('studentAi.tryIt') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-green">
            <span><IconGlyph name="language" :size="24" /></span>
            <strong>{{ t('studentAi.vocabulary') }}</strong>
            <p>{{ t('studentAi.vocabularyHint') }}</p>
            <input
              v-model="wordForm.word"
              :aria-label="t('studentAi.wordAria')"
              autocomplete="off"
              placeholder="苹果"
            />
            <button type="button" @click="runVocabulary" :disabled="submitting.vocab">
              {{ t('studentAi.tryIt') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-orange">
            <span><IconGlyph name="clip" :size="24" /></span>
            <strong>{{ t('studentAi.sentence') }}</strong>
            <p>{{ t('studentAi.sentenceHint') }}</p>
            <input
              v-model="sentenceForm.sentence"
              :aria-label="t('studentAi.sentenceAria')"
              autocomplete="off"
              placeholder="我喜欢学习中文。"
            />
            <button type="button" @click="runSentence" :disabled="submitting.sentence">
              {{ t('studentAi.tryIt') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-blue">
            <span><IconGlyph name="live" :size="24" /></span>
            <strong>{{ t('studentAi.pronunciation') }}</strong>
            <p>{{ audioCapabilityMessage }}</p>
            <input
              v-model="pronunciationForm.targetText"
              :aria-label="t('studentAi.pronunciationTarget')"
              autocomplete="off"
              placeholder="妈妈"
              :disabled="audioUploadDisabled"
            />
            <input
              v-model="pronunciationForm.pinyin"
              :aria-label="t('studentAi.pinyinAria')"
              autocomplete="off"
              placeholder="mā ma"
              :disabled="audioUploadDisabled"
            />
            <input
              type="file"
              accept=".webm,.wav,.mp3,audio/webm,audio/wav,audio/mpeg,audio/mp3"
              :aria-label="t('studentAi.audioAria')"
              :disabled="audioUploadDisabled"
              @change="selectPronunciationFile"
            />
            <button type="button" @click="runPronunciation" :disabled="audioUploadDisabled || submitting.pronunciation">
              {{ audioUploadButtonLabel }}
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-red">
            <span><IconGlyph name="file" :size="24" /></span>
            <strong>{{ t('studentAi.characterHelp') }}</strong>
            <p>{{ imageCapabilityMessage }}</p>
            <input
              v-model="writingForm.targetCharacter"
              :aria-label="t('studentAi.characterAria')"
              autocomplete="off"
              placeholder="好"
              :disabled="imageUploadDisabled"
            />
            <input
              type="file"
              accept=".png,.jpg,.jpeg,image/png,image/jpeg"
              :aria-label="t('studentAi.writingAria')"
              :disabled="imageUploadDisabled"
              @change="selectWritingFile"
            />
            <button type="button" @click="runWriting" :disabled="imageUploadDisabled || submitting.writing">
              {{ imageUploadButtonLabel }}
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-teal">
            <span><IconGlyph name="quiz" :size="24" /></span>
            <strong>{{ t('studentAi.quizMaker') }}</strong>
            <p>{{ t('studentAi.quizHint') }}</p>
            <input
              v-model="quizForm.topic"
              :aria-label="t('studentAi.quizAria')"
              autocomplete="off"
              :placeholder="t('studentAi.storyPlaceholder')"
            />
            <button type="button" @click="runQuiz" :disabled="submitting.quiz">
              {{ quizButtonLabel }}
              <IconGlyph v-if="!submitting.quiz" name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-violet">
            <span><IconGlyph name="shield" :size="24" /></span>
            <strong>{{ t('studentAi.safeGuidance') }}</strong>
            <p>{{ t('studentAi.safeHint') }}</p>
            <button type="button" @click="runRecommendation" :disabled="submitting.recommendation">
              {{ t('studentAi.explore') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>
        </section>

        <section class="student-ai-helper-result-grid">
          <div v-if="vocabularyReply" class="student-ai-helper-result">
            <strong>{{ vocabularyReply.chineseWord }} - {{ vocabularyReply.pinyin }}</strong>
            <p>{{ vocabularyReply.englishMeaning }} / {{ vocabularyReply.bahasaMelayuMeaning }}</p>
            <small>{{ vocabularyReply.exampleSentence }}</small>
          </div>
          <div v-if="sentenceReply" class="student-ai-helper-result">
            <strong>{{ sentenceResultTitle }}</strong>
            <p>{{ sentenceReply.improvementExplanation }}</p>
          </div>
          <div v-if="recommendationReply" class="student-ai-helper-result">
            <strong>{{ recommendationReply.recommendedNextStep }}</strong>
            <p>{{ recommendationReply.weakAreaSummary }}</p>
          </div>
          <div v-if="quizReply" class="student-ai-helper-result">
            <strong>{{ quizReply.title }} - {{ formatStatus(quizReply.status) }}</strong>
            <p>{{ quizFirstQuestion }}</p>
            <small
              >{{ quizQuestionCountLabel }} -
              {{ quizReply.usageMode ? formatEnumLabel(quizReply.usageMode) : t('studentAi.statusUnavailable') }}</small
            >
          </div>
          <div v-if="pronunciationReply" class="student-ai-helper-result">
            <strong>{{
              t('studentAi.pronunciationFeedback', { status: formatStatus(pronunciationReply.status) })
            }}</strong>
            <p>{{ pronunciationFeedbackText }}</p>
            <small>{{ pronunciationSupportText }}</small>
          </div>
          <div v-if="writingReply" class="student-ai-helper-result">
            <strong>{{ t('studentAi.writingFeedback', { status: formatStatus(writingReply.status) }) }}</strong>
            <p>{{ writingFeedbackText }}</p>
            <small>{{ writingSupportText }}</small>
          </div>
        </section>

        <section class="student-ai-helper-footer">
          <span><IconGlyph name="fun" :size="24" /></span>
          <div>
            <strong>{{ t('studentAi.footerTitle') }}</strong>
            <p>{{ t('studentAi.footerHint') }}</p>
          </div>
          <button type="button" @click="runStory" :disabled="submitting.story">
            {{ t('studentAi.generateStory') }}
          </button>
        </section>
      </div>

      <aside class="student-ai-helper-side">
        <article class="student-ai-helper-status-card">
          <strong><IconGlyph name="shield" :size="18" /> {{ t('studentAi.safeAi') }}</strong>
          <span>{{ formatStatus(availability.status) }}</span>
          <p>{{ platformSettings.aiDisclaimerText }}</p>
          <small>{{ availability.message }}</small>
        </article>

        <article class="panel student-ai-helper-support-card">
          <strong>{{ t('studentAi.supportTitle') }}</strong>
          <ul>
            <li>{{ t('studentAi.supportOne') }}</li>
            <li>{{ t('studentAi.supportTwo') }}</li>
            <li>{{ t('studentAi.supportThree') }}</li>
            <li>{{ t('studentAi.supportFour') }}</li>
          </ul>
          <div class="student-ai-helper-care-note">
            <strong><IconGlyph name="status" :size="18" /> {{ t('studentAi.supportNote') }}</strong>
            <p>{{ platformSettings.aiDisclaimerText }}</p>
            <span><IconGlyph name="fun" :size="24" /></span>
          </div>
        </article>

        <article class="student-ai-helper-story-card">
          <strong>{{ t('studentAi.storyGenerator') }}</strong>
          <input
            v-model="storyForm.topic"
            :aria-label="t('studentAi.storyAria')"
            autocomplete="off"
            :placeholder="t('studentAi.storyPlaceholder')"
          />
          <button class="btn btn-primary" type="button" @click="runStory" :disabled="submitting.story">
            {{ storyButtonLabel }}
          </button>
          <div v-if="storyReply" class="student-ai-helper-story-result">
            <strong>{{ storyReply.title }}</strong>
            <p>{{ storyReply.chineseStory }}</p>
            <span>{{ storyReply.englishTranslation }}</span>
          </div>
        </article>

        <article class="student-ai-helper-story-card">
          <strong>{{ t('studentAi.myReports') }}</strong>
          <div v-if="aiReports.length" class="student-ai-helper-report-list">
            <div v-for="item in aiReports.slice(0, 3)" :key="item.id">
              <span>{{ formatReportReason(item.reasonCategory) }}</span>
              <strong>{{ formatStatus(item.status) }}</strong>
            </div>
          </div>
          <p v-else class="student-ai-helper-empty-note">{{ t('studentAi.noReports') }}</p>
        </article>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { aiApi, publicApi, studentApi } from '@/api/services'
import { appEnv } from '@/config/env'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import { useResource } from '@/composables/useResource'
import {
  aiAvailability,
  aiChatReply,
  aiQuizReply,
  aiRecommendationReply,
  aiSentenceReply,
  aiStoryReply,
  aiVocabularyReply,
  studentAiReports,
  studentProgress,
} from '@/demo/data'
import { studentAiHelperMessages } from '@/plugins/studentAiHelperMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  AiChatResponse,
  AiLearningRecommendationResponse,
  AiPronunciationFeedbackResponse,
  AiQuizGeneratorResponse,
  AiResponseReportReason,
  AiSentencePracticeResponse,
  StudentAiResponseReportResponse,
  AiStoryGeneratorResponse,
  AiVocabularyHelperResponse,
  AiWritingFeedbackResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, nonNegativeInteger } from '@/utils/format'

const { t } = useI18n({
  messages: {
    en: { studentAi: studentAiHelperMessages.en },
    ms: { studentAi: studentAiHelperMessages.ms },
    zh: { studentAi: studentAiHelperMessages.zh },
  },
})

const auth = useAuthStore()
const availabilityResource = useResource(() => aiApi.availability(), aiAvailability)
const progressResource = useResource(() => studentApi.progress(), studentProgress)
const platformSettingsResource = useResource(() => publicApi.platformSettings(), {
  platformName: appEnv.appName,
  aiDisclaimerText: t('studentAi.disclaimer'),
  strictContentFiltering: true,
  sensitiveWordFilter: true,
  aiSafetyCheck: true,
})

const previewFallbackEnabled = computed(() => appEnv.useDemoData && auth.isPreviewSession)
const emptyAvailability = {
  available: false,
  provider: null,
  model: null,
  status: 'UNAVAILABLE',
  message: t('studentAi.unavailable'),
  textGenerationAvailable: false,
  audioAnalysisAvailable: false,
  imageAnalysisAvailable: false,
}
const emptyProgress = {
  totalLessonsStarted: 0,
  completedLessons: 0,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  lastActivitySummary: null,
  recentLessons: [],
}

const availability = computed(
  () => availabilityResource.data.value ?? (previewFallbackEnabled.value ? aiAvailability : emptyAvailability),
)
const progress = computed(
  () => progressResource.data.value ?? (previewFallbackEnabled.value ? studentProgress : emptyProgress),
)
const reportsResource = useResource(() => aiApi.reports(), studentAiReports)
const usingFallback = computed(
  () =>
    availabilityResource.usingFallback.value ||
    progressResource.usingFallback.value ||
    reportsResource.usingFallback.value ||
    platformSettingsResource.usingFallback.value,
)
const rawAiReports = computed(
  () => reportsResource.data.value ?? (reportsResource.usingFallback.value ? studentAiReports : []),
)
const aiReports = computed(() =>
  rawAiReports.value.filter((item): item is StudentAiResponseReportResponse =>
    Boolean(item?.id && item.reasonCategory && item.status),
  ),
)
const platformSettings = computed(
  () =>
    platformSettingsResource.data.value ?? {
      platformName: appEnv.appName,
      aiDisclaimerText: t('studentAi.disclaimer'),
      strictContentFiltering: true,
      sensitiveWordFilter: true,
      aiSafetyCheck: true,
    },
)
const learnerLevelLabel = computed(() => {
  if (progress.value.lastActivitySummary) return progress.value.lastActivitySummary
  const overallCompletionPercent = clampPercent(progress.value.overallCompletionPercent)
  if (overallCompletionPercent >= 80) return t('studentAi.advanced')
  if (overallCompletionPercent >= 40) return t('studentAi.intermediate')
  if (nonNegativeInteger(progress.value.totalLessonsStarted) > 0) return t('studentAi.activeBeginner')
  return t('studentAi.newLearner')
})
const audioUploadDisabled = computed(() => !previewFallbackEnabled.value && !availability.value.audioAnalysisAvailable)
const imageUploadDisabled = computed(() => !previewFallbackEnabled.value && !availability.value.imageAnalysisAvailable)
const audioCapabilityMessage = computed(() =>
  audioUploadDisabled.value ? t('studentAi.audioUnsupported') : t('studentAi.audioHint'),
)
const imageCapabilityMessage = computed(() =>
  imageUploadDisabled.value ? t('studentAi.imageUnsupported') : t('studentAi.imageHint'),
)
const submitting = reactive({
  chat: false,
  vocab: false,
  sentence: false,
  story: false,
  recommendation: false,
  quiz: false,
  pronunciation: false,
  writing: false,
  report: false,
})
const audioUploadButtonLabel = computed(() => {
  if (audioUploadDisabled.value) return t('studentAi.audioUnavailable')
  return submitting.pronunciation ? t('studentAi.checking') : t('studentAi.uploadAudio')
})
const imageUploadButtonLabel = computed(() => {
  if (imageUploadDisabled.value) return t('studentAi.imageUnavailable')
  return submitting.writing ? t('studentAi.checking') : t('studentAi.uploadWriting')
})

const chatForm = reactive({ message: '' })
const wordForm = reactive({ word: '' })
const sentenceForm = reactive({ sentence: '' })
const storyForm = reactive({ topic: '' })
const quizForm = reactive({ topic: t('studentAi.storyPlaceholder') })
interface PronunciationForm {
  targetText: string
  pinyin: string
  tone: string
  file: File | null
}

interface WritingForm {
  targetCharacter: string
  file: File | null
}

const pronunciationForm = reactive<PronunciationForm>({
  targetText: '',
  pinyin: '',
  tone: '',
  file: null,
})
const writingForm = reactive<WritingForm>({
  targetCharacter: '',
  file: null,
})

const chatReply = ref<AiChatResponse | null>(previewFallbackEnabled.value ? aiChatReply : null)
const streamingChatText = ref('')
const vocabularyReply = ref<AiVocabularyHelperResponse | null>(previewFallbackEnabled.value ? aiVocabularyReply : null)
const sentenceReply = ref<AiSentencePracticeResponse | null>(previewFallbackEnabled.value ? aiSentenceReply : null)
const storyReply = ref<AiStoryGeneratorResponse | null>(previewFallbackEnabled.value ? aiStoryReply : null)
const quizReply = ref<AiQuizGeneratorResponse | null>(previewFallbackEnabled.value ? aiQuizReply : null)
const recommendationReply = ref<AiLearningRecommendationResponse | null>(
  previewFallbackEnabled.value ? aiRecommendationReply : null,
)
const pronunciationReply = ref<AiPronunciationFeedbackResponse | null>(null)
const writingReply = ref<AiWritingFeedbackResponse | null>(null)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const maxPronunciationFileSize = 10 * 1024 * 1024
const maxWritingFileSize = 5 * 1024 * 1024
const pronunciationExtensions = ['.webm', '.wav', '.mp3'] as const
const writingExtensions = ['.png', '.jpg', '.jpeg'] as const
const pronunciationMimeTypes = new Set(['audio/webm', 'audio/wav', 'audio/x-wav', 'audio/mpeg', 'audio/mp3'])
const writingMimeTypes = new Set(['image/png', 'image/jpeg'])
const reportForm = reactive<{
  reasonCategory: AiResponseReportReason
  description: string
}>({
  reasonCategory: 'INCORRECT_CONTENT',
  description: '',
})
const reportDisabled = computed(() => previewFallbackEnabled.value || submitting.report || !chatReply.value)
const pageError = computed(
  () =>
    actionError.value ||
    availabilityResource.error.value ||
    progressResource.error.value ||
    reportsResource.error.value ||
    platformSettingsResource.error.value,
)
const chatSubmitAriaLabel = computed(() => (submitting.chat ? t('studentAi.answering') : t('studentAi.sendQuestion')))
const reportButtonLabel = computed(() => (submitting.report ? t('studentAi.reporting') : t('studentAi.reportResponse')))
const displayedChatReply = computed(() => streamingChatText.value || chatReply.value?.aiResponse || '')
const quizButtonLabel = computed(() => (submitting.quiz ? t('studentAi.creating') : t('studentAi.createQuiz')))
const storyButtonLabel = computed(() => (submitting.story ? t('studentAi.generating') : t('studentAi.createStory')))
const sentenceResultTitle = computed(
  () => sentenceReply.value?.correctedSentence || sentenceReply.value?.originalSentence || '',
)
const quizFirstQuestion = computed(() => quizReply.value?.questions[0]?.questionText || t('studentAi.quizReady'))
const quizQuestionCountLabel = computed(() => {
  const count = quizReply.value?.questions.length ?? 0
  return t('studentAi.questions', { count })
})
const pronunciationFeedbackText = computed(
  () => pronunciationReply.value?.feedback || pronunciationReply.value?.pronunciationComment || '',
)
const pronunciationSupportText = computed(
  () => pronunciationReply.value?.toneComment || pronunciationReply.value?.improvementTips || '',
)
const writingFeedbackText = computed(() => writingReply.value?.feedback || writingReply.value?.clarityComment || '')
const writingSupportText = computed(
  () => writingReply.value?.improvementTips || writingReply.value?.encouragement || '',
)

type AiResponseStatusCarrier = { status?: string | null }

function aiResponseSucceeded(response: AiResponseStatusCarrier): boolean {
  return response.status?.toUpperCase() === 'SUCCESS'
}

function applyAiResponseStatus(
  response: AiResponseStatusCarrier,
  successMessage: string,
  unavailableMessage: string,
): void {
  if (aiResponseSucceeded(response)) {
    actionError.value = null
    actionSuccess.value = successMessage
    return
  }

  actionSuccess.value = null
  actionError.value = t('studentAi.responseStatus', {
    message: unavailableMessage,
    status: formatStatus(response.status || 'UNAVAILABLE'),
  })
}

function hasAllowedExtension(file: File, extensions: readonly string[]): boolean {
  const name = file.name.toLowerCase()
  return extensions.some((extension) => name.endsWith(extension))
}

function hasAllowedUploadType(file: File, extensions: readonly string[], mimeTypes: ReadonlySet<string>): boolean {
  const mimeType = file.type.trim().toLowerCase()
  if (mimeType) return mimeTypes.has(mimeType)

  return hasAllowedExtension(file, extensions)
}

function showPreviewResponse(message: string): void {
  actionError.value = null
  actionSuccess.value = t('studentAi.previewSuffix', { message })
}

function selectedFile(event: Event): File | null {
  const input = event.target as HTMLInputElement | null
  return input?.files?.[0] ?? null
}

function clearFileInput(event: Event): void {
  const input = event.target as HTMLInputElement | null
  if (input) input.value = ''
}

function selectPronunciationFile(event: Event): void {
  actionError.value = null
  const file = selectedFile(event)

  if (!file) {
    pronunciationForm.file = null
    return
  }

  if (
    !hasAllowedUploadType(file, pronunciationExtensions, pronunciationMimeTypes) ||
    file.size > maxPronunciationFileSize
  ) {
    pronunciationForm.file = null
    clearFileInput(event)
    actionError.value = t('studentAi.uploadAudioInvalid')
    return
  }

  pronunciationForm.file = file
}

function selectWritingFile(event: Event): void {
  actionError.value = null
  const file = selectedFile(event)

  if (!file) {
    writingForm.file = null
    return
  }

  if (!hasAllowedUploadType(file, writingExtensions, writingMimeTypes) || file.size > maxWritingFileSize) {
    writingForm.file = null
    clearFileInput(event)
    actionError.value = t('studentAi.uploadImageInvalid')
    return
  }

  writingForm.file = file
}

async function runChat(): Promise<void> {
  if (submitting.chat) return
  actionError.value = null
  actionSuccess.value = null
  streamingChatText.value = ''
  if (!chatForm.message.trim()) {
    actionError.value = t('studentAi.askRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    chatReply.value = aiChatReply
    showPreviewResponse(t('studentAi.chatSample'))
    return
  }
  submitting.chat = true
  try {
    chatReply.value = {
      sessionId: chatReply.value?.sessionId ?? null,
      userMessage: chatForm.message.trim(),
      aiResponse: '',
      language: 'en',
      createdAt: new Date().toISOString(),
      status: 'STREAMING',
    }
    chatReply.value = await aiApi.chatStream(
      { message: chatForm.message.trim(), languagePreference: 'en', sessionId: chatReply.value.sessionId ?? undefined },
      {
        onDelta: (chunk) => {
          streamingChatText.value += chunk
        },
      },
    )
    streamingChatText.value = ''
    applyAiResponseStatus(chatReply.value, t('studentAi.chatReady'), t('studentAi.chatFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.chatError'))
    chatReply.value = null
    streamingChatText.value = ''
  } finally {
    submitting.chat = false
  }
}

async function runVocabulary(): Promise<void> {
  if (submitting.vocab) return
  actionError.value = null
  actionSuccess.value = null
  if (!wordForm.word.trim()) {
    actionError.value = t('studentAi.wordRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    vocabularyReply.value = aiVocabularyReply
    showPreviewResponse(t('studentAi.vocabSample'))
    return
  }
  submitting.vocab = true
  try {
    vocabularyReply.value = await aiApi.vocabularyHelper({
      word: wordForm.word.trim(),
      inputLanguage: 'zh',
      outputLanguage: 'en',
    })
    applyAiResponseStatus(vocabularyReply.value, t('studentAi.vocabReady'), t('studentAi.vocabFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.vocabError'))
    vocabularyReply.value = null
  } finally {
    submitting.vocab = false
  }
}

async function runSentence(): Promise<void> {
  if (submitting.sentence) return
  actionError.value = null
  actionSuccess.value = null
  if (!sentenceForm.sentence.trim()) {
    actionError.value = t('studentAi.sentenceRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    sentenceReply.value = aiSentenceReply
    showPreviewResponse(t('studentAi.sentenceSample'))
    return
  }
  submitting.sentence = true
  try {
    sentenceReply.value = await aiApi.sentencePractice({
      sentence: sentenceForm.sentence.trim(),
      learnerLevel: learnerLevelLabel.value,
    })
    applyAiResponseStatus(sentenceReply.value, t('studentAi.sentenceReady'), t('studentAi.sentenceFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.sentenceError'))
    sentenceReply.value = null
  } finally {
    submitting.sentence = false
  }
}

async function runStory(): Promise<void> {
  if (submitting.story) return
  actionError.value = null
  actionSuccess.value = null
  if (!storyForm.topic.trim()) {
    actionError.value = t('studentAi.storyRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    storyReply.value = aiStoryReply
    showPreviewResponse(t('studentAi.storySample'))
    return
  }
  submitting.story = true
  try {
    storyReply.value = await aiApi.storyGenerator({
      topic: storyForm.topic.trim(),
      level: learnerLevelLabel.value,
      languagePreference: 'en',
    })
    applyAiResponseStatus(storyReply.value, t('studentAi.storyReady'), t('studentAi.storyFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.storyError'))
    storyReply.value = null
  } finally {
    submitting.story = false
  }
}

async function runRecommendation(): Promise<void> {
  if (submitting.recommendation) return
  actionError.value = null
  actionSuccess.value = null
  if (previewFallbackEnabled.value) {
    recommendationReply.value = aiRecommendationReply
    showPreviewResponse(t('studentAi.recommendationSample'))
    return
  }
  submitting.recommendation = true
  try {
    recommendationReply.value = await aiApi.learningRecommendation({
      currentLevel: learnerLevelLabel.value,
      weakAreas: progress.value.weakAreas,
      languagePreference: 'en',
    })
    applyAiResponseStatus(
      recommendationReply.value,
      t('studentAi.recommendationReady'),
      t('studentAi.recommendationFallback'),
    )
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.recommendationError'))
    recommendationReply.value = null
  } finally {
    submitting.recommendation = false
  }
}

async function runQuiz(): Promise<void> {
  if (submitting.quiz) return
  actionError.value = null
  actionSuccess.value = null
  if (!quizForm.topic.trim()) {
    actionError.value = t('studentAi.quizRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    quizReply.value = aiQuizReply
    showPreviewResponse(t('studentAi.quizSample'))
    return
  }
  submitting.quiz = true
  try {
    quizReply.value = await aiApi.quizGenerator({
      topic: quizForm.topic.trim(),
      level: learnerLevelLabel.value,
      numberOfQuestions: 3,
      category: 'Conversation',
      targetRoleContext: 'student self practice',
      languagePreference: 'en',
    })
    applyAiResponseStatus(quizReply.value, t('studentAi.quizSuccess'), t('studentAi.quizFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.quizError'))
    quizReply.value = null
  } finally {
    submitting.quiz = false
  }
}

async function submitAiReport(): Promise<void> {
  if (submitting.report) return
  actionError.value = null
  actionSuccess.value = null
  if (!chatReply.value) {
    actionError.value = t('studentAi.reportRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    showPreviewResponse(t('studentAi.reportPreview'))
    return
  }
  submitting.report = true
  try {
    await aiApi.reportResponse({
      featureKey: 'CHAT_TUTOR',
      reasonCategory: reportForm.reasonCategory,
      description: reportForm.description.trim() || undefined,
      promptSnapshot: chatForm.message.trim() || undefined,
      aiResponseSnapshot: chatReply.value.aiResponse,
    })
    reportForm.description = ''
    actionSuccess.value = t('studentAi.reportThanks')
    await reportsResource.reload()
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.reportError'))
  } finally {
    submitting.report = false
  }
}

async function runPronunciation(): Promise<void> {
  if (submitting.pronunciation) return
  actionError.value = null
  actionSuccess.value = null
  if (audioUploadDisabled.value) {
    actionError.value = t('studentAi.pronunciationUnsupported')
    return
  }
  if (!pronunciationForm.targetText.trim()) {
    actionError.value = t('studentAi.pronunciationTargetRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    pronunciationReply.value = null
    showPreviewResponse(t('studentAi.pronunciationPreview'))
    return
  }
  if (!pronunciationForm.file) {
    actionError.value = t('studentAi.pronunciationFileRequired')
    return
  }
  submitting.pronunciation = true
  try {
    const formData = new FormData()
    formData.append('file', pronunciationForm.file)
    formData.append('targetText', pronunciationForm.targetText.trim())
    if (pronunciationForm.pinyin.trim()) {
      formData.append('pinyin', pronunciationForm.pinyin.trim())
    }
    if (pronunciationForm.tone.trim()) {
      formData.append('tone', pronunciationForm.tone.trim())
    }
    formData.append('languagePreference', 'en')
    pronunciationReply.value = await aiApi.pronunciationFeedback(formData)
    applyAiResponseStatus(
      pronunciationReply.value,
      t('studentAi.pronunciationReady'),
      t('studentAi.pronunciationFallback'),
    )
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.pronunciationError'))
    pronunciationReply.value = null
  } finally {
    submitting.pronunciation = false
  }
}

async function runWriting(): Promise<void> {
  if (submitting.writing) return
  actionError.value = null
  actionSuccess.value = null
  if (imageUploadDisabled.value) {
    actionError.value = t('studentAi.writingUnsupported')
    return
  }
  if (!writingForm.targetCharacter.trim()) {
    actionError.value = t('studentAi.characterRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    writingReply.value = null
    showPreviewResponse(t('studentAi.writingPreview'))
    return
  }
  if (!writingForm.file) {
    actionError.value = t('studentAi.writingFileRequired')
    return
  }
  submitting.writing = true
  try {
    const formData = new FormData()
    formData.append('file', writingForm.file)
    formData.append('targetCharacter', writingForm.targetCharacter.trim())
    formData.append('languagePreference', 'en')
    writingReply.value = await aiApi.writingFeedback(formData)
    applyAiResponseStatus(writingReply.value, t('studentAi.writingReady'), t('studentAi.writingFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.writingError'))
    writingReply.value = null
  } finally {
    submitting.writing = false
  }
}

function fillChatSuggestion(message: string): void {
  chatForm.message = message
}

function fillWordSuggestion(word: string): void {
  wordForm.word = word
}

function fillSentenceSuggestion(sentence: string): void {
  sentenceForm.sentence = sentence
}

function formatEnumLabel(value: string): string {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function formatReportReason(value: string): string {
  const keys: Record<string, string> = {
    INCORRECT_CONTENT: 'incorrect',
    HALLUCINATION: 'hallucination',
    UNSAFE_CONTENT: 'unsafe',
    AGE_INAPPROPRIATE: 'ageInappropriate',
    OTHER: 'other',
  }
  const key = keys[value.toUpperCase()]
  if (key) return t(`studentAi.${key}`)
  return formatEnumLabel(value)
}

function formatStatus(value: string): string {
  const keys: Record<string, string> = {
    PENDING_REVIEW: 'statusPendingReview',
    IN_REVIEW: 'statusInReview',
    RESOLVED: 'statusResolved',
    DISMISSED: 'statusDismissed',
    SUCCESS: 'statusSuccess',
    AVAILABLE: 'statusAvailable',
    DEGRADED: 'statusDegraded',
    UNAVAILABLE: 'statusUnavailable',
  }
  const key = keys[value.toUpperCase()]
  if (key) return t(`studentAi.${key}`)
  return formatEnumLabel(value)
}
</script>
