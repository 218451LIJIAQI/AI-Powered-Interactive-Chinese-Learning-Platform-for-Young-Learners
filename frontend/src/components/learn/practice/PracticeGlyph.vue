<template>
  <span
    class="practice-glyph"
    :class="[`practice-pigment-${info.pigment}`, { 'is-lg': size === 'lg' }]"
    lang="zh-CN"
    aria-hidden="true"
    >{{ info.glyph }}</span
  >
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { PracticeType } from '@/types/learn'

import { practiceTypeInfo } from './practiceTypes'

/** A Practice type's character in a 田字格 tile drawn in the type's pigment (decorative: the type is named in text). */
interface Props {
  type: PracticeType
  size?: 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), { size: 'md' })

const info = computed(() => practiceTypeInfo(props.type))
</script>
