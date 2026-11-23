<template>
  <div class="landing">
    <AppToast :message="toast" />

    <section class="landing-hero" aria-labelledby="landing-title">
      <div class="landing-hero-copy">
        <span class="landing-audience">
          <IconGlyph name="students" :size="14" />
          {{ t('landing.audience') }}
        </span>
        <div class="landing-hero-heading">
          <h1 id="landing-title" class="landing-title">{{ t('landing.heroTitle') }}</h1>
          <p class="landing-lead">{{ t('landing.heroLead') }}</p>
        </div>
        <div class="btn-group">
          <RouterLink class="btn btn-accent btn-lg" :to="startRoute">
            {{ startLabel }}
            <IconGlyph name="chevronRight" :size="18" />
          </RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" to="/classes">{{ t('common.exploreClasses') }}</RouterLink>
        </div>
        <ul class="landing-facts">
          <li v-for="fact in FACTS" :key="fact">
            <IconGlyph name="correct" :size="14" />
            {{ t(fact) }}
          </li>
        </ul>
      </div>

      <!-- A first lesson in miniature: the first word a learner meets, and the four tones it is spoken with. -->
      <div class="landing-preview">
        <div class="landing-word" :class="{ 'is-speaking': speaking }">
          <div class="landing-word-head">
            <p class="landing-preview-label">{{ t('landing.previewWord') }}</p>
            <!-- A lesson reads its word aloud, so the preview does too, where the device has a Mandarin voice. The
                 row keeps the button's height, so nothing moves when a browser lists its voices late. -->
            <button
              v-if="canHear"
              class="btn btn-secondary btn-sm landing-listen"
              type="button"
              :aria-label="t('landing.listenGreeting')"
              data-testid="landing-listen"
              @click="sayGreeting"
            >
              <IconGlyph name="audio" :size="16" />
              {{ t('landing.listen') }}
            </button>
          </div>
          <figure class="landing-word-figure">
            <span class="landing-word-tiles" lang="zh-CN">
              <span class="hanzi-tile landing-word-tile">你</span>
              <span class="hanzi-tile landing-word-tile">好</span>
            </span>
            <figcaption class="landing-word-gloss">
              <span lang="zh-Latn-pinyin">nǐ hǎo</span> · <span>{{ t('common.helloMeaning') }}</span>
            </figcaption>
          </figure>
        </div>

        <div class="landing-tones" role="img" :aria-label="t('landing.toneContours')">
          <p class="landing-preview-label">{{ t('landing.previewTones') }}</p>
          <ul class="landing-tones-list">
            <li v-for="tone in TONES" :key="tone.pinyin">
              <!-- pathLength: the line counts as 1 long, so the stylesheet can draw it in as one dash. -->
              <svg viewBox="0 0 40 32" focusable="false" aria-hidden="true">
                <path :d="tone.contour" pathLength="1" />
              </svg>
              <span lang="zh-Latn-pinyin">{{ tone.pinyin }}</span>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- What each part of MandarinSpark is for: explanations, not links. -->
    <section id="how-it-works" class="landing-section" aria-labelledby="landing-how-title">
      <div class="landing-section-head">
        <h2 id="landing-how-title" class="landing-section-title">{{ t('landing.howTitle') }}</h2>
        <p class="landing-section-lead">{{ t('landing.howLead') }}</p>
      </div>
      <ul class="panel landing-how-list">
        <li v-for="part in PARTS" :key="part.title" class="landing-how-item" :class="`tint-${part.tint}`">
          <LandingSpecimen :kind="part.art" />
          <h3 class="card-title">{{ t(part.title) }}</h3>
          <p>{{ t(part.copy) }}</p>
        </li>
      </ul>
    </section>

    <!-- What follows "Start learning": the three steps of the setup, in the setup's own numbers. -->
    <section class="landing-section" aria-labelledby="landing-plan-title">
      <div class="landing-section-head">
        <h2 id="landing-plan-title" class="landing-section-title">{{ t('landing.planTitle') }}</h2>
        <p class="landing-section-lead">{{ t('landing.planLead') }}</p>
      </div>
      <ol class="landing-steps">
        <li v-for="(step, index) in PLAN_STEPS" :key="step.title" class="landing-step">
          <!-- 一 二 三: one, two and three strokes, the first characters a learner can read. -->
          <span class="hanzi-tile landing-step-tile" lang="zh-CN" aria-hidden="true">{{ step.numeral }}</span>
          <p class="page-eyebrow">{{ t('landing.planStep', { number: index + 1 }) }}</p>
          <h3 class="card-title">{{ t(step.title) }}</h3>
          <p class="landing-step-copy">{{ t(step.copy) }}</p>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LandingSpecimen from '@/components/public/LandingSpecimen.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { useToast } from '@/composables/useToast'
import { landingMessages } from '@/plugins/landingMessages'
import { useAuthStore } from '@/stores/auth'
import { withoutQueryKeys } from '@/utils/queryText'
import { defaultRouteForRole } from '@/utils/role'

const FACTS = ['landing.factFree', 'landing.factPlan'] as const

/** The word of the hero's preview, as it is read aloud. */
const GREETING = '你好'

/** The four tones on one syllable, each with the line its pitch draws. */
const TONES = [
  { pinyin: 'mā', contour: 'M5 8 H35' },
  { pinyin: 'má', contour: 'M5 25 L35 7' },
  { pinyin: 'mǎ', contour: 'M5 12 Q18 34 35 9' },
  { pinyin: 'mà', contour: 'M5 6 L35 26' },
] as const

/** The four parts of the product, in the learner's sidebar order; each keeps one tint and one picture. */
const PARTS = [
  { art: 'path', tint: 'violet', title: 'nav.learn', copy: 'landing.learnCopy' },
  { art: 'cards', tint: 'teal', title: 'nav.practice', copy: 'landing.practiceCopy' },
  { art: 'chat', tint: 'amber', title: 'nav.aiHelper', copy: 'landing.aiCopy' },
  { art: 'scenes', tint: 'blue', title: 'nav.classes', copy: 'landing.classesCopy' },
] as const

/** The setup a new learner goes through (StudentSetupPage): the goal, the level check, then the plan. */
const PLAN_STEPS = [
  { numeral: '一', title: 'landing.planGoalTitle', copy: 'landing.planGoalCopy' },
  { numeral: '二', title: 'landing.planLevelTitle', copy: 'landing.planLevelCopy' },
  { numeral: '三', title: 'landing.planPathTitle', copy: 'landing.planPathCopy' },
] as const

/** After an account is deleted, the Account page brings the user here with this notice. */
const ACCOUNT_DELETED_NOTICE = 'account-deleted'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ landing: landingMessages })
const { toast, showToast } = useToast()

const startRoute = computed<RouteLocationRaw>(() =>
  auth.isAuthenticated ? defaultRouteForRole(auth.activeRole) : { path: '/auth', query: { tab: 'register' } },
)
const startLabel = computed(() => (auth.isAuthenticated ? t('landing.openMyHome') : t('landing.startLearning')))

/** The device has a Mandarin voice: Listen is offered only where the word will really be heard. */
const { hasInstalledVoice: canHear, speak } = useMandarinReadAloud()
const speaking = ref(false)

function sayGreeting(): void {
  speak(GREETING, false, (value) => {
    speaking.value = value
  })
}

// Shown once the toast's live region is on the page, so it is announced; the notice then leaves the address, so a
// reload does not say it again.
onMounted(() => {
  if (route.query.notice !== ACCOUNT_DELETED_NOTICE) return
  showToast(t('landing.accountDeleted'))
  void router.replace({ path: route.path, query: withoutQueryKeys(route.query, 'notice') })
})
</script>
