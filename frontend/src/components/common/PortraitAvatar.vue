<template>
  <div class="portrait-avatar" :class="[shapeClass, `is-${kind}`]" :style="{ '--portrait-size': `${size}px`, '--portrait-accent': accent }">
    <span class="portrait-backdrop" />
    <span class="portrait-shoulders" />
    <span class="portrait-neck" />
    <span class="portrait-face" />
    <span class="portrait-hair" />
    <span class="portrait-fringe" />
    <span class="portrait-hijab" />
    <span class="portrait-eye portrait-eye--left" />
    <span class="portrait-eye portrait-eye--right" />
    <span class="portrait-blush portrait-blush--left" />
    <span class="portrait-blush portrait-blush--right" />
    <span class="portrait-mouth" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    size?: number
    kind?: 'female' | 'male' | 'hijab' | 'student'
    accent?: string
    rounded?: 'circle' | 'soft'
  }>(),
  {
    size: 56,
    kind: 'student',
    accent: '#7b57ff',
    rounded: 'circle',
  },
)

const shapeClass = computed(() => (props.rounded === 'soft' ? 'is-soft' : 'is-circle'))
</script>

<style scoped>
.portrait-avatar {
  position: relative;
  width: var(--portrait-size);
  height: var(--portrait-size);
  overflow: hidden;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.88),
    0 10px 22px rgba(135, 144, 198, 0.14);
}

.portrait-avatar.is-circle {
  border-radius: 50%;
}

.portrait-avatar.is-soft {
  border-radius: calc(var(--portrait-size) * 0.28);
}

.portrait-avatar span {
  position: absolute;
  display: block;
}

.portrait-backdrop {
  inset: 0;
  background:
    radial-gradient(circle at 28% 20%, rgba(255, 255, 255, 0.94), transparent 24%),
    linear-gradient(180deg, #f2f4ff 0%, #ffffff 100%);
}

.portrait-shoulders {
  left: 15%;
  right: 15%;
  bottom: -8%;
  height: 42%;
  border-radius: 46% 46% 0 0;
  background: var(--portrait-accent);
}

.portrait-neck {
  left: 44%;
  bottom: 26%;
  width: 12%;
  height: 14%;
  border-radius: 999px;
  background: #f0c39d;
}

.portrait-face {
  left: 30%;
  top: 18%;
  width: 40%;
  height: 44%;
  border-radius: 46% 46% 44% 44%;
  background: linear-gradient(180deg, #f9d3ac, #efc39c);
}

.portrait-hair {
  left: 22%;
  top: 12%;
  width: 56%;
  height: 42%;
  border-radius: 48% 48% 34% 34%;
  background: #342934;
}

.portrait-fringe {
  left: 28%;
  top: 15%;
  width: 44%;
  height: 18%;
  border-radius: 48% 48% 60% 60%;
  background: #2f2430;
}

.portrait-eye {
  top: 35%;
  width: 6%;
  height: 6%;
  border-radius: 50%;
  background: #241b26;
}

.portrait-eye--left {
  left: 41%;
}

.portrait-eye--right {
  right: 41%;
}

.portrait-blush {
  top: 42%;
  width: 8%;
  height: 4%;
  border-radius: 50%;
  background: rgba(255, 164, 176, 0.62);
}

.portrait-blush--left {
  left: 33%;
}

.portrait-blush--right {
  right: 33%;
}

.portrait-mouth {
  left: 46%;
  top: 47%;
  width: 8%;
  height: 4%;
  border-bottom: 2px solid #7c5461;
  border-radius: 0 0 60% 60%;
}

.portrait-avatar.is-male .portrait-hair {
  left: 25%;
  width: 50%;
  height: 28%;
  border-radius: 48% 48% 20% 20%;
  background: #2d2529;
}

.portrait-avatar.is-male .portrait-fringe {
  left: 34%;
  width: 32%;
  top: 18%;
  height: 10%;
  border-radius: 50%;
}

.portrait-avatar.is-male .portrait-face {
  top: 20%;
  height: 42%;
}

.portrait-avatar.is-male .portrait-shoulders {
  background: #2c5ca8;
}

.portrait-avatar.is-female .portrait-shoulders {
  background: #253f71;
}

.portrait-avatar.is-student .portrait-shoulders {
  background: #ffb54a;
}

.portrait-avatar.is-student .portrait-hair {
  background: #2f2430;
}

.portrait-avatar.is-student .portrait-fringe {
  left: 31%;
  width: 38%;
}

.portrait-avatar.is-hijab .portrait-hijab {
  left: 18%;
  right: 18%;
  top: 12%;
  bottom: 0;
  border-radius: 46% 46% 24% 24%;
  background: linear-gradient(180deg, #2aa7cb, #1c7dac 70%, #14567a);
}

.portrait-avatar.is-hijab .portrait-hair,
.portrait-avatar.is-hijab .portrait-fringe {
  display: none;
}

.portrait-avatar.is-hijab .portrait-face {
  left: 32%;
  top: 22%;
  width: 36%;
  height: 36%;
}

.portrait-avatar.is-hijab .portrait-shoulders {
  left: 10%;
  right: 10%;
  background: #1e8ab7;
}
</style>
