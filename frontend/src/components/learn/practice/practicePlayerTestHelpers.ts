import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import type { Component } from 'vue'

import type { HintStep, PracticeSet } from '@/types/learn'

export function hintStep(overrides: Partial<HintStep> = {}): HintStep {
  return {
    kind: 'RULE_OUT',
    words: ['Cup'],
    pinyin: [],
    text: null,
    count: null,
    ruledOut: ['Cup'],
    ...overrides,
  }
}

/** In the document (so the focus can be followed), with dialogs and toasts rendered in place. */
export async function mountPlayer(component: Component, set: PracticeSet): Promise<VueWrapper> {
  const wrapper = mount(component, { props: { set }, attachTo: document.body, global: { stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}
