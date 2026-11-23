<template>
  <div class="plan-page page-stack">
    <PageHeaderBlock :title="t('plan.page.title')" :description="goalLine">
      <template #before>
        <AppBreadcrumb
          :items="[{ label: t('nav.progress'), to: { name: 'student-progress' } }, { label: t('plan.page.title') }]"
        />
      </template>
      <template v-if="data" #actions>
        <RouterLink class="btn btn-ghost" :to="{ name: 'student-level-check' }" data-testid="retake">
          {{ t('plan.page.retake') }}
        </RouterLink>
        <button class="btn btn-secondary" type="button" data-testid="update-goal" @click="openUpdate">
          {{ t('plan.page.update') }}
        </button>
      </template>
    </PageHeaderBlock>

    <StatePanel
      v-if="resource.loading.value"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.page.loadingHint')"
    />
    <StatePanel
      v-else-if="resource.error.value && !data"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('plan.page.loadError')"
      :message="resource.error.value"
      @retry="resource.reload"
    />
    <StatePanel
      v-else-if="!data"
      heading-tag="h2"
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
      <section class="panel plan-stats" :aria-label="t('plan.page.statsLabel')" data-testid="plan-stats">
        <div class="plan-stat">
          <h2 class="page-eyebrow">{{ t('plan.page.mandarinLevel') }}</h2>
          <p class="plan-stat-value">{{ levelSpan(t, data.level.placed, data.level.target) }}</p>
          <p v-if="data.level.placed === data.level.target" class="type-caption">
            {{ t('plan.page.targetReached') }}
          </p>
        </div>
        <div class="plan-stat">
          <h2 class="page-eyebrow">{{ t('plan.page.planProgress') }}</h2>
          <p class="plan-stat-value">{{ data.progress.percent }}%</p>
          <ProgressBar class="is-sm" :progress="data.progress.percent" :label="t('plan.page.planProgress')" />
        </div>
        <div class="plan-stat">
          <h2 class="page-eyebrow">{{ t('plan.page.thisWeek') }}</h2>
          <p class="plan-stat-value">
            {{ t('plan.page.daysOf', { done: data.progress.daysThisWeek, total: DAYS_A_WEEK }) }}
          </p>
          <p class="type-caption" :class="{ 'plan-stat-behind': data.progress.status === 'BEHIND' }">
            {{ t(`plan.status.${data.progress.status}`) }}
          </p>
        </div>
        <div class="plan-stat">
          <h2 class="page-eyebrow">{{ t('plan.page.pace') }}</h2>
          <p class="plan-stat-value">{{ paceDaily(t, data.pace) }}</p>
        </div>
      </section>

      <div class="plan-layout">
        <section class="plan-road-card" aria-labelledby="plan-path-heading">
          <h2 id="plan-path-heading" class="card-title">{{ t('plan.page.yourPath') }}</h2>
          <PlanPath :plan="data" live :picked="picked" @pick="togglePick" />
          <p v-if="pickError" class="field-error" role="alert" data-testid="pick-error">{{ pickError }}</p>
        </section>

        <aside class="plan-rail">
          <section class="plan-rail-block" aria-labelledby="plan-week-heading">
            <h2 id="plan-week-heading" class="card-title">{{ t('plan.page.thisWeek') }}</h2>
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
                  <span class="type-caption">{{ task.meta }}</span>
                </span>
                <span class="sr-only">{{ task.done ? t('plan.page.taskDone') : t('plan.page.taskToDo') }}</span>
              </li>
            </ul>
          </section>
          <PlanOutcomes :plan="data" />
          <!-- Who wrote what: an AI-made plan can make mistakes and its texts can be reported. -->
          <div class="plan-ai-note" data-testid="plan-ai-note">
            <IconGlyph :name="data.aiAssisted ? 'magic' : 'info'" :size="14" />
            <p>
              {{ data.aiAssisted ? t('plan.page.aiNote') : t('plan.page.rulesNote') }}
              <PlanReportLink v-if="data.aiAssisted" :plan="data" @sent="showToast(t('aiSafety.reportSent'))" />
            </p>
          </div>
        </aside>
      </div>
    </template>

    <AppOverlay
      :open="updating"
      variant="drawer"
      :title="t('plan.page.update')"
      :busy="rebuilding"
      test-id="update-goal-drawer"
      @close="closeUpdate"
    >
      <!-- A failed rebuild says so at the top, where it is seen first; the draft stays to try again (S12.12). -->
      <p
        v-if="actionError"
        ref="errorNotice"
        class="alert alert-danger"
        role="alert"
        tabindex="-1"
        data-testid="rebuild-error"
      >
        {{ actionError }}
      </p>
      <template v-if="options.data.value">
        <fieldset class="field-set" :disabled="rebuilding">
          <legend class="sr-only">{{ t('plan.page.update') }}</legend>
          <GoalForm
            v-model="draft"
            :options="options.data.value"
            :sections="['reason', 'goals', 'deadline', 'pace', 'focus']"
            compact
            :calendar-no-date="false"
          />
        </fieldset>
        <p
          v-if="data && deadlinePassed(data.goal.deadline, effectiveToday) && !deadlineReady(draft)"
          class="callout is-compact"
          role="status"
          data-testid="deadline-passed"
        >
          <IconGlyph name="calendar" :size="16" /><span>{{ t('plan.page.deadlinePassed') }}</span>
        </p>
        <p class="callout is-compact">
          <IconGlyph name="info" :size="16" /><span>{{ t('plan.page.keptNote') }}</span>
        </p>
      </template>
      <StatePanel
        v-else-if="options.error.value"
        tone="error"
        icon="alert"
        :title="t('plan.page.updateError')"
        :message="options.error.value"
        @retry="options.reload"
      />
      <StatePanel v-else tone="loading" :title="t('plan.page.loading')" :message="t('plan.check.loadingHint')" />
      <template #footer>
        <p v-if="rebuilding" class="type-caption is-far-left" role="status">{{ t('plan.page.readingGoal') }}</p>
        <button class="btn btn-secondary" type="button" :disabled="rebuilding" @click="closeUpdate">
          {{ t('common.cancel') }}
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

    <!-- Closing the drawer with changes in it asks first (X2.31); without changes it just closes. -->
    <ConfirmDialog
      :open="discarding"
      :title="t('plan.page.discardTitle')"
      :message="t('plan.page.discardMessage')"
      :confirm-label="t('plan.page.discardChanges')"
      :cancel-label="t('plan.page.keepEditing')"
      @confirm="discardChanges"
      @cancel="discarding = false"
    />
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import '@/styles/plan.css'

import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { planApi } from '@/api/plan'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { practiceSetRoute } from '@/components/learn/practice/practiceTypes'
import GoalForm from '@/components/plan/GoalForm.vue'
import {
  deadlinePassed,
  deadlineReady,
  draftFromPlan,
  emptyDraft,
  goalInput,
  sameGoal,
  type GoalDraft,
} from '@/components/plan/goalDraft'
import { goalTitle, levelSpan, paceDaily, planExpired, practiceSetTitle } from '@/components/plan/planFormat'
import { LEVEL_KEYS } from '@/components/plan/planLabels'
import PlanOutcomes from '@/components/plan/PlanOutcomes.vue'
import PlanPath from '@/components/plan/PlanPath.vue'
import PlanReportLink from '@/components/plan/PlanReportLink.vue'
import { usePlanPick } from '@/components/plan/usePlanPick'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { pauseDuringNavigation } from '@/composables/usePageNavigation'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatWeekdayLongDate } from '@/utils/format'

/**
 * My learning plan (nested under Progress): the Mandarin level, plan progress, this week's days and the daily time; the
 * road map from where the learner is to the goal; this week's to-dos and what the plan leads to. "Update my goal"
 * rebuilds the plan as a new version (progress is kept); "Retake level check" opens the check in focus mode. Home's
 * "Adjust plan" / "Set a new goal" open the drawer (`?edit=goal`), and a retake that changed the level is confirmed on
 * arrival (`?updated=level`).
 */
const DAYS_A_WEEK = 7

const route = useRoute()
const router = useRouter()
const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages, planPathMessages] })
const { toast, showToast } = useToast()
const resource = useResource(planApi.plan)
const options = useResource(planApi.options, route.query.edit === 'goal')

const data = resource.data
const effectiveToday = computed(() => options.data.value?.today ?? data.value?.today ?? '')
const updating = ref(false)
const rebuilding = ref(false)
/** "Discard your changes?" over the drawer. */
const discarding = ref(false)
const actionError = ref<string | null>(null)
const errorNotice = ref<HTMLElement | null>(null)
const draft = ref<GoalDraft>(emptyDraft())

const { picked, togglePick, pickError, whenSaved } = usePlanPick(data, () => t('plan.path.pickError'))
const isUnmounted = useIsUnmounted()

/** The drawer opens on the plan's own goal: there is nothing to rebuild until the learner changes it (S12.11). */
const baselineDraft = computed(() =>
  data.value ? draftFromPlan({ ...data.value, today: effectiveToday.value }) : null,
)
const goalChanged = computed(() => (baselineDraft.value ? !sameGoal(draft.value, baselineDraft.value) : false))
const canRebuild = computed(() => {
  const goal = draft.value
  return Boolean(
    options.data.value &&
    goalChanged.value &&
    deadlineReady(goal) &&
    (goal.reason !== 'OTHER' || goal.reasonText.trim() || goal.goalText.trim()),
  )
})

const goalLine = computed(() => {
  const plan = data.value
  if (!plan) return ''
  const goal = goalTitle(t, plan.goal.title, plan.goal.reason)
  const { deadline } = plan.goal
  if (!deadline) return t('plan.page.meta', { goal, week: plan.weeks.current, total: plan.weeks.total })
  const date = formatWeekdayLongDate(deadline, locale.value)
  return planExpired(plan.progress.status, deadline, plan.today)
    ? t('plan.page.metaEnded', { goal, date })
    : t('plan.page.metaBy', { goal, date, week: plan.weeks.current, total: plan.weeks.total })
})

const tasks = computed<{ key: string; done: boolean; title: string; meta: string; to: RouteLocationRaw | null }[]>(() =>
  (data.value?.thisWeek ?? []).map((task, index) => {
    const key = `${task.kind}-${index}`
    switch (task.kind) {
      case 'LEARN_DONE':
        return {
          key,
          done: true,
          title: task.title,
          meta: t('plan.page.learnDone', { day: formatDate(task.doneOn, { weekday: 'long' }, locale.value) }),
          to: null,
        }
      case 'LEARN_NEXT':
        return {
          key,
          done: false,
          title: task.title,
          meta: t('plan.page.learnNext', { minutes: task.minutes }),
          to: { name: 'student-learn-lesson', params: { lessonId: task.lessonId } },
        }
      case 'PRACTICE':
        return {
          key,
          done: false,
          title: practiceSetTitle(t, task.practiceType, task.title),
          meta: t('plan.page.practice'),
          to: practiceSetRoute(task.practiceType, task.practiceSetId),
        }
      case 'XIAOMEI':
        return {
          key,
          done: task.done,
          title: t('plan.page.xiaomei'),
          meta: t('plan.page.xiaomeiMeta'),
          to: { name: 'student-ai', params: { tab: 'talk' } },
        }
    }
  }),
)

function openUpdate(): void {
  if (!data.value) return
  draft.value = draftFromPlan({ ...data.value, today: effectiveToday.value })
  actionError.value = null
  updating.value = true
  if (!options.data.value && !options.loading.value) void options.reload()
}

// If setup options arrive with a newer platform day that has passed the plan's saved deadline, clear the untouched date.
watch(options.data, (loaded) => {
  if (!updating.value || !loaded || !data.value) return
  const savedDeadline = data.value.goal.deadline
  if (savedDeadline && draft.value.deadline === savedDeadline && deadlinePassed(savedDeadline, loaded.today)) {
    draft.value = { ...draft.value, deadline: undefined }
  }
})

/** Cancel, × and Escape: changes to the goal are lost on close, so the learner confirms that first (X2.31). */
function closeUpdate(): void {
  if (goalChanged.value) discarding.value = true
  else updating.value = false
}

/** The confirm dialog closes before the drawer, so focus goes back to "Update my goal". */
async function discardChanges(): Promise<void> {
  discarding.value = false
  await nextTick()
  updating.value = false
}

async function rebuild(): Promise<void> {
  if (rebuilding.value || !canRebuild.value) return
  rebuilding.value = true
  actionError.value = null
  const session = getAuthSessionVersion()
  const stillCurrent = () => !isUnmounted() && session === getAuthSessionVersion()
  const goal = goalInput(draft.value)
  const language = locale.value
  try {
    await whenSaved()
    if (!stillCurrent()) return
    const updated = await planApi.updateGoal(goal, language)
    if (!stillCurrent()) return
    data.value = updated
    updating.value = false
    showToast(t('plan.page.updated'))
  } catch (reason) {
    if (!stillCurrent()) return
    actionError.value = resolveApiError(reason, t('plan.page.rebuildError'))
    await nextTick()
    errorNotice.value?.focus()
  } finally {
    if (stillCurrent()) rebuilding.value = false
  }
}

// What the page was opened for, once the plan is in: the drawer, or the confirmation of a new level. The address
// drops the request, so going back or reloading does not repeat it.
const openFromQuery = watch(
  [data, () => route.query.edit, () => route.query.updated],
  ([plan]) => {
    if (route.name !== 'student-plan' || !plan) return
    const { edit, updated, ...query } = route.query
    if (edit === undefined && updated === undefined) return
    if (edit === 'goal') openUpdate()
    if (updated === 'level') showToast(t('plan.page.levelUpdated', { level: t(LEVEL_KEYS[plan.level.placed]) }))
    void router.replace({ query, hash: route.hash })
  },
  { immediate: true },
)
pauseDuringNavigation(openFromQuery)
</script>
