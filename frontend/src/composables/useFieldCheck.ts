import { computed, readonly, ref, watch, type Ref } from 'vue'

/**
 * One form field checked the same way on every form: when the field is left and when the form is sent, and again on
 * every change while its error shows, so the error goes the moment it is fixed. `error` stays null until a check
 * fails, so a form can disable its main button on `error` without blocking a field nobody has touched yet.
 *
 * `check` answers with the message that says how to fix the value, or null when the value is fine.
 */
export function useFieldCheck(value: Ref<string>, check: (value: string) => string | null) {
  const error = ref<string | null>(null)
  const currentIssue = computed(() => check(value.value))

  /** Checks the value now, on blur or submit; true when it is fine. */
  function validate(): boolean {
    error.value = currentIssue.value
    return error.value === null
  }

  /** Forgets the error, e.g. when the form opens again. */
  function reset(): void {
    error.value = null
  }

  // A displayed message also follows reactive dependencies of the rule, including its language.
  watch(currentIssue, (issue) => {
    if (error.value !== null) error.value = issue
  })

  return { error: readonly(error), validate, reset }
}
