<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import KidStatCard from '../../components/common/KidStatCard.vue'
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
      kid
      show-mascot
      :eyebrow="t('learning.rewardsEyebrow')"
      :title="t('learning.rewardsTitle')"
      :description="t('kid.rewardsHero')"
      :mascot-message="t('kid.rewardsHero')"
      mascot-holding="金"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <section class="student-stat-grid" v-if="rewards">
          <KidStatCard
            :label="t('learning.totalPoints')"
            :value="rewards.totalPoints"
            emoji="⭐"
            accent="sun"
          />
          <KidStatCard
            :label="t('learning.levelLabel')"
            :value="rewards.currentLevelNumber"
            emoji="🚀"
            accent="sea"
          />
          <KidStatCard
            :label="t('learning.currentStreak')"
            :value="rewards.streak.currentStreak"
            emoji="🔥"
            accent="berry"
          />
          <KidStatCard
            :label="t('learning.nextLevelProgress')"
            :value="`${rewards.progressToNextLevelPercent}%`"
            emoji="🎉"
            accent="lime"
          />
        </section>

        <section class="dashboard-grid" v-if="rewards">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">🏅 {{ t('kid.rewardsBadgesTitle') }}</p>
                <h2>{{ t('learning.achievementShelf') }}</h2>
              </div>
            </div>
            <div class="badge-grid" v-if="rewards.badges.length">
              <article v-for="badge in rewards.badges" :key="badge.badgeId" class="badge-medal">
                <span class="badge-medal__ribbon" aria-hidden="true">🏅</span>
                <strong>{{ badge.name }}</strong>
              </article>
            </div>
            <el-empty v-else :description="t('kid.rewardsEmptyBadges')" />
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">✨ {{ t('kid.rewardsPointsTitle') }}</p>
                <h2>{{ t('learning.recentWins') }}</h2>
              </div>
            </div>
            <div class="stacked-summary-list">
              <article v-for="item in rewards.recentPoints" :key="item.id" class="summary-row">
                <div>
                  <strong>🌟 {{ item.reason }}</strong>
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

<style scoped>
.badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 16px;
  margin-top: 8px;
}

.badge-medal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 12px;
  border-radius: 22px;
  background: linear-gradient(135deg, #fff3a8, #ff9f1c);
  border: 3px solid #fff;
  box-shadow: 0 12px 22px rgba(255, 159, 28, 0.25);
  text-align: center;
  transition: transform 0.2s ease;
  animation: medal-bounce 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
}

.badge-medal:nth-child(odd) {
  background: linear-gradient(135deg, #ffd1e6, #ff5d8f);
  box-shadow: 0 12px 22px rgba(255, 93, 143, 0.3);
}

.badge-medal:nth-child(3n) {
  background: linear-gradient(135deg, #c9f7d4, #2ec4b6);
  box-shadow: 0 12px 22px rgba(46, 196, 182, 0.3);
}

.badge-medal:hover {
  transform: translateY(-4px) rotate(-2deg);
}

.badge-medal__ribbon {
  font-size: 2.4rem;
  filter: drop-shadow(0 4px 6px rgba(0, 0, 0, 0.15));
  animation: medal-spin 4s ease-in-out infinite;
}

.badge-medal strong {
  font-family: 'Baloo 2', system-ui, sans-serif;
  color: #fff;
  font-weight: 800;
  font-size: 0.95rem;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.18);
}

@keyframes medal-bounce {
  from {
    opacity: 0;
    transform: scale(0.5);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes medal-spin {
  0%, 100% {
    transform: rotate(-8deg);
  }
  50% {
    transform: rotate(8deg);
  }
}
</style>
