<template>
  <section class="panel admin-detail">
    <div class="admin-detail-heading">
      <h2 :id="`${baseId}-title`" class="card-title">{{ t('adminAi.hintsTitle') }}</h2>
      <p class="admin-meta">{{ t('adminAi.hintsIntro') }}</p>
    </div>

    <StatePanel v-if="resource.loading.value" tone="loading" :title="t('common.loadingTitle')" />
    <StatePanel
      v-else-if="resource.error.value"
      tone="error"
      :title="t('adminAi.hintsLoadFailed')"
      :message="resource.error.value"
      @retry="resource.reload"
    />

    <form v-else class="admin-detail" novalidate @submit.prevent="save">
      <div class="data-table-wrap" role="region" :aria-labelledby="`${baseId}-title`" tabindex="0">
        <table class="data-table admin-hint-table">
          <thead>
            <tr>
              <th scope="col">{{ t('adminAi.hintsColLevel') }}</th>
              <th scope="col">{{ t('adminAi.hintsColAfter') }}</th>
              <th scope="col">{{ t('adminAi.hintsColPerSet') }}</th>
              <th scope="col">{{ t('adminAi.hintsColGives') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.tier" :data-testid="`hint-rule-${row.tier}`">
              <th scope="row">{{ tierLabels[row.tier] }}</th>
              <td>
                <select
                  v-model.number="row.delaySeconds"
                  class="select"
                  :aria-label="`${tierLabels[row.tier]} · ${t('adminAi.hintsColAfter')}`"
                  :disabled="saving"
                  @change="saveError = null"
                >
                  <option v-for="seconds in delayOptions" :key="seconds" :value="seconds">
                    {{ seconds }}
                  </option>
                </select>
              </td>
              <td>
                <select
                  v-model.number="row.perSet"
                  class="select"
                  :aria-label="`${tierLabels[row.tier]} · ${t('adminAi.hintsColPerSet')}`"
                  :disabled="saving"
                  @change="saveError = null"
                >
                  <option :value="0">{{ t('adminAi.hintsEveryQuestion') }}</option>
                  <option v-for="count in 10" :key="count" :value="count">
                    {{ t('adminAi.hintsPerSetOption', { count }, count) }}
                  </option>
                </select>
              </td>
              <td class="admin-meta">{{ t(`adminAi.hintsGives${row.tier}`) }}</td>
            </tr>
            <tr>
              <th scope="row">{{ t('adminAi.hintsLevelCheckRow') }}</th>
              <td colspan="3" class="admin-meta">{{ t('adminAi.hintsNever') }}</td>
            </tr>
            <tr data-testid="hint-rule-stories">
              <th scope="row">{{ t('adminAi.hintsStoriesRow') }}</th>
              <td colspan="3" class="admin-meta">{{ t('adminAi.hintsNever') }}</td>
            </tr>
            <tr data-testid="hint-rule-class-quizzes">
              <th scope="row">{{ t('adminAi.hintsClassQuizRow') }}</th>
              <td colspan="3" class="admin-meta">{{ t('adminAi.hintsClassQuizNote') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="card-footer">
        <button
          class="btn btn-primary"
          type="submit"
          :disabled="!changed || saving"
          :aria-busy="saving"
          data-testid="save-hint-rules"
        >
          {{ t('adminAi.hintsSave') }}
        </button>
      </footer>
      <p v-if="saveError" class="field-error" role="alert">{{ saveError }}</p>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { adminAiApi } from '@/api/ai'
import StatePanel from '@/components/common/StatePanel.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import type { HintTier } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'

interface EditableHintRule {
  tier: HintTier
  delaySeconds: number
  perSet: number
}

/**
 * AI Settings, "Hints for learners" (A5.05): per level, how long a learner is stuck before a hint is offered and how
 * many hints a set may use. A hint is one step; what it gives follows the level and is not a setting, so that column
 * only describes it. Three rows under the levels say where the rules do not reach: level checks and AI Helper stories
 * never have hints, and a class lesson quiz has none until an admin allows SparkBot hints for that lesson (the lesson
 * editor's Quiz tab). Saved on its own.
 */
const emit = defineEmits<{ saved: [message: string] }>()

const SUGGESTED_DELAYS = [10, 15, 20, 30, 45, 60, 90, 120]

const { t } = useLocalMessages({ adminAi: adminAiMessages })
const tierLabels = computed<Record<HintTier, string>>(() => ({
  HSK1: t('hsk.level1'),
  HSK2: t('hsk.level2'),
  HSK3: t('hsk.level3'),
}))
const baseId = useId()

const resource = useResource(adminAiApi.hintPolicy)
// Existing rules can use any whole number from 5 to 120 seconds.
const delayOptions = computed(() => {
  const saved = resource.data.value?.map((level) => level.delaySeconds) ?? []
  return [...new Set([...SUGGESTED_DELAYS, ...saved])].sort((a, b) => a - b)
})
const rows = ref<EditableHintRule[]>([])
const saving = ref(false)
const saveError = ref<string | null>(null)
const isUnmounted = useIsUnmounted()

watch(resource.data, (levels) => {
  if (levels) rows.value = levels.map((level) => ({ ...level }))
})

const changed = computed(() => {
  const savedByTier = new Map((resource.data.value ?? []).map((level) => [level.tier, level]))
  return rows.value.some((row) => {
    const before = savedByTier.get(row.tier)
    return !before || row.delaySeconds !== before.delaySeconds || row.perSet !== before.perSet
  })
})

async function save(): Promise<void> {
  if (saving.value || !changed.value) return
  saving.value = true
  saveError.value = null
  try {
    const saved = await adminAiApi.saveHintPolicy(rows.value)
    if (isUnmounted()) return
    resource.replace(saved)
    emit('saved', t('adminAi.hintsSaved'))
  } catch (reason) {
    if (isUnmounted()) return
    saveError.value = resolveApiError(reason, t('adminAi.hintsSaveFailed'))
  } finally {
    if (!isUnmounted()) saving.value = false
  }
}
</script>
