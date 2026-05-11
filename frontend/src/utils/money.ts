/**
 * Format a monetary amount as Malaysian Ringgit (e.g. `RM 12.50`).
 *
 * The platform is currently single-currency (MYR), so the prefix is hard-coded
 * here. Pages that used to declare their own `formatMoney(amount)` helper
 * should import this instead — keeping the rendering consistent and giving us
 * a single seam to extend if/when multi-currency support lands.
 *
 * Accepts `number | null | undefined`; non-finite or missing values render
 * as `RM 0.00` so a missing field never breaks the table cell.
 */
export function formatMoney(amount: number | null | undefined): string {
  const value = Number(amount ?? 0)
  return `RM ${(Number.isFinite(value) ? value : 0).toFixed(2)}`
}
