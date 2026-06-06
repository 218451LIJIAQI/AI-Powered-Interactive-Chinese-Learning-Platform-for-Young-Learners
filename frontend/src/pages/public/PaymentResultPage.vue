<template>
  <section class="public-page payment-result-page" :class="resultStateClass">
    <div class="payment-result-shell">
      <aside class="payment-result-visual">
        <span class="payment-result-cloud payment-result-cloud--one" />
        <span class="payment-result-cloud payment-result-cloud--two" />
        <span class="payment-result-bamboo" />
        <span class="payment-result-pagoda" />
        <PandaIllustration
          class="payment-result-panda"
          :size="210"
          :outfit="pandaOutfit"
          accent="#ffcc2f"
        />
        <div class="payment-result-speech">
          <strong>{{ speechTitle }}</strong>
          <span>{{ speechCopy }}</span>
        </div>
      </aside>

      <div class="surface-card payment-result-card">
        <AppBrand />

        <div class="payment-result-icon" :class="resultStateClass">
          <IconGlyph :name="resultIcon" :size="34" />
        </div>

        <div class="auth-form-title payment-result-copy">
          <span class="payment-result-kicker">{{ kicker }}</span>
          <h2>{{ title }}</h2>
          <p>{{ description }}</p>
        </div>

        <div v-if="loading" class="payment-result-loading">
          <span />
          <strong>Verifying checkout status...</strong>
        </div>

        <div v-if="error" class="soft-banner auth-alert auth-alert--error" role="alert" aria-live="assertive">
          <span>{{ error }}</span>
        </div>

        <div v-if="payment" class="payment-result-summary">
          <div>
            <span>Order</span>
            <strong>{{ payment.orderNumber }}</strong>
          </div>
          <div>
            <span>Status</span>
            <strong>{{ payment.paymentStatus }}</strong>
          </div>
          <div>
            <span>Amount</span>
            <strong>{{ formatCurrency(payment.amount, payment.currencyCode) }}</strong>
          </div>
        </div>

        <div class="payment-result-timeline">
          <div v-for="step in resultSteps" :key="step.label" :class="`is-${step.state}`">
            <span><IconGlyph :name="step.icon" :size="18" /></span>
            <div>
              <strong>{{ step.label }}</strong>
              <small>{{ step.copy }}</small>
            </div>
          </div>
        </div>

        <div class="payment-result-actions">
          <RouterLink class="btn btn-primary" to="/student/profile">View payments</RouterLink>
          <RouterLink class="btn btn-ghost" to="/courses">Continue learning</RouterLink>
        </div>

        <p class="payment-result-footnote">{{ footnote }}</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { paymentApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import type { StudentPaymentRecordResponse } from '@/types/api'
import { isUnauthorizedApiError, resolveApiError } from '@/utils/apiError'
import { formatCurrency } from '@/utils/format'

const route = useRoute()

const payment = ref<StudentPaymentRecordResponse | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

type ResultKind = 'cancelled' | 'pending' | 'paid'
type ResultStepState = 'active' | 'done' | 'muted'
type ResultIcon = 'chevronLeft' | 'finance' | 'fun' | 'history' | 'learn' | 'status'

const paymentId = computed(() => {
  const value = route.query.paymentId
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = raw ? Number(raw) : NaN
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})
const routeCancelled = computed(() => route.path.endsWith('/cancel') || route.query.cancelled === 'true')
const paymentPaid = computed(() => payment.value?.paymentStatus === 'PAID')
const showCancelledState = computed(() => routeCancelled.value && !paymentPaid.value)
const resultKind = computed<ResultKind>(() => {
  if (paymentPaid.value) return 'paid'
  return showCancelledState.value ? 'cancelled' : 'pending'
})
const resultStateClass = computed(() => (resultKind.value === 'cancelled' ? 'is-cancelled' : 'is-success'))
const pandaOutfit = computed(() => (resultKind.value === 'cancelled' ? '#ff9f2f' : '#46b851'))
const speechTitle = computed(() => (resultKind.value === 'cancelled' ? 'No worries!' : 'Great job!'))
const speechCopy = computed(() =>
  resultKind.value === 'cancelled' ? 'You can return to courses anytime.' : 'Your learning journey continues.',
)
const resultIcon = computed<ResultIcon>(() => (resultKind.value === 'cancelled' ? 'chevronLeft' : 'status'))
const kicker = computed(() => (resultKind.value === 'cancelled' ? 'Checkout paused' : 'Checkout complete'))
const title = computed(() => {
  if (resultKind.value === 'paid') return 'Payment successful'
  return resultKind.value === 'cancelled' ? 'Payment cancelled' : 'Payment status'
})
const description = computed(() => {
  if (loading.value) return 'Verifying your payment status...'
  if (resultKind.value === 'paid') return 'Your payment has been verified and your learning access is unlocked.'
  if (resultKind.value === 'cancelled') return 'Your checkout was cancelled. No new access was unlocked.'
  return 'We could not automatically verify this payment. You can check your payments page or sign in again.'
})
const footnote = computed(() =>
  resultKind.value === 'cancelled'
    ? 'Your course access was not changed by this cancelled checkout.'
    : 'If access does not appear immediately, refresh your student profile payments area.',
)
const resultSteps = computed<Array<{ icon: ResultIcon; label: string; copy: string; state: ResultStepState }>>(() => [
  {
    icon: 'finance',
    label: 'Checkout',
    copy:
      resultKind.value === 'cancelled' ? 'Checkout was safely cancelled.' : 'Checkout returned to MandarinSpark.',
    state: 'done',
  },
  {
    icon: verificationStepIcon(resultKind.value),
    label: 'Verification',
    copy: verificationStepCopy(resultKind.value),
    state: verificationStepState(resultKind.value),
  },
  {
    icon: resultKind.value === 'paid' ? 'learn' : 'fun',
    label: 'Learning access',
    copy: resultKind.value === 'paid' ? 'Unlocked for your student account.' : 'Browse courses when you are ready.',
    state: resultKind.value === 'paid' ? 'done' : 'muted',
  },
])

function resolvePaymentError(reason: unknown): string {
  if (isUnauthorizedApiError(reason)) {
    return 'Please sign in again to verify this payment.'
  }
  return resolveApiError(reason, 'Unable to verify this payment right now.')
}

function verificationStepIcon(kind: ResultKind): ResultIcon {
  if (kind === 'paid') return 'status'
  return kind === 'cancelled' ? 'chevronLeft' : 'history'
}

function verificationStepCopy(kind: ResultKind): string {
  if (kind === 'paid') return 'Payment is confirmed.'
  return kind === 'cancelled' ? 'No payment confirmation needed.' : 'Status may need another check.'
}

function verificationStepState(kind: ResultKind): ResultStepState {
  if (kind === 'paid') return 'done'
  return kind === 'cancelled' ? 'muted' : 'active'
}

onMounted(async () => {
  if (paymentId.value == null) return

  loading.value = true
  try {
    payment.value = await paymentApi.status(paymentId.value)
  } catch (reason) {
    error.value = resolvePaymentError(reason)
  } finally {
    loading.value = false
  }
})
</script>
