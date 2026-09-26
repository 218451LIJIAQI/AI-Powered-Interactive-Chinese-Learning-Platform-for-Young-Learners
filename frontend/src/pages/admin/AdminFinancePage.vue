<template>
  <div class="page-stack admin-finance-page">
    <PageHeaderBlock :title="t('adminFinance.title')" :description="t('adminFinance.description')" icon="finance" />

    <DataNotice :using-fallback="usingFallback" :error="pageError" :success="actionSuccess" />

    <section class="admin-ops-hero admin-ops-hero--finance">
      <div>
        <span class="admin-ops-kicker">{{ t('adminFinance.operations') }}</span>
        <h2>{{ t('adminFinance.heroTitle') }}</h2>
        <p>{{ t('adminFinance.heroHint') }}</p>
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
        :label="t('adminFinance.payments')"
        :value="String(payments.length)"
        :subtitle="t('adminFinance.paymentsHint')"
        icon="finance"
      />
      <MetricCard
        :label="t('adminFinance.refunds')"
        :value="String(refunds.length)"
        :subtitle="t('adminFinance.refundsHint')"
        icon="help"
      />
      <MetricCard
        :label="t('adminFinance.payouts')"
        :value="String(payouts.length)"
        :subtitle="t('adminFinance.payoutsHint')"
        icon="earnings"
      />
      <MetricCard
        :label="t('adminFinance.commission')"
        :value="`${commissionRatePercent}%`"
        :subtitle="t('adminFinance.commissionHint')"
        icon="chart"
      />
    </section>

    <section class="two-col admin-finance-grid">
      <InfoCard :title="t('adminFinance.payments')" :subtitle="t('adminFinance.paymentHistory')" icon="finance">
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
                <StatusPill
                  :label="financeStatusLabel(payment.paymentStatus)"
                  :tone="paymentTone(payment.paymentStatus)"
                />
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <InfoCard :title="t('adminFinance.refundRequests')" :subtitle="t('adminFinance.refundActions')" icon="help">
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
                <StatusPill :label="financeStatusLabel(refund.refundStatus)" :tone="refundTone(refund.refundStatus)" />
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="refundReadOnly || financeActionBusy || !canApproveRefund(refund.refundStatus)"
                  @click="approveRefund(refund.id)"
                >
                  {{ t('adminFinance.approve') }}
                </button>
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="refundReadOnly || financeActionBusy || !canRejectRefund(refund.refundStatus)"
                  @click="openRefundReject(refund.id)"
                >
                  {{ t('adminFinance.reject') }}
                </button>
                <button
                  class="btn btn-ghost"
                  type="button"
                  :disabled="refundReadOnly || financeActionBusy || !canMarkRefunded(refund.refundStatus)"
                  @click="markRefunded(refund.id)"
                >
                  {{ t('adminFinance.markRefunded') }}
                </button>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>
    </section>

    <section class="two-col admin-finance-grid">
      <InfoCard :title="t('adminFinance.payoutRequests')" :subtitle="t('adminFinance.payoutActions')" icon="earnings">
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
                <strong>{{ formatCurrency(payout.amount, payout.currencyCode) }}</strong>
                <div class="list-action-row">
                  <StatusPill
                    :label="financeStatusLabel(payout.payoutStatus)"
                    :tone="payoutTone(payout.payoutStatus)"
                  />
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="payoutReadOnly || financeActionBusy || !canApprovePayout(payout.payoutStatus)"
                    @click="approvePayout(payout.id)"
                  >
                    {{ t('adminFinance.approve') }}
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="payoutReadOnly || financeActionBusy || !canRejectPayout(payout.payoutStatus)"
                    @click="openPayoutReject(payout.id)"
                  >
                    {{ t('adminFinance.reject') }}
                  </button>
                  <button
                    class="btn btn-ghost"
                    type="button"
                    :disabled="payoutReadOnly || financeActionBusy || !canMarkPaid(payout.payoutStatus)"
                    @click="markPaid(payout.id)"
                  >
                    {{ t('adminFinance.markPaid') }}
                  </button>
                </div>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <ActionFormCard
        :title="t('adminFinance.commissionSetting')"
        :subtitle="t('adminFinance.settingHint')"
        icon="chart"
        :submit-label="t('adminFinance.saveCommission')"
        :submitting-label="t('adminFinance.saving')"
        :submitting="submittingCommission"
        :disabled="commissionReadOnly"
        @submit="saveCommission"
      >
        <div class="field">
          <label for="admin-commission-rate">{{ t('adminFinance.commissionRate') }}</label>
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
        :title="financeRejectionTitle"
        :subtitle="financeRejectionSubtitle"
        icon="help"
        :submit-label="t('adminFinance.submitRejection')"
        :submitting-label="t('adminFinance.submitting')"
        :submitting="financeActionBusy"
        :disabled="financeRejectionReadOnly"
        @submit="submitFinanceRejection"
      >
        <div class="field">
          <label for="admin-finance-rejection-reason">{{ t('adminFinance.rejectionReason') }}</label>
          <textarea
            id="admin-finance-rejection-reason"
            v-model="financeDecisionReason"
            class="textarea"
            maxlength="1000"
            :placeholder="t('adminFinance.rejectionPlaceholder')"
            :disabled="financeActionBusy || financeRejectionReadOnly"
          />
        </div>
        <div class="list-action-row">
          <button
            class="teacher-outline-button"
            type="button"
            :disabled="financeActionBusy"
            @click="cancelFinanceDecision"
          >
            {{ t('adminFinance.cancel') }}
          </button>
        </div>
      </ActionFormCard>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

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
import { adminFinanceMessages } from '@/plugins/adminFinanceMessages'
import type { AdminPayoutRequestResponse, AdminRefundRequestResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampNumber, clampPercent, formatCurrency, toFiniteNumber } from '@/utils/format'

const { t } = useI18n({
  messages: {
    en: { adminFinance: adminFinanceMessages.en },
    ms: { adminFinance: adminFinanceMessages.ms },
    zh: { adminFinance: adminFinanceMessages.zh },
  },
})

type PillTone = 'danger' | 'info' | 'primary' | 'success' | 'warning'

const paymentsResource = useResource(() => adminApi.payments(), adminPayments)
const refundsResource = useResource(() => adminApi.refunds(), adminRefunds)
const payoutsResource = useResource(() => adminApi.payouts(), adminPayouts)
const commissionResource = useResource(() => adminApi.commissionSetting(), commissionSetting)

const emptyCommission = {
  commissionRate: 0,
  settingKey: 'platform.commission-rate',
  description: t('adminFinance.unavailable'),
}

const payments = computed(
  () => paymentsResource.data.value ?? (paymentsResource.usingFallback.value ? adminPayments : []),
)
const refunds = computed(() => refundsResource.data.value ?? (refundsResource.usingFallback.value ? adminRefunds : []))
const payouts = computed(() => payoutsResource.data.value ?? (payoutsResource.usingFallback.value ? adminPayouts : []))
const commission = computed(
  () => commissionResource.data.value ?? (commissionResource.usingFallback.value ? commissionSetting : emptyCommission),
)
const settledPayments = computed(() => payments.value.filter((payment) => payment.paymentStatus === 'PAID'))
const totalRevenue = computed(() =>
  settledPayments.value.reduce((sum, payment) => sum + toFiniteNumber(payment.amount), 0),
)
const commissionRatePercent = computed(() => clampPercent(commission.value.commissionRate * 100))
const pendingRefundCount = computed(() => refunds.value.filter((refund) => refund.refundStatus === 'PENDING').length)
const pendingPayoutCount = computed(() => payouts.value.filter((payout) => payout.payoutStatus === 'PENDING').length)
const financeHighlights = computed(() => [
  {
    icon: 'finance',
    label: t('adminFinance.visiblePaid'),
    value: formatCurrency(
      totalRevenue.value,
      settledPayments.value[0]?.currencyCode ??
        payments.value[0]?.currencyCode ??
        payouts.value[0]?.currencyCode ??
        'MYR',
    ),
  },
  { icon: 'chevronLeft', label: t('adminFinance.refundQueue'), value: String(pendingRefundCount.value) },
  { icon: 'earnings', label: t('adminFinance.payoutQueue'), value: String(pendingPayoutCount.value) },
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
const financeRejectionIsRefund = computed(() => pendingFinanceDecision.value?.kind === 'refund')
const financeRejectionTitle = computed(() =>
  financeRejectionIsRefund.value ? t('adminFinance.rejectRefund') : t('adminFinance.rejectPayout'),
)
const financeRejectionSubtitle = computed(() =>
  financeRejectionIsRefund.value ? t('adminFinance.rejectRefundHint') : t('adminFinance.rejectPayoutHint'),
)
const financeRejectionReadOnly = computed(() =>
  financeRejectionIsRefund.value ? refundReadOnly.value : payoutReadOnly.value,
)

watch(
  commission,
  (value) => {
    commissionRate.value = clampCommissionRate(value.commissionRate)
  },
  { immediate: true },
)

function paymentTone(status: string): PillTone {
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

function refundTone(status: string): PillTone {
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

function payoutTone(status: string): PillTone {
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

function financeStatusLabel(status: string): string {
  const keys: Record<string, string> = {
    PENDING: 'statusPending',
    APPROVED: 'statusApproved',
    REJECTED: 'statusRejected',
    REFUNDED: 'statusRefunded',
    PAID: 'statusPaid',
    FAILED: 'statusFailed',
    CANCELLED: 'statusCancelled',
    PROCESSING: 'statusProcessing',
  }
  const key = keys[status]
  return key ? t(`adminFinance.${key}`) : status
}

function refundDescription(refund: AdminRefundRequestResponse): string {
  const decision = refund.decisionReason?.trim()
  const owner = `${refund.studentName} • ${refund.studentEmail}`
  const base = `${owner} • ${refund.reason}`
  return decision ? `${base} • ${t('adminFinance.decision', { reason: decision })}` : base
}

function payoutDescription(payout: AdminPayoutRequestResponse): string {
  const requestedNote = payout.requestedNote?.trim() || t('adminFinance.noNote')
  const decision = payout.decisionReason?.trim()
  const owner = `${payout.teacherName} • ${payout.teacherEmail}`
  const base = `${owner} • ${requestedNote}`
  return decision ? `${base} • ${t('adminFinance.decision', { reason: decision })}` : base
}

function canApproveRefund(status: string): boolean {
  return status === 'PENDING'
}

function canRejectRefund(status: string): boolean {
  return status === 'PENDING'
}

function canMarkRefunded(status: string): boolean {
  return status === 'APPROVED'
}

function canApprovePayout(status: string): boolean {
  return status === 'PENDING'
}

function canRejectPayout(status: string): boolean {
  return status === 'PENDING'
}

function canMarkPaid(status: string): boolean {
  return status === 'APPROVED'
}

function openRefundReject(id: number): void {
  if (!ensureFinanceLive(refundReadOnly.value, t('adminFinance.refundPreview'))) return
  pendingFinanceDecision.value = { kind: 'refund', id }
  financeDecisionReason.value = ''
  actionError.value = null
  actionSuccess.value = null
}

function openPayoutReject(id: number): void {
  if (!ensureFinanceLive(payoutReadOnly.value, t('adminFinance.payoutPreview'))) return
  pendingFinanceDecision.value = { kind: 'payout', id }
  financeDecisionReason.value = ''
  actionError.value = null
  actionSuccess.value = null
}

function cancelFinanceDecision(): void {
  pendingFinanceDecision.value = null
  financeDecisionReason.value = ''
  actionError.value = null
}

async function approveRefund(id: number): Promise<void> {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(refundReadOnly.value, t('adminFinance.refundPreview'))) return
  if (!confirmFinanceAction(t('adminFinance.confirmRefundApprove'))) return
  await runFinanceAction(() => adminApi.approveRefund(id), refundsResource.reload, t('adminFinance.approveRefundError'))
}

async function markRefunded(id: number): Promise<void> {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(refundReadOnly.value, t('adminFinance.refundPreview'))) return
  if (!confirmFinanceAction(t('adminFinance.confirmRefunded'))) return
  await runFinanceAction(() => adminApi.markRefunded(id), refundsResource.reload, t('adminFinance.markRefundedError'))
}

async function approvePayout(id: number): Promise<void> {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(payoutReadOnly.value, t('adminFinance.payoutPreview'))) return
  if (!confirmFinanceAction(t('adminFinance.confirmPayoutApprove'))) return
  await runFinanceAction(() => adminApi.approvePayout(id), payoutsResource.reload, t('adminFinance.approvePayoutError'))
}

async function markPaid(id: number): Promise<void> {
  if (financeActionBusy.value) return
  if (!ensureFinanceLive(payoutReadOnly.value, t('adminFinance.payoutPreview'))) return
  if (!confirmFinanceAction(t('adminFinance.confirmPaid'))) return
  await runFinanceAction(() => adminApi.markPayoutPaid(id), payoutsResource.reload, t('adminFinance.markPaidError'))
}

async function submitFinanceRejection(): Promise<void> {
  if (financeActionBusy.value) return
  const pendingDecision = pendingFinanceDecision.value
  if (!pendingDecision) {
    return
  }
  const readOnly = pendingDecision.kind === 'refund' ? refundReadOnly.value : payoutReadOnly.value
  if (!ensureFinanceLive(readOnly, t('adminFinance.financePreview'))) return

  const reason = financeDecisionReason.value.trim()
  if (!reason) {
    actionSuccess.value = null
    actionError.value = t('adminFinance.reasonRequired')
    return
  }

  if (pendingDecision.kind === 'refund') {
    await runFinanceAction(
      () => adminApi.rejectRefund(pendingDecision.id, { reason }),
      refundsResource.reload,
      t('adminFinance.rejectRefundError'),
    )
    return
  }

  await runFinanceAction(
    () => adminApi.rejectPayout(pendingDecision.id, { reason }),
    payoutsResource.reload,
    t('adminFinance.rejectPayoutError'),
  )
}

async function saveCommission(): Promise<void> {
  if (submittingCommission.value) return
  if (!ensureFinanceLive(commissionReadOnly.value, t('adminFinance.commissionPreview'))) return
  actionError.value = null
  actionSuccess.value = null
  submittingCommission.value = true
  try {
    const normalizedRate = clampCommissionRate(commissionRate.value)
    commissionRate.value = normalizedRate
    await adminApi.updateCommissionSetting({ commissionRate: normalizedRate })
    await commissionResource.reload()
    actionSuccess.value = t('adminFinance.commissionSaved')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminFinance.commissionSaveError'))
  } finally {
    submittingCommission.value = false
  }
}

async function runFinanceAction(
  action: () => Promise<unknown>,
  reload: () => Promise<unknown>,
  fallback: string,
): Promise<void> {
  actionError.value = null
  actionSuccess.value = null
  financeActionBusy.value = true
  try {
    await action()
    await reload()
    pendingFinanceDecision.value = null
    financeDecisionReason.value = ''
    actionSuccess.value = t('adminFinance.actionComplete')
  } catch (reason) {
    actionError.value = resolveApiError(reason, fallback)
  } finally {
    financeActionBusy.value = false
  }
}

function ensureFinanceLive(readOnly: boolean, message: string): boolean {
  if (!readOnly) return true
  actionSuccess.value = null
  actionError.value = message
  return false
}

function clampCommissionRate(value: number): number {
  return clampNumber(value, 0, 1)
}

function confirmFinanceAction(message: string): boolean {
  const confirmed = window.confirm(`${message}\n\n${t('adminFinance.liveWarning')}`)
  if (!confirmed) {
    actionSuccess.value = null
    actionError.value = null
  }

  return confirmed
}
</script>
