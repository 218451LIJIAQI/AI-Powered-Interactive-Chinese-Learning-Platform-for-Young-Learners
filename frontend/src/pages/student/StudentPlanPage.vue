<template>
  <div class="plan-page page-stack">
    <PageHeaderBlock :title="t('plan.page.title')" :description="data ? goalLine : ''">
      <template #before>
        <AppBreadcrumb
          :items="[
            { label: t('plan.page.progress'), to: { name: 'student-progress' } },
            { label: t('plan.page.title') },
          ]"
        />
        <p v-if="justUpdated" class="alert alert-success plan-updated" role="status" data-testid="plan-updated">
          <IconGlyph name="check" :size="18" />{{ t('plan.page.updated') }}
        </p>
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
      <section class="panel plan-stats" :aria-label="t('plan.page.statsLabel')">
        <div class="plan-stat">
          <h2 class="page-eyebrow">{{ t('plan.page.mandarinLevel') }}</h2>
          <!-- One level when the goal is the level already reached ("Beginner 1", not "Beginner 1 → Beginner 1"). -->
          <p class="plan-stat-value">
            {{
              data.level.placed === data.level.target
                ? t(LEVEL_KEYS[data.level.placed])
                : t('plan.level.fromTo', {
                    from: t(LEVEL_KEYS[data.level.placed]),
                    to: t(LEVEL_KEYS[data.level.target]),
                  })
            }}
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
            {{ t('plan.page.daysOf', { done: data.progress.daysThisWeek, total: data.progress.paceDays }) }}
          </p>
          <p class="type-caption" :class="{ 'plan-stat-behind': data.progress.status === 'BEHIND' }">
            {{ t(STATUS_KEYS[data.progress.status]) }}
          </p>
        </div>
        <div class="plan-stat">
          <h2 class="page-eyebrow">{{ t('plan.page.pace') }}</h2>
          <p class="plan-stat-value">{{ paceText(t, data.pace) }}</p>
          <p class="type-caption">{{ t('plan.page.dailyGoal', { xp: data.pace.dailyGoalXp }) }}</p>
        </div>
      </section>

      <div class="plan-layout">
        <section class="plan-road-card" aria-labelledby="plan-path-heading">
          <h2 id="plan-path-heading" class="card-title">{{ t('plan.page.yourPath') }}</h2>
          <PlanPath :plan="data" live :picked="picked" @pick="togglePick" />
        </section>

        <aside class="plan-rail">
          <section class="plan-rail-block" aria-labelledby="plan-week-heading">
            <h2 id="plan-week-heading" class="card-title">
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
                  <span class="type-caption">{{ task.meta }}</span>
                </span>
                <span class="sr-only">{{ task.done ? t('plan.page.taskDone') : t('plan.page.taskToDo') }}</span>
              </li>
            </ul>
          </section>
          <PlanOutcomes :plan="data" />
          <PlanAiNote :plan="data" />
        </aside>
      </div>
    </template>

    <AppOverlay
      :open="updating"
      variant="drawer"
      :title="t('plan.page.updateTitle')"
      :busy="rebuilding"
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
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="options.reload">{{ t('plan.common.retry') }}</button>
        </template>
      </StatePanel>
      <StatePanel v-else tone="loading" :title="t('plan.page.loading')" :message="t('plan.check.loadingHint')" />
      <p v-if="actionError" class="field-error" role="alert">{{ actionError }}</p>
      <template #footer>
        <p v-if="rebuilding" class="type-caption is-far-left" role="status">{{ t('plan.page.readingGoal') }}</p>
        <button class="btn btn-secondary" type="button" :disabled="rebuilding" @click="closeUpdate">
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
          <span v-if="rebuilding" class="spinner is-sm" aria-hidden="true" />
          {{ rebuilding ? t('plan.page.rebuilding') : t('plan.page.rebuild') }}
        </button>
      </template>
    </AppOverlay>
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
import AppOverlay from '@/components/common/AppOverlay.vue'
import GoalForm from '@/components/plan/GoalForm.vue'
import { deadlineReady, draftFromPlan, emptyDraft, goalInput, type GoalDraft } from '@/components/plan/goalDraft'
import PlanAiNote from '@/components/plan/PlanAiNote.vue'
import { goalTitle, longDate, paceText } from '@/components/plan/planFormat'
import { LEVEL_KEYS, PRACTICE_TYPE_KEYS, STATUS_KEYS } from '@/components/plan/planLabels'
import PlanOutcomes from '@/components/plan/PlanOutcomes.vue'
import PlanPath from '@/components/plan/PlanPath.vue'
import { usePlanPick } from '@/components/plan/usePlanPick'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatDate } from '@/utils/format'

/**
 * My learning plan (nested under Progress): the Mandarin level, plan progress, this week's days and the pace; the road
 * map from where the student is to the goal; this week's to-dos and what the plan leads to. "Update my goal" rebuilds the
 * plan as a new version (progress is kept); "Retake level check" opens the check in focus mode.
 */
const { t, locale } = useLocalMessages({ plan: planMessages })
const resource = useResource(() => planApi.plan())
const options = useResource(() => planApi.options())

const data = computed(() => resource.data.value ?? null)
const updating = ref(false)
const rebuilding = ref(false)
const justUpdated = ref(false)
const actionError = ref<string | null>(null)
const draft = ref<GoalDraft>(emptyDraft())

const { picked, togglePick } = usePlanPick(data)

const goalLine = computed(() => {
  const plan = data.value
  if (!plan) return ''
  const goal = goalTitle(t, plan.goal.title, plan.goal.reason)
  const weeks = { week: plan.weeks.current, total: plan.weeks.total }
  return plan.goal.deadline
    ? t('plan.page.metaBy', { goal, date: longDate(plan.goal.deadline), ...weeks })
    : t('plan.page.meta', { goal, ...weeks })
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
  justUpdated.value = false
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
    justUpdated.value = true
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('plan.page.updateError'))
  } finally {
    rebuilding.value = false
  }
}
</script>
