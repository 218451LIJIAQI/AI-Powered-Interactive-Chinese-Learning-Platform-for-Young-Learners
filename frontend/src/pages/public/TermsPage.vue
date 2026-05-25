<template>
  <section class="public-page legal-page">
    <button class="btn btn-ghost legal-back-button" type="button" @click="goBack">Back</button>

    <article class="hero-card light legal-hero">
      <span class="legal-eyebrow">MandarinSpark</span>
      <h1>Terms of Service</h1>
      <p>
        These terms explain the rules for using MandarinSpark learning, teaching, AI assistance, payments, and account
        tools.
      </p>
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
 * Static terms-of-service page. Same authoring approach as
 * {@link PrivacyPage} — copy lives in the template so legal can
 * diff per release without touching any tooling.
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
    title: 'Accounts and roles',
    copy: 'Students, teachers, and admins must use the platform only through their assigned role. Teacher publishing, verification, classroom, finance, and admin operations are protected by role-based access.',
  },
  {
    title: 'Learning content and AI support',
    copy: 'Lessons, activities, quizzes, live classes, recorded resources, teacher feedback, and AI helper responses are provided for learning support. AI responses should be reviewed by learners, guardians, or teachers when needed.',
  },
  {
    title: 'Payments, refunds, and entitlements',
    copy: 'Paid course access, checkout, refunds, payouts, and entitlements are handled through platform commerce records and payment-provider availability. Failed provider operations do not grant paid access.',
  },
  {
    title: 'Safe use',
    copy: 'Users must not upload harmful files, misuse classroom links, harass other users, attempt unauthorized access, or bypass moderation and safety controls.',
  },
  {
    title: 'Support and changes',
    copy: 'Support requests can be started from the public support section or from signed-in notification/profile areas. Platform rules may be updated as the product evolves.',
  },
]
</script>
