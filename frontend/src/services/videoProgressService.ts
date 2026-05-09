import { apiClient } from './apiClient'
import type { VideoProgress, VideoProgressPayload } from '../types/liveLearning'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const videoProgressService = {
  async getVideoProgress(videoId: number) {
    const response = await apiClient.get<ApiResponse<VideoProgress>>(`/students/video-progress/${videoId}`)
    return response.data.data
  },

  async saveVideoProgress(videoId: number, payload: VideoProgressPayload) {
    const response = await apiClient.post<ApiResponse<VideoProgress>>(`/students/video-progress/${videoId}`, payload)
    return response.data.data
  },

  async updateVideoProgress(videoId: number, payload: VideoProgressPayload) {
    const response = await apiClient.patch<ApiResponse<VideoProgress>>(`/students/video-progress/${videoId}`, payload)
    return response.data.data
  },

  async completeVideo(videoId: number) {
    const response = await apiClient.post<ApiResponse<VideoProgress>>(`/students/video-progress/${videoId}/complete`)
    return response.data.data
  },
}
