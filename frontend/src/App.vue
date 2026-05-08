<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

function handleForbidden(event: Event) {
  const detail = (event as CustomEvent<string>).detail
  ElMessage.error(detail || 'You do not have permission to access that page.')
}

onMounted(() => {
  window.addEventListener('app:forbidden', handleForbidden)
})

onBeforeUnmount(() => {
  window.removeEventListener('app:forbidden', handleForbidden)
})
</script>

<template>
  <router-view />
</template>
