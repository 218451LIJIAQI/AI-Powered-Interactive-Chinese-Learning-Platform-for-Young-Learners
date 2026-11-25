import { nextTick } from 'vue'

/** Direct `[data-row-item]` rows owned by `root`, excluding rows nested inside another `[data-row-item]` within `root`. */
function ownRows(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>('[data-row-item]')).filter((row) => {
    const ancestorRow = row.parentElement?.closest<HTMLElement>('[data-row-item]')
    return !ancestorRow || !root.contains(ancestorRow) || ancestorRow === root
  })
}

function ownFirst<T extends HTMLElement>(row: HTMLElement, selector: string): T | null {
  return Array.from(row.querySelectorAll<T>(selector)).find((el) => el.closest('[data-row-item]') === row) ?? null
}

/**
 * Restores keyboard focus after removing a row from an editable list: moves to the surviving remove button at the same
 * index (or the last remaining one) if enabled, else the first input in the surviving row, else the Add button.
 */
export function focusAfterRowRemove(
  container: () => HTMLElement | null,
  addButton: () => HTMLButtonElement | null,
  removedIndex: number,
  wasFocused: boolean,
): void {
  if (!wasFocused) return
  void nextTick(() => {
    const root = container()
    const rows = root ? ownRows(root) : []
    const removeButtons = rows
      .map((row) => ownFirst<HTMLButtonElement>(row, 'button[data-row-remove]:not(:disabled)'))
      .filter((button): button is HTMLButtonElement => button !== null)
    if (removeButtons.length) {
      removeButtons[Math.min(removedIndex, removeButtons.length - 1)]?.focus()
      return
    }
    const survivingRow = rows.length ? rows[Math.min(removedIndex, rows.length - 1)] : null
    const survivingInput = survivingRow ? ownFirst<HTMLInputElement>(survivingRow, 'input:not(:disabled)') : null
    if (survivingInput) {
      survivingInput.focus()
      return
    }
    addButton()?.focus()
  })
}

/**
 * When clicking an Add-row button hits the maximum row count and unmounts the button itself, moves focus to the first
 * input of the newly added last row so keyboard users do not lose focus to document.body.
 */
export function focusAfterRowAdd(
  container: () => HTMLElement | null,
  addButton: () => HTMLButtonElement | null,
  wasFocused: boolean,
): void {
  if (!wasFocused) return
  void nextTick(() => {
    const add = addButton()
    if (add && !add.disabled && document.activeElement === add) return
    const root = container()
    const rows = root ? ownRows(root) : []
    const lastRow = rows.length ? rows[rows.length - 1] : null
    if (lastRow) ownFirst<HTMLInputElement>(lastRow, 'input:not(:disabled)')?.focus()
  })
}
