/** Names and status tones of the admin console, shared by the admin pages and their drawers. */
import type { PillTone } from '@/components/common/StatusPill.vue'
import type { AiFeatureKey, AiResponseReportStatus, ContentStatus } from '@/types/api'
import type { SupportStatus } from '@/types/ops'

/** The name of an AI feature through the page's `t` (the page registers the opsLabels bundle). */
export function aiFeatureLabel(featureKey: AiFeatureKey, translate: (key: string) => string): string {
  return translate(`opsLabels.aiFeatures.${featureKey}`)
}

/*
 * Status tones from the admin's side: `strong` is work waiting for an admin, `success` a positive end, `outline` what is
 * inactive or not live yet.
 */

export function accountTone(active: boolean): PillTone {
  return active ? 'success' : 'outline'
}

export function adminSupportTone(status: SupportStatus): PillTone {
  if (status === 'OPEN') return 'strong'
  return status === 'SOLVED' ? 'success' : 'info'
}

export function adminReportTone(status: AiResponseReportStatus): PillTone {
  if (status === 'PENDING_REVIEW') return 'strong'
  return status === 'RESOLVED' ? 'success' : 'neutral'
}

/** Classes, lessons, practice activities and Learn units: live content reads as success, Draft and Archived as outline. */
export function contentStatusTone(status: ContentStatus): PillTone {
  return status === 'PUBLISHED' ? 'success' : 'outline'
}
