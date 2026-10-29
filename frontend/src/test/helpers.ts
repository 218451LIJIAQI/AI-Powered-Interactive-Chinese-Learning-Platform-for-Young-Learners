import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'

import type { PageResponse } from '@/types/api'

/**
 * RouterLink for tests that mock vue-router: an `<a>` around the link's content. A `vi.mock` factory is hoisted above
 * the imports, so it loads the stub itself: `RouterLink: (await import('@/test/helpers')).routerLinkStub`.
 */
export const routerLinkStub = { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' }

/** The same with the target in `href` (a path as is, a route object as JSON), for tests that check where links go. */
export const routerLinkHrefStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a :href="typeof to === \'string\' ? to : JSON.stringify(to)"><slot /></a>',
}

/** One page of a paged API response, like the backend's PageResponse. */
export function pageOf<T>(items: T[], page = 0, totalItems = items.length, size = 10): PageResponse<T> {
  return { items, page, size, totalItems, totalPages: Math.ceil(totalItems / size) }
}

/** Selects a file in a file input the way a browser does: set `files`, then fire `change`. */
export async function chooseFile(input: Omit<DOMWrapper<Element>, 'exists'>, file: File): Promise<void> {
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

/** Runs a composable in the setup of a mounted render-less component, so its lifecycle hooks and injections work. */
export function withSetup<T>(composable: () => T): { result: T; wrapper: VueWrapper } {
  let result!: T
  const wrapper = mount(
    defineComponent({
      setup() {
        result = composable()
        return () => null
      },
    }),
  )
  return { result, wrapper }
}
