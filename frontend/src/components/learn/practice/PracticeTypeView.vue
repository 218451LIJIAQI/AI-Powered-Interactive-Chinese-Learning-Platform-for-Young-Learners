<template>
  <div class="practice-workspace">
    <PracticeSetList
      v-if="setList.length || sets.loading.value"
      :sets="setList"
      :selected-id="selectedId"
      :loading="sets.loading.value"
      :type="type"
    />

    <section class="practice-stage" :aria-labelledby="selected ? `${baseId}-title` : undefined">
      <StatePanel
        v-if="sets.loading.value"
        class="practice-stage-state"
        heading-tag="h2"
        tone="loading"
        :title="t('practice.setsLoading')"
        :message="t('practice.setsLoadingHint')"
      />
      <!-- Only without a list: a list read again after a finished set keeps the last one, its result and run. -->
      <StatePanel
        v-else-if="sets.error.value && !setList.length"
        class="practice-stage-state"
        heading-tag="h2"
        tone="error"
        icon="alert"
        :title="t('practice.setsError')"
        :message="sets.error.value"
        @retry="sets.reload"
      />
      <StatePanel
        v-else-if="!setList.length"
        class="practice-stage-state"
        heading-tag="h2"
        tone="empty"
        icon="practice"
        :title="t('practice.noSets')"
        :message="t('practice.noSetsHint')"
      />

      <template v-else-if="selected">
        <!-- The set's name is in the list beside the stage; the heading stays for assistive technology. -->
        <h2 :id="`${baseId}-title`" class="sr-only">{{ selected.title }} · {{ meta }}</h2>

        <StatePanel
          v-if="detail.error.value"
          class="practice-stage-state"
          tone="error"
          icon="alert"
          :title="t('practice.setError')"
          :message="detail.error.value"
          @retry="retryDetail"
        />
        <StatePanel
          v-else-if="detail.loading.value || detail.data.value?.id !== selected.id"
          class="practice-stage-state"
          tone="loading"
          :title="t('practice.setLoading')"
          :message="t('practice.setLoadingHint')"
        />
        <SetDoneCard
          v-else-if="completion"
          v-bind="completion"
          :set="detail.data.value"
          :busy="restarting"
          @again="practiseAgain"
        />
        <!-- A set read again gets a new player: a run never goes on over a set replaced under it. -->
        <component
          :is="PLAYERS[type]"
          v-else
          :key="`${detail.data.value.id}-${detail.data.value.runSignature ?? ''}`"
          :set="detail.data.value"
          @finished="onFinished"
        />
      </template>
    </section>

    <RankUpDialog
      v-if="rankingUp && completion?.result.xp.rankUp"
      :rank-up="completion.result.xp.rankUp"
      :streak="completion.result.xp.currentStreak"
      source="practice"
      @close="dismissRankUp"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, useId, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { practiceApi } from '@/api/learn'
import StatePanel from '@/components/common/StatePanel.vue'
import RankUpDialog from '@/components/learn/RankUpDialog.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useRecordById, useResource } from '@/composables/useResource'
import { PRACTICE_ACTIVITIES } from '@/config/practiceActivities'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSetSummary, PracticeType } from '@/types/learn'
import { parseRecordId } from '@/utils/recordId'

import AnswerPlayer from './AnswerPlayer.vue'
import FlashcardsPlayer from './FlashcardsPlayer.vue'
import MatchingPlayer from './MatchingPlayer.vue'
import PracticeSetList from './PracticeSetList.vue'
import type { RunTally } from './practiceTypes'
import SetDoneCard from './SetDoneCard.vue'
import TracingPlayer from './TracingPlayer.vue'
import { useRankUpLater } from './useRankUpLater'

/**
 * One activity: the type's sets in a list grouped by level (the first set not done yet is open unless `?set=` names
 * one) beside the stage with the set's player and, once finished, its result in the same place. A finished set that
 * takes the learner past a rank opens the rank-up dialog over its result.
 */
const props = defineProps<{ type: PracticeType }>()

const PLAYERS: Readonly<Record<PracticeType, Component>> = {
  FLASHCARDS: FlashcardsPlayer,
  LISTENING: AnswerPlayer,
  TRACING: TracingPlayer,
  DRAG_DROP: AnswerPlayer,
  MATCHING: MatchingPlayer,
  TYPING: AnswerPlayer,
}

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ practice: practiceMessages })
const baseId = useId()

const sets = useResource(() => practiceApi.sets(props.type))
const setList = computed(() => sets.data.value ?? [])

const selected = computed<PracticeSetSummary | null>(() => {
  const wanted = parseRecordId(route.query.set)
  return (
    setList.value.find((item) => item.id === wanted) ??
    setList.value.find((item) => !item.done) ??
    setList.value[0] ??
    null
  )
})

const selectedId = computed(() => selected.value?.id ?? null)
const detail = useRecordById(selectedId, (id) => practiceApi.set(props.type, id))

const completion = shallowRef<{ result: PracticeResult; recap: string; tally: RunTally | null } | null>(null)
const { rankingUp, dismiss: dismissRankUp } = useRankUpLater(() => completion.value?.result.xp ?? null)
/** Practise again is reading the set again. */
const restarting = ref(false)
let restartVersion = 0
onBeforeUnmount(() => {
  restartVersion += 1
})

/** "Flashcards · 6 cards · HSK 1 · +10 XP"; a traced set counts strokes, which only the set itself knows. */
const meta = computed(() => {
  const set = selected.value
  if (!set) return ''
  const strokes = detail.data.value?.id === set.id ? detail.data.value.strokeCount : null
  const units = props.type === 'TRACING' ? strokes : set.itemCount
  const parts = [t(`practiceType.${props.type}`)]
  if (units !== null) parts.push(t(PRACTICE_ACTIVITIES[props.type].unitKey, { count: units }, units))
  if (set.hskLevel) parts.push(t('hsk.number', { level: set.hskLevel }))
  parts.push(t('practice.xpTag', { xp: set.xpReward }))
  return parts.join(' · ')
})

watch(selectedId, () => {
  restartVersion += 1
  restarting.value = false
  completion.value = null
})

/** A player sends its result with what only it saw: a recap (Matching, Tracing) or a tally (answered items). */
async function onFinished(result: PracticeResult, recap = '', tally: RunTally | null = null): Promise<void> {
  // Keep the finished set on screen: the reloaded list marks it done, which would move the default choice on.
  const finishedId = selected.value!.id
  if (parseRecordId(route.query.set) !== finishedId) {
    const failure = await router.replace({ query: { ...route.query, set: finishedId } })
    if (failure) return
  }
  if (selectedId.value !== finishedId) return
  completion.value = { result, recap, tally }
  void sets.reload()
}

/**
 * Practise again keeps the result on screen, its button busy, until the set is read again; the new run then starts in
 * a new player on that copy, so nothing of the last run carries over.
 */
async function practiseAgain(): Promise<void> {
  if (restarting.value) return
  const setId = selected.value!.id
  const version = ++restartVersion
  dismissRankUp()
  restarting.value = true
  await detail.reload()
  if (version !== restartVersion) return
  restarting.value = false
  // A failed read shows its panel (Try again comes back here); another set opened meanwhile has its own run.
  if (selectedId.value !== setId || detail.error.value) return
  completion.value = null
}

/** Try again after a failed read: a failed Practise again tries that again, so its run still starts on a fresh copy. */
function retryDetail(): void {
  if (completion.value) void practiseAgain()
  else void detail.reload()
}
</script>
