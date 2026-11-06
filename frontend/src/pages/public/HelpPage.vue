<template>
  <div class="public-page public-doc">
    <PageHeaderBlock :title="t('help.title')">
      <template #before>
        <AppBreadcrumb :items="[{ label: t('help.home'), to: '/' }, { label: t('help.title') }]" />
      </template>
    </PageHeaderBlock>

    <div class="public-rail-layout">
      <nav class="panel public-rail" :aria-label="t('help.topics')">
        <p class="page-eyebrow" aria-hidden="true">{{ t('help.topics') }}</p>
        <ul class="public-rail-list">
          <li v-for="topic in navTopics" :key="topic.id">
            <a class="public-rail-link" :href="`#${topic.id}`">
              <IconGlyph :name="topic.icon" :size="16" />
              <span>{{ t(topic.title) }}</span>
            </a>
          </li>
        </ul>
      </nav>

      <div class="help-main">
        <section
          v-for="topic in TOPICS"
          :id="topic.id"
          :key="topic.id"
          class="panel help-card"
          :aria-labelledby="`${topic.id}-title`"
        >
          <h2 :id="`${topic.id}-title`" class="card-title">{{ t(topic.title) }}</h2>
          <ul class="row-list help-questions">
            <li v-for="item in topic.items" :key="item.question">
              <div class="row-list-copy">
                <h3 class="row-list-title">{{ t(item.question) }}</h3>
                <p class="help-answer">
                  {{ t(item.answer) }}
                  <RouterLink v-if="item.question === AI_DATA_QUESTION" to="/responsible-ai">
                    {{ t('help.aiDataLink') }}
                  </RouterLink>
                </p>
              </div>
            </li>
          </ul>
        </section>

        <section id="contact" class="panel help-card" aria-labelledby="help-contact-title">
          <h2 id="help-contact-title" class="card-title">{{ t('help.contactTitle') }}</h2>

          <div v-if="auth.isAuthenticated" class="help-form" data-testid="help-signed-in">
            <p class="help-lead">{{ t('help.signedInLead') }}</p>
            <div class="form-actions">
              <RouterLink class="btn btn-primary" :to="supportPath">
                {{ t('help.openSupport') }}
                <IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </div>
          </div>

          <template v-else-if="sent">
            <div class="help-sent" role="status" data-testid="help-sent">
              <span class="seal help-sent-seal" aria-hidden="true"><IconGlyph name="check" :size="20" /></span>
              <div class="row-list-copy">
                <h3 class="card-title">{{ t('help.sentTitle') }}</h3>
                <p>
                  {{ t('help.sentCopy', { email: sent.email }) }}
                  {{ t('help.sentNumber', { number: sent.number }) }}
                </p>
              </div>
            </div>
            <div class="form-actions">
              <button class="btn btn-secondary" type="button" @click="sendAnother">{{ t('help.sendAnother') }}</button>
            </div>
          </template>

          <form v-else class="help-form" novalidate @submit.prevent="submit">
            <p class="help-lead">{{ t('help.contactLead') }}</p>
            <div v-if="sendError" id="help-send-error" class="alert alert-danger" role="alert">
              <IconGlyph name="alert" :size="18" />
              <span>{{ sendError }}</span>
            </div>
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
                <p v-if="errors.email" id="help-email-error" class="field-error">
                  <IconGlyph name="alert" :size="14" />{{ errors.email }}
                </p>
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
                rows="4"
                required
                :maxlength="MESSAGE_MAX_LENGTH"
                :placeholder="t('help.messagePlaceholder')"
                :aria-invalid="Boolean(errors.message)"
                :aria-describedby="errors.message ? 'help-message-error help-message-hint' : 'help-message-hint'"
              />
              <p v-if="errors.message" id="help-message-error" class="field-error">
                <IconGlyph name="alert" :size="14" />{{ errors.message }}
              </p>
            </div>
            <div class="help-form-footer">
              <p id="help-message-hint" class="field-hint">{{ t('help.noPasswords') }}</p>
              <button class="btn btn-primary" type="submit" :disabled="sending" :aria-busy="sending">
                {{ sending ? t('help.sending') : t('help.send') }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
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

/** Its answer ends with a link to the full guidance. */
const AI_DATA_QUESTION = 'help.aiDataQuestion'

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
      { question: AI_DATA_QUESTION, answer: 'help.aiDataAnswer' },
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
  if (sending.value) return
  if (!validate()) {
    void nextTick(() => document.getElementById(errors.email ? 'help-email' : 'help-message')?.focus())
    return
  }
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
