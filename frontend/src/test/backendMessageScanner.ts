import { messageKey } from '@/utils/serverMessages'

import {
  declaredType,
  depthStep,
  enclosingMethod,
  expressionEnd,
  indexJavaFile,
  isSwitchArrowArm,
  isWildcardQuestion,
  lastWord,
  matchClose,
  splitTopLevel,
  tokenize,
  type JavaFile,
  type JavaMethod,
  type JavaToken,
} from './javaSourceIndex'

export { tokenize, type JavaFile, type JavaToken }

/*
 * Completeness: the backend's Java sources are read, and every message that can reach a user must be translated.
 * Lexing and declaration indexing live in javaSourceIndex.ts; this file evaluates message expressions and traces
 * ResponseStatusException / Notification sinks across callers.
 */

/** A message part: fixed text, a parameter or record component of the code around it, or any other value. */
export type MessagePart =
  | { lit: string }
  | { param: number; text: string }
  | { field: string; index: number; text: string }
  | { hole: string }
export type MessageParts = MessagePart[]
type StatusExpression = { code: number } | { param: number }

export type ExtractedMessage = {
  status: number
  parts: MessageParts
  file: string
  /** A template message finished with a fixed value that the code passes in. */
  example: boolean
}

/** A parameter or record component that ends up in (or as) an exception message. */
type MessageSink = {
  kind: 'call' | 'new'
  cls: string
  name: string
  arity: number
  isPrivate: boolean
  /** Parameter indices referenced by `template` (empty when only `status` or a record `fieldIndex` is forwarded). */
  slots: number[]
  /** Record component index when `kind === 'new'`. */
  fieldIndex: number | null
  status: StatusExpression
  template: MessageParts
  /** The value is part of a longer text (a template), not the whole message. */
  embedded: boolean
  /** True once a template slot has been filled with a fixed literal value from a caller. */
  example: boolean
  /** Source file where the throw/sink originated (used when no caller matches). */
  origin: string
}

const HTTP_STATUS: Readonly<Record<string, number>> = {
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  PAYLOAD_TOO_LARGE: 413,
  TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500,
  SERVICE_UNAVAILABLE: 503,
}

/** Distinct parameter indices referenced by a message template, in appearance order. */
function paramSlots(parts: MessageParts): number[] {
  return [...new Set(parts.flatMap((part) => ('param' in part ? [part.param] : [])))]
}

const NUMERIC_TYPES = new Set(['byte', 'short', 'int', 'long', 'float', 'double', 'Integer', 'Long'])

/** A forwarded call can reach the same value along several paths. Keep each distinct value in discovery order. */
function collectDistinct<T>(items: Map<string, T>, value: T): void {
  items.set(JSON.stringify(value), value)
}

/** Substitutes every parameter in `slots` across the cartesian product of caller argument alternatives. */
function substituteSlots(
  template: MessageParts,
  slots: readonly number[],
  choices: readonly MessageParts[][],
): Array<{ parts: MessageParts; substitutedLiteral: boolean }> {
  const combinations = choices.reduce<MessageParts[][]>(
    (partial, options) => partial.flatMap((combination) => options.map((option) => [...combination, option])),
    [[]],
  )
  return combinations.map((combination) => {
    let substitutedLiteral = false
    const parts = template.flatMap((part): MessagePart[] => {
      if (!('param' in part)) return [part]
      const index = slots.indexOf(part.param)
      if (index < 0) return [part]
      const value = combination[index]
      if (value.every((entry) => 'lit' in entry)) substitutedLiteral = true
      return value
    })
    return { parts, substitutedLiteral }
  })
}

/** Finds the top-level ternary `?` / `:` split in `tokens[from..to)`, respecting nested ternaries and `<?>` wildcards. */
function findTopLevelTernary(
  tokens: JavaToken[],
  from: number,
  to: number,
): { question: number; colon: number } | null {
  let depth = 0
  for (let k = from; k < to; k++) {
    depth += depthStep(tokens[k])
    if (depth !== 0 || tokens[k].kind !== 'mark' || tokens[k].value !== '?' || isWildcardQuestion(tokens, k)) continue
    let inner = 0
    let ternaryDepth = 0
    for (let q = k + 1; q < to; q++) {
      inner += depthStep(tokens[q])
      if (inner !== 0 || tokens[q].kind !== 'mark') continue
      if (tokens[q].value === '?' && !isWildcardQuestion(tokens, q)) {
        ternaryDepth++
      } else if (tokens[q].value === ':') {
        if (ternaryDepth === 0) return { question: k, colon: q }
        ternaryDepth--
      }
    }
  }
  return null
}

/**
 * Finds the messages the backend sends with a failed request: the message of every ResponseStatusException
 * (GlobalExceptionHandler replaces IllegalArgumentException with the generic invalid-request message), followed
 * through constants, local variables, helper methods and record components (an upload policy's messages).
 */
export class BackendMessageExtractor {
  readonly files: JavaFile[] = []
  private readonly classes = new Map<string, JavaFile>()
  private readonly evaluating = new Set<string>()
  /** Record component name -> the records that declare it, with the component's position. */
  private readonly recordComponents = new Map<string, Array<{ record: string; index: number; arity: number }>>()

  constructor(sources: ReadonlyArray<{ path: string; source: string }>) {
    for (const { path, source } of sources) {
      const file = indexJavaFile(path, source)
      this.files.push(file)
      this.classes.set(file.cls, file)
      this.indexRecords(file)
    }
  }

  /** The `List.of("...", ...)` strings of a constant, e.g. the pass-through hints of FriendlyErrorMessageResolver. */
  stringList(cls: string, constant: string): string[] {
    const tokens = this.classes.get(cls)?.tokens ?? []
    for (let k = 0; k < tokens.length - 5; k++) {
      if (tokens[k].value !== constant || tokens[k + 1].value !== '=' || tokens[k + 4].value !== 'of') continue
      const close = matchClose(tokens, k + 5)
      return tokens.slice(k + 6, close).flatMap((token) => (token.kind === 'text' ? [token.value] : []))
    }
    return []
  }

  /** The literal string value of a `final String` constant, e.g. `PlatformSettingsService.DEFAULT_AI_DISCLAIMER_TEXT`. */
  stringConstant(cls: string, constant: string): string | null {
    const file = this.classes.get(cls)
    const entry = file?.constants.get(constant)
    if (!file || !entry || entry.numeric) return null
    const end = expressionEnd(file.tokens, entry.index)
    const evaluated = this.evaluate(file, entry.index, end, null)
    if (evaluated.length !== 1 || evaluated[0].some((part) => !('lit' in part))) return null
    return evaluated[0].map((part) => ('lit' in part ? part.lit : '')).join('')
  }

  private indexRecords(file: JavaFile) {
    const t = file.tokens
    for (let k = 0; k < t.length - 2; k++) {
      if (t[k].value !== 'record' || t[k + 1].kind !== 'word' || t[k + 2].value !== '(') continue
      const components = splitTopLevel(t, k + 3, matchClose(t, k + 2), ',')
      components.forEach(([s, e], index) => {
        const name = lastWord(t, s, e)
        const records = this.recordComponents.get(name) ?? []
        records.push({ record: t[k + 1].value, index, arity: components.length })
        this.recordComponents.set(name, records)
      })
    }
  }

  private evaluate(file: JavaFile, from: number, to: number, method: JavaMethod | null): MessageParts[] {
    const key = `${file.path}:${from}:${to}`
    if (this.evaluating.has(key)) throw new Error(`Cyclic message expression in ${file.path}.`)
    this.evaluating.add(key)
    try {
      const t = file.tokens
      // `condition ? a : b` gives both messages (supporting nested ternaries and `<?>` generic wildcards).
      const ternary = findTopLevelTernary(t, from, to)
      if (ternary) {
        return [
          ...this.evaluate(file, ternary.question + 1, ternary.colon, method),
          ...this.evaluate(file, ternary.colon + 1, to, method),
        ]
      }
      // `a + b + c` concatenates.
      const operands = splitTopLevel(t, from, to, '+')
      if (operands.length < 2) return this.evaluateOperand(file, from, to, method)
      let result: MessageParts[] = [[]]
      for (const [s, e] of operands) {
        const alternatives = this.evaluate(file, s, e, method)
        result = result.flatMap((prefix) => alternatives.map((alt) => [...prefix, ...alt]))
      }
      return result
    } finally {
      this.evaluating.delete(key)
    }
  }

  private constant(file: JavaFile, name: string): MessageParts[] | null {
    const entry = file.constants.get(name)
    return entry === undefined ? null : this.evaluate(file, entry.index, expressionEnd(file.tokens, entry.index), null)
  }

  private isNumericOperand(file: JavaFile, from: number, to: number, method: JavaMethod | null): boolean {
    const t = file.tokens
    const size = to - from
    if (size === 1 && t[from].kind === 'number') return true
    if (size === 1 && t[from].kind === 'word') {
      const name = t[from].value
      const type = declaredType(file, method, name, from)
      if (type !== null) return NUMERIC_TYPES.has(type)
      return file.constants.get(name)?.numeric === true
    }
    if (size === 3 && t[from + 1].value === '.' && this.classes.has(t[from].value)) {
      return this.classes.get(t[from].value)!.constants.get(t[from + 2].value)?.numeric === true
    }
    return false
  }

  private localVariable(file: JavaFile, method: JavaMethod, name: string, before: number): MessageParts[] | null {
    const t = file.tokens
    const alternatives: MessageParts[] = []
    const limit = Math.min(before, method.end - 2)
    for (let k = method.start; k < limit; k++) {
      if (t[k].value !== name || t[k - 1]?.value === '.') continue
      // Compound `+=` mutates the message dynamically: treat as opaque instead of dropping the appended suffix.
      if (t[k + 1].value === '+' && t[k + 2].value === '=') return null
      if (t[k + 1].value === '=' && t[k + 2].value !== '=') {
        alternatives.push(...this.evaluate(file, k + 2, expressionEnd(t, k + 2), method))
      }
    }
    return alternatives.length ? alternatives : null
  }

  private evaluateSameFileCall(
    file: JavaFile,
    name: string,
    arity: number,
    fallbackText: string,
  ): MessageParts[] | null {
    const helpers = file.methods.filter((m) => m.name === name && m.params.length === arity)
    if (!helpers.length) return null
    const returned = helpers.map((helper) => this.helperTexts(file, helper))
    if (returned.every((values) => values !== null)) return returned.flat()
    return [[{ hole: fallbackText }]]
  }

  private evaluateOperand(file: JavaFile, from: number, to: number, method: JavaMethod | null): MessageParts[] {
    const t = file.tokens
    const size = to - from
    const text = t
      .slice(from, to)
      .map((token) => (token.kind === 'text' ? JSON.stringify(token.value) : token.value))
      .join('')
    if (size === 1 && t[from].kind === 'text') return [[{ lit: t[from].value }]]
    if (size === 1 && t[from].kind === 'number' && /^[0-9]+[lL]?$/.test(t[from].value)) {
      return [[{ lit: t[from].value.replace(/[lL]$/, '') }]]
    }
    if (size === 1 && t[from].kind === 'word') {
      const name = t[from].value
      if (method?.params.includes(name)) return [[{ param: method.params.indexOf(name), text: name }]]
      const local = method ? this.localVariable(file, method, name, from) : null
      if (local) return local
      const constant = this.constant(file, name)
      if (constant) return constant
    }
    if (size === 3 && t[from + 1].value === '.' && this.classes.has(t[from].value)) {
      const constant = this.constant(this.classes.get(t[from].value)!, t[from + 2].value)
      if (constant) return constant
    }
    // Parenthesised expressions: unwrap ternaries, single operands, and string concatenations; keep numeric sums opaque.
    if (t[from].value === '(' && matchClose(t, from) === to - 1) {
      const innerFrom = from + 1
      const innerTo = to - 1
      if (findTopLevelTernary(t, innerFrom, innerTo)) {
        return this.evaluate(file, innerFrom, innerTo, method)
      }
      const operands = splitTopLevel(t, innerFrom, innerTo, '+')
      if (operands.length === 1) return this.evaluate(file, innerFrom, innerTo, method)
      const evaluated = operands.map(([s, e]) => ({
        isNumber: this.isNumericOperand(file, s, e, method),
        alts: this.evaluate(file, s, e, method),
      }))
      const producesText = evaluated.some(
        ({ isNumber, alts }) => !isNumber && alts.some((alt) => alt.some((part) => 'lit' in part)),
      )
      if (!producesText) return [[{ hole: text }]]
      return evaluated.reduce<MessageParts[]>(
        (acc, { alts }) => acc.flatMap((prefix) => alts.map((alt) => [...prefix, ...alt])),
        [[]],
      )
    }
    // `helper(x)` or `this.helper(x)`: all same-file implementations (including inner-class overrides) of that arity.
    if (t[from].kind === 'word' && t[from + 1]?.value === '(' && matchClose(t, from + 1) === to - 1) {
      const arity = splitTopLevel(t, from + 2, to - 1, ',').length
      const returned = this.evaluateSameFileCall(file, t[from].value, arity, text)
      if (returned) return returned
    }
    if (
      t[from].value === 'this' &&
      t[from + 1]?.value === '.' &&
      t[from + 2]?.kind === 'word' &&
      t[from + 3]?.value === '(' &&
      matchClose(t, from + 3) === to - 1
    ) {
      const arity = splitTopLevel(t, from + 4, to - 1, ',').length
      const returned = this.evaluateSameFileCall(file, t[from + 2].value, arity, text)
      if (returned) return returned
    }
    // A fixed-message helper, or `value.component()` of a record that carries the message.
    if (size === 5 && t[from + 1].value === '.' && t[from + 3].value === '(' && t[from + 4].value === ')') {
      const owner = this.classes.get(t[from].value)
      const getter = owner?.methods.find((m) => m.name === t[from + 2].value && m.params.length === 0)
      const body = owner && getter ? owner.tokens.slice(getter.start + 1, getter.end) : []
      if (body.length === 3 && body[0].value === 'return' && body[1].kind === 'text' && body[2].value === ';') {
        return [[{ lit: body[1].value }]]
      }
      const records = this.recordComponents.get(t[from + 2].value)
      if (records && !owner) {
        const receiverType = declaredType(file, method, t[from].value, from)
        const matched = receiverType ? records.filter((r) => r.record === receiverType) : records
        if (matched.length) {
          return matched.map((r) => [{ field: `${r.record}/${r.arity}`, index: r.index, text }])
        }
      }
    }
    return [[{ hole: text }]]
  }

  /**
   * The texts a helper method returns: each fixed text of a body that picks one (`return switch (...) { case A ->
   * "..."; }`, or `if (...) return "..."; return "...";`), or the text of a body that is one `return` (its parameters
   * as values). Null for any other body.
   */
  private helperTexts(file: JavaFile, helper: JavaMethod): MessageParts[] | null {
    const t = file.tokens
    if (t[helper.start + 1].value === 'return' && t[helper.start + 2].value === 'switch') {
      const block = matchClose(t, helper.start + 3) + 1
      const arms: MessageParts[] = []
      const end = matchClose(t, block)
      for (let k = block + 1; k < end; k++) {
        if (t[k].value !== '->') continue
        if (t[k + 1].kind !== 'text' || t[k + 2].value !== ';') return null
        arms.push([{ lit: t[k + 1].value }])
      }
      return arms.length ? arms : null
    }
    if (t[helper.start + 1].value === 'return' && expressionEnd(t, helper.start + 2) === helper.end - 1) {
      return this.evaluate(file, helper.start + 2, helper.end - 1, helper).map((alt) =>
        alt.map((p) => ('param' in p ? { hole: p.text } : p)),
      )
    }
    const texts: MessageParts[] = []
    for (let k = helper.start + 1; k < helper.end; k++) {
      // Skip lambda block bodies (which return values for stream predicates, etc.), but enter arrow-switch arms.
      if (t[k].value === '->' && t[k + 1].value === '{' && !isSwitchArrowArm(t, k)) {
        k = matchClose(t, k + 1)
        continue
      }
      if (t[k].kind !== 'word' || t[k].value !== 'return') continue
      const end = expressionEnd(t, k + 1)
      for (const parts of this.evaluate(file, k + 1, end, helper)) {
        if (parts.length === 1 && 'hole' in parts[0] && parts[0].hole === 'null') continue
        if (!parts.every((part) => 'lit' in part)) return null
        texts.push(parts)
      }
      k = end
    }
    return texts.length ? texts : null
  }

  /** A status the map does not know counts as a plain 400: the message is shown as sent, so it must be translated. */
  private evaluateStatus(file: JavaFile, from: number, to: number, method: JavaMethod | null): StatusExpression {
    const t = file.tokens
    const last = t[to - 1]
    if (last && HTTP_STATUS[last.value] !== undefined) return { code: HTTP_STATUS[last.value] }
    if (to - from === 1 && method?.params.includes(t[from].value))
      return { param: method.params.indexOf(t[from].value) }
    return { code: 400 }
  }

  /** True when tokens[k] (the sink's name followed by `(`) calls the sink's method or constructs its record. */
  private isCallOf(sink: Pick<MessageSink, 'kind' | 'cls' | 'isPrivate'>, file: JavaFile, k: number): boolean {
    const t = file.tokens
    const prev = t[k - 1]
    if (sink.kind === 'new') {
      let q = k - 1
      while (t[q]?.value === '.' && t[q - 1]?.kind === 'word') q -= 2
      return t[q]?.value === 'new'
    }
    if (sink.isPrivate && file.cls !== sink.cls) return false
    if (['>', ']', '::'].includes(prev.value)) return false
    const qualifier = prev.value === '.' ? t[k - 2] : null
    if (qualifier) {
      if (qualifier.value === 'this') return file.cls === sink.cls
      if (this.classes.has(qualifier.value)) return qualifier.value === sink.cls
      const method = enclosingMethod(file.methods, k)
      const receiverType = declaredType(file, method, qualifier.value, k)
      if (receiverType && (file.cls !== sink.cls || this.classes.has(receiverType))) {
        return receiverType === sink.cls
      }
      return true
    }
    if (prev.kind === 'word' && !['return', 'throw', 'else'].includes(prev.value)) return false
    return file.cls === sink.cls
  }

  private *callsOf(sink: Pick<MessageSink, 'kind' | 'cls' | 'name' | 'arity' | 'isPrivate'>) {
    for (const file of this.files) {
      const tokens = file.tokens
      for (let k = 1; k < tokens.length - 1; k++) {
        if (tokens[k].value !== sink.name || tokens[k + 1].value !== '(' || !this.isCallOf(sink, file, k)) continue
        const args = splitTopLevel(tokens, k + 2, matchClose(tokens, k + 1), ',')
        if (args.length === sink.arity) yield { file, args, method: enclosingMethod(file.methods, k) }
      }
    }
  }

  extract(): ExtractedMessage[] {
    const found = new Map<string, ExtractedMessage>()
    const sinks = new Map<string, MessageSink>()
    const asHoles = (alt: MessageParts): MessageParts =>
      alt.map((p) => ('field' in p || 'param' in p ? { hole: p.text } : p))
    const addCallSink = (
      file: JavaFile,
      method: JavaMethod,
      sink: Pick<MessageSink, 'slots' | 'status' | 'template' | 'embedded' | 'example' | 'origin'>,
    ) => {
      collectDistinct(sinks, {
        kind: 'call',
        cls: file.cls,
        name: method.name,
        arity: method.params.length,
        isPrivate: method.isPrivate,
        fieldIndex: null,
        ...sink,
      })
    }

    /**
     * Records the messages of one throw, or of one place that supplies a helper's message. A parameter or record
     * component that is the whole message is followed to its callers. Inside a longer text it is a value (a status, a
     * content label, a title): the text is a template, and each caller that passes a fixed value adds an example.
     */
    const record = (
      file: JavaFile,
      method: JavaMethod | null,
      status: StatusExpression,
      alternatives: MessageParts[],
      example = false,
    ) => {
      for (const alt of alternatives) {
        const forwarded = alt.find((p) => 'param' in p || 'field' in p)
        if (forwarded && alt.length === 1) {
          if ('field' in forwarded) {
            const [name, arity] = forwarded.field.split('/')
            collectDistinct(sinks, {
              kind: 'new',
              cls: '',
              name,
              arity: Number(arity),
              isPrivate: false,
              slots: [],
              fieldIndex: forwarded.index,
              status,
              template: alt,
              embedded: false,
              example: false,
              origin: file.path,
            })
            continue
          }
          if ('param' in forwarded && method) {
            addCallSink(file, method, {
              slots: [forwarded.param],
              status,
              template: alt,
              embedded: false,
              example: false,
              origin: file.path,
            })
            continue
          }
        }
        if ('code' in status) {
          collectDistinct(found, {
            status: status.code,
            parts: asHoles(alt),
            file: file.path,
            example: example && alt.every((p) => 'lit' in p),
          })
        }
        const slots = paramSlots(alt)
        if ((slots.length > 0 || 'param' in status) && method) {
          addCallSink(file, method, {
            slots,
            status,
            template: alt,
            embedded: true,
            example,
            origin: file.path,
          })
        }
      }
    }

    for (const file of this.files) {
      const t = file.tokens
      for (let k = 0; k < t.length - 2; k++) {
        if (t[k].value !== 'new' || t[k + 2].value !== '(') continue
        if (t[k + 1].value !== 'ResponseStatusException') continue
        const args = splitTopLevel(t, k + 3, matchClose(t, k + 2), ',')
        const messageArg = args[1]
        if (!messageArg) continue
        const method = enclosingMethod(file.methods, k)
        const status = this.evaluateStatus(file, args[0][0], args[0][1], method)
        record(file, method, status, this.evaluate(file, messageArg[0], messageArg[1], method))
      }
    }

    // Follow each forwarded parameter or record component to the places that supply it, until nothing new is found.
    for (const sink of sinks.values()) {
      let called = false
      for (const { file, args, method } of this.callsOf(sink)) {
        called = true
        const status: StatusExpression =
          'param' in sink.status ? this.evaluateStatus(file, ...args[sink.status.param], method) : sink.status
        if (!sink.embedded) {
          const argIndex = sink.kind === 'new' ? sink.fieldIndex! : sink.slots[0]
          record(file, method, status, this.evaluate(file, ...args[argIndex], method))
          continue
        }
        if (sink.slots.length === 0) {
          record(file, method, status, [sink.template], sink.example)
          continue
        }
        if ('param' in sink.status && 'code' in status && !sink.example) {
          collectDistinct(found, {
            status: status.code,
            parts: asHoles(sink.template),
            file: file.path,
            example: false,
          })
        }
        const choices = sink.slots.map((slot) => this.evaluate(file, ...args[slot], method))
        for (const { parts, substitutedLiteral } of substituteSlots(sink.template, sink.slots, choices)) {
          const nextExample = sink.example || substitutedLiteral
          const remainingParams = paramSlots(parts)
          const addFallbackTemplate = () => {
            if (!method || !('param' in status)) return
            addCallSink(file, method, {
              slots: [],
              status,
              template: asHoles(sink.template),
              embedded: true,
              example: false,
              origin: sink.origin,
            })
          }
          if (remainingParams.length > 0 && method) {
            addCallSink(file, method, {
              slots: remainingParams,
              status,
              template: parts,
              embedded: true,
              example: nextExample,
              origin: sink.origin,
            })
            if (nextExample) addFallbackTemplate()
          } else if (parts.every((p) => 'lit' in p)) {
            if ('code' in status) {
              collectDistinct(found, {
                status: status.code,
                parts,
                file: file.path,
                example: true,
              })
            } else if ('param' in status && method) {
              addCallSink(file, method, {
                slots: [],
                status,
                template: parts,
                embedded: true,
                example: true,
                origin: sink.origin,
              })
              addFallbackTemplate()
            }
          } else if ('param' in status && method) {
            addCallSink(file, method, {
              slots: [],
              status,
              template: asHoles(parts),
              embedded: true,
              example: false,
              origin: sink.origin,
            })
          }
        }
      }
      // Fail loudly when a forwarded message or parameterized-status sink has no matching Java call site.
      if (!called && (!sink.embedded || !('code' in sink.status))) {
        collectDistinct(found, {
          status: 'code' in sink.status ? sink.status.code : 400,
          parts: asHoles(sink.template),
          file: sink.origin,
          example: sink.example && sink.template.every((p) => 'lit' in p),
        })
      }
    }
    return [...found.values()]
  }

  /**
   * The titles and messages of in-app notifications: the text set as a `Notification`'s title or message
   * (NotificationService.draft, DemoOpsSeeder.notify), followed through the parameters that carry it
   * (safeCreateNotification, notifyActiveAdmins and the helpers around them) to the places that write it. A value the
   * code computes (a name, a title, a number) is a hole.
   */
  notificationTexts(): NotificationText[] {
    type TextSink = {
      cls: string
      name: string
      arity: number
      isPrivate: boolean
      field: NotificationField
      template: MessageParts
      origin?: string
    }
    const found = new Map<string, NotificationText>()
    const sinks = new Map<string, TextSink>()
    /**
     * A text with a parameter of its method in it is followed to the callers; any other is one the backend writes. The
     * text belongs to the file that wrote its first fixed words, not to the one that passes a value such as a reason.
     */
    const resolve = (
      file: JavaFile,
      method: JavaMethod | null,
      field: NotificationField,
      parts: MessageParts,
      origin = parts.some((p) => 'lit' in p) ? file.path : undefined,
    ) => {
      if (method && parts.some((p) => 'param' in p)) {
        collectDistinct(sinks, {
          cls: file.cls,
          name: method.name,
          arity: method.params.length,
          isPrivate: method.isPrivate,
          field,
          template: parts,
          origin,
        })
        return
      }
      collectDistinct(found, {
        field,
        parts: parts.map((p) => ('lit' in p || 'hole' in p ? p : { hole: p.text })),
        file: origin ?? file.path,
      })
    }

    for (const file of this.files) {
      const t = file.tokens
      const entitiesByMethod = new Map<JavaMethod | null, Set<string>>()
      for (let k = 0; k < t.length - 4; k++) {
        const declared =
          (t[k].value === 'Notification' || t[k].value === 'var') && t[k + 1].kind === 'word' && t[k + 2].value === '='
        if (declared && t[k + 3].value === 'new' && t[k + 4].value === 'Notification') {
          const method = enclosingMethod(file.methods, k)
          let names = entitiesByMethod.get(method)
          if (!names) {
            names = new Set<string>()
            entitiesByMethod.set(method, names)
          }
          names.add(t[k + 1].value)
        }
      }
      for (let k = 0; k < t.length - 3; k++) {
        const field = NOTIFICATION_SETTERS.get(t[k + 2].value)
        if (!field || t[k + 1].value !== '.' || t[k + 3].value !== '(') continue
        const method = enclosingMethod(file.methods, k)
        if (!entitiesByMethod.get(method)?.has(t[k].value)) continue
        for (const alt of this.evaluate(file, k + 4, matchClose(t, k + 3), method)) resolve(file, method, field, alt)
      }
    }

    for (const sink of sinks.values()) {
      const slots = paramSlots(sink.template)
      let called = false
      for (const { file, args, method } of this.callsOf({ ...sink, kind: 'call' })) {
        called = true
        const choices = slots.map((slot) => this.evaluate(file, ...args[slot], method))
        for (const { parts } of substituteSlots(sink.template, slots, choices)) {
          resolve(file, method, sink.field, parts, sink.origin)
        }
      }
      // A method no Java code calls (a request handler, a scheduled job) gets its parameters from outside: values.
      const owner = this.classes.get(sink.cls)
      if (!called && owner) resolve(owner, null, sink.field, sink.template, sink.origin)
    }
    return [...found.values()]
  }
}

export type NotificationField = 'title' | 'message'
/** A notification title or message as the backend writes it, and the file that writes it. */
export type NotificationText = { field: NotificationField; parts: MessageParts; file: string }
const NOTIFICATION_SETTERS = new Map<string, NotificationField>([
  ['setTitle', 'title'],
  ['setMessage', 'message'],
])

/** Renders extracted message parts into normalized English text with `{}` placeholders. */
export function messageText(parts: MessageParts): string {
  return parts
    .map((p) => ('lit' in p ? p.lit : '{}'))
    .join('')
    .split(' ')
    .filter(Boolean)
    .join(' ')
}

/** Every string literal of the backend sources, with `"a" + "b"` joined, as lookup keys. */
export function javaStringLiterals(files: readonly JavaFile[]): Set<string> {
  const keys = new Set<string>()
  for (const { tokens } of files) {
    for (let i = 0; i < tokens.length; i++) {
      if (tokens[i].kind !== 'text') continue
      let text = tokens[i].value
      while (tokens[i + 1]?.kind === 'mark' && tokens[i + 1].value === '+' && tokens[i + 2]?.kind === 'text') {
        text += tokens[i + 2].value
        i += 2
      }
      keys.add(messageKey(text))
    }
  }
  return keys
}
