const TINTS = ['violet', 'blue', 'teal', 'green', 'amber', 'rose'] as const

/** Stable palette colour for a name wherever its avatar or class cover appears. */
export function nameTint(name: string): (typeof TINTS)[number] {
  let sum = 0
  for (const character of name.trim()) sum += character.codePointAt(0) ?? 0
  return TINTS[sum % TINTS.length]
}
