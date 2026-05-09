<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { supportTicketService } from '../../services/supportTicketService'
import type { SupportTicketDetail, SupportTicketPriority, SupportTicketStatus } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const route = useRoute()
const { t } = useI18n()
const loading = ref(true)
const replying = ref(false)
const updating = ref(false)
const detail = ref<SupportTicketDetail | null>(null)
const replyForm = reactive({ message: '' })
const controls = reactive<{
  status: SupportTicketStatus
  priority: SupportTicketPriority
}>({
  status: 'OPEN',
  priority: 'MEDIUM',
})

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('en-MY')
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await supportTicketService.getAdminTicket(Number(route.params.id))
    if (detail.value) {
      controls.status = detail.value.ticket.status
      controls.priority = detail.value.ticket.priority
    }
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function reply() {
  replying.value = true
  try {
    detail.value = await supportTicketService.addAdminMessage(Number(route.params.id), replyForm)
    replyForm.message = ''
    ElMessage.success(t('phase11.messages.supportTicketReplied'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    replying.value = false
  }
}

async function updateControls() {
  updating.value = true
  try {
    await supportTicketService.updateAdminStatus(Number(route.params.id), { status: controls.status })
    await supportTicketService.updateAdminPriority(Number(route.params.id), { priority: controls.priority })
    ElMessage.success(t('phase11.messages.supportTicketUpdated'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
    await loadDetail()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    updating.value = false
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.supportEyebrow')"
      :title="detail?.ticket.subject || t('phase11.supportTicketDetailTitle')"
      :description="t('phase11.adminSupportDetailDescription')"
    />

    <el-card v-loading="loading" class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-select v-model="controls.status" class="content-filter">
          <el-option label="OPEN" value="OPEN" />
          <el-option label="IN_PROGRESS" value="IN_PROGRESS" />
          <el-option label="RESOLVED" value="RESOLVED" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
        <el-select v-model="controls.priority" class="content-filter">
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
          <el-option label="URGENT" value="URGENT" />
        </el-select>
        <el-button type="primary" :loading="updating" @click="updateControls">{{ t('content.save') }}</el-button>
      </div>

      <el-timeline v-if="detail">
        <el-timeline-item
          v-for="message in detail.messages"
          :key="message.id"
          :timestamp="formatDateTime(message.createdAt)"
          placement="top"
        >
          <el-card shadow="never">
            <div class="support-thread__header">
              <strong>{{ message.senderName || '-' }}</strong>
              <el-tag round>{{ message.senderRole }}</el-tag>
            </div>
            <p>{{ message.message }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>

      <el-form label-position="top" class="support-reply-form">
        <el-form-item :label="t('phase11.reply')">
          <el-input v-model="replyForm.message" type="textarea" :rows="4" maxlength="4000" show-word-limit />
        </el-form-item>
        <el-button type="primary" :loading="replying" @click="reply">{{ t('phase11.reply') }}</el-button>
      </el-form>
    </el-card>
  </div>
</template>
