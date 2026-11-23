<template>
  <Teleport to="body">
    <div v-if="shown" class="overlay" :class="`overlay--${variant}`" @mousedown.self="requestClose">
      <section
        ref="panel"
        tabindex="-1"
        class="overlay-panel"
        :class="[`overlay-panel--${variant}`, `is-${size}`]"
        :role="role"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="describedBy"
        :aria-busy="busy || undefined"
        :data-testid="testId"
        @keydown="keepPanelKeys"
      >
        <div class="overlay-header">
          <div class="overlay-heading">
            <h2 :id="titleId" class="overlay-title">{{ title }}</h2>
            <p v-if="subtitle" :id="subtitleId" class="overlay-subtitle">{{ subtitle }}</p>
          </div>
          <button
            type="button"
            class="btn btn-ghost btn-icon btn-sm"
            :aria-label="t('common.close')"
            :disabled="busy"
            @click="requestClose"
          >
            <IconGlyph name="close" :size="18" />
          </button>
        </div>
        <div
          ref="body"
          class="overlay-body"
          :tabindex="bodyScrolls ? 0 : undefined"
          :role="bodyScrolls ? 'group' : undefined"
          :aria-labelledby="bodyScrolls ? titleId : undefined"
        >
          <div class="overlay-body-content"><slot /></div>
        </div>
        <div v-if="$slots.footer" class="overlay-footer">
          <slot name="footer" />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useOverlayFocus } from '@/composables/useOverlayFocus'

/**
 * The one modal layer of the app: a centred dialog (`variant="dialog"`) or a side drawer (`variant="drawer"`) with a
 * title, a body and an optional footer of actions. Escape, the close button and a press on the scrim close it, except
 * while `busy`; focus is trapped inside while it is open, starts on a `[data-autofocus]` element when there is one, and
 * returns to the opener afterwards. `open` defaults to true, so a parent may also show it with `v-if`.
 *
 * Inside a part of the page kept alive behind the others (the AI Helper's tabs), an open overlay leaves the screen with
 * that part and comes back with it: drawn in the body, it would otherwise stay over the next tab, and its buttons
 * (Allow microphone…) would act in a tab no longer on screen.
 */
interface Props {
  title: string
  open?: boolean
  subtitle?: string
  variant?: 'dialog' | 'drawer'
  /** md: a 520px dialog or a 480px drawer; lg: 720px wide, either way. */
  size?: 'md' | 'lg'
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
  busy: false,
  role: 'dialog',
  describedById: undefined,
  testId: undefined,
})

const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

const panel = ref<HTMLElement | null>(null)
const body = ref<HTMLElement | null>(null)
const bodyScrolls = ref(false)
const baseId = useId()
const titleId = `${baseId}-title`
const subtitleId = `${baseId}-subtitle`
const describedBy = computed(() => props.describedById ?? (props.subtitle ? subtitleId : undefined))

// A long read-only drawer needs a keyboard scroll target. Short dialogs keep their natural tab order.
watch(
  body,
  (element, _, onCleanup) => {
    bodyScrolls.value = false
    if (!element || typeof ResizeObserver === 'undefined') return
    const measure = () => {
      bodyScrolls.value = element.scrollHeight > element.clientHeight + 1
    }
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    if (element.firstElementChild) observer.observe(element.firstElementChild)
    onCleanup(() => observer.disconnect())
    measure()
  },
  { flush: 'post' },
)

/** False while a KeepAlive ancestor is deactivated (these hooks never run outside one). */
const onScreen = ref(true)
onActivated(() => {
  onScreen.value = true
})
onDeactivated(() => {
  onScreen.value = false
})
const shown = computed(() => props.open && onScreen.value)

function requestClose(): void {
  if (!props.busy) emit('close')
}

/** The panel's own controls receive their keys before page shortcuts. Escape and Tab reach the focus trap. */
function keepPanelKeys(event: KeyboardEvent): void {
  if (event.key !== 'Escape' && event.key !== 'Tab') event.stopPropagation()
}

useOverlayFocus({
  active: shown,
  container: panel,
  onClose: requestClose,
})
</script>
