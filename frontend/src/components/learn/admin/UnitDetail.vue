<template>
  <AppOverlay
    variant="drawer"
    :title="unitTitle || t(detail.error.value ? 'adminLearning.unitError' : 'adminLearning.unitLoading')"
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
      :title="t('adminLearning.unitError')"
      :message="detail.error.value"
      @retry="detail.reload"
    />
    <section v-else-if="unit" class="admin-section" :aria-labelledby="`${baseId}-lessons`">
      <h3 :id="`${baseId}-lessons`" class="admin-section-title">{{ t('adminLearning.lessons') }}</h3>
      <ol class="row-list">
        <li v-for="stop in stops" :key="stop.id">
          <span class="admin-path-stop">
            <span class="admin-step" aria-hidden="true">
              <IconGlyph v-if="stop.icon" :name="stop.icon" :size="14" />
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
    <template v-if="unit" #footer>
      <!-- By the buttons, not on the page under the drawer's scrim, nor below a long list. -->
      <p v-if="actionError" class="field-error" role="alert">{{ actionError }}</p>
      <button class="btn btn-secondary" type="button" :disabled="busy" @click="emit('close')">
        {{ t('common.close') }}
      </button>
      <button
        v-if="published"
        class="btn btn-secondary"
        type="button"
        :disabled="busy"
        :aria-busy="busy || undefined"
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
        :aria-busy="busy || undefined"
        data-testid="unit-publish"
        @click="changeStatus(true)"
      >
        {{ t('adminLearning.publish') }}
      </button>
    </template>
  </AppOverlay>

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
import { computed, ref, useId } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AppOverlay from '@/components/common/AppOverlay.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminLearningPathMessages } from '@/plugins/adminLearningPathMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import type { LessonKind } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'

import { unitDrawerMeta } from './unitLessons'

/**
 * One unit of the admin Learning path, in a drawer (A4.02): its stops in order (kind, minutes, XP), whether learners see
 * it, and publish / unpublish (unpublishing asks first: learners lose the unit from their path); a change that fails says
 * so in the drawer. The page mounts it for one opening, so it reads its unit once.
 */
const props = defineProps<{ unitId: number }>()
const emit = defineEmits<{
  close: []
  changed: [message: string]
}>()

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminLearningPathMessages] })
const baseId = useId()
const isUnmounted = useIsUnmounted()

const detail = useResource(() => adminLearningApi.unit(props.unitId))
const busy = ref(false)
const confirming = ref(false)
const actionError = ref<string | null>(null)

const unit = computed(() => detail.data.value?.unit ?? null)
const published = computed(() => unit.value?.status === 'PUBLISHED')

const unitTitle = computed(() => {
  const value = unit.value
  return value ? t('adminLearning.unitTitle', { number: value.number, title: value.title, titleZh: value.titleZh }) : ''
})

const unitMeta = computed(() => (unit.value ? unitDrawerMeta(t, unit.value) : ''))

const KIND_KEYS: Readonly<Record<Exclude<LessonKind, 'BONUS'>, string>> = {
  LESSON: 'adminLearning.kindLesson',
  REVIEW: 'adminLearning.kindReview',
  CHECKPOINT: 'adminLearning.kindCheckpoint',
}

/** The stops as learners meet them: numbered lessons/reviews; bonus chests (star) and checkpoints (trophy) are unnumbered. */
const stops = computed(() => {
  let number = 0
  return (detail.data.value?.lessons ?? []).map((stop) => {
    if (stop.kind === 'BONUS') {
      return {
        id: stop.id,
        number: null,
        icon: 'star' as const,
        title: stop.title,
        meta: t('adminLearning.bonusMeta', { lesson: number }),
      }
    }
    if (stop.kind === 'CHECKPOINT') {
      return {
        id: stop.id,
        number: null,
        icon: 'trophy' as const,
        title: stop.title,
        meta: t('adminLearning.stopMeta', { kind: t(KIND_KEYS[stop.kind]), minutes: stop.minutes, xp: stop.xpReward }),
      }
    }
    number += 1
    return {
      id: stop.id,
      number,
      icon: null,
      title: stop.title,
      meta: t('adminLearning.stopMeta', { kind: t(KIND_KEYS[stop.kind]), minutes: stop.minutes, xp: stop.xpReward }),
    }
  })
})

async function changeStatus(publish: boolean): Promise<void> {
  if (busy.value) return
  busy.value = true
  actionError.value = null
  try {
    const updated = await adminLearningApi.setUnitPublished(props.unitId, publish)
    if (isUnmounted()) return
    detail.replace(updated)
    const title = unitTitle.value
    emit('changed', t(publish ? 'adminLearning.unitPublished' : 'adminLearning.unitUnpublished', { title }))
  } catch (reason) {
    if (!isUnmounted()) actionError.value = resolveApiError(reason, t('adminLearning.saveFailed'))
  } finally {
    if (!isUnmounted()) {
      busy.value = false
      confirming.value = false
    }
  }
}
</script>
