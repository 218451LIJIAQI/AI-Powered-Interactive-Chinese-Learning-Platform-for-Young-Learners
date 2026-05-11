/**
 * Shared envelope types returned by the backend Spring Boot controllers.
 *
 * Every REST endpoint wraps its payload in an `ApiResponse<T>`, and list
 * endpoints that support pagination use `PageResponse<T>`.
 *
 * Importing from one central file avoids the previous situation where each
 * service file (and authStore) re-declared the same shapes — with the risk of
 * them drifting out of sync when the backend contract evolves.
 */

export type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalPages: number
  totalItems: number
}
