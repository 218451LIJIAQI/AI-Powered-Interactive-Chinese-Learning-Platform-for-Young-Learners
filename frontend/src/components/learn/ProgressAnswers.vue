<template>
  <dl v-if="data" class="progress-answers">
    <div v-if="hasAnswers" class="progress-answers-row" data-testid="answers-this-week">
      <dt>{{ t('progress.answersThisWeek') }}</dt>
      <dd>
        <span class="tag">{{ t('progress.sureAndRight', { count: data.sureAndRight }) }}</span>
        <span class="tag is-outline">{{ t('progress.luckyGuesses', { count: data.luckyGuesses }) }}</span>
        <span class="tag is-outline">{{ t('progress.sureButWrong', { count: data.sureButWrong }) }}</span>
        <span v-if="data.wrongGuesses > 0" class="tag is-outline">
          {{ t('progress.wrongGuesses', { count: data.wrongGuesses }) }}
        </span>
        <RouterLink
          v-if="hasWrongAnswers"
          class="btn btn-secondary btn-sm"
          :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
        >
          {{ t('common.reviewMistakes') }}
        </RouterLink>
      </dd>
    </div>
    <div class="progress-answers-row">
      <dt>{{ t('progress.hintsThisWeek') }}</dt>
      <dd>
        <template v-if="data.hintsUsed > 0">
          <span class="tag">{{ t('progress.hintsUsed', { count: data.hintsUsed }) }}</span>
          <span v-if="mostIn" class="tag is-outline">{{ t('progress.mostIn', { type: mostIn }) }}</span>
        </template>
        <span v-else class="type-caption">{{ t('progress.noHintsYet') }}</span>
        <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-account' }">
          {{ t('progress.hintSettings') }}
        </RouterLink>
      </dd>
    </div>
  </dl>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { xpApi } from '@/api/learn'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { progressMessages } from '@/plugins/progressMessages'

/**
 * The last seven days of Practice confidence ratings and hint usage. The answers row appears after the first
 * rated answer. These secondary figures load independently of the skills above.
 */
const { t } = useLocalMessages({ progress: progressMessages })

const { data } = useResource(xpApi.progressAnswers)

const hasWrongAnswers = computed(() => {
  const week = data.value
  return Boolean(week && (week.sureButWrong > 0 || week.wrongGuesses > 0))
})

const hasAnswers = computed(() => {
  const week = data.value
  return Boolean(week && (week.sureAndRight > 0 || week.luckyGuesses > 0 || hasWrongAnswers.value))
})

const mostIn = computed(() => {
  const type = data.value?.mostHintsIn
  return type ? t(`practiceType.${type}`) : ''
})
</script>
