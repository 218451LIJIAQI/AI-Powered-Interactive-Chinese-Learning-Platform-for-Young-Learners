<template>
  <StatusPill :label="t(view.key)" :tone="view.tone" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { ContentStatus } from '@/types/api'
import type { RevisionStatus } from '@/types/classes'

/**
 * The status of a class or lesson as its teacher reads it: changes requested is the one that needs them; what waits
 * in review is a normal state; a pending update of published content shows the update's status, not "Published".
 */
const props = withDefaults(defineProps<{ status: ContentStatus; revisionStatus?: RevisionStatus | null }>(), {
  revisionStatus: null,
})

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages })

const STATUSES: Record<ContentStatus, { key: string; tone: PillTone }> = {
  DRAFT: { key: 'teacherClasses.status.DRAFT', tone: 'outline' },
  SUBMITTED: { key: 'teacherClasses.status.SUBMITTED', tone: 'info' },
  PUBLISHED: { key: 'teacherClasses.status.PUBLISHED', tone: 'success' },
  REJECTED: { key: 'teacherClasses.status.REJECTED', tone: 'strong' },
  ARCHIVED: { key: 'teacherClasses.status.ARCHIVED', tone: 'outline' },
}

const REVISIONS: Record<RevisionStatus, { key: string; tone: PillTone }> = {
  DRAFT: { key: 'teacherClasses.status.revisionDraft', tone: 'outline' },
  SUBMITTED: STATUSES.SUBMITTED,
  REJECTED: STATUSES.REJECTED,
}

const view = computed(() =>
  props.status === 'PUBLISHED' && props.revisionStatus ? REVISIONS[props.revisionStatus] : STATUSES[props.status],
)
</script>
