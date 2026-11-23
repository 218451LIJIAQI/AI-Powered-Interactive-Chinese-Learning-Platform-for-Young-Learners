<template>
  <div v-if="visible && (text || offered || error)" class="hint-panel" :class="`hint-panel--${variant}`">
    <div
      v-if="text"
      ref="card"
      class="hint-panel-card"
      role="status"
      aria-live="polite"
      tabindex="-1"
      :data-testid="ui.cardTestId"
    >
      <div class="hint-panel-head">
        <p class="hint-panel-title">
          <IconGlyph :name="ui.icon" :size="16" />
          {{ t('hintChrome.hintTitle') }}
        </p>
        <span class="hint-panel-ai">{{ t('aiSafety.disclaimer') }}</span>
        <button
          class="btn btn-ghost btn-sm hint-panel-report"
          type="button"
          :disabled="reported"
          :aria-label="reported ? undefined : t('hintChrome.hintReportTitle')"
          :data-testid="ui.reportTestId"
          @click="reporting = true"
        >
          <IconGlyph :name="reported ? 'status' : 'flag'" :size="16" />
          {{ reported ? t('aiSafety.reported') : t('aiSafety.report') }}
        </button>
      </div>
      <p class="hint-panel-text"><CjkText :text="text" /></p>
      <p class="hint-panel-note" :data-testid="ui.noteTestId">{{ footnote }}</p>
    </div>

    <div v-else-if="offered" class="hint-panel-offer" :data-testid="ui.offerTestId">
      <IconGlyph :name="ui.icon" :size="16" />
      <span class="hint-panel-offer-text">{{ offerText ?? t('hintChrome.hintOffer') }}</span>
      <button
        ref="offerButton"
        class="btn btn-secondary btn-sm"
        type="button"
        :aria-disabled="loading || undefined"
        :aria-busy="loading"
        :data-testid="ui.showTestId"
        @click="!loading && emit('show')"
      >
        {{ t('hintChrome.showHint') }}
      </button>
    </div>

    <p v-if="error" ref="failure" class="field-error" role="alert" tabindex="-1" :data-testid="ui.errorTestId">
      {{ error }}
    </p>
  </div>
  <slot v-else-if="!visible" />

  <HintReportDialog
    v-if="reporting && text"
    :text="text"
    :context="reportContext"
    @close="reporting = false"
    @sent="onReported"
  />
  <AppToast v-if="ownToast" :message="toast" />
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useToast } from '@/composables/useToast'
import { hintChromeMessages } from '@/plugins/hintChromeMessages'

import CjkText from './CjkText.vue'
import HintReportDialog from './HintReportDialog.vue'

const VARIANT_UI = {
  practice: {
    icon: 'hint',
    cardTestId: 'hint-card',
    reportTestId: 'hint-report',
    noteTestId: undefined,
    offerTestId: 'hint-offer',
    showTestId: 'hint-show',
    errorTestId: 'hint-failed',
  },
  class: {
    icon: 'about',
    cardTestId: 'class-hint',
    reportTestId: 'class-hint-report',
    noteTestId: 'class-hint-settings',
    offerTestId: 'class-hint-offer',
    showTestId: 'class-hint-show',
    errorTestId: undefined,
  },
} as const

/**
 * Shared SparkBot hint surface used by both Practice/Lesson runs (`practice`) and focus-mode Class activities/quizzes
 * (`class`): offer bar, shown hint card with AI disclaimer and report dialog, error alert, and keyboard focus handoff.
 */
const props = withDefaults(
  defineProps<{
    variant: 'practice' | 'class'
    visible?: boolean
    text: string | null
    offered: boolean
    loading: boolean
    error: string | null
    offerText?: string
    footnote: string
    reportContext: string
    /** Identity of the active hint; changing it closes any open report dialog. */
    hintKey: unknown
    reported: boolean
    /** Set false when the parent displays the toast outside a conditionally unmounted footer slot. */
    ownToast?: boolean
  }>(),
  { visible: true, offerText: undefined, ownToast: true },
)
const emit = defineEmits<{ show: []; reported: [] }>()

const { t } = useLocalMessages({ hintChrome: hintChromeMessages })
const { toast, showToast } = useToast()

const ui = computed(() => VARIANT_UI[props.variant])
const card = ref<HTMLElement | null>(null)
const offerButton = ref<HTMLButtonElement | null>(null)
const failure = ref<HTMLElement | null>(null)
const reporting = ref(false)

watch(
  () => props.hintKey,
  () => {
    reporting.value = false
  },
)

// When accepting an offer unmounts the offer button (replaced by the hint card or a hard refusal), move focus so
// keyboard users do not drop to <body>. Transient errors keep the offer button mounted for immediate retry.
watch([() => props.text, () => props.offered, () => props.error], async ([nextText, nextOffered, nextError]) => {
  const replacingOffer = Boolean(nextText) || (!nextOffered && Boolean(nextError))
  if (!replacingOffer || document.activeElement !== offerButton.value) return
  await nextTick()
  if (nextText) card.value?.focus()
  else failure.value?.focus()
})

/** The Report button becomes disabled after submitting, so move focus onto the hint card itself. */
async function onReported(): Promise<void> {
  reporting.value = false
  emit('reported')
  if (props.ownToast) showToast(t('aiSafety.reportSent'))
  await nextTick()
  card.value?.focus()
}
</script>

<style scoped>
@keyframes hint-panel-rise {
  from {
    opacity: 0;
    transform: translateY(var(--space-1));
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hint-panel,
.hint-panel-card {
  display: grid;
  gap: var(--space-2);
}

.hint-panel--class {
  min-width: 0;
}

.hint-panel--practice {
  animation: hint-panel-rise var(--duration-base) var(--ease-out);
}

.hint-panel-offer,
.hint-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.hint-panel-offer {
  padding: var(--space-2) var(--space-2) var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-sunken);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-strong);
}

.hint-panel--practice .hint-panel-offer {
  column-gap: var(--space-3);
  min-height: var(--space-12);
  padding-left: var(--space-4);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-btn-lg);
  background: transparent;
  color: var(--text-secondary);
  font-weight: var(--font-weight-medium);
}

.hint-panel-offer-text {
  flex: 1;
  min-width: 0;
}

.hint-panel--practice .hint-panel-offer-text {
  flex: 1 1 auto;
}

.hint-panel-offer > :deep(.icon-glyph),
.hint-panel-title > :deep(.icon-glyph) {
  color: var(--color-primary-600);
}

.hint-panel-card {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface-sunken);
}

.hint-panel--practice .hint-panel-card {
  border-radius: var(--radius-btn-lg);
  background: var(--color-primary-50);
  box-shadow: inset 0 0 0 1px var(--color-primary-200);
}

.hint-panel-card:focus-visible {
  outline: var(--border-width-focus) solid var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
}

.hint-panel-card p {
  margin: 0;
}

.hint-panel--practice .hint-panel-head {
  gap: var(--space-1) var(--space-3);
  min-width: 0;
}

.hint-panel-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-heading);
}

.hint-panel--class .hint-panel-title {
  margin-right: auto;
}

.hint-panel--practice .hint-panel-title {
  gap: var(--space-2);
  color: var(--color-primary-700);
  line-height: var(--line-height-snug);
}

.hint-panel-ai,
.hint-panel-note {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-snug);
}

.hint-panel--practice .hint-panel-ai {
  margin-left: auto;
}

.hint-panel-report {
  flex: none;
}

.hint-panel--practice .hint-panel-report {
  margin-right: calc(-1 * var(--space-2));
}

.hint-panel-text {
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.hint-panel--practice .hint-panel-text {
  font-weight: var(--font-weight-strong);
  line-height: var(--line-height-normal);
}

@media (forced-colors: active) {
  .hint-panel--practice .hint-panel-card {
    border: 1px solid CanvasText;
  }
}
</style>
