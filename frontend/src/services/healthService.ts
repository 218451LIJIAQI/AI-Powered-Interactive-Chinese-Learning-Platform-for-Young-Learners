import { apiClient } from './apiClient'

export function fetchHealthStatus() {
  return apiClient.get('/health')
}
