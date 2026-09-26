<template>
  <main class="system-page system-page--not-found" :aria-label="t('system.notFoundAria')">
    <span class="system-bg-orb system-bg-orb--one" />
    <span class="system-bg-orb system-bg-orb--two" />
    <div class="surface-card system-card">
      <div class="system-card-copy">
        <AppBrand />

        <div class="system-kicker">
          <IconGlyph name="search" :size="18" />
          <span>{{ t('system.routeNotFound') }}</span>
        </div>

        <h1>{{ t('system.notFoundTitle') }}</h1>
        <p>{{ t('system.notFoundMessage') }}</p>

        <div class="system-action-row">
          <RouterLink class="btn btn-primary" to="/">{{ t('system.returnHome') }}</RouterLink>
          <RouterLink class="btn btn-ghost" to="/courses">{{ t('system.exploreCourses') }}</RouterLink>
        </div>

        <div class="system-link-grid">
          <RouterLink to="/auth">
            <strong>{{ t('system.signIn') }}</strong>
            <span>{{ t('system.signInHint') }}</span>
          </RouterLink>
          <RouterLink to="/courses">
            <strong>{{ t('system.courseCatalog') }}</strong>
            <span>{{ t('system.courseCatalogHint') }}</span>
          </RouterLink>
        </div>
      </div>

      <div class="system-visual">
        <span class="system-code">404</span>
        <span class="system-cloud system-cloud--one" />
        <span class="system-cloud system-cloud--two" />
        <span class="system-pagoda" />
        <PandaIllustration class="system-panda" :size="220" outfit="#6f4cff" accent="#ffcc2f" />
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { systemMessages } from '@/plugins/systemMessages'

const { t } = useI18n({
  messages: {
    en: { system: systemMessages.en },
    ms: { system: systemMessages.ms },
    zh: { system: systemMessages.zh },
  },
})
</script>
