<template>
  <figure class="learn-stamp">
    <span class="seal learn-stamp-seal" lang="zh-CN" aria-hidden="true">{{ character }}</span>
    <figcaption class="learn-stamp-caption">{{ caption }}</figcaption>
  </figure>
</template>

<script setup lang="ts">
/**
 * The red 印章 every finished task is stamped with, landing with a short press: 棒 (bàng, great job) after a lesson,
 * 好 (hǎo, well done) after practice, 加 (jiā yóu, keep going) when it is not passed yet.
 */
defineProps<{
  character: string
  /** Pinyin and meaning of the character, e.g. "bàng · great job!". */
  caption: string
}>()
</script>
