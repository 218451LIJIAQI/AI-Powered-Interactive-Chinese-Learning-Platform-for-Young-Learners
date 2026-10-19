import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useAuthorizedFile } from './useAuthorizedFile'

function mountFile() {
  const urlApi = {
    createObjectURL: vi.fn(() => 'blob:practice-1'),
    revokeObjectURL: vi.fn(),
  }
  let state: ReturnType<typeof useAuthorizedFile> | undefined
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useAuthorizedFile(urlApi)
        return () => null
      },
    }),
  )
  if (!state) throw new Error('composable did not initialise')
  return { wrapper, state, urlApi }
}

describe('useAuthorizedFile', () => {
  it('turns an authorised blob download into an object URL and revokes it on unmount', async () => {
    const { wrapper, state, urlApi } = mountFile()
    const blob = new Blob(['audio'], { type: 'audio/webm' })

    await expect(
      state.load(() => Promise.resolve({ blob, contentType: null, fileName: 'a.webm' }), 'fail'),
    ).resolves.toBe(true)

    expect(state.objectUrl.value).toBe('blob:practice-1')
    expect(state.contentType.value).toBe('audio/webm')
    expect(urlApi.createObjectURL).toHaveBeenCalledWith(blob)

    wrapper.unmount()
    expect(urlApi.revokeObjectURL).toHaveBeenCalledWith('blob:practice-1')
  })

  it('exposes the server message when the file cannot be loaded', async () => {
    const { state } = mountFile()

    await state.load(() => Promise.reject({ response: { status: 404, data: { message: 'Not found.' } } }), 'fail')
    await flushPromises()

    expect(state.objectUrl.value).toBeNull()
    expect(state.error.value).toBe('Not found.')
    expect(state.loading.value).toBe(false)
  })
})
