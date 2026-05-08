import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import AdminDashboardPage from '../pages/admin/AdminDashboardPage.vue'
import AiDisclaimerPage from '../pages/public/AiDisclaimerPage.vue'
import ChildSafetyPage from '../pages/public/ChildSafetyPage.vue'
import CourseDiscoveryPage from '../pages/public/CourseDiscoveryPage.vue'
import FAQPage from '../pages/public/FAQPage.vue'
import LandingPage from '../pages/public/LandingPage.vue'
import LoginPage from '../pages/public/LoginPage.vue'
import PrivacyPage from '../pages/public/PrivacyPage.vue'
import RegisterPage from '../pages/public/RegisterPage.vue'
import TermsPage from '../pages/public/TermsPage.vue'
import StudentDashboardPage from '../pages/student/StudentDashboardPage.vue'
import TeacherDashboardPage from '../pages/teacher/TeacherDashboardPage.vue'
import { pinia } from '../stores'
import { useAuthStore, type UserRole } from '../stores/authStore'

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: string
    requiresAuth?: boolean
    roles?: UserRole[]
    guestOnly?: boolean
  }
}

const routes: RouteRecordRaw[] = [
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
        meta: { titleKey: 'nav.login', guestOnly: true },
      },
      {
        path: 'register',
        name: 'register',
        component: RegisterPage,
        meta: { titleKey: 'nav.register', guestOnly: true },
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
    meta: { requiresAuth: true, roles: ['STUDENT' as UserRole], titleKey: 'nav.studentDashboard' },
    children: [
      {
        path: 'dashboard',
        name: 'student-dashboard',
        component: StudentDashboardPage,
      },
    ],
  },
  {
    path: '/teacher',
    component: TeacherLayout,
    meta: { requiresAuth: true, roles: ['TEACHER' as UserRole], titleKey: 'nav.teacherDashboard' },
    children: [
      {
        path: 'dashboard',
        name: 'teacher-dashboard',
        component: TeacherDashboardPage,
      },
    ],
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN' as UserRole], titleKey: 'nav.adminDashboard' },
    children: [
      {
        path: 'dashboard',
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

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)

  if (!authStore.initialized && authStore.token) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      // Redirect handling happens below and in the Axios interceptor.
    }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return redirectByRole(authStore.roles[0] ?? null)
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.roles?.length && !to.meta.roles.some((role) => authStore.hasRole(role))) {
    return redirectByRole(authStore.roles[0] ?? null)
  }

  return true
})

router.afterEach((to: RouteLocationNormalized) => {
  const titleKey = typeof to.meta.titleKey === 'string' ? String(to.meta.titleKey) : null
  const pageTitle = titleKey ? i18n.global.t(titleKey) : i18n.global.t('common.appName')
  document.title = `${pageTitle} | ${i18n.global.t('common.appName')}`
})

function redirectByRole(role: UserRole | null) {
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
