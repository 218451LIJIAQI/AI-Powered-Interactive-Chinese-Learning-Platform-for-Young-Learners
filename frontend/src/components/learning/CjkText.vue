<template>
  <template v-for="(run, index) in runs" :key="index">
    <span v-if="run.cjk" lang="zh-CN" class="cjk-text">{{ run.text }}</span>
    <template v-else>{{ run.text }}</template>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { splitCjkRuns } from '@/utils/cjkText'

/** Inline text with Chinese runs marked `lang="zh-CN"` (readable CJK font, correct screen-reader voice). */
const props = withDefaults(defineProps<{ text?: string | null }>(), { text: '' })

const runs = computed(() => splitCjkRuns(props.text))
</script>
