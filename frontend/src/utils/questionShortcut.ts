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
      'input, textarea, select, [contenteditable]:not([contenteditable="false"]), [role="dialog"], [role="alertdialog"]',
    )
  )
    return null
  if (event.key === 'Enter') return target?.closest('button, a') ? null : 'enter'
  return /^[1-9]$/.test(event.key) ? Number(event.key) - 1 : null
}
