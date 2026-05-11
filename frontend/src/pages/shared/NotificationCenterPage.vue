<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { APP_EVENTS, dispatchAppEvent } from '../../constants/appEvents'
import { notificationService } from '../../services/notificationService'
import type { NotificationItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/datetime'

const router = useRouter()
const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const notifications = ref<NotificationItem[]>([])

const unreadCount = computed(() => notifications.value.filter((item) => item.status === 'UNREAD').length)

async function loadNotifications() {
  loading.value = true
  try {
    notifications.value = await notificationService.getNotifications()
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function markAsRead(item: NotificationItem) {
  try {
    await notificationService.markAsRead(item.id)
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    if (item.targetPath) {
      await router.push(item.targetPath)
      return
    }
    await loadNotifications()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  }
}

async function markAllAsRead() {
  try {
    await notificationService.markAllAsRead()
    ElMessage.success(t('phase11.messages.notificationsUpdated'))
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    await loadNotifications()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  }
}

onMounted(() => {
  loadNotifications()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.notificationsEyebrow')"
      :title="t('phase11.notificationsTitle')"
      :description="t('phase11.notificationsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.notifications') }}</p>
          <h2>{{ t('phase11.unreadCountLabel', { count: unreadCount }) }}</h2>
        </div>
        <el-button type="primary" plain :disabled="!unreadCount" @click="markAllAsRead">
          {{ t('phase11.markAllAsRead') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="notifications" class="phase-table">
        <el-table-column :label="t('phase11.notificationType')" min-width="150">
          <template #default="{ row }">
            <el-tag round :type="row.status === 'UNREAD' ? 'warning' : 'info'">
              {{ translateEnum('notification.type', row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('phase11.notificationTitle')" min-width="220" />
        <el-table-column prop="message" :label="t('phase11.notificationMessage')" min-width="320" />
        <el-table-column :label="t('commerce.status')" min-width="140">
          <template #default="{ row }">
            <el-tag round :type="row.status === 'UNREAD' ? 'danger' : 'success'">
              {{ translateEnum('notification.status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('commerce.createdAt')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column width="180">
          <template #default="{ row }">
            <el-button plain type="primary" @click="markAsRead(row)">
              {{ row.status === 'UNREAD' ? t('phase11.markAsRead') : t('content.reviewDetail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !notifications.length" :description="t('phase11.emptyNotifications')" />
    </el-card>
  </div>
</template>
