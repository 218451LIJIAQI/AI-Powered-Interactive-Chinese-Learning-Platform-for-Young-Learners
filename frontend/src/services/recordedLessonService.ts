import { apiClient } from './apiClient'
import type {
  LessonVideoProgressSummary,
  RecordedLessonVideo,
  RecordedLessonVideoPayload,
} from '../types/liveLearning'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

function buildFormData(payload: RecordedLessonVideoPayload, file?: File | null) {
  const formData = new FormData()
  formData.append('title', payload.title)
  formData.append('description', payload.description ?? '')
  formData.append('sourceType', payload.sourceType)
  formData.append('sortOrder', String(payload.sortOrder))

  if (payload.packageId != null) {
    formData.append('packageId', String(payload.packageId))
  }
  if (payload.externalVideoUrl) {
    formData.append('externalVideoUrl', payload.externalVideoUrl)
  }
  if (payload.durationSeconds != null) {
    formData.append('durationSeconds', String(payload.durationSeconds))
  }
  if (file) {
    formData.append('file', file)
  }

  return formData
}

export const recordedLessonService = {
  async getTeacherRecordedVideos(lessonId: number) {
    const response = await apiClient.get<ApiResponse<RecordedLessonVideo[]>>(`/teachers/lessons/${lessonId}/recorded-videos`)
    return response.data.data
  },

  async createRecordedVideo(lessonId: number, payload: RecordedLessonVideoPayload, file?: File | null) {
    const response = await apiClient.post<ApiResponse<RecordedLessonVideo>>(
      `/teachers/lessons/${lessonId}/recorded-videos`,
      buildFormData(payload, file),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    return response.data.data
  },

  async updateRecordedVideo(lessonId: number, videoId: number, payload: RecordedLessonVideoPayload, file?: File | null) {
    const response = await apiClient.put<ApiResponse<RecordedLessonVideo>>(
      `/teachers/lessons/${lessonId}/recorded-videos/${videoId}`,
      buildFormData(payload, file),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    return response.data.data
  },

  async archiveRecordedVideo(lessonId: number, videoId: number) {
    const response = await apiClient.patch<ApiResponse<RecordedLessonVideo>>(
      `/teachers/lessons/${lessonId}/recorded-videos/${videoId}/archive`,
    )
    return response.data.data
  },

  async getStudentRecordedVideos(lessonId: number) {
    const response = await apiClient.get<ApiResponse<RecordedLessonVideo[]>>(`/students/lessons/${lessonId}/recorded-videos`)
    return response.data.data
  },

  async getStudentRecordedVideo(lessonId: number, videoId: number) {
    const response = await apiClient.get<ApiResponse<RecordedLessonVideo>>(`/students/lessons/${lessonId}/recorded-videos/${videoId}`)
    return response.data.data
  },

  async getTeacherLessonVideoProgress(lessonId: number) {
    const response = await apiClient.get<ApiResponse<LessonVideoProgressSummary[]>>(`/teachers/lessons/${lessonId}/video-progress`)
    return response.data.data
  },
}
