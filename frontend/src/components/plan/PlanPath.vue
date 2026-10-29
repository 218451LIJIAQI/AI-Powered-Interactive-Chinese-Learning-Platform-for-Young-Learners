<template>
  <ol class="plan-path" :aria-label="t('plan.path.listLabel')" data-testid="plan-path">
    <template v-for="(entry, index) in entries" :key="entry.key">
      <li v-if="entry.kind === 'here'" class="plan-node is-here">
        <span class="plan-node-mark is-you" aria-hidden="true">{{ t('plan.result.you') }}</span>
        <div class="plan-node-body">
          <p class="plan-node-title">{{ hereTitle }}</p>
          <p class="plan-node-meta">{{ hereMeta }}</p>
        </div>
      </li>

      <li
        v-else-if="entry.kind === 'step'"
        class="plan-node"
        :class="[`is-${entry.step.status.toLowerCase().replace(/_/g, '-')}`, { 'is-dashed': dashedFrom <= index }]"
        :data-testid="`plan-step-${entry.step.position}`"
      >
        <!-- A finished unit is stamped with its first character; a class shows its teacher. -->
        <span
          v-if="entry.step.status === 'DONE' && entry.step.kind !== 'CLASS'"
          class="plan-node-mark seal"
          lang="zh-CN"
          aria-hidden="true"
        >
          <template v-if="entry.step.titleZh">{{ [...entry.step.titleZh][0] }}</template>
          <IconGlyph v-else name="check" :size="20" />
        </span>
        <span v-else class="plan-node-mark" aria-hidden="true">
          <PortraitAvatar
            v-if="entry.step.kind === 'CLASS'"
            :size="40"
            :name="entry.step.teacherName ?? entry.step.title"
          />
          <IconGlyph v-else :name="entry.step.iconKey || 'learn'" :size="20" />
        </span>
        <div class="plan-node-body">
          <div class="plan-node-head">
            <p class="plan-node-title">
              <span>{{ stepTitle(entry.step) }}</span>
              <template v-if="entry.step.titleZh">
                · <span class="cjk-text" lang="zh-CN">{{ entry.step.titleZh }}</span>
              </template>
            </p>
            <StatusPill
              v-if="showStatus(entry.step)"
              :label="statusLabel(entry.step)"
              :tone="STATUS_TONES[entry.step.status]"
              :icon="entry.step.status === 'DONE' ? 'check' : ''"
            />
            <span v-if="entry.step.status === 'IN_MY_CLASSES'" class="plan-node-percent"
              >{{ entry.step.percent }}%</span
            >
            <span v-if="entry.step.hskLevel && entry.step.hskLevel > 1" class="tag">{{
              t('plan.common.hsk', { level: entry.step.hskLevel })
            }}</span>
          </div>
          <p v-if="meta(entry.step)" class="plan-node-meta">{{ meta(entry.step) }}</p>
          <p v-if="live && entry.step.status === 'IN_PROGRESS' && entry.step.nextLesson" class="plan-node-meta">
            {{ t('plan.path.nextLesson', { title: entry.step.nextLesson.title }) }}
          </p>
          <p class="plan-node-why">
            <strong>{{ t('plan.path.why') }}</strong> {{ why(entry.step) }}
          </p>
        </div>
        <div v-if="live && action(entry.step)" class="plan-node-action">
          <RouterLink
            class="btn btn-sm"
            :class="action(entry.step)!.primary ? 'btn-primary' : 'btn-secondary'"
            :to="action(entry.step)!.to"
          >
            {{ action(entry.step)!.label }}
          </RouterLink>
        </div>
      </li>

      <li v-else-if="entry.kind === 'milestone' && plan.milestone" class="plan-node is-milestone">
        <span class="plan-node-mark" aria-hidden="true"><IconGlyph name="target" :size="20" /></span>
        <div class="plan-node-body">
          <p class="plan-node-title">{{ t('plan.side.milestoneTitle', { date: longDate(plan.milestone.date) }) }}</p>
          <p v-if="plan.milestone.text" class="plan-node-meta">{{ plan.milestone.text }}</p>
        </div>
      </li>

      <li v-else class="plan-node is-goal">
        <span class="plan-node-mark is-goal" aria-hidden="true"><IconGlyph name="flag" :size="20" /></span>
        <div class="plan-node-body">
          <p class="plan-node-title">{{ t('plan.path.goalNode', { goal: title }) }}</p>
          <p class="plan-node-meta">
            {{
              plan.goal.deadline
                ? t('plan.path.goalNodeDate', { date: longDate(plan.goal.deadline), level: targetLabel })
                : t('plan.path.goalNodeTarget', { level: targetLabel })
            }}
          </p>
        </div>
      </li>
    </template>
  </ol>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan, PlanStep, StepStatus } from '@/types/plan'
import { formatDateTime } from '@/utils/format'

import { goalList, goalTitle, longDate } from './planFormat'
import { LEVEL_HSK_KEYS, LEVEL_KEYS, STEP_STATUS_KEYS } from './planLabels'

/**
 * The plan's path: where the student is, each step (a Learn unit or an optional class) with why it is there and
 * when, the first milestone of an ambitious plan, and the goal at the end. `live` (My learning plan) adds each step's
 * status and its action; the setup shows the path as planned.
 */
const props = withDefaults(defineProps<{ plan: LearningPlan; live?: boolean }>(), { live: false })

const { t } = useLocalMessages({ plan: planMessages })

const STATUS_TONES: Readonly<Record<StepStatus, PillTone>> = {
  DONE: 'success',
  IN_PROGRESS: 'info',
  NEXT: 'info',
  LATER: 'neutral',
  IN_MY_CLASSES: 'success',
  OPTIONAL: 'neutral',
  UNAVAILABLE: 'warning',
}

type Entry =
  | { kind: 'here'; key: string }
  | { kind: 'step'; key: string; step: PlanStep }
  | { kind: 'milestone'; key: string }
  | { kind: 'goal'; key: string }

const title = computed(() => goalTitle(t, props.plan.goal.title, props.plan.goal.reason))
const targetLabel = computed(() => t(LEVEL_HSK_KEYS[props.plan.level.target]))

/** "You are here" sits before the first step not done; the milestone after its last step. */
const entries = computed<Entry[]>(() => {
  const steps = props.plan.steps
  const firstOpen = steps.findIndex((step) => step.status !== 'DONE')
  // Every step done: the student stands at the goal.
  const hereAt = props.live && firstOpen < 0 ? steps.length : props.live ? firstOpen : 0
  const milestoneAfter = props.plan.milestone ? Math.max(...props.plan.milestone.stepPositions, 0) : null
  const list: Entry[] = []
  steps.forEach((step, index) => {
    if (index === hereAt) list.push({ kind: 'here', key: 'here' })
    list.push({ kind: 'step', key: `step-${step.position}`, step })
    if (milestoneAfter !== null && step.position === milestoneAfter) list.push({ kind: 'milestone', key: 'milestone' })
  })
  if (hereAt >= steps.length) list.push({ kind: 'here', key: 'here' })
  list.push({ kind: 'goal', key: 'goal' })
  return list
})

/** The line turns dashed after the step in progress (the part still ahead). */
const dashedFrom = computed(() => {
  if (!props.live) return Number.MAX_SAFE_INTEGER
  const current = entries.value.findIndex(
    (entry) => entry.kind === 'step' && (entry.step.status === 'IN_PROGRESS' || entry.step.status === 'NEXT'),
  )
  return current < 0 ? Number.MAX_SAFE_INTEGER : current + 1
})

const hereTitle = computed(() =>
  props.live ? t('plan.path.youAreHereWeek', { week: props.plan.weeks.current }) : t('plan.path.youAreHere'),
)

const hereMeta = computed(() => {
  if (props.live) {
    const { lessons, practiceSets } = props.plan.kept
    return t('plan.path.soFar', {
      lessons: t('plan.common.lessons', { count: lessons }, lessons),
      sets: t('plan.common.practiceSets', { count: practiceSets }, practiceSets),
    })
  }
  const level = props.plan.level
  if (level.placed === 'NEW_TO_MANDARIN') return t('plan.path.hereFresh')
  return level.headStart
    ? t('plan.path.hereHeadStart', { level: t(LEVEL_KEYS[level.placed]) })
    : t('plan.path.hereChecked', { level: t(LEVEL_KEYS[level.placed]) })
})

function stepTitle(step: PlanStep): string {
  return step.kind === 'CLASS'
    ? t('plan.path.classStep', { title: step.title })
    : t('plan.path.learnUnit', { number: step.unitNumber ?? '', title: step.title })
}

function showStatus(step: PlanStep): boolean {
  if (props.live) return true
  return step.status === 'OPTIONAL' || step.status === 'IN_MY_CLASSES'
}

function statusLabel(step: PlanStep): string {
  return t(STEP_STATUS_KEYS[step.status], { done: step.completedCount, total: step.lessonCount })
}

function weeksLabel(step: PlanStep): string {
  return step.weekFrom === step.weekTo
    ? t('plan.path.week', { week: step.weekFrom })
    : t('plan.path.weeks', { from: step.weekFrom, to: step.weekTo })
}

function meta(step: PlanStep): string {
  if (step.kind === 'CLASS') {
    if (!step.teacherName) return ''
    return step.nextSessionAt
      ? t('plan.path.liveSession', { teacher: step.teacherName, when: formatDateTime(step.nextSessionAt) })
      : t('plan.path.teacher', { teacher: step.teacherName })
  }
  if (props.live && (step.status === 'DONE' || step.status === 'IN_PROGRESS')) return ''
  const lessons = step.quickReview
    ? t('plan.path.quickReview', { count: step.lessonCount })
    : t('plan.path.lessons', { count: step.lessonCount }, step.lessonCount)
  return `${weeksLabel(step)} · ${lessons}`
}

function why(step: PlanStep): string {
  if (step.reason) return step.reason
  if (step.goals.length) {
    const goals = t('plan.path.whyGoals', { goals: goalList(t, step.goals) })
    return step.kind === 'CLASS' && step.joined ? `${t('plan.path.whyJoined')} ${goals}` : goals
  }
  if (step.kind === 'CLASS') return step.joined ? t('plan.path.whyJoined') : t('plan.path.whyClass')
  return step.quickReview ? t('plan.path.whyReview') : t('plan.path.whyPath')
}

function action(step: PlanStep): { label: string; to: RouteLocationRaw; primary: boolean } | null {
  if (step.kind === 'CLASS') {
    if (step.status === 'UNAVAILABLE') return null
    return {
      label: step.joined ? t('plan.path.openClass') : t('plan.path.viewClass'),
      to: { name: 'student-class', params: { classId: step.refId } },
      primary: false,
    }
  }
  if (step.status !== 'IN_PROGRESS' && step.status !== 'NEXT') return null
  if (!step.nextLesson) return null
  return {
    label: step.status === 'IN_PROGRESS' ? t('plan.path.continue') : t('plan.path.start'),
    to: { name: 'student-learn-lesson', params: { lessonId: step.nextLesson.id } },
    primary: true,
  }
}
</script>
