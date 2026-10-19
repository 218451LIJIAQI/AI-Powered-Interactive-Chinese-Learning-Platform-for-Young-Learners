<template>
  <section class="public-page legal-page">
    <div class="legal-intro">
      <button class="btn btn-ghost btn-sm" type="button" @click="goBack">
        <IconGlyph name="chevronLeft" :size="16" />
        {{ backLabel }}
      </button>

      <header class="page-header legal-page-header">
        <div class="page-title">
          <div class="page-title-copy">
            <span class="page-eyebrow">{{ eyebrow }}</span>
            <h1>{{ title }}</h1>
            <slot name="lead" />
            <p>{{ intro }}</p>
            <div v-if="$slots.links" class="legal-related-links">
              <slot name="links" />
            </div>
          </div>
        </div>
      </header>
    </div>

    <div class="legal-layout">
      <nav class="legal-toc" :aria-label="tocLabel">
        <p class="legal-toc-title" aria-hidden="true">{{ tocLabel }}</p>
        <ol>
          <li v-for="section in sections" :key="section.id">
            <a :href="`#${section.id}`">{{ section.title }}</a>
          </li>
        </ol>
      </nav>

      <div class="panel legal-body">
        <section
          v-for="section in sections"
          :id="section.id"
          :key="section.id"
          class="legal-section"
          :aria-labelledby="`${section.id}-title`"
        >
          <h2 :id="`${section.id}-title`">{{ section.title }}</h2>
          <p>{{ section.copy }}</p>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'

export interface LegalSection {
  id: string
  title: string
  copy: string
}

/**
 * Shared long-form layout for the terms, privacy and responsible-AI pages:
 * back action, page header, a table of contents built from the sections, and the readable body.
 */
interface Props {
  eyebrow: string
  title: string
  intro: string
  backLabel: string
  tocLabel: string
  sections: LegalSection[]
}

defineProps<Props>()

const router = useRouter()

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push('/')
  }
}
</script>
