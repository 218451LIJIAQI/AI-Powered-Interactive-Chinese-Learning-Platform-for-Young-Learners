import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { adminLearningApi } from '@/api/learn'
import { buttonNamed, deferred } from '@/test/helpers'
import { adminPracticeItem, tracingSet } from '@/test/learnFixtures'
import type { AdminPracticeSetDetail } from '@/types/learn'

import PracticeSetEditor from './PracticeSetEditor.vue'

const saveSet = vi.fn<typeof adminLearningApi.savePracticeSet>()
const MISMATCH = 'Give the strokes of each character, adding up to the total.'

function mountEditor(existing: AdminPracticeSetDetail | null = null) {
  return mount(PracticeSetEditor, { props: { existing, saveSet }, global: { stubs: { teleport: true } } })
}

/** A new tracing set: its title, the characters to write and their strokes in all. */
async function newTracingSet(characters: string, strokes: string) {
  const wrapper = mountEditor()
  await wrapper.get('[data-testid="editor-type"]').setValue('TRACING')
  await wrapper.get('[data-testid="editor-title"]').setValue('Write it')
  await wrapper.get('input[id$="-chars"]').setValue(characters)
  await wrapper.get('input[id$="-strokes"]').setValue(strokes)
  return wrapper
}

function strokeFields(wrapper: ReturnType<typeof mountEditor>) {
  return wrapper.findAll<HTMLInputElement>('[data-testid="editor-character-strokes"]')
}

function payload() {
  return saveSet.mock.calls[0]![2]
}

beforeEach(() => {
  saveSet.mockReset().mockResolvedValue(tracingSet())
})

describe('PracticeSetEditor', () => {
  it('connects a missing title to its field and clears the message when the title is supplied', async () => {
    const wrapper = mountEditor()
    await wrapper.get('form').trigger('submit')
    const title = wrapper.get('[data-testid="editor-title"]')
    expect(title.attributes('aria-invalid')).toBe('true')
    expect(title.attributes('aria-describedby')).toBe(wrapper.get('[role="alert"]').attributes('id'))
    await title.setValue('At the library')
    await flushPromises()
    expect(wrapper.get('[data-testid="editor-title"]').attributes('aria-invalid')).toBe('false')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('asks for the strokes of each character of a word, which must add up to the total once left', async () => {
    const wrapper = await newTracingSet('你好', '13')

    expect(strokeFields(wrapper).map((field) => field.attributes('aria-label'))).toEqual([
      'Strokes of 你',
      'Strokes of 好',
    ])
    await strokeFields(wrapper)[0]!.setValue('7')
    await strokeFields(wrapper)[1]!.setValue('5')
    // Nothing is said while the numbers are being typed.
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await strokeFields(wrapper)[1]!.trigger('blur')
    expect(wrapper.get('[role="alert"]').text()).toBe(MISMATCH)
    expect(strokeFields(wrapper)[1]!.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('[data-testid="editor-save"]').attributes('disabled')).toBeDefined()

    await strokeFields(wrapper)[1]!.setValue('6')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="editor-save"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(saveSet).toHaveBeenCalledWith(
      'TRACING',
      null,
      expect.objectContaining({
        title: 'Write it',
        strokeCount: 13,
        characterStrokes: [7, 6],
        items: [expect.objectContaining({ hanzi: '你好' })],
      }),
    )
    // The server works out the XP.
    expect(payload()).not.toHaveProperty('xpReward')
  })

  it('checks the strokes of each character against a changed total, and when a save is tried', async () => {
    const wrapper = await newTracingSet('你好', '13')
    await strokeFields(wrapper)[0]!.setValue('7')
    await strokeFields(wrapper)[1]!.setValue('6')

    await wrapper.get('input[id$="-strokes"]').setValue('12')
    await wrapper.get('input[id$="-strokes"]').trigger('blur')
    expect(wrapper.get('[role="alert"]').text()).toBe(MISMATCH)

    // Only some of them given: all of them or none.
    await wrapper.get('input[id$="-strokes"]').setValue('13')
    await strokeFields(wrapper)[1]!.setValue('')
    await wrapper.get('form').trigger('submit')
    expect(saveSet).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(MISMATCH)
  })

  it('says, as the server does, that a character has 1 to 60 strokes and shows single-character total errors on the strokes field', async () => {
    const wrapper = await newTracingSet('你好', '13')
    await strokeFields(wrapper)[0]!.setValue('0')
    await strokeFields(wrapper)[1]!.setValue('13')
    await strokeFields(wrapper)[0]!.trigger('blur')

    expect(wrapper.get('[role="alert"]').text()).toBe('Each character needs 1 to 60 strokes.')

    const single = await newTracingSet('好', '61')
    expect(single.get('input[id$="-strokes"]').attributes('max')).toBe('60')
    await single.get('input[id$="-strokes"]').trigger('blur')
    const strokesInput = single.get('input[id$="-strokes"]')
    const alert = single.get('[role="alert"]')
    expect(alert.text()).toBe('Strokes must be between 1 and 60.')
    expect(strokesInput.attributes('aria-invalid')).toBe('true')
    expect(strokesInput.attributes('aria-describedby')).toBe(alert.attributes('id'))
    expect(single.get('[data-testid="editor-save"]').attributes('disabled')).toBeDefined()

    const tooFewForWord = await newTracingSet('你好', '1')
    expect(tooFewForWord.get('input[id$="-strokes"]').attributes('min')).toBe('2')
    expect(tooFewForWord.get('input[id$="-strokes"]').attributes('max')).toBe('120')
    await tooFewForWord.get('input[id$="-strokes"]').trigger('blur')
    expect(tooFewForWord.get('[role="alert"]').text()).toBe('Each character needs 1 to 60 strokes.')
  })

  it('counts ten visible Unicode characters, including supplementary Han characters separated by spaces', async () => {
    const ten = Array(10).fill('𠀀').join(' ')
    const wrapper = await newTracingSet(`${ten}一`, '10')
    expect(wrapper.get('[role="alert"]').text()).toBe('A tracing set has at most 10 characters.')
    await wrapper.get('form').trigger('submit')
    expect(saveSet).not.toHaveBeenCalled()

    const field = wrapper.get<HTMLInputElement>('input[id$="-chars"]')
    expect(field.element.maxLength).toBeGreaterThanOrEqual(ten.length)
    await field.setValue(ten)
    expect(strokeFields(wrapper)).toHaveLength(10)
    await wrapper.get('form').trigger('submit')
    expect(saveSet).toHaveBeenCalledWith(
      'TRACING',
      null,
      expect.objectContaining({
        items: [expect.objectContaining({ hanzi: ten })],
      }),
    )
  })

  it('saves a word without the strokes of each character, and a single character without asking for them', async () => {
    const word = await newTracingSet('你好', '13')
    await word.get('form').trigger('submit')
    await flushPromises()
    expect(payload()).toEqual(expect.objectContaining({ strokeCount: 13, characterStrokes: [] }))

    saveSet.mockClear()
    const single = await newTracingSet('好', '6')
    expect(strokeFields(single)).toHaveLength(0)
    await single.get('form').trigger('submit')
    await flushPromises()
    expect(payload()).toEqual(expect.objectContaining({ strokeCount: 6, characterStrokes: [] }))
  })

  it('starts an edited tracing set from its strokes and shows the XP the server gave it', async () => {
    const wrapper = mountEditor(tracingSet())

    expect(strokeFields(wrapper).map((field) => field.element.value)).toEqual(['7', '6'])
    expect(wrapper.text()).toContain('+10 XP the first time a learner gets every item right.')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(saveSet).toHaveBeenCalledWith(
      'TRACING',
      2,
      expect.objectContaining({ stage: 'GUIDED', strokeCount: 13, characterStrokes: [7, 6] }),
    )
  })

  it('names no XP for a new set: the server works it out when the set is saved', () => {
    const wrapper = mountEditor()

    expect(wrapper.text()).not.toContain('XP')
  })

  it('describes the reward for finishing the first flashcard review', () => {
    const wrapper = mountEditor({
      ...tracingSet(),
      type: 'FLASHCARDS',
      items: [adminPracticeItem({ hanzi: '你好', meaning: 'hello' })],
      strokeCount: null,
      characterStrokes: null,
    })

    expect(wrapper.text()).toContain('+10 XP when a learner first finishes reviewing the cards.')
  })

  it('requires an explicit replacement for a deleted correct answer and prevents duplicate saves', async () => {
    const pending = deferred<AdminPracticeSetDetail>()
    saveSet.mockReturnValueOnce(pending.promise)
    const wrapper = mountEditor()
    await wrapper.get('[data-testid="editor-type"]').setValue('LISTENING')
    await wrapper.get('[data-testid="editor-title"]').setValue('Listening')
    const add = buttonNamed(wrapper, 'Add answer')
    await add.trigger('click')
    await wrapper.get('[aria-label="Remove answer 1"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(saveSet).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('Tick exactly one right answer.')
    await wrapper.findAll('input[type="radio"]')[1]!.setValue()
    await wrapper.get('form').trigger('submit')
    expect(saveSet).toHaveBeenCalledWith(
      'LISTENING',
      null,
      expect.objectContaining({
        items: [expect.objectContaining({ correctIndex: 1 })],
      }),
    )
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(saveSet).toHaveBeenCalledTimes(1)
    const cancelButton = wrapper.get<HTMLButtonElement>('.overlay-footer .btn-secondary')
    expect(cancelButton.element.disabled).toBe(false)
    await cancelButton.trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
    pending.resolve(tracingSet())
    await flushPromises()
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('restores focus to the surviving listening question instead of nested option controls when removing a question', async () => {
    const wrapper = mount(PracticeSetEditor, {
      attachTo: document.body,
      props: { existing: null, saveSet },
      global: { stubs: { teleport: true } },
    })
    try {
      await wrapper.get('[data-testid="editor-type"]').setValue('LISTENING')
      await buttonNamed(wrapper, 'Add question').trigger('click')
      await buttonNamed(wrapper, 'Add question').trigger('click')

      // Removing question 2 with 3 questions keeps remove buttons enabled and focuses surviving question 2's remove button.
      const removeSecond = wrapper.get<HTMLButtonElement>('[aria-label="Remove item 2"]')
      removeSecond.element.focus()
      await removeSecond.trigger('click')
      await flushPromises()
      expect((document.activeElement as HTMLElement | null)?.getAttribute('aria-label')).toBe('Remove item 2')

      // Removing down to 1 question disables question remove buttons and focuses the surviving question's first input.
      const removeLast = wrapper.get<HTMLButtonElement>('[aria-label="Remove item 2"]')
      removeLast.element.focus()
      await removeLast.trigger('click')
      await flushPromises()
      expect((document.activeElement as HTMLElement | null)?.getAttribute('aria-label')).toBe('Question 1')
    } finally {
      wrapper.unmount()
    }
  })
})
