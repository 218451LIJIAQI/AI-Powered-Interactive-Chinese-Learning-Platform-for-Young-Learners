<template>
  <section class="public-page payment-result-page" :class="resultStateClass">
    <div class="payment-result-shell">
      <aside class="payment-result-visual">
        <span class="payment-result-cloud payment-result-cloud--one" />
        <span class="payment-result-cloud payment-result-cloud--two" />
        <span class="payment-result-bamboo" />
        <span class="payment-result-pagoda" />
        <PandaIllustration class="payment-result-panda" :size="210" :outfit="pandaOutfit" accent="#ffcc2f" />
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
          <strong>{{ t('payment.verifyingCheckout') }}</strong>
        </div>

        <div v-if="error" class="soft-banner auth-alert auth-alert--error" role="alert" aria-live="assertive">
          <span>{{ error }}</span>
        </div>

        <div v-if="payment" class="payment-result-summary">
          <div>
            <span>{{ t('payment.order') }}</span>
            <strong>{{ payment.orderNumber }}</strong>
          </div>
          <div>
            <span>{{ t('payment.status') }}</span>
            <strong>{{ paymentStatusLabel }}</strong>
          </div>
          <div>
            <span>{{ t('payment.amount') }}</span>
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
          <RouterLink class="btn btn-primary" to="/student/profile">{{ t('payment.viewPayments') }}</RouterLink>
          <RouterLink class="btn btn-ghost" to="/courses">{{ t('payment.continueLearning') }}</RouterLink>
        </div>

        <p class="payment-result-footnote">{{ footnote }}</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { paymentApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { paymentMessages } from '@/plugins/paymentMessages'
import type { StudentPaymentRecordResponse } from '@/types/api'
import { isUnauthorizedApiError, resolveApiError } from '@/utils/apiError'
import { formatCurrency } from '@/utils/format'

const route = useRoute()
const { t } = useI18n({
  messages: {
    en: { payment: paymentMessages.en },
    ms: { payment: paymentMessages.ms },
    zh: { payment: paymentMessages.zh },
  },
})

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
const speechTitle = computed(() => (resultKind.value === 'cancelled' ? t('payment.noWorries') : t('payment.greatJob')))
const speechCopy = computed(() =>
  resultKind.value === 'cancelled' ? t('payment.returnAnytime') : t('payment.journeyContinues'),
)
const resultIcon = computed<ResultIcon>(() => (resultKind.value === 'cancelled' ? 'chevronLeft' : 'status'))
const kicker = computed(() =>
  resultKind.value === 'cancelled' ? t('payment.checkoutPaused') : t('payment.checkoutComplete'),
)
const title = computed(() => {
  if (resultKind.value === 'paid') return t('payment.successful')
  return resultKind.value === 'cancelled' ? t('payment.cancelled') : t('payment.statusTitle')
})
const description = computed(() => {
  if (loading.value) return t('payment.verifyingPayment')
  if (resultKind.value === 'paid') return t('payment.paidCopy')
  if (resultKind.value === 'cancelled') return t('payment.cancelledCopy')
  return t('payment.pendingCopy')
})
const footnote = computed(() =>
  resultKind.value === 'cancelled' ? t('payment.cancelledFootnote') : t('payment.pendingFootnote'),
)
const paymentStatusLabel = computed(() => {
  const keys: Record<string, string> = {
    PAID: 'paidStatus',
    PENDING: 'pendingStatus',
    FAILED: 'failedStatus',
    REFUNDED: 'refundedStatus',
    CANCELLED: 'cancelledStatus',
  }
  const key = payment.value?.paymentStatus ? keys[payment.value.paymentStatus] : null
  return key ? t(`payment.${key}`) : (payment.value?.paymentStatus ?? '')
})
const resultSteps = computed<Array<{ icon: ResultIcon; label: string; copy: string; state: ResultStepState }>>(() => [
  {
    icon: 'finance',
    label: t('payment.checkout'),
    copy: resultKind.value === 'cancelled' ? t('payment.checkoutCancelled') : t('payment.checkoutReturned'),
    state: 'done',
  },
  {
    icon: verificationStepIcon(resultKind.value),
    label: t('payment.verification'),
    copy: verificationStepCopy(resultKind.value),
    state: verificationStepState(resultKind.value),
  },
  {
    icon: resultKind.value === 'paid' ? 'learn' : 'fun',
    label: t('payment.learningAccess'),
    copy: resultKind.value === 'paid' ? t('payment.unlocked') : t('payment.browseWhenReady'),
    state: resultKind.value === 'paid' ? 'done' : 'muted',
  },
])

function resolvePaymentError(reason: unknown): string {
  if (isUnauthorizedApiError(reason)) {
    return t('payment.signInAgain')
  }
  return resolveApiError(reason, t('payment.verificationFailed'))
}

function verificationStepIcon(kind: ResultKind): ResultIcon {
  if (kind === 'paid') return 'status'
  return kind === 'cancelled' ? 'chevronLeft' : 'history'
}

function verificationStepCopy(kind: ResultKind): string {
  if (kind === 'paid') return t('payment.paymentConfirmed')
  return kind === 'cancelled' ? t('payment.noConfirmationNeeded') : t('payment.checkAgain')
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
