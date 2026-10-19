import type { AdminUserListItemResponse, PageResponse, TeacherStatus, UserRole } from '@/types/api'

export const ADMIN_USER_PAGE_SIZES = [10, 20, 50] as const
const ADMIN_USER_MAX_PAGE_SIZE = 100

export type AdminUserRoleFilter = 'ALL' | UserRole
export type AdminUserActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'
export type AdminUserTeacherStatusFilter = 'ALL' | TeacherStatus

export interface AdminUserDirectoryFilters {
  keyword: string
  role: AdminUserRoleFilter
  active: AdminUserActiveFilter
  teacherStatus: AdminUserTeacherStatusFilter
  /** 0-based page index. */
  page: number
  size: number
}

/** Query parameters accepted by GET /admin/users (FR-ADM-02). */
export interface AdminUserQueryParams {
  keyword?: string
  role?: UserRole
  status?: TeacherStatus
  active?: boolean
  page: number
  size: number
}

function clampSize(size: number): number {
  if (!Number.isFinite(size) || size < 1) return ADMIN_USER_PAGE_SIZES[0]
  return Math.min(ADMIN_USER_MAX_PAGE_SIZE, Math.floor(size))
}

/** Turns the page's filter state into the server-side query, omitting "all" filters and blank keywords. */
export function buildAdminUserQuery(filters: AdminUserDirectoryFilters): AdminUserQueryParams {
  const query: AdminUserQueryParams = {
    page: Math.max(0, Math.floor(filters.page) || 0),
    size: clampSize(filters.size),
  }
  const keyword = filters.keyword.trim()
  if (keyword) query.keyword = keyword
  if (filters.role !== 'ALL') query.role = filters.role
  if (filters.teacherStatus !== 'ALL') query.status = filters.teacherStatus
  if (filters.active !== 'ALL') query.active = filters.active === 'ACTIVE'
  return query
}

/**
 * Preview/demo counterpart of the server-side search: applies keyword (display name or e-mail,
 * case-insensitive), role, teacher status and active filters, then pages the result.
 */
export function filterDemoAdminUsers(
  users: readonly AdminUserListItemResponse[],
  query: AdminUserQueryParams,
): PageResponse<AdminUserListItemResponse> {
  const keyword = query.keyword?.trim().toLowerCase() ?? ''
  const matches = users.filter((user) => {
    if (keyword && !user.fullName.toLowerCase().includes(keyword) && !user.email.toLowerCase().includes(keyword)) {
      return false
    }
    if (query.role && !user.roles.includes(query.role)) return false
    if (query.status && user.teacherStatus !== query.status) return false
    if (query.active !== undefined && user.active !== query.active) return false
    return true
  })

  const size = clampSize(query.size)
  const page = Math.max(0, query.page)
  const start = page * size
  return {
    items: matches.slice(start, start + size),
    page,
    size,
    totalItems: matches.length,
    totalPages: Math.ceil(matches.length / size),
  }
}

/** The page to show after the current one shrank (e.g. its last user was deactivated out of the filter). */
export function clampAdminUserPage(page: number, totalPages: number): number {
  if (totalPages <= 0) return 0
  return Math.min(Math.max(0, page), totalPages - 1)
}
