/** Ports and sign-in details of the end-to-end stack (the demo profile seeds these accounts at start-up). */
export const E2E_BACKEND_PORT = 8185
export const E2E_FRONTEND_PORT = 5185

export const E2E_ACCOUNTS = {
  admin: { email: 'admin@fyp.local', password: 'E2e-Admin-Pass-2026', name: 'Platform Administrator' },
  student: { email: 'student@fyp.local', password: 'E2e-Student-Pass-2026', name: 'Amira Hassan' },
} as const

export type E2eAccount = (typeof E2E_ACCOUNTS)[keyof typeof E2E_ACCOUNTS]

/** Password of the learners that E2E specs (02-auth-and-access, 07-student-setup) register through the sign-up form. */
export const NEW_STUDENT_PASSWORD = 'E2e-New-Student-2026'
