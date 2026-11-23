<template>
  <tavus-embed
    v-if="loaded"
    ref="embed"
    :deployment-id="deploymentId"
    :conversational-context="context"
    :custom-greeting="greeting"
    :override-config="EMBED_OPTIONS"
    camera-on-start="off"
    @tavus:ready="startConversation"
    @tavus:conversation-started="onConnected"
    @tavus:conversation-ended="onEnded"
    @tavus:mode-change="onModeChange"
    @tavus:error="onTavusError"
    @tavus:protocol-message="onProtocolMessage"
  ></tavus-embed>
  <Transition name="xiaomei-loading">
    <div v-if="!ready" class="xiaomei-live-placeholder" aria-hidden="true">
      <XiaomeiAvatar class="xiaomei-live-portrait" state="idle" />
      <span class="spinner is-sm" />
    </div>
  </Transition>
</template>

<script setup lang="ts">
import type { TavusElement, TavusEventMap } from '@tavus/embed'
import { onBeforeUnmount, onMounted, ref } from 'vue'

import type { XiaomeiTurn } from '@/types/ai'

import XiaomeiAvatar from './XiaomeiAvatar.vue'

defineProps<{ deploymentId: string; context: string; greeting: string }>()
const emit = defineEmits<{
  connected: []
  ended: []
  failed: [code?: string]
  utterance: [speaker: XiaomeiTurn['speaker'], text: string]
}>()

const EMBED_OPTIONS = JSON.stringify({ customization: { conversation: { unique_memory_tag: false } } })
const embed = ref<TavusElement | null>(null)
const loaded = ref(false)
const ready = ref(false)
let active = true

function startConversation(): void {
  if (!active) return
  ready.value = true
  // The Talk tab's own microphone button mounted this element: the call starts once Tavus has its session methods.
  void embed.value?.startConversation?.().catch(() => {
    if (active) emit('failed')
  })
}

function onConnected(): void {
  if (active) emit('connected')
}

function onEnded(): void {
  if (active) emit('ended')
}

function onModeChange(event: Event): void {
  if (!active) return
  if ((event as CustomEvent<TavusEventMap['tavus:mode-change']>).detail?.mode === 'unavailable') {
    emit('failed')
  }
}

function onTavusError(event: Event): void {
  if (!active) return
  emit('failed', (event as CustomEvent<TavusEventMap['tavus:error']>).detail?.code)
}

function onProtocolMessage(event: Event): void {
  if (!active) return
  const speechEvent = (
    event as CustomEvent<{
      event_type?: string
      properties?: { role?: string; speech?: unknown; final?: boolean }
    }>
  ).detail
  const properties = speechEvent?.properties
  // Tavus sends one complete utterance without a final field; boolean flags belong to its streaming previews.
  if (speechEvent?.event_type !== 'conversation.utterance' || !properties || typeof properties.final === 'boolean') {
    return
  }
  const { role, speech } = properties
  const text = typeof speech === 'string' ? speech.trim() : ''
  if (!text) return
  if (role === 'user') emit('utterance', 'STUDENT', text)
  else if (role === 'replica' || role === 'pal') emit('utterance', 'XIAOMEI', text)
}

onMounted(async () => {
  try {
    await import('@tavus/embed')
    if (active) loaded.value = true
  } catch {
    if (active) emit('failed')
  }
})

onBeforeUnmount(() => {
  active = false
  // Cancel any pending join as well as a connected call.
  void embed.value?.endConversation?.().catch(() => {})
})
</script>

<style scoped>
tavus-embed,
.xiaomei-live-placeholder {
  position: absolute;
  inset: 0;
}

tavus-embed {
  display: block;
  z-index: 0;
  width: 100%;
  height: 100%;
}

.xiaomei-live-placeholder {
  display: grid;
  z-index: 1;
  place-content: center;
  justify-items: center;
  gap: var(--space-3);
  background:
    var(--pattern-grid-paper) 50% 100% / var(--pattern-grid-size),
    radial-gradient(ellipse at 50% 65%, var(--color-primary-100), var(--color-primary-50) 70%);
  pointer-events: none;
}

.xiaomei-live-portrait {
  --ai-avatar-size: clamp(76px, 15dvh, 124px);
}

@media (prefers-reduced-motion: no-preference) {
  .xiaomei-loading-leave-active {
    transition: opacity var(--duration-slow) var(--ease-out);
  }

  .xiaomei-loading-leave-to {
    opacity: 0;
  }
}
</style>
