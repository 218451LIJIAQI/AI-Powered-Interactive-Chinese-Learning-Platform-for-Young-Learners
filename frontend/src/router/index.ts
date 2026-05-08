import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import AiDisclaimerPage from '../pages/public/AiDisclaimerPage.vue'
import ChildSafetyPage from '../pages/public/ChildSafetyPage.vue'
import CourseDiscoveryPage from '../pages/public/CourseDiscoveryPage.vue'
import FAQPage from '../pages/public/FAQPage.vue'
import LandingPage from '../pages/public/LandingPage.vue'
import LoginPage from '../pages/public/LoginPage.vue'
import PrivacyPage from '../pages/public/PrivacyPage.vue'
import RegisterPage from '../pages/public/RegisterPage.vue'
import TermsPage from '../pages/public/TermsPage.vue'
import AdminDashboardPage from '../pages/admin/AdminDashboardPage.vue'
import StudentDashboardPage from '../pages/student/StudentDashboardPage.vue'
import TeacherDashboardPage from '../pages/teacher/TeacherDashboardPage.vue'
import { useAuthStore } from '../stores/authStore'

const routes = [
  {
    path: '/',
    component: PublicLayout,
    children: [
      {
        path: '',
        name: 'landing',
        component: LandingPage,
        meta: { titleKey: 'nav.home' },
      },
      {
        path: 'login',
        name: 'login',
        component: LoginPage,
        meta: { titleKey: 'nav.login' },
      },
      {
        path: 'register',
        name: 'register',
        component: RegisterPage,
        meta: { titleKey: 'nav.register' },
      },
      {
        path: 'courses',
        name: 'courses',
        component: CourseDiscoveryPage,
        meta: { titleKey: 'nav.courses' },
      },
      {
        path: 'faq',
        name: 'faq',
        component: FAQPage,
        meta: { titleKey: 'nav.faq' },
      },
      {
        path: 'terms',
        name: 'terms',
        component: TermsPage,
        meta: { titleKey: 'nav.terms' },
      },
      {
        path: 'privacy',
        name: 'privacy',
        component: PrivacyPage,
        meta: { titleKey: 'nav.privacy' },
      },
      {
        path: 'child-safety',
        name: 'child-safety',
        component: ChildSafetyPage,
        meta: { titleKey: 'nav.childSafety' },
      },
      {
        path: 'ai-disclaimer',
        name: 'ai-disclaimer',
        component: AiDisclaimerPage,
        meta: { titleKey: 'nav.aiDisclaimer' },
      },
    ],
  },
  {
    path: '/student',
    component: StudentLayout,
    meta: { requiresAuth: true, role: 'STUDENT', titleKey: 'nav.studentDashboard' },
    children: [
      {
        path: '',
        name: 'student-dashboard',
        component: StudentDashboardPage,
      },
    ],
  },
  {
    path: '/teacher',
    component: TeacherLayout,
    meta: { requiresAuth: true, role: 'TEACHER', titleKey: 'nav.teacherDashboard' },
    children: [
      {
        path: '',
        name: 'teacher-dashboard',
        component: TeacherDashboardPage,
      },
    ],
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, role: 'ADMIN', titleKey: 'nav.adminDashboard' },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: AdminDashboardPage,
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAuth && to.meta.role && authStore.userRole !== to.meta.role) {
    return redirectByRole(authStore.userRole)
  }

  return true
})

router.afterEach((to: RouteLocationNormalized) => {
  const titleKey = typeof to.meta.titleKey === 'string' ? String(to.meta.titleKey) : null
  const pageTitle = titleKey ? i18n.global.t(titleKey) : i18n.global.t('common.appName')
  document.title = `${pageTitle} | ${i18n.global.t('common.appName')}`
})

function redirectByRole(role: string | null) {
  if (role === 'STUDENT') {
    return { name: 'student-dashboard' }
  }

  if (role === 'TEACHER') {
    return { name: 'teacher-dashboard' }
  }

  if (role === 'ADMIN') {
    return { name: 'admin-dashboard' }
  }

  return { name: 'landing' }
}
