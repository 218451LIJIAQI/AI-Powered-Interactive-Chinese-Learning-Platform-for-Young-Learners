<script setup lang="ts">
import KidSparkles from './KidSparkles.vue'
import MascotPanda from './MascotPanda.vue'

withDefaults(
  defineProps<{
    eyebrow: string
    title: string
    description: string
    /** Set to true on student-facing pages to show sparkles + mascot bubble */
    kid?: boolean
    mascotMessage?: string
    mascotHolding?: string
    showMascot?: boolean
  }>(),
  {
    kid: false,
    mascotMessage: '',
    mascotHolding: '\u4f60\u597d',
    showMascot: false,
  },
)
</script>

<template>
  <section class="page-hero" :class="{ 'page-hero--kid': kid }">
    <KidSparkles v-if="kid" :count="10" />
    <div class="page-hero__inner">
      <div class="page-hero__copy">
        <p class="page-hero__eyebrow">{{ eyebrow }}</p>
        <h1>{{ title }}</h1>
        <p class="page-hero__description">{{ description }}</p>
      </div>
      <MascotPanda
        v-if="kid && showMascot"
        size="md"
        :bubble="mascotMessage"
        :holding="mascotHolding"
        class="page-hero__mascot"
      />
    </div>
  </section>
</template>

<style scoped>
/* Default (teacher / admin / public) hero: typography-driven, no decorative
 * frame. The eyebrow is a quiet uppercase label; the title scales with the
 * viewport via clamp() and uses the Inter weight ladder. */
.page-hero {
  position: relative;
  padding: 4px 0 8px;
  max-width: 800px;
}

/* Student hero: keep the playful candy-gradient surface — kid-theme.css adds
 * extra polish (sparkles, mascot bubble) on top. */
.page-hero--kid {
  padding: 24px 28px 20px;
  border-radius: 36px;
  border: 4px solid #fff;
  background:
    radial-gradient(circle at 90% 20%, rgba(255, 210, 63, 0.5), transparent 50%),
    radial-gradient(circle at 10% 100%, rgba(76, 201, 240, 0.35), transparent 55%),
    linear-gradient(135deg, #fff5b1 0%, #ffd9eb 60%, #d9f1ff 100%);
  box-shadow: 0 22px 60px rgba(43, 29, 79, 0.18);
  overflow: hidden;
  max-width: none;
}

.page-hero__inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.page-hero__copy {
  flex: 1 1 320px;
  min-width: 0;
}

.page-hero__mascot {
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .page-hero--kid {
    padding: 22px 20px;
    border-radius: 28px;
  }

  .page-hero__mascot {
    align-self: flex-end;
  }
}
</style>
