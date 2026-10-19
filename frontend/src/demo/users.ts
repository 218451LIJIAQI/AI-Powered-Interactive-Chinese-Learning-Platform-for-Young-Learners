import type { CurrentUserResponse, UserRole } from '@/types/api'

export const demoUsers: Record<UserRole, CurrentUserResponse> = {
  STUDENT: {
    id: 101,
    fullName: 'Learner',
    email: 'learner@example.com',
    preferredLanguage: 'en',
    roles: ['STUDENT'],
    studentProfile: {
      id: 201,
      displayName: 'Learner',
      currentLevel: 'Level 3 Developing Learner',
      totalPoints: 250,
      institution: 'Universiti Malaya',
    },
    teacherProfile: null,
  },
  TEACHER: {
    id: 102,
    fullName: 'Li Wei',
    email: 'li.wei@example.com',
    preferredLanguage: 'en',
    roles: ['TEACHER'],
    studentProfile: null,
    teacherProfile: {
      id: 301,
      teacherStatus: 'APPROVED',
      bio: 'Structured beginner Mandarin teacher for university students and young professionals, focused on everyday conversation and pinyin.',
      rejectionReason: null,
    },
  },
  ADMIN: {
    id: 103,
    fullName: 'Admin Olivia',
    email: 'admin@example.com',
    preferredLanguage: 'en',
    roles: ['ADMIN'],
    studentProfile: null,
    teacherProfile: null,
  },
}
