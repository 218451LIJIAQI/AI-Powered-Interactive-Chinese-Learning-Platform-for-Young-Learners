<template>
  <section class="panel account-card" aria-labelledby="account-hints-title" data-testid="account-hints">
    <h2 id="account-hints-title" class="card-title">{{ t('account.hintsTitle') }}</h2>

    <template v-if="settings">
      <p class="account-hints-intro">{{ intro }}</p>
      <FilterChips
        :model-value="settings.mode"
        :label="t('account.hintsModeLabel')"
        :options="modes"
        :disabled="saving"
        @update:model-value="change"
      />
      <p class="field-hint" data-testid="hint-rule">{{ ruleText }}</p>
      <p v-if="saveError" class="field-error" role="alert">{{ saveError }}</p>
    </template>
    <p v-else-if="loadFailed" class="field-error" role="alert">{{ t('account.hintsLoadFailed') }}</p>
    <div v-else class="account-hints-loading" aria-hidden="true">
      <span class="skeleton skeleton-line" />
      <span class="skeleton skeleton-line is-short" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { hintApi } from '@/api/learn'
import FilterChips, { type FilterChipOption } from '@/components/common/FilterChips.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useHintSettings } from '@/composables/useHintSettings'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { accountMessages } from '@/plugins/accountMessages'
import type { HintMode, HintTier } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'

/**
 * The learner's AI hints on the Account page: Automatic (after being stuck a while), Only when I ask, or Off, with what
 * the rule of their level means. A change saves straight away and says what hints do now, with Undo (S14.14, S14.15).
 */
const emit = defineEmits<{ saved: [message: string, undo?: { label: string; run: () => void }] }>()

/** The level of a hint rule, as the short HSK code a sentence uses ("At your level (HSK 1)"). */
const TIER_CODES: Readonly<Record<HintTier, string>> = { HSK1: 'HSK 1', HSK2: 'HSK 2', HSK3: 'HSK 3' }

const { t } = useLocalMessages({ account: accountMessages })
const { settings, failed: loadFailed, remember } = useHintSettings()

const saving = ref(false)
const saveError = ref<string | null>(null)
const isUnmounted = useIsUnmounted()

const modes = computed<FilterChipOption<HintMode>[]>(() => [
  { value: 'AUTOMATIC', label: t('account.hintsAutomatic'), testId: 'hint-mode-automatic' },
  { value: 'ON_REQUEST', label: t('account.hintsOnRequest'), testId: 'hint-mode-on-request' },
  { value: 'OFF', label: t('account.hintsOff'), testId: 'hint-mode-off' },
])

const intro = computed(() => {
  switch (settings.value?.mode) {
    case 'ON_REQUEST':
      return t('account.hintsIntroOnRequest')
    case 'OFF':
      return t('account.hintsIntroOff')
    default:
      return t('account.hintsIntroAutomatic')
  }
})

/** What the chosen mode means at the learner's level, and that it saves at once. */
const ruleText = computed(() => {
  const current = settings.value
  if (!current) return ''
  if (current.mode === 'OFF') return [t('account.hintsRuleOff'), t('account.hintsSaveNote')].join(' ')
  return [
    current.mode === 'ON_REQUEST'
      ? t('account.hintsRuleOnRequest')
      : t('account.hintsRuleAutomatic', { level: TIER_CODES[current.tier], seconds: current.delaySeconds }),
    current.perSet > 0 ? t('account.hintsPerSet', { count: current.perSet }, current.perSet) : '',
    t('account.hintsNoLevelCheck'),
    t('account.hintsSaveNote'),
  ]
    .filter(Boolean)
    .join(' ')
})

/** Saves `mode`; the toast offers to go back to the mode before it, except when it is itself an Undo. */
async function change(mode: HintMode, undoable = true): Promise<void> {
  const before = settings.value?.mode
  if (saving.value || before === undefined || before === mode) return
  const version = getAuthSessionVersion()
  saving.value = true
  saveError.value = null
  try {
    const saved = await hintApi.setMode(mode)
    if (!remember(saved, version) || isUnmounted()) return
    const undo = undoable
      ? {
          label: t('common.undo'),
          run: () => {
            if (!isUnmounted() && getAuthSessionVersion() === version) void change(before, false)
          },
        }
      : undefined
    emit('saved', t(`account.hintsSaved${saved.mode}`), undo)
  } catch (reason) {
    if (!isUnmounted() && version === getAuthSessionVersion()) {
      saveError.value = resolveApiError(reason, t('account.hintsSaveFailed'))
    }
  } finally {
    if (!isUnmounted()) saving.value = false
  }
}
</script>
