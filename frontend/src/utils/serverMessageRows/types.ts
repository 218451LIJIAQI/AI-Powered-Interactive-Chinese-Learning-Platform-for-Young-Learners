/**
 * One backend message: [English as the backend sends it, Bahasa Melayu, Simplified Chinese]. In a template row each
 * value is a placeholder, a name in braces (`{title}`: a letter, then letters or digits), and all three name the same
 * values.
 */
export type MessageRow = readonly [en: string, ms: string, zh: string]
