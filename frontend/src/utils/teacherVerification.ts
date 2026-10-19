/**
 * Admin teacher verification (A5 #2): the decision buttons stay disabled until the selected applicant's own
 * detail (documents, history) has loaded, so an admin never approves or rejects a profile they have not seen.
 */
export interface VerificationDecisionState {
  selectedTeacherId: number
  loadedDetailTeacherId: number | null
  detailLoading: boolean
  readOnly: boolean
  submitting: boolean
}

export function canDecideVerification(state: VerificationDecisionState): boolean {
  return (
    state.selectedTeacherId > 0 &&
    state.loadedDetailTeacherId === state.selectedTeacherId &&
    !state.detailLoading &&
    !state.readOnly &&
    !state.submitting
  )
}

/** The applicant whose detail must be loaded after the list changes (keeps the current one when still listed). */
export function nextSelectedTeacherId(listedIds: readonly number[], currentId: number): number {
  if (listedIds.includes(currentId)) return currentId
  return listedIds[0] ?? 0
}
