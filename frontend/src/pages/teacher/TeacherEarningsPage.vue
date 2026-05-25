<template>
  <div class="page-stack teacher-earnings-page">
    <PageHeaderBlock
      title="Teacher Earnings"
      description="Track teacher earnings, payouts, and platform commission settings that affect teacher revenue."
      icon="earnings"
    />

    <DataNotice :using-fallback="usingFallback" :error="pageError" :success="actionSuccess" />

    <section class="teacher-earnings-hero">
      <div>
        <span>Teacher revenue</span>
        <h2>{{ formatCurrency(summary.availableBalance) }} ready for payout</h2>
        <p>
          Review released earnings, pending balances, and payout requests with a parent-friendly platform finance flow.
        </p>
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
        label="Gross"
        :value="formatCurrency(summary.grossAmount)"
        subtitle="Total teacher sales"
        icon="money"
      />
      <MetricCard
        label="Net"
        :value="formatCurrency(summary.totalNetAmount)"
        subtitle="After commission"
        icon="earnings"
      />
      <MetricCard
        label="Available"
        :value="formatCurrency(summary.availableBalance)"
        subtitle="Ready for payout request"
        icon="finance"
      />
      <MetricCard
        label="Pending"
        :value="formatCurrency(summary.pendingBalance)"
        subtitle="Awaiting release"
        icon="chart"
      />
    </section>

    <section class="two-col teacher-earnings-layout">
      <InfoCard title="Earning entries" subtitle="Revenue records tied to teacher order items." icon="earnings">
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
                <strong>{{ formatCurrency(item.netAmount) }}</strong>
                <span class="muted">Commission {{ formatCurrency(item.commissionAmount) }}</span>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <div class="stack">
        <ActionFormCard
          title="Request payout"
          subtitle="Uses the teacher payout request endpoint."
          icon="finance"
          submit-label="Submit payout request"
          submitting-label="Submitting..."
          :submitting="submittingPayout"
          :disabled="payoutFormDisabled"
          @submit="submitPayout"
        >
          <div v-if="payoutUsingFallback" class="soft-banner">
            Preview data mode keeps payout requests read-only until a live teacher account is connected.
          </div>
          <div class="field">
            <label for="teacher-payout-amount">Amount</label>
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
            <label for="teacher-payout-note">Note</label>
            <textarea
              id="teacher-payout-note"
              v-model="payoutForm.requestedNote"
              class="textarea"
              maxlength="500"
              :disabled="payoutFormDisabled"
            />
          </div>
        </ActionFormCard>

        <InfoCard title="Payout history" subtitle="Teacher payout requests and statuses." icon="finance">
          <div class="list">
            <ListInfoItem
              v-for="item in payouts"
              :key="item.id"
              :title="formatCurrency(item.amount)"
              :description="payoutDescription(item)"
              icon="finance"
            >
              <template #aside>
                <StatusPill :label="item.payoutStatus" :tone="payoutTone(item.payoutStatus)" />
              </template>
            </ListInfoItem>
          </div>
        </InfoCard>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Teacher earnings and payout request surface.
 *
 * <p>Shows earnings ledger, current balance, completed payouts, and
 * the in-page form for requesting a new payout. The page never
 * computes balances client-side — all amounts come from
 * {@code teacherApi.earnings()} which derives them from approved
 * payments minus paid-out payouts (single source of truth).</p>
 */
import { computed, reactive, ref } from 'vue'

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
import type { TeacherPayoutRequestResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatCurrency, formatDateTime } from '@/utils/format'

const earningsResource = useResource(() => teacherApi.earnings(), teacherEarnings)
const summaryResource = useResource(() => teacherApi.earningsSummary(), teacherEarningsSummary)
const payoutsResource = useResource(() => teacherApi.payouts(), teacherPayouts)

const emptySummary = {
  grossAmount: 0,
  totalNetAmount: 0,
  availableBalance: 0,
  pendingBalance: 0,
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
  { icon: 'money', label: 'Net earned', value: formatCurrency(summary.value.totalNetAmount) },
  { icon: 'history', label: 'Pending balance', value: formatCurrency(summary.value.pendingBalance) },
  { icon: 'finance', label: 'Payout requests', value: String(payouts.value.length) },
])
const payoutUsingFallback = computed(() => summaryResource.usingFallback.value || payoutsResource.usingFallback.value)
const payoutFormDisabled = computed(() => payoutUsingFallback.value || submittingPayout.value)
const usingFallback = computed(
  () =>
    earningsResource.usingFallback.value || summaryResource.usingFallback.value || payoutsResource.usingFallback.value,
)
const pageError = computed(
  () => actionError.value || earningsResource.error.value || summaryResource.error.value || payoutsResource.error.value,
)

const payoutForm = reactive({
  amount: 0,
  requestedNote: '',
})
const submittingPayout = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

function payoutTone(status: string) {
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

function payoutDescription(payout: TeacherPayoutRequestResponse) {
  const requestedNote = payout.requestedNote?.trim() || 'No note provided.'
  const decision = payout.decisionReason?.trim()
  return decision ? `${requestedNote} • Decision: ${decision}` : requestedNote
}

async function submitPayout() {
  if (submittingPayout.value) return
  if (payoutUsingFallback.value) {
    actionError.value = 'Payout requests are unavailable while using preview data.'
    actionSuccess.value = null
    return
  }

  if (payoutForm.amount <= 0) {
    actionError.value = 'Enter a payout amount greater than zero.'
    actionSuccess.value = null
    return
  }

  if (payoutForm.amount > summary.value.availableBalance) {
    actionError.value = 'Requested payout exceeds the available balance shown above.'
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
    actionSuccess.value = 'Payout request submitted, balance refreshed, and history updated.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to submit this payout request.')
  } finally {
    submittingPayout.value = false
  }
}
</script>
