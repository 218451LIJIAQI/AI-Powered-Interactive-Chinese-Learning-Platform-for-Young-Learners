<template>
  <StatePanel icon="safe" tone="neutral" :title="title" :message="message" data-testid="teacher-approval-lock">
    <template #actions>
      <RouterLink class="btn btn-primary" :to="{ name: 'teacher-profile' }">
        {{ t('governance.lockedOpenProfile') }}
      </RouterLink>
    </template>
  </StatePanel>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import StatePanel from '@/components/common/StatePanel.vue'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'

/** Friendly locked state for teaching surfaces while the teacher account is not approved (D13). */
defineProps<{ title: string; message: string }>()

const { t } = useI18n({
  messages: {
    en: { governance: contentGovernanceMessages.en },
    ms: { governance: contentGovernanceMessages.ms },
    'zh-CN': { governance: contentGovernanceMessages['zh-CN'] },
  },
})
</script>
