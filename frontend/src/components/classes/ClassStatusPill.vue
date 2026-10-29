<template>
  <StatusPill :label="t(view.key)" :tone="view.tone" :icon="plain ? undefined : view.icon" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ContentStatus } from '@/types/api'
import type { RevisionStatus } from '@/types/classes'
import { contentStatusView } from '@/utils/classes'

/**
 * Review status of a class or lesson, with "Published · Revision: …" while a change to it is pending. `plain` drops the
 * icon for a dot, as in the admin tables next to the platform's own content statuses.
 */
const props = withDefaults(
  defineProps<{ status: ContentStatus; revisionStatus?: RevisionStatus | null; plain?: boolean }>(),
  { revisionStatus: null, plain: false },
)

const { t } = useLocalMessages({ classes: classesMessages })
const view = computed(() => contentStatusView(props.status, props.revisionStatus))
</script>
