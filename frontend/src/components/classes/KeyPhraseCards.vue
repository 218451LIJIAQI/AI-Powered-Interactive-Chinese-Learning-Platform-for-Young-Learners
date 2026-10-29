<template>
  <ul class="key-phrase-cards">
    <li v-for="(phrase, index) in phrases" :key="`${phrase.chinese}-${index}`" class="key-phrase-card">
      <strong class="hanzi key-phrase-chinese" lang="zh-CN">{{ phrase.chinese }}</strong>
      <span class="key-phrase-copy">
        <span class="key-phrase-pinyin">{{ phrase.pinyin }}</span>
        <span class="key-phrase-meaning">{{ phrase.meaning }}</span>
      </span>
      <button
        v-if="canListen"
        type="button"
        class="btn btn-ghost btn-icon btn-sm key-phrase-listen"
        :aria-label="t('classes.listenTo', { text: phrase.chinese })"
        :title="t('classes.listen')"
        @click="speakChinese(phrase.chinese)"
      >
        <IconGlyph name="audio" :size="16" />
      </button>
    </li>
  </ul>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { KeyPhrase } from '@/types/classes'
import { canSpeak, speakChinese } from '@/utils/speech'

/**
 * Key phrases of a lesson as a phrase sheet: the Chinese, its pinyin and meaning, each read aloud with Listen
 * (browser speech). Used by the student's lesson, the teacher's lesson preview and the admin content review.
 */
defineProps<{ phrases: readonly KeyPhrase[] }>()

const { t } = useLocalMessages({ classes: classesMessages })
const canListen = canSpeak()
</script>
