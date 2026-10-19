<template>
  <main id="main-content" class="system-page system-page--unauthorized" :aria-label="t('system.accessDeniedAria')">
    <div class="system-card">
      <div class="system-copy">
        <AppBrand />

        <p class="system-kicker">
          <IconGlyph name="safe" :size="16" />
          <span>{{ t('system.protectedWorkspace') }}</span>
        </p>

        <h1>{{ t('system.unauthorizedTitle') }}</h1>
        <p class="system-message">{{ t('system.unauthorizedMessage') }}</p>

        <div class="system-actions">
          <RouterLink class="btn btn-primary btn-lg" to="/auth">{{ t('system.goToSignIn') }}</RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" to="/">{{ t('system.returnHome') }}</RouterLink>
        </div>

        <ul class="system-links">
          <li>
            <RouterLink class="system-link" to="/student/home">
              <span class="system-link-icon" aria-hidden="true"><IconGlyph name="students" :size="20" /></span>
              <span class="system-link-copy">
                <strong>{{ t('system.studentArea') }}</strong>
                <span>{{ t('system.studentAreaHint') }}</span>
              </span>
              <IconGlyph class="system-link-arrow" name="chevronRight" :size="18" />
            </RouterLink>
          </li>
          <li>
            <RouterLink class="system-link" to="/teacher/dashboard">
              <span class="system-link-icon" aria-hidden="true"><IconGlyph name="teacher" :size="20" /></span>
              <span class="system-link-copy">
                <strong>{{ t('system.teacherArea') }}</strong>
                <span>{{ t('system.teacherAreaHint') }}</span>
              </span>
              <IconGlyph class="system-link-arrow" name="chevronRight" :size="18" />
            </RouterLink>
          </li>
        </ul>
      </div>

      <div class="system-visual" aria-hidden="true">
        <span class="system-code">403</span>
        <PandaIllustration class="system-panda" :size="200" outfit="#ff9f2f" accent="#6f4cff" />
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
