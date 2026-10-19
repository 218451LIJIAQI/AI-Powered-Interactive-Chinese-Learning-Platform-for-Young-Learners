<template>
  <span
    v-if="normalized || showUnset"
    class="learning-stage-tag"
    :class="`is-${(normalized ?? 'unset').toLowerCase()}`"
    data-testid="learning-stage-tag"
  >
    {{ t(`governance.${learningStageMessageKey(normalized)}`) }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import type { LearningStage } from '@/types/api'
import { learningStageMessageKey, normalizeLearningStage } from '@/utils/learningStage'

const props = withDefaults(defineProps<{ stage?: LearningStage | string | null; showUnset?: boolean }>(), {
  stage: null,
  showUnset: false,
})

const { t } = useI18n({
  messages: {
    en: { governance: contentGovernanceMessages.en },
    ms: { governance: contentGovernanceMessages.ms },
    'zh-CN': { governance: contentGovernanceMessages['zh-CN'] },
  },
})

const normalized = computed(() => normalizeLearningStage(props.stage))
</script>

<style scoped>
.learning-stage-tag {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 24px;
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-snug);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.learning-stage-tag.is-guided {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.learning-stage-tag.is-scaffolded {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.learning-stage-tag.is-independent {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.learning-stage-tag.is-unset {
  background: var(--color-ink-100);
  color: var(--text-secondary);
}
</style>
