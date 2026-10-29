<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" :class="`overlay--${variant}`" @mousedown.self="requestClose">
      <section
        ref="panel"
        class="overlay-panel"
        :class="[`overlay-panel--${variant}`, `is-${size}`]"
        :role="role"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="describedBy"
        :aria-busy="busy || undefined"
        :data-testid="testId"
      >
        <header class="overlay-header">
          <div class="overlay-heading">
            <h2 :id="titleId" class="overlay-title">{{ title }}</h2>
            <p v-if="subtitle" :id="subtitleId" class="overlay-subtitle">{{ subtitle }}</p>
          </div>
          <button
            v-if="closable"
            type="button"
            class="btn btn-ghost btn-icon btn-sm"
            :aria-label="t('common.close')"
            :disabled="busy"
            @click="requestClose"
          >
            <IconGlyph name="close" :size="18" />
          </button>
        </header>
        <div class="overlay-body">
          <slot :description-id="subtitleId" />
        </div>
        <footer v-if="$slots.footer" class="overlay-footer">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useOverlayFocus } from '@/composables/useOverlayFocus'
import { commonMessages } from '@/plugins/commonMessages'

/**
 * The one modal layer of the app: a centred dialog (`variant="dialog"`) or a side drawer (`variant="drawer"`) with a
 * title, a body and an optional footer of actions. Escape, the
 * close button and a press on the scrim close it, except while `busy`; focus is trapped inside while it is open,
 * starts on a `[data-autofocus]` element when there is one, and returns to the opener afterwards. `open` defaults to
 * true, so a parent may also show it with `v-if`.
 */
interface Props {
  title: string
  open?: boolean
  subtitle?: string
  variant?: 'dialog' | 'drawer'
  /** Dialog: sm 440px, md 560px, lg 680px. Drawer: md 480px, lg 720px (editors with tables). */
  size?: 'sm' | 'md' | 'lg'
  /** Shows the × button in the header (confirm dialogs close through their own buttons). */
  closable?: boolean
  /** True while an action runs: the overlay cannot be closed meanwhile. */
  busy?: boolean
  role?: 'dialog' | 'alertdialog'
  /** Id of the element that describes the overlay; defaults to the subtitle. */
  describedById?: string
  testId?: string
}

const props = withDefaults(defineProps<Props>(), {
  open: true,
  subtitle: '',
  variant: 'dialog',
  size: 'md',
  closable: true,
  busy: false,
  role: 'dialog',
  describedById: undefined,
  testId: undefined,
})

const emit = defineEmits<{ close: [] }>()
const { t } = useLocalMessages({ common: commonMessages })

const panel = ref<HTMLElement | null>(null)
const baseId = useId()
const titleId = `${baseId}-title`
const subtitleId = `${baseId}-subtitle`
const describedBy = computed(() => props.describedById ?? (props.subtitle ? subtitleId : undefined))

function requestClose(): void {
  if (!props.busy) emit('close')
}

useOverlayFocus({
  active: computed(() => props.open),
  container: panel,
  onClose: requestClose,
})
</script>
