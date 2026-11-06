<template>
  <div class="public-page">
    <template v-if="detail">
      <AppBreadcrumb
        :items="[{ label: t('classes.classes'), to: { name: 'classes' } }, { label: detail.summary.title }]"
      />

      <section class="pub-class-hero">
        <div class="pub-class-hero-copy">
          <h1 class="pub-class-title">{{ detail.summary.title }}</h1>
          <p v-if="detail.summary.description" class="pub-class-about">{{ detail.summary.description }}</p>
          <p class="type-caption">{{ facts }}</p>
          <div class="btn-group pub-class-actions">
            <RouterLink
              v-if="isStudent"
              class="btn btn-primary btn-lg"
              :to="{ name: 'student-class', params: { classId: detail.summary.id } }"
            >
              {{ t('classCatalogue.detail.openClass') }}
            </RouterLink>
            <template v-else>
              <RouterLink
                class="btn btn-primary btn-lg"
                :to="{ path: '/auth', query: { tab: 'register', role: 'student' } }"
              >
                {{ t('classCatalogue.detail.start') }}
              </RouterLink>
              <RouterLink
                class="btn btn-secondary btn-lg"
                :to="{ path: '/auth', query: { tab: 'signin', redirect: studentClassPath } }"
              >
                {{ t('classCatalogue.detail.signIn') }}
              </RouterLink>
            </template>
          </div>
        </div>
        <div class="pub-class-teacher">
          <PortraitAvatar :size="40" :name="detail.summary.teacher.name" />
          <span class="row-list-copy">
            <span class="row-list-meta">{{ t('classCatalogue.detail.teacher') }}</span>
            <span class="row-list-title">{{ detail.summary.teacher.name }}</span>
          </span>
          <StatusPill v-if="detail.summary.teacher.verified" :label="t('classes.verified')" tone="success" />
        </div>
      </section>

      <section class="panel pub-class-units" aria-labelledby="pub-class-units-title">
        <h2 id="pub-class-units-title" class="card-title">{{ t('classCatalogue.detail.whatsInside') }}</h2>
        <p v-if="!detail.units.length" class="type-caption">{{ t('classCatalogue.detail.noLessons') }}</p>
        <div v-for="(unit, unitIndex) in detail.units" :key="unit.id" class="pub-class-unit">
          <span class="pub-class-unit-number" aria-hidden="true">{{ unitIndex + 1 }}</span>
          <div class="pub-class-unit-body">
            <div class="row-list-copy">
              <h3 class="row-list-title pub-class-unit-title">
                {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
              </h3>
              <span class="row-list-meta">{{ t('classes.lessons', unit.lessons.length) }}</span>
            </div>
            <ol class="row-list">
              <li v-for="lesson in unit.lessons" :key="lesson.id" class="pub-class-lesson">
                <IconGlyph name="lock" :size="16" />
                <span class="row-list-title">{{ lesson.title }}</span>
              </li>
            </ol>
          </div>
        </div>
      </section>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('classCatalogue.title') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classCatalogue.title')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('classCatalogue.detail.loadErrorTitle')"
        :message="resource.error.value ?? t('classCatalogue.detail.notFound')"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'classes' }">
            {{ t('classCatalogue.detail.backToClasses') }}
          </RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { publicClassesApi } from '@/api/classes'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { useAuthStore } from '@/stores/auth'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })
const route = useRoute()
const auth = useAuthStore()

const classId = computed(() => Number(route.params.classId))
const resource = useResource(() => publicClassesApi.detail(classId.value))
const detail = computed(() => resource.data.value)
const isStudent = computed(() => auth.isAuthenticated && auth.hasRole('STUDENT'))
const studentClassPath = computed(() => `/student/classes/${classId.value}`)

/** "Study or exchange · 2 units · 5 lessons · Best after Unit 3" */
const facts = computed(() => {
  const summary = detail.value?.summary
  if (!summary) return ''
  return [
    summary.topicName,
    t('classes.units', summary.unitCount),
    t('classes.lessons', summary.lessonCount),
    summary.bestAfterUnit ? t('classes.bestAfter', { unit: summary.bestAfterUnit }) : null,
  ]
    .filter(Boolean)
    .join(' · ')
})
</script>
