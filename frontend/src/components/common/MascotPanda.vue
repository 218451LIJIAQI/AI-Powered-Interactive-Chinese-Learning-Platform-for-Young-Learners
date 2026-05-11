<script setup lang="ts">
import { computed, ref } from 'vue'

import { useMascotEyes } from '../../composables/useMascotEyes'

const props = withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
    bubble?: string
    holding?: string
    /** Set to false to disable cursor-tracking eyes (e.g. in dense lists). */
    trackEyes?: boolean
  }>(),
  {
    size: 'md',
    bubble: '',
    holding: '\u4f60\u597d',
    trackEyes: true,
  },
)

const svgRef = ref<SVGElement | null>(null)
const { offset } = useMascotEyes(svgRef, { maxOffset: 2.6 })

// Both eyes share the same transform — they look in the same direction.
const pupilTransform = computed(() =>
  props.trackEyes
    ? `translate(${offset.value.x.toFixed(2)} ${offset.value.y.toFixed(2)})`
    : 'translate(0 0)',
)
</script>

<template>
  <div class="kid-mascot" :class="`kid-mascot--${size}`" aria-hidden="true">
    <svg
      ref="svgRef"
      class="kid-mascot__svg"
      viewBox="0 0 200 200"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
    >
      <!-- Soft shadow -->
      <ellipse cx="100" cy="184" rx="60" ry="6" fill="rgba(43, 29, 79, 0.18)" />

      <!-- Body -->
      <ellipse cx="100" cy="138" rx="58" ry="48" fill="#ffffff" stroke="#2b1d4f" stroke-width="3" />
      <ellipse cx="100" cy="142" rx="40" ry="32" fill="#fff7e6" />

      <!-- Arms -->
      <ellipse cx="48" cy="130" rx="16" ry="22" fill="#2b1d4f" transform="rotate(-18 48 130)" />
      <ellipse cx="152" cy="120" rx="16" ry="22" fill="#2b1d4f" transform="rotate(28 152 120)">
        <animateTransform
          attributeName="transform"
          type="rotate"
          from="22 152 120"
          to="34 152 120"
          dur="2.4s"
          values="22 152 120; 34 152 120; 22 152 120"
          repeatCount="indefinite"
        />
      </ellipse>

      <!-- Hand holding scroll/character -->
      <g transform="translate(154 102)">
        <rect x="-18" y="-18" width="36" height="36" rx="9" fill="#ffd23f" stroke="#2b1d4f" stroke-width="2.5">
          <animate
            attributeName="y"
            values="-18; -22; -18"
            keyTimes="0; 0.5; 1"
            dur="2.4s"
            repeatCount="indefinite"
          />
        </rect>
        <text
          x="0"
          y="6"
          text-anchor="middle"
          font-family="'Baloo 2','Noto Sans SC',sans-serif"
          font-size="22"
          font-weight="800"
          fill="#2b1d4f"
        >
          {{ holding }}
          <animate
            attributeName="y"
            values="6; 2; 6"
            keyTimes="0; 0.5; 1"
            dur="2.4s"
            repeatCount="indefinite"
          />
        </text>
      </g>

      <!-- Legs -->
      <ellipse cx="78" cy="178" rx="14" ry="10" fill="#2b1d4f" />
      <ellipse cx="122" cy="178" rx="14" ry="10" fill="#2b1d4f" />

      <!-- Head -->
      <circle cx="100" cy="78" r="48" fill="#ffffff" stroke="#2b1d4f" stroke-width="3" />

      <!-- Ears -->
      <circle cx="62" cy="40" r="16" fill="#2b1d4f" />
      <circle cx="138" cy="40" r="16" fill="#2b1d4f" />
      <circle cx="62" cy="40" r="8" fill="#ff5d8f" opacity="0.7" />
      <circle cx="138" cy="40" r="8" fill="#ff5d8f" opacity="0.7" />

      <!-- Eye patches -->
      <ellipse cx="80" cy="78" rx="12" ry="16" fill="#2b1d4f" transform="rotate(-15 80 78)" />
      <ellipse cx="120" cy="78" rx="12" ry="16" fill="#2b1d4f" transform="rotate(15 120 78)" />

      <!-- Eyes (sclera + pupil; pupil tracks cursor) -->
      <g>
        <circle cx="80" cy="80" r="5" fill="#ffffff" />
        <g :transform="pupilTransform">
          <circle cx="80" cy="80" r="2.4" fill="#2b1d4f">
            <animate
              attributeName="r"
              values="2.4;0.5;2.4;2.4"
              keyTimes="0;0.05;0.1;1"
              dur="5s"
              repeatCount="indefinite"
            />
          </circle>
          <circle cx="80.7" cy="79.4" r="0.7" fill="#fff" />
        </g>
      </g>
      <g>
        <circle cx="120" cy="80" r="5" fill="#ffffff" />
        <g :transform="pupilTransform">
          <circle cx="120" cy="80" r="2.4" fill="#2b1d4f">
            <animate
              attributeName="r"
              values="2.4;0.5;2.4;2.4"
              keyTimes="0;0.05;0.1;1"
              dur="5s"
              repeatCount="indefinite"
            />
          </circle>
          <circle cx="120.7" cy="79.4" r="0.7" fill="#fff" />
        </g>
      </g>

      <!-- Cheeks -->
      <circle cx="66" cy="98" r="6" fill="#ff5d8f" opacity="0.5">
        <animate
          attributeName="opacity"
          values="0.45;0.65;0.45"
          dur="3s"
          repeatCount="indefinite"
        />
      </circle>
      <circle cx="134" cy="98" r="6" fill="#ff5d8f" opacity="0.5">
        <animate
          attributeName="opacity"
          values="0.45;0.65;0.45"
          dur="3s"
          repeatCount="indefinite"
        />
      </circle>

      <!-- Nose -->
      <ellipse cx="100" cy="98" rx="6" ry="4" fill="#2b1d4f" />

      <!-- Smile -->
      <path
        d="M88 108 Q100 120 112 108"
        stroke="#2b1d4f"
        stroke-width="3"
        stroke-linecap="round"
        fill="none"
      />
      <path
        d="M94 113 Q100 117 106 113"
        stroke="#ff5d8f"
        stroke-width="2"
        stroke-linecap="round"
        fill="none"
      />
    </svg>
    <div v-if="bubble" class="kid-mascot__bubble">{{ bubble }}</div>
  </div>
</template>
