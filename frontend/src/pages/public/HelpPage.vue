<template>
  <div class="public-page public-doc">
    <PageHeaderBlock :title="t('nav.help')" />
    <AppToast :message="toast" />

    <div class="public-rail-layout">
      <aside ref="rail" class="panel public-rail">
        <form role="search" @submit.prevent>
          <label class="sr-only" for="help-search">{{ t('help.searchLabel') }}</label>
          <span class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="help-search"
              ref="searchInput"
              v-model="search"
              class="input"
              type="search"
              autocomplete="off"
              :maxlength="SEARCH_MAX_LENGTH"
              :placeholder="t('help.searchLabel')"
            />
          </span>
          <!-- Always on the page, so every new count is announced while the visitor types. -->
          <p class="sr-only" role="status" data-testid="help-search-status">{{ searchSummary }}</p>
        </form>

        <!-- While searching, the results take the place of the topics. -->
        <nav v-if="!query" class="help-topics" :aria-label="t('help.topics')">
          <p class="page-eyebrow" aria-hidden="true">{{ t('help.topics') }}</p>
          <ul class="public-rail-list">
            <li v-for="topic in railTopics" :key="topic.id">
              <a
                class="public-rail-link"
                :class="{ 'is-active': topic.id === activeId }"
                :href="`#${topic.id}`"
                :aria-current="topic.id === activeId ? 'location' : undefined"
                @click.exact="pinSection(topic.id)"
              >
                <IconGlyph :name="topic.icon" :size="16" />
                <span>{{ t(topic.title) }}</span>
              </a>
            </li>
          </ul>
        </nav>
      </aside>

      <div class="help-main">
        <div v-if="query" class="help-search-summary" data-testid="help-search-results">
          <p>{{ searchSummary }}</p>
          <button class="btn btn-secondary btn-sm" type="button" @click="clearSearch">
            {{ t('common.clearSearch') }}
          </button>
        </div>

        <section
          v-for="topic in visibleTopics"
          :id="topic.id"
          :key="topic.id"
          class="panel help-card"
          :aria-labelledby="`${topic.id}-title`"
        >
          <h2 :id="`${topic.id}-title`" class="card-title">{{ topic.title }}</h2>
          <ul class="row-list is-flush help-questions">
            <li v-for="item in topic.items" :key="item.id">
              <div class="row-list-copy">
                <h3 class="row-list-title">{{ item.question }}</h3>
                <p class="help-answer">
                  {{ item.answer }}
                  <RouterLink v-if="item.guidanceLink !== undefined" to="/responsible-ai">
                    {{ item.guidanceLink }}
                  </RouterLink>
                </p>
              </div>
            </li>
          </ul>
        </section>

        <section
          id="contact"
          ref="contactSection"
          class="panel help-card"
          tabindex="-1"
          aria-labelledby="help-contact-title"
        >
          <h2 id="help-contact-title" class="card-title">{{ t('help.contactTitle') }}</h2>

          <!-- The toast announces the send; the card keeps the confirmation where the form was. -->
          <template v-if="sentEmail !== null">
            <div class="help-sent" data-testid="help-sent">
              <span class="seal help-sent-seal" aria-hidden="true"><IconGlyph name="check" :size="20" /></span>
              <div class="row-list-copy">
                <h3 class="card-title">{{ t('help.sentTitle') }}</h3>
                <p>{{ t('help.sentCopy', { email: sentEmail }) }}</p>
              </div>
            </div>
            <div class="form-actions">
              <button
                ref="sendAnotherButton"
                class="btn btn-secondary"
                type="button"
                data-testid="help-send-another"
                @click="sendAnother"
              >
                {{ t('help.sendAnother') }}
              </button>
            </div>
          </template>

          <form v-else class="help-form" novalidate @submit.prevent="submit">
            <div class="form-grid two">
              <div class="field">
                <label class="field-label" for="help-email">
                  {{ t('help.email') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="help-email"
                  ref="emailInput"
                  v-model="form.email"
                  class="input"
                  type="email"
                  autocomplete="email"
                  required
                  :readonly="sending"
                  :maxlength="EMAIL_MAX_LENGTH"
                  :placeholder="t('help.emailPlaceholder')"
                  :aria-invalid="Boolean(emailError)"
                  :aria-describedby="emailError ? 'help-email-error' : undefined"
                  @blur="validateEmail"
                />
                <p v-if="emailError" id="help-email-error" class="field-error">
                  <IconGlyph name="alert" :size="14" />{{ t(emailError) }}
                </p>
              </div>
              <div class="field">
                <label class="field-label" for="help-topic">
                  {{ t('help.topic') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <select id="help-topic" v-model="form.topic" class="select" required :disabled="sending">
                  <option v-for="topic in SUPPORT_TOPICS" :key="topic" :value="topic">
                    {{ t(SUPPORT_TOPIC_KEYS[topic]) }}
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
                ref="messageInput"
                v-model="form.message"
                class="textarea"
                rows="4"
                required
                :readonly="sending"
                :maxlength="MESSAGE_MAX_LENGTH"
                :placeholder="t('help.messagePlaceholder')"
                :aria-invalid="Boolean(messageError)"
                :aria-describedby="messageError ? 'help-message-error help-message-hint' : 'help-message-hint'"
                @blur="validateMessage"
              />
              <p v-if="messageError" id="help-message-error" class="field-error">
                <IconGlyph name="alert" :size="14" />{{ t(messageError) }}
              </p>
            </div>
            <!-- Beside the button that sends again, so the visitor sees it where they are; nothing typed is lost. -->
            <p v-if="sendError" id="help-send-error" class="callout is-danger" role="alert">
              <IconGlyph name="alert" :size="18" />
              <span>{{ sendError }}</span>
            </p>
            <div class="help-form-footer">
              <p id="help-message-hint" class="field-hint">{{ t('help.noPasswords') }}</p>
              <button
                ref="sendButton"
                class="btn btn-primary"
                type="submit"
                :disabled="sending || hasErrors"
                :aria-busy="sending"
                :aria-describedby="sendError ? 'help-send-error' : undefined"
                data-testid="help-send"
                @mousedown.prevent
              >
                {{ sendLabel }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef, toRef, useTemplateRef, watch, watchPostEffect } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { publicApi } from '@/api/services'
import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { usePublicSectionNav } from '@/components/public/usePublicSectionNav'
import PageHeaderBlock from '@/components/layout/PageHeaderBlock.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { configuredTavusDeploymentId } from '@/composables/usePublicPlatformSettings'
import { useToast } from '@/composables/useToast'
import { helpMessages } from '@/plugins/helpMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { appLocale } from '@/plugins/i18n'
import type { SelectableSupportTopic } from '@/types/ops'
import { isNetworkError, resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { restoreFocusIfDropped } from '@/utils/focus'
import { SUPPORT_TOPICS, SUPPORT_TOPIC_KEYS } from '@/utils/opsLabels'
import { queryOption } from '@/utils/routeQuery'
import { truncateText } from '@/utils/truncateText'

// The server's limits (VisitorSupportRequest): the fields stop typed and pasted text there.
const EMAIL_MAX_LENGTH = 150
const MESSAGE_MAX_LENGTH = 4000

const SEARCH_MAX_LENGTH = 80
/** A long search is shown shortened in the result line, so it never breaks its layout. */
const SHOWN_QUERY_LENGTH = 40

const TOPICS = [
  { id: 'getting-started', icon: 'learn', title: 'help.gettingStarted', items: ['whatIs', 'free', 'createAccount'] },
  { id: 'learning', icon: 'students', title: 'help.learning', items: ['difference', 'xp'] },
  { id: 'your-account', icon: 'account', title: 'help.yourAccount', items: ['forgot', 'changeEmail', 'delete'] },
  { id: 'ai-and-safety', icon: 'ai', title: 'help.aiAndSafety', items: ['sparkBot', 'aiWrong', 'aiData'] },
] as const

/** The topics, then the message form. */
const railTopics = [...TOPICS, { id: 'contact', icon: 'mail', title: 'help.contactTitle' }]
const route = useRoute()

const { t } = useLocalMessages({ help: helpMessages, opsLabels: opsLabelMessages })
const { toast, showToast } = useToast()

// Search: filters the questions on the page by the words shown in the current language.

const search = ref('')
const query = computed(() => search.value.trim())
const { activeId, pinSection } = usePublicSectionNav(
  () => (query.value ? [] : railTopics),
  useTemplateRef<HTMLElement>('rail'),
  () => route.hash,
)
const shownQuery = computed(() =>
  query.value.length > SHOWN_QUERY_LENGTH ? `${truncateText(query.value, SHOWN_QUERY_LENGTH)}…` : query.value,
)

const hasLiveVideo = Boolean(configuredTavusDeploymentId())

function itemAnswer(item: (typeof TOPICS)[number]['items'][number]): string {
  if (item !== 'aiData') return t(`help.${item}Answer`)
  const parts = [t('help.aiDataAnswer'), ...(hasLiveVideo ? [t('help.aiDataLiveVideo')] : []), t('help.aiDataCaution')]
  return parts.join(appLocale.value === 'zh-CN' ? '' : ' ')
}

const localizedTopics = computed(() =>
  TOPICS.map((topic) => ({
    id: topic.id,
    title: t(topic.title),
    items: topic.items.map((item) => ({
      id: item,
      question: t(`help.${item}Question`),
      answer: itemAnswer(item),
      guidanceLink: item === 'aiData' ? t('help.aiDataLink') : undefined,
    })),
  })),
)

/**
 * Whether a text has the search, whatever the case: from the start of a word ("xp" finds "XP", not "explain"), or
 * anywhere for Chinese, which has no spaces between words.
 */
function searchMatcher(needle: string): (text: string) => boolean {
  if (!/^(?!\p{Script=Han})[\p{L}\p{N}]/u.test(needle)) return (text) => text.toLowerCase().includes(needle)
  const wordStart = new RegExp(`(?<![\\p{L}\\p{N}])${needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`, 'iu')
  return (text) => wordStart.test(text)
}

/** Every topic without a search; with one, the topics that still have a matching question, with those questions only. */
const visibleTopics = computed(() => {
  if (!query.value) return localizedTopics.value
  const matches = searchMatcher(query.value.toLowerCase())
  return localizedTopics.value
    .map((topic) => ({
      ...topic,
      items: topic.items.filter(({ question, answer, guidanceLink }) =>
        matches(`${question} ${answer} ${guidanceLink ?? ''}`),
      ),
    }))
    .filter((topic) => topic.items.length > 0)
})
const matchCount = computed(() => visibleTopics.value.reduce((count, topic) => count + topic.items.length, 0))
/** "1 result for “xp”", or what to do when nothing matches; empty without a search. */
const searchSummary = computed(() => {
  if (!query.value) return ''
  return matchCount.value
    ? t('help.searchResults', { query: shownQuery.value }, matchCount.value)
    : t('help.searchEmpty', { query: shownQuery.value })
})

const searchInput = useTemplateRef<HTMLInputElement>('searchInput')
const emailInput = useTemplateRef<HTMLInputElement>('emailInput')
const messageInput = useTemplateRef<HTMLTextAreaElement>('messageInput')

function clearSearch(): void {
  search.value = ''
  searchInput.value?.focus()
}

// The message form, for everyone (support answers by e-mail). It starts empty, on the topic a link asks for.

const contactSection = useTemplateRef<HTMLElement>('contactSection')
watchPostEffect(() => {
  if (route.hash === '#contact') contactSection.value?.focus({ preventScroll: true })
})

/**
 * The topic a link opens the form on (`/help?topic=SAFETY#contact`: the Safety centre's "Report a concern"), when it is
 * one the form offers; anything else is ignored.
 */
const form = reactive({ email: '', topic: 'ACCOUNT' as SelectableSupportTopic, message: '' })
watch(
  () => route.query.topic,
  (value) => {
    const topic = queryOption(value, SUPPORT_TOPICS)
    if (topic) form.topic = topic
  },
  { immediate: true },
)

// The errors are message keys, so an error shown under a field follows a language switch.
const { error: emailError, validate: validateEmail } = useFieldCheck(toRef(form, 'email'), (value) => {
  const email = value.trim()
  if (!email) return 'help.emailRequired'
  return isValidEmail(email) ? null : 'help.emailInvalid'
})
const { error: messageError, validate: validateMessage } = useFieldCheck(toRef(form, 'message'), (value) =>
  value.trim() ? null : 'help.messageRequired',
)
const hasErrors = computed(() => Boolean(emailError.value || messageError.value))
const sending = ref(false)
/** Why the last send failed; the banner's text is built from it, so it follows a language switch. */
const sendFailure = shallowRef<{ reason: unknown } | null>(null)
const sentEmail = ref<string | null>(null)
const sendButton = useTemplateRef<HTMLButtonElement>('sendButton')
const sendAnotherButton = useTemplateRef<HTMLButtonElement>('sendAnotherButton')

const sendError = computed(() => {
  if (!sendFailure.value) return null
  const { reason } = sendFailure.value
  const cause = isNetworkError(reason) ? t('help.checkConnection') : resolveApiError(reason, t('help.sendFailedRetry'))
  return t('help.sendFailed', { reason: cause })
})
const sendLabel = computed(() => {
  if (sending.value) return t('common.sending')
  return sendFailure.value ? t('common.retry') : t('help.send')
})

/**
 * Sends the form; after a failure the same button says "Try again", with everything the visitor typed in place. A
 * refused field keeps what was typed in every field, and only the field at fault is marked.
 */
async function submit(): Promise<void> {
  if (sending.value) return
  const emailOk = validateEmail()
  const messageOk = validateMessage()
  if (!emailOk || !messageOk) {
    void nextTick(() => (emailOk ? messageInput.value : emailInput.value)?.focus())
    return
  }
  sending.value = true
  try {
    const answer = await publicApi.sendHelpMessage({
      email: form.email.trim().toLowerCase(),
      topic: form.topic,
      message: form.message.trim(),
      // The e-mails that bring support's replies use the language the visitor reads this page in.
      language: appLocale.value,
    })
    sendFailure.value = null
    sentEmail.value = answer.email
    // Said where the visitor is looking, whatever part of the page is in view (a search may have shortened it).
    showToast(t('help.sentToast'))
  } catch (reason) {
    sendFailure.value = { reason }
  } finally {
    sending.value = false
  }
  if (sentEmail.value !== null) {
    // The form made way for the confirmation in the same card; move focus to the next step only if focus was inside
    // the contact card or dropped to body (never steal focus if the visitor moved elsewhere while sending).
    await nextTick()
    const active = document.activeElement
    if (!active || active === document.body || !active.isConnected || contactSection.value?.contains(active)) {
      sendAnotherButton.value?.focus()
    }
    return
  }
  // The button is disabled while it sends, and the browser takes the focus from it: it gets the focus back (now
  // "Try again", in view where the visitor is). A visitor who sent the form from a field (Enter) stays there.
  await restoreFocusIfDropped(() => sendButton.value)
}

/** Back to the form, in the same place: the address and topic stay, the message starts empty. */
async function sendAnother(): Promise<void> {
  sentEmail.value = null
  form.message = ''
  await nextTick()
  messageInput.value?.focus()
}
</script>
