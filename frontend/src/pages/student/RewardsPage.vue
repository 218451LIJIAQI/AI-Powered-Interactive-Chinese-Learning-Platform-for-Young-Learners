<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { studentLearningService } from '../../services/studentLearningService'
import type { RewardSummary } from '../../types/learning'

const { t } = useI18n()
const loading = ref(true)
const rewards = ref<RewardSummary | null>(null)
const errorMessage = ref('')

async function loadRewards() {
  loading.value = true
  try {
    rewards.value = await studentLearningService.getRewards()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRewards()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('learning.rewardsEyebrow')"
      :title="t('learning.rewardsTitle')"
      :description="t('learning.rewardsDescription')"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <section class="student-stat-grid" v-if="rewards">
          <el-card class="metric-card metric-card--student metric-card--sun" shadow="hover">
            <p>{{ t('learning.totalPoints') }}</p>
            <strong>{{ rewards.totalPoints }}</strong>
          </el-card>
          <el-card class="metric-card metric-card--student metric-card--sea" shadow="hover">
            <p>{{ t('learning.levelLabel') }}</p>
            <strong>{{ rewards.currentLevelNumber }}</strong>
          </el-card>
          <el-card class="metric-card metric-card--student metric-card--berry" shadow="hover">
            <p>{{ t('learning.currentStreak') }}</p>
            <strong>{{ rewards.streak.currentStreak }}</strong>
          </el-card>
          <el-card class="metric-card metric-card--student metric-card--lime" shadow="hover">
            <p>{{ t('learning.nextLevelProgress') }}</p>
            <strong>{{ rewards.progressToNextLevelPercent }}%</strong>
          </el-card>
        </section>

        <section class="dashboard-grid" v-if="rewards">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('learning.badges') }}</p>
                <h2>{{ t('learning.achievementShelf') }}</h2>
              </div>
            </div>
            <div class="tag-list">
              <el-tag v-for="badge in rewards.badges" :key="badge.badgeId" type="success" round>
                {{ badge.name }}
              </el-tag>
            </div>
            <el-empty v-if="!rewards.badges.length" :description="t('learning.noBadges')" />
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('learning.pointsHistory') }}</p>
                <h2>{{ t('learning.recentWins') }}</h2>
              </div>
            </div>
            <div class="stacked-summary-list">
              <article v-for="item in rewards.recentPoints" :key="item.id" class="summary-row">
                <div>
                  <strong>{{ item.reason }}</strong>
                  <p>{{ item.sourceType || t('learning.learningActivityLabel') }}</p>
                </div>
                <el-tag type="success" round>+{{ item.pointsDelta }}</el-tag>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
