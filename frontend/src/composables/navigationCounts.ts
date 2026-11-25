import type { InjectionKey, Ref } from 'vue'

import type { NavCounts, NotificationUnreadCountResponse } from '@/types/api'

/** The signed-in layout shares the latest successful counts with the admin AI area. */
export const navigationCountsKey: InjectionKey<{
  readonly latest: Readonly<Ref<NavCounts | null>>
}> = Symbol('navigationCounts')

/** The bell and the notifications page use the same account-wide unread count. */
export const unreadNotificationsKey: InjectionKey<{
  readonly data: Ref<NotificationUnreadCountResponse | null>
  readonly reload: () => Promise<void>
}> = Symbol('unreadNotifications')
