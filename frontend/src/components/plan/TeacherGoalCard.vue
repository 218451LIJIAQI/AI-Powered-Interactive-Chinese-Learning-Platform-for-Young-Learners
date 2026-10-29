<template>
  <section v-if="goal" class="plan-teacher-goal" aria-labelledby="plan-teacher-goal-title" data-testid="learning-goal">
    <div class="plan-teacher-goal-head">
      <div>
        <h3 id="plan-teacher-goal-title" class="card-title">{{ t('plan.cards.learningGoal') }}</h3>
        <p class="type-caption">
          {{ t('plan.cards.fromPlan', { name: studentName, date: shortDate(goal.updatedAt) }) }}
        </p>
      </div>
      <StatusPill :label="t('plan.cards.readOnly')" tone="neutral" icon="eye" />
    </div>
    <dl class="plan-teacher-goal-list">
      <div>
        <dt>{{ t('plan.cards.goal') }}</dt>
        <dd>{{ goal.deadline ? t('plan.cards.goalBy', { goal: title, date: shortDate(goal.deadline) }) : title }}</dd>
      </div>
      <div>
        <dt>{{ t('plan.cards.focus') }}</dt>
        <dd>{{ focus }}</dd>
      </div>
      <div>
        <dt>{{ t('plan.cards.level') }}</dt>
        <dd>
          {{
            t('plan.cards.levelTarget', { level: t(LEVEL_KEYS[goal.level]), target: t(LEVEL_KEYS[goal.targetLevel]) })
          }}
        </dd>
      </div>
      <div>
        <dt>{{ t('plan.cards.yourClass') }}</dt>
        <dd>
          <template v-if="goal.yourClasses.length">
            <span v-for="item in goal.yourClasses" :key="item.classId" class="plan-teacher-goal-class">
              {{ t('plan.cards.classStep', { title: item.title, step: item.position, total: goal.stepCount }) }}
            </span>
          </template>
          <template v-else>{{ t('plan.cards.notInPlan') }}</template>
        </dd>
      </div>
    </dl>
    <p class="type-caption">{{ t('plan.cards.tailor') }}</p>
  </section>
  <p v-else-if="resource.error.value" class="field-error" role="alert">
    {{ t('plan.cards.goalLoadError') }}: {{ resource.error.value }}
  </p>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'

import { teacherPlanApi } from '@/api/plan'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'

import { goalTitle, shortDate } from './planFormat'
import { FOCUSES, LEVEL_KEYS } from './planLabels'

/**
 * The read-only "Learning goal" card on the teacher's student detail: the student's goal, focus and level from their
 * plan, and where the teacher's classes are in it. The server shows it only for a student in one of the teacher's
 * classes, and never the student's own words. Nothing shows when the student has no plan.
 */
const props = defineProps<{ studentId: number; studentName: string }>()

const { t } = useLocalMessages({ plan: planMessages })
const resource = useResource(() => teacherPlanApi.learningGoal(props.studentId))

watch(
  () => props.studentId,
  () => void resource.reload(),
)

const goal = computed(() => resource.data.value ?? null)
const title = computed(() => (goal.value ? goalTitle(t, goal.value.goalTitle, goal.value.reason) : ''))
const focus = computed(() =>
  goal.value?.focus.length
    ? goal.value.focus.map((item) => t(FOCUSES[item].label)).join(', ')
    : t('plan.cards.noFocus'),
)
</script>
