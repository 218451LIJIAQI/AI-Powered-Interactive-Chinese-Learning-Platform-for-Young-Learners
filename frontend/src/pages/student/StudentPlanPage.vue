<template>
  <div class="plan-page page-stack">
    <header class="plan-hero">
      <AppBreadcrumb
        :items="[{ label: t('plan.page.progress'), to: { name: 'student-progress' } }, { label: t('plan.page.title') }]"
      />
      <div class="plan-hero-main">
        <div class="plan-hero-copy">
          <!-- With a plan, the breadcrumb already names the page and the goal is the headline (the h1 stays for screen
               readers); the hidden separator keeps the goal one sentence for them. -->
          <h1 class="plan-hero-title" :class="{ 'sr-only': data }">{{ t('plan.page.title') }}</h1>
          <p v-if="data" class="plan-hero-goal">
            <span class="plan-hero-goal-name">{{ goal }}</span
            ><span class="sr-only">{{ ' · ' }}</span
            ><span class="plan-hero-goal-when">{{ goalWhen }}</span>
          </p>
        </div>
        <div v-if="data" class="plan-hero-actions">
          <RouterLink class="btn btn-link" :to="{ name: 'student-level-check' }" data-testid="retake">
            {{ t('plan.page.retake') }}
          </RouterLink>
          <button class="btn btn-secondary" type="button" data-testid="update-goal" @click="openUpdate">
            <IconGlyph name="edit" :size="16" />{{ t('plan.page.update') }}
          </button>
        </div>
      </div>

      <!-- The weeks from the start to the goal: done, this week, still to come, and the review weeks at the end. -->
      <div v-if="data" class="plan-weekline" data-testid="plan-weekline">
        <div class="plan-weekline-caption">
          <strong>{{ t('plan.page.weekOf', { week: data.weeks.current, total: data.weeks.total }) }}</strong>
          <span v-if="data.goal.deadline" class="plan-weekline-end">
            <IconGlyph name="flag" :size="14" />{{ longDate(data.goal.deadline) }}
          </span>
        </div>
        <ol class="plan-weekline-track" :aria-label="t('plan.page.weeksLabel')">
          <li v-for="week in weekCells" :key="week.number" :class="week.state">
            <span class="sr-only">{{ week.label }}</span>
          </li>
        </ol>
        <ul class="plan-weekline-legend" aria-hidden="true">
          <li><span class="plan-weekline-key is-done" />{{ t('plan.page.weeksDone') }}</li>
          <li><span class="plan-weekline-key is-now" />{{ t('plan.page.thisWeek') }}</li>
          <li v-if="data.weeks.total > data.weeks.content">
            <span class="plan-weekline-key is-review" />{{ t('plan.page.reviewWeeks') }}
          </li>
        </ul>
      </div>
    </header>

    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.page.loadingHint')"
    />
    <StatePanel
      v-else-if="resource.error.value && !data"
      tone="error"
      icon="alert"
      :title="t('plan.page.loadError')"
      :message="resource.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('plan.common.retry') }}</button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="!data"
      tone="empty"
      icon="route"
      :title="t('plan.page.noPlanTitle')"
      :message="t('plan.page.noPlanMessage')"
      spacious
    >
      <template #actions>
        <RouterLink class="btn btn-primary" :to="{ name: 'student-setup' }">{{ t('plan.page.buildPlan') }}</RouterLink>
      </template>
    </StatePanel>

    <template v-else>
      <section class="plan-stats" :aria-label="t('plan.page.statsLabel')">
        <div class="plan-stat">
          <h2 class="plan-stat-label">{{ t('plan.page.mandarinLevel') }}</h2>
          <p class="plan-stat-value">
            <!-- One level when the goal is the level already reached ("Beginner 1", not "Beginner 1 → Beginner 1"). -->
            {{
              data.level.placed === data.level.target
                ? t(LEVEL_KEYS[data.level.placed])
                : t('plan.level.fromTo', {
                    from: t(LEVEL_KEYS[data.level.placed]),
                    to: t(LEVEL_KEYS[data.level.target]),
                  })
            }}
          </p>
          <p class="plan-stat-note">{{ levelNote }}</p>
        </div>
        <div class="plan-stat">
          <h2 class="plan-stat-label">{{ t('plan.page.planProgress') }}</h2>
          <p class="plan-stat-value">{{ data.progress.percent }}%</p>
          <ProgressBar :progress="data.progress.percent" :label="t('plan.page.planProgress')" />
        </div>
        <div class="plan-stat">
          <h2 class="plan-stat-label">{{ t('plan.page.thisWeek') }}</h2>
          <p class="plan-stat-value">
            {{ t('plan.page.daysOf', { done: data.progress.daysThisWeek, total: data.progress.paceDays }) }}
          </p>
          <StatusPill :label="t(STATUS_KEYS[data.progress.status])" :tone="STATUS_TONES[data.progress.status]" />
        </div>
        <div class="plan-stat">
          <h2 class="plan-stat-label">{{ t('plan.page.pace') }}</h2>
          <p class="plan-stat-value">{{ paceText(t, data.pace) }}</p>
          <p class="plan-stat-note">{{ t('plan.page.dailyGoal', { xp: data.pace.dailyGoalXp }) }}</p>
        </div>
      </section>

      <div class="plan-page-layout">
        <section class="plan-page-path" aria-labelledby="plan-path-heading">
          <h2 id="plan-path-heading" class="plan-section-title">{{ t('plan.page.yourPath') }}</h2>
          <PlanPath :plan="data" live />
        </section>

        <aside class="plan-page-rail">
          <section class="panel plan-week" aria-labelledby="plan-week-heading">
            <h2 id="plan-week-heading" class="plan-week-title">
              {{ t('plan.page.thisWeekTitle', { week: data.weeks.current }) }}
            </h2>
            <ul class="plan-tasks" :aria-label="t('plan.page.tasksLabel')" data-testid="plan-tasks">
              <li v-for="task in tasks" :key="task.key" :class="{ 'is-done': task.done }">
                <span class="plan-task-box" aria-hidden="true">
                  <IconGlyph v-if="task.done" name="check" :size="14" />
                </span>
                <span class="plan-task-copy">
                  <RouterLink v-if="task.to && !task.done" :to="task.to" class="plan-task-title">{{
                    task.title
                  }}</RouterLink>
                  <span v-else class="plan-task-title">{{ task.title }}</span>
                  <span class="plan-task-meta">{{ task.meta }}</span>
                </span>
                <span class="sr-only">{{ task.done ? t('plan.page.taskDone') : t('plan.page.taskToDo') }}</span>
              </li>
            </ul>
          </section>
          <PlanOutcomes :plan="data" />
          <PlanCoverage :plan="data" />
          <PlanAiNote :plan="data" />
        </aside>
      </div>
    </template>

    <AppOverlay
      :open="updating"
      variant="drawer"
      :title="t('plan.page.updateTitle')"
      :subtitle="t('plan.page.updateLead')"
      test-id="update-goal-drawer"
      @close="closeUpdate"
    >
      <template v-if="options.data.value">
        <GoalForm
          v-model="draft"
          :options="options.data.value"
          :sections="['reason', 'goals', 'deadline', 'pace', 'focus']"
          variant="chips"
        />
        <p v-if="data" class="plan-form-note">
          <IconGlyph name="info" :size="16" />{{
            t('plan.page.keptNote', {
              lessons: t('plan.common.lessons', { count: data.kept.lessons }, data.kept.lessons),
              sets: t('plan.common.practiceSets', { count: data.kept.practiceSets }, data.kept.practiceSets),
              classes: t('plan.common.classes', { count: data.kept.classes }, data.kept.classes),
              level: t(LEVEL_KEYS[data.level.placed]),
            })
          }}
        </p>
      </template>
      <StatePanel
        v-else-if="options.error.value"
        tone="error"
        icon="alert"
        :title="t('plan.page.updateError')"
        :message="options.error.value"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="options.reload">{{ t('plan.common.retry') }}</button>
        </template>
      </StatePanel>
      <StatePanel v-else tone="loading" :title="t('plan.page.loading')" :message="t('plan.check.loadingHint')" />
      <p v-if="rebuilding" class="plan-form-note" role="status">
        <span class="spinner is-sm" />{{ t('plan.page.readingGoal') }}
      </p>
      <p v-if="actionError" class="field-error" role="alert">{{ actionError }}</p>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="rebuilding" @click="closeUpdate">
          {{ t('plan.common.cancel') }}
        </button>
        <button
          class="btn btn-primary"
          type="button"
          data-testid="rebuild"
          :disabled="rebuilding || !canRebuild"
          :aria-busy="rebuilding"
          @click="rebuild"
        >
          {{ rebuilding ? t('plan.page.rebuilding') : t('plan.page.rebuild') }}
        </button>
      </template>
    </AppOverlay>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import '@/styles/plan.css'

import { computed, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { PRACTICE_TYPE_SLUGS } from '@/api/learn'
import { planApi } from '@/api/plan'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import GoalForm from '@/components/plan/GoalForm.vue'
import { deadlineReady, draftFromPlan, emptyDraft, goalInput, type GoalDraft } from '@/components/plan/goalDraft'
import PlanAiNote from '@/components/plan/PlanAiNote.vue'
import PlanCoverage from '@/components/plan/PlanCoverage.vue'
import { goalTitle, longDate, paceText, shortDate } from '@/components/plan/planFormat'
import { LEVEL_KEYS, PRACTICE_TYPE_KEYS, STATUS_KEYS } from '@/components/plan/planLabels'
import PlanOutcomes from '@/components/plan/PlanOutcomes.vue'
import PlanPath from '@/components/plan/PlanPath.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { planMessages } from '@/plugins/planMessages'
import type { PlanStatus } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'
import { formatDate } from '@/utils/format'

/**
 * My learning plan (nested under Progress): the goal and its weeks, the Mandarin level, plan progress from real
 * completions, this week's days and to-dos, the path with each step's status and action, outcomes and goal
 * coverage. "Update my goal" rebuilds the plan as a new version (progress is kept); "Retake level check" opens the
 * check in focus mode.
 */
const STATUS_TONES: Readonly<Record<PlanStatus, PillTone>> = {
  STARTS_TODAY: 'info',
  ON_TRACK: 'success',
  BEHIND: 'warning',
  COMPLETE: 'success',
}

const { t, locale } = useLocalMessages({ plan: planMessages })
const resource = useResource(() => planApi.plan())
const options = useResource(() => planApi.options())
const { toast, showToast } = useToast()

const data = computed(() => resource.data.value ?? null)
const updating = ref(false)
const rebuilding = ref(false)
const actionError = ref<string | null>(null)
const draft = ref<GoalDraft>(emptyDraft())

const goal = computed(() => (data.value ? goalTitle(t, data.value.goal.title, data.value.goal.reason) : ''))

const goalWhen = computed(() => {
  const plan = data.value
  if (!plan) return ''
  return plan.goal.deadline
    ? t('plan.cards.byWeek', { date: longDate(plan.goal.deadline), week: plan.weeks.current, total: plan.weeks.total })
    : t('plan.cards.weekOnly', { week: plan.weeks.current, total: plan.weeks.total })
})

/** One cell per week of the plan: done, this week, still to come, or one of the review weeks before the goal. */
const weekCells = computed(() => {
  const plan = data.value
  if (!plan) return []
  const { current, total, content } = plan.weeks
  const complete = plan.progress.status === 'COMPLETE'
  return Array.from({ length: total }, (_, index) => {
    const number = index + 1
    const state =
      complete || number < current
        ? 'is-done'
        : number === current
          ? 'is-now'
          : number > content
            ? 'is-review'
            : 'is-later'
    const key =
      state === 'is-done'
        ? 'plan.page.weekDone'
        : state === 'is-now'
          ? 'plan.page.weekNow'
          : state === 'is-review'
            ? 'plan.page.weekReview'
            : 'plan.page.weekLater'
    return { number, state, label: t(key, { week: number }) }
  })
})

const levelNote = computed(() => {
  const level = data.value?.level
  if (!level?.checkedAt) return t('plan.page.noCheck')
  return level.headStart
    ? t('plan.page.headStartOn', { date: shortDate(level.checkedAt) })
    : t('plan.page.checkedOn', { date: shortDate(level.checkedAt) })
})

const tasks = computed(() =>
  (data.value?.thisWeek ?? []).map((task, index) => {
    const key = `${task.kind}-${index}`
    switch (task.kind) {
      case 'LEARN_DONE':
        return {
          key,
          done: true,
          title: task.title ?? '',
          meta: t('plan.page.learnDone', { day: task.doneOn ? weekday(task.doneOn) : '' }),
          to: null,
        }
      case 'LEARN_NEXT':
        return {
          key,
          done: false,
          title: task.title ?? '',
          meta: t('plan.page.learnNext', { minutes: task.minutes ?? 0 }),
          to: task.lessonId
            ? ({ name: 'student-learn-lesson', params: { lessonId: task.lessonId } } as RouteLocationRaw)
            : null,
        }
      case 'PRACTICE':
        return {
          key,
          done: false,
          title: task.practiceType
            ? t('plan.practiceType.setTitle', {
                type: t(PRACTICE_TYPE_KEYS[task.practiceType]),
                title: task.title ?? '',
              })
            : (task.title ?? ''),
          meta: t('plan.page.practice'),
          to: task.practiceType
            ? ({
                name: 'student-practice',
                params: { type: PRACTICE_TYPE_SLUGS[task.practiceType] },
                query: task.practiceSetId ? { set: String(task.practiceSetId) } : {},
              } as RouteLocationRaw)
            : null,
        }
      default:
        return {
          key,
          done: task.done,
          title: t('plan.page.xiaomei'),
          meta: t('plan.page.xiaomeiMeta'),
          to: { name: 'student-ai', params: { tab: 'talk' } } as RouteLocationRaw,
        }
    }
  }),
)

const canRebuild = computed(
  () => draft.value.reason !== null && draft.value.pace !== null && deadlineReady(draft.value),
)

function weekday(iso: string): string {
  return formatDate(iso, { weekday: 'long' }, locale.value)
}

function openUpdate(): void {
  if (!data.value) return
  draft.value = draftFromPlan(data.value)
  actionError.value = null
  updating.value = true
  if (options.error.value) void options.reload()
}

function closeUpdate(): void {
  if (!rebuilding.value) updating.value = false
}

async function rebuild(): Promise<void> {
  rebuilding.value = true
  actionError.value = null
  try {
    resource.data.value = await planApi.updateGoal(goalInput(draft.value), locale.value)
    updating.value = false
    showToast(t('plan.page.updated'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('plan.page.updateError'))
  } finally {
    rebuilding.value = false
  }
}
</script>
