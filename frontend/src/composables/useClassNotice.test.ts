import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'

import { clearClassNotice, queueClassNotice, reconcileClassNoticeNavigation, useClassNotice } from './useClassNotice'

function mountConsumer(
  routeName?: string,
  undoJoin?: { label: string; run: (classId: number, title?: string) => void },
) {
  let state!: ReturnType<typeof useClassNotice>
  const Host = defineComponent({
    setup() {
      state = useClassNotice(undoJoin, routeName)
      return () => null
    },
  })
  const wrapper = mount(Host)
  return { wrapper, state }
}

afterEach(() => {
  clearClassNotice()
})

describe('useClassNotice', () => {
  it('displays a queued notice on the matching route and clears it after showing', () => {
    queueClassNotice({ message: 'Lesson completed.', targetRoute: 'student-class-lesson' })
    const first = mountConsumer('student-class-lesson')
    expect(first.state.toast.value).toBe('Lesson completed.')
    first.wrapper.unmount()

    const second = mountConsumer('student-class-lesson')
    expect(second.state.toast.value).toBeNull()
    second.wrapper.unmount()
  })

  it('drops a notice when mounted on a different route, cleared, or after a session switch', () => {
    queueClassNotice({ message: 'Lesson completed.', targetRoute: 'student-class-lesson' })
    const wrong = mountConsumer('student-classes')
    expect(wrong.state.toast.value).toBeNull()
    wrong.wrapper.unmount()
    const laterMatch = mountConsumer('student-class-lesson')
    expect(laterMatch.state.toast.value).toBeNull()
    laterMatch.wrapper.unmount()

    queueClassNotice({ message: 'Class joined.', targetRoute: 'student-classes' })
    clearClassNotice()
    const cleared = mountConsumer('student-classes')
    expect(cleared.state.toast.value).toBeNull()
    cleared.wrapper.unmount()

    queueClassNotice({ message: 'Class joined.', targetRoute: 'student-classes' })
    setAccessToken('next-session-token')
    const afterSession = mountConsumer('student-classes')
    expect(afterSession.state.toast.value).toBeNull()
    afterSession.wrapper.unmount()
  })

  it('reconciles router transitions by discarding notices on failed or diverted navigations', () => {
    queueClassNotice({ message: 'Lesson completed.', targetRoute: 'student-class-lesson' })
    reconcileClassNoticeNavigation('student-class-lesson', true)
    const afterFailure = mountConsumer('student-class-lesson')
    expect(afterFailure.state.toast.value).toBeNull()
    afterFailure.wrapper.unmount()

    queueClassNotice({ message: 'Lesson completed.', targetRoute: 'student-class-lesson' })
    reconcileClassNoticeNavigation('student-home', false)
    const afterDivert = mountConsumer('student-class-lesson')
    expect(afterDivert.state.toast.value).toBeNull()
    afterDivert.wrapper.unmount()

    const undo = vi.fn()
    queueClassNotice({
      message: 'Joined.',
      targetRoute: 'student-classes',
      joinedClassId: 7,
      joinedClassTitle: 'Mandarin 101',
    })
    reconcileClassNoticeNavigation('student-classes', false)
    const matched = mountConsumer('student-classes', { label: 'Undo', run: undo })
    expect(matched.state.toast.value).toBe('Joined.')
    matched.state.toastAction.value?.run()
    expect(undo).toHaveBeenCalledWith(7, 'Mandarin 101')
    matched.wrapper.unmount()
  })
})
