<template>
  <div
    class="content-review-notice"
    :class="`is-${normalized.toLowerCase()}`"
    role="note"
    data-testid="content-review-notice"
  >
    <IconGlyph :name="icon" :size="18" />
    <div class="content-review-notice-copy">
      <span>{{ t(`governance.${noticeKey}`) }}</span>
      <span
        v-if="normalized === 'REJECTED'"
        class="content-review-notice-reason"
        data-testid="content-rejection-reason"
      >
        <strong>{{ t('governance.rejectionReasonLabel') }}:</strong>
        {{ rejectionReason?.trim() || t('governance.rejectionReasonMissing') }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import type { ContentStatus } from '@/types/api'
import { normalizeContentStatus } from '@/utils/contentStatus'

/** Explains to the teacher what the current review state means and what happens on save (D4, D24). */
const props = withDefaults(
  defineProps<{ status: ContentStatus | string | null | undefined; rejectionReason?: string | null }>(),
  { rejectionReason: null },
)

const { t } = useI18n({
  messages: {
    en: { governance: contentGovernanceMessages.en },
    ms: { governance: contentGovernanceMessages.ms },
    'zh-CN': { governance: contentGovernanceMessages['zh-CN'] },
  },
})

const normalized = computed(() => normalizeContentStatus(props.status))
const noticeKey = computed(() => {
  switch (normalized.value) {
    case 'SUBMITTED':
      return 'noticeSubmitted'
    case 'PUBLISHED':
      return 'noticePublished'
    case 'REJECTED':
      return 'noticeRejected'
    case 'ARCHIVED':
      return 'noticeArchived'
    default:
      return 'noticeDraft'
  }
})
const icon = computed(() => {
  switch (normalized.value) {
    case 'SUBMITTED':
      return 'history'
    case 'PUBLISHED':
      return 'status'
    case 'REJECTED':
      return 'alert'
    case 'ARCHIVED':
      return 'safe'
    default:
      return 'content'
  }
})
</script>

<style scoped>
.content-review-notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2-5);
  min-width: 0;
  padding: var(--space-2-5) var(--space-3-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.content-review-notice > .icon-glyph {
  flex: 0 0 auto;
  margin-top: var(--space-0-5);
}

.content-review-notice.is-submitted,
.content-review-notice.is-published {
  border-color: var(--color-warning-border);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.content-review-notice.is-rejected {
  border-color: var(--color-danger-border);
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
}

.content-review-notice-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.content-review-notice-reason {
  color: var(--color-danger-text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
