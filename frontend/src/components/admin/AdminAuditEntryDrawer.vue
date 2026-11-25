<template>
  <AppOverlay
    :open="open"
    variant="drawer"
    :title="
      entry ? auditActionLabel(t, entry.actionType) : t(error ? 'adminAudit.entryError' : 'adminAudit.entryLoading')
    "
    :subtitle="
      entry
        ? t('adminAudit.entryMeta', {
            category: auditCategoryLabel(t, entry.category),
            date: formatDateTime(entry.createdAt),
          })
        : ''
    "
    test-id="admin-audit-drawer"
    @close="emit('close')"
  >
    <DataNotice v-if="error" :error="error" @retry="emit('retry')" />
    <StatePanel v-else-if="!entry" tone="loading" :title="t('adminAudit.entryLoadingHint')" />
    <template v-else>
      <section class="admin-section" aria-labelledby="admin-audit-what-title">
        <h3 id="admin-audit-what-title" class="admin-section-title">{{ t('adminAudit.whatHappened') }}</h3>
        <p class="admin-audit-what">{{ whatHappened }}</p>
      </section>
      <dl class="kv-list">
        <dt>{{ t('adminAudit.actor') }}</dt>
        <dd>
          <span class="admin-cell-title">
            <strong>{{ entry.actorName ?? entry.actorEmail ?? t('adminAudit.systemActor') }}</strong>
            <span v-if="entry.actorName && entry.actorEmail">{{ entry.actorEmail }}</span>
          </span>
        </dd>
        <template v-if="entry.targetLabel">
          <dt>{{ t('adminAudit.kvTarget') }}</dt>
          <dd>{{ entry.targetLabel }}</dd>
        </template>
        <template v-if="entry.reason">
          <dt>{{ t('adminAudit.kvReason') }}</dt>
          <dd data-testid="admin-audit-reason">{{ entry.reason }}</dd>
        </template>
        <dt>{{ t('adminAudit.time') }}</dt>
        <dd>{{ formatFullDateTime(entry.createdAt) }}</dd>
        <dt>{{ t('adminAudit.category') }}</dt>
        <dd>{{ auditCategoryLabel(t, entry.category) }}</dd>
      </dl>
      <p class="callout is-compact">
        <IconGlyph name="lock" :size="16" />
        <span>{{ t('adminAudit.immutable') }}</span>
      </p>
    </template>
    <template v-if="entry?.targetUserId" #footer>
      <RouterLink
        class="btn btn-secondary"
        :to="{ name: 'admin-users', query: { user: entry.targetUserId } }"
        data-testid="admin-audit-open-target"
      >
        {{
          t('adminAudit.openTarget', {
            target: entry.targetLabel || t('adminAudit.userUnknown', { id: entry.targetUserId }),
          })
        }}
        <IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import AppOverlay from '@/components/common/AppOverlay.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminAuditMessages } from '@/plugins/adminAuditMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { AuditEntry } from '@/types/ops'
import { auditActionLabel, auditCategoryLabel } from '@/utils/auditActions'
import { formatDateTime, formatFullDateTime } from '@/utils/format'

const props = defineProps<{
  open: boolean
  entry: AuditEntry | null
  error: string | null
}>()
const emit = defineEmits<{ close: []; retry: [] }>()

const { t } = useLocalMessages({ adminAudit: adminAuditMessages, auditActions: auditActionMessages })

const whatHappened = computed(() => {
  const current = props.entry
  if (!current) return ''
  const description = current.description || auditActionLabel(t, current.actionType)
  const actorName = current.actorName ?? current.actorEmail
  return actorName ? t('adminAudit.happenedBy', { actor: actorName, description }) : description
})
</script>
