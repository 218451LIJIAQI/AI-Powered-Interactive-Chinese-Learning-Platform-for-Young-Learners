/**
 * Reads a design token (CSS custom property) as resolved on `element`, e.g. for canvas drawing, which cannot use
 * `var()`. Falls back when the property is missing or the environment has no computed styles (SSR, tests).
 */
export function readCssToken(element: Element | null, name: `--${string}`, fallback: string): string {
  if (!element || typeof window === 'undefined' || typeof window.getComputedStyle !== 'function') return fallback
  const value = window.getComputedStyle(element).getPropertyValue(name).trim()
  return value || fallback
}
