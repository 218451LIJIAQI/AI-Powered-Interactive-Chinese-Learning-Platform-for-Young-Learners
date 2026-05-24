<template>
  <section class="public-page legal-page">
    <button class="btn btn-ghost legal-back-button" type="button" @click="goBack">Back</button>

    <article class="hero-card light legal-hero">
      <span class="legal-eyebrow">MandarinSpark</span>
      <h1>Privacy Policy</h1>
      <p>This policy explains how MandarinSpark handles learner, teacher, admin, learning, AI, commerce, notification, and moderation data.</p>
    </article>

    <section class="legal-grid">
      <article v-for="section in sections" :key="section.title" class="panel legal-card">
        <h2>{{ section.title }}</h2>
        <p>{{ section.copy }}</p>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
/**
 * Static privacy policy page. Authored copy lives directly in the
 * template so legal review can diff it cleanly per release — no
 * CMS, no markdown loader, no dynamic data.
 */
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push('/')
  }
}

const sections = [
  {
    title: 'Data we use',
    copy: 'MandarinSpark uses account profile data, role information, learning progress, quiz attempts, rewards, notifications, teacher verification records, payment records, refunds, payouts, and audit events to operate the platform.',
  },
  {
    title: 'Learner safety',
    copy: 'Young learner experiences are designed with restricted role access, moderation reporting, teacher verification, notification controls, and AI safety settings.',
  },
  {
    title: 'AI and reports',
    copy: 'AI usage may create operational metadata and report records for safety review. Prompt and response content is not required for normal admin usage log display.',
  },
  {
    title: 'Files and verification',
    copy: 'Uploaded teacher verification documents and recorded lesson resources are stored through protected backend file handling and are only available to authorized users.',
  },
  {
    title: 'Your choices',
    copy: 'Signed-in users can review account/profile areas, notifications, payments, refunds, teacher verification status, and support pathways according to their role.',
  },
]
</script>
