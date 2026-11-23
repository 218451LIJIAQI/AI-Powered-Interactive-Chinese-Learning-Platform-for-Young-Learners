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
        @click="speak(phrase.chinese)"
      >
        <IconGlyph name="audio" :size="16" />{{ t('classes.listen') }}
      </button>
    </li>
  </ul>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { classesMessages } from '@/plugins/classesMessages'
import type { KeyPhrase } from '@/types/classes'

/**
 * Key phrases of a lesson as sentence rows: the Chinese with its pinyin, the meaning, and Listen (browser speech). Used
 * by the student's lesson and the admin lesson preview.
 */
defineProps<{ phrases: readonly KeyPhrase[] }>()

const { t } = useLocalMessages({ classes: classesMessages })
const { canSpeak: canListen, speak } = useMandarinReadAloud()
</script>

<style scoped>
/* Key phrases: sentence rows, the Chinese and its pinyin, the meaning, Listen on the right edge. */
.key-phrase-rows > li.key-phrase-row {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr) auto;
  padding: var(--space-2) 0;
}

.key-phrase-copy {
  display: grid;
  min-width: 0;
}

.key-phrase-chinese {
  font-size: var(--font-size-xl);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.key-phrase-meaning {
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

@media (max-width: 48rem) {
  .key-phrase-rows > li.key-phrase-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .key-phrase-rows > li.key-phrase-row > .key-phrase-meaning {
    grid-row: 2;
    grid-column: 1 / -1;
  }
}
</style>
