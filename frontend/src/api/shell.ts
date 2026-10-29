import { apiGet } from './services'

/**
 * Badge counts for the signed-in user's sidebar (`GET /users/me/nav-counts`), keyed by `RoleNavItem.countKey`:
 * student `mistakes`, `supportReplies`; teacher `supportReplies`; admin `teacherApplications`, `reviewQueue`,
 * `aiReports`, `supportOpen`. A key the server does not send counts as zero.
 */
type NavCounts = Readonly<Record<string, number>>

export const shellApi = {
  navCounts() {
    return apiGet<NavCounts>('/users/me/nav-counts')
  },
}
