<template>
  <div
    class="panda-illustration"
    :class="[`is-${pose}`, compact ? 'is-compact' : '']"
    :style="{
      '--panda-size': `${size}px`,
      '--panda-outfit': outfit,
      '--panda-accent': accent,
    }"
  >
    <span class="panda-shadow" />
    <span class="panda-ear panda-ear--left" />
    <span class="panda-ear panda-ear--right" />
    <span class="panda-head" />
    <span class="panda-eye-patch panda-eye-patch--left" />
    <span class="panda-eye-patch panda-eye-patch--right" />
    <span class="panda-eye panda-eye--left" />
    <span class="panda-eye panda-eye--right" />
    <span class="panda-glint panda-glint--left" />
    <span class="panda-glint panda-glint--right" />
    <span class="panda-cheek panda-cheek--left" />
    <span class="panda-cheek panda-cheek--right" />
    <span class="panda-nose" />
    <span class="panda-mouth panda-mouth--left" />
    <span class="panda-mouth panda-mouth--right" />
    <span class="panda-body" />
    <span class="panda-belly" />
    <span class="panda-outfit" />
    <span class="panda-arm panda-arm--left" />
    <span class="panda-arm panda-arm--right" />
    <span class="panda-leg panda-leg--left" />
    <span class="panda-leg panda-leg--right" />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number
    pose?: 'wave' | 'stand'
    outfit?: string
    accent?: string
    compact?: boolean
  }>(),
  {
    size: 140,
    pose: 'wave',
    outfit: '#ef6b40',
    accent: '#f6c24d',
    compact: false,
  },
)
</script>

<style scoped>
.panda-illustration {
  position: relative;
  width: calc(var(--panda-size) * 0.86);
  height: var(--panda-size);
  filter: drop-shadow(0 18px 28px rgba(78, 74, 129, 0.18));
}

.panda-illustration span {
  position: absolute;
  display: block;
}

.panda-shadow {
  left: 50%;
  bottom: 4%;
  width: 60%;
  height: 10%;
  border-radius: 999px;
  background: rgba(64, 73, 120, 0.12);
  transform: translateX(-50%);
}

.panda-ear {
  top: 8%;
  width: 18%;
  height: 18%;
  border-radius: 50%;
  background: #16192a;
}

.panda-ear--left {
  left: 16%;
}

.panda-ear--right {
  right: 16%;
}

.panda-head {
  top: 12%;
  left: 22%;
  width: 56%;
  height: 43%;
  border-radius: 50% 50% 47% 47%;
  background: linear-gradient(180deg, #ffffff, #faf7ff 78%, #f2ecff);
}

.panda-eye-patch {
  top: 24%;
  width: 14%;
  height: 15%;
  border-radius: 50% 55% 45% 50%;
  background: #1f2238;
}

.panda-eye-patch--left {
  left: 30%;
  transform: rotate(-18deg);
}

.panda-eye-patch--right {
  right: 30%;
  transform: rotate(18deg);
}

.panda-eye {
  top: 29%;
  width: 5%;
  height: 6%;
  border-radius: 50%;
  background: #11172a;
}

.panda-eye--left {
  left: 37%;
}

.panda-eye--right {
  right: 37%;
}

.panda-glint {
  top: 30%;
  width: 1.7%;
  height: 1.7%;
  border-radius: 50%;
  background: white;
}

.panda-glint--left {
  left: 38.6%;
}

.panda-glint--right {
  right: 38.6%;
}

.panda-cheek {
  top: 36%;
  width: 7%;
  height: 4.5%;
  border-radius: 50%;
  background: rgba(255, 173, 180, 0.6);
}

.panda-cheek--left {
  left: 28%;
}

.panda-cheek--right {
  right: 28%;
}

.panda-nose {
  top: 35.5%;
  left: 47%;
  width: 6%;
  height: 4.4%;
  border-radius: 50% 50% 58% 58%;
  background: #151a30;
}

.panda-mouth {
  top: 39%;
  width: 7%;
  height: 5%;
  border-bottom: 2px solid #151a30;
  border-radius: 0 0 50% 50%;
}

.panda-mouth--left {
  left: 42.8%;
  transform: rotate(12deg);
}

.panda-mouth--right {
  right: 42.8%;
  transform: rotate(-12deg);
}

.panda-body {
  left: 19%;
  bottom: 12%;
  width: 62%;
  height: 40%;
  border-radius: 48% 48% 42% 42%;
  background: linear-gradient(180deg, #ffffff, #f3ecff 82%, #ede4ff);
}

.panda-belly {
  left: 35%;
  bottom: 17%;
  width: 30%;
  height: 21%;
  border-radius: 46%;
  background: linear-gradient(180deg, #fff8f0, #f3e4d8);
}

.panda-outfit {
  left: 27%;
  bottom: 15%;
  width: 46%;
  height: 26%;
  border-radius: 34% 34% 44% 44%;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.12), rgba(0, 0, 0, 0.08)),
    var(--panda-outfit);
  box-shadow: inset 0 -8px 0 rgba(0, 0, 0, 0.08);
}

.panda-outfit::before,
.panda-outfit::after {
  content: '';
  position: absolute;
  top: 24%;
  width: 8%;
  height: 52%;
  border-radius: 999px;
  background: var(--panda-accent);
}

.panda-outfit::before {
  left: 35%;
}

.panda-outfit::after {
  right: 35%;
}

.panda-arm {
  bottom: 24%;
  width: 17%;
  height: 12%;
  border-radius: 40px;
  background: #181b2d;
}

.panda-arm--left {
  left: 13%;
  transform: rotate(26deg);
}

.panda-arm--right {
  right: 13%;
  transform: rotate(-16deg);
  transform-origin: 20% 50%;
}

.is-wave .panda-arm--right {
  right: 10%;
  bottom: 34%;
  width: 16%;
  transform: rotate(-58deg);
}

.panda-leg {
  bottom: 8%;
  width: 18%;
  height: 12%;
  border-radius: 42px;
  background: #181b2d;
}

.panda-leg--left {
  left: 23%;
  transform: rotate(10deg);
}

.panda-leg--right {
  right: 23%;
  transform: rotate(-10deg);
}

.is-compact {
  filter: drop-shadow(0 12px 18px rgba(78, 74, 129, 0.14));
}

.is-compact .panda-shadow {
  width: 54%;
}
</style>
