<template>
  <div ref="root" class="plan-road" :class="{ 'is-live': live }">
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
        <span class="plan-road-mark is-you" data-road="you">{{ t('plan.result.you') }}</span>
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
          <div v-if="live" class="plan-road-actions">
            <RouterLink
              v-if="current.nextLesson"
              class="btn btn-primary btn-sm"
              :to="{ name: 'student-learn-lesson', params: { lessonId: current.nextLesson.id } }"
            >
              {{ current.status === 'IN_PROGRESS' ? t('plan.path.continue') : t('plan.path.start') }}
            </RouterLink>
            <StatusPill :label="statusLabel(current)" tone="info" />
          </div>
        </div>
      </li>

      <!-- A class the student already has runs alongside Learn. -->
      <li
        v-for="step in joinedClasses"
        :key="`class-${step.refId}`"
        class="plan-road-joined"
        :data-testid="`plan-step-${step.position}`"
      >
        <p class="page-eyebrow">{{ t('plan.path.alongside') }}</p>
        <p class="plan-road-joined-title">
          <span
            ><span class="sr-only">{{ t('plan.path.classPrefix') }} </span>{{ step.title }}</span
          >
          <span class="tag is-sm">{{ step.percent }}%</span>
        </p>
        <p v-if="classMeta(step)" class="plan-road-meta">{{ classMeta(step) }}</p>
        <RouterLink
          v-if="live"
          class="btn btn-secondary btn-sm"
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
          <p class="plan-road-class">
            <span class="sr-only">{{ t('plan.path.classPrefix') }} </span>{{ branch.step.title }}
          </p>
          <p class="plan-road-meta">{{ branch.meta }}</p>
          <p class="sr-only">{{ t('plan.path.why') }} {{ why(branch.step) }}</p>
          <div class="btn-group is-sm plan-road-actions">
            <RouterLink
              v-if="live"
              class="btn btn-secondary btn-sm"
              :to="{ name: 'student-class', params: { classId: branch.step.refId } }"
            >
              {{ t('plan.path.viewClass') }}
            </RouterLink>
            <button
              v-if="branches.length > 1"
              class="btn btn-sm"
              :class="branch.step.refId === picked ? 'btn-primary' : 'btn-secondary'"
              type="button"
              :aria-pressed="branch.step.refId === picked"
              :data-testid="`pick-class-${branch.step.refId}`"
              @click="emit('pick', branch.step.refId)"
            >
              {{ branch.step.refId === picked ? t('plan.path.picked') : t('plan.path.pickFirst') }}
            </button>
          </div>
        </div>
      </li>

      <li class="plan-road-goal">
        <span class="plan-road-mark is-goal" data-road="goal" aria-hidden="true">
          <IconGlyph name="flag" :size="22" />
        </span>
        <p class="plan-road-title">{{ t('plan.path.goalNode', { goal: title }) }}</p>
        <p class="plan-road-meta">
          {{
            plan.goal.deadline
              ? t('plan.path.goalNodeDate', { date: shortDayDate(plan.goal.deadline), level: targetLabel })
              : t('plan.path.goalNodeTarget', { level: targetLabel })
          }}
        </p>
      </li>
    </ol>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan, PlanStep } from '@/types/plan'
import { formatDateTime } from '@/utils/format'

import { goalList, goalTitle, shortDayDate } from './planFormat'
import { LEVEL_HSK_KEYS, LEVEL_KEYS, REASONS, STEP_STATUS_KEYS } from './planLabels'
import { optionalClassSteps } from './usePlanPick'

/**
 * The plan as a road map, drawn from its real steps: You, the Learn unit to do now (a class the student has runs
 * alongside it), a fork to the optional classes ("Pick this first"), and the goal. With one optional class the road has
 * no fork; with none it runs straight to the goal. The road is an SVG over the list, measured from where the marks
 * really are, so it follows wrapped text and the window's width. `live` (My learning plan) adds the actions.
 */
const props = withDefaults(defineProps<{ plan: LearningPlan; live?: boolean; picked?: number | null }>(), {
  live: false,
  picked: null,
})

const emit = defineEmits<{ pick: [classId: number] }>()

const { t } = useLocalMessages({ plan: planMessages })

const title = computed(() => goalTitle(t, props.plan.goal.title, props.plan.goal.reason))
const targetLabel = computed(() => t(LEVEL_HSK_KEYS[props.plan.level.target]))

const learnSteps = computed(() => props.plan.steps.filter((step) => step.kind === 'LEARN_UNIT'))

/** The Learn unit to do now: the first one not done. */
const current = computed(() => learnSteps.value.find((step) => step.status !== 'DONE') ?? null)

const joinedClasses = computed(() =>
  props.plan.steps.filter((step) => step.kind === 'CLASS' && step.joined && step.status !== 'UNAVAILABLE'),
)

/** The picked class first; the road only has room for three branches, and the server plans at most three classes. */
const optionalSteps = computed(() => {
  const steps = optionalClassSteps(props.plan)
  return [...steps].sort((a, b) => Number(b.refId === props.picked) - Number(a.refId === props.picked))
})

/** The Learn unit a class backs up: the last one before it in the plan. */
function anchorUnit(step: PlanStep): number | null {
  const before = learnSteps.value.filter((learn) => learn.position < step.position)
  return before.at(-1)?.unitNumber ?? null
}

/** Left and right stay where the plan put them; a pick only changes the highlight and the order in the words. */
const branches = computed(() =>
  optionalClassSteps(props.plan).map((step) => {
    const unit = anchorUnit(step)
    const order = props.picked === null ? null : optionalSteps.value.indexOf(step)
    let meta: string
    if (unit === null) meta = t('plan.path.classFromWeek', { week: step.weekFrom })
    else if (order === 0) meta = t('plan.path.classAfterFirst', { unit })
    else if (order === 1) meta = t('plan.path.classAfterSecond', { unit })
    else meta = t('plan.path.classBestAfter', { unit })
    return { step, meta }
  }),
)

/** Every other step: later Learn units, and classes no longer available. */
const quietSteps = computed(() =>
  props.plan.steps.filter((step) =>
    step.kind === 'LEARN_UNIT' ? step !== current.value : step.status === 'UNAVAILABLE',
  ),
)

/** The units still to do after the current one and before the first class. */
const unitsBefore = computed(() => {
  const rest = learnSteps.value.filter((step) => step.status !== 'DONE' && step !== current.value)
  const firstClass = branches.value[0]?.step
  const before = firstClass ? rest.filter((step) => step.position < firstClass.position) : rest
  return before.map((step) => step.unitNumber).filter((unit): unit is number => unit !== null)
})

function unitsWord(units: readonly number[]): string {
  if (units.length === 1) return t('plan.path.unitOne', { unit: units[0] })
  return t('plan.path.unitRange', { from: units[0], to: units[units.length - 1] })
}

/** The fork, or a plain waypoint when there is no choice to make but Learn units are still ahead. */
const waypoint = computed<{ title: string; meta: string } | null>(() => {
  const units = unitsBefore.value
  if (branches.value.length > 1) {
    let meta: string
    if (props.picked !== null) {
      meta = t('plan.path.forkPicked', { first: optionalSteps.value[0]?.title, second: optionalSteps.value[1]?.title })
    } else {
      meta = units.length
        ? t('plan.path.forkUnits', { units: unitsWord(units) }, units.length)
        : t('plan.path.forkNext')
    }
    return { title: t(REASONS[props.plan.goal.reason].forkTitle), meta }
  }
  if (!units.length) return null
  return {
    title: t('plan.path.thenUnits', { units: unitsWord(units) }),
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
  return t('plan.path.learnUnit', { number: step.unitNumber ?? '', title: step.title })
}

function weeksLabel(step: PlanStep): string {
  return step.weekFrom === step.weekTo
    ? t('plan.path.week', { week: step.weekFrom })
    : t('plan.path.weeks', { from: step.weekFrom, to: step.weekTo })
}

function learnMeta(step: PlanStep): string {
  if (props.live && step === current.value && step.nextLesson) {
    const minutes = props.plan.thisWeek.find((task) => task.lessonId === step.nextLesson?.id)?.minutes
    return minutes
      ? t('plan.path.nextLessonMinutes', { title: step.nextLesson.title, minutes })
      : t('plan.path.nextLesson', { title: step.nextLesson.title })
  }
  const lessons = step.quickReview
    ? t('plan.path.quickReview', { count: step.lessonCount })
    : t('plan.path.lessons', { count: step.lessonCount }, step.lessonCount)
  return `${lessons} · ${weeksLabel(step)}`
}

function classMeta(step: PlanStep): string {
  if (!step.teacherName) return ''
  return step.nextSessionAt
    ? t('plan.path.liveSession', { teacher: step.teacherName, when: formatDateTime(step.nextSessionAt) })
    : step.teacherName
}

function statusLabel(step: PlanStep): string {
  return t(STEP_STATUS_KEYS[step.status], { done: step.completedCount, total: step.lessonCount })
}

function why(step: PlanStep): string {
  if (step.reason) return step.reason
  if (step.goals.length) return t('plan.path.whyGoals', { goals: goalList(t, step.goals) })
  if (step.kind === 'CLASS') return t('plan.path.whyClass')
  return step.quickReview ? t('plan.path.whyReview') : t('plan.path.whyPath')
}

function branchSide(index: number): string {
  if (branches.value.length === 1 || index === 2) return 'is-center'
  return index === 0 ? 'is-left' : 'is-right'
}

// ----- the road -----

type RoadState = 'done' | 'next' | 'later'

interface Point {
  x: number
  y: number
}

const root = ref<HTMLElement | null>(null)
const segments = ref<{ key: string; d: string; state: RoadState }[]>([])
let observer: ResizeObserver | null = null

/** Down out of a mark, across, and into the next one from above. */
function trunk(from: Point, to: Point): string {
  const height = to.y - from.y
  return `M ${from.x} ${from.y} C ${from.x} ${from.y + height * 0.65} ${to.x} ${from.y + height * 0.4} ${to.x} ${to.y}`
}

/** Down past the words beside a mark, then across and into the next one from above. */
function trunkBelow(from: Point, to: Point, wordsEnd: number): string {
  const bend = Math.max(from.y, Math.min(wordsEnd, to.y - 24))
  const height = to.y - bend
  return `M ${from.x} ${from.y} L ${from.x} ${bend} C ${from.x} ${bend + height * 0.7} ${to.x} ${bend + height * 0.3} ${to.x} ${to.y}`
}

/** Out of the fork sideways, then down into the class. */
function intoBranch(fork: Point, branch: Point): string {
  return `M ${fork.x} ${fork.y} C ${fork.x + (branch.x - fork.x) * 0.6} ${fork.y} ${branch.x} ${fork.y} ${branch.x} ${branch.y}`
}

/** Straight down from the class, then round into the goal. */
function intoGoal(branch: Point, goal: Point): string {
  const bend = Math.max(goal.y - 56, branch.y)
  return `M ${branch.x} ${branch.y} L ${branch.x} ${bend} C ${branch.x} ${goal.y - 8} ${branch.x + (goal.x - branch.x) * 0.5} ${goal.y} ${goal.x} ${goal.y}`
}

function draw(): void {
  const element = root.value
  if (!element) return
  const box = element.getBoundingClientRect()
  const point = (name: string): Point | null => {
    const mark = element.querySelector(`[data-road="${name}"]`)
    if (!mark) return null
    const rect = mark.getBoundingClientRect()
    return { x: rect.left - box.left + rect.width / 2, y: rect.top - box.top + rect.height / 2 }
  }

  const list: { key: string; d: string; state: RoadState }[] = []
  const you = point('you')
  const goal = point('goal')
  if (!you || !goal) return
  const learn = point('learn')
  const fork = point('fork')

  // You, the current unit, the fork: travelled as far as the student has come.
  let last = you
  if (learn) {
    list.push({ key: 'you-learn', d: trunk(you, learn), state: 'done' })
    last = learn
  }
  if (fork) {
    const words = element.querySelector('.plan-road-learn')?.getBoundingClientRect().bottom ?? box.top
    list.push({
      key: 'learn-fork',
      d: learn ? trunkBelow(learn, fork, words - box.top) : trunk(last, fork),
      state: learn ? 'next' : 'done',
    })
    last = fork
  }

  const marks = branches.value.map((_, index) => point(`branch-${index}`))
  branches.value.forEach((branch, index) => {
    const mark = marks[index]
    if (!mark) return
    const state: RoadState = branch.step.refId === props.picked ? 'next' : 'later'
    const straight = Math.abs(mark.x - last.x) < 1
    list.push({
      key: `fork-${index}`,
      d: straight ? trunk(last, mark) : intoBranch(last, mark),
      state,
    })
    const down = Math.abs(mark.x - goal.x) < 1
    list.push({ key: `goal-${index}`, d: down ? trunk(mark, goal) : intoGoal(mark, goal), state })
  })
  if (!branches.value.length) list.push({ key: 'to-goal', d: trunk(last, goal), state: learn ? 'next' : 'done' })

  // The chosen way is drawn last, so it stays on top where the roads share a stretch.
  segments.value = list.sort((a, b) => Number(a.state === 'next') - Number(b.state === 'next'))
}

function redraw(): void {
  void nextTick(draw)
}

watch(() => [props.plan, props.picked, props.live], redraw, { flush: 'post' })

onMounted(() => {
  draw()
  if (typeof ResizeObserver !== 'undefined' && root.value) {
    observer = new ResizeObserver(draw)
    observer.observe(root.value)
  }
  // The marks move once the web font is in.
  void document.fonts?.ready.then(draw)
})

onBeforeUnmount(() => observer?.disconnect())
</script>
