<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { supportTicketService } from '../../services/supportTicketService'
import type { SupportTicketDetail } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/datetime'

const route = useRoute()
const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const detail = ref<SupportTicketDetail | null>(null)
const replying = ref(false)
const form = reactive({ message: '' })


async function loadDetail() {
  loading.value = true
  try {
    detail.value = await supportTicketService.getMyTicket(Number(route.params.id))
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function reply() {
  replying.value = true
  try {
    detail.value = await supportTicketService.addMyMessage(Number(route.params.id), { message: form.message })
    form.message = ''
    ElMessage.success(t('phase11.messages.supportTicketReplied'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    replying.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    loadDetail()
  },
)

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.supportEyebrow')"
      :title="detail?.ticket.subject || t('phase11.supportTicketDetailTitle')"
      :description="t('phase11.supportTicketDetailDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item :label="t('phase11.ticketStatus')">{{ translateEnum('ticketStatus', detail.ticket.status) }}</el-descriptions-item>
        <el-descriptions-item :label="t('phase11.ticketPriority')">{{ translateEnum('ticketPriority', detail.ticket.priority) }}</el-descriptions-item>
        <el-descriptions-item :label="t('commerce.createdAt')">{{ formatDateTime(detail.ticket.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('phase11.ticketRequester')">{{ detail.ticket.requesterName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="support-thread">
        <el-timeline v-loading="loading">
          <el-timeline-item
            v-for="message in detail?.messages ?? []"
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
      </div>

      <el-form v-if="detail" label-position="top" class="support-reply-form">
        <el-form-item :label="t('phase11.reply')">
          <el-input v-model="form.message" type="textarea" :rows="4" maxlength="4000" show-word-limit />
        </el-form-item>
        <el-button
          type="primary"
          :loading="replying"
          :disabled="detail.ticket.status === 'CLOSED'"
          @click="reply"
        >
          {{ t('phase11.reply') }}
        </el-button>
      </el-form>

      <el-empty v-if="!loading && !detail" :description="t('phase11.emptySupportTickets')" />
    </el-card>
  </div>
</template>
