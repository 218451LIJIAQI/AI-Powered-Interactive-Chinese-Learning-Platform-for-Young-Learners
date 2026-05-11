import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type {
  LearningCategory,
  LearningLevel,
  PageResponse,
  PublicCourseDetail,
  PublicCourseSummary,
} from '../types/contentManagement'

export const publicContentService = {
  async getLevels() {
    const response = await apiClient.get<ApiResponse<LearningLevel[]>>('/public/levels')
    return response.data.data
  },

  async getCategories() {
    const response = await apiClient.get<ApiResponse<LearningCategory[]>>('/public/categories')
    return response.data.data
  },

  async discoverCourses(params: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<PageResponse<PublicCourseSummary>>>('/public/courses', { params })
    return response.data.data
  },

  async getCourseDetail(id: string) {
    const response = await apiClient.get<ApiResponse<PublicCourseDetail>>(`/public/courses/${id}`)
    return response.data.data
  },
}
