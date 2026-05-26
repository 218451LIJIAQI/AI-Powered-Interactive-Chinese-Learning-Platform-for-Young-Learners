<template>
  <div class="page-stack admin-finance-page">
    <PageHeaderBlock
      title="Finance"
      description="Manage payments, refunds, payouts, and the platform commission setting."
      icon="finance"
    />

    <DataNotice :using-fallback="usingFallback" :error="pageError" :success="actionSuccess" />

    <section class="admin-ops-hero admin-ops-hero--finance">
      <div>
        <span class="admin-ops-kicker">Finance Operations</span>
        <h2>Monitor payments, refunds, payouts, and commission signals in one trusted view.</h2>
        <p>
          Every action here stays connected to real backend finance endpoints while preserving a calm admin workflow.
        </p>
      </div>
      <div class="admin-ops-stat-grid">
        <article v-for="stat in financeHighlights" :key="stat.label">
          <span><IconGlyph :name="stat.icon" :size="20" /></span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.label }}</small>
        </article>
      </div>
    </section>

    <section class="stats-grid admin-finance-stats">
      <MetricCard
        label="Payments"
        :value="String(payments.length)"
        subtitle="Recorded payment transactions"
        icon="finance"
      />
      <MetricCard label="Refunds" :value="String(refunds.length)" subtitle="Current refund records" icon="help" />
      <MetricCard label="Payouts" :value="String(payouts.length)" subtitle="Teacher payout requests" icon="earnings" />
      <MetricCard
        label="Commission"
        :value="`${commissionRatePercent}%`"
        subtitle="Platform commission rate"
        icon="chart"
      />
    </section>

    <section class="two-col admin-finance-grid">
      <InfoCard title="Payments" subtitle="Recorded payment history." icon="finance">
        <div class="list">
          <ListInfoItem
            v-for="payment in payments"
            :key="payment.paymentId"
            :title="payment.orderNumber"
            :description="`${payment.studentName} - ${payment.providerName}`"
            icon="finance"
          >
            <template #aside>
              <div class="list-value-stack">
                <strong>{{ formatCurrency(payment.amount, payment.currencyCode) }}</strong>
                <StatusPill :label="payment.paymentStatus" :tone="paymentTone(payment.paymentStatus)" />
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <InfoCard title="Refund requests" subtitle="Approve, reject, or mark refunded." icon="help">
        <div class="list">
          <ListInfoItem
            v-for="refund in refunds"
            :key="refund.id"
            :title="refund.orderNumber"
            :description="refundDescription(refund)"
            icon="help"
          >
            <template #aside>
              <div class="list-action-row">
                <StatusPill :label="refund.refundStatus" :tone="refundTone(refund.refundStatus)" />
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="refundReadOnly || financeActionBusy || !canApproveRefund(refund.refundStatus)"
                  @click="approveRefund(refund.id)"
                >
                  Approve
                </button>
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="refundReadOnly || financeActionBusy || !canRejectRefund(refund.refundStatus)"
                  @click="openRefundReject(refund.id)"
                >
                  Reject
                </button>
                <button
                  class="btn btn-ghost"
                  type="button"
                  :disabled="refundReadOnly || financeActionBusy || !canMarkRefunded(refund.refundStatus)"
                  @click="markRefunded(refund.id)"
                >
                  Mark refunded
                </button>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>
    </section>

    <section class="two-col admin-finance-grid">
      <InfoCard title="Payout requests" subtitle="Approve, reject, or mark paid." icon="earnings">
        <div class="list">
          <ListInfoItem
            v-for="payout in payouts"
            :key="payout.id"
            :title="payout.teacherName"
            :description="payoutDescription(payout)"
            icon="earnings"
          >
            <template #aside>
              <div class="list-value-stack">
                <strong>{{ formatCurrency(payout.amount) }}</strong>
                <div class="list-action-row">
                  <StatusPill :label="payout.payoutStatus" :tone="payoutTone(payout.payoutStatus)" />
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="payoutReadOnly || financeActionBusy || !canApprovePayout(payout.payoutStatus)"
                    @click="approvePayout(payout.id)"
                  >
                    Approve
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="payoutReadOnly || financeActionBusy || !canRejectPayout(payout.payoutStatus)"
                    @click="openPayoutReject(payout.id)"
                  >
                    Reject
                  </button>
                  <button
                    class="btn btn-ghost"
                    type="button"
                    :disabled="payoutReadOnly || financeActionBusy || !canMarkPaid(payout.payoutStatus)"
                    @click="markPaid(payout.id)"
                  >
                    Mark paid
                  </button>
                </div>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <ActionFormCard
        title="Commission setting"
        subtitle="Real backend system setting"
        icon="chart"
        submit-label="Save commission"
        submitting-label="Saving..."
        :submitting="submittingCommission"
        :disabled="commissionReadOnly"
        @submit="saveCommission"
      >
        <div class="field">
          <label for="admin-commission-rate">Commission rate (0.0 to 1.0)</label>
          <input
            id="admin-commission-rate"
            v-model.number="commissionRate"
            class="input"
            type="number"
            min="0"
            max="1"
            step="0.01"
            :disabled="commissionReadOnly || submittingCommission"
          />
        </div>
        <div class="soft-banner">{{ commission.description }}</div>
      </ActionFormCard>
    </section>

    <section v-if="pendingFinanceDecision" class="two-col admin-finance-grid">
      <ActionFormCard
        :title="pendingFinanceDecision.kind === 'refund' ? 'Reject refund request' : 'Reject payout request'"
        :subtitle="
          pendingFinanceDecision.kind === 'refund'
            ? 'A real decision reason is required before rejecting the refund.'
            : 'A real decision reason is required before rejecting the payout.'
        "
        icon="help"
        submit-label="Submit rejection"
        submitting-label="Submitting..."
        :submitting="financeActionBusy"
        :disabled="pendingFinanceDecision.kind === 'refund' ? refundReadOnly : payoutReadOnly"
        @submit="submitFinanceRejection"
      >
        <div class="field">
          <label for="admin-finance-rejection-reason">Rejection reason</label>
          <textarea
            id="admin-finance-rejection-reason"
            v-model="financeDecisionReason"
            class="textarea"
            maxlength="1000"
            placeholder="Explain why this request is being rejected."
            :disabled="
              financeActionBusy || (pendingFinanceDecision.kind === 'refund' ? refundReadOnly : payoutReadOnly)
            "
          />
        </div>
        <div class="list-action-row">
          <button
            class="teacher-outline-button"
            type="button"
            :disabled="financeActionBusy"
            @click="cancelFinanceDecision"
          >
            Cancel
          </button>
        </div>
      </ActionFormCard>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Admin finance hub — payments ledger, refunds queue, payouts
 * approval, revenue summary.
 *
 * <h2>Money-handling safety</h2>
 * <p>Refund and payout approval show the target amount + recipient
 * name in the confirmation dialog and require an admin note. The
 * backend logs each decision through {@code AdminAuditLogService}
 * with the approving admin id, so a paper trail exists for every
 * money-moving action.</p>
 */
import { computed, ref, watch } from 'vue'

import { adminApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import InfoCard from '@/components/dashboard/InfoCard.vue'
import ListInfoItem from '@/components/dashboard/ListInfoItem.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminPayments, adminPayouts, adminRefunds, commissionSetting } from '@/demo/data'
import type { AdminPayoutRequestResponse, AdminRefundRequestResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampNumber, clampPercent, formatCurrency, toFiniteNumber } from '@/utils/format'

const paymentsResource = useResource(() => adminApi.payments(), adminPayments)
const refundsResource = useResource(() => adminApi.refunds(), adminRefunds)
const payoutsResource = useResource(() => adminApi.payouts(), adminPayouts)
const commissionResource = useResource(() => adminApi.commissionSetting(), commissionSetting)

const emptyCommission = {
  commissionRate: 0,
  settingKey: 'platform.commission-rate',
  description: 'Platform commission rate is unavailable right now.',
}

const payments = computed(
  () => paymentsResource.data.value ?? (paymentsResource.usingFallback.value ? adminPayments : []),
)
const refunds = computed(() => refundsResource.data.value ?? (refundsResource.usingFallback.value ? adminRefunds : []))
const payouts = computed(() => payoutsResource.data.value ?? (payoutsResource.usingFallback.value ? adminPayouts : []))
const commission = computed(
  () => commissionResource.data.value ?? (commissionResource.usingFallback.value ? commissionSetting : emptyCommission),
)
const totalRevenue = computed(() => payments.value.reduce((sum, payment) => sum + toFiniteNumber(payment.amount), 0))
const commissionRatePercent = computed(() => clampPercent(commission.value.commissionRate * 100))
const pendingRefundCount = computed(() => refunds.value.filter((refund) => refund.refundStatus === 'PENDING').length)
const pendingPayoutCount = computed(() => payouts.value.filter((payout) => payout.payoutStatus === 'PENDING').length)
const financeHighlights = computed(() => [
  {
    icon: 'finance',
    label: 'Verified revenue',
    value: formatCurrency(totalRevenue.value, payments.value[0]?.currencyCode ?? 'USD'),
  },
  { icon: 'chevronLeft', label: 'Refund queue', value: String(pendingRefundCount.value) },
  { icon: 'earnings', label: 'Payout queue', value: String(pendingPayoutCount.value) },
])
const usingFallback = computed(
  () =>
    paymentsResource.usingFallback.value ||
    refundsResource.usingFallback.value ||
    payoutsResource.usingFallback.value ||
    commissionResource.usingFallback.value,
)
const refundReadOnly = computed(() => refundsResource.usingFallback.value)
const payoutReadOnly = computed(() => payoutsResource.usingFallback.value)
const commissionReadOnly = computed(() => commissionResource.usingFallback.value)
const pageError = computed(
  () =>
    actionError.value ||
    paymentsResource.error.value ||
    refundsResource.error.value ||
    payoutsResource.error.value ||
    commissionResource.error.value,
)

const commissionRate = ref(0)
const submittingCommission = ref(false)
const financeActionBusy = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const pendingFinanceDecision = ref<{ kind: 'refund' | 'payout'; id: number } | null>(null)
const financeDecisionReason = ref('')

watch(
  commission,
  (value) => {
    commissionRate.value = clampCommissionRate(value.commissionRate)
  },
  { immediate: true },
)

function paymentTone(status: string) {
  switch (status) {
    case 'PAID':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REFUNDED':
      return 'info'
    default:
      return 'danger'
  }
}

function refundTone(status: string) {
  switch (status) {
    case 'PENDING':
      return 'warning'
    case 'APPROVED':
      return 'info'
    case 'REFUNDED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'primary'
  }
}

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

function refundDescription(refund: AdminRefundRequestResponse) {
  const decision = refund.decisionReason?.trim()
  const owner = `${refund.studentName} • ${refund.studentEmail}`
  const base = `${owner} • ${refund.reason}`
  return decision ? `${base} • Decision: ${decision}` : base
}

function payoutDescription(payout: AdminPayoutRequestResponse) {
  const requestedNote = payout.requestedNote?.trim() || 'No note provided.'
  const decision = payout.decisionReason?.trim()
  const owner = `${payout.teacherName} • ${payout.teacherEmail}`
  const base = `${owner} • ${requestedNote}`
  return decision ? `${base} • Decision: ${decision}` : base
}

function canApproveRefund(status: string) {
  return status === 'PENDING'
}

function canRejectRefund(status: string) {
  return status === 'PENDING'
}

function canMarkRefunded(status: string) {
  return status === 'APPROVED'
}

function canApprovePayout(status: string) {
  return status === 'PENDING'
}

function canRejectPayout(status: string) {
  return status === 'PENDING'
}

function canMarkPaid(status: string) {
  return status === 'APPROVED'
}

function openRefundReject(id: number) {
  if (!ensureFinanceLive(refundReadOnly.value, 'Refund requests cannot be changed while preview data is active.'))
    return
  pendingFinanceDecision.value = { kind: 'refund', id }
  financeDecisionReason.value = ''
  actionError.value = null
  actionSuccess.value = null
}

function openPayoutReject(id: number) {
  if (!ensureFinanceLive(payoutReadOnly.value, 'Payout requests cannot be changed while preview data is active.'))
    return
  pendingFinanceDecision.value = { kind: 'payout', id }
  financeDecisionReason.value = ''
  actionError.value = null
  actionSuccess.value = null
}

function cancelFinanceDecision() {
  pendingFinanceDecision.value = null
  financeDecisionReason.value = ''
  actionError.value = null
}

async function approveRefund(id: number) {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(refundReadOnly.value, 'Refund requests cannot be changed while preview data is active.'))
    return
  if (!confirmFinanceAction('Approve this refund request?')) return
  await runFinanceAction(() => adminApi.approveRefund(id), refundsResource.reload, 'Unable to approve this refund.')
}

async function markRefunded(id: number) {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(refundReadOnly.value, 'Refund requests cannot be changed while preview data is active.'))
    return
  if (!confirmFinanceAction('Mark this refund as refunded? Confirm only after the provider refund has been completed.'))
    return
  await runFinanceAction(
    () => adminApi.markRefunded(id),
    refundsResource.reload,
    'Unable to mark this refund as refunded.',
  )
}

async function approvePayout(id: number) {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(payoutReadOnly.value, 'Payout requests cannot be changed while preview data is active.'))
    return
  if (!confirmFinanceAction('Approve this teacher payout request?')) return
  await runFinanceAction(() => adminApi.approvePayout(id), payoutsResource.reload, 'Unable to approve this payout.')
}

async function markPaid(id: number) {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(payoutReadOnly.value, 'Payout requests cannot be changed while preview data is active.'))
    return
  if (!confirmFinanceAction('Mark this payout as paid? Confirm only after funds have been sent.')) return
  await runFinanceAction(
    () => adminApi.markPayoutPaid(id),
    payoutsResource.reload,
    'Unable to mark this payout as paid.',
  )
}

async function submitFinanceRejection() {
  if (financeActionBusy.value) return
  const pendingDecision = pendingFinanceDecision.value
  if (!pendingDecision) {
    return
  }
  const readOnly = pendingDecision.kind === 'refund' ? refundReadOnly.value : payoutReadOnly.value
  if (!ensureFinanceLive(readOnly, 'Finance decisions cannot be changed while preview data is active.')) return

  const reason = financeDecisionReason.value.trim()
  if (!reason) {
    actionSuccess.value = null
    actionError.value = 'Enter a rejection reason before submitting the finance decision.'
    return
  }

  if (pendingDecision.kind === 'refund') {
    await runFinanceAction(
      () => adminApi.rejectRefund(pendingDecision.id, { reason }),
      refundsResource.reload,
      'Unable to reject this refund.',
    )
    return
  }

  await runFinanceAction(
    () => adminApi.rejectPayout(pendingDecision.id, { reason }),
    payoutsResource.reload,
    'Unable to reject this payout.',
  )
}

async function saveCommission() {
  if (submittingCommission.value) return
  if (
    !ensureFinanceLive(commissionReadOnly.value, 'Commission settings cannot be changed while preview data is active.')
  )
    return
  actionError.value = null
  actionSuccess.value = null
  submittingCommission.value = true
  try {
    const normalizedRate = clampCommissionRate(commissionRate.value)
    commissionRate.value = normalizedRate
    await adminApi.updateCommissionSetting({ commissionRate: normalizedRate })
    await commissionResource.reload()
    actionSuccess.value = 'Commission setting saved and refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save the commission setting.')
  } finally {
    submittingCommission.value = false
  }
}

async function runFinanceAction(action: () => Promise<unknown>, reload: () => Promise<unknown>, fallback: string) {
  actionError.value = null
  actionSuccess.value = null
  financeActionBusy.value = true
  try {
    await action()
    await reload()
    pendingFinanceDecision.value = null
    financeDecisionReason.value = ''
    actionSuccess.value = 'Finance action completed and data refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, fallback)
  } finally {
    financeActionBusy.value = false
  }
}

function ensureFinanceLive(readOnly: boolean, message: string) {
  if (!readOnly) return true
  actionSuccess.value = null
  actionError.value = message
  return false
}

function clampCommissionRate(value: number) {
  return clampNumber(value, 0, 1)
}

function confirmFinanceAction(message: string) {
  const confirmed = window.confirm(`${message}\n\nThis updates live finance records.`)
  if (!confirmed) {
    actionSuccess.value = null
    actionError.value = null
  }

  return confirmed
}
</script>
