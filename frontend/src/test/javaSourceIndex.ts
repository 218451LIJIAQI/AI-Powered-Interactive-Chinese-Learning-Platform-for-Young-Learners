/*
 * Lightweight Java lexical and declaration indexer for backendMessageScanner.ts.
 * Recognises the Java patterns used by this backend (classes, methods, records, fields, constants);
 * not a general Java parser.
 */

export type JavaToken = { kind: 'word' | 'text' | 'number' | 'mark' | 'char'; value: string }

export type JavaMethod = {
  name: string
  params: string[]
  paramTypes: Map<string, string>
  isPrivate: boolean
  start: number
  end: number
}

type JavaConstant = { index: number; numeric: boolean }

export type JavaFile = {
  path: string
  cls: string
  tokens: JavaToken[]
  methods: JavaMethod[]
  constants: Map<string, JavaConstant>
  fieldTypes: Map<string, string>
}

const JAVA_ESCAPES: Readonly<Record<string, string>> = { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f', s: ' ' }
const NOT_A_METHOD = new Set([
  'if',
  'for',
  'while',
  'switch',
  'catch',
  'synchronized',
  'new',
  'record',
  'return',
  'throw',
  'try',
])
const MODIFIERS = new Set(['static', 'final', 'private', 'public', 'protected'])
const OPENERS = new Set(['(', '[', '{'])
const CLOSERS = new Set([')', ']', '}'])
const PRIMITIVES = new Set(['boolean', 'byte', 'short', 'int', 'long', 'float', 'double', 'char'])

function unescapeJava(body: string): string {
  let out = ''
  for (let i = 0; i < body.length; i++) {
    if (body[i] !== '\\') {
      out += body[i]
      continue
    }
    const next = body[i + 1] ?? ''
    if (next === 'u') {
      let j = i + 1
      while (body[j] === 'u') j++
      out += String.fromCharCode(parseInt(body.slice(j, j + 4), 16))
      i = j + 3
    } else {
      out += JAVA_ESCAPES[next] ?? next
      i++
    }
  }
  return out
}

export function tokenize(src: string): JavaToken[] {
  const tokens: JavaToken[] = []
  let i = 0
  while (i < src.length) {
    const c = src[i]
    if (c.trim() === '') {
      i++
    } else if (src.startsWith('//', i)) {
      while (i < src.length && src[i] !== '\n') i++
    } else if (src.startsWith('/*', i)) {
      const stop = src.indexOf('*/', i + 2)
      if (stop < 0) throw new SyntaxError('Unterminated Java block comment.')
      i = stop + 2
    } else if (src.startsWith('"""', i)) {
      // A text block (an AI prompt) is never a message: only its extent matters.
      const stop = src.indexOf('"""', i + 3)
      if (stop < 0) throw new SyntaxError('Unterminated Java text block.')
      tokens.push({ kind: 'text', value: src.slice(i + 3, stop) })
      i = stop + 3
    } else if (c === '"' || c === "'") {
      let j = i + 1
      while (j < src.length && src[j] !== c) j += src[j] === '\\' ? 2 : 1
      if (j >= src.length) {
        throw new SyntaxError(`Unterminated Java ${c === '"' ? 'string' : 'character'} literal.`)
      }
      tokens.push({ kind: c === '"' ? 'text' : 'char', value: unescapeJava(src.slice(i + 1, j)) })
      i = j + 1
    } else if (/[A-Za-z_$]/.test(c)) {
      let j = i + 1
      while (j < src.length && /[A-Za-z0-9_$]/.test(src[j])) j++
      tokens.push({ kind: 'word', value: src.slice(i, j) })
      i = j
    } else if (/[0-9]/.test(c)) {
      let j = i + 1
      while (j < src.length && /[A-Za-z0-9_.]/.test(src[j])) j++
      tokens.push({ kind: 'number', value: src.slice(i, j) })
      i = j
    } else if (src.startsWith('->', i) || src.startsWith('::', i)) {
      tokens.push({ kind: 'mark', value: src.slice(i, i + 2) })
      i += 2
    } else {
      tokens.push({ kind: 'mark', value: c })
      i++
    }
  }
  return tokens
}

export function depthStep(token: JavaToken): number {
  if (token.kind !== 'mark') return 0
  return OPENERS.has(token.value) ? 1 : CLOSERS.has(token.value) ? -1 : 0
}

export function matchClose(tokens: JavaToken[], open: number): number {
  let depth = 0
  for (let k = open; k < tokens.length; k++) {
    depth += depthStep(tokens[k])
    if (depth === 0) return k
  }
  return tokens.length - 1
}

/** Splits arguments or concatenated message parts, leaving nested expressions intact. */
export function splitTopLevel(
  tokens: JavaToken[],
  from: number,
  to: number,
  separator: ',' | '+',
  angleBrackets = false,
): Array<[number, number]> {
  const parts: Array<[number, number]> = []
  let depth = 0
  let start = from
  for (let k = from; k < to; k++) {
    const token = tokens[k]
    depth += depthStep(token)
    if (angleBrackets && token.kind === 'mark') {
      if (token.value === '<') depth += 1
      else if (token.value === '>') depth = Math.max(0, depth - 1)
    }
    if (depth === 0 && token.kind === 'mark' && token.value === separator) {
      parts.push([start, k])
      start = k + 1
    }
  }
  if (to > from) parts.push([start, to])
  return parts
}

/** End of the expression starting at `from`: the first top-level `;` or `,`, or an unmatched closing bracket. */
export function expressionEnd(tokens: JavaToken[], from: number): number {
  let depth = 0
  for (let k = from; k < tokens.length; k++) {
    const token = tokens[k]
    if (token.kind === 'mark' && CLOSERS.has(token.value) && depth === 0) return k
    depth += depthStep(token)
    if (depth === 0 && token.kind === 'mark' && (token.value === ';' || token.value === ',')) return k
  }
  return tokens.length
}

export function lastWord(tokens: JavaToken[], from: number, to: number): string {
  const idx = lastWordIndex(tokens, from, to)
  return idx >= 0 ? tokens[idx].value : ''
}

function lastWordIndex(tokens: JavaToken[], from: number, to: number): number {
  let last = -1
  for (let k = from; k < to; k++) if (tokens[k].kind === 'word') last = k
  return last
}

/** Simple type name immediately before the variable or parameter at `nameIndex`, or null when none applies. */
function typeBefore(tokens: JavaToken[], nameIndex: number): string | null {
  let k = nameIndex - 1
  while (k >= 0 && tokens[k].kind === 'mark' && tokens[k].value === '.') k--
  while (k >= 0 && tokens[k].kind === 'mark' && (tokens[k].value === ']' || tokens[k].value === '>')) {
    const close = tokens[k].value
    const open = close === ']' ? '[' : '<'
    let depth = 0
    while (k >= 0) {
      if (tokens[k].kind === 'mark') {
        if (tokens[k].value === close) depth++
        else if (tokens[k].value === open) depth--
      }
      k--
      if (depth === 0) break
    }
  }
  const token = tokens[k]
  if (!token || token.kind !== 'word') return null
  if (!/^[A-Z]/.test(token.value) && !PRIMITIVES.has(token.value)) return null
  return token.value
}

function isMethodPrivate(tokens: JavaToken[], methodNameIndex: number): boolean {
  let k = methodNameIndex - 1
  let depth = 0
  while (k >= 0) {
    const token = tokens[k]
    if (token.kind === 'mark') {
      if (token.value === ')' || token.value === ']' || token.value === '>') {
        depth++
        k--
        continue
      }
      if (token.value === '(' || token.value === '[' || token.value === '<') {
        depth--
        if (depth < 0) break
        k--
        continue
      }
      if (depth === 0 && (token.value === ';' || token.value === '{' || token.value === '}')) break
    } else if (depth === 0 && token.kind === 'word' && token.value === 'private') {
      return true
    }
    k--
  }
  return false
}

export function enclosingMethod(methods: readonly JavaMethod[], index: number): JavaMethod | null {
  let best: JavaMethod | null = null
  for (const m of methods) if (index > m.start && index < m.end && (!best || m.start > best.start)) best = m
  return best
}

function findMethods(tokens: JavaToken[]): JavaMethod[] {
  const methods: JavaMethod[] = []
  for (let k = 1; k < tokens.length - 1; k++) {
    const token = tokens[k]
    if (token.kind !== 'word' || NOT_A_METHOD.has(token.value) || tokens[k + 1].value !== '(') continue
    const prev = tokens[k - 1]
    const declared =
      prev.kind === 'word'
        ? !NOT_A_METHOD.has(prev.value) && prev.value !== 'else'
        : ['>', ']'].includes(prev.value) || (/^[A-Z]/.test(token.value) && ['{', ';', '}', ')'].includes(prev.value))
    if (!declared) continue
    const close = matchClose(tokens, k + 1)
    let body = close + 1
    if (tokens[body]?.value === 'throws') {
      while (body < tokens.length && !['{', ';'].includes(tokens[body].value)) body++
    }
    if (tokens[body]?.value !== '{') continue
    const paramTypes = new Map<string, string>()
    const params = splitTopLevel(tokens, k + 2, close, ',', true).map(([s, e]) => {
      const idx = lastWordIndex(tokens, s, e)
      const name = idx >= 0 ? tokens[idx].value : ''
      if (name) {
        const paramType = typeBefore(tokens, idx)
        if (paramType) paramTypes.set(name, paramType)
      }
      return name
    })
    methods.push({
      name: token.value,
      params,
      paramTypes,
      isPrivate: isMethodPrivate(tokens, k),
      start: body,
      end: matchClose(tokens, body),
    })
  }
  return methods
}

/** `final` String, int and long class/nested-class fields with an initialiser: name -> initialiser index and kind. */
function findConstants(tokens: JavaToken[], methods: readonly JavaMethod[]): Map<string, JavaConstant> {
  const constants = new Map<string, JavaConstant>()
  for (let k = 1; k < tokens.length - 3; k++) {
    const typed = tokens[k].kind === 'word' && ['String', 'int', 'long'].includes(tokens[k].value)
    if (!typed || tokens[k + 1].kind !== 'word' || tokens[k + 2].kind !== 'mark' || tokens[k + 2].value !== '=') {
      continue
    }
    if (enclosingMethod(methods, k) !== null) continue
    let q = k - 1
    let isFinal = false
    while (q >= 0 && tokens[q].kind === 'word' && MODIFIERS.has(tokens[q].value)) {
      if (tokens[q--].value === 'final') isFinal = true
    }
    if (isFinal) constants.set(tokens[k + 1].value, { index: k + 3, numeric: tokens[k].value !== 'String' })
  }
  return constants
}

/** Class and nested-class field types (outside method bodies): field name -> simple type name. */
function findFieldTypes(tokens: JavaToken[], methods: readonly JavaMethod[]): Map<string, string> {
  const fields = new Map<string, string>()
  for (let k = 1; k < tokens.length - 1; k++) {
    if (tokens[k].kind !== 'word') continue
    const next = tokens[k + 1].value
    if (next !== ';' && next !== '=') continue
    if (enclosingMethod(methods, k) !== null) continue
    const fieldType = typeBefore(tokens, k)
    if (fieldType) fields.set(tokens[k].value, fieldType)
  }
  return fields
}

/** Resolves the simple declared type of an identifier visible at `before` inside `file`/`method`. */
export function declaredType(file: JavaFile, method: JavaMethod | null, name: string, before: number): string | null {
  const paramType = method?.paramTypes.get(name)
  if (paramType) return paramType
  if (method) {
    const t = file.tokens
    let resolved: string | null = null
    for (let k = method.start + 1; k < before && k < method.end - 1; k++) {
      if (t[k].kind !== 'word' || t[k].value !== name) continue
      const next = t[k + 1].value
      if (next !== '=' && next !== ';' && next !== ':') continue
      if (t[k - 1]?.value === 'var' && next === '=' && t[k + 2]?.value === 'new' && t[k + 3]?.kind === 'word') {
        resolved = t[k + 3].value
        continue
      }
      const localType = typeBefore(t, k)
      if (localType) resolved = localType
    }
    if (resolved) return resolved
  }
  return file.fieldTypes.get(name) ?? null
}

/** True when `tokens[k]` (`->`) opens a `case ... ->` or `default ->` switch arm rather than a lambda. */
export function isSwitchArrowArm(tokens: JavaToken[], arrowIndex: number): boolean {
  let depth = 0
  for (let k = arrowIndex - 1; k >= 0; k--) {
    const token = tokens[k]
    const step = depthStep(token)
    if (step !== 0) {
      depth -= step
      if (depth < 0) return false
      continue
    }
    if (depth !== 0) continue
    if (token.kind === 'mark') {
      if (token.value === ';' || token.value === '=') return false
      continue
    }
    if (token.kind !== 'word') continue
    if (token.value === 'return' || token.value === 'yield' || token.value === 'throw') return false
    if (token.value === 'case' || token.value === 'default') return true
  }
  return false
}

/** True when `tokens[k]` (`?`) is a Java generic wildcard (`<?>`, `<? extends ...>`) rather than a ternary operator. */
export function isWildcardQuestion(tokens: JavaToken[], qIndex: number): boolean {
  const prev = tokens[qIndex - 1]
  const next = tokens[qIndex + 1]
  return (
    (prev?.kind === 'mark' && (prev.value === '<' || prev.value === ',')) ||
    (next?.kind === 'mark' && (next.value === '>' || next.value === ','))
  )
}

export function indexJavaFile(path: string, source: string): JavaFile {
  const tokens = tokenize(source)
  const methods = findMethods(tokens)
  return {
    path,
    cls: path.slice(path.lastIndexOf('/') + 1, -'.java'.length),
    tokens,
    methods,
    constants: findConstants(tokens, methods),
    fieldTypes: findFieldTypes(tokens, methods),
  }
}
