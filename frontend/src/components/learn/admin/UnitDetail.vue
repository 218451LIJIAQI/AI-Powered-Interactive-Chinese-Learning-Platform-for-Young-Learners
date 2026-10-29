<template>
  <div class="admin-reader-slot" data-testid="unit-detail">
    <StatePanel
      v-if="unitId === null"
      tone="empty"
      icon="route"
      spacious
      :title="t('adminLearning.chooseUnit')"
      :message="t('adminLearning.chooseUnitHint')"
    />
    <StatePanel
      v-else-if="detail.loading.value"
      tone="loading"
      spacious
      :title="t('adminLearning.unitLoading')"
      :message="t('adminLearning.unitLoadingHint')"
    />
    <StatePanel
      v-else-if="detail.error.value"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminLearning.unitError')"
      :message="detail.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('adminLearning.retry') }}</button>
      </template>
    </StatePanel>
    <section v-else-if="unit" class="panel admin-reader" aria-labelledby="unit-detail-title">
      <header class="admin-reader-head">
        <div class="admin-reader-heading">
          <h2 id="unit-detail-title">{{ unitTitle }}</h2>
          <p>{{ unitMeta }}</p>
          <p class="admin-path-state">
            <StatusPill
              :label="published ? t('adminLearning.published') : t('adminLearning.draft')"
              :tone="published ? 'success' : 'neutral'"
            />
            <span>{{ published ? t('adminLearning.unitLive') : t('adminLearning.unitHidden') }}</span>
          </p>
        </div>
        <button
          v-if="published"
          class="btn btn-outline"
          type="button"
          data-testid="unit-unpublish"
          @click="confirming = true"
        >
          <IconGlyph name="eyeOff" :size="16" />
          {{ t('adminLearning.unpublish') }}
        </button>
        <button
          v-else
          class="btn btn-primary"
          type="button"
          :disabled="busy"
          :aria-busy="busy"
          data-testid="unit-publish"
          @click="changeStatus(true)"
        >
          <IconGlyph name="send" :size="16" />
          {{ t('adminLearning.publish') }}
        </button>
      </header>

      <section class="admin-reader-section" aria-labelledby="unit-detail-lessons">
        <h3 id="unit-detail-lessons" class="admin-reader-label">{{ t('adminLearning.lessons') }}</h3>
        <ol class="admin-timeline">
          <li v-for="stop in detail.data.value!.lessons" :key="stop.id">
            <span class="admin-timeline-mark" :class="{ 'is-numbered': stop.kind === 'LESSON' }" aria-hidden="true">
              <IconGlyph v-if="stop.kind !== 'LESSON'" :name="kindIcon(stop.kind)" :size="14" />
              <template v-else>{{ lessonNumber(stop.id) }}</template>
            </span>
            <span class="admin-timeline-copy">
              <strong>{{ stop.title }}</strong>
              <span>
                {{ t(kindKey(stop.kind)) }} · {{ t('adminLearning.aboutMinutes', { minutes: stop.minutes }) }} · +{{
                  stop.xpReward
                }}
                XP · {{ t('adminLearning.questionCount', { count: stop.questionCount }, stop.questionCount) }}
              </span>
            </span>
          </li>
        </ol>
        <p class="type-caption">{{ t('adminLearning.unitNote') }}</p>
      </section>
    </section>
  </div>

  <ConfirmDialog
    :open="confirming"
    :title="t('adminLearning.unpublishUnitTitle', { title: unitTitle })"
    :message="t('adminLearning.unpublishUnitMessage')"
    :confirm-label="t('adminLearning.unpublishUnit')"
    :cancel-label="t('adminLearning.keepPublished')"
    :busy="busy"
    @confirm="changeStatus(false)"
    @cancel="confirming = false"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type { AdminUnitDetail, LessonKind } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'

/**
 * The unit chosen on the admin Learning path, beside the path: its stops in order (kind, minutes, XP, questions),
 * whether students see it, and publish / unpublish (unpublishing asks first).
 */
const props = defineProps<{ unitId: number | null }>()
const emit = defineEmits<{
  changed: [detail: AdminUnitDetail, message: string]
  failed: [message: string]
}>()

const { t } = useLocalMessages({ adminLearning: adminLearningMessages })

const detail = useResource(
  () => (props.unitId === null ? Promise.resolve(null) : adminLearningApi.unit(props.unitId)),
  false,
)
const busy = ref(false)
const confirming = ref(false)

watch(
  () => props.unitId,
  (id) => {
    detail.data.value = null
    if (id !== null) void detail.reload()
  },
  { immediate: true },
)

const unit = computed(() => detail.data.value?.unit ?? null)
const published = computed(() => unit.value?.status === 'PUBLISHED')

const unitTitle = computed(() => {
  const value = unit.value
  return value ? t('adminLearning.unitTitle', { number: value.number, title: value.title, titleZh: value.titleZh }) : ''
})

const unitMeta = computed(() => {
  const value = unit.value
  if (!value) return ''
  return t('adminLearning.unitMeta', {
    lessons: value.lessonCount,
    hsk: value.hskLevel,
    students: value.studentsStarted,
  })
})

function lessonNumber(id: number): number {
  const lessons = detail.data.value?.lessons.filter((stop) => stop.kind === 'LESSON') ?? []
  return lessons.findIndex((stop) => stop.id === id) + 1
}

function kindIcon(kind: LessonKind): string {
  if (kind === 'BONUS') return 'rewards'
  return kind === 'CHECKPOINT' ? 'trophy' : 'star'
}

function kindKey(kind: LessonKind): string {
  switch (kind) {
    case 'BONUS':
      return 'adminLearning.kindBonus'
    case 'REVIEW':
      return 'adminLearning.kindReview'
    case 'CHECKPOINT':
      return 'adminLearning.kindCheckpoint'
    default:
      return 'adminLearning.kindLesson'
  }
}

async function changeStatus(publish: boolean): Promise<void> {
  if (props.unitId === null) return
  busy.value = true
  try {
    const updated = await adminLearningApi.setUnitPublished(props.unitId, publish)
    detail.data.value = updated
    const title = t('adminLearning.unitTitle', {
      number: updated.unit.number,
      title: updated.unit.title,
      titleZh: updated.unit.titleZh,
    })
    emit(
      'changed',
      updated,
      publish ? t('adminLearning.unitPublished', { title }) : t('adminLearning.unitUnpublished', { title }),
    )
  } catch (reason) {
    emit('failed', resolveApiError(reason, t('adminLearning.saveFailed')))
  } finally {
    busy.value = false
    confirming.value = false
  }
}
</script>
