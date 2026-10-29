<template>
  <div class="landing-page">
    <div v-if="accountDeleted" class="landing-notice" role="status" data-testid="account-deleted-notice">
      <IconGlyph name="check" :size="18" />
      <p>
        <strong>{{ t('landing.accountDeletedTitle') }}</strong>
        <span>{{ t('landing.accountDeleted') }}</span>
      </p>
      <button
        class="btn btn-ghost btn-icon btn-sm"
        type="button"
        :aria-label="t('landing.dismiss')"
        @click="dismissNotice"
      >
        <IconGlyph name="close" :size="16" />
      </button>
    </div>

    <section class="landing-hero" aria-labelledby="landing-title">
      <div class="landing-hero-copy">
        <p class="page-eyebrow landing-audience">{{ t('landing.audience') }}</p>
        <h1 id="landing-title" class="landing-title">{{ t('landing.heroTitle') }}</h1>
        <p class="landing-lead">{{ t('landing.heroLead') }}</p>
        <div class="landing-actions">
          <RouterLink class="btn btn-accent btn-lg" :to="startRoute">
            {{ auth.isAuthenticated ? t('landing.openMyHome') : t('landing.startLearning') }}
            <IconGlyph name="chevronRight" :size="18" />
          </RouterLink>
          <RouterLink class="btn btn-ghost btn-lg" to="/classes">{{ t('landing.exploreClasses') }}</RouterLink>
        </div>
        <ul class="landing-facts" :aria-label="t('landing.highlights')">
          <li v-for="chip in CHIPS" :key="chip">{{ t(chip) }}</li>
        </ul>
      </div>

      <!-- A real lesson moment built from the product's own primitives: 你好 in 田字格 tiles, pinyin, tone, listen. -->
      <figure class="landing-moment">
        <div class="landing-moment-sheet">
          <div class="landing-moment-top">
            <span class="page-eyebrow">{{ t('landing.momentLesson') }}</span>
            <span class="landing-moment-steps" aria-hidden="true">
              <span v-for="step in 6" :key="step" :class="{ 'is-done': step <= 2 }" />
            </span>
          </div>
          <div class="landing-moment-word">
            <span v-for="syllable in GREETING" :key="syllable.hanzi" class="landing-moment-syllable">
              <span class="hanzi-tile landing-moment-tile" lang="zh-CN">{{ syllable.hanzi }}</span>
              <span class="landing-moment-pinyin" lang="zh-Latn-pinyin">{{ syllable.pinyin }}</span>
              <span class="landing-moment-tone">{{ t('landing.tone3') }}</span>
            </span>
          </div>
          <div class="landing-moment-meaning">
            <strong>{{ t('landing.momentMeaning') }}</strong>
            <button
              v-if="speechAvailable"
              class="btn btn-secondary btn-sm"
              type="button"
              :aria-label="t('landing.listenLabel')"
              :aria-pressed="speaking"
              @click="listen"
            >
              <IconGlyph name="audio" :size="16" />
              {{ t('landing.listen') }}
            </button>
          </div>
          <p class="landing-moment-xiaomei">
            <span class="landing-xiaomei-mark hanzi" aria-hidden="true">美</span>
            <span>
              <strong>{{ t('landing.xiaomeiName') }}</strong>
              {{ t('landing.xiaomeiLine') }}
            </span>
          </p>
        </div>
        <figcaption class="landing-moment-caption">{{ t('landing.momentCaption') }}</figcaption>
      </figure>
    </section>

    <section id="how-it-works" class="landing-ways" aria-labelledby="landing-ways-title">
      <header class="landing-section-head">
        <p class="page-eyebrow">{{ t('landing.howEyebrow') }}</p>
        <h2 id="landing-ways-title" class="landing-h2">{{ t('landing.howTitle') }}</h2>
        <p>{{ t('landing.howLead') }}</p>
      </header>

      <ol class="landing-way-list">
        <li class="landing-way is-learn">
          <div class="landing-way-copy">
            <span class="landing-way-number" aria-hidden="true">01</span>
            <h3>{{ t('landing.learnTitle') }}</h3>
            <p>{{ t('landing.learnCopy') }}</p>
            <p class="landing-xp is-earning">{{ t('landing.earnsXp') }}</p>
          </div>
          <div class="landing-render landing-path" role="img" :aria-label="t('landing.learnPathLabel')">
            <span v-for="stop in PATH" :key="stop.hanzi" class="landing-path-stop" :class="`is-${stop.state}`">
              <span class="seal landing-path-seal" :class="SEAL_CLASS[stop.state]" lang="zh-CN">
                {{ stop.hanzi[0] }}
              </span>
              <span class="landing-path-word" lang="zh-CN">{{ stop.hanzi }}</span>
              <span class="landing-path-pinyin" lang="zh-Latn-pinyin">{{ stop.pinyin }}</span>
              <span v-if="stop.state === 'current'" class="landing-path-next">
                {{ t('landing.upNext') }}
              </span>
              <IconGlyph v-else-if="stop.state === 'locked'" class="landing-path-lock" name="lock" :size="14" />
            </span>
          </div>
        </li>

        <li class="landing-way is-practice">
          <div class="landing-way-copy">
            <span class="landing-way-number" aria-hidden="true">02</span>
            <h3>{{ t('landing.practiceTitle') }}</h3>
            <p>{{ t('landing.practiceCopy') }}</p>
            <p class="landing-xp is-earning">{{ t('landing.earnsXp') }}</p>
          </div>
          <div class="landing-render landing-practice" role="img" :aria-label="t('landing.practiceRenderLabel')">
            <div class="landing-flashcard">
              <span class="landing-flashcard-tiles" lang="zh-CN">
                <span class="hanzi-tile landing-flashcard-tile">谢</span>
                <span class="hanzi-tile landing-flashcard-tile">谢</span>
              </span>
              <span class="landing-flashcard-copy">
                <strong lang="zh-Latn-pinyin">xièxie</strong>
                <span>{{ t('landing.practiceMeaning') }}</span>
              </span>
              <span class="landing-flashcard-count">{{ t('landing.practiceCard') }}</span>
            </div>
            <ul class="landing-practice-types">
              <li v-for="type in PRACTICE_TYPES" :key="type.label" :class="`is-${type.pigment}`">
                <IconGlyph :name="type.icon" :size="16" />
                {{ t(type.label) }}
              </li>
            </ul>
          </div>
        </li>

        <li class="landing-way is-ai">
          <div class="landing-way-copy">
            <span class="landing-way-number" aria-hidden="true">03</span>
            <h3>{{ t('landing.aiTitle') }}</h3>
            <p>{{ t('landing.aiCopy') }}</p>
            <p class="landing-xp">{{ t('landing.optionalNoXp') }}</p>
          </div>
          <div class="landing-render landing-chat" role="img" :aria-label="t('landing.aiRenderLabel')">
            <p class="landing-chat-line is-xiaomei">
              <span class="landing-xiaomei-mark hanzi">美</span>
              <span class="landing-chat-bubble">
                <span class="landing-chat-name">{{ t('landing.xiaomeiName') }}</span>
                <span class="landing-chat-hanzi" lang="zh-CN">你今天好吗？</span>
                <span class="landing-chat-pinyin" lang="zh-Latn-pinyin">Nǐ jīntiān hǎo ma?</span>
              </span>
            </p>
            <p class="landing-chat-line is-you">
              <span class="landing-chat-bubble">
                <span class="landing-chat-name">{{ t('landing.chatYou') }}</span>
                <span class="landing-chat-hanzi" lang="zh-CN">我很好，谢谢！</span>
                <span class="landing-chat-pinyin" lang="zh-Latn-pinyin">Wǒ hěn hǎo, xièxie!</span>
              </span>
            </p>
            <p class="landing-chat-line is-xiaomei">
              <span class="landing-xiaomei-mark hanzi">美</span>
              <span class="landing-chat-bubble">
                <span class="landing-chat-hanzi" lang="zh-CN">很好！你的声调很清楚。</span>
                <span class="landing-chat-pinyin" lang="zh-Latn-pinyin">Hěn hǎo! Nǐ de shēngdiào hěn qīngchu.</span>
              </span>
            </p>
          </div>
        </li>

        <li class="landing-way is-classes">
          <div class="landing-way-copy">
            <span class="landing-way-number" aria-hidden="true">04</span>
            <h3>{{ t('landing.classesTitle') }}</h3>
            <p>{{ t('landing.classesCopy') }}</p>
            <p class="landing-xp">{{ t('landing.freeNoXp') }}</p>
          </div>
          <FeaturedClasses class="landing-render" />
        </li>
      </ol>
    </section>

    <section id="about" class="landing-about" aria-labelledby="landing-about-title">
      <div class="landing-about-copy">
        <p class="page-eyebrow">{{ t('landing.aboutEyebrow') }}</p>
        <h2 id="landing-about-title" class="landing-h2">{{ t('landing.aboutTitle') }}</h2>
        <p>{{ t('landing.aboutCopy') }}</p>
        <RouterLink class="btn btn-link" to="/responsible-ai">
          {{ t('landing.aboutLink') }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
      </div>
      <dl class="landing-roles">
        <div v-for="role in ROLES" :key="role.title" class="landing-role">
          <dt>{{ t(role.title) }}</dt>
          <dd>
            {{ t(role.copy) }}
            <RouterLink v-if="role.title === 'landing.roleTeachers'" class="btn btn-link" :to="TEACH_ROUTE">
              {{ t('landing.teachWithUs') }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </dd>
        </div>
      </dl>
    </section>

    <section class="landing-faq" aria-labelledby="landing-faq-title">
      <div class="landing-faq-head">
        <h2 id="landing-faq-title" class="landing-h2">{{ t('landing.faqTitle') }}</h2>
        <RouterLink class="btn btn-link" to="/help">
          {{ t('landing.allQuestions') }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
      </div>
      <div class="landing-faq-list">
        <details v-for="question in QUESTIONS" :key="question.question" class="public-qa landing-faq-item">
          <summary>
            <span>{{ t(question.question) }}</span>
            <IconGlyph class="public-qa-chevron" name="chevronDown" :size="18" />
          </summary>
          <div class="landing-faq-answer">
            <p>{{ t(question.answer) }}</p>
            <RouterLink class="btn btn-link" :to="question.to">
              {{ t(question.more) }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </div>
        </details>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import FeaturedClasses from '@/components/public/FeaturedClasses.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { landingMessages } from '@/plugins/landingMessages'
import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'
import { canSpeak, speakChinese, stopSpeaking } from '@/utils/speech'

/** After an account is deleted, the Account page brings the user here with this notice. */
const ACCOUNT_DELETED_NOTICE = 'account-deleted'

const CHIPS = ['landing.chipFree', 'landing.chipBeginner', 'landing.chipVerified', 'landing.chipPlan'] as const

const GREETING = [
  { hanzi: '你', pinyin: 'nǐ' },
  { hanzi: '好', pinyin: 'hǎo' },
] as const

type PathState = 'done' | 'current' | 'locked'

const PATH: readonly { hanzi: string; pinyin: string; state: PathState }[] = [
  { hanzi: '你好', pinyin: 'nǐ hǎo', state: 'done' },
  { hanzi: '谢谢', pinyin: 'xièxie', state: 'done' },
  { hanzi: '再见', pinyin: 'zàijiàn', state: 'current' },
  { hanzi: '朋友', pinyin: 'péngyou', state: 'locked' },
]

/** Finished lessons are stamped, the next one is an outline seal, locked ones are muted. */
const SEAL_CLASS: Record<PathState, string> = { done: '', current: 'is-outline', locked: 'is-muted' }

const PRACTICE_TYPES = [
  { icon: 'layers', label: 'landing.typeFlashcards', pigment: 'cobalt' },
  { icon: 'listening', label: 'landing.typeListening', pigment: 'teal' },
  { icon: 'puzzle', label: 'landing.typeMatching', pigment: 'jade' },
  { icon: 'brush', label: 'landing.typeTracing', pigment: 'cinnabar' },
  { icon: 'quiz', label: 'landing.typeQuizzes', pigment: 'ochre' },
  { icon: 'story', label: 'landing.typeStory', pigment: 'plum' },
] as const

const ROLES = [
  { title: 'landing.roleStudents', copy: 'landing.roleStudentsCopy' },
  { title: 'landing.roleTeachers', copy: 'landing.roleTeachersCopy' },
  { title: 'landing.roleAdmins', copy: 'landing.roleAdminsCopy' },
] as const

const TEACH_ROUTE = { path: '/auth', query: { tab: 'register', role: 'teacher' } } satisfies RouteLocationRaw

const QUESTIONS = [
  {
    question: 'landing.faqStartQuestion',
    answer: 'landing.faqStartAnswer',
    more: 'landing.faqStartMore',
    to: '/help#getting-started',
  },
  {
    question: 'landing.faqTeachersQuestion',
    answer: 'landing.faqTeachersAnswer',
    more: 'landing.faqTeachersMore',
    to: '/help#for-teachers',
  },
  {
    question: 'landing.faqAiQuestion',
    answer: 'landing.faqAiAnswer',
    more: 'landing.faqAiMore',
    to: '/help#ai-and-safety',
  },
] as const

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ landing: landingMessages })

const startRoute = computed<RouteLocationRaw>(() =>
  auth.isAuthenticated
    ? defaultRouteForRole(auth.activeRole)
    : { path: '/auth', query: { tab: 'register', role: 'student' } },
)
const accountDeleted = computed(() => route.query.notice === ACCOUNT_DELETED_NOTICE)

// The Listen button reads 你好 aloud with the browser's own speech (hidden where there is none).
const speechAvailable = ref(false)
const speaking = ref(false)

onMounted(() => {
  speechAvailable.value = canSpeak()
})
onBeforeUnmount(() => {
  if (speaking.value) stopSpeaking()
})

function listen(): void {
  speakChinese('你好', false, (value) => {
    speaking.value = value
  })
}

function dismissNotice(): void {
  const query = { ...route.query }
  delete query.notice
  void router.replace({ query })
}
</script>
