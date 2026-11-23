<template>
  <AppOverlay variant="drawer" :title="title" test-id="ai-responsible-drawer" @close="emit('close')">
    <div class="ai-guidance">
      <section
        v-for="section in sections"
        :key="section.id"
        class="ai-guidance-section"
        :aria-labelledby="`${baseId}-${section.id}`"
      >
        <h3 :id="`${baseId}-${section.id}`" class="ai-guidance-title">{{ section.title }}</h3>
        <p v-if="section.note" class="callout ai-guidance-note">
          <IconGlyph name="shield" :size="16" />
          <span>
            <strong>{{ section.note.label }}</strong> {{ section.note.text }}
          </span>
        </p>
        <p v-for="paragraph in section.paragraphs ?? []" :key="paragraph" class="ai-guidance-copy">{{ paragraph }}</p>
      </section>
    </div>
  </AppOverlay>
</template>

<script setup lang="ts">
import { useId } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useResponsibleAiGuidance } from '@/composables/useResponsibleAiGuidance'

/**
 * The Responsible AI guidance in a drawer over the learner's page (X2.22): the seven sections of the public page, read
 * in place and scrolled in the drawer, so a signed-in learner never lands on the public site. The ×, Escape or the
 * scrim close it.
 */
const props = defineProps<{ liveVideo?: boolean }>()
const emit = defineEmits<{ close: [] }>()

const baseId = useId()
const { title, sections } = useResponsibleAiGuidance(() => Boolean(props.liveVideo))
</script>

<style scoped>
/* Scoped: the drawer renders in the body, outside the AI Helper's own stylesheet. */
.ai-guidance {
  display: grid;
  gap: var(--space-6);
}

.ai-guidance-section {
  display: grid;
  gap: var(--space-2);
}

.ai-guidance-title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-strong);
  line-height: 22px;
}

.ai-guidance-copy {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.ai-guidance-note {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.ai-guidance-note .icon-glyph {
  margin-top: var(--space-0-5);
  color: var(--text-brand);
}
</style>
