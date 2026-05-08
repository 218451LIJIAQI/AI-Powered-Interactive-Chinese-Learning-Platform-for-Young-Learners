import { apiClient } from './apiClient'

export type LoginPayload = {
  email: string
  password: string
}

export type StudentRegisterPayload = {
  fullName: string
  email: string
  password: string
  confirmPassword: string
  preferredLanguage?: string
}

export type TeacherRegisterPayload = {
  fullName: string
  email: string
  password: string
  confirmPassword: string
  bio?: string
}

export const authService = {
  login: (payload: LoginPayload) => apiClient.post('/auth/login', payload),
  registerStudent: (payload: StudentRegisterPayload) => apiClient.post('/auth/register/student', payload),
  registerTeacher: (payload: TeacherRegisterPayload) => apiClient.post('/auth/register/teacher', payload),
  me: () => apiClient.get('/auth/me'),
  logout: () => apiClient.post('/auth/logout'),
}
