<template>
  <LegalDocument :title="title" :sections="sections" :updated="RESPONSIBLE_AI_UPDATED" />
</template>

<script setup lang="ts">
import LegalDocument from '@/components/public/LegalDocument.vue'
import { configuredTavusDeploymentId } from '@/composables/usePublicPlatformSettings'
import { RESPONSIBLE_AI_UPDATED, useResponsibleAiGuidance } from '@/composables/useResponsibleAiGuidance'

const { title, sections } = useResponsibleAiGuidance(Boolean(configuredTavusDeploymentId()))
</script>
