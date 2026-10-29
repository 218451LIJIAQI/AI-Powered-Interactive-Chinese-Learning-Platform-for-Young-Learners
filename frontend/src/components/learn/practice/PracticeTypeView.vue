<template>
  <div class="practice-type" :class="`practice-pigment-${info.pigment}`">
    <PracticeHead :title="t(info.labelKey)" :lede="lede">
      <template #mark><PracticeGlyph :type="type" size="lg" /></template>
    </PracticeHead>

    <div class="practice-run" :class="{ 'has-sets': setList.length > 0 }">
      <section class="practice-stage" :aria-labelledby="selected ? `${baseId}-title` : undefined">
        <StatePanel
          v-if="sets.loading.value"
          tone="loading"
          :title="t('practice.setsLoading')"
          :message="t('practice.setsLoadingHint')"
        />
        <StatePanel
          v-else-if="sets.error.value"
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
          tone="empty"
          icon="practice"
          :title="t('practice.noSets')"
          :message="t('practice.noSetsHint')"
        >
          <template #actions>
            <RouterLink class="btn btn-secondary" :to="{ name: 'student-practice' }">
              {{ t('practice.allActivities') }}
            </RouterLink>
          </template>
        </StatePanel>

        <template v-else-if="selected">
          <header class="practice-stage-head">
            <div class="practice-stage-copy">
              <h2 :id="`${baseId}-title`" class="practice-stage-title">{{ selected.title }}</h2>
              <p class="practice-stage-meta">{{ setMeta(selected) }}</p>
            </div>
            <StatusPill v-if="selected.done" :label="t('practice.done')" tone="success" icon="check" />
          </header>

          <StatePanel
            v-if="detail.error.value"
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
            tone="loading"
            :title="t('practice.setLoading')"
            :message="t('practice.setLoadingHint')"
          />
          <SetDoneCard
            v-else-if="result"
            :result="result"
            :xp-reward="selected.xpReward"
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

      <div
        v-if="setList.length"
        class="practice-sets"
        role="group"
        :aria-label="t('practice.setsOfType', { count: setList.length })"
      >
        <p class="practice-sets-head">
          <span class="page-eyebrow">{{ t('practice.setsTitle') }}</span>
          <span class="practice-sets-count">{{
            t('practice.setsDone', { done: doneCount, total: setList.length })
          }}</span>
        </p>
        <ul class="practice-sets-list">
          <li v-for="item in setList" :key="item.id">
            <RouterLink
              class="practice-set"
              :class="{ 'is-current': item.id === selected?.id, 'is-done': item.done }"
              :to="{ name: 'student-practice', params: { type: info.slug }, query: { set: item.id } }"
              :aria-current="item.id === selected?.id ? 'true' : undefined"
              :data-testid="`set-${item.id}`"
            >
              <span class="practice-set-mark" aria-hidden="true">
                <IconGlyph v-if="item.done" name="check" :size="14" />
              </span>
              <span class="practice-set-title"
                >{{ item.title }}<span v-if="item.done" class="sr-only"> ({{ t('practice.done') }})</span></span
              >
              <span class="practice-set-meta">{{ itemMeta(item) }}</span>
            </RouterLink>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch, type Component } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { practiceApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSetSummary, PracticeType } from '@/types/learn'

import FlashcardsPlayer from './FlashcardsPlayer.vue'
import ItemsPlayer from './ItemsPlayer.vue'
import MatchingPlayer from './MatchingPlayer.vue'
import PracticeGlyph from './PracticeGlyph.vue'
import PracticeHead from './PracticeHead.vue'
import { practiceTypeInfo } from './practiceTypes'
import SetDoneCard from './SetDoneCard.vue'
import StoryPlayer from './StoryPlayer.vue'
import TracingPlayer from './TracingPlayer.vue'

/**
 * One Practice type: its header (glyph, name, what it trains), the stage with the chosen set's player (the first set
 * not done yet unless `?set=` names one) and its result, and the type's sets beside it.
 */
const props = defineProps<{ type: PracticeType }>()

const PLAYERS: Readonly<Record<PracticeType, Component>> = {
  FLASHCARDS: FlashcardsPlayer,
  QUIZZES: ItemsPlayer,
  LISTENING: ItemsPlayer,
  TRACING: TracingPlayer,
  DRAG_DROP: ItemsPlayer,
  MATCHING: MatchingPlayer,
  STORY: StoryPlayer,
}

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ practice: practiceMessages })
const baseId = useId()

const info = computed(() => practiceTypeInfo(props.type))
// The time ("about 3 min") never splits, and the separator stays with the text before it.
const lede = computed(() =>
  [t(info.value.trainsKey), t('practice.aboutMinutes', { minutes: info.value.minutes }).replaceAll(' ', '\u00a0')].join(
    '\u00a0· ',
  ),
)

const sets = useResource(() => practiceApi.sets(props.type))
const setList = computed(() => sets.data.value ?? [])
const doneCount = computed(() => setList.value.filter((item) => item.done).length)

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
const runKey = ref(0)
const player = computed(() => PLAYERS[props.type])

watch(
  () => selected.value?.id,
  (id) => {
    result.value = null
    if (id !== undefined) void detail.reload()
  },
  { immediate: true },
)

function itemMeta(set: PracticeSetSummary): string {
  const parts = [t(info.value.unitKey, { count: set.itemCount }, set.itemCount)]
  if (set.hskLevel) parts.push(`HSK ${set.hskLevel}`)
  return parts.join(' · ')
}

function setMeta(set: PracticeSetSummary): string {
  return [t(info.value.labelKey), itemMeta(set), `+${set.xpReward} XP`].join(' · ')
}

async function onFinished(finished: PracticeResult): Promise<void> {
  // Keep the finished set on screen: the reloaded list marks it done, which would move the default choice on.
  const finishedId = selected.value?.id
  if (finishedId !== undefined && Number(route.query.set) !== finishedId) {
    await router.replace({ query: { ...route.query, set: finishedId } })
  }
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
