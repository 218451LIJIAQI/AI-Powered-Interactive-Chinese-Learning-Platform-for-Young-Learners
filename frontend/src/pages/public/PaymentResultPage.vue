<template>
  <section class="public-page payment-result-page" :class="showCancelledState ? 'is-cancelled' : 'is-success'">
    <div class="payment-result-shell">
      <aside class="payment-result-visual">
        <span class="payment-result-cloud payment-result-cloud--one" />
        <span class="payment-result-cloud payment-result-cloud--two" />
        <span class="payment-result-bamboo" />
        <span class="payment-result-pagoda" />
        <PandaIllustration
          class="payment-result-panda"
          :size="210"
          :outfit="showCancelledState ? '#ff9f2f' : '#46b851'"
          accent="#ffcc2f"
        />
        <div class="payment-result-speech">
          <strong>{{ showCancelledState ? 'No worries!' : 'Great job!' }}</strong>
          <span>{{
            showCancelledState ? 'You can return to courses anytime.' : 'Your learning journey continues.'
          }}</span>
        </div>
      </aside>

      <div class="surface-card payment-result-card">
        <AppBrand />

        <div class="payment-result-icon" :class="showCancelledState ? 'is-cancelled' : 'is-success'">
          <IconGlyph :name="showCancelledState ? 'chevronLeft' : 'status'" :size="34" />
        </div>

        <div class="auth-form-title payment-result-copy">
          <span class="payment-result-kicker">{{ showCancelledState ? 'Checkout paused' : 'Checkout complete' }}</span>
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

        <p class="payment-result-footnote">
          {{
            showCancelledState
              ? 'Your course access was not changed by this cancelled checkout.'
              : 'If access does not appear immediately, refresh your student profile payments area.'
          }}
        </p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * Post-checkout landing page for `/payment/success` and
 * `/payment/cancel` redirects with an optional payment id.
 *
 * <h2>Why server verification</h2>
 * <p>The query string alone is untrusted — a user can edit the URL
 * and reach this page with `success` even when the payment failed.
 * On mount we call {@code GET /payments/status/{id}} which is the
 * authoritative source: the entitlement and notification are
 * created by the backend webhook, not by this redirect.</p>
 *
 * <h2>Defensive copy</h2>
 * <p>The success copy is gated on the verification result, not the
 * query string, so an attacker can never make the page show
 * "Payment confirmed" without an actual confirmed payment record.</p>
 */
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

const paymentId = computed(() => {
  const value = route.query.paymentId
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = raw ? Number(raw) : NaN
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})
const routeCancelled = computed(() => route.path.endsWith('/cancel') || route.query.cancelled === 'true')
const paymentPaid = computed(() => payment.value?.paymentStatus === 'PAID')
const showCancelledState = computed(() => routeCancelled.value && !paymentPaid.value)
const title = computed(() =>
  paymentPaid.value ? 'Payment successful' : showCancelledState.value ? 'Payment cancelled' : 'Payment status',
)
const description = computed(() => {
  if (loading.value) return 'Verifying your payment status...'
  if (paymentPaid.value) return 'Your payment has been verified and your learning access is unlocked.'
  if (showCancelledState.value) return 'Your checkout was cancelled. No new access was unlocked.'
  return 'We could not automatically verify this payment. You can check your payments page or sign in again.'
})
const resultSteps = computed(() => [
  {
    icon: 'finance',
    label: 'Checkout',
    copy: showCancelledState.value ? 'Checkout was safely cancelled.' : 'Checkout returned to MandarinSpark.',
    state: 'done',
  },
  {
    icon: paymentPaid.value ? 'status' : showCancelledState.value ? 'chevronLeft' : 'history',
    label: 'Verification',
    copy: paymentPaid.value
      ? 'Payment is confirmed.'
      : showCancelledState.value
        ? 'No payment confirmation needed.'
        : 'Status may need another check.',
    state: paymentPaid.value ? 'done' : showCancelledState.value ? 'muted' : 'active',
  },
  {
    icon: paymentPaid.value ? 'learn' : 'fun',
    label: 'Learning access',
    copy: paymentPaid.value ? 'Unlocked for your student account.' : 'Browse courses when you are ready.',
    state: paymentPaid.value ? 'done' : 'muted',
  },
])

function resolvePaymentError(reason: unknown) {
  if (isUnauthorizedApiError(reason)) {
    return 'Please sign in again to verify this payment.'
  }
  return resolveApiError(reason, 'Unable to verify this payment right now.')
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
