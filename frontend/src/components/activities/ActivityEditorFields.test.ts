import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/plugins/i18n'
import { createEmptyActivityForm } from '@/utils/activityForm'

import ActivityEditorFields from './ActivityEditorFields.vue'

describe('ActivityEditorFields (shared admin / teacher editor)', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('edits the parent form in place, including the learning stage', async () => {
    const form = reactive(createEmptyActivityForm('FLASHCARD'))
    const wrapper = mount(ActivityEditorFields, { props: { form }, global: { plugins: [i18n] } })

    const stage = wrapper.get('[data-testid="activity-learning-stage"]')
    expect(stage.findAll('option').map((option) => option.text())).toEqual([
      'Not set',
      'Guided',
      'Scaffolded',
      'Independent',
    ])
    await stage.setValue('INDEPENDENT')
    await wrapper.get('textarea').setValue('茶')

    expect(form.learningStage).toBe('INDEPENDENT')
    expect(form.frontText).toBe('茶')
  })

  it('renders the story section with a slot for AI tools and can be fully disabled', () => {
    const form = reactive(createEmptyActivityForm('STORY_ACTIVITY'))
    const wrapper = mount(ActivityEditorFields, {
      props: { form, disabled: true },
      slots: { 'story-tools': '<div data-testid="story-tools">AI</div>' },
      global: { plugins: [i18n] },
    })

    expect(wrapper.find('[data-testid="activity-story-text"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="story-tools"]').exists()).toBe(true)
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
  })

  it('lists story options 1-based and stores the correct option 0-based', async () => {
    const form = reactive(createEmptyActivityForm('STORY_ACTIVITY'))
    form.storyQuestions[0].optionsText = '坐轻快铁\n坐巴士'
    const wrapper = mount(ActivityEditorFields, { props: { form }, global: { plugins: [i18n] } })

    const select = wrapper.get('[data-testid="activity-story-correct-option"]')
    expect(select.findAll('option').map((option) => option.text())).toEqual(['Not set yet', '1. 坐轻快铁', '2. 坐巴士'])
    await select.setValue('1')
    expect(form.storyQuestions[0].correctOptionIndex).toBe(1)
    await select.setValue('')
    expect(form.storyQuestions[0].correctOptionIndex).toBeNull()
  })

  it('shows the structured listening format and warns without blocking', async () => {
    const form = reactive(createEmptyActivityForm('LISTENING_EXERCISE'))
    const wrapper = mount(ActivityEditorFields, { props: { form }, global: { plugins: [i18n] } })

    expect(wrapper.get('[data-testid="activity-listening-help"]').text()).toContain('"answer":"你好"')
    expect(wrapper.findAll('[data-testid="activity-listening-warning"]')).toHaveLength(0)

    await wrapper.get('[data-testid="activity-listening-payload"]').setValue('{"prompt":"Listen"}')
    expect(wrapper.get('[data-testid="activity-listening-warning"]').text()).toContain('at least one question')

    await wrapper
      .get('[data-testid="activity-listening-payload"]')
      .setValue('{"questions":[{"question":"Which?","options":["你好","再见"],"answer":"谢谢"}]}')
    expect(wrapper.get('[data-testid="activity-listening-warning"]').text()).toContain('question 1')
    expect(form.questionPayload).toContain('谢谢')
  })
})
