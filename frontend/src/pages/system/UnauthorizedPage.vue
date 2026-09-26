<template>
  <main class="system-page system-page--unauthorized" :aria-label="t('system.accessDeniedAria')">
    <span class="system-bg-orb system-bg-orb--one" />
    <span class="system-bg-orb system-bg-orb--two" />
    <div class="surface-card system-card">
      <div class="system-card-copy">
        <AppBrand />

        <div class="system-kicker">
          <IconGlyph name="safe" :size="18" />
          <span>{{ t('system.protectedWorkspace') }}</span>
        </div>

        <h1>{{ t('system.unauthorizedTitle') }}</h1>
        <p>{{ t('system.unauthorizedMessage') }}</p>

        <div class="system-action-row">
          <RouterLink class="btn btn-primary" to="/auth">{{ t('system.goToSignIn') }}</RouterLink>
          <RouterLink class="btn btn-ghost" to="/">{{ t('system.returnHome') }}</RouterLink>
        </div>

        <div class="system-link-grid">
          <RouterLink to="/student/home">
            <strong>{{ t('system.studentArea') }}</strong>
            <span>{{ t('system.studentAreaHint') }}</span>
          </RouterLink>
          <RouterLink to="/teacher/dashboard">
            <strong>{{ t('system.teacherArea') }}</strong>
            <span>{{ t('system.teacherAreaHint') }}</span>
          </RouterLink>
        </div>
      </div>

      <div class="system-visual">
        <span class="system-code">403</span>
        <span class="system-cloud system-cloud--one" />
        <span class="system-cloud system-cloud--two" />
        <span class="system-pagoda" />
        <PandaIllustration class="system-panda" :size="220" outfit="#ff9f2f" accent="#6f4cff" />
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
