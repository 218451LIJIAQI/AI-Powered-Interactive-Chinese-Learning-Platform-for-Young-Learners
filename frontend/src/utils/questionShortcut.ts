/** Keyboard shortcuts shared by question players. Number keys select a zero-based option and Enter checks or advances. */
export function questionShortcut(event: KeyboardEvent): number | 'enter' | null {
  if (
    event.defaultPrevented ||
    event.repeat ||
    event.isComposing ||
    event.altKey ||
    event.ctrlKey ||
    event.metaKey ||
    event.shiftKey
  )
    return null
  const target = event.target instanceof Element ? event.target : null
  if (
    target?.closest(
      'input, textarea, select, [contenteditable]:not([contenteditable="false"]), dialog, [role="dialog"], [role="alertdialog"], [role="listbox"], [role="combobox"], [role="menu"]',
    )
  )
    return null
  if (event.key === 'Enter') {
    const control = target?.closest(
      'button, a, summary, [role="button"], [role="link"], [role="tab"], [role="menuitem"], [role="switch"]',
    )
    return control && !control.matches('.option-row[aria-pressed="true"], .option-row[aria-checked="true"]')
      ? null
      : 'enter'
  }
  return /^[1-9]$/.test(event.key) ? Number(event.key) - 1 : null
}
