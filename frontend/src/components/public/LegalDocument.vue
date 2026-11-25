<template>
  <article class="public-page public-doc">
    <PageHeaderBlock :title="title" :description="updatedLine">
      <template #before>
        <AppBreadcrumb :items="[{ label: t('nav.home'), to: '/' }, { label: title }]" />
      </template>
      <template v-if="$slots.meta" #meta>
        <slot name="meta" />
      </template>
    </PageHeaderBlock>

    <div class="public-rail-layout">
      <nav ref="rail" class="panel public-rail" :aria-label="t('legal.onThisPage')">
        <p class="page-eyebrow" aria-hidden="true">{{ t('legal.onThisPage') }}</p>
        <ol class="public-rail-list">
          <li v-for="(section, index) in sections" :key="section.id">
            <a
              class="public-rail-link"
              :class="{ 'is-active': section.id === activeId }"
              :href="`#${section.id}`"
              :aria-current="section.id === activeId ? 'location' : undefined"
              @click.exact="pinSection(section.id)"
            >
              <span class="public-rail-number" aria-hidden="true">{{ index + 1 }}.</span>
              <span>{{ section.title }}</span>
            </a>
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
          <p v-if="section.note" class="callout legal-note">
            <IconGlyph name="shield" :size="18" />
            <span>
              <strong>{{ section.note.label }}</strong> {{ section.note.text }}
            </span>
          </p>
          <p v-for="(paragraph, pIndex) in section.paragraphs ?? []" :key="`${section.id}-p-${pIndex}`">
            {{ paragraph }}
          </p>
          <ul v-if="section.bullets?.length" class="legal-bullets">
            <li
              v-for="(bullet, bIndex) in section.bullets"
              :key="typeof bullet === 'string' ? `${section.id}-b-${bIndex}` : bullet.slot"
            >
              <template v-if="typeof bullet === 'string'">{{ bullet }}</template>
              <slot v-else :name="bullet.slot" />
            </li>
          </ul>
        </section>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useRoute } from 'vue-router'

import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { usePublicSectionNav } from '@/components/public/usePublicSectionNav'
import PageHeaderBlock from '@/components/layout/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { legalMessages } from '@/plugins/legalMessages'
import { formatDate } from '@/utils/format'

export interface LegalSection {
  readonly id: string
  readonly title: string
  /** A boxed statement under the heading, such as what is shown with every AI answer. */
  readonly note?: { readonly label: string; readonly text: string }
  readonly paragraphs?: readonly string[]
  /** A plain sentence, or `{ slot }`: the page fills the slot of that name (a sentence with a link in it). */
  readonly bullets?: readonly (string | { readonly slot: string })[]
}

/**
 * The reading layout of the Terms, Privacy, Responsible AI and Safety pages: breadcrumb and H1, a numbered "On this page"
 * rail built from the sections (sticky beside the text, the section in view marked) and one card holding the text.
 */
const props = defineProps<{
  title: string
  sections: readonly LegalSection[]
  /** The day the document last changed (`yyyy-MM-dd`), shown under the H1 in the reader's language. */
  updated?: string
}>()

const { t, locale } = useLocalMessages({ legal: legalMessages })

const UPDATED_DATE: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'long', year: 'numeric' }

const updatedLine = computed(() =>
  props.updated ? t('legal.lastUpdated', { date: formatDate(props.updated, UPDATED_DATE, locale.value) }) : '',
)

const route = useRoute()
const { activeId, pinSection } = usePublicSectionNav(
  () => props.sections,
  useTemplateRef<HTMLElement>('rail'),
  () => route.hash,
)
</script>
