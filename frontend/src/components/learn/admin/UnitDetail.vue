<template>
  <AppOverlay
    variant="drawer"
    :open="unitId !== null"
    :title="unitTitle || t('adminLearning.unitLoading')"
    :subtitle="unitMeta"
    :busy="busy"
    test-id="unit-detail"
    @close="emit('close')"
  >
    <StatePanel
      v-if="detail.loading.value"
      tone="loading"
      :title="t('adminLearning.unitLoading')"
      :message="t('adminLearning.unitLoadingHint')"
    />
    <StatePanel
      v-else-if="detail.error.value"
      tone="error"
      icon="alert"
      :title="t('adminLearning.unitError')"
      :message="detail.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('adminLearning.retry') }}</button>
      </template>
    </StatePanel>
    <section v-else-if="unit" class="admin-section" aria-labelledby="unit-detail-lessons">
      <h3 id="unit-detail-lessons" class="admin-section-title">{{ t('adminLearning.lessons') }}</h3>
      <ol class="row-list">
        <li v-for="stop in stops" :key="stop.id">
          <span class="admin-path-stop">
            <span class="admin-step" aria-hidden="true">
              <IconGlyph v-if="stop.number === null" name="star" :size="14" />
              <template v-else>{{ stop.number }}</template>
            </span>
            <span class="row-list-copy">
              <span class="row-list-title"><CjkText :text="stop.title" /></span>
              <span class="row-list-meta">{{ stop.meta }}</span>
            </span>
          </span>
        </li>
      </ol>
    </section>
    <template v-if="unit && !detail.loading.value" #footer>
      <button class="btn btn-secondary" type="button" @click="emit('close')">{{ t('adminLearning.close') }}</button>
      <button
        v-if="published"
        class="btn btn-secondary"
        type="button"
        data-testid="unit-unpublish"
        @click="confirming = true"
      >
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
        {{ t('adminLearning.publish') }}
      </button>
    </template>
  </AppOverlay>

  <AdminDecisionDialog
    :open="confirming"
    :title="t('adminLearning.unpublishUnitTitle', { title: unitTitle })"
    :confirm-label="t('adminLearning.unpublishUnit')"
    :cancel-label="t('adminLearning.keepPublished')"
    :busy="busy"
    test-id="unit-unpublish-dialog"
    @confirm="changeStatus(false)"
    @cancel="confirming = false"
  >
    <p class="overlay-message">{{ t('adminLearning.unpublishUnitMessage') }}</p>
  </AdminDecisionDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AdminDecisionDialog from '@/components/admin/AdminDecisionDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type { AdminUnitDetail, LessonKind } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatNumber } from '@/utils/format'

/**
 * One unit of the admin Learning path, in a drawer: its stops in order (kind, minutes, XP), whether students see it, and
 * publish / unpublish (unpublishing asks first).
 */
const props = defineProps<{ unitId: number | null }>()
const emit = defineEmits<{
  close: []
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
  const parts = [t('adminLearning.lessonsHsk', { lessons: value.lessonCount, hsk: value.hskLevel })]
  if (published.value) {
    parts.push(
      t('adminLearning.studentsStarted', { count: formatNumber(value.studentsStarted) }, value.studentsStarted),
    )
  }
  parts.push(published.value ? t('adminLearning.published') : t('adminLearning.draft'))
  return parts.join(' · ')
})

const KIND_KEYS: Readonly<Record<LessonKind, string>> = {
  LESSON: 'adminLearning.kindLesson',
  BONUS: 'adminLearning.kindBonus',
  REVIEW: 'adminLearning.kindReview',
  CHECKPOINT: 'adminLearning.kindCheckpoint',
}

/** The stops as students meet them: numbered in order, a bonus chest (a star) opens after the lesson before it. */
const stops = computed(() => {
  let number = 0
  return (detail.data.value?.lessons ?? []).map((stop) => {
    if (stop.kind === 'BONUS') {
      return {
        id: stop.id,
        number: null,
        title: stop.title,
        meta: t('adminLearning.bonusMeta', { lesson: number }),
      }
    }
    number += 1
    return {
      id: stop.id,
      number,
      title: stop.title,
      meta: t('adminLearning.stopMeta', { kind: t(KIND_KEYS[stop.kind]), minutes: stop.minutes, xp: stop.xpReward }),
    }
  })
})

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
