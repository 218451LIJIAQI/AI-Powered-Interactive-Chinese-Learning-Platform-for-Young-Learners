<template>
  <div ref="panelRef" class="admin-users-side" tabindex="-1">
    <StatePanel
      v-if="loading || selectedId === null"
      tone="loading"
      :title="t('adminUsers.detailLoading')"
      :message="t('adminUsers.detailLoadingHint')"
    />
    <StatePanel
      v-else-if="error || !user"
      tone="error"
      icon="alert"
      :title="t('adminUsers.detailError')"
      :message="error ?? ''"
      @retry="emit('retry')"
    />
    <template v-else>
      <section class="panel admin-detail" aria-labelledby="admin-users-detail-name" data-testid="admin-users-detail">
        <header class="admin-person">
          <PortraitAvatar :size="56" :name="user.fullName" />
          <div class="admin-detail-heading">
            <h2 id="admin-users-detail-name" class="admin-detail-title">{{ user.fullName }}</h2>
            <p class="admin-meta">{{ user.email }}</p>
            <div class="admin-tags">
              <span v-if="user.role" class="tag is-outline">{{ t(ROLE_KEYS[user.role]) }}</span>
              <StatusPill
                :label="user.active ? t('adminUsers.accountActive') : t('adminUsers.accountDeactivated')"
                :tone="accountTone(user.active)"
              />
            </div>
          </div>
        </header>

        <!-- The header has the role and the account's state; this section adds what only the account says. -->
        <section class="admin-section admin-ruled" aria-labelledby="admin-users-account-title">
          <h3 id="admin-users-account-title" class="admin-section-title">{{ t('adminUsers.accountLabel') }}</h3>
          <dl class="kv-list">
            <template v-if="user.deactivation">
              <dt>{{ t('adminUsers.reason') }}</dt>
              <dd class="admin-users-reason" data-testid="admin-users-deactivation">
                <span v-if="user.deactivation.reason">{{ user.deactivation.reason }}</span>
                <span class="admin-meta">{{ deactivationLine }}</span>
              </dd>
            </template>
            <dt>{{ t('adminUsers.joined') }}</dt>
            <dd>{{ formatDate(user.joinedAt) }}</dd>
          </dl>
        </section>

        <div v-if="user.role" class="admin-section admin-ruled">
          <button
            v-if="!user.self && user.active"
            class="btn btn-secondary btn-block"
            type="button"
            :disabled="saving"
            data-testid="admin-users-deactivate"
            @click="emit('deactivate')"
          >
            <IconGlyph name="lock" :size="16" />
            <span>{{ t('adminUsers.deactivate') }}</span>
          </button>
          <!-- Easy to take back (Deactivate again), so it runs at once: no question, no reason. -->
          <button
            v-else-if="!user.self"
            class="btn btn-primary btn-block"
            type="button"
            :disabled="saving"
            :aria-busy="saving || undefined"
            data-testid="admin-users-reactivate"
            @click="emit('reactivate')"
          >
            <IconGlyph name="unlock" :size="16" />
            <span>{{ t('adminUsers.reactivate') }}</span>
          </button>
          <p class="admin-meta" data-testid="admin-users-access-hint">
            {{
              user.self
                ? t('adminUsers.selfNote')
                : user.active
                  ? t('adminUsers.deactivateHint')
                  : t('adminUsers.reactivateHint')
            }}
          </p>
          <p v-if="accessError" class="field-error" role="alert" data-testid="admin-users-access-error">
            {{ accessError }}
          </p>
        </div>
      </section>

      <section class="panel admin-detail" aria-labelledby="admin-users-audit-title">
        <div class="admin-section-head">
          <h2 id="admin-users-audit-title" class="card-title">{{ t('adminUsers.recentAudit') }}</h2>
          <RouterLink
            class="btn btn-ghost btn-sm admin-link-action"
            :to="{ name: 'admin-audit-log', query: { user: user.id } }"
            data-testid="admin-users-audit-all"
            :aria-label="t('adminUsers.viewAllAudit', { name: user.fullName })"
          >
            {{ t('adminUsers.viewAll') }}
            <IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </div>
        <ol v-if="user.recentAudit.length" class="row-list">
          <li v-for="entry in user.recentAudit" :key="entry.id">
            <RouterLink class="admin-home-row" :to="{ name: 'admin-audit-log', query: { entry: entry.id } }">
              <span class="row-list-copy">
                <span class="row-list-title">{{ auditActionLabel(t, entry.actionType) }}</span>
                <span class="row-list-meta">{{ auditMeta(entry, user) }}</span>
                <span v-if="entry.actionType === 'ACCOUNT_CREATED' && user.role === 'STUDENT'" class="row-list-meta">
                  {{ t('adminUsers.signedUpAsLearner') }}
                </span>
              </span>
              <IconGlyph class="admin-row-chevron" name="chevronRight" :size="16" />
            </RouterLink>
          </li>
        </ol>
        <p v-else class="admin-meta">{{ t('adminUsers.noAudit') }}</p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { accountTone } from '@/components/admin/adminDisplay'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { AdminUserDetail, AuditEntry } from '@/types/ops'
import { auditActionLabel } from '@/utils/auditActions'
import { formatDate, formatDateTime } from '@/utils/format'
import { ROLE_KEYS } from '@/utils/opsLabels'

const props = defineProps<{
  selectedId: number | null
  user: AdminUserDetail | null
  loading: boolean
  error: string | null
  saving: boolean
  accessError: string | null
}>()
const emit = defineEmits<{ retry: []; deactivate: []; reactivate: [] }>()

const { t } = useLocalMessages({
  adminUsers: adminUsersMessages,
  auditActions: auditActionMessages,
})

const panelRef = ref<HTMLElement | null>(null)

function focus(): void {
  const side = panelRef.value
  // Under 1200 the person sits below the table: focusing them there scrolls them into view.
  side?.focus({ preventScroll: side.getBoundingClientRect().top < window.innerHeight })
}

defineExpose({ focus })

const deactivationLine = computed(() => {
  const deactivation = props.user?.deactivation
  if (!deactivation) return ''
  return deactivation.actorName
    ? t('adminUsers.deactivatedBy', { date: formatDateTime(deactivation.at), actor: deactivation.actorName })
    : t('adminUsers.deactivatedOn', { date: formatDateTime(deactivation.at) })
})

/** "18 May, 2:00 PM · olivia@example.com": who acted only when it was someone else; the account's own events need no name. */
function auditMeta(entry: AuditEntry, user: AdminUserDetail): string {
  const when = formatDateTime(entry.createdAt)
  return entry.actorEmail && entry.actorEmail !== user.email ? `${when} · ${entry.actorEmail}` : when
}
</script>
