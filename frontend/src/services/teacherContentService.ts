import { apiClient } from './apiClient'
import type {
  CoursePackageFormPayload,
  CoursePackageItem,
  LearningModuleItem,
  LessonActivityFormPayload,
  LessonActivityItem,
  LessonFormPayload,
  LessonItem,
  ModuleFormPayload,
  PackageItemFormPayload,
} from '../types/contentManagement'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const teacherContentService = {
  async getModules() {
    const response = await apiClient.get<ApiResponse<LearningModuleItem[]>>('/teachers/modules')
    return response.data.data
  },

  async createModule(payload: ModuleFormPayload) {
    const response = await apiClient.post<ApiResponse<LearningModuleItem>>('/teachers/modules', payload)
    return response.data.data
  },

  async getModule(id: number) {
    const response = await apiClient.get<ApiResponse<LearningModuleItem>>(`/teachers/modules/${id}`)
    return response.data.data
  },

  async updateModule(id: number, payload: ModuleFormPayload) {
    const response = await apiClient.put<ApiResponse<LearningModuleItem>>(`/teachers/modules/${id}`, payload)
    return response.data.data
  },

  async submitModule(id: number) {
    const response = await apiClient.post<ApiResponse<LearningModuleItem>>(`/teachers/modules/${id}/submit`)
    return response.data.data
  },

  async archiveModule(id: number) {
    const response = await apiClient.patch<ApiResponse<LearningModuleItem>>(`/teachers/modules/${id}/archive`)
    return response.data.data
  },

  async getLessons() {
    const response = await apiClient.get<ApiResponse<LessonItem[]>>('/teachers/lessons')
    return response.data.data
  },

  async createLesson(payload: LessonFormPayload) {
    const response = await apiClient.post<ApiResponse<LessonItem>>('/teachers/lessons', payload)
    return response.data.data
  },

  async getLesson(id: number) {
    const response = await apiClient.get<ApiResponse<LessonItem>>(`/teachers/lessons/${id}`)
    return response.data.data
  },

  async updateLesson(id: number, payload: LessonFormPayload) {
    const response = await apiClient.put<ApiResponse<LessonItem>>(`/teachers/lessons/${id}`, payload)
    return response.data.data
  },

  async submitLesson(id: number) {
    const response = await apiClient.post<ApiResponse<LessonItem>>(`/teachers/lessons/${id}/submit`)
    return response.data.data
  },

  async archiveLesson(id: number) {
    const response = await apiClient.patch<ApiResponse<LessonItem>>(`/teachers/lessons/${id}/archive`)
    return response.data.data
  },

  async getLessonActivities(lessonId: number) {
    const response = await apiClient.get<ApiResponse<LessonActivityItem[]>>(`/teachers/lessons/${lessonId}/activities`)
    return response.data.data
  },

  async createLessonActivity(lessonId: number, payload: LessonActivityFormPayload) {
    const response = await apiClient.post<ApiResponse<LessonActivityItem>>(`/teachers/lessons/${lessonId}/activities`, payload)
    return response.data.data
  },

  async updateLessonActivity(activityId: number, payload: LessonActivityFormPayload) {
    const response = await apiClient.put<ApiResponse<LessonActivityItem>>(`/teachers/lesson-activities/${activityId}`, payload)
    return response.data.data
  },

  async archiveLessonActivity(activityId: number) {
    const response = await apiClient.patch<ApiResponse<LessonActivityItem>>(`/teachers/lesson-activities/${activityId}/archive`)
    return response.data.data
  },

  async getPackages() {
    const response = await apiClient.get<ApiResponse<CoursePackageItem[]>>('/teachers/packages')
    return response.data.data
  },

  async createPackage(payload: CoursePackageFormPayload) {
    const response = await apiClient.post<ApiResponse<CoursePackageItem>>('/teachers/packages', payload)
    return response.data.data
  },

  async getPackage(id: number) {
    const response = await apiClient.get<ApiResponse<CoursePackageItem>>(`/teachers/packages/${id}`)
    return response.data.data
  },

  async updatePackage(id: number, payload: CoursePackageFormPayload) {
    const response = await apiClient.put<ApiResponse<CoursePackageItem>>(`/teachers/packages/${id}`, payload)
    return response.data.data
  },

  async submitPackage(id: number) {
    const response = await apiClient.post<ApiResponse<CoursePackageItem>>(`/teachers/packages/${id}/submit`)
    return response.data.data
  },

  async archivePackage(id: number) {
    const response = await apiClient.patch<ApiResponse<CoursePackageItem>>(`/teachers/packages/${id}/archive`)
    return response.data.data
  },

  async addPackageItem(id: number, payload: PackageItemFormPayload) {
    const response = await apiClient.post<ApiResponse<CoursePackageItem>>(`/teachers/packages/${id}/items`, payload)
    return response.data.data
  },

  async removePackageItem(packageId: number, itemId: number) {
    const response = await apiClient.delete<ApiResponse<CoursePackageItem>>(`/teachers/packages/${packageId}/items/${itemId}`)
    return response.data.data
  },
}
