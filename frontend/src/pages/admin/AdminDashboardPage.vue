<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { dashboardService } from '../../services/dashboardService'
import { useAuthStore } from '../../stores/authStore'
import type { AdminDashboardSummary } from '../../types/dashboard'

const authStore = useAuthStore()
const { t } = useI18n()
const user = computed(() => authStore.currentUser)
const dashboard = ref<AdminDashboardSummary | null>(null)
const loading = ref(true)

const summaryCards = computed(() => [
  { label: t('dashboard.totalStudents'), value: dashboard.value?.totalStudents ?? 0 },
  { label: t('dashboard.totalTeachers'), value: dashboard.value?.totalTeachers ?? 0 },
  { label: t('dashboard.pendingTeacherApprovals'), value: dashboard.value?.pendingTeacherApprovals ?? 0 },
  { label: t('dashboard.totalModules'), value: dashboard.value?.totalModules ?? 0 },
  { label: t('dashboard.totalLessons'), value: dashboard.value?.totalLessons ?? 0 },
  { label: t('dashboard.totalQuizzesGames'), value: (dashboard.value?.totalQuizzes ?? 0) + (dashboard.value?.totalGames ?? 0) },
])

async function loadDashboard() {
  loading.value = true

  try {
    dashboard.value = await dashboardService.getAdminDashboard()
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
                <strong>RM {{ (dashboard?.paymentRevenueSummary.totalRevenue ?? 0).toFixed(2) }}</strong>
              </div>
              <div class="dashboard-bubble">
                <span class="dashboard-bubble__label">{{ t('dashboard.aiUsageSummary') }}</span>
                <strong>{{ dashboard?.aiUsageSummary.totalRequests ?? 0 }}</strong>
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
              <router-link class="management-card" :to="{ name: 'admin-content-approval' }">
                <h3>{{ t('dashboard.manageContent') }}</h3>
                <p>{{ t('dashboard.manageContentDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-reports' }">
                <h3>{{ t('dashboard.reportsSummary') }}</h3>
                <p>{{ t('dashboard.reportsSummaryDesc') }}</p>
              </router-link>
              <router-link class="management-card" :to="{ name: 'admin-activity-logs' }">
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
                  <p>{{ activity.email }} · {{ activity.roles.join(', ') }}</p>
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
                <el-tag round>{{ dashboard?.aiUsageSummary.totalRequests ?? 0 }}</el-tag>
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
