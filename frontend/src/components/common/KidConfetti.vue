<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    trigger?: number | boolean
    pieces?: number
  }>(),
  {
    trigger: 0,
    pieces: 60,
  },
)

const palette = ['#ffd23f', '#ff9f1c', '#ff5d8f', '#b15dff', '#2ec4b6', '#4cc9f0', '#ffffff']

type Piece = {
  id: number
  color: string
  left: string
  top: string
  size: number
  rotate: string
  tx: string
  ty: string
  spin: string
  delay: string
  duration: string
}

const visible = ref(false)
const seed = ref(0)
let hideTimeoutId: number | null = null

const pieces = computed<Piece[]>(() => {
  void seed.value
  return Array.from({ length: props.pieces }).map((_, idx) => {
    const angle = Math.random() * Math.PI * 2
    const distance = 220 + Math.random() * 320
    return {
      id: idx,
      color: palette[Math.floor(Math.random() * palette.length)],
      left: '50%',
      top: '40%',
      size: 6 + Math.random() * 12,
      rotate: `${Math.random() * 360}deg`,
      tx: `${Math.cos(angle) * distance}px`,
      ty: `${Math.sin(angle) * distance + 200}px`,
      spin: `${Math.random() * 720 - 360}deg`,
      delay: `${Math.random() * 0.15}s`,
      duration: `${1 + Math.random() * 0.8}s`,
    }
  })
})

function fire() {
  seed.value++
  visible.value = true
  if (hideTimeoutId !== null) {
    window.clearTimeout(hideTimeoutId)
  }
  hideTimeoutId = window.setTimeout(() => {
    hideTimeoutId = null
    visible.value = false
  }, 1800)
}

watch(
  () => props.trigger,
  (val, prev) => {
    if (val && val !== prev) {
      fire()
    }
  },
)

onMounted(() => {
  if (props.trigger) {
    fire()
  }
})

onBeforeUnmount(() => {
  if (hideTimeoutId !== null) {
    window.clearTimeout(hideTimeoutId)
    hideTimeoutId = null
  }
})

defineExpose({ fire })
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="kid-confetti" aria-hidden="true">
      <span
        v-for="piece in pieces"
        :key="piece.id"
        class="kid-confetti__piece"
        :style="{
          background: piece.color,
          left: piece.left,
          top: piece.top,
          width: `${piece.size}px`,
          height: `${piece.size * 1.4}px`,
          transform: `rotate(${piece.rotate})`,
          animationDelay: piece.delay,
          animationDuration: piece.duration,
          // Custom CSS variables consumed by the kid-confetti-fall keyframe.
          '--tx': piece.tx,
          '--ty': piece.ty,
          '--r': piece.spin,
        } as Record<string, string>"
      />
    </div>
  </Teleport>
</template>
