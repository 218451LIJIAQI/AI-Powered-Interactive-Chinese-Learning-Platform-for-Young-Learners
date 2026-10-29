<template>
  <div class="practice-hub">
    <PageHeaderBlock class="practice-hub-head" :title="t('practice.pageTitle')" :description="t('practice.hubLede')" />

    <div class="practice-hub-main">
      <section
        v-if="mistakesWaiting > 0"
        class="practice-call"
        aria-labelledby="practice-call-title"
        data-testid="mistakes-call"
      >
        <span class="seal is-outline practice-call-seal" lang="zh-CN" aria-hidden="true">改</span>
        <div class="practice-call-copy">
          <h2 id="practice-call-title" class="practice-call-title">
            <strong class="stat-figure">{{ mistakesWaiting }}</strong>
            {{ t('practice.mistakesNoun', {}, mistakesWaiting) }}
          </h2>
          <p class="practice-call-text">{{ t('practice.mistakesSubtitle') }}</p>
        </div>
        <div class="practice-call-actions">
          <RouterLink class="btn btn-accent btn-lg" :to="{ name: 'student-practice-review' }" data-testid="fix-now">
            <IconGlyph name="retry" :size="18" />{{ t('practice.startReview') }}
          </RouterLink>
          <RouterLink
            class="btn btn-link"
            :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
            data-testid="see-mistakes"
          >
            {{ t('practice.seeTheList') }}
          </RouterLink>
        </div>
      </section>

      <nav class="practice-room" :aria-label="t('practice.tabsLabel')">
        <div class="practice-room-head">
          <h2 class="section-title">{{ t('practice.roomTitle') }}</h2>
          <p v-if="totals" class="practice-room-total">
            {{ t('practice.roomDone', { done: totals.done, total: totals.total }) }}
          </p>
        </div>

        <ul class="practice-room-list">
          <li
            v-for="row in rows"
            :key="row.info.type"
            class="practice-room-row"
            :class="[`practice-pigment-${row.info.pigment}`, { 'is-open': open === row.info.type }]"
          >
            <div class="practice-room-item">
              <PracticeGlyph :type="row.info.type" />
              <div class="practice-room-copy">
                <RouterLink
                  class="practice-room-name"
                  :to="{ name: 'student-practice', params: { type: row.info.slug } }"
                  :data-testid="`type-${row.info.slug}`"
                  >{{ t(row.info.labelKey) }}</RouterLink
                >
                <p class="practice-room-trains">{{ t(row.info.trainsKey) }}</p>
                <p class="practice-room-meta">{{ row.meta }}</p>
              </div>

              <div v-if="row.sets?.length" class="practice-room-progress">
                <span class="practice-room-squares" aria-hidden="true">
                  <span
                    v-for="set in row.sets"
                    :key="set.id"
                    class="practice-room-square"
                    :class="{ 'is-done': set.done, 'is-next': set.id === row.next?.id }"
                  />
                </span>
                <span class="practice-room-count">
                  <span
                    v-if="row.done === row.sets.length"
                    class="seal practice-room-seal"
                    lang="zh-CN"
                    aria-hidden="true"
                    >完</span
                  >
                  {{
                    row.done === row.sets.length
                      ? t('practice.allSetsDone', { total: row.sets.length })
                      : t('practice.setsDone', { done: row.done, total: row.sets.length })
                  }}
                </span>
              </div>
              <span v-else-if="catalogue.loading.value" class="practice-room-progress">
                <span class="skeleton skeleton-line is-short" aria-hidden="true" />
              </span>

              <button
                v-if="row.sets?.length"
                class="btn btn-ghost btn-icon practice-room-toggle"
                type="button"
                :aria-expanded="open === row.info.type"
                :aria-controls="`practice-sets-${row.info.slug}`"
                :aria-label="
                  t('practice.setsOfTypeNamed', { count: row.sets.length, type: t(row.info.labelKey) }, row.sets.length)
                "
                :data-testid="`toggle-${row.info.slug}`"
                @click="toggle(row.info.type)"
              >
                <IconGlyph name="chevronDown" :size="20" />
              </button>
            </div>

            <ul
              v-if="open === row.info.type && row.sets"
              :id="`practice-sets-${row.info.slug}`"
              class="practice-room-sets"
            >
              <li v-for="set in row.sets" :key="set.id">
                <RouterLink
                  class="practice-room-set"
                  :class="{ 'is-done': set.done }"
                  :to="{ name: 'student-practice', params: { type: row.info.slug }, query: { set: set.id } }"
                >
                  <span class="practice-room-set-mark" aria-hidden="true">
                    <IconGlyph v-if="set.done" name="check" :size="14" />
                  </span>
                  <span class="practice-room-set-title">
                    {{ set.title }}<span v-if="set.done" class="sr-only"> ({{ t('practice.done') }})</span>
                  </span>
                  <span v-if="set.id === row.next?.id" class="practice-room-set-next">{{ t('practice.upNext') }}</span>
                  <span class="practice-room-set-meta">{{ setMeta(row.info, set) }}</span>
                </RouterLink>
              </li>
            </ul>
          </li>
        </ul>

        <StatePanel
          v-if="catalogue.error.value"
          tone="error"
          icon="alert"
          :title="t('practice.setsError')"
          :message="catalogue.error.value"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="catalogue.reload">{{ t('practice.retry') }}</button>
          </template>
        </StatePanel>
      </nav>
    </div>

    <PracticeTodayRail v-if="today.data.value" class="practice-hub-rail" :today="today.data.value" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { practiceApi, xpApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useProgressVersion } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeSetSummary, PracticeType } from '@/types/learn'

import PracticeGlyph from './PracticeGlyph.vue'
import PracticeTodayRail from './PracticeTodayRail.vue'
import { PRACTICE_TYPES, type PracticeTypeInfo } from './practiceTypes'

/**
 * The Practice room: mistakes waiting come first (one review fixes them), then the seven activities, each in its own
 * pigment and glyph with what it trains, how long a set takes, its XP and how many of its sets are done; a type's
 * sets open on request. Today's goal and streak sit in a slim rail.
 */
const { t } = useLocalMessages({ practice: practiceMessages })

const today = useResource(() => xpApi.today())
const catalogue = useResource(() => Promise.all(PRACTICE_TYPES.map((info) => practiceApi.sets(info.type))))

const mistakesWaiting = computed(() => today.data.value?.mistakesWaiting ?? 0)
const open = ref<PracticeType | null>(null)

const rows = computed(() =>
  PRACTICE_TYPES.map((info, index) => {
    const sets: readonly PracticeSetSummary[] | null = catalogue.data.value?.[index] ?? null
    const xp = (sets ?? []).map((set) => set.xpReward)
    const low = Math.min(...xp)
    const high = Math.max(...xp)
    const parts = [t('practice.aboutMinutes', { minutes: info.minutes })]
    if (xp.length) {
      parts.push(low === high ? t('practice.xpEach', { xp: low }) : t('practice.xpRange', { min: low, max: high }))
    }
    return {
      info,
      sets,
      done: (sets ?? []).filter((set) => set.done).length,
      next: (sets ?? []).find((set) => !set.done) ?? null,
      meta: parts.join(' · '),
    }
  }),
)

const totals = computed(() => {
  const all = catalogue.data.value?.flat()
  return all?.length ? { done: all.filter((set) => set.done).length, total: all.length } : null
})

function setMeta(info: PracticeTypeInfo, set: PracticeSetSummary): string {
  const parts = [t(info.unitKey, { count: set.itemCount }, set.itemCount)]
  if (set.hskLevel) parts.push(`HSK ${set.hskLevel}`)
  return parts.join(' · ')
}

function toggle(type: PracticeType): void {
  open.value = open.value === type ? null : type
}

// Practice elsewhere (the review, a set) changes today's numbers and the sets done.
const progressVersion = useProgressVersion()
watch(progressVersion, () => {
  void today.reload()
  void catalogue.reload()
})
</script>
