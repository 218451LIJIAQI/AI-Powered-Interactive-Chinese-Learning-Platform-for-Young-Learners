<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { publicTeacherService } from '../../services/publicTeacherService'
import type { TeacherProfilePublicResponse } from '../../types/teacherManagement'

const { t } = useI18n()
const loading = ref(true)
const profile = ref<TeacherProfilePublicResponse | null>(null)

const teacherId = useNumericRouteParam('id')

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await publicTeacherService.getTeacherProfile(teacherId.value)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      :eyebrow="t('teacherVerification.publicProfileEyebrow')"
      :title="profile?.displayName || t('teacherVerification.publicProfileTitle')"
      :description="t('teacherVerification.publicProfileDescription')"
    />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-card class="simple-page-card" shadow="hover">
          <div class="feature-banner__content">
            <div>
              <div class="tag-list">
                <el-tag type="success" round>{{ t('teacherVerification.verifiedBadge') }}</el-tag>
                <el-tag v-for="category in profile?.teachingCategories || []" :key="category" type="info" round>
                  {{ category }}
                </el-tag>
              </div>
              <h1>{{ profile?.displayName }}</h1>
              <p>{{ profile?.bio || t('dashboard.noBio') }}</p>
            </div>
          </div>
        </el-card>

        <section class="placeholder-grid">
          <el-card class="placeholder-card" shadow="hover">
            <h2>{{ t('teacherVerification.publicCourses') }}</h2>
            <p>{{ t('teacherVerification.publicCoursesDescription') }}</p>
            <el-tag round>{{ profile?.courseCount ?? 0 }}</el-tag>
          </el-card>

          <el-card class="placeholder-card" shadow="hover">
            <h2>{{ t('teacherVerification.publicRatings') }}</h2>
            <p>{{ t('teacherVerification.publicRatingsDescription') }}</p>
            <el-tag type="success" round>{{ profile?.ratingAverage ?? 0 }}</el-tag>
          </el-card>

          <el-card class="placeholder-card" shadow="hover">
            <h2>{{ t('teacherVerification.publicReviews') }}</h2>
            <p>{{ t('teacherVerification.publicReviewsDescription') }}</p>
            <el-tag round>{{ profile?.reviewCount ?? 0 }}</el-tag>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
