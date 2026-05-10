<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import AnalyticsChartCard from '../../components/common/AnalyticsChartCard.vue'
import { dashboardService } from '../../services/dashboardService'
import { useAuthStore } from '../../stores/authStore'
import type { AdminAnalyticsResponse } from '../../types/analytics'
import type { AdminDashboardSummary } from '../../types/dashboard'
import {
  buildBarChartOption,
  buildDonutChartOption,
  currency,
  hasBreakdownData,
  hasTrendData,
  normalizeBreakdown,
} from '../../utils/analytics'

const authStore = useAuthStore()
const { t } = useI18n()
const user = computed(() => authStore.currentUser)
const dashboard = ref<AdminDashboardSummary | null>(null)
const analytics = ref<AdminAnalyticsResponse | null>(null)
const loading = ref(true)
const analyticsError = ref('')

const summaryCards = computed(() => [
  { label: t('analytics.cards.totalStudents'), value: analytics.value?.overview.totalStudents ?? dashboard.value?.totalStudents ?? 0 },
  { label: t('analytics.cards.totalTeachers'), value: analytics.value?.overview.totalTeachers ?? dashboard.value?.totalTeachers ?? 0 },
  { label: t('analytics.cards.pendingTeacherApprovals'), value: analytics.value?.overview.pendingTeacherApprovals ?? dashboard.value?.pendingTeacherApprovals ?? 0 },
  { label: t('analytics.cards.publishedContentItems'), value: analytics.value?.overview.publishedContentItems ?? (dashboard.value?.totalModules ?? 0) + (dashboard.value?.totalLessons ?? 0) },
  { label: t('analytics.cards.totalRevenue'), value: currency(analytics.value?.overview.totalRevenue ?? dashboard.value?.paymentRevenueSummary.totalRevenue ?? 0) },
  { label: t('analytics.cards.aiRequests'), value: analytics.value?.overview.aiRequests ?? dashboard.value?.aiUsageSummary.totalRequests ?? 0 },
])

const userRegistrationTrendOption = computed(() =>
  buildBarChartOption(analytics.value?.userRegistrationTrend ?? [], t, {
    color: '#22c55e',
    seriesName: t('analytics.charts.userRegistrationTrend'),
  }),
)

const teacherApprovalBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.teacherApprovalBreakdown ?? [], ['PENDING', 'APPROVED', 'REJECTED']),
)

const paymentStatusBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.paymentStatusBreakdown ?? [], ['PENDING', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED']),
)

const supportTicketStatusBreakdown = computed(() =>
  normalizeBreakdown(analytics.value?.supportTicketStatusBreakdown ?? [], ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']),
)

const teacherApprovalOption = computed(() =>
  buildDonutChartOption(teacherApprovalBreakdown.value, t, {
    centerLabel: t('analytics.charts.teacherApprovalBreakdown'),
  }),
)

const paymentStatusOption = computed(() =>
  buildDonutChartOption(paymentStatusBreakdown.value, t, {
    centerLabel: t('analytics.charts.paymentStatusBreakdown'),
  }),
)

const supportTicketOption = computed(() =>
  buildDonutChartOption(supportTicketStatusBreakdown.value, t, {
    centerLabel: t('analytics.charts.supportTicketStatusBreakdown'),
  }),
)

const userRegistrationEmpty = computed(() => !hasTrendData(analytics.value?.userRegistrationTrend ?? []))
const teacherApprovalEmpty = computed(() => !hasBreakdownData(teacherApprovalBreakdown.value))
const paymentStatusEmpty = computed(() => !hasBreakdownData(paymentStatusBreakdown.value))
const supportTicketEmpty = computed(() => !hasBreakdownData(supportTicketStatusBreakdown.value))

async function loadDashboard() {
  loading.value = true
  analyticsError.value = ''

  try {
    const [dashboardResponse, analyticsResponse] = await Promise.all([
      dashboardService.getAdminDashboard(),
      dashboardService.getAdminAnalytics().catch((error) => {
        analyticsError.value = error instanceof Error ? error.message : t('analytics.loadFailed')
        return null
      }),
    ])
    dashboard.value = dashboardResponse
    analytics.value = analyticsResponse
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="10">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('nav.adminDashboard') }}</p>
              <h1>{{ t('dashboard.adminWelcome', { name: user?.fullName ?? dashboard?.fullName ?? 'Administrator' }) }}</h1>
              <p>{{ t('dashboard.adminOverview') }}</p>
            </div>

            <div class="dashboard-hero__aside">
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('dashboard.paymentRevenueSummary') }}</span>
                <strong>{{ currency(analytics?.overview.totalRevenue ?? dashboard?.paymentRevenueSummary.totalRevenue ?? 0) }}</strong>
              </div>
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('dashboard.aiUsageSummary') }}</span>
                <strong>{{ analytics?.overview.aiRequests ?? dashboard?.aiUsageSummary.totalRequests ?? 0 }}</strong>
              </div>
            </div>
          </div>
        </el-card>

        <section class="admin-stat-grid">
          <el-card v-for="card in summaryCards" :key="card.label" class="metric-card metric-card--admin" shadow="hover">
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
          </el-card>
        </section>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.operations') }}</p>
                <h2>{{ t('dashboard.platformSnapshot') }}</h2>
              </div>
            </div>

            <div class="action-card-grid action-card-grid--admin">
              <router-link class="management-card" :to="{ name: 'admin-users' }">
                <h3>{{ t('dashboard.manageStudents') }}</h3>
                <p>{{ t('dashboard.manageStudentsDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-teacher-approval' }">
                <h3>{{ t('dashboard.manageTeachers') }}</h3>
                <p>{{ t('dashboard.manageTeachersDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-content-approvals' }">
                <h3>{{ t('dashboard.manageContent') }}</h3>
                <p>{{ t('dashboard.manageContentDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-reports' }">
                <h3>{{ t('dashboard.reportsSummary') }}</h3>
                <p>{{ t('dashboard.reportsSummaryDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-audit-logs' }">
                <h3>{{ t('dashboard.activityLogsSummary') }}</h3>
                <p>{{ t('dashboard.activityLogsSummaryDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-payments' }">
                <h3>{{ t('dashboard.paymentRevenueSummary') }}</h3>
                <p>{{ t('dashboard.paymentRevenueSummaryDesc') }}</p>
              </router-link>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.pendingActions') }}</p>
                <h2>{{ t('dashboard.priorityQueue') }}</h2>
              </div>
            </div>

            <div class="stacked-summary-list">
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.pendingRefunds') }}</strong>
                  <p>{{ t('dashboard.financialRequestsWaiting') }}</p>
                </div>
                <el-tag round type="warning">{{ dashboard?.pendingRefunds ?? 0 }}</el-tag>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.pendingPayouts') }}</strong>
                  <p>{{ t('dashboard.teacherRequestsWaiting') }}</p>
                </div>
                <el-tag round type="warning">{{ dashboard?.pendingPayouts ?? 0 }}</el-tag>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.pendingTeacherApprovals') }}</strong>
                  <p>{{ t('dashboard.teacherApprovalsWaiting') }}</p>
                </div>
                <el-tag round type="danger">{{ dashboard?.pendingTeacherApprovals ?? 0 }}</el-tag>
              </article>
            </div>
          </el-card>
        </section>

        <section class="dashboard-grid">
          <AnalyticsChartCard
            :title="t('analytics.charts.userRegistrationTrend')"
            :subtitle="t('analytics.adminUserRegistrationDescription')"
            :option="userRegistrationTrendOption"
            :loading="loading"
            :error="analyticsError"
            :empty="userRegistrationEmpty"
            :empty-description="t('analytics.emptyTrend')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.teacherApprovalBreakdown')"
            :subtitle="t('analytics.adminTeacherApprovalDescription')"
            :option="teacherApprovalOption"
            :loading="loading"
            :error="analyticsError"
            :empty="teacherApprovalEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.paymentStatusBreakdown')"
            :subtitle="t('analytics.adminPaymentStatusDescription')"
            :option="paymentStatusOption"
            :loading="loading"
            :error="analyticsError"
            :empty="paymentStatusEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />

          <AnalyticsChartCard
            :title="t('analytics.charts.supportTicketStatusBreakdown')"
            :subtitle="t('analytics.adminSupportStatusDescription')"
            :option="supportTicketOption"
            :loading="loading"
            :error="analyticsError"
            :empty="supportTicketEmpty"
            :empty-description="t('analytics.emptyBreakdown')"
          />
        </section>

        <section class="dashboard-grid">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recentUserActivity') }}</p>
                <h2>{{ t('dashboard.newAccountsAndRoles') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!dashboard?.recentUserActivity.length"
              :description="t('dashboard.noRecentUserActivity')"
            />
            <div v-else class="stacked-summary-list">
              <article v-for="activity in dashboard.recentUserActivity" :key="activity.userId" class="summary-row">
                <div>
                  <strong>{{ activity.fullName }}</strong>
                  <p>{{ activity.email }} - {{ activity.roles.join(', ') }}</p>
                </div>
                <el-tag round>{{ activity.roles[0] }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.aiUsageSummary') }}</p>
                <h2>{{ t('dashboard.aiOperations') }}</h2>
              </div>
            </div>

            <div class="stacked-summary-list">
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.totalAiRequests') }}</strong>
                </div>
                <el-tag round>{{ analytics?.overview?.aiRequests ?? dashboard?.aiUsageSummary.totalRequests ?? 0 }}</el-tag>
              </article>
              <article class="summary-row">
                <div>
                  <strong>{{ t('dashboard.successfulAiRequests') }}</strong>
                </div>
                <el-tag round type="success">{{ dashboard?.aiUsageSummary.successfulRequests ?? 0 }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recentReports') }}</p>
                <h2>{{ t('dashboard.safetyAndModeration') }}</h2>
              </div>
            </div>

            <el-empty v-if="!dashboard?.recentReports.length" :description="t('dashboard.noRecentReports')" />
            <div v-else class="stacked-summary-list">
              <article v-for="report in dashboard.recentReports" :key="report.reportId" class="summary-row">
                <div>
                  <strong>{{ report.targetType }}</strong>
                  <p>{{ report.status }}</p>
                </div>
                <el-tag round type="warning">{{ report.status }}</el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('dashboard.recentAuditLogs') }}</p>
                <h2>{{ t('dashboard.operationalTrail') }}</h2>
              </div>
            </div>

            <el-empty v-if="!dashboard?.recentAuditLogs.length" :description="t('dashboard.noRecentAuditLogs')" />
            <div v-else class="stacked-summary-list">
              <article v-for="log in dashboard.recentAuditLogs" :key="log.auditLogId" class="summary-row">
                <div>
                  <strong>{{ log.actionType }}</strong>
                  <p>{{ log.description || t('dashboard.activityRecorded') }}</p>
                </div>
                <el-tag round>{{ log.targetType || 'SYSTEM' }}</el-tag>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
