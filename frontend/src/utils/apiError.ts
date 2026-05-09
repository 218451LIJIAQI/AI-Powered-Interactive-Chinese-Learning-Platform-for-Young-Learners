import { AxiosError } from 'axios'

type ApiResponseShape = {
  success?: boolean
  message?: string
  errors?: string[]
}

export function extractApiError(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data as ApiResponseShape | undefined

    if (Array.isArray(responseData?.errors) && responseData.errors.length > 0) {
      const firstError = responseData.errors.find((item) => typeof item === 'string' && item.trim().length > 0)
      if (firstError) {
        return firstError
      }
    }

    if (typeof responseData?.message === 'string' && responseData.message.trim().length > 0) {
      return responseData.message
    }

    if (typeof error.message === 'string' && error.message.trim().length > 0) {
      return error.message
    }
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return fallback
}
