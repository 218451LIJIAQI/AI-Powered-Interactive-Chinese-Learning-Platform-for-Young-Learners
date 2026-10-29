<template>
  <article class="legal-page">
    <PageHeaderBlock :title="title" :description="intro">
      <template #before>
        <AppBreadcrumb :items="[{ label: t('legal.home'), to: '/' }, { label: title }]" />
      </template>
      <template v-if="$slots.lead || $slots.links" #meta>
        <slot name="lead" />
        <div v-if="$slots.links" class="legal-related-links">
          <slot name="links" />
        </div>
      </template>
    </PageHeaderBlock>

    <div class="legal-layout">
      <nav class="legal-toc" :aria-label="t('legal.onThisPage')">
        <p class="legal-toc-title" aria-hidden="true">{{ t('legal.onThisPage') }}</p>
        <ol>
          <li v-for="section in sections" :key="section.id">
            <a :href="`#${section.id}`">{{ section.title }}</a>
          </li>
        </ol>
      </nav>

      <div class="legal-body">
        <section
          v-for="(section, index) in sections"
          :id="section.id"
          :key="section.id"
          class="legal-section"
          :aria-labelledby="`${section.id}-title`"
        >
          <h2 :id="`${section.id}-title`">
            <span class="legal-section-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
            {{ section.title }}
          </h2>
          <p v-for="paragraph in section.paragraphs ?? []" :key="paragraph">{{ paragraph }}</p>
          <ul v-if="section.bullets?.length" class="legal-bullets">
            <li v-for="bullet in section.bullets" :key="bullet">{{ bullet }}</li>
          </ul>
          <p v-if="section.links?.length" class="legal-section-links">
            <RouterLink v-for="link in section.links" :key="link.label" class="btn btn-link" :to="link.to">
              {{ link.label }}
              <IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
          </p>
        </section>
        <slot name="footer" />
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { legalMessages } from '@/plugins/legalMessages'

export interface LegalSection {
  readonly id: string
  readonly title: string
  readonly paragraphs?: readonly string[]
  readonly bullets?: readonly string[]
  readonly links?: readonly { readonly label: string; readonly to: RouteLocationRaw }[]
}

/**
 * The reading layout of the Terms, Privacy, Responsible AI and Safety pages: breadcrumb and header, a numbered
 * "On this page" list built from the sections (sticky beside the text), and the text at a 68-character measure.
 */
defineProps<{
  title: string
  intro: string
  sections: readonly LegalSection[]
}>()

const { t } = useLocalMessages({ legal: legalMessages })
</script>
