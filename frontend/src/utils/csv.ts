export type CsvCell = string | number | null | undefined

/** Cells a spreadsheet would read as a formula start with one of these; they get a leading apostrophe. */
const FORMULA_START = /^[=+\-@\t\r]/

/** One CSV cell (RFC 4180): quoted when it holds a comma, a quote or a line break; formulas are neutralised. */
function csvCell(value: CsvCell): string {
  if (value === null || value === undefined) return ''
  let text = String(value)
  if (typeof value === 'string' && FORMULA_START.test(text)) text = `'${text}`
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** CSV text with CRLF line ends, the way spreadsheet programs expect it. */
export function toCsv(rows: readonly (readonly CsvCell[])[]): string {
  return rows.map((row) => row.map(csvCell).join(',')).join('\r\n') + '\r\n'
}

/** A CSV download: a byte-order mark lets spreadsheet programs read the UTF-8 text (Malay and Chinese). */
export function csvBlob(text: string): Blob {
  return new Blob(['\uFEFF', text], { type: 'text/csv;charset=utf-8' })
}
