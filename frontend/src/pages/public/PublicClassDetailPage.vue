<template>
  <div class="public-page">
    <template v-if="detail">
      <AppBreadcrumb :items="[{ label: t('nav.classes'), to: catalogue }, { label: detail.summary.title }]" />

      <section class="pub-class-hero" :class="`tint-${nameTint(detail.summary.title)}`">
        <div class="pub-class-hero-copy">
          <h1 class="pub-class-title"><CjkText :text="detail.summary.title" /></h1>
          <p v-if="detail.summary.description" class="pub-class-about">
            <CjkText :text="detail.summary.description" />
          </p>
          <p class="type-caption" data-testid="pub-class-facts">{{ facts }}</p>
          <div class="btn-group pub-class-actions">
            <RouterLink
              v-if="isLearner"
              class="btn btn-primary btn-lg"
              :to="{ name: 'student-class', params: { classId: detail.summary.id } }"
            >
              {{ t('classes.openClass') }}
            </RouterLink>
            <RouterLink
              v-else-if="isAdmin"
              class="btn btn-primary btn-lg"
              :to="{ name: 'admin-class', params: { classId: detail.summary.id } }"
            >
              {{ t('classCatalogue.detail.manageClass') }}
            </RouterLink>
            <!-- A new learner comes back to this class, where they join it. -->
            <RouterLink
              v-else
              class="btn btn-primary btn-lg"
              data-testid="pub-class-register"
              :to="{ path: '/auth', query: { tab: 'register', redirect: learnerClassPath } }"
            >
              {{ t('classCatalogue.detail.start') }}
            </RouterLink>
          </div>
        </div>
        <div class="pub-class-cover" aria-hidden="true">
          <ClassAvatar :title="detail.summary.title" :size="72" />
        </div>
      </section>

      <section class="panel pub-class-units" aria-labelledby="pub-class-units-title">
        <div class="row-list-copy">
          <h2 id="pub-class-units-title" class="card-title">{{ t('classCatalogue.detail.whatsInside') }}</h2>
          <p v-if="detail.units.length && !isAdmin" class="card-subtitle" data-testid="pub-class-locked-note">
            {{ isLearner ? t('classCatalogue.detail.lockedNoteLearner') : t('classCatalogue.detail.lockedNote') }}
          </p>
        </div>
        <p v-if="!detail.units.length" class="type-caption">{{ t('classCatalogue.detail.noLessons') }}</p>
        <div v-for="(unit, unitIndex) in detail.units" :key="unit.id" class="pub-class-unit">
          <h3 class="row-list-title">
            <CjkText :text="t('classes.unitLabel', { number: unitIndex + 1, title: unit.title })" />
          </h3>
          <ol class="row-list">
            <li v-for="lesson in unit.lessons" :key="lesson.id" class="pub-class-lesson">
              <IconGlyph :name="isLearner || isAdmin ? 'learn' : 'lock'" :size="16" />
              <span class="row-list-title"><CjkText :text="lesson.title" /></span>
            </li>
          </ol>
        </div>
      </section>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('common.exploreClasses') }}</h1>
      <StatePanel
        v-if="loading"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('common.exploreClasses')"
      />
      <ClassLoadError
        v-else
        :title="t('classes.classLoadError')"
        :message="error"
        :back-to="catalogue"
        :back-label="t('common.exploreClasses')"
        @retry="reload"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { publicClassesApi } from '@/api/classes'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import ClassAvatar from '@/components/classes/ClassAvatar.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useRecordById } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { useAuthStore } from '@/stores/auth'
import { catalogueQuery } from '@/utils/classes'
import { nameTint } from '@/utils/nameTint'
import { parseRecordId } from '@/utils/recordId'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })
const route = useRoute()
const auth = useAuthStore()

const classId = computed(() => parseRecordId(route.params.classId))
const { data: detail, loading, error, reload } = useRecordById(classId, publicClassesApi.detail)
const isLearner = computed(() => auth.roles.includes('STUDENT'))
const isAdmin = computed(() => !isLearner.value && auth.roles.includes('ADMIN'))
const catalogue = computed(() => ({ name: 'classes', query: catalogueQuery(route.query) }))
/** The learner's page of this class (join it there), where creating an account leads. */
const learnerClassPath = computed(() => `/student/classes/${detail.value?.summary.id ?? classId.value}`)

/** "Study or exchange · Free · best after Unit 3 of Learn" */
const facts = computed(() => {
  const summary = detail.value?.summary
  if (!summary) return ''
  return [
    summary.topicName,
    t('classes.free'),
    summary.bestAfterUnit ? t('classes.bestAfterLearn', { unit: summary.bestAfterUnit }) : null,
  ]
    .filter(Boolean)
    .join(' · ')
})
</script>
