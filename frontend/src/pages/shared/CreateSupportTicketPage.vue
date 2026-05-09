<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { supportTicketService } from '../../services/supportTicketService'
import { extractApiError } from '../../utils/apiError'

const router = useRouter()
const { t } = useI18n()
const saving = ref(false)
const form = reactive({
  subject: '',
  message: '',
})

async function submit() {
  saving.value = true
  try {
    const ticket = await supportTicketService.createTicket({
      subject: form.subject,
      message: form.message,
    })
    ElMessage.success(t('phase11.messages.supportTicketCreated'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
    await router.push(`/support-tickets/${ticket.id}`)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.supportEyebrow')"
      :title="t('phase11.createSupportTicket')"
      :description="t('phase11.createSupportTicketDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-form label-position="top">
        <el-form-item :label="t('phase11.ticketSubject')">
          <el-input v-model="form.subject" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('phase11.ticketMessage')">
          <el-input v-model="form.message" type="textarea" :rows="6" maxlength="4000" show-word-limit />
        </el-form-item>
        <div class="table-actions">
          <el-button @click="router.push('/support-tickets')">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submit">{{ t('phase11.submitTicket') }}</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
