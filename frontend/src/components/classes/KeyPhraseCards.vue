<template>
  <ul class="row-list key-phrase-rows">
    <li v-for="(phrase, index) in phrases" :key="`${phrase.chinese}-${index}`" class="key-phrase-row">
      <span class="key-phrase-copy">
        <strong class="hanzi key-phrase-chinese" lang="zh-CN">{{ phrase.chinese }}</strong>
        <span class="row-list-meta">{{ phrase.pinyin }}</span>
      </span>
      <span class="key-phrase-meaning">{{ phrase.meaning }}</span>
      <button
        v-if="canListen"
        type="button"
        class="btn btn-ghost btn-sm"
        :aria-label="t('classes.listenTo', { text: phrase.chinese })"
        @click="speakChinese(phrase.chinese)"
      >
        <IconGlyph name="audio" :size="16" />{{ t('classes.listen') }}
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
 * Key phrases of a lesson as sentence rows: the Chinese with its pinyin, the meaning, and Listen (browser speech). Used
 * by the student's lesson, the teacher's lesson preview and the admin content review.
 */
defineProps<{ phrases: readonly KeyPhrase[] }>()

const { t } = useLocalMessages({ classes: classesMessages })
const canListen = canSpeak()
</script>
