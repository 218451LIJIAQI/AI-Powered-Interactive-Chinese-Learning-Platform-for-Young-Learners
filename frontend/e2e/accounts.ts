/** Ports and sign-in details of the end-to-end stack (the demo profile seeds these accounts at start-up). */
export const E2E_BACKEND_PORT = 8185
export const E2E_FRONTEND_PORT = 5185

const STUDENT_PASSWORD = 'E2e-Student-Pass-2026'
const TEACHER_PASSWORD = 'E2e-Teacher-Pass-2026'

export const E2E_ACCOUNTS = {
  admin: { email: 'admin@fyp.local', password: 'E2e-Admin-Pass-2026', name: 'Platform Administrator' },
  student: { email: 'student@fyp.local', password: STUDENT_PASSWORD, name: 'Amira Hassan' },
  classmate: { email: 'chen.hao@fyp.local', password: STUDENT_PASSWORD, name: 'Chen Hao' },
  teacher: { email: 'teacher@fyp.local', password: TEACHER_PASSWORD, name: 'Li Wei' },
  applicant: { email: 'applicant@fyp.local', password: TEACHER_PASSWORD, name: 'Mei Lin' },
} as const

export type E2eAccount = (typeof E2E_ACCOUNTS)[keyof typeof E2E_ACCOUNTS]

/** Password of the students that 07-student-setup registers through the sign-up form (each with a new e-mail). */
export const NEW_STUDENT_PASSWORD = 'E2e-New-Student-2026'
