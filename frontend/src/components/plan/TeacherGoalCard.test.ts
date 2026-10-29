import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ learningGoal: vi.fn() }))

vi.mock('@/api/plan', () => ({ teacherPlanApi: { learningGoal: api.learningGoal } }))

import TeacherGoalCard from './TeacherGoalCard.vue'

async function mountCard() {
  const wrapper = mount(TeacherGoalCard, { props: { studentId: 7, studentName: 'Amira Hassan' } })
  await flushPromises()
  return wrapper
}

describe('TeacherGoalCard', () => {
  it('shows the student’s goal read-only, with where the teacher’s class is in the plan', async () => {
    api.learningGoal.mockResolvedValue({
      goalTitle: 'Travel to Shanghai',
      reason: 'TRAVEL',
      deadline: '2026-07-18',
      focus: ['LISTENING', 'CONVERSATION'],
      level: 'BEGINNER_1',
      targetLevel: 'BEGINNER_2',
      updatedAt: '2026-05-18T10:00:00',
      aiAssisted: true,
      yourClasses: [{ classId: 3, title: 'Mandarin for Beginners', position: 2 }],
      stepCount: 5,
    })
    const wrapper = await mountCard()

    expect(api.learningGoal).toHaveBeenCalledWith(7)
    const card = wrapper.get('[data-testid="learning-goal"]')
    expect(card.text()).toContain('From Amira Hassan’s learning plan')
    expect(card.text()).toContain('Read only')
    expect(card.text()).toContain('Travel to Shanghai by')
    expect(card.text()).toContain('Listening, Conversation')
    expect(card.text()).toContain('Beginner 1 → target Beginner 2')
    expect(card.text()).toContain('Mandarin for Beginners · step 2 of 5')
  })

  it('shows nothing for a student without a plan', async () => {
    api.learningGoal.mockResolvedValue(null)
    const wrapper = await mountCard()

    expect(wrapper.find('[data-testid="learning-goal"]').exists()).toBe(false)
    expect(wrapper.text()).toBe('')
  })
})
