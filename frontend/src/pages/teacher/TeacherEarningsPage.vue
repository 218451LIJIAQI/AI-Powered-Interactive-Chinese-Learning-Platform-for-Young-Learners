<template>
  <div class="page-stack teacher-earnings-page">
    <PageHeaderBlock
      :title="t('teacherEarnings.title')"
      :description="t('teacherEarnings.description')"
      icon="earnings"
    />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <section class="teacher-earnings-hero">
      <div>
        <span>{{ t('teacherEarnings.revenue') }}</span>
        <h2>
          {{
            t('teacherEarnings.readyAmount', {
              amount: formatCurrency(summary.availableBalance, summary.currencyCode),
            })
          }}
        </h2>
        <p>{{ t('teacherEarnings.heroHint') }}</p>
      </div>
      <div class="teacher-earnings-hero-grid">
        <article v-for="item in earningHighlights" :key="item.label">
          <span><IconGlyph :name="item.icon" :size="20" /></span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.label }}</small>
        </article>
      </div>
    </section>

    <section class="stats-grid teacher-earnings-stats">
      <MetricCard
        :label="t('teacherEarnings.gross')"
        :value="formatCurrency(summary.grossAmount, summary.currencyCode)"
        :subtitle="t('teacherEarnings.grossHint')"
        icon="money"
      />
      <MetricCard
        :label="t('teacherEarnings.net')"
        :value="formatCurrency(summary.totalNetAmount, summary.currencyCode)"
        :subtitle="t('teacherEarnings.netHint')"
        icon="earnings"
      />
      <MetricCard
        :label="t('teacherEarnings.available')"
        :value="formatCurrency(summary.availableBalance, summary.currencyCode)"
        :subtitle="t('teacherEarnings.availableHint')"
        icon="finance"
      />
      <MetricCard
        :label="t('teacherEarnings.pending')"
        :value="formatCurrency(summary.pendingBalance, summary.currencyCode)"
        :subtitle="t('teacherEarnings.pendingHint')"
        icon="chart"
      />
    </section>

    <section class="two-col teacher-earnings-layout">
      <InfoCard
        :title="t('teacherEarnings.earningEntries')"
        :subtitle="t('teacherEarnings.earningEntriesHint')"
        icon="earnings"
      >
        <div class="list">
          <ListInfoItem
            v-for="item in earnings"
            :key="item.id"
            :title="item.itemTitle"
            :description="formatDateTime(item.createdAt)"
            icon="earnings"
          >
            <template #meta>
              <span class="tag">{{ item.earningStatus }}</span>
            </template>
            <template #aside>
              <div class="list-value-stack">
                <strong>{{ formatCurrency(item.netAmount, item.currencyCode) }}</strong>
                <span class="muted">{{
                  t('teacherEarnings.commission', {
                    amount: formatCurrency(item.commissionAmount, item.currencyCode),
                  })
                }}</span>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <div class="stack">
        <ActionFormCard
          :title="t('teacherEarnings.requestPayout')"
          :subtitle="t('teacherEarnings.requestPayoutHint')"
          icon="finance"
          :submit-label="t('teacherEarnings.submit')"
          :submitting-label="t('teacherEarnings.submitting')"
          :submitting="submittingPayout"
          :disabled="payoutFormDisabled"
          @submit="submitPayout"
        >
          <div v-if="payoutUsingFallback" class="soft-banner">
            {{ t('teacherEarnings.previewReadOnly') }}
          </div>
          <div class="field">
            <label for="teacher-payout-amount">{{ t('teacherEarnings.amount') }}</label>
            <input
              id="teacher-payout-amount"
              v-model.number="payoutForm.amount"
              class="input"
              type="number"
              min="0.01"
              :max="summary.availableBalance"
              step="0.01"
              :disabled="payoutFormDisabled"
            />
          </div>
          <div class="field">
            <label for="teacher-payout-note">{{ t('teacherEarnings.note') }}</label>
            <textarea
              id="teacher-payout-note"
              v-model="payoutForm.requestedNote"
              class="textarea"
              maxlength="500"
              :disabled="payoutFormDisabled"
            />
          </div>
        </ActionFormCard>

        <InfoCard
          :title="t('teacherEarnings.payoutHistory')"
          :subtitle="t('teacherEarnings.payoutHistoryHint')"
          icon="finance"
        >
          <div class="list">
            <ListInfoItem
              v-for="item in payouts"
              :key="item.id"
              :title="formatCurrency(item.amount, item.currencyCode)"
              :description="payoutDescription(item)"
              icon="finance"
            >
              <template #aside>
                <StatusPill :label="payoutStatusLabel(item.payoutStatus)" :tone="payoutTone(item.payoutStatus)" />
              </template>
            </ListInfoItem>
          </div>
        </InfoCard>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import InfoCard from '@/components/dashboard/InfoCard.vue'
import ListInfoItem from '@/components/dashboard/ListInfoItem.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { teacherEarnings, teacherEarningsSummary, teacherPayouts } from '@/demo/data'
import { teacherEarningsMessages } from '@/plugins/teacherEarningsMessages'
import type { TeacherPayoutRequestResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatCurrency, formatDateTime } from '@/utils/format'

type PayoutTone = 'warning' | 'info' | 'success' | 'danger' | 'primary'

const { t } = useI18n({
  messages: {
    en: { teacherEarnings: teacherEarningsMessages.en },
    ms: { teacherEarnings: teacherEarningsMessages.ms },
    zh: { teacherEarnings: teacherEarningsMessages.zh },
  },
})

const earningsResource = useResource(() => teacherApi.earnings(), teacherEarnings)
const summaryResource = useResource(() => teacherApi.earningsSummary(), teacherEarningsSummary)
const payoutsResource = useResource(() => teacherApi.payouts(), teacherPayouts)

const emptySummary = {
  grossAmount: 0,
  totalNetAmount: 0,
  availableBalance: 0,
  pendingBalance: 0,
  currencyCode: 'MYR',
}
const earnings = computed(
  () => earningsResource.data.value ?? (earningsResource.usingFallback.value ? teacherEarnings : []),
)
const summary = computed(
  () => summaryResource.data.value ?? (summaryResource.usingFallback.value ? teacherEarningsSummary : emptySummary),
)
const payouts = computed(
  () => payoutsResource.data.value ?? (payoutsResource.usingFallback.value ? teacherPayouts : []),
)
const earningHighlights = computed(() => [
  {
    icon: 'money',
    label: t('teacherEarnings.netEarned'),
    value: formatCurrency(summary.value.totalNetAmount, summary.value.currencyCode),
  },
  {
    icon: 'history',
    label: t('teacherEarnings.pendingBalance'),
    value: formatCurrency(summary.value.pendingBalance, summary.value.currencyCode),
  },
  { icon: 'finance', label: t('teacherEarnings.payoutRequests'), value: String(payouts.value.length) },
])
const payoutUsingFallback = computed(() => summaryResource.usingFallback.value || payoutsResource.usingFallback.value)
const payoutFormDisabled = computed(() => payoutUsingFallback.value || submittingPayout.value)
const usingFallback = computed(
  () =>
    earningsResource.usingFallback.value || summaryResource.usingFallback.value || payoutsResource.usingFallback.value,
)
const pageError = computed(
  () => earningsResource.error.value || summaryResource.error.value || payoutsResource.error.value,
)
const noticeError = computed(() => actionError.value || pageError.value)

const payoutForm = reactive({
  amount: 0,
  requestedNote: '',
})
const submittingPayout = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

function payoutTone(status: string): PayoutTone {
  switch (status) {
    case 'PENDING':
      return 'warning'
    case 'APPROVED':
      return 'info'
    case 'PAID':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'primary'
  }
}

function payoutStatusLabel(status: string): string {
  const keys: Record<string, string> = {
    PENDING: 'statusPending',
    APPROVED: 'statusApproved',
    PAID: 'statusPaid',
    REJECTED: 'statusRejected',
  }
  const key = keys[status]
  return key ? t(`teacherEarnings.${key}`) : status
}

function payoutDescription(payout: TeacherPayoutRequestResponse): string {
  const requestedNote = payout.requestedNote?.trim() || t('teacherEarnings.noNote')
  const decision = payout.decisionReason?.trim()
  return decision ? `${requestedNote} • ${t('teacherEarnings.decision', { reason: decision })}` : requestedNote
}

async function submitPayout(): Promise<void> {
  if (submittingPayout.value) return
  if (payoutUsingFallback.value) {
    actionError.value = t('teacherEarnings.previewUnavailable')
    actionSuccess.value = null
    return
  }

  if (payoutForm.amount <= 0) {
    actionError.value = t('teacherEarnings.invalidAmount')
    actionSuccess.value = null
    return
  }

  if (payoutForm.amount > summary.value.availableBalance) {
    actionError.value = t('teacherEarnings.exceedsBalance')
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingPayout.value = true
  try {
    await teacherApi.createPayoutRequest({
      amount: payoutForm.amount,
      requestedNote: payoutForm.requestedNote.trim() || undefined,
    })
    await payoutsResource.reload()
    await summaryResource.reload()
    payoutForm.amount = 0
    payoutForm.requestedNote = ''
    actionSuccess.value = t('teacherEarnings.submitted')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherEarnings.submitError'))
  } finally {
    submittingPayout.value = false
  }
}
</script>
