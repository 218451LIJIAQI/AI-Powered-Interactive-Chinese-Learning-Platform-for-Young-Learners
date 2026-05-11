<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { APP_EVENTS, dispatchAppEvent } from '../../constants/appEvents'
import { supportTicketService } from '../../services/supportTicketService'
import type { SupportTicketDetail } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/datetime'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const ticketId = useNumericRouteParam('id')
const loading = ref(true)
const detail = ref<SupportTicketDetail | null>(null)
const replying = ref(false)
const replyFormRef = ref<FormInstance | null>(null)
const form = reactive({ message: '' })

const replyRules = computed<FormRules>(() => ({
  message: [
    { required: true, message: t('phase11.validation.replyRequired'), trigger: 'blur' },
  ],
}))

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await supportTicketService.getMyTicket(ticketId.value)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function reply() {
  if (!replyFormRef.value) {
    return
  }
  try {
    await replyFormRef.value.validate()
  } catch {
    return
  }
  replying.value = true
  try {
    detail.value = await supportTicketService.addMyMessage(ticketId.value, { message: form.message.trim() })
    form.message = ''
    replyFormRef.value.clearValidate()
    ElMessage.success(t('phase11.messages.supportTicketReplied'))
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    replying.value = false
  }
}

watch(ticketId, () => {
  loadDetail()
})

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

      <el-form
        v-if="detail"
        ref="replyFormRef"
        :model="form"
        :rules="replyRules"
        label-position="top"
        class="support-reply-form"
        @submit.prevent="reply"
      >
        <el-form-item :label="t('phase11.reply')" prop="message">
          <el-input v-model="form.message" type="textarea" :rows="4" maxlength="4000" show-word-limit />
        </el-form-item>
        <el-button
          type="primary"
          :loading="replying"
          :disabled="detail.ticket.status === 'CLOSED'"
          native-type="submit"
          @click="reply"
        >
          {{ t('phase11.reply') }}
        </el-button>
      </el-form>

      <el-empty v-if="!loading && !detail" :description="t('phase11.emptySupportTickets')" />
    </el-card>
  </div>
</template>
