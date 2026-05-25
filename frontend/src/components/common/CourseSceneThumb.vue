<template>
  <div class="course-scene-thumb" :class="`is-${variant}`" :style="{ '--scene-thumb-size': `${size}px` }">
    <span class="course-scene-sun" />
    <span class="course-scene-cloud course-scene-cloud--1" />
    <span class="course-scene-cloud course-scene-cloud--2" />
    <span class="course-scene-hill course-scene-hill--1" />
    <span class="course-scene-hill course-scene-hill--2" />
    <span class="course-scene-confetti course-scene-confetti--1" />
    <span class="course-scene-confetti course-scene-confetti--2" />
    <span class="course-scene-confetti course-scene-confetti--3" />

    <PandaIllustration
      v-if="variant === 'panda'"
      class="course-scene-panda"
      :size="Math.round(size * 0.74)"
      outfit="#ef6b40"
      accent="#f5c74f"
      compact
    />

    <div v-else-if="variant === 'family'" class="course-scene-family">
      <span class="course-scene-person is-left" />
      <span class="course-scene-person is-center" />
      <span class="course-scene-person is-right" />
    </div>

    <div v-else-if="variant === 'pagoda'" class="course-scene-pagoda-wrap">
      <span class="course-scene-pagoda" />
      <span class="course-scene-tree" />
    </div>

    <div v-else-if="variant === 'travel'" class="course-scene-travel">
      <span class="course-scene-wave" />
      <span class="course-scene-boat" />
      <span class="course-scene-peak course-scene-peak--1" />
      <span class="course-scene-peak course-scene-peak--2" />
    </div>

    <span v-else class="course-scene-book">学</span>
  </div>
</template>

<script setup lang="ts">
/**
 * Pure-CSS illustrated thumbnail used as the placeholder course
 * cover when a teacher has not uploaded a real image. Five
 * variants render different scenes (panda, family, pagoda,
 * travel, classic-book) so a row of course cards looks
 * visually varied without any image assets in the bundle.
 *
 * Sizing flows through the `--scene-thumb-size` custom property
 * so a single component can serve catalogue cards (large),
 * dashboard summaries (medium) and dense list rows (small)
 * without separate variants.
 */
import PandaIllustration from '@/components/common/PandaIllustration.vue'

type SceneVariant = 'panda' | 'family' | 'pagoda' | 'travel' | 'general'

interface Props {
  size?: number
  variant?: SceneVariant
}

withDefaults(defineProps<Props>(), {
  size: 72,
  variant: 'general',
})
</script>

<style scoped>
.course-scene-thumb {
  position: relative;
  width: var(--scene-thumb-size);
  height: var(--scene-thumb-size);
  border-radius: calc(var(--scene-thumb-size) * 0.26);
  overflow: hidden;
  contain: layout paint;
  border: 1px solid rgba(221, 226, 245, 0.98);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.86),
    0 10px 22px rgba(132, 141, 199, 0.12);
}

.course-scene-thumb span,
.course-scene-family,
.course-scene-travel,
.course-scene-pagoda-wrap {
  position: absolute;
  display: block;
}

.course-scene-thumb.is-panda {
  background:
    radial-gradient(circle at 22% 16%, rgba(255, 255, 255, 0.94), transparent 26%),
    linear-gradient(180deg, #9f84ff 0%, #b791ff 50%, #ffd56a 100%);
}

.course-scene-thumb.is-family {
  background:
    radial-gradient(circle at 30% 18%, rgba(255, 255, 255, 0.84), transparent 24%),
    linear-gradient(180deg, #7cc7ff 0%, #b8e5ff 54%, #ffd16a 54%, #f6b54a 100%);
}

.course-scene-thumb.is-pagoda {
  background:
    radial-gradient(circle at 30% 18%, rgba(255, 255, 255, 0.9), transparent 22%),
    linear-gradient(180deg, #8fd3ff 0%, #d7f2ff 56%, #8fca5c 57%, #6aac4e 100%);
}

.course-scene-thumb.is-travel {
  background:
    radial-gradient(circle at 24% 16%, rgba(255, 255, 255, 0.92), transparent 22%),
    linear-gradient(180deg, #8cd0ff 0%, #d8f1ff 58%, #8dd0ea 58%, #55addf 100%);
}

.course-scene-thumb.is-general {
  background:
    radial-gradient(circle at 30% 18%, rgba(255, 255, 255, 0.88), transparent 24%),
    linear-gradient(180deg, #a88fff 0%, #cfb8ff 60%, #f8d56b 100%);
}

.course-scene-sun {
  top: 10%;
  right: 12%;
  width: 16%;
  height: 16%;
  border-radius: 50%;
  background: rgba(255, 230, 123, 0.95);
}

.course-scene-cloud {
  height: 8%;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
}

.course-scene-cloud::before,
.course-scene-cloud::after {
  content: '';
  position: absolute;
  bottom: 0;
  border-radius: 50%;
  background: inherit;
}

.course-scene-cloud--1 {
  left: 10%;
  top: 12%;
  width: 26%;
}

.course-scene-cloud--1::before {
  left: 14%;
  width: 18%;
  height: 160%;
}

.course-scene-cloud--1::after {
  right: 10%;
  width: 16%;
  height: 130%;
}

.course-scene-cloud--2 {
  right: 30%;
  top: 18%;
  width: 20%;
  opacity: 0.86;
}

.course-scene-cloud--2::before {
  left: 14%;
  width: 16%;
  height: 150%;
}

.course-scene-cloud--2::after {
  right: 10%;
  width: 14%;
  height: 120%;
}

.course-scene-hill {
  bottom: -4%;
  border-radius: 50%;
}

.course-scene-hill--1 {
  left: -10%;
  width: 70%;
  height: 34%;
  background: rgba(145, 206, 93, 0.82);
}

.course-scene-hill--2 {
  right: -12%;
  width: 68%;
  height: 30%;
  background: rgba(104, 175, 84, 0.76);
}

.course-scene-confetti {
  width: 8%;
  height: 8%;
  border-radius: 10px;
}

.course-scene-confetti--1 {
  left: 16%;
  top: 18%;
  background: #ffd149;
}

.course-scene-confetti--2 {
  right: 22%;
  top: 30%;
  background: #ff7a52;
}

.course-scene-confetti--3 {
  left: 34%;
  top: 28%;
  background: #6bc45c;
}

.course-scene-panda {
  position: absolute;
  left: 14%;
  bottom: 4%;
}

.course-scene-family {
  inset: auto 0 0 0;
  height: 70%;
}

.course-scene-person {
  bottom: 0;
  width: 30%;
  height: 72%;
  border-radius: 46% 46% 18% 18%;
}

.course-scene-person::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 4%;
  width: 54%;
  height: 34%;
  border-radius: 50%;
  background: #f7cc9a;
  transform: translateX(-50%);
}

.course-scene-person::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  width: 62%;
  height: 24%;
  border-radius: 50% 50% 34% 34%;
  background: #3a2d32;
  transform: translateX(-50%);
}

.course-scene-person.is-left {
  left: 8%;
  background: linear-gradient(180deg, #ffffff 0 38%, #5cc080 39% 100%);
}

.course-scene-person.is-center {
  left: 35%;
  bottom: 4%;
  width: 32%;
  height: 78%;
  background: linear-gradient(180deg, #ffffff 0 38%, #ffb454 39% 100%);
}

.course-scene-person.is-right {
  right: 8%;
  background: linear-gradient(180deg, #ffffff 0 38%, #6c8cff 39% 100%);
}

.course-scene-pagoda-wrap {
  inset: auto 0 0 0;
  height: 78%;
}

.course-scene-pagoda {
  left: 34%;
  bottom: 10%;
  width: 28%;
  height: 58%;
  background: linear-gradient(
    180deg,
    transparent 0 14%,
    #df6150 14% 24%,
    transparent 24% 38%,
    #df6150 38% 48%,
    transparent 48% 64%,
    #df6150 64% 74%,
    transparent 74% 100%
  );
  clip-path: polygon(
    44% 0,
    56% 0,
    62% 8%,
    76% 8%,
    82% 16%,
    61% 16%,
    66% 30%,
    86% 30%,
    92% 38%,
    68% 38%,
    72% 52%,
    90% 52%,
    96% 60%,
    70% 60%,
    70% 100%,
    30% 100%,
    30% 60%,
    4% 60%,
    10% 52%,
    28% 52%,
    32% 38%,
    8% 38%,
    14% 30%,
    34% 30%,
    39% 16%,
    18% 16%,
    24% 8%,
    38% 8%
  );
}

.course-scene-tree {
  left: 20%;
  bottom: 12%;
  width: 10%;
  height: 26%;
  border-radius: 999px;
  background: linear-gradient(180deg, #7bc152, #4e9d3d);
  box-shadow: 6px 10px 0 -3px rgba(123, 193, 82, 0.92);
}

.course-scene-travel {
  inset: auto 0 0 0;
  height: 78%;
}

.course-scene-wave {
  left: -8%;
  right: -8%;
  bottom: -8%;
  height: 40%;
  border-radius: 50% 50% 0 0;
  background: linear-gradient(180deg, rgba(149, 224, 251, 0.55), rgba(75, 171, 223, 0.92));
}

.course-scene-boat {
  left: 40%;
  bottom: 18%;
  width: 16%;
  height: 10%;
  background: #ff9a43;
  clip-path: polygon(0 40%, 100% 40%, 84% 100%, 16% 100%);
}

.course-scene-boat::before {
  content: '';
  position: absolute;
  left: 46%;
  bottom: 100%;
  width: 6%;
  height: 170%;
  background: #7c5d45;
}

.course-scene-boat::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 132%;
  width: 46%;
  height: 70%;
  background: rgba(255, 255, 255, 0.88);
  clip-path: polygon(0 0, 100% 50%, 0 100%);
}

.course-scene-peak {
  bottom: 22%;
  width: 26%;
  height: 36%;
  background: rgba(109, 182, 111, 0.92);
  clip-path: polygon(50% 0, 100% 100%, 0 100%);
}

.course-scene-peak--1 {
  left: 12%;
}

.course-scene-peak--2 {
  right: 14%;
  width: 22%;
  height: 28%;
}

.course-scene-book {
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  font-family: 'Fredoka', sans-serif;
  font-size: calc(var(--scene-thumb-size) * 0.34);
  font-weight: 700;
  color: white;
  text-shadow: 0 8px 18px rgba(72, 50, 162, 0.28);
}
</style>
