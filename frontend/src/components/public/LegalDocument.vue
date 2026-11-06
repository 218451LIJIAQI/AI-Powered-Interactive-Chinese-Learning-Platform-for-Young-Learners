<template>
  <article class="public-page public-doc">
    <PageHeaderBlock :title="title">
      <template #before>
        <AppBreadcrumb :items="[{ label: t('legal.home'), to: '/' }, { label: title }]" />
      </template>
      <template v-if="$slots.meta" #meta>
        <slot name="meta" />
      </template>
    </PageHeaderBlock>

    <div class="public-rail-layout">
      <nav class="panel public-rail" :aria-label="t('legal.onThisPage')">
        <p class="page-eyebrow" aria-hidden="true">{{ t('legal.onThisPage') }}</p>
        <ol class="public-rail-list">
          <li v-for="(section, index) in sections" :key="section.id">
            <a
              class="public-rail-link"
              :class="{ 'is-active': section.id === activeId }"
              :href="`#${section.id}`"
              :aria-current="section.id === activeId ? 'location' : undefined"
              @click="pinSection(section.id)"
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
              <span>
                <strong>{{ section.note.label }}</strong> {{ section.note.text }}
              </span>
            </span>
          </p>
          <p v-for="paragraph in section.paragraphs ?? []" :key="paragraph">{{ paragraph }}</p>
          <ul v-if="section.bullets?.length" class="legal-bullets">
            <li v-for="bullet in section.bullets" :key="bullet">{{ bullet }}</li>
          </ul>
        </section>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { legalMessages } from '@/plugins/legalMessages'

export interface LegalSection {
  readonly id: string
  readonly title: string
  /** A boxed statement under the heading, such as what is shown with every AI answer. */
  readonly note?: { readonly label: string; readonly text: string }
  readonly paragraphs?: readonly string[]
  readonly bullets?: readonly string[]
}

/**
 * The reading layout of the Terms, Privacy, Responsible AI and Safety pages: breadcrumb and H1, a numbered "On this page"
 * rail built from the sections (sticky beside the text, the section in view marked) and one card holding the text.
 */
const props = defineProps<{
  title: string
  sections: readonly LegalSection[]
}>()

const { t } = useLocalMessages({ legal: legalMessages })

/** A section counts as in view once its top has passed this line, just under the sticky header. */
const IN_VIEW_LINE = 160

const activeId = ref<string | undefined>(props.sections[0]?.id)
/** A row that was clicked or linked to stays marked until the reader scrolls: short last sections never reach the line. */
let pinned = false

function pinSection(id: string): void {
  activeId.value = id
  pinned = true
}

function unpin(): void {
  pinned = false
}

function markSectionInView(): void {
  if (pinned) return
  let current = props.sections[0]?.id
  for (const { id } of props.sections) {
    const top = document.getElementById(id)?.getBoundingClientRect().top
    if (top !== undefined && top <= IN_VIEW_LINE) current = id
  }
  const atPageEnd = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 1
  activeId.value = atPageEnd ? props.sections.at(-1)?.id : current
}

onMounted(() => {
  const linked = window.location.hash.slice(1)
  if (props.sections.some(({ id }) => id === linked)) pinSection(linked)
  window.addEventListener('scroll', markSectionInView, { passive: true })
  window.addEventListener('wheel', unpin, { passive: true })
  window.addEventListener('keydown', unpin)
  window.addEventListener('pointerdown', unpin)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', markSectionInView)
  window.removeEventListener('wheel', unpin)
  window.removeEventListener('keydown', unpin)
  window.removeEventListener('pointerdown', unpin)
})
</script>
