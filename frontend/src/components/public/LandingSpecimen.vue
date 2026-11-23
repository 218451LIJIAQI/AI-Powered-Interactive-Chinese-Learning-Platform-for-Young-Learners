<template>
  <!-- Decoration only: the heading and the sentence under it say what the part is for. -->
  <div class="landing-art" :class="`is-${kind}`" aria-hidden="true">
    <!-- Learn: the path. Two lessons done, the one you are on, the next still locked. -->
    <svg v-if="kind === 'path'" class="landing-art-path" viewBox="0 0 240 96" focusable="false">
      <path class="landing-art-trail" d="M36 62C58 62 70 34 92 34S126 62 148 62S182 34 204 34" />
      <path class="landing-art-trail is-done" d="M36 62C58 62 70 34 92 34S126 62 148 62" />
      <g v-for="node in DONE_NODES" :key="node" class="landing-art-node is-done" :transform="node">
        <circle r="15" />
        <path d="M-5.5 0.5l3.5 3.5l7.5-8" />
      </g>
      <g class="landing-art-node is-here" transform="translate(148 62)">
        <circle class="landing-art-halo" r="22" />
        <circle r="15" />
        <circle class="landing-art-dot" r="5" />
      </g>
      <g class="landing-art-node is-locked" transform="translate(204 34)">
        <circle r="15" />
        <path d="M-3.5-1v-2.5a3.5 3.5 0 0 1 7 0V-1" />
        <rect x="-5.5" y="-1" width="11" height="8.5" rx="2" />
      </g>
    </svg>

    <!-- Practice: a flashcard on its pile. -->
    <div v-else-if="kind === 'cards'" class="landing-art-cards">
      <span class="landing-art-card is-under" />
      <span class="landing-art-card">
        <span class="landing-art-hanzi" lang="zh-CN">谢谢</span>
        <span class="landing-art-pinyin" lang="zh-Latn-pinyin">xièxie</span>
      </span>
    </div>

    <!-- AI Helper: a question from the talk partner, and a spoken reply. -->
    <div v-else-if="kind === 'chat'" class="landing-art-chat">
      <span class="landing-art-bubble" lang="zh-CN">你叫什么名字？</span>
      <span class="landing-art-bubble is-reply">
        <IconGlyph name="mic" :size="14" />
        <span class="landing-art-wave"><i v-for="bar in 7" :key="bar" /></span>
      </span>
    </div>

    <!-- Classes: three of the scenarios a themed class is about. -->
    <div v-else class="landing-art-scenes">
      <span class="landing-art-scene"><IconGlyph name="utensils" :size="20" /></span>
      <span class="landing-art-scene is-front"><IconGlyph name="shopping" :size="22" /></span>
      <span class="landing-art-scene"><IconGlyph name="briefcase" :size="20" /></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * The picture over one part of "How MandarinSpark works" on the landing page: a specimen of what the part looks like,
 * drawn with the product's own pieces on the part's tint. Its Chinese is the subject matter, the same in every
 * interface language, so nothing here is translated.
 */
defineProps<{ kind: 'path' | 'cards' | 'chat' | 'scenes' }>()

/** The finished lessons of the path, by where their nodes sit. */
const DONE_NODES = ['translate(36 62)', 'translate(92 34)'] as const
</script>
