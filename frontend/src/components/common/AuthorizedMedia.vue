<template>
  <div class="authorized-media" data-testid="authorized-media">
    <template v-if="objectUrl">
      <audio
        v-if="kind === 'audio'"
        class="authorized-media-audio"
        :src="objectUrl"
        controls
        preload="metadata"
        :aria-label="audioLabel"
      />
      <img v-else class="authorized-media-image" :src="objectUrl" :alt="imageAlt" />
      <button class="btn btn-secondary btn-sm" type="button" @click="reset">{{ t('media.hide') }}</button>
    </template>
    <button
      v-else
      class="btn btn-secondary btn-sm"
      type="button"
      :disabled="loading || disabled"
      :aria-busy="loading"
      data-testid="authorized-media-load"
      @click="open"
    >
      <IconGlyph :name="kind === 'audio' ? 'audio' : 'image'" :size="16" />
      {{ loading ? t('media.loading') : kind === 'audio' ? t('media.loadAudio') : t('media.loadImage') }}
    </button>
    <p v-if="disabled && disabledMessage" class="muted authorized-media-note">{{ disabledMessage }}</p>
    <p v-if="error" class="authorized-media-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import type { DownloadResult } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useAuthorizedFile } from '@/composables/useAuthorizedFile'
import { mediaMessages } from '@/plugins/mediaMessages'

/**
 * Plays / shows a protected practice-submission file. The file is fetched with the bearer token through the
 * shared http client (`loader`) and rendered from a Blob object URL - never from a tokenised URL.
 */
const props = withDefaults(
  defineProps<{
    kind: 'audio' | 'image'
    loader: () => Promise<DownloadResult>
    target?: string | null
    disabled?: boolean
    disabledMessage?: string | null
  }>(),
  { target: null, disabled: false, disabledMessage: null },
)

const { t } = useI18n({
  messages: {
    en: { media: mediaMessages.en },
    ms: { media: mediaMessages.ms },
    'zh-CN': { media: mediaMessages['zh-CN'] },
  },
})

const { objectUrl, loading, error, load, reset } = useAuthorizedFile()

const imageAlt = computed(() =>
  props.target ? t('media.imageAlt', { target: props.target }) : t('media.imageAltGeneric'),
)
const audioLabel = computed(() =>
  props.target ? t('media.audioLabel', { target: props.target }) : t('media.audioLabelGeneric'),
)

function open(): void {
  void load(props.loader, t('media.loadError'))
}
</script>

<style scoped>
.authorized-media {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.authorized-media-audio {
  max-width: 100%;
}

.authorized-media-image {
  max-width: min(100%, 320px);
  max-height: 240px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-default);
  object-fit: contain;
  background: var(--surface-card);
}

.authorized-media-note {
  flex-basis: 100%;
  margin: 0;
  font-size: var(--font-size-md);
}

.authorized-media-error {
  flex-basis: 100%;
  margin: 0;
  color: var(--color-danger-text);
  font-size: var(--font-size-md);
}
</style>
