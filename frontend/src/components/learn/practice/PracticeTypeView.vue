<template>
  <section class="practice-stage" :aria-labelledby="selected ? `${baseId}-title` : undefined">
    <StatePanel
      v-if="sets.loading.value"
      class="practice-stage-state"
      tone="loading"
      :title="t('practice.setsLoading')"
      :message="t('practice.setsLoadingHint')"
    />
    <StatePanel
      v-else-if="sets.error.value"
      class="practice-stage-state"
      tone="error"
      icon="alert"
      :title="t('practice.setsError')"
      :message="sets.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="sets.reload">{{ t('practice.retry') }}</button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="!setList.length"
      class="practice-stage-state"
      tone="empty"
      icon="practice"
      :title="t('practice.noSets')"
      :message="t('practice.noSetsHint')"
    />

    <template v-else-if="selected">
      <header class="practice-stage-head">
        <h2 :id="`${baseId}-title`" class="practice-stage-title">{{ selected.title }}</h2>
        <p class="practice-stage-meta">{{ meta }}</p>
      </header>

      <div class="practice-sets" role="group" :aria-label="t('practice.setsOfType', { count: setList.length })">
        <span class="practice-sets-count">{{
          t('practice.setsCount', { count: setList.length }, setList.length)
        }}</span>
        <RouterLink
          v-for="item in setList"
          :key="item.id"
          class="filter-chip practice-set-chip"
          :to="{ name: 'student-practice', params: { type: info.slug }, query: { set: item.id } }"
          :aria-current="item.id === selected.id ? 'true' : undefined"
          :data-testid="`set-${item.id}`"
        >
          <IconGlyph v-if="item.done" name="check" :size="14" />
          {{ item.title }}<span v-if="item.done" class="sr-only"> ({{ t('practice.done') }})</span>
        </RouterLink>
      </div>

      <StatePanel
        v-if="detail.error.value"
        class="practice-stage-state"
        tone="error"
        icon="alert"
        :title="t('practice.setError')"
        :message="detail.error.value"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('practice.retry') }}</button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="detail.loading.value || detail.data.value?.id !== selected.id"
        class="practice-stage-state"
        tone="loading"
        :title="t('practice.setLoading')"
        :message="t('practice.setLoadingHint')"
      />
      <SetDoneCard
        v-else-if="result"
        :result="result"
        :xp-reward="selected.xpReward"
        :stroke-count="detail.data.value?.strokeCount ?? null"
        :characters="detail.data.value?.items[0]?.hanzi ?? ''"
        :recap="recap"
        @again="practiseAgain"
        @next="openSet"
      />
      <component
        :is="player"
        v-else-if="detail.data.value"
        :key="runKey"
        :set="detail.data.value"
        @finished="onFinished"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch, type Component } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { practiceApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSetSummary, PracticeType } from '@/types/learn'

import FlashcardsPlayer from './FlashcardsPlayer.vue'
import ItemsPlayer from './ItemsPlayer.vue'
import MatchingPlayer from './MatchingPlayer.vue'
import { practiceTypeInfo } from './practiceTypes'
import SetDoneCard from './SetDoneCard.vue'
import TracingPlayer from './TracingPlayer.vue'

/**
 * One activity's stage: the chosen set's title and meta line (the first set not done yet unless `?set=` names one), the
 * type's sets as chips, then the set's player and, once finished, its result in the same place.
 */
const props = defineProps<{ type: PracticeType }>()

const PLAYERS: Readonly<Partial<Record<PracticeType, Component>>> = {
  FLASHCARDS: FlashcardsPlayer,
  QUIZZES: ItemsPlayer,
  LISTENING: ItemsPlayer,
  TRACING: TracingPlayer,
  DRAG_DROP: ItemsPlayer,
  MATCHING: MatchingPlayer,
}

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ practice: practiceMessages })
const baseId = useId()

const info = computed(() => practiceTypeInfo(props.type))

const sets = useResource(() => practiceApi.sets(props.type))
const setList = computed(() => sets.data.value ?? [])

const selected = computed<PracticeSetSummary | null>(() => {
  const wanted = Number(route.query.set)
  return (
    setList.value.find((item) => item.id === wanted) ??
    setList.value.find((item) => !item.done) ??
    setList.value[0] ??
    null
  )
})

const detail = useResource(
  () => (selected.value ? practiceApi.set(props.type, selected.value.id) : Promise.resolve(null)),
  false,
)

const result = ref<PracticeResult | null>(null)
const recap = ref('')
const runKey = ref(0)
const player = computed(() => PLAYERS[props.type])

/** "Flashcards · 6 cards · HSK 1 · +10 XP"; a traced set counts strokes, which only the set itself knows. */
const meta = computed(() => {
  const set = selected.value
  if (!set) return ''
  const units =
    props.type === 'TRACING'
      ? detail.data.value?.id === set.id
        ? (detail.data.value.strokeCount ?? null)
        : null
      : set.itemCount
  const parts = [t(info.value.metaKey)]
  if (units !== null) parts.push(t(info.value.unitKey, { count: units }, units))
  if (set.hskLevel) parts.push(`HSK ${set.hskLevel}`)
  parts.push(t('practice.xpTag', { xp: set.xpReward }))
  return parts.join(' · ')
})

watch(
  () => selected.value?.id,
  (id) => {
    result.value = null
    if (id !== undefined) void detail.reload()
  },
  { immediate: true },
)

async function onFinished(finished: PracticeResult, finishedRecap = ''): Promise<void> {
  // Keep the finished set on screen: the reloaded list marks it done, which would move the default choice on.
  const finishedId = selected.value?.id
  if (finishedId !== undefined && Number(route.query.set) !== finishedId) {
    await router.replace({ query: { ...route.query, set: finishedId } })
  }
  recap.value = finishedRecap
  result.value = finished
  void sets.reload()
}

function practiseAgain(): void {
  result.value = null
  runKey.value += 1
  void detail.reload()
}

function openSet(setId: number): void {
  void router.push({ name: 'student-practice', params: { type: info.value.slug }, query: { set: setId } })
}
</script>
