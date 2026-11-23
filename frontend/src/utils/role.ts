import type { UserRole } from '@/types/api'

const ROLE_PREFIXES: Readonly<Record<UserRole, string>> = {
  STUDENT: '/student',
  ADMIN: '/admin',
}

const ROLE_HOMES: Readonly<Record<UserRole, string>> = {
  STUDENT: '/student/home',
  ADMIN: '/admin/dashboard',
}

export function workspacePathForRole(role: UserRole, section: 'notifications' | 'account'): string {
  return `${ROLE_PREFIXES[role]}/${section}`
}

export function defaultRouteForRole(role: UserRole | null): string {
  return (role && ROLE_HOMES[role]) || '/'
}
