import { expect, type Locator, type Page } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { signIn } from './support'

/**
 * Shared helpers for the demo learner Amira Hassan's E2E suites (`06a`–`06d`), which run sequentially against one
 * seeded backend: Unit 1 of Learn, 279 XP (Rank 3), 20 / 30 XP today, 7-day streak, 6 mistakes, plan "Travel to
 * Shanghai", and "Mandarin for Campus Life".
 */
export const AMIRA = E2E_ACCOUNTS.student
export const MY_CLASS = 'Mandarin for Campus Life'
/** XP ranks: every 100 XP is one rank (utils/xpLevel). */
const RANK_TITLES = ['Starter', 'Explorer', 'Regular', 'Committed', 'Spark Champion']
/** The bonus for reaching today's goal (RewardRules.DAILY_GOAL_BONUS_XP). */
export const GOAL_BONUS_XP = 5

export async function signInAmira(page: Page, redirect?: string): Promise<void> {
  await signIn(page, AMIRA, redirect)
  if (!redirect) await expect(page).toHaveURL(/\/student\/home$/)
}

/** The first number a pattern captures in a text on the page. */
export async function numberIn(text: string | Promise<string | null>, pattern: RegExp): Promise<number> {
  const match = pattern.exec((await text) ?? '')
  expect(match, `${pattern} in the page text`).not.toBeNull()
  return Number(match![1])
}

/** The sidebar's status card with the total XP ("Rank 3 · Regular 279 XP · 7-day streak"). */
export function sidebarStatus(page: Page): Locator {
  return page.getByRole('link', { name: /\d+ XP · / })
}

/** "Rank 4 · Committed" for a total of XP. */
export function rankName(xp: number): string {
  const rank = Math.floor(xp / 100) + 1
  return `Rank ${rank} · ${RANK_TITLES[Math.min(rank, RANK_TITLES.length) - 1]}`
}

/** The toast of a page, found by its message. */
export function toast(page: Page, message: string): Locator {
  return page.getByRole('status').filter({ hasText: message })
}

/**
 * A result that takes Amira past a rank (every 100 XP) opens "You reached Rank N · …" over it; Keep going closes it.
 * Nothing opens when the XP stays in the same rank.
 */
export async function passRankUp(page: Page, xpBefore: number, earned: number): Promise<void> {
  if (Math.floor((xpBefore + earned) / 100) === Math.floor(xpBefore / 100)) return
  const rankUp = page.getByRole('dialog', { name: `You reached ${rankName(xpBefore + earned)}` })
  await expect(rankUp).toBeVisible()
  await rankUp.getByRole('button', { name: 'Keep going' }).click()
  await expect(rankUp).toBeHidden()
}
