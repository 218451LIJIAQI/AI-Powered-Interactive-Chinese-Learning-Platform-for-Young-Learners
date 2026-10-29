<template>
  <div class="ai-xiaomei-avatar" :class="`is-${state}`" aria-hidden="true">
    <svg viewBox="0 0 200 200" focusable="false">
      <circle class="ai-xiaomei-halo" cx="100" cy="100" r="96" />
      <!-- Hair behind the head: a low ponytail that sways while she talks. -->
      <path class="ai-xiaomei-ponytail" d="M138 78 C170 92 168 150 146 170 C150 140 146 112 128 96 Z" fill="#2f2440" />
      <path d="M58 92 C52 50 80 34 104 34 C134 34 152 56 146 98 L140 120 L62 120 Z" fill="#2f2440" />
      <!-- Shoulders, a plum top and the neck. -->
      <path class="ai-xiaomei-top" d="M40 200 C42 160 66 146 100 146 C134 146 158 160 160 200 Z" />
      <path d="M86 134 L114 134 L116 152 C108 160 92 160 84 152 Z" fill="#efc4a6" />
      <path class="ai-xiaomei-collar" d="M84 150 L100 166 L116 150 L122 156 L100 176 L78 156 Z" />
      <!-- Face and ears. -->
      <ellipse cx="62" cy="98" rx="7" ry="10" fill="#f3c9ad" />
      <ellipse cx="138" cy="98" rx="7" ry="10" fill="#f3c9ad" />
      <ellipse cx="100" cy="96" rx="39" ry="44" fill="#f8d7bf" />
      <!-- Fringe with a side part and a small hair clip. -->
      <path
        d="M60 92 C58 60 78 44 102 44 C126 44 142 58 141 86 C128 72 112 66 98 60 C90 72 76 82 60 92 Z"
        fill="#3a2d4f"
      />
      <path class="ai-xiaomei-clip" d="M126 60 l5 -6 l5 6 l-5 6 Z" />
      <!-- Brows, eyes (they blink; closed while she is away), cheeks. -->
      <path d="M76 82 Q84 77 92 81" stroke="#3a2d4f" stroke-width="3" stroke-linecap="round" fill="none" />
      <path d="M108 81 Q116 77 124 82" stroke="#3a2d4f" stroke-width="3" stroke-linecap="round" fill="none" />
      <g class="ai-xiaomei-eyes">
        <ellipse cx="84" cy="96" rx="5.5" ry="7" fill="#2b2238" />
        <ellipse cx="116" cy="96" rx="5.5" ry="7" fill="#2b2238" />
        <circle cx="86" cy="93" r="1.8" fill="#ffffff" />
        <circle cx="118" cy="93" r="1.8" fill="#ffffff" />
      </g>
      <g class="ai-xiaomei-eyes-closed" stroke="#2b2238" stroke-width="3" stroke-linecap="round" fill="none">
        <path d="M78 96 Q84 101 90 96" />
        <path d="M110 96 Q116 101 122 96" />
      </g>
      <ellipse cx="74" cy="112" rx="7" ry="4.5" fill="#f2a7a2" opacity="0.55" />
      <ellipse cx="126" cy="112" rx="7" ry="4.5" fill="#f2a7a2" opacity="0.55" />
      <!-- Mouth: a smile at rest, an open mouth that moves while she speaks. -->
      <path
        class="ai-xiaomei-smile"
        d="M88 116 Q100 126 112 116"
        stroke="#b5485a"
        stroke-width="3.2"
        stroke-linecap="round"
        fill="none"
      />
      <ellipse class="ai-xiaomei-mouth" cx="100" cy="119" rx="8" ry="5" fill="#b5485a" />
    </svg>
    <span v-if="state === 'thinking'" class="ai-xiaomei-thinking">
      <span />
      <span />
      <span />
    </span>
  </div>
</template>

<script setup lang="ts">
/**
 * Xiaomei, drawn as a friendly illustrated student (not a photo). She smiles at rest, moves her mouth while she
 * speaks, is ringed in plum while she listens, shows thinking dots while SparkBot writes her reply and rests with her
 * eyes closed while the AI is away. Decorative only: the page announces her state in text. Size it with CSS.
 */
export type XiaomeiState = 'idle' | 'speaking' | 'listening' | 'thinking' | 'away'

withDefaults(defineProps<{ state?: XiaomeiState }>(), { state: 'idle' })
</script>
