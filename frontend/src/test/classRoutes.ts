import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

const Blank = defineComponent({ render: () => h('div') })

/** The named routes the classes pages link to, with the app's paths, for mounting a page with a real router. */
const ROUTES = [
  ['/classes', 'classes'],
  ['/classes/:classId(\\d+)', 'class-detail'],
  ['/auth', 'auth'],
  ['/student/classes', 'student-classes'],
  ['/student/classes/find', 'student-classes-find'],
  ['/student/classes/live', 'student-live-sessions'],
  ['/student/classes/:classId(\\d+)/:tab(results|feedback)?', 'student-class'],
  ['/student/classes/:classId(\\d+)/lessons/:lessonId(\\d+)', 'student-class-lesson'],
  [
    '/student/classes/:classId(\\d+)/lessons/:lessonId(\\d+)/activities/:activityType/:activityId(\\d+)',
    'student-class-activity',
  ],
  ['/student/quiz/:quizId(\\d+)', 'student-quiz'],
  ['/student/ai/:tab(talk|ask|quiz|story|voice)?', 'student-ai'],
  ['/teacher/classes', 'teacher-classes'],
  ['/teacher/classes/:classId(\\d+)/:tab(details|history)?', 'teacher-class'],
  ['/teacher/classes/:classId(\\d+)/lessons/:lessonId(\\d+)/:tab(activities|quiz|preview)?', 'teacher-lesson'],
  ['/teacher/live-sessions/:tab(past)?', 'teacher-live-sessions'],
  ['/teacher/students', 'teacher-students'],
  ['/teacher/students/feedback', 'teacher-feedback-history'],
  ['/admin/content', 'admin-content-review'],
  ['/admin/content/classes', 'admin-teacher-classes'],
  ['/admin/content/learning-path', 'admin-learning-path'],
  ['/admin/content/practice', 'admin-practice-library'],
] as const

/**
 * A memory router with the classes routes; the page under test is mounted directly, so every route renders a blank
 * component. Push `path` before mounting so `useRoute()` sees its params.
 */
export async function classesTestRouter(path: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(([routePath, name]) => ({ path: routePath, name, component: Blank })),
  })
  await router.push(path)
  await router.isReady()
  return router
}
