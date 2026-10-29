<template>
  <div class="help-page">
    <PageHeaderBlock :title="t('help.title')" :description="t('help.lead')">
      <template #before>
        <AppBreadcrumb :items="[{ label: t('help.home'), to: '/' }, { label: t('help.title') }]" />
      </template>
    </PageHeaderBlock>

    <div class="help-layout">
      <nav class="help-topics" :aria-label="t('help.topics')">
        <p class="help-topics-title" aria-hidden="true">{{ t('help.topics') }}</p>
        <ol>
          <li v-for="topic in navTopics" :key="topic.id">
            <a :href="`#${topic.id}`">
              <IconGlyph :name="topic.icon" :size="16" />
              <span>{{ t(topic.title) }}</span>
            </a>
          </li>
        </ol>
      </nav>

      <div class="help-main">
        <section
          v-for="(topic, index) in TOPICS"
          :id="topic.id"
          :key="topic.id"
          class="help-topic"
          :aria-labelledby="`${topic.id}-title`"
        >
          <h2 :id="`${topic.id}-title`" class="help-topic-title">
            <span class="help-topic-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
            {{ t(topic.title) }}
          </h2>
          <div class="help-questions">
            <details v-for="item in topic.items" :key="item.question" class="public-qa help-question">
              <summary>
                <span>{{ t(item.question) }}</span>
                <IconGlyph class="public-qa-chevron" name="chevronDown" :size="18" />
              </summary>
              <p>{{ t(item.answer) }}</p>
            </details>
          </div>
          <p v-if="topic.id === 'ai-and-safety'" class="help-topic-links">
            <RouterLink class="btn btn-link" to="/responsible-ai">
              {{ t('help.aiDataLink') }}<IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
            <RouterLink class="btn btn-link" to="/safety">
              {{ t('help.safetyLink') }}<IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
          </p>
        </section>
      </div>

      <section id="contact" class="panel help-contact" aria-labelledby="help-contact-title">
        <div class="help-contact-head">
          <span class="help-contact-mark" aria-hidden="true"><IconGlyph name="mail" :size="20" /></span>
          <h2 id="help-contact-title" class="help-contact-title">{{ t('help.contactTitle') }}</h2>
          <p v-if="!auth.isAuthenticated && !sent">{{ t('help.contactLead') }}</p>
        </div>

        <div v-if="auth.isAuthenticated" class="help-signed-in" data-testid="help-signed-in">
          <p>{{ t('help.signedInLead') }}</p>
          <RouterLink class="btn btn-primary" :to="supportPath">
            {{ t('help.openSupport') }}
            <IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </div>

        <div v-else-if="sent" class="help-sent" role="status" data-testid="help-sent">
          <span class="help-sent-mark" aria-hidden="true"><IconGlyph name="check" :size="24" /></span>
          <div>
            <h3>{{ t('help.sentTitle') }}</h3>
            <p>{{ t('help.sentCopy', { email: sent.email }) }}</p>
            <p class="help-sent-number">{{ t('help.sentNumber', { number: sent.number }) }}</p>
          </div>
          <button class="btn btn-secondary" type="button" @click="sendAnother">{{ t('help.sendAnother') }}</button>
        </div>

        <form v-else class="help-form" novalidate @submit.prevent="submit">
          <p v-if="sendError" id="help-send-error" class="alert alert-danger" role="alert">
            <IconGlyph name="alert" :size="18" />
            <span>{{ sendError }}</span>
          </p>
          <div class="form-grid two">
            <div class="field">
              <label class="field-label" for="help-email">
                {{ t('help.email') }}<span class="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="help-email"
                v-model="form.email"
                class="input"
                type="email"
                autocomplete="email"
                required
                :placeholder="t('help.emailPlaceholder')"
                :aria-invalid="Boolean(errors.email)"
                :aria-describedby="errors.email ? 'help-email-error' : undefined"
              />
              <p v-if="errors.email" id="help-email-error" class="field-error">{{ errors.email }}</p>
            </div>
            <div class="field">
              <label class="field-label" for="help-topic">{{ t('help.topic') }}</label>
              <select id="help-topic" v-model="form.topic" class="select">
                <option v-for="topic in SUPPORT_TOPICS" :key="topic" :value="topic">
                  {{ t(supportTopicKey(topic)) }}
                </option>
              </select>
            </div>
          </div>
          <div class="field">
            <label class="field-label" for="help-message">
              {{ t('help.message') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <textarea
              id="help-message"
              v-model="form.message"
              class="textarea"
              rows="5"
              required
              :maxlength="MESSAGE_MAX_LENGTH"
              :placeholder="t('help.messagePlaceholder')"
              :aria-invalid="Boolean(errors.message)"
              :aria-describedby="errors.message ? 'help-message-error help-message-hint' : 'help-message-hint'"
            />
            <div class="field-foot">
              <p id="help-message-hint" class="field-hint">{{ t('help.noPasswords') }}</p>
              <p class="field-hint field-counter">{{ t('help.messageCount', { count: form.message.length }) }}</p>
            </div>
            <p v-if="errors.message" id="help-message-error" class="field-error">{{ errors.message }}</p>
          </div>
          <div class="help-form-footer">
            <p class="field-hint">{{ t('help.signedInNote') }}</p>
            <button class="btn btn-primary" type="submit" :disabled="sending" :aria-busy="sending">
              {{ sending ? t('help.sending') : t('help.send') }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { publicOpsApi } from '@/api/ops'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { helpMessages } from '@/plugins/helpMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { useAuthStore } from '@/stores/auth'
import type { SupportTopic } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { SUPPORT_TOPICS, supportTopicKey } from '@/utils/opsLabels'

const MESSAGE_MAX_LENGTH = 4000

const TOPICS = [
  {
    id: 'getting-started',
    icon: 'learn',
    title: 'help.gettingStarted',
    items: [
      { question: 'help.whatIsQuestion', answer: 'help.whatIsAnswer' },
      { question: 'help.freeQuestion', answer: 'help.freeAnswer' },
      { question: 'help.accountTypeQuestion', answer: 'help.accountTypeAnswer' },
    ],
  },
  {
    id: 'for-students',
    icon: 'students',
    title: 'help.forStudents',
    items: [
      { question: 'help.differenceQuestion', answer: 'help.differenceAnswer' },
      { question: 'help.xpQuestion', answer: 'help.xpAnswer' },
      { question: 'help.feedbackQuestion', answer: 'help.feedbackAnswer' },
    ],
  },
  {
    id: 'for-teachers',
    icon: 'teacher',
    title: 'help.forTeachers',
    items: [
      { question: 'help.verifiedQuestion', answer: 'help.verifiedAnswer' },
      { question: 'help.lessonVisibleQuestion', answer: 'help.lessonVisibleAnswer' },
      { question: 'help.liveSessionsQuestion', answer: 'help.liveSessionsAnswer' },
    ],
  },
  {
    id: 'your-account',
    icon: 'account',
    title: 'help.yourAccount',
    items: [
      { question: 'help.forgotQuestion', answer: 'help.forgotAnswer' },
      { question: 'help.changeEmailQuestion', answer: 'help.changeEmailAnswer' },
      { question: 'help.deleteQuestion', answer: 'help.deleteAnswer' },
    ],
  },
  {
    id: 'ai-and-safety',
    icon: 'ai',
    title: 'help.aiAndSafety',
    items: [
      { question: 'help.sparkBotQuestion', answer: 'help.sparkBotAnswer' },
      { question: 'help.aiWrongQuestion', answer: 'help.aiWrongAnswer' },
      { question: 'help.aiDataQuestion', answer: 'help.aiDataAnswer' },
    ],
  },
] as const

const auth = useAuthStore()
const { t } = useLocalMessages({ help: helpMessages, opsLabels: opsLabelMessages })

const navTopics = computed(() => [
  ...TOPICS.map(({ id, icon, title }) => ({ id, icon, title })),
  { id: 'contact', icon: 'mail', title: 'help.contactUs' },
])
const supportPath = computed(() => `/${(auth.activeRole ?? 'STUDENT').toLowerCase()}/support`)

const form = reactive({ email: '', topic: 'ACCOUNT' as SupportTopic, message: '' })
const errors = reactive<{ email: string | null; message: string | null }>({ email: null, message: null })
const sending = ref(false)
const sendError = ref<string | null>(null)
const sent = ref<{ email: string; number: number } | null>(null)

function validate(): boolean {
  errors.email = isValidEmail(form.email.trim()) ? null : t('help.emailInvalid')
  const message = form.message.trim()
  errors.message = !message
    ? t('help.messageRequired')
    : message.length > MESSAGE_MAX_LENGTH
      ? t('help.messageTooLong')
      : null
  return !errors.email && !errors.message
}

async function submit(): Promise<void> {
  sendError.value = null
  if (sending.value || !validate()) return
  sending.value = true
  try {
    sent.value = await publicOpsApi.sendHelpMessage({
      email: form.email.trim().toLowerCase(),
      topic: form.topic,
      message: form.message.trim(),
    })
  } catch (reason) {
    sendError.value = resolveApiError(reason, t('help.sendFailed'))
  } finally {
    sending.value = false
  }
}

function sendAnother(): void {
  sent.value = null
  form.message = ''
}
</script>
