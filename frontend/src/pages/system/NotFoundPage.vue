<template>
  <main id="main-content" class="system-page system-page--not-found" :aria-label="t('system.notFoundAria')">
    <div class="system-card">
      <div class="system-copy">
        <AppBrand />

        <p class="system-kicker">
          <IconGlyph name="search" :size="16" />
          <span>{{ t('system.routeNotFound') }}</span>
        </p>

        <h1>{{ t('system.notFoundTitle') }}</h1>
        <p class="system-message">{{ t('system.notFoundMessage') }}</p>

        <div class="system-actions">
          <RouterLink class="btn btn-primary btn-lg" to="/">{{ t('system.returnHome') }}</RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" to="/courses">{{ t('system.exploreCourses') }}</RouterLink>
        </div>

        <ul class="system-links">
          <li>
            <RouterLink class="system-link" to="/auth">
              <span class="system-link-icon" aria-hidden="true"><IconGlyph name="profile" :size="20" /></span>
              <span class="system-link-copy">
                <strong>{{ t('system.signIn') }}</strong>
                <span>{{ t('system.signInHint') }}</span>
              </span>
              <IconGlyph class="system-link-arrow" name="chevronRight" :size="18" />
            </RouterLink>
          </li>
          <li>
            <RouterLink class="system-link" to="/courses">
              <span class="system-link-icon" aria-hidden="true"><IconGlyph name="courses" :size="20" /></span>
              <span class="system-link-copy">
                <strong>{{ t('system.courseCatalog') }}</strong>
                <span>{{ t('system.courseCatalogHint') }}</span>
              </span>
              <IconGlyph class="system-link-arrow" name="chevronRight" :size="18" />
            </RouterLink>
          </li>
        </ul>
      </div>

      <div class="system-visual" aria-hidden="true">
        <span class="system-code">404</span>
        <PandaIllustration class="system-panda" :size="200" outfit="#6f4cff" accent="#ffcc2f" />
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
    'zh-CN': { system: systemMessages['zh-CN'] },
  },
})
</script>
