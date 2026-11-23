<template>
  <div class="lesson-video">
    <!-- Open: the player with its own bar (play or pause, where it is, close); a video that did not load says so. -->
    <template v-if="state !== 'closed'">
      <div class="lesson-video-stage" :class="{ 'is-error': state === 'error' }">
        <video
          v-if="url"
          ref="player"
          class="lesson-video-player"
          :src="url"
          :aria-label="video.title"
          playsinline
          @click="togglePlay"
          @play="state = 'playing'"
          @pause="onPause"
          @ended="onEnded"
          @timeupdate="readTime"
          @loadedmetadata="readTime"
          @error="fail"
        />
        <p v-else-if="state === 'loading'" class="lesson-video-message" role="status">
          <span class="spinner is-sm" aria-hidden="true" />{{ t('studentClasses.lesson.videoLoading') }}
        </p>
        <p v-else class="lesson-video-message" role="alert" data-testid="video-error">
          <IconGlyph name="alert" :size="20" />{{ t('studentClasses.lesson.videoError') }}
        </p>
      </div>
      <div class="lesson-video-bar">
        <button
          v-if="state === 'error'"
          ref="primaryButton"
          type="button"
          class="btn btn-primary btn-sm"
          data-testid="video-retry"
          @click="load"
        >
          <IconGlyph name="retry" :size="16" />{{ t('studentClasses.lesson.retry') }}
        </button>
        <button
          v-else
          ref="primaryButton"
          type="button"
          class="btn btn-primary btn-sm lesson-video-toggle"
          :disabled="state === 'loading'"
          data-testid="video-toggle"
          @click="togglePlay"
        >
          {{ state === 'playing' ? t('studentClasses.lesson.pause') : t('studentClasses.lesson.play') }}
        </button>
        <span class="lesson-video-time">{{ formatClock(current) }}</span>
        <input
          class="lesson-video-seek"
          type="range"
          min="0"
          :max="Math.max(length, 1)"
          step="1"
          :value="current"
          :disabled="state === 'loading' || state === 'error' || length <= 0"
          :aria-label="t('studentClasses.lesson.position', { title: video.title })"
          :aria-valuetext="`${formatClock(current)} / ${formatClock(length)}`"
          :style="{ '--seek-progress': `${seekProgress}%` }"
          @input="seek(Number(($event.target as HTMLInputElement).value))"
        />
        <span class="lesson-video-time">{{ formatClock(length) }}</span>
        <button ref="closeButton" type="button" class="btn btn-ghost btn-sm" data-testid="video-close" @click="close">
          <IconGlyph name="close" :size="16" />{{ t('studentClasses.lesson.closeVideo') }}
        </button>
      </div>
      <p v-if="state === 'error'" class="lesson-video-help">
        {{ t('studentClasses.lesson.stillNotLoading') }}
        <button type="button" class="btn btn-link" data-testid="video-report" @click="emit('report')">
          {{ t('studentClasses.report.title') }}<IconGlyph name="chevronRight" :size="14" />
        </button>
      </p>
      <strong class="row-list-title">{{ video.title }}</strong>
    </template>

    <!-- Closed: the video's row, with what opens it. A lesson of an archived class shows the row without a way to play. -->
    <div v-else class="lesson-video-row">
      <span class="lesson-video-thumb" aria-hidden="true"><IconGlyph name="play" :size="24" /></span>
      <span class="row-list-copy">
        <strong class="row-list-title">{{ video.title }}</strong>
        <span v-if="video.durationSeconds" class="row-list-meta">{{ formatClock(video.durationSeconds) }}</span>
      </span>
      <button
        v-if="video.streamPath"
        ref="primaryButton"
        type="button"
        class="btn btn-secondary btn-sm"
        data-testid="video-play"
        @click="load"
      >
        <IconGlyph name="play" :size="16" />{{ t('studentClasses.lesson.playVideo') }}
      </button>
      <a
        v-else-if="video.externalUrl && isWebLink(video.externalUrl)"
        class="btn btn-secondary btn-sm"
        :href="video.externalUrl"
        target="_blank"
        rel="noopener noreferrer"
        @click="emit('watched')"
      >
        <IconGlyph name="external" :size="16" />{{ t('studentClasses.lesson.openVideo') }}
        <span class="sr-only">{{ t('common.opensInNewTab') }}</span>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'

import { videoBlob } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { StudentLesson } from '@/types/classes'
import { isWebLink } from '@/utils/classes'
import { formatClock } from '@/utils/format'

type Video = NonNullable<StudentLesson['video']>
type PlayerState = 'closed' | 'loading' | 'playing' | 'paused' | 'error'

/**
 * A lesson's video. Closed, it is a row with Play video (an uploaded file) or Open video (a link, in a new tab). Play
 * downloads the file with the learner's sign-in and plays it in the card, under a bar of its own: Pause or Play, the
 * time, a position slider and Close video. A video that does not load (the download or the file itself) says so, with
 * Retry and "Still not loading? Contact support ›" (`report`). `watched` fires when it plays to the end or its link is
 * opened. A video of an archived class has neither path, so its row offers nothing to open. The page keys it by
 * lesson, so another lesson starts closed.
 */
const props = defineProps<{ video: Video }>()
const emit = defineEmits<{ watched: []; report: [] }>()

const { t } = useLocalMessages({ studentClasses: studentClassesMessages })

const state = ref<PlayerState>('closed')
const url = ref<string | null>(null)
const player = ref<HTMLVideoElement | null>(null)
const primaryButton = ref<HTMLButtonElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const current = ref(0)
const length = ref(props.video.durationSeconds ?? 0)
const seekProgress = computed(() =>
  length.value > 0 ? Math.min(100, Math.max(0, (current.value / length.value) * 100)) : 0,
)
let download: AbortController | null = null

function release(): void {
  download?.abort()
  download = null
  player.value?.pause()
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = null
  current.value = 0
}

/** Downloads the file (again, after a failure) and starts it. */
async function load(): Promise<void> {
  const path = props.video.streamPath
  if (!path) return
  const openingFromButton = document.activeElement === primaryButton.value
  release()
  state.value = 'loading'
  // Close stays available while the button that started the download is replaced.
  if (openingFromButton) void nextTick(() => closeButton.value?.focus())
  const mine = new AbortController()
  download = mine
  try {
    const blob = await videoBlob(path, mine.signal)
    if (download !== mine) return
    url.value = URL.createObjectURL(blob)
    state.value = 'paused'
    await nextTick()
    if (download === mine) await play(true)
  } catch {
    if (download === mine) fail()
  }
}

function fail(): void {
  const focusedPlayback = document.activeElement === primaryButton.value
  release()
  state.value = 'error'
  if (focusedPlayback) void nextTick(() => primaryButton.value?.focus())
}

function togglePlay(): void {
  const element = player.value
  if (!element) return
  if (element.paused) void play()
  else element.pause()
}

async function play(automatic = false): Promise<void> {
  const element = player.value
  if (!element) return
  try {
    await element.play()
  } catch (reason) {
    if (player.value !== element || !url.value) return
    const name = reason instanceof DOMException ? reason.name : ''
    // Closing or pausing interrupts play; browsers may also require a click for the initial playback.
    if (name === 'AbortError' || (automatic && name === 'NotAllowedError')) return
    fail()
  }
}

function onPause(): void {
  if (state.value === 'playing') state.value = 'paused'
}

function onEnded(): void {
  if (length.value > 0) current.value = length.value
  state.value = 'paused'
  emit('watched')
}

function readTime(): void {
  const element = player.value
  if (!element) return
  current.value = Math.floor(element.currentTime)
  if (Number.isFinite(element.duration) && element.duration > 0) length.value = Math.round(element.duration)
}

function seek(seconds: number): void {
  current.value = seconds
  if (player.value) player.value.currentTime = seconds
}

function close(): void {
  release()
  state.value = 'closed'
  void nextTick(() => primaryButton.value?.focus())
}

onBeforeUnmount(release)
</script>
