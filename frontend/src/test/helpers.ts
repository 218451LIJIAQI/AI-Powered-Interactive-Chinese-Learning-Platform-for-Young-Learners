import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Plugin } from 'vue'

import type { PageResponse } from '@/types/api'

/** A visible control's client rects for focus tests; jsdom has no layout engine. */
export function visibleElementRects(): DOMRectList {
  const rect = new DOMRect(0, 0, 100, 20)
  return Object.assign([rect], { item: (index: number) => (index === 0 ? rect : null) })
}

/**
 * RouterLink for tests that mock vue-router: an `<a>` carrying the target in `href` (a path as is, a route object as
 * JSON). A `vi.mock` factory is hoisted above imports, so it loads the stub itself:
 * `RouterLink: (await import('@/test/helpers')).routerLinkStub`.
 */
export const routerLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a :href="typeof to === \'string\' ? to : JSON.stringify(to)"><slot /></a>',
}

export const toastMessageStub = { props: ['message'], template: '<p data-testid="toast">{{ message }}</p>' }

/** One page of a paged API response, like the backend's PageResponse. */
export function pageOf<T>(items: T[], page = 0, totalItems = items.length, size = 10): PageResponse<T> {
  return { items, page, totalItems, totalPages: Math.ceil(totalItems / size) }
}

/** A promise the test settles itself, for a request that is still pending while something else happens. */
export function deferred<T = unknown>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => {
    resolve = done
    reject = fail
  })
  return { promise, resolve, reject }
}

/** The button whose whole text is `name`; a test without one fails here, naming the button it looked for. */
export function buttonNamed(wrapper: Pick<VueWrapper, 'findAll'>, name: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === name)
  if (!button) throw new Error(`No button named "${name}"`)
  return button
}

/** A file with the reported byte size of an upload, without allocating its full contents. */
export function fileWithSize(name: string, type: string, bytes: number): File {
  const file = new File(['test upload'], name, { type })
  Object.defineProperty(file, 'size', { value: bytes })
  return file
}

/** Selects a file in a file input the way a browser does: set `files`, then fire `change`. */
export async function chooseFile(input: Omit<DOMWrapper<Element>, 'exists'>, file: File): Promise<void> {
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

export function pickAiQuizOption(wrapper: VueWrapper, question: number, option: number): Promise<void> {
  return wrapper.findAll('.ai-question')[question]!.findAll('input[type="radio"]')[option]!.setValue(true)
}

/** Picks and checks a multiple-choice answer in a Learn, review or level check player. */
export async function checkChoice(wrapper: VueWrapper, index: number): Promise<void> {
  await wrapper.get(`[data-testid="option-${index}"]`).trigger('click')
  await wrapper.get('[data-testid="check"]').trigger('click')
  await flushPromises()
}

/** Turns the flashcard on screen and rates it. */
export async function rateFlashcard(wrapper: VueWrapper, rating: 'learning' | 'known'): Promise<void> {
  await wrapper.get('[data-testid="flip"]').trigger('click')
  await wrapper.get(`[data-testid="rate-${rating}"]`).trigger('click')
  await flushPromises()
}

/** Runs a composable in the setup of a mounted render-less component, so its lifecycle hooks and injections work. */
export function withSetup<T>(composable: () => T, plugins: Plugin[] = []): { result: T; wrapper: VueWrapper } {
  let result!: T
  const wrapper = mount(
    defineComponent({
      setup() {
        result = composable()
        return () => null
      },
    }),
    { global: { plugins } },
  )
  return { result, wrapper }
}
