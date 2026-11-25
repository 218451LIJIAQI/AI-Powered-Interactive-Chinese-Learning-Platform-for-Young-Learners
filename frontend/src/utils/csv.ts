type CsvCell = string | number | null

/** Cells a spreadsheet would read as a formula start with one of these (even after leading spaces); prefix `'`. */
const FORMULA_START = /^(?:[\t\r\n]|\s*[=+\-@])/

/** One CSV cell (RFC 4180): quoted when it holds a comma, a quote or a line break; formulas are neutralised. */
function csvCell(value: CsvCell): string {
  if (value === null) return ''
  let text = String(value)
  if (typeof value === 'string' && FORMULA_START.test(text)) text = `'${text}`
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** CSV text with CRLF line ends, the way spreadsheet programs expect it. */
export function toCsv(rows: readonly (readonly CsvCell[])[]): string {
  if (rows.length === 0) return ''
  return rows.map((row) => row.map(csvCell).join(',')).join('\r\n') + '\r\n'
}

/** A CSV download: a byte-order mark lets spreadsheet programs read the UTF-8 text (Malay and Chinese). */
export function csvBlob(rows: readonly (readonly CsvCell[])[]): Blob {
  return new Blob(['\uFEFF', toCsv(rows)], { type: 'text/csv;charset=utf-8' })
}
