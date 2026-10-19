<template>
  <span class="content-status-badge" data-testid="content-status-badge">
    <StatusPill :label="label" :tone="tone" />
    <span v-if="showRejectionReason" class="content-status-reason" role="note" data-testid="content-rejection-reason">
      <strong>{{ t('governance.rejectionReasonLabel') }}:</strong>
      {{ rejectionReason?.trim() || t('governance.rejectionReasonMissing') }}
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import StatusPill from '@/components/common/StatusPill.vue'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import type { ContentStatus } from '@/types/api'
import { contentStatusMessageKey, contentStatusTone, normalizeContentStatus } from '@/utils/contentStatus'

const props = withDefaults(
  defineProps<{
    status: ContentStatus | string | null | undefined
    rejectionReason?: string | null
    /** Shows the reviewer reason next to REJECTED items (default true). */
    showReason?: boolean
  }>(),
  { rejectionReason: null, showReason: true },
)

const { t } = useI18n({
  messages: {
    en: { governance: contentGovernanceMessages.en },
    ms: { governance: contentGovernanceMessages.ms },
    'zh-CN': { governance: contentGovernanceMessages['zh-CN'] },
  },
})

const label = computed(() => t(`governance.${contentStatusMessageKey(props.status)}`))
const tone = computed(() => contentStatusTone(props.status))
const showRejectionReason = computed(() => props.showReason && normalizeContentStatus(props.status) === 'REJECTED')
</script>

<style scoped>
.content-status-badge {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-1-5);
  min-width: 0;
}

.content-status-reason {
  display: block;
  max-width: 360px;
  padding: var(--space-1-5) var(--space-2-5);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
