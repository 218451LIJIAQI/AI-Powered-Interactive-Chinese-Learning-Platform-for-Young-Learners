<template>
  <div class="page-stack student-ai-helper-page">
    <PageHeaderBlock :title="t('studentAi.pageTitle')" :description="t('studentAi.pageDescription')" icon="ai">
      <template #meta>
        <StatusPill :label="availabilityLabel" :tone="availabilityTone" icon="ai" />
      </template>
      <template #actions>
        <RouterLink class="btn btn-secondary" to="/student/practice-submissions">
          <IconGlyph name="mic" :size="18" />
          {{ t('studentAi.viewSubmissions') }}
        </RouterLink>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="usingFallback"
      :error="pageError"
      :success="actionSuccess"
      :fallback-title="t('studentAi.previewTitle')"
      :fallback-message="t('studentAi.previewMessage')"
      :success-title="t('studentAi.successTitle')"
    >
      <template v-if="loadError" #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="reloadAll">
          {{ t('studentAi.tryAgain') }}
        </button>
      </template>
    </DataNotice>

    <div class="student-ai-helper-shell">
      <div class="student-ai-helper-main">
        <section class="panel student-ai-helper-chat" :aria-labelledby="`${baseId}-chat`">
          <header class="student-ai-helper-chat-head">
            <span class="student-ai-helper-avatar" aria-hidden="true">
              <PandaIllustration :size="52" outfit="#6f4cff" accent="#ffcc2f" pose="wave" compact />
            </span>
            <div class="student-ai-helper-chat-copy">
              <h2 :id="`${baseId}-chat`" class="card-title">{{ t('studentAi.chatTitle') }}</h2>
              <p class="card-subtitle">{{ t('studentAi.hero') }}</p>
            </div>
          </header>

          <div class="student-ai-helper-thread">
            <template v-if="chatReply">
              <div v-if="chatReply.userMessage" class="student-ai-helper-message is-user">
                <span class="student-ai-helper-message-author">{{ t('studentAi.youLabel') }}</span>
                <p class="student-ai-helper-bubble">{{ chatReply.userMessage }}</p>
              </div>
              <div class="student-ai-helper-message is-bot" data-testid="ai-chat-reply" :aria-busy="submitting.chat">
                <div class="student-ai-helper-message-head">
                  <span class="student-ai-helper-message-author">SparkBot</span>
                  <AiResultLabel />
                </div>
                <div class="student-ai-helper-bubble">
                  <p v-if="displayedChatReply">{{ displayedChatReply }}</p>
                  <p v-else-if="submitting.chat" class="student-ai-helper-typing">
                    <span class="spinner is-sm" aria-hidden="true" />
                    {{ t('studentAi.answering') }}
                  </p>
                </div>
                <AiReportButton
                  v-if="chatReply.aiResponse"
                  feature-key="CHAT_TUTOR"
                  :response-snapshot="chatReply.aiResponse"
                  :prompt-snapshot="chatReply.userMessage"
                  :chat-session-id="chatReply.sessionId"
                  :preview="previewFallbackEnabled"
                  @reported="onReported"
                />
              </div>
            </template>
            <p v-else class="student-ai-helper-thread-empty">{{ t('studentAi.tryAsking') }}</p>
          </div>

          <div class="student-ai-helper-suggestions" role="group" :aria-label="t('studentAi.tryAsking')">
            <button
              class="student-ai-helper-chip"
              type="button"
              @click="fillChatSuggestion(t('studentAi.helloSuggestion'))"
            >
              <IconGlyph name="moderation" :size="16" />
              {{ t('studentAi.helloSuggestion') }}
            </button>
            <button class="student-ai-helper-chip" type="button" @click="fillWordSuggestion('食堂')">
              <IconGlyph name="learn" :size="16" />
              {{ t('studentAi.appleSuggestion') }}
            </button>
            <button class="student-ai-helper-chip" type="button" @click="fillSentenceSuggestion('我是大学生。')">
              <IconGlyph name="pen" :size="16" />
              {{ t('studentAi.readSentence') }}
            </button>
          </div>

          <form class="student-ai-helper-chatbar" @submit.prevent="runChat">
            <input
              v-model="chatForm.message"
              class="input"
              :aria-label="t('studentAi.askAria')"
              autocomplete="off"
              :placeholder="t('studentAi.askPlaceholder')"
            />
            <button
              class="btn btn-primary btn-icon student-ai-helper-send"
              type="submit"
              :disabled="submitting.chat"
              :aria-busy="submitting.chat"
              :aria-label="chatSubmitAriaLabel"
            >
              <IconGlyph v-if="!submitting.chat" name="chevronRight" :size="20" />
            </button>
          </form>

          <AiDisclaimer :text="platformSettings.aiDisclaimerText" />
        </section>

        <section class="student-ai-helper-section" :aria-labelledby="`${baseId}-tools`">
          <div class="section-header">
            <div>
              <h2 :id="`${baseId}-tools`" class="section-title">{{ t('studentAi.toolsTitle') }}</h2>
              <p class="section-description">{{ t('studentAi.toolsDescription') }}</p>
            </div>
          </div>

          <div class="student-ai-helper-tool-grid">
            <article class="student-ai-helper-tool-card is-purple">
              <header class="student-ai-helper-tool-head">
                <span class="student-ai-helper-tool-icon" aria-hidden="true">
                  <IconGlyph name="moderation" :size="22" />
                </span>
                <h3>{{ t('studentAi.chatTutor') }}</h3>
              </header>
              <p>{{ t('studentAi.chatHint') }}</p>
              <p class="student-ai-helper-tool-sample">“{{ t('studentAi.chatSamplePrompt') }}”</p>
              <button class="btn btn-secondary" type="button" :disabled="submitting.chat" @click="tryChatSample">
                {{ t('studentAi.tryIt') }}
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </article>

            <article class="student-ai-helper-tool-card is-green">
              <header class="student-ai-helper-tool-head">
                <span class="student-ai-helper-tool-icon" aria-hidden="true">
                  <IconGlyph name="language" :size="22" />
                </span>
                <h3>{{ t('studentAi.vocabulary') }}</h3>
              </header>
              <p>{{ t('studentAi.vocabularyHint') }}</p>
              <div class="field">
                <label class="field-label" :for="`${baseId}-word`">{{ t('studentAi.fieldWord') }}</label>
                <input
                  :id="`${baseId}-word`"
                  v-model="wordForm.word"
                  class="input"
                  autocomplete="off"
                  placeholder="食堂"
                />
              </div>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="submitting.vocab"
                :aria-busy="submitting.vocab"
                @click="runVocabulary"
              >
                {{ t('studentAi.tryIt') }}
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </article>

            <article class="student-ai-helper-tool-card is-orange">
              <header class="student-ai-helper-tool-head">
                <span class="student-ai-helper-tool-icon" aria-hidden="true">
                  <IconGlyph name="clip" :size="22" />
                </span>
                <h3>{{ t('studentAi.sentence') }}</h3>
              </header>
              <p>{{ t('studentAi.sentenceHint') }}</p>
              <div class="field">
                <label class="field-label" :for="`${baseId}-sentence`">{{ t('studentAi.fieldSentence') }}</label>
                <input
                  :id="`${baseId}-sentence`"
                  v-model="sentenceForm.sentence"
                  class="input"
                  autocomplete="off"
                  placeholder="我喜欢学习中文。"
                />
              </div>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="submitting.sentence"
                :aria-busy="submitting.sentence"
                @click="runSentence"
              >
                {{ t('studentAi.tryIt') }}
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </article>

            <article class="student-ai-helper-tool-card is-teal">
              <header class="student-ai-helper-tool-head">
                <span class="student-ai-helper-tool-icon" aria-hidden="true">
                  <IconGlyph name="quiz" :size="22" />
                </span>
                <h3>{{ t('studentAi.quizMaker') }}</h3>
              </header>
              <p>{{ t('studentAi.quizHint') }}</p>
              <div class="field">
                <label class="field-label" :for="`${baseId}-quiz`">{{ t('studentAi.fieldTopic') }}</label>
                <input
                  :id="`${baseId}-quiz`"
                  v-model="quizForm.topic"
                  class="input"
                  autocomplete="off"
                  :placeholder="t('studentAi.quizPlaceholder')"
                />
              </div>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="submitting.quiz"
                :aria-busy="submitting.quiz"
                @click="runQuiz"
              >
                {{ quizButtonLabel }}
                <IconGlyph v-if="!submitting.quiz" name="chevronRight" :size="16" />
              </button>
            </article>

            <article class="student-ai-helper-tool-card is-blue">
              <header class="student-ai-helper-tool-head">
                <span class="student-ai-helper-tool-icon" aria-hidden="true">
                  <IconGlyph name="mic" :size="22" />
                </span>
                <h3>{{ t('studentAi.pronunciation') }}</h3>
              </header>
              <p>{{ audioCapabilityMessage }}</p>
              <div class="student-ai-helper-field-row">
                <div class="field">
                  <label class="field-label" :for="`${baseId}-target`">{{ t('studentAi.fieldTarget') }}</label>
                  <input
                    :id="`${baseId}-target`"
                    v-model="pronunciationForm.targetText"
                    class="input"
                    autocomplete="off"
                    placeholder="你好"
                  />
                </div>
                <div class="field">
                  <label class="field-label" :for="`${baseId}-pinyin`">{{ t('studentAi.fieldPinyin') }}</label>
                  <input
                    :id="`${baseId}-pinyin`"
                    v-model="pronunciationForm.pinyin"
                    class="input"
                    autocomplete="off"
                    placeholder="nǐ hǎo"
                  />
                </div>
              </div>
              <div class="field">
                <label class="field-label" :for="`${baseId}-audio`">{{ t('studentAi.fieldAudio') }}</label>
                <input
                  :id="`${baseId}-audio`"
                  class="input"
                  type="file"
                  :accept="UPLOAD_ACCEPT.audio"
                  @change="selectPronunciationFile"
                />
              </div>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="submitting.pronunciation"
                :aria-busy="submitting.pronunciation"
                @click="runPronunciation"
              >
                <IconGlyph v-if="!submitting.pronunciation" name="upload" :size="16" />
                {{ submitting.pronunciation ? t('studentAi.checking') : t('studentAi.uploadAudio') }}
              </button>
              <RouterLink
                class="student-ai-helper-inline-link"
                to="/student/practice-submissions"
                data-testid="ai-practice-submissions-link"
              >
                {{ t('studentAi.submissionsLink') }}
              </RouterLink>
            </article>

            <article class="student-ai-helper-tool-card is-red">
              <header class="student-ai-helper-tool-head">
                <span class="student-ai-helper-tool-icon" aria-hidden="true">
                  <IconGlyph name="pen" :size="22" />
                </span>
                <h3>{{ t('studentAi.characterHelp') }}</h3>
              </header>
              <p>{{ imageCapabilityMessage }}</p>
              <div class="field">
                <label class="field-label" :for="`${baseId}-character`">{{ t('studentAi.fieldCharacter') }}</label>
                <input
                  :id="`${baseId}-character`"
                  v-model="writingForm.targetCharacter"
                  class="input"
                  autocomplete="off"
                  placeholder="好"
                />
              </div>
              <div class="field">
                <label class="field-label" :for="`${baseId}-image`">{{ t('studentAi.fieldImage') }}</label>
                <input
                  :id="`${baseId}-image`"
                  class="input"
                  type="file"
                  :accept="UPLOAD_ACCEPT.image"
                  @change="selectWritingFile"
                />
              </div>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="submitting.writing"
                :aria-busy="submitting.writing"
                @click="runWriting"
              >
                <IconGlyph v-if="!submitting.writing" name="upload" :size="16" />
                {{ submitting.writing ? t('studentAi.checking') : t('studentAi.uploadWriting') }}
              </button>
              <RouterLink
                class="student-ai-helper-inline-link"
                to="/student/practice-submissions"
                data-testid="ai-practice-submissions-link"
              >
                {{ t('studentAi.submissionsLink') }}
              </RouterLink>
            </article>
          </div>
        </section>

        <section v-if="hasToolResults" class="student-ai-helper-section" :aria-labelledby="`${baseId}-results`">
          <div class="section-header">
            <div>
              <h2 :id="`${baseId}-results`" class="section-title">{{ t('studentAi.resultsTitle') }}</h2>
              <p class="section-description">{{ t('studentAi.resultsDescription') }}</p>
            </div>
          </div>

          <div class="student-ai-helper-result-grid">
            <article v-if="vocabularyReply" class="student-ai-helper-result" data-testid="ai-vocabulary-result">
              <div class="student-ai-helper-result-head">
                <span class="student-ai-helper-result-kicker">{{ t('studentAi.vocabulary') }}</span>
                <AiResultLabel />
              </div>
              <strong class="student-ai-helper-result-title">
                {{ vocabularyReply.chineseWord }} - {{ vocabularyReply.pinyin }}
              </strong>
              <p>{{ vocabularyReply.englishMeaning }} / {{ vocabularyReply.bahasaMelayuMeaning }}</p>
              <small>{{ vocabularyReply.exampleSentence }}</small>
              <AiReportButton
                feature-key="VOCABULARY_HELPER"
                :response-snapshot="vocabularySnapshot"
                :prompt-snapshot="lastPrompts.vocabulary"
                :preview="previewFallbackEnabled"
                @reported="onReported"
              />
            </article>
            <article v-if="sentenceReply" class="student-ai-helper-result" data-testid="ai-sentence-result">
              <div class="student-ai-helper-result-head">
                <span class="student-ai-helper-result-kicker">{{ t('studentAi.sentence') }}</span>
                <AiResultLabel />
              </div>
              <strong class="student-ai-helper-result-title">{{ sentenceResultTitle }}</strong>
              <p>{{ sentenceReply.improvementExplanation }}</p>
              <AiReportButton
                feature-key="SENTENCE_PRACTICE"
                :response-snapshot="sentenceSnapshot"
                :prompt-snapshot="lastPrompts.sentence"
                :preview="previewFallbackEnabled"
                @reported="onReported"
              />
            </article>
            <article v-if="pronunciationReply" class="student-ai-helper-result" data-testid="ai-pronunciation-result">
              <div class="student-ai-helper-result-head">
                <span class="student-ai-helper-result-kicker">{{ t('studentAi.pronunciation') }}</span>
                <AiResultLabel feedback />
              </div>
              <strong class="student-ai-helper-result-title">{{
                t('studentAi.pronunciationFeedback', { status: formatStatus(pronunciationReply.status) })
              }}</strong>
              <p v-if="pronunciationFeedbackText">{{ pronunciationFeedbackText }}</p>
              <small v-if="pronunciationSupportText">{{ pronunciationSupportText }}</small>
              <small v-if="pronunciationReply.notice">{{ pronunciationReply.notice }}</small>
              <small v-if="pronunciationReply.submissionId" data-testid="ai-pronunciation-submission">
                {{ t('studentAi.submissionSaved', { id: pronunciationReply.submissionId }) }}
                <RouterLink class="student-ai-helper-inline-link" to="/student/practice-submissions">{{
                  t('studentAi.viewSubmissions')
                }}</RouterLink>
              </small>
              <AiReportButton
                v-if="pronunciationSnapshot"
                feature-key="PRONUNCIATION_FEEDBACK"
                :response-snapshot="pronunciationSnapshot"
                :prompt-snapshot="lastPrompts.pronunciation"
                :preview="previewFallbackEnabled"
                @reported="onReported"
              />
            </article>
            <article v-if="writingReply" class="student-ai-helper-result" data-testid="ai-writing-result">
              <div class="student-ai-helper-result-head">
                <span class="student-ai-helper-result-kicker">{{ t('studentAi.characterHelp') }}</span>
                <AiResultLabel feedback />
              </div>
              <strong class="student-ai-helper-result-title">{{
                t('studentAi.writingFeedback', { status: formatStatus(writingReply.status) })
              }}</strong>
              <p v-if="writingFeedbackText">{{ writingFeedbackText }}</p>
              <small v-if="writingSupportText">{{ writingSupportText }}</small>
              <small v-if="writingReply.notice">{{ writingReply.notice }}</small>
              <small v-if="writingReply.submissionId" data-testid="ai-writing-submission">
                {{ t('studentAi.submissionSaved', { id: writingReply.submissionId }) }}
                <RouterLink class="student-ai-helper-inline-link" to="/student/practice-submissions">{{
                  t('studentAi.viewSubmissions')
                }}</RouterLink>
              </small>
              <AiReportButton
                v-if="writingSnapshot"
                feature-key="WRITING_FEEDBACK"
                :response-snapshot="writingSnapshot"
                :prompt-snapshot="lastPrompts.writing"
                :preview="previewFallbackEnabled"
                @reported="onReported"
              />
            </article>
            <article v-if="quizReply" class="student-ai-helper-result is-wide" data-testid="ai-quiz-result">
              <div class="student-ai-helper-result-head">
                <span class="student-ai-helper-result-kicker">{{ t('studentAi.quizMaker') }}</span>
                <AiResultLabel />
              </div>
              <strong class="student-ai-helper-result-title">
                {{ quizReply.title }} - {{ formatStatus(quizReply.status) }}
              </strong>
              <small>{{ quizQuestionCountLabel }}</small>
              <AiPracticeQuiz :quiz="quizReply" />
              <AiReportButton
                feature-key="QUIZ_GENERATOR"
                :response-snapshot="quizSnapshot"
                :prompt-snapshot="lastPrompts.quiz"
                :preview="previewFallbackEnabled"
                @reported="onReported"
              />
            </article>
          </div>
        </section>

        <section class="panel student-ai-helper-story" :aria-labelledby="`${baseId}-story`">
          <div class="card-header">
            <div>
              <h2 :id="`${baseId}-story`" class="card-title">{{ t('studentAi.storyGenerator') }}</h2>
              <p class="card-subtitle">{{ t('studentAi.storyDescription') }}</p>
            </div>
          </div>
          <form class="student-ai-helper-story-form" @submit.prevent="runStory">
            <div class="field">
              <label class="field-label" :for="`${baseId}-story-topic`">{{ t('studentAi.fieldTopic') }}</label>
              <input
                :id="`${baseId}-story-topic`"
                v-model="storyForm.topic"
                class="input"
                autocomplete="off"
                :placeholder="t('studentAi.storyPlaceholder')"
              />
            </div>
            <button class="btn btn-primary" type="submit" :disabled="submitting.story" :aria-busy="submitting.story">
              <IconGlyph v-if="!submitting.story" name="fun" :size="18" />
              {{ storyButtonLabel }}
            </button>
          </form>
          <AiDisclaimer :text="platformSettings.aiDisclaimerText" :show-guidance-link="false" />
          <div v-if="storyReply" class="student-ai-helper-story-result" data-testid="ai-story-result">
            <AiResultLabel />
            <AiStoryReader :story="storyReply" :language="languagePreference" />
            <AiReportButton
              feature-key="STORY_GENERATOR"
              :response-snapshot="storySnapshot"
              :prompt-snapshot="lastPrompts.story"
              :preview="previewFallbackEnabled"
              @reported="onReported"
            />
          </div>
        </section>
      </div>

      <aside class="student-ai-helper-side">
        <article class="panel student-ai-helper-side-card">
          <h2 class="card-title student-ai-helper-side-title">
            <IconGlyph name="shield" :size="18" />
            {{ t('studentAi.aiStatus') }}
          </h2>
          <StatusPill :label="availabilityLabel" :tone="availabilityTone" />
          <p v-if="!availabilityResource.loading.value">{{ availability.message }}</p>
          <p class="student-ai-helper-side-muted">
            {{ platformSettings.aiDisclaimerText || t('aiSafety.disclaimer') }}
          </p>
        </article>

        <article class="panel student-ai-helper-side-card">
          <h2 class="card-title student-ai-helper-side-title">{{ t('studentAi.supportTitle') }}</h2>
          <ul class="student-ai-helper-support-list">
            <li><IconGlyph name="status" :size="16" />{{ t('studentAi.supportOne') }}</li>
            <li><IconGlyph name="status" :size="16" />{{ t('studentAi.supportTwo') }}</li>
            <li><IconGlyph name="status" :size="16" />{{ t('studentAi.supportThree') }}</li>
            <li><IconGlyph name="status" :size="16" />{{ t('studentAi.supportFour') }}</li>
          </ul>
          <div class="student-ai-helper-care-note">
            <strong>{{ t('studentAi.supportNote') }}</strong>
            <RouterLink class="student-ai-helper-inline-link" :to="{ name: 'responsible-ai' }">{{
              t('aiSafety.guidanceLink')
            }}</RouterLink>
          </div>
        </article>

        <article class="panel student-ai-helper-side-card">
          <h2 class="card-title student-ai-helper-side-title">{{ t('studentAi.myReports') }}</h2>
          <StatePanel
            v-if="reportsResource.loading.value"
            tone="loading"
            icon="progress"
            :title="t('studentAi.reportsLoading')"
            :message="t('studentAi.reportsLoadingHint')"
            :skeleton="false"
            compact
          />
          <StatePanel
            v-else-if="reportsResource.error.value && !aiReports.length"
            tone="error"
            icon="alert"
            :title="t('studentAi.reportsErrorTitle')"
            :message="reportsResource.error.value"
            compact
          >
            <template #actions>
              <button class="btn btn-secondary btn-sm" type="button" @click="reportsResource.reload">
                {{ t('studentAi.tryAgain') }}
              </button>
            </template>
          </StatePanel>
          <ul v-else-if="aiReports.length" class="student-ai-helper-report-list">
            <li v-for="item in aiReports.slice(0, 3)" :key="item.id">
              <span>{{ formatReportReason(item.reasonCategory) }}</span>
              <StatusPill :label="formatStatus(item.status)" :tone="reportTone(item.status)" />
            </li>
          </ul>
          <p v-else class="student-ai-helper-side-muted">{{ t('studentAi.noReports') }}</p>
        </article>

        <article class="student-ai-helper-tip">
          <span class="student-ai-helper-tip-icon" aria-hidden="true"><IconGlyph name="hint" :size="20" /></span>
          <div>
            <strong>{{ t('studentAi.footerTitle') }}</strong>
            <p>{{ t('studentAi.footerHint') }}</p>
          </div>
        </article>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, useId } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { aiApi, publicApi, studentApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import AiPracticeQuiz from '@/components/ai/AiPracticeQuiz.vue'
import AiReportButton from '@/components/ai/AiReportButton.vue'
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import AiStoryReader from '@/components/ai/AiStoryReader.vue'
import { appEnv } from '@/config/env'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import {
  aiAvailability,
  aiChatReply,
  aiPronunciationFeedbackReply,
  aiQuizReply,
  aiSentenceReply,
  aiStoryReply,
  aiVocabularyReply,
  aiWritingFeedbackReply,
  studentAiReports,
  studentProgress,
} from '@/demo/data'
import { AI_REPORT_REASON_KEYS, aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import { studentAiHelperMessages } from '@/plugins/studentAiHelperMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type {
  AiChatResponse,
  AiPronunciationFeedbackResponse,
  AiQuizGeneratorResponse,
  AiResponseReportReason,
  AiSentencePracticeResponse,
  AiStoryGeneratorResponse,
  AiVocabularyHelperResponse,
  AiWritingFeedbackResponse,
  PublicPlatformSettingsResponse,
  StudentAiResponseReportResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, nonNegativeInteger } from '@/utils/format'
import {
  buildPronunciationFormData,
  buildWritingFormData,
  isAcceptedUploadType,
  MAX_PRACTICE_UPLOAD_BYTES,
  UPLOAD_ACCEPT,
} from '@/utils/practiceUpload'
import { emptyStudentProgress } from '@/utils/progressSummary'

const { t } = useI18n({
  messages: {
    en: { studentAi: studentAiHelperMessages.en, aiSafety: aiSafetyMessages.en },
    ms: { studentAi: studentAiHelperMessages.ms, aiSafety: aiSafetyMessages.ms },
    'zh-CN': { studentAi: studentAiHelperMessages['zh-CN'], aiSafety: aiSafetyMessages['zh-CN'] },
  },
})

const baseId = useId()
const auth = useAuthStore()
const ui = useUiStore()
/** D18: every AI request uses the learner's current interface language (en | ms | zh-CN). */
const languagePreference = computed(() => ui.locale)

function defaultPlatformSettings(): PublicPlatformSettingsResponse {
  return {
    platformName: appEnv.appName,
    aiDisclaimerText: t('aiSafety.disclaimer'),
  }
}

const availabilityResource = useResource(() => aiApi.availability(), aiAvailability)
const progressResource = useResource(() => studentApi.progress(), studentProgress)
const platformSettingsResource = useResource(() => publicApi.platformSettings(), defaultPlatformSettings)

const previewFallbackEnabled = computed(() => appEnv.useDemoData && auth.isPreviewSession)
const emptyAvailability = computed(() => ({
  available: false,
  provider: null,
  model: null,
  status: 'UNAVAILABLE',
  message: t('studentAi.unavailable'),
  textGenerationAvailable: false,
  audioAnalysisAvailable: false,
  imageAnalysisAvailable: false,
}))
const emptyProgress = emptyStudentProgress()

const availability = computed(
  () => availabilityResource.data.value ?? (previewFallbackEnabled.value ? aiAvailability : emptyAvailability.value),
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
const rawAiReports = computed(() => reportsResource.data.value ?? [])
const aiReports = computed(() =>
  rawAiReports.value.filter((item): item is StudentAiResponseReportResponse =>
    Boolean(item?.id && item.reasonCategory && item.status),
  ),
)
const platformSettings = computed(() => platformSettingsResource.data.value ?? defaultPlatformSettings())
/**
 * Level hint sent to the AI tools. It is derived from progress only: `lastActivitySummary` is the latest lesson
 * title (up to 200 characters), not a level, and would exceed the 50 / 100 character level limits of the API.
 */
const learnerLevelLabel = computed(() => {
  const overallCompletionPercent = clampPercent(progress.value.overallCompletionPercent)
  if (overallCompletionPercent >= 80) return t('studentAi.advanced')
  if (overallCompletionPercent >= 40) return t('studentAi.intermediate')
  if (nonNegativeInteger(progress.value.totalLessonsStarted) > 0) return t('studentAi.activeBeginner')
  return t('studentAi.newLearner')
})
/*
 * Uploads stay possible even when the model cannot analyse audio/images: the backend stores the
 * file for teacher review and answers status UNAVAILABLE with an explanatory message.
 */
const audioCapabilityMessage = computed(() =>
  availability.value.audioAnalysisAvailable ? t('studentAi.audioHint') : t('studentAi.audioUnsupported'),
)
const imageCapabilityMessage = computed(() =>
  availability.value.imageAnalysisAvailable ? t('studentAi.imageHint') : t('studentAi.imageUnsupported'),
)
const submitting = reactive({
  chat: false,
  vocab: false,
  sentence: false,
  story: false,
  quiz: false,
  pronunciation: false,
  writing: false,
})

const chatForm = reactive({ message: '' })
const wordForm = reactive({ word: '' })
const sentenceForm = reactive({ sentence: '' })
const storyForm = reactive({ topic: '' })
const quizForm = reactive({ topic: '' })
interface PronunciationForm {
  targetText: string
  pinyin: string
  file: File | null
}

interface WritingForm {
  targetCharacter: string
  file: File | null
}

const pronunciationForm = reactive<PronunciationForm>({
  targetText: '',
  pinyin: '',
  file: null,
})
const writingForm = reactive<WritingForm>({
  targetCharacter: '',
  file: null,
})

/** Learner input behind each displayed result, sent as the report promptSnapshot. */
const lastPrompts = reactive({
  vocabulary: previewFallbackEnabled.value ? aiVocabularyReply.chineseWord : '',
  sentence: previewFallbackEnabled.value ? aiSentenceReply.originalSentence : '',
  quiz: previewFallbackEnabled.value ? aiQuizReply.topic : '',
  story: previewFallbackEnabled.value ? aiStoryReply.title : '',
  pronunciation: '',
  writing: '',
})

const chatReply = ref<AiChatResponse | null>(previewFallbackEnabled.value ? aiChatReply : null)
const streamingChatText = ref('')
const vocabularyReply = ref<AiVocabularyHelperResponse | null>(previewFallbackEnabled.value ? aiVocabularyReply : null)
const sentenceReply = ref<AiSentencePracticeResponse | null>(previewFallbackEnabled.value ? aiSentenceReply : null)
const storyReply = ref<AiStoryGeneratorResponse | null>(previewFallbackEnabled.value ? aiStoryReply : null)
const quizReply = ref<AiQuizGeneratorResponse | null>(previewFallbackEnabled.value ? aiQuizReply : null)
const pronunciationReply = ref<AiPronunciationFeedbackResponse | null>(null)
const writingReply = ref<AiWritingFeedbackResponse | null>(null)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
// A reports error with no rows is already shown (with a retry) in the "My reports" panel, so it stays out of the notice.
const loadError = computed(
  () =>
    availabilityResource.error.value ||
    progressResource.error.value ||
    (aiReports.value.length ? reportsResource.error.value : null) ||
    platformSettingsResource.error.value,
)
const pageError = computed(() => actionError.value || loadError.value)
type PillTone = 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'neutral'

const availabilityLabel = computed(() =>
  availabilityResource.loading.value ? t('studentAi.statusChecking') : formatStatus(availability.value.status),
)
const availabilityTone = computed<PillTone>(() => {
  if (availabilityResource.loading.value) return 'neutral'
  const status = availability.value.status?.toUpperCase()
  if (status === 'AVAILABLE') return 'success'
  return 'danger'
})
const hasToolResults = computed(() =>
  Boolean(
    vocabularyReply.value || sentenceReply.value || pronunciationReply.value || writingReply.value || quizReply.value,
  ),
)

function reloadAll(): void {
  void availabilityResource.reload()
  void progressResource.reload()
  void reportsResource.reload()
  void platformSettingsResource.reload()
}

function reportTone(status: string): PillTone {
  const value = status.toUpperCase()
  if (value === 'RESOLVED') return 'success'
  if (value === 'DISMISSED') return 'neutral'
  if (value === 'IN_REVIEW') return 'info'
  return 'warning'
}

const chatSubmitAriaLabel = computed(() => (submitting.chat ? t('studentAi.answering') : t('studentAi.sendQuestion')))
const displayedChatReply = computed(() => streamingChatText.value || chatReply.value?.aiResponse || '')
const quizButtonLabel = computed(() => (submitting.quiz ? t('studentAi.creating') : t('studentAi.createQuiz')))
const storyButtonLabel = computed(() => (submitting.story ? t('studentAi.generating') : t('studentAi.createStory')))
const sentenceResultTitle = computed(
  () => sentenceReply.value?.correctedSentence || sentenceReply.value?.originalSentence || '',
)
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

function joinSnapshot(parts: Array<string | null | undefined>): string {
  return parts
    .map((part) => part?.trim())
    .filter(Boolean)
    .join('\n')
}

const vocabularySnapshot = computed(() => {
  const reply = vocabularyReply.value
  if (!reply) return ''
  return joinSnapshot([
    `${reply.chineseWord ?? ''} ${reply.pinyin ?? ''}`,
    reply.englishMeaning,
    reply.bahasaMelayuMeaning,
    reply.exampleSentence,
  ])
})
const sentenceSnapshot = computed(() => {
  const reply = sentenceReply.value
  if (!reply) return ''
  return joinSnapshot([reply.correctedSentence || reply.originalSentence, reply.pinyin, reply.improvementExplanation])
})
const quizSnapshot = computed(() => {
  const reply = quizReply.value
  if (!reply) return ''
  return joinSnapshot([
    reply.title,
    ...reply.questions.map((question) =>
      joinSnapshot([question.questionText, question.options.join(' / '), question.correctAnswer]),
    ),
  ])
})
const storySnapshot = computed(() => {
  const reply = storyReply.value
  if (!reply) return ''
  return joinSnapshot([
    reply.title,
    reply.chineseStory,
    reply.pinyin,
    reply.englishTranslation,
    reply.bahasaMelayuTranslation,
    ...(reply.vocabularyList ?? []).map((item) =>
      joinSnapshot([`${item.chineseWord ?? ''} ${item.pinyin ?? ''} ${item.englishMeaning ?? ''}`]),
    ),
    ...(reply.comprehensionQuestions ?? []).map((question) =>
      joinSnapshot([question.question, (question.options ?? []).join(' / '), question.answer]),
    ),
  ])
})
const pronunciationSnapshot = computed(() => {
  const reply = pronunciationReply.value
  if (!reply) return ''
  return joinSnapshot([reply.feedback, reply.pronunciationComment, reply.toneComment, reply.improvementTips])
})
const writingSnapshot = computed(() => {
  const reply = writingReply.value
  if (!reply) return ''
  return joinSnapshot([reply.feedback, reply.clarityComment, writingSupportText.value])
})

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

/**
 * Upload feedback: SUCCESS shows the AI-assisted feedback; UNAVAILABLE still means the file was
 * stored for teacher review (not an error); FAILED is reported as an error.
 */
function applyUploadStatus(
  response: AiResponseStatusCarrier & { submissionId?: number | null },
  readyMessage: string,
  savedMessage: string,
  errorMessage: string,
): void {
  const status = response.status?.toUpperCase()
  if (status === 'SUCCESS' || status === 'UNAVAILABLE') {
    actionError.value = null
    const message = status === 'SUCCESS' ? readyMessage : savedMessage
    // D7a: every upload is stored as a practice submission; name it when the server returns its id.
    actionSuccess.value = response.submissionId
      ? `${message} ${t('studentAi.submissionSaved', { id: response.submissionId })}`
      : message
    return
  }
  actionSuccess.value = null
  actionError.value = t('studentAi.responseStatus', { message: errorMessage, status: formatStatus(status || 'FAILED') })
}

/** Same rules as the practice upload forms and the server: allowed type and extension, not empty, at most 10 MB. */
function isUploadableFile(file: File, kind: 'audio' | 'image'): boolean {
  return isAcceptedUploadType(file, kind) && file.size > 0 && file.size <= MAX_PRACTICE_UPLOAD_BYTES
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

  if (!isUploadableFile(file, 'audio')) {
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

  if (!isUploadableFile(file, 'image')) {
    writingForm.file = null
    clearFileInput(event)
    actionError.value = t('studentAi.uploadImageInvalid')
    return
  }

  writingForm.file = file
}

let chatStreamController: AbortController | null = null

async function runChat(): Promise<void> {
  if (submitting.chat) return
  actionError.value = null
  actionSuccess.value = null
  streamingChatText.value = ''
  const message = chatForm.message.trim()
  if (!message) {
    actionError.value = t('studentAi.askRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    chatReply.value = aiChatReply
    showPreviewResponse(t('studentAi.chatSample'))
    return
  }
  submitting.chat = true
  const previousReply = chatReply.value
  try {
    const sessionId = previousReply?.sessionId ?? null
    chatReply.value = {
      sessionId,
      userMessage: message,
      aiResponse: '',
      language: languagePreference.value,
      createdAt: new Date().toISOString(),
      status: 'STREAMING',
    }
    chatStreamController = new AbortController()
    chatReply.value = await aiApi.chatStream(
      { message, languagePreference: languagePreference.value, sessionId: sessionId ?? undefined },
      {
        onDelta: (chunk) => {
          streamingChatText.value += chunk
        },
        signal: chatStreamController.signal,
      },
    )
    streamingChatText.value = ''
    applyAiResponseStatus(chatReply.value, t('studentAi.chatReady'), t('studentAi.chatFallback'))
  } catch (reason) {
    // The page was left while SparkBot was still answering: nothing to show.
    if (reason instanceof DOMException && reason.name === 'AbortError') return
    actionError.value = resolveApiError(reason, t('studentAi.chatError'))
    // Keep the last good exchange so the next question continues the same session (and its history).
    chatReply.value = previousReply
    streamingChatText.value = ''
  } finally {
    submitting.chat = false
  }
}

// Stops a running SparkBot stream when the learner leaves the page.
onBeforeUnmount(() => chatStreamController?.abort())

/** "Try it" on the chat card: uses the sample prompt when the chat bar is empty, then asks. */
function tryChatSample(): void {
  if (!chatForm.message.trim()) {
    chatForm.message = t('studentAi.chatSamplePrompt')
  }
  void runChat()
}

async function runVocabulary(): Promise<void> {
  if (submitting.vocab) return
  actionError.value = null
  actionSuccess.value = null
  const word = wordForm.word.trim()
  if (!word) {
    actionError.value = t('studentAi.wordRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    vocabularyReply.value = aiVocabularyReply
    lastPrompts.vocabulary = aiVocabularyReply.chineseWord
    showPreviewResponse(t('studentAi.vocabSample'))
    return
  }
  submitting.vocab = true
  try {
    const reply = await aiApi.vocabularyHelper({
      word,
      inputLanguage: 'zh-CN',
      outputLanguage: languagePreference.value,
      languagePreference: languagePreference.value,
    })
    lastPrompts.vocabulary = word
    // An UNAVAILABLE / FAILED reply carries a fixed server message, not AI output: show only the notice.
    vocabularyReply.value = aiResponseSucceeded(reply) ? reply : null
    applyAiResponseStatus(reply, t('studentAi.vocabReady'), t('studentAi.vocabFallback'))
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
  const sentence = sentenceForm.sentence.trim()
  if (!sentence) {
    actionError.value = t('studentAi.sentenceRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    sentenceReply.value = aiSentenceReply
    lastPrompts.sentence = aiSentenceReply.originalSentence
    showPreviewResponse(t('studentAi.sentenceSample'))
    return
  }
  submitting.sentence = true
  try {
    const reply = await aiApi.sentencePractice({
      sentence,
      learnerLevel: learnerLevelLabel.value,
      languagePreference: languagePreference.value,
    })
    lastPrompts.sentence = sentence
    // An UNAVAILABLE / FAILED reply carries a fixed server message, not AI output: show only the notice.
    sentenceReply.value = aiResponseSucceeded(reply) ? reply : null
    applyAiResponseStatus(reply, t('studentAi.sentenceReady'), t('studentAi.sentenceFallback'))
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
  const topic = storyForm.topic.trim()
  if (!topic) {
    actionError.value = t('studentAi.storyRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    storyReply.value = aiStoryReply
    lastPrompts.story = topic
    showPreviewResponse(t('studentAi.storySample'))
    return
  }
  submitting.story = true
  try {
    const reply = await aiApi.storyGenerator({
      topic,
      level: learnerLevelLabel.value,
      languagePreference: languagePreference.value,
    })
    lastPrompts.story = topic
    // An UNAVAILABLE / FAILED reply carries a fixed server message, not AI output: show only the notice.
    storyReply.value = aiResponseSucceeded(reply) ? reply : null
    applyAiResponseStatus(reply, t('studentAi.storyReady'), t('studentAi.storyFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.storyError'))
    storyReply.value = null
  } finally {
    submitting.story = false
  }
}

async function runQuiz(): Promise<void> {
  if (submitting.quiz) return
  actionError.value = null
  actionSuccess.value = null
  const topic = quizForm.topic.trim()
  if (!topic) {
    actionError.value = t('studentAi.quizRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    quizReply.value = aiQuizReply
    lastPrompts.quiz = topic
    showPreviewResponse(t('studentAi.quizSample'))
    return
  }
  submitting.quiz = true
  try {
    const reply = await aiApi.quizGenerator({
      topic,
      level: learnerLevelLabel.value,
      numberOfQuestions: 3,
      category: 'Conversation',
      languagePreference: languagePreference.value,
    })
    lastPrompts.quiz = topic
    // An UNAVAILABLE / FAILED reply carries a fixed server message, not AI output: show only the notice.
    quizReply.value = aiResponseSucceeded(reply) ? reply : null
    applyAiResponseStatus(reply, t('studentAi.quizSuccess'), t('studentAi.quizFallback'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentAi.quizError'))
    quizReply.value = null
  } finally {
    submitting.quiz = false
  }
}

async function onReported(): Promise<void> {
  if (previewFallbackEnabled.value) return
  await reportsResource.reload()
}

async function runPronunciation(): Promise<void> {
  if (submitting.pronunciation) return
  actionError.value = null
  actionSuccess.value = null
  const targetText = pronunciationForm.targetText.trim()
  if (!targetText) {
    actionError.value = t('studentAi.pronunciationTargetRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    pronunciationReply.value = aiPronunciationFeedbackReply
    lastPrompts.pronunciation = targetText
    showPreviewResponse(t('studentAi.pronunciationPreview'))
    return
  }
  if (!pronunciationForm.file) {
    actionError.value = t('studentAi.pronunciationFileRequired')
    return
  }
  submitting.pronunciation = true
  try {
    const formData = buildPronunciationFormData({
      file: pronunciationForm.file,
      targetText,
      pinyin: pronunciationForm.pinyin,
      languagePreference: languagePreference.value,
    })
    pronunciationReply.value = await aiApi.pronunciationFeedback(formData)
    lastPrompts.pronunciation = targetText
    applyUploadStatus(
      pronunciationReply.value,
      t('studentAi.pronunciationReady'),
      t('studentAi.pronunciationSaved'),
      t('studentAi.pronunciationError'),
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
  const targetCharacter = writingForm.targetCharacter.trim()
  if (!targetCharacter) {
    actionError.value = t('studentAi.characterRequired')
    return
  }
  if (previewFallbackEnabled.value) {
    writingReply.value = aiWritingFeedbackReply
    lastPrompts.writing = targetCharacter
    showPreviewResponse(t('studentAi.writingPreview'))
    return
  }
  if (!writingForm.file) {
    actionError.value = t('studentAi.writingFileRequired')
    return
  }
  submitting.writing = true
  try {
    const formData = buildWritingFormData({
      file: writingForm.file,
      targetCharacter,
      languagePreference: languagePreference.value,
    })
    writingReply.value = await aiApi.writingFeedback(formData)
    lastPrompts.writing = targetCharacter
    applyUploadStatus(
      writingReply.value,
      t('studentAi.writingReady'),
      t('studentAi.writingSaved'),
      t('studentAi.writingError'),
    )
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

function formatReportReason(value: string): string {
  const key = AI_REPORT_REASON_KEYS[value.toUpperCase() as AiResponseReportReason]
  if (key) return t(`aiSafety.${key}`)
  return humanizeEnumValue(value)
}

function formatStatus(value: string): string {
  const keys: Record<string, string> = {
    PENDING_REVIEW: 'statusPendingReview',
    IN_REVIEW: 'statusInReview',
    RESOLVED: 'statusResolved',
    DISMISSED: 'statusDismissed',
    SUCCESS: 'statusSuccess',
    FAILED: 'statusFailed',
    AVAILABLE: 'statusAvailable',
    UNAVAILABLE: 'statusUnavailable',
  }
  const key = keys[value.toUpperCase()]
  if (key) return t(`studentAi.${key}`)
  return humanizeEnumValue(value)
}
</script>

<style scoped>
.student-ai-helper-page {
  display: grid;
  gap: var(--page-gap);
}

.student-ai-helper-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 320px);
  gap: var(--page-gap);
  align-items: start;
}

.student-ai-helper-main,
.student-ai-helper-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  align-items: start;
  min-width: 0;
}

.student-ai-helper-section {
  display: grid;
  gap: var(--section-gap);
  min-width: 0;
}

/* SparkBot chat ---------------------------------------------------------- */
.student-ai-helper-chat {
  display: grid;
  gap: var(--space-4);
  border-color: var(--color-primary-100);
  background: linear-gradient(180deg, var(--color-primary-50), var(--surface-card) 160px);
}

.student-ai-helper-chat-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-4);
}

.student-ai-helper-avatar {
  display: grid;
  place-items: center;
  width: var(--space-16);
  height: var(--space-16);
  overflow: hidden;
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-round);
  background: var(--surface-card);
  box-shadow: var(--shadow-xs);
}

.student-ai-helper-chat-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.student-ai-helper-chat-copy .card-title,
.student-ai-helper-chat-copy .card-subtitle {
  margin: 0;
}

.student-ai-helper-thread {
  display: grid;
  gap: var(--space-3);
  min-height: var(--space-20);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.student-ai-helper-thread-empty {
  align-self: center;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  text-align: center;
}

.student-ai-helper-message {
  display: grid;
  gap: var(--space-1-5);
  max-width: min(100%, 640px);
  min-width: 0;
}

.student-ai-helper-message.is-user {
  justify-self: end;
  justify-items: end;
}

.student-ai-helper-message.is-bot {
  justify-self: start;
}

.student-ai-helper-message-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.student-ai-helper-message-author {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.student-ai-helper-bubble {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  overflow-wrap: anywhere;
}

.student-ai-helper-bubble p {
  margin: 0;
}

.is-user .student-ai-helper-bubble {
  border-bottom-right-radius: var(--radius-xs);
  background: var(--color-primary-500);
  color: var(--text-on-primary);
}

.is-bot .student-ai-helper-bubble {
  border: 1px solid var(--border-subtle);
  border-bottom-left-radius: var(--radius-xs);
  background: var(--surface-subtle);
  color: var(--text-primary);
}

.student-ai-helper-typing {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
}

.student-ai-helper-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.student-ai-helper-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  max-width: 100%;
  min-height: var(--control-height-sm);
  padding: var(--space-1-5) var(--space-3);
  border: 1px solid var(--color-primary-200);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  color: var(--color-primary-700);
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-snug);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.student-ai-helper-chip:hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.student-ai-helper-chip:active {
  background: var(--surface-selected);
}

.student-ai-helper-chip .icon-glyph {
  flex: 0 0 auto;
  color: var(--text-brand);
}

.student-ai-helper-chatbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
}

.student-ai-helper-chatbar .input {
  width: 100%;
  max-width: none;
}

/* Tool cards ------------------------------------------------------------- */
.student-ai-helper-tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: var(--grid-gap);
}

.student-ai-helper-tool-card {
  --tool-tint: var(--color-primary-50);
  --tool-ink: var(--color-primary-700);

  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--card-padding);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
}

.student-ai-helper-tool-card.is-green {
  --tool-tint: var(--color-success-soft);
  --tool-ink: var(--color-success-text);
}

.student-ai-helper-tool-card.is-orange {
  --tool-tint: var(--color-accent-100);
  --tool-ink: var(--color-accent-800);
}

.student-ai-helper-tool-card.is-blue {
  --tool-tint: var(--color-info-soft);
  --tool-ink: var(--color-info-text);
}

.student-ai-helper-tool-card.is-red {
  --tool-tint: var(--color-danger-soft);
  --tool-ink: var(--color-danger-text);
}

.student-ai-helper-tool-card.is-teal {
  --tool-tint: var(--color-ink-100);
  --tool-ink: var(--color-ink-700);
}

.student-ai-helper-tool-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.student-ai-helper-tool-head h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.student-ai-helper-tool-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--tool-tint);
  color: var(--tool-ink);
}

.student-ai-helper-tool-card > p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--type-body-sm-size);
  line-height: var(--line-height-normal);
}

.student-ai-helper-tool-card > .student-ai-helper-tool-sample {
  padding: var(--space-2-5) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
  color: var(--text-primary);
  font-style: italic;
}

.student-ai-helper-tool-card .input {
  width: 100%;
  max-width: none;
}

/* The action sits at the bottom of every card so the grid rows line up. */
.student-ai-helper-tool-card > .btn {
  align-self: flex-start;
  margin-top: auto;
}

.student-ai-helper-field-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 120px), 1fr));
  gap: var(--space-3);
}

.student-ai-helper-inline-link {
  color: var(--text-link);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-normal);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.student-ai-helper-inline-link:hover {
  color: var(--text-link-hover);
}

/* Results ---------------------------------------------------------------- */
.student-ai-helper-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  align-items: start;
  gap: var(--grid-gap);
}

.student-ai-helper-result {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--card-padding);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
}

/* The full practice quiz needs the whole result row. */
.student-ai-helper-result.is-wide {
  grid-column: 1 / -1;
}

.student-ai-helper-result-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.student-ai-helper-result-kicker {
  color: var(--text-brand);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.student-ai-helper-result-title {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-ai-helper-result p {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.student-ai-helper-result small {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

/* Story generator -------------------------------------------------------- */
.student-ai-helper-story {
  display: grid;
  gap: var(--space-4);
}

.student-ai-helper-story .card-header {
  margin: 0;
}

.student-ai-helper-story-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--space-3);
}

.student-ai-helper-story-form .input {
  width: 100%;
  max-width: none;
}

.student-ai-helper-story-result {
  display: grid;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

/* Side column ------------------------------------------------------------ */
.student-ai-helper-side-card {
  display: grid;
  gap: var(--space-3);
}

.student-ai-helper-side-card p {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.student-ai-helper-side-card > .student-ai-helper-side-muted,
.student-ai-helper-side-muted {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-ai-helper-side-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
}

.student-ai-helper-side-title .icon-glyph {
  color: var(--text-brand);
}

.student-ai-helper-support-list,
.student-ai-helper-report-list {
  display: grid;
  gap: var(--space-2-5);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-ai-helper-support-list li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-ai-helper-support-list .icon-glyph {
  margin-top: var(--space-0-5);
  color: var(--color-success-text);
}

.student-ai-helper-care-note {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-accent-50);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-ai-helper-report-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-bottom: var(--space-2-5);
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.student-ai-helper-report-list li:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.student-ai-helper-tip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-accent-200);
  border-radius: var(--radius-lg);
  background: var(--color-accent-50);
}

.student-ai-helper-tip-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.student-ai-helper-tip strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.student-ai-helper-tip p {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

@media (max-width: 1200px) {
  .student-ai-helper-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-ai-helper-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  }
}

@media (max-width: 640px) {
  .student-ai-helper-chat-head {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-ai-helper-avatar {
    display: none;
  }

  .student-ai-helper-chip {
    min-height: var(--tap-target-min);
  }

  .student-ai-helper-story-form {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-ai-helper-tool-card > .btn {
    align-self: stretch;
  }
}
</style>
