<template>
  <div class="page-stack student-ai-helper-page">
    <DataNotice
      :using-fallback="usingFallback"
      :error="actionError || availabilityResource.error.value || progressResource.error.value || reportsResource.error.value || platformSettingsResource.error.value"
      :success="actionSuccess"
      fallback-title="Preview AI helper"
      fallback-message="SparkBot is showing safe sample guidance. Preview actions do not send live AI requests."
      success-title="AI helper update"
    />

    <section class="student-ai-helper-shell">
      <div class="student-ai-helper-main">
        <article class="student-ai-helper-hero">
          <span class="student-ai-helper-cloud is-left" />
          <span class="student-ai-helper-cloud is-right" />
          <span class="student-ai-helper-pagoda" />
          <span class="student-ai-helper-hill" />
          <PandaIllustration class="student-ai-helper-panda" :size="190" outfit="#7d56ff" accent="#f6d056" pose="wave" />

          <div class="student-ai-helper-prompt-card">
            <strong>I can help you learn Mandarin in fun and easy ways!</strong>
            <p>Try asking me something like:</p>
            <div class="student-ai-helper-suggestions">
              <button type="button" @click="chatForm.message = 'How do you say hello?'">
                <IconGlyph name="moderation" :size="16" />
                “你好” 怎么说?
              </button>
              <button type="button" @click="wordForm.word = '苹果'">
                <IconGlyph name="learn" :size="16" />
                “苹果” 是什么意思?
              </button>
              <button type="button" @click="sentenceForm.sentence = '妈妈 máma'">
                <IconGlyph name="live" :size="16" />
                Help me read this sentence
              </button>
            </div>
          </div>

          <form class="student-ai-helper-chatbar" @submit.prevent="runChat">
            <input
              v-model="chatForm.message"
              aria-label="Ask SparkBot a Mandarin question"
              autocomplete="off"
              placeholder="Type your question in English or Mandarin..."
            />
            <button
              type="submit"
              :disabled="submitting.chat"
              :aria-label="submitting.chat ? 'SparkBot is answering' : 'Send question to SparkBot'"
            >
              <span v-if="submitting.chat">Thinking</span>
              <IconGlyph v-else name="chevronRight" :size="18" />
            </button>
          </form>
        </article>

        <div v-if="chatReply" class="student-ai-helper-reply">
          <div class="student-ai-helper-reply-header">
            <strong>SparkBot</strong>
            <button type="button" :disabled="reportDisabled" @click="submitAiReport">
              {{ submitting.report ? 'Reporting...' : 'Report response' }}
            </button>
          </div>
          <p>{{ chatReply.aiResponse }}</p>
          <div class="student-ai-helper-report-controls">
            <label for="student-ai-report-reason">Reason</label>
            <select id="student-ai-report-reason" v-model="reportForm.reasonCategory" class="select" :disabled="reportDisabled">
              <option value="INCORRECT_CONTENT">Incorrect content</option>
              <option value="HALLUCINATION">Made-up information</option>
              <option value="UNSAFE_CONTENT">Unsafe content</option>
              <option value="AGE_INAPPROPRIATE">Age inappropriate</option>
              <option value="OTHER">Other</option>
            </select>
            <textarea
              v-model="reportForm.description"
              class="textarea"
              maxlength="1000"
              :disabled="reportDisabled"
              aria-label="AI report description"
              placeholder="Tell the review team what looked wrong or unsafe."
            />
          </div>
        </div>

        <section class="student-ai-helper-tool-grid">
          <article class="student-ai-helper-tool-card is-purple">
            <span><IconGlyph name="moderation" :size="24" /></span>
            <strong>AI Chat Tutor</strong>
            <p>Ask questions and get friendly explanations.</p>
            <small>“为什么天空是蓝色的?”</small>
            <button type="button" @click="runChat" :disabled="submitting.chat">
              Try it
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-green">
            <span><IconGlyph name="language" :size="24" /></span>
            <strong>Vocabulary Helper</strong>
            <p>Learn new words with meanings and examples.</p>
            <input v-model="wordForm.word" aria-label="Chinese word for vocabulary help" autocomplete="off" placeholder="苹果" />
            <button type="button" @click="runVocabulary" :disabled="submitting.vocab">
              Try it
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-orange">
            <span><IconGlyph name="clip" :size="24" /></span>
            <strong>Sentence Practice</strong>
            <p>Build simple sentences and get feedback.</p>
            <input v-model="sentenceForm.sentence" aria-label="Sentence for AI feedback" autocomplete="off" placeholder="我喜欢学习中文。" />
            <button type="button" @click="runSentence" :disabled="submitting.sentence">
              Try it
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-blue">
            <span><IconGlyph name="live" :size="24" /></span>
            <strong>Pronunciation & Tone</strong>
            <p>{{ audioCapabilityMessage }}</p>
            <input v-model="pronunciationForm.targetText" aria-label="Pronunciation target text" autocomplete="off" placeholder="妈妈" :disabled="audioUploadDisabled" />
            <input v-model="pronunciationForm.pinyin" aria-label="Pronunciation pinyin guide" autocomplete="off" placeholder="mā ma" :disabled="audioUploadDisabled" />
            <input
              type="file"
              accept=".webm,.wav,.mp3,audio/webm,audio/wav,audio/mpeg,audio/mp3"
              aria-label="Upload pronunciation recording"
              :disabled="audioUploadDisabled"
              @change="selectPronunciationFile"
            />
            <button type="button" @click="runPronunciation" :disabled="audioUploadDisabled || submitting.pronunciation">
              {{ audioUploadButtonLabel }}
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-red">
            <span><IconGlyph name="file" :size="24" /></span>
            <strong>Character Help</strong>
            <p>{{ imageCapabilityMessage }}</p>
            <input v-model="writingForm.targetCharacter" aria-label="Target Chinese character for writing feedback" autocomplete="off" placeholder="好" :disabled="imageUploadDisabled" />
            <input
              type="file"
              accept=".png,.jpg,.jpeg,image/png,image/jpeg"
              aria-label="Upload writing image"
              :disabled="imageUploadDisabled"
              @change="selectWritingFile"
            />
            <button type="button" @click="runWriting" :disabled="imageUploadDisabled || submitting.writing">
              {{ imageUploadButtonLabel }}
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-teal">
            <span><IconGlyph name="quiz" :size="24" /></span>
            <strong>Quiz Maker</strong>
            <p>Create a quick self-practice quiz from a topic.</p>
            <input v-model="quizForm.topic" aria-label="Quiz topic" autocomplete="off" placeholder="Greetings" />
            <button type="button" @click="runQuiz" :disabled="submitting.quiz">
              {{ submitting.quiz ? 'Creating...' : 'Create Quiz' }}
              <IconGlyph v-if="!submitting.quiz" name="chevronRight" :size="16" />
            </button>
          </article>

          <article class="student-ai-helper-tool-card is-violet">
            <span><IconGlyph name="shield" :size="24" /></span>
            <strong>Safe Guidance</strong>
            <p>Get learning tips and stay safe online.</p>
            <button type="button" @click="runRecommendation" :disabled="submitting.recommendation">
              Explore
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
            <strong>{{ sentenceReply.correctedSentence || sentenceReply.originalSentence }}</strong>
            <p>{{ sentenceReply.improvementExplanation }}</p>
          </div>
          <div v-if="recommendationReply" class="student-ai-helper-result">
            <strong>{{ recommendationReply.recommendedNextStep }}</strong>
            <p>{{ recommendationReply.weakAreaSummary }}</p>
          </div>
          <div v-if="quizReply" class="student-ai-helper-result">
            <strong>{{ quizReply.title }} - {{ quizReply.status }}</strong>
            <p>{{ quizReply.questions[0]?.questionText || 'Quiz draft is ready.' }}</p>
            <small>{{ quizReply.questions.length }} question{{ quizReply.questions.length === 1 ? '' : 's' }} - {{ quizReply.usageMode }}</small>
          </div>
          <div v-if="pronunciationReply" class="student-ai-helper-result">
            <strong>Pronunciation Feedback - {{ pronunciationReply.status }}</strong>
            <p>{{ pronunciationReply.feedback || pronunciationReply.pronunciationComment }}</p>
            <small>{{ pronunciationReply.toneComment || pronunciationReply.improvementTips }}</small>
          </div>
          <div v-if="writingReply" class="student-ai-helper-result">
            <strong>Writing Feedback - {{ writingReply.status }}</strong>
            <p>{{ writingReply.feedback || writingReply.clarityComment }}</p>
            <small>{{ writingReply.improvementTips || writingReply.encouragement }}</small>
          </div>
        </section>

        <section class="student-ai-helper-footer">
          <span><IconGlyph name="fun" :size="24" /></span>
          <div>
            <strong>SparkBot is here to help you learn and have fun!</strong>
            <p>If you ever feel stuck or need help, just ask. We’ll learn together!</p>
          </div>
          <button type="button" @click="runStory" :disabled="submitting.story">Generate a Story</button>
        </section>
      </div>

      <aside class="student-ai-helper-side">
        <article class="student-ai-helper-status-card">
          <strong><IconGlyph name="shield" :size="18" /> Safe, friendly, supportive AI</strong>
          <span>{{ availability.status }}</span>
          <p>{{ platformSettings.aiDisclaimerText }}</p>
          <small>{{ availability.message }}</small>
        </article>

        <article class="panel student-ai-helper-support-card">
          <strong>How AI Helper Supports You</strong>
          <ul>
            <li>Explains and practices Mandarin with you</li>
            <li>Provides examples and clear explanations</li>
            <li>Gives instant, helpful feedback</li>
            <li>Encourages you to keep learning!</li>
          </ul>
          <div class="student-ai-helper-care-note">
            <strong><IconGlyph name="status" :size="18" /> AI Helper supports your learning.</strong>
            <p>{{ platformSettings.aiDisclaimerText }}</p>
            <span><IconGlyph name="fun" :size="24" /></span>
          </div>
        </article>

        <article class="student-ai-helper-story-card">
          <strong>Story Generator</strong>
          <input v-model="storyForm.topic" aria-label="Story topic" autocomplete="off" placeholder="Greetings at school" />
          <button class="btn btn-primary" type="button" @click="runStory" :disabled="submitting.story">
            {{ submitting.story ? 'Generating...' : 'Create Story' }}
          </button>
          <div v-if="storyReply" class="student-ai-helper-story-result">
            <strong>{{ storyReply.title }}</strong>
            <p>{{ storyReply.chineseStory }}</p>
            <span>{{ storyReply.englishTranslation }}</span>
          </div>
        </article>

        <article class="student-ai-helper-story-card">
          <strong>My AI Reports</strong>
          <div v-if="aiReports.length" class="student-ai-helper-report-list">
            <div v-for="item in aiReports.slice(0, 3)" :key="item.id">
              <span>{{ formatReportReason(item.reasonCategory) }}</span>
              <strong>{{ formatStatus(item.status) }}</strong>
            </div>
          </div>
          <p v-else class="student-ai-helper-empty-note">Reported AI responses will appear here after submission.</p>
        </article>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Student-facing AI helper — chat tutor, quiz generator, story
 * generator, vocabulary helper, sentence practice, pronunciation
 * and writing feedback.
 *
 * <h2>Availability gate</h2>
 * <p>The page calls {@code aiApi.availability()} on mount; when the
 * provider is disabled or mis-configured the UI swaps the chat
 * input for the friendly "AI tutor unavailable" copy and disables
 * generator buttons. Per-feature endpoints still degrade gracefully
 * to deterministic stubs so a learner never sees a 500.</p>
 *
 * <h2>Audio / image uploads</h2>
 * <p>Pronunciation + writing feedback include client-side mime +
 * size validation before submission; the backend re-validates with
 * magic-byte sniffing so the client check is purely a UX nicety.</p>
 *
 * <h2>Reporting</h2>
 * <p>"Report this response" submits through
 * {@code aiApi.reportResponse()} — the report goes into the admin
 * AI moderation queue.</p>
 */
import { computed, reactive, ref } from 'vue'

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

const auth = useAuthStore()
const availabilityResource = useResource(() => aiApi.availability(), aiAvailability)
const progressResource = useResource(() => studentApi.progress(), studentProgress)
const platformSettingsResource = useResource(
  () => publicApi.platformSettings(),
  {
    platformName: appEnv.appName,
    aiDisclaimerText: 'AI-generated content may not always be accurate. Please review before use.',
    strictContentFiltering: true,
    sensitiveWordFilter: true,
    aiSafetyCheck: true,
  },
)

const previewFallbackEnabled = computed(() => appEnv.useDemoData && auth.isPreviewSession)
const emptyAvailability = {
  available: false,
  provider: null,
  model: null,
  status: 'UNAVAILABLE',
  message: 'AI helper is unavailable right now.',
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

const availability = computed(() => availabilityResource.data.value ?? (previewFallbackEnabled.value ? aiAvailability : emptyAvailability))
const progress = computed(() => progressResource.data.value ?? (previewFallbackEnabled.value ? studentProgress : emptyProgress))
const reportsResource = useResource(() => aiApi.reports(), studentAiReports)
const usingFallback = computed(
  () =>
    availabilityResource.usingFallback.value ||
    progressResource.usingFallback.value ||
    reportsResource.usingFallback.value ||
    platformSettingsResource.usingFallback.value,
)
const rawAiReports = computed(() => reportsResource.data.value ?? (reportsResource.usingFallback.value ? studentAiReports : []))
const aiReports = computed(() =>
  rawAiReports.value.filter(
    (item): item is StudentAiResponseReportResponse =>
      Boolean(item?.id && item.reasonCategory && item.status),
  ),
)
const platformSettings = computed(
  () =>
    platformSettingsResource.data.value ?? {
      platformName: appEnv.appName,
      aiDisclaimerText: 'AI-generated content may not always be accurate. Please review before use.',
      strictContentFiltering: true,
      sensitiveWordFilter: true,
      aiSafetyCheck: true,
    },
)
const learnerLevelLabel = computed(() => {
  if (progress.value.lastActivitySummary) return progress.value.lastActivitySummary
  if (progress.value.overallCompletionPercent >= 80) return 'Advanced learner'
  if (progress.value.overallCompletionPercent >= 40) return 'Intermediate learner'
  if (progress.value.totalLessonsStarted > 0) return 'Active beginner'
  return 'New learner'
})
const audioUploadDisabled = computed(() => !previewFallbackEnabled.value && !availability.value.audioAnalysisAvailable)
const imageUploadDisabled = computed(() => !previewFallbackEnabled.value && !availability.value.imageAnalysisAvailable)
const audioCapabilityMessage = computed(() =>
  audioUploadDisabled.value
    ? 'Audio analysis is not supported by the current AI provider.'
    : 'Practice speaking with tones and pronunciation guidance.',
)
const imageCapabilityMessage = computed(() =>
  imageUploadDisabled.value
    ? 'Image analysis is not supported by the current AI provider.'
    : 'Learn characters with strokes and examples.',
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
  if (audioUploadDisabled.value) return 'Audio unavailable'
  return submitting.pronunciation ? 'Checking...' : 'Upload Audio'
})
const imageUploadButtonLabel = computed(() => {
  if (imageUploadDisabled.value) return 'Image unavailable'
  return submitting.writing ? 'Checking...' : 'Upload Writing'
})

const chatForm = reactive({ message: '' })
const wordForm = reactive({ word: '' })
const sentenceForm = reactive({ sentence: '' })
const storyForm = reactive({ topic: '' })
const quizForm = reactive({ topic: 'Greetings' })
const pronunciationForm = reactive({
  targetText: '',
  pinyin: '',
  tone: '',
  file: null as File | null,
})
const writingForm = reactive({
  targetCharacter: '',
  file: null as File | null,
})

const chatReply = ref<AiChatResponse | null>(previewFallbackEnabled.value ? aiChatReply : null)
const vocabularyReply = ref<AiVocabularyHelperResponse | null>(previewFallbackEnabled.value ? aiVocabularyReply : null)
const sentenceReply = ref<AiSentencePracticeResponse | null>(previewFallbackEnabled.value ? aiSentenceReply : null)
const storyReply = ref<AiStoryGeneratorResponse | null>(previewFallbackEnabled.value ? aiStoryReply : null)
const quizReply = ref<AiQuizGeneratorResponse | null>(previewFallbackEnabled.value ? aiQuizReply : null)
const recommendationReply = ref<AiLearningRecommendationResponse | null>(previewFallbackEnabled.value ? aiRecommendationReply : null)
const pronunciationReply = ref<AiPronunciationFeedbackResponse | null>(null)
const writingReply = ref<AiWritingFeedbackResponse | null>(null)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const maxPronunciationFileSize = 10 * 1024 * 1024
const maxWritingFileSize = 5 * 1024 * 1024
const pronunciationExtensions = ['.webm', '.wav', '.mp3']
const writingExtensions = ['.png', '.jpg', '.jpeg']
const reportForm = reactive<{
  reasonCategory: AiResponseReportReason
  description: string
}>({
  reasonCategory: 'INCORRECT_CONTENT',
  description: '',
})
const reportDisabled = computed(() => previewFallbackEnabled.value || submitting.report || !chatReply.value)

type AiResponseStatusCarrier = { status?: string | null }

function aiResponseSucceeded(response: AiResponseStatusCarrier) {
  return response.status?.toUpperCase() === 'SUCCESS'
}

function applyAiResponseStatus(response: AiResponseStatusCarrier, successMessage: string, unavailableMessage: string) {
  if (aiResponseSucceeded(response)) {
    actionError.value = null
    actionSuccess.value = successMessage
    return
  }

  actionSuccess.value = null
  actionError.value = `${unavailableMessage} Status: ${response.status || 'UNAVAILABLE'}.`
}

function hasAllowedExtension(file: File, extensions: string[]) {
  const name = file.name.toLowerCase()
  return extensions.some((extension) => name.endsWith(extension))
}

function showPreviewResponse(message: string) {
  actionError.value = null
  actionSuccess.value = `${message} Preview mode used a safe sample response and did not send a live AI request.`
}

function selectedFile(event: Event) {
  const input = event.target as HTMLInputElement | null
  return input?.files?.[0] ?? null
}

function selectPronunciationFile(event: Event) {
  actionError.value = null
  const file = selectedFile(event)

  if (!file) {
    pronunciationForm.file = null
    return
  }

  if (!hasAllowedExtension(file, pronunciationExtensions) || file.size > maxPronunciationFileSize) {
    pronunciationForm.file = null
    actionError.value = 'Upload a WEBM, WAV, or MP3 recording under 10 MB.'
    return
  }

  pronunciationForm.file = file
}

function selectWritingFile(event: Event) {
  actionError.value = null
  const file = selectedFile(event)

  if (!file) {
    writingForm.file = null
    return
  }

  if (!hasAllowedExtension(file, writingExtensions) || file.size > maxWritingFileSize) {
    writingForm.file = null
    actionError.value = 'Upload a PNG or JPG writing image under 5 MB.'
    return
  }

  writingForm.file = file
}

async function runChat() {
  if (submitting.chat) return
  actionError.value = null
  actionSuccess.value = null
  if (!chatForm.message.trim()) {
    actionError.value = 'Type a question before asking SparkBot.'
    return
  }
  if (previewFallbackEnabled.value) {
    chatReply.value = aiChatReply
    showPreviewResponse('SparkBot sample answer is ready.')
    return
  }
  submitting.chat = true
  try {
    chatReply.value = await aiApi.chat({ message: chatForm.message.trim(), languagePreference: 'en' })
    applyAiResponseStatus(chatReply.value, 'SparkBot answered your question.', 'SparkBot returned safe fallback guidance instead of a live AI answer.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to get an AI chat response.')
    chatReply.value = null
  } finally {
    submitting.chat = false
  }
}

async function runVocabulary() {
  if (submitting.vocab) return
  actionError.value = null
  actionSuccess.value = null
  if (!wordForm.word.trim()) {
    actionError.value = 'Enter a word before asking for vocabulary help.'
    return
  }
  if (previewFallbackEnabled.value) {
    vocabularyReply.value = aiVocabularyReply
    showPreviewResponse('Vocabulary sample help is ready.')
    return
  }
  submitting.vocab = true
  try {
    vocabularyReply.value = await aiApi.vocabularyHelper({ word: wordForm.word.trim(), inputLanguage: 'zh', outputLanguage: 'en' })
    applyAiResponseStatus(vocabularyReply.value, 'Vocabulary help is ready.', 'Vocabulary helper returned safe fallback guidance instead of a live AI answer.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to get vocabulary help.')
    vocabularyReply.value = null
  } finally {
    submitting.vocab = false
  }
}

async function runSentence() {
  if (submitting.sentence) return
  actionError.value = null
  actionSuccess.value = null
  if (!sentenceForm.sentence.trim()) {
    actionError.value = 'Enter a sentence before asking for feedback.'
    return
  }
  if (previewFallbackEnabled.value) {
    sentenceReply.value = aiSentenceReply
    showPreviewResponse('Sentence sample feedback is ready.')
    return
  }
  submitting.sentence = true
  try {
    sentenceReply.value = await aiApi.sentencePractice({ sentence: sentenceForm.sentence.trim(), learnerLevel: learnerLevelLabel.value })
    applyAiResponseStatus(sentenceReply.value, 'Sentence feedback is ready.', 'Sentence practice returned safe fallback guidance instead of a live AI answer.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to review this sentence.')
    sentenceReply.value = null
  } finally {
    submitting.sentence = false
  }
}

async function runStory() {
  if (submitting.story) return
  actionError.value = null
  actionSuccess.value = null
  if (!storyForm.topic.trim()) {
    actionError.value = 'Enter a story topic before generating a story.'
    return
  }
  if (previewFallbackEnabled.value) {
    storyReply.value = aiStoryReply
    showPreviewResponse('Story sample is ready.')
    return
  }
  submitting.story = true
  try {
    storyReply.value = await aiApi.storyGenerator({ topic: storyForm.topic.trim(), level: learnerLevelLabel.value, languagePreference: 'en' })
    applyAiResponseStatus(storyReply.value, 'Your Mandarin story is ready.', 'Story generator returned safe fallback guidance instead of a live AI answer.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to generate this story.')
    storyReply.value = null
  } finally {
    submitting.story = false
  }
}

async function runRecommendation() {
  if (submitting.recommendation) return
  actionError.value = null
  actionSuccess.value = null
  if (previewFallbackEnabled.value) {
    recommendationReply.value = aiRecommendationReply
    showPreviewResponse('Learning recommendation sample is ready.')
    return
  }
  submitting.recommendation = true
  try {
    recommendationReply.value = await aiApi.learningRecommendation({
      currentLevel: learnerLevelLabel.value,
      weakAreas: progress.value.weakAreas,
      languagePreference: 'en',
    })
    applyAiResponseStatus(recommendationReply.value, 'A learning recommendation is ready.', 'Recommendation helper returned safe fallback guidance instead of a live AI answer.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to generate this recommendation.')
    recommendationReply.value = null
  } finally {
    submitting.recommendation = false
  }
}

async function runQuiz() {
  if (submitting.quiz) return
  actionError.value = null
  actionSuccess.value = null
  if (!quizForm.topic.trim()) {
    actionError.value = 'Enter a quiz topic before creating a practice quiz.'
    return
  }
  if (previewFallbackEnabled.value) {
    quizReply.value = aiQuizReply
    showPreviewResponse('Practice quiz sample is ready.')
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
    applyAiResponseStatus(quizReply.value, 'Practice quiz is ready.', 'Quiz maker returned safe fallback guidance instead of a live AI quiz.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to create this practice quiz.')
    quizReply.value = null
  } finally {
    submitting.quiz = false
  }
}

async function submitAiReport() {
  if (submitting.report) return
  actionError.value = null
  actionSuccess.value = null
  if (!chatReply.value) {
    actionError.value = 'Ask SparkBot a question before reporting a response.'
    return
  }
  if (previewFallbackEnabled.value) {
    showPreviewResponse('AI report preview is visible.')
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
    actionSuccess.value = 'Thanks. The AI response was sent to the review team.'
    await reportsResource.reload()
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to report this AI response.')
  } finally {
    submitting.report = false
  }
}

async function runPronunciation() {
  if (submitting.pronunciation) return
  actionError.value = null
  actionSuccess.value = null
  if (audioUploadDisabled.value) {
    actionError.value = 'Pronunciation audio analysis is not supported by the current AI provider.'
    return
  }
  if (!pronunciationForm.targetText.trim()) {
    actionError.value = 'Enter the target text before requesting pronunciation feedback.'
    return
  }
  if (previewFallbackEnabled.value) {
    pronunciationReply.value = null
    showPreviewResponse('Pronunciation upload preview is visible.')
    return
  }
  if (!pronunciationForm.file) {
    actionError.value = 'Upload a WEBM, WAV, or MP3 recording first.'
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
    applyAiResponseStatus(pronunciationReply.value, 'Pronunciation recording uploaded and feedback is ready.', 'Pronunciation analysis is unavailable for the current AI provider.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to process pronunciation feedback.')
    pronunciationReply.value = null
  } finally {
    submitting.pronunciation = false
  }
}

async function runWriting() {
  if (submitting.writing) return
  actionError.value = null
  actionSuccess.value = null
  if (imageUploadDisabled.value) {
    actionError.value = 'Writing image analysis is not supported by the current AI provider.'
    return
  }
  if (!writingForm.targetCharacter.trim()) {
    actionError.value = 'Enter the target character before requesting writing feedback.'
    return
  }
  if (previewFallbackEnabled.value) {
    writingReply.value = null
    showPreviewResponse('Writing upload preview is visible.')
    return
  }
  if (!writingForm.file) {
    actionError.value = 'Upload a PNG or JPG writing image first.'
    return
  }
  submitting.writing = true
  try {
    const formData = new FormData()
    formData.append('file', writingForm.file)
    formData.append('targetCharacter', writingForm.targetCharacter.trim())
    formData.append('languagePreference', 'en')
    writingReply.value = await aiApi.writingFeedback(formData)
    applyAiResponseStatus(writingReply.value, 'Writing image uploaded and feedback is ready.', 'Writing image analysis is unavailable for the current AI provider.')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to process writing feedback.')
    writingReply.value = null
  } finally {
    submitting.writing = false
  }
}

function formatEnumLabel(value: string) {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

const formatReportReason = formatEnumLabel
const formatStatus = formatEnumLabel
</script>
