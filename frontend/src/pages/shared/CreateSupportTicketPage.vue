<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useRoleBasePath } from '../../composables/useRoleBasePath'
import { APP_EVENTS, dispatchAppEvent } from '../../constants/appEvents'
import { supportTicketService } from '../../services/supportTicketService'
import { extractApiError } from '../../utils/apiError'

const router = useRouter()
const { t } = useI18n()
const basePath = useRoleBasePath()
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)
const form = reactive({
  subject: '',
  message: '',
})

const rules = computed<FormRules>(() => ({
  subject: [
    { required: true, message: t('phase11.validation.subjectRequired'), trigger: 'blur' },
    { min: 5, message: t('phase11.validation.subjectMin'), trigger: 'blur' },
  ],
  message: [
    { required: true, message: t('phase11.validation.messageRequired'), trigger: 'blur' },
    { min: 10, message: t('phase11.validation.messageMin'), trigger: 'blur' },
  ],
}))

async function submit() {
  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const ticket = await supportTicketService.createTicket({
      subject: form.subject.trim(),
      message: form.message.trim(),
    })
    ElMessage.success(t('phase11.messages.supportTicketCreated'))
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    await router.push(`${basePath.value}/support-tickets/${ticket.id}`)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push(`${basePath.value}/support-tickets`)
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
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item :label="t('phase11.ticketSubject')" prop="subject">
          <el-input v-model="form.subject" maxlength="200" show-word-limit clearable />
        </el-form-item>
        <el-form-item :label="t('phase11.ticketMessage')" prop="message">
          <el-input v-model="form.message" type="textarea" :rows="6" maxlength="4000" show-word-limit />
        </el-form-item>
        <div class="table-actions">
          <el-button @click="cancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" native-type="submit" @click="submit">{{ t('phase11.submitTicket') }}</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
