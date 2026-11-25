<template>
  <div ref="root" class="plan-road">
    <!-- The road is a drawing; everything it says is in the list on top of it. -->
    <svg class="plan-road-svg" aria-hidden="true" focusable="false">
      <path v-for="segment in segments" :key="`bed-${segment.key}`" class="plan-road-bed" :d="segment.d" />
      <path
        v-for="segment in segments"
        :key="`line-${segment.key}`"
        class="plan-road-line"
        :class="`is-${segment.state}`"
        :d="segment.d"
      />
    </svg>

    <ol class="plan-road-list" :aria-label="t('plan.path.listLabel')" data-testid="plan-path">
      <li class="plan-road-here">
        <span class="plan-road-mark is-you" data-road="you" aria-hidden="true">{{ t('plan.result.you') }}</span>
        <div class="plan-road-copy">
          <p class="plan-road-title">{{ hereTitle }}</p>
          <p v-if="hereMeta" class="plan-road-meta">{{ hereMeta }}</p>
        </div>
      </li>

      <li v-if="current" class="plan-road-learn" :data-testid="`plan-step-${current.position}`">
        <span class="plan-road-mark is-learn" data-road="learn" aria-hidden="true">
          <IconGlyph name="learn" :size="22" />
        </span>
        <div class="plan-road-copy">
          <p class="plan-road-title">
            <span>{{ learnTitle(current) }}</span>
            <span v-if="current.titleZh" class="sr-only">
              · <span lang="zh-CN">{{ current.titleZh }}</span></span
            >
          </p>
          <p class="plan-road-meta">{{ learnMeta(current) }}</p>
          <p v-if="!live" class="plan-road-why">
            <strong>{{ t('plan.path.why') }}</strong> {{ why(current) }}
          </p>
          <div v-else class="btn-group is-sm plan-road-actions">
            <RouterLink
              v-if="current.nextLesson"
              class="btn btn-primary btn-sm"
              :to="{ name: 'student-learn-lesson', params: { lessonId: current.nextLesson.id } }"
            >
              {{ t(current.status === 'IN_PROGRESS' ? 'plan.common.continue' : 'plan.path.start') }}
            </RouterLink>
            <StatusPill :label="statusLabel(current)" tone="info" />
          </div>
        </div>
      </li>

      <!-- A class the learner already has runs alongside Learn. -->
      <li
        v-for="step in joinedClasses"
        :key="`class-${step.refId}`"
        class="plan-road-joined"
        :data-testid="`plan-step-${step.position}`"
      >
        <p class="page-eyebrow">{{ t('plan.path.yourClass') }}</p>
        <p class="plan-road-joined-title">
          <span :id="`${baseId}-joined-${step.refId}`"
            ><span class="sr-only">{{ t('plan.path.classPrefix') }} </span>{{ step.title }}</span
          >
          <span class="tag is-sm">{{ step.percent }}%</span>
        </p>
        <p v-if="step.nextLesson" class="plan-road-meta">
          {{ t('plan.path.classNext', { title: step.nextLesson.title }) }}
        </p>
        <RouterLink
          v-if="live"
          class="btn btn-secondary btn-sm"
          :aria-describedby="`${baseId}-joined-${step.refId}`"
          :to="{ name: 'student-class', params: { classId: step.refId } }"
        >
          {{ t('plan.path.openClass') }}
        </RouterLink>
      </li>

      <!-- The other steps stay in the list for screen readers; the road sums them up. -->
      <li
        v-for="step in quietSteps"
        :key="`quiet-${step.position}`"
        class="sr-only"
        :data-testid="`plan-step-${step.position}`"
      >
        <template v-if="step.kind === 'LEARN_UNIT'">
          {{ learnTitle(step)
          }}<template v-if="step.titleZh">
            · <span lang="zh-CN">{{ step.titleZh }}</span></template
          >. {{ learnMeta(step) }}. {{ statusLabel(step) }}. {{ t('plan.path.why') }} {{ why(step) }}
        </template>
        <template v-else>{{ t('plan.path.classPrefix') }} {{ step.title }}. {{ statusLabel(step) }}.</template>
      </li>

      <li v-if="waypoint" class="plan-road-fork">
        <span class="plan-road-mark is-fork" data-road="fork" aria-hidden="true">
          <IconGlyph name="route" :size="22" />
        </span>
        <div class="plan-road-copy">
          <p class="plan-road-title">{{ waypoint.title }}</p>
          <p class="plan-road-meta">{{ waypoint.meta }}</p>
        </div>
      </li>

      <li
        v-for="(branch, index) in branches"
        :key="`branch-${branch.step.refId}`"
        class="plan-road-branch"
        :class="[branchSide(index), { 'is-picked': branch.step.refId === picked }]"
        :data-testid="`plan-step-${branch.step.position}`"
      >
        <span class="plan-road-mark is-class" :data-road="`branch-${index}`" aria-hidden="true">
          <IconGlyph name="classes" :size="22" />
        </span>
        <div class="plan-road-copy">
          <p :id="`${baseId}-branch-${branch.step.refId}`" class="plan-road-class">
            <span class="sr-only">{{ t('plan.path.classPrefix') }} </span>{{ branch.step.title }}
          </p>
          <p class="plan-road-meta">{{ branch.meta }}</p>
          <p class="sr-only">{{ t('plan.path.why') }} {{ why(branch.step) }}</p>
          <div v-if="live || canPick" class="btn-group is-sm plan-road-actions">
            <RouterLink
              v-if="live"
              class="btn btn-secondary btn-sm"
              :aria-describedby="`${baseId}-branch-${branch.step.refId}`"
              :to="{ name: 'student-class', params: { classId: branch.step.refId } }"
            >
              {{ t('plan.path.viewClass') }}
            </RouterLink>
            <button
              v-if="canPick"
              class="btn btn-sm"
              :class="branch.step.refId === picked ? 'btn-primary' : 'btn-secondary'"
              type="button"
              :aria-pressed="branch.step.refId === picked"
              :aria-describedby="`${baseId}-branch-${branch.step.refId}`"
              :data-testid="`pick-class-${branch.step.refId}`"
              @click="emit('pick', branch.step.refId)"
            >
              {{ pickLabel(branch.step) }}
            </button>
          </div>
        </div>
      </li>

      <li class="plan-road-goal">
        <span class="plan-road-mark is-goal" data-road="goal" aria-hidden="true">
          <IconGlyph name="flag" :size="22" />
        </span>
        <p class="plan-road-title">{{ t('plan.path.goalNode', { goal: title }) }}</p>
        <p class="plan-road-meta">{{ goalMeta }}</p>
      </li>
    </ol>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LearningPlan, PlanStep } from '@/types/plan'
import { formatList, formatWeekdayDate } from '@/utils/format'

import { goalTitle, unitsWord } from './planFormat'
import { LEVEL_KEYS } from './planLabels'
import { buildRoadSegments, type RoadPoint, type RoadSegment } from './planRoad'
import { bestAfterUnit, optionalClassesInOrder, optionalClassSteps } from './usePlanPick'

/**
 * The plan as a road map, drawn from its real steps: You, the Learn unit to do now (a class the learner has runs
 * alongside it), a fork to the optional classes ("Pick this first"), and the goal. The road is an SVG over the list,
 * measured from where the marks really are, so it follows wrapped text and the window's width.
 */
const props = defineProps<{ plan: LearningPlan; live?: boolean; picked: number | null }>()
const emit = defineEmits<{ pick: [classId: number] }>()

const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages, planPathMessages] })
const baseId = useId()

const title = computed(() => goalTitle(t, props.plan.goal.title, props.plan.goal.reason))

const goalMeta = computed(() => {
  const { milestone, goal, level } = props.plan
  if (milestone) return t('plan.path.goalNodeMilestone', { date: formatWeekdayDate(milestone.date, locale.value) })
  const target = t(LEVEL_KEYS[level.target])
  return goal.deadline
    ? t('plan.path.goalNodeDate', { date: formatWeekdayDate(goal.deadline, locale.value), level: target })
    : t('plan.path.goalNodeNoDate', { level: target })
})

const activeLearnSteps = computed(() =>
  props.plan.steps.filter(
    (step) => step.kind === 'LEARN_UNIT' && step.status !== 'DONE' && step.status !== 'UNAVAILABLE',
  ),
)
const current = computed(() => activeLearnSteps.value[0] ?? null)

const joinedClasses = computed(() => props.plan.steps.filter((step) => step.joined))
const optionalSteps = computed(() => optionalClassesInOrder(props.plan, props.picked))

function branchMeta(step: PlanStep): string {
  const unit = bestAfterUnit(props.plan, step)
  return unit === null ? t('plan.path.classFromWeek', { week: step.weekFrom }) : t('plan.path.classBestAfter', { unit })
}

const branches = computed(() => optionalClassSteps(props.plan).map((step) => ({ step, meta: branchMeta(step) })))

const canPick = computed(
  () =>
    branches.value.length > 1 &&
    (props.live || props.picked !== null || (props.plan.aiAssisted && props.plan.milestone === null)),
)

const quietSteps = computed(() =>
  props.plan.steps.filter((step) =>
    step.kind === 'LEARN_UNIT' ? step !== current.value : !step.joined && step.status === 'UNAVAILABLE',
  ),
)

const remainingUnits = computed(() =>
  activeLearnSteps.value.slice(1).flatMap((step) => (step.unitNumber === null ? [] : [step.unitNumber])),
)

function forkMeta(units: readonly number[]): string {
  if (props.picked === null) {
    if (!units.length) return t('plan.path.forkNext')
    return t('plan.path.forkUnits', { units: unitsWord(t, units, locale.value) }, units.length)
  }
  const [first, ...rest] = optionalSteps.value
  if (!props.live) return t('plan.path.forkPickedShort', { first: first?.title })
  return t('plan.path.forkPicked', {
    first: first?.title,
    second: formatList(
      rest.map((step) => step.title),
      locale.value,
    ),
  })
}

const waypoint = computed<{ title: string; meta: string } | null>(() => {
  const units = remainingUnits.value
  if (branches.value.length > 1) {
    const fork = canPick.value ? 'fork' : 'then'
    return {
      title: t(`plan.reason.${props.plan.goal.reason}_${props.plan.milestone ? 'after' : fork}`),
      meta: forkMeta(units),
    }
  }
  if (!units.length) return null
  return {
    title: t('plan.path.thenUnits', { units: unitsWord(t, units, locale.value) }),
    meta: branches.value.length ? t('plan.path.thenClass') : t('plan.path.thenGoal'),
  }
})

const hereTitle = computed(() =>
  props.live ? t('plan.path.youAreHereWeek', { week: props.plan.weeks.current }) : t('plan.path.youAreHere'),
)

const hereMeta = computed(() => {
  if (!props.live) return t(LEVEL_KEYS[props.plan.level.placed])
  const { lessons, practiceSets } = props.plan.kept
  if (lessons + practiceSets === 0) return ''
  return t('plan.path.soFar', {
    lessons: t('plan.common.lessons', { count: lessons }, lessons),
    sets: t('plan.common.practiceSets', { count: practiceSets }, practiceSets),
  })
})

function learnTitle(step: PlanStep): string {
  if (step.unitNumber === null) return step.title
  return t('plan.path.learnUnit', { number: step.unitNumber, title: step.title })
}

function weeksLabel(step: PlanStep): string {
  return step.weekFrom === step.weekTo
    ? t('plan.path.week', { week: step.weekFrom })
    : t('plan.path.weeks', { from: step.weekFrom, to: step.weekTo })
}

function learnMeta(step: PlanStep): string {
  if (props.live && step === current.value && step.nextLesson) {
    const task = props.plan.thisWeek.find((item) => item.kind === 'LEARN_NEXT')
    const checkpoint = step.nextLesson.kind === 'CHECKPOINT'
    return task?.lessonId === step.nextLesson.id
      ? t(checkpoint ? 'plan.path.nextCheckpointMinutes' : 'plan.path.nextLessonMinutes', {
          title: step.nextLesson.title,
          minutes: task.minutes,
        })
      : t(checkpoint ? 'plan.path.nextCheckpoint' : 'plan.path.nextLesson', { title: step.nextLesson.title })
  }
  const lessons = step.quickReview
    ? t('plan.path.quickReview', { count: step.lessonCount }, step.lessonCount)
    : t('plan.common.lessons', { count: step.lessonCount }, step.lessonCount)
  return `${lessons} · ${weeksLabel(step)}`
}

function statusLabel(step: PlanStep): string {
  if (step.status === 'IN_MY_CLASSES' || step.status === 'OPTIONAL') return ''
  return t(`plan.stepStatus.${step.status}`, { done: step.completedCount, total: step.lessonCount })
}

function why(step: PlanStep): string {
  if (step.reason) return step.reason
  if (step.goals.length) {
    return t('plan.path.whyGoals', {
      goals: formatList(
        step.goals.map((goal) => t(`plan.goal.${goal}`)),
        locale.value,
      ),
    })
  }
  if (step.kind === 'CLASS') return t('plan.path.whyClass')
  return step.quickReview ? t('plan.path.whyReview') : t('plan.path.whyPath')
}

function pickLabel(step: PlanStep): string {
  if (step.refId !== props.picked) return t('plan.path.pickFirst')
  return props.live ? t('plan.path.pickedFirst') : t('plan.path.picked')
}

function branchSide(index: number): string {
  if (branches.value.length === 1 || index === 2) return 'is-center'
  return index === 0 ? 'is-left' : 'is-right'
}

// Road measurement

const root = ref<HTMLElement | null>(null)
const segments = ref<RoadSegment[]>([])
let observer: ResizeObserver | null = null

function draw(): void {
  const element = root.value
  if (!element) return
  const box = element.getBoundingClientRect()
  const point = (name: string): RoadPoint | null => {
    const mark = element.querySelector(`[data-road="${name}"]`)
    if (!mark) return null
    const rect = mark.getBoundingClientRect()
    return { x: rect.left - box.left + rect.width / 2, y: rect.top - box.top + rect.height / 2 }
  }

  const you = point('you')
  const goal = point('goal')
  if (!you || !goal) return

  const learnWordsBottom = element.querySelector('.plan-road-learn')?.getBoundingClientRect().bottom ?? box.top
  const measuredBranches = branches.value.flatMap((branch, index) => {
    const mark = point(`branch-${index}`)
    return mark ? [{ point: mark, picked: branch.step.refId === props.picked }] : []
  })

  segments.value = buildRoadSegments({
    you,
    goal,
    learn: point('learn'),
    learnWordsEnd: learnWordsBottom - box.top,
    fork: point('fork'),
    branches: measuredBranches,
  })
}

watch(() => [props.plan, props.picked, props.live, locale.value], draw, { flush: 'post' })

onMounted(() => {
  draw()
  if (typeof ResizeObserver !== 'undefined' && root.value) {
    observer = new ResizeObserver(draw)
    observer.observe(root.value)
  }
  void document.fonts?.ready.then(draw)
})

onBeforeUnmount(() => observer?.disconnect())
</script>
