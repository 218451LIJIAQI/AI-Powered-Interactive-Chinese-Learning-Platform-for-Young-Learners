import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { testRouter } from '@/test/routes'
import type { AdminClassPage, AdminUnit } from '@/types/adminClasses'

const api = vi.hoisted(() => ({ addUnit: vi.fn(), renameUnit: vi.fn() }))

vi.mock('@/api/adminClasses', () => ({ adminClassesApi: api }))

import AdminClassUnits from './AdminClassUnits.vue'

const FIRST_UNIT: AdminUnit = {
  id: 70,
  title: 'First Conversations',
  lessons: [
    { id: 11, title: 'Meeting Classmates', status: 'PUBLISHED', hasUnpublishedChanges: false },
    { id: 12, title: 'University Life', status: 'PUBLISHED', hasUnpublishedChanges: true },
  ],
}
/** A class has 2 units: these are all of them. */
const UNITS: AdminUnit[] = [FIRST_UNIT, { id: 71, title: 'Campus Life', lessons: [] }]

function classPage(units: AdminUnit[]): AdminClassPage {
  return {
    id: 5,
    title: 'Mandarin for Campus Life',
    description: null,
    category: null,
    bestAfterUnit: null,
    status: 'PUBLISHED',
    lessonCount: 2,
    units,
    canPublish: false,
  }
}

async function mountUnits(units: readonly AdminUnit[] = UNITS, editable = true) {
  const router = await testRouter('/admin/content/classes/5')
  return mount(AdminClassUnits, {
    props: { classId: 5, units, editable },
    global: { plugins: [router], stubs: { teleport: true } },
  })
}

describe('AdminClassUnits', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('lists the numbered units with their lessons, statuses and Edit', async () => {
    const wrapper = await mountUnits()

    const units = wrapper.findAll('[data-testid="class-unit"]')
    expect(units.map((unit) => unit.get('.authoring-tile').text())).toEqual(['1', '2'])
    expect(units.map((unit) => unit.get('h3').text())).toEqual(['First Conversations', 'Campus Life'])
    expect(units[1]!.text()).toContain('No lessons yet')
    const lessons = units[0]!.findAll('[data-testid="class-lesson"]')
    expect(lessons[0]!.text()).toContain('Meeting Classmates')
    expect(lessons[0]!.text()).toContain('Published')
    expect(lessons[1]!.text()).toContain('Unpublished changes')
    expect(lessons[0]!.get('a').attributes('href')).toBe('/admin/content/classes/5/lessons/11')
    expect(lessons[0]!.get('a').text()).toBe('Edit')
  })

  it('starts a class without units with a primary Add unit', async () => {
    const wrapper = await mountUnits([])

    const empty = wrapper.get('.state-panel')
    expect(empty.text()).toContain('Add your first unit')
    expect(empty.get('[data-testid="add-first-unit"]').classes()).toContain('btn-primary')
    expect(wrapper.find('[data-testid="add-unit"]').exists()).toBe(false)
  })

  it('adds the second unit (X2.08) and says so (X1.09)', async () => {
    const added = classPage(UNITS)
    api.addUnit.mockResolvedValue(added)
    const wrapper = await mountUnits([FIRST_UNIT])

    expect(wrapper.find('[data-testid="units-full"]').exists()).toBe(false)
    await wrapper.get('[data-testid="add-unit"]').trigger('click')
    const dialog = wrapper.get('[data-testid="unit-dialog"]')
    expect(dialog.get('h2').text()).toBe('Add a unit')
    expect(dialog.get('input').attributes('placeholder')).toBe('e.g. Around town')
    await dialog.get('input').setValue('  Campus Life ')
    await dialog.get('form').trigger('submit')
    await flushPromises()

    expect(api.addUnit).toHaveBeenCalledWith(5, 'Campus Life')
    expect(wrapper.emitted('updated')).toEqual([[added]])
    expect(wrapper.emitted('notice')).toEqual([['Unit added.']])
    expect(wrapper.find('[data-testid="unit-dialog"]').exists()).toBe(false)
  })

  it('holds Add unit back once the class has its 2 units, and says why', async () => {
    const wrapper = await mountUnits()

    const add = wrapper.get<HTMLButtonElement>('[data-testid="add-unit"]')
    expect(add.element.disabled).toBe(true)
    const note = wrapper.get('[data-testid="units-full"]')
    expect(note.text()).toBe('A class has 2 units.')
    expect(add.attributes('aria-describedby')).toBe(note.attributes('id'))
    await add.trigger('click')
    expect(wrapper.find('[data-testid="unit-dialog"]').exists()).toBe(false)
    // Lessons still go into the units it has.
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Add lesson')).toHaveLength(2)
  })

  it('renames a unit from a dialog that holds its name (X2.12) and says so (X1.10)', async () => {
    api.renameUnit.mockResolvedValue(classPage(UNITS))
    const wrapper = await mountUnits()

    await wrapper.get('button[aria-label="Rename First Conversations"]').trigger('click')
    const dialog = wrapper.get('[data-testid="unit-dialog"]')
    expect(dialog.get('h2').text()).toBe('Rename unit')
    expect(dialog.get<HTMLInputElement>('input').element.value).toBe('First Conversations')
    await dialog.get('input').setValue('Hello')
    await dialog.get('form').trigger('submit')
    await flushPromises()

    expect(api.renameUnit).toHaveBeenCalledWith(70, 'Hello')
    expect(wrapper.emitted('notice')).toEqual([['Unit name saved.']])
  })

  it('asks for a title when the field is left empty, and holds the button back until it has one', async () => {
    const wrapper = await mountUnits([FIRST_UNIT])

    await wrapper.get('[data-testid="add-unit"]').trigger('click')
    const field = wrapper.get('[data-testid="unit-dialog"] input')
    await field.trigger('blur')
    expect(wrapper.get('[data-testid="unit-dialog"] [role="alert"]').text()).toBe('Add a unit title.')
    expect(wrapper.get<HTMLButtonElement>('[data-testid="unit-dialog-confirm"]').element.disabled).toBe(true)

    await field.setValue('Campus Life')
    expect(wrapper.find('[data-testid="unit-dialog"] [role="alert"]').exists()).toBe(false)
    expect(wrapper.get<HTMLButtonElement>('[data-testid="unit-dialog-confirm"]').element.disabled).toBe(false)
  })

  it('keeps the dialog open with the server’s reason when the unit cannot be saved', async () => {
    // Another admin added the second unit meanwhile.
    api.addUnit.mockRejectedValue({ response: { status: 409, data: { message: 'A class has 2 units.' } } })
    const wrapper = await mountUnits([FIRST_UNIT])

    await wrapper.get('[data-testid="add-unit"]').trigger('click')
    await wrapper.get('[data-testid="unit-dialog"] input').setValue('Campus Life')
    await wrapper.get('[data-testid="unit-dialog"] form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="unit-dialog"] [role="alert"]').text()).toBe('A class has 2 units.')
    expect(wrapper.emitted('updated')).toBeUndefined()
  })

  it('shows an archived class read-only: no Add, Rename or Edit', async () => {
    const wrapper = await mountUnits(UNITS, false)

    expect(wrapper.find('[data-testid="add-unit"]').exists()).toBe(false)
    expect(wrapper.findAll('button').filter((button) => button.text().includes('Rename'))).toHaveLength(0)
    expect(wrapper.findAll('button').filter((button) => button.text().includes('Add lesson'))).toHaveLength(0)
    expect(wrapper.find('[data-testid="class-lesson"] a').exists()).toBe(false)
    expect(wrapper.get('[data-testid="class-lesson"]').text()).toContain('Published')
  })
})
