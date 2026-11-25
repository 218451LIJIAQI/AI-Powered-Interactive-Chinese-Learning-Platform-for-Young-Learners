import { describe, expect, it } from 'vitest'

import { questionShortcut } from './questionShortcut'

function keyEvent(key: string, target: EventTarget, extra: KeyboardEventInit = {}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...extra })
  Object.defineProperty(event, 'target', { value: target })
  return event
}

describe('questionShortcut', () => {
  it('maps 1-9 to zero-based option indices and Enter outside interactive controls to enter', () => {
    const stage = document.createElement('div')
    expect(questionShortcut(keyEvent('1', stage))).toBe(0)
    expect(questionShortcut(keyEvent('4', stage))).toBe(3)
    expect(questionShortcut(keyEvent('0', stage))).toBeNull()
    expect(questionShortcut(keyEvent('Enter', stage))).toBe('enter')
  })

  it('blocks Enter on unselected option buttons and action buttons/links, but allows Enter on selected .option-row buttons', () => {
    const unselectedOption = document.createElement('button')
    unselectedOption.className = 'option-row'
    unselectedOption.setAttribute('aria-pressed', 'false')

    const pressedOption = document.createElement('button')
    pressedOption.className = 'option-row'
    pressedOption.setAttribute('aria-pressed', 'true')
    const innerSpan = document.createElement('span')
    pressedOption.appendChild(innerSpan)

    const checkedRadio = document.createElement('button')
    checkedRadio.className = 'option-row'
    checkedRadio.setAttribute('aria-checked', 'true')

    const actionButton = document.createElement('button')
    actionButton.className = 'btn'
    actionButton.setAttribute('aria-pressed', 'true')

    const link = document.createElement('a')

    expect(questionShortcut(keyEvent('Enter', unselectedOption))).toBeNull()
    expect(questionShortcut(keyEvent('Enter', pressedOption))).toBe('enter')
    expect(questionShortcut(keyEvent('Enter', innerSpan))).toBe('enter')
    expect(questionShortcut(keyEvent('Enter', checkedRadio))).toBe('enter')
    expect(questionShortcut(keyEvent('Enter', actionButton))).toBeNull()
    expect(questionShortcut(keyEvent('Enter', link))).toBeNull()
  })

  it('ignores shortcuts inside text fields, dialogs, IME composition, and modifier chords', () => {
    const input = document.createElement('input')
    expect(questionShortcut(keyEvent('1', input))).toBeNull()
    expect(questionShortcut(keyEvent('Enter', input))).toBeNull()

    const dialog = document.createElement('div')
    dialog.setAttribute('role', 'dialog')
    const dialogChild = document.createElement('span')
    dialog.appendChild(dialogChild)
    expect(questionShortcut(keyEvent('Enter', dialogChild))).toBeNull()

    const nativeDialog = document.createElement('dialog')
    const nativeDialogChild = document.createElement('span')
    nativeDialog.appendChild(nativeDialogChild)
    expect(questionShortcut(keyEvent('1', nativeDialogChild))).toBeNull()

    const listbox = document.createElement('div')
    listbox.setAttribute('role', 'listbox')
    expect(questionShortcut(keyEvent('2', listbox))).toBeNull()

    const stage = document.createElement('div')
    expect(questionShortcut(keyEvent('Enter', stage, { isComposing: true }))).toBeNull()
    expect(questionShortcut(keyEvent('1', stage, { ctrlKey: true }))).toBeNull()
  })
})
