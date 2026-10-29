import type { BadgeKey } from '@/types/learn'

/** How each badge looks and reads: its icon, its name and its earning rule (full keys of the `learn` bundle). */
interface BadgeMeta {
  readonly icon: string
  readonly nameKey: string
  readonly ruleKey: string
}

export const BADGES: Readonly<Record<BadgeKey, BadgeMeta>> = {
  FIRST_STEPS: { icon: 'flag', nameKey: 'learn.badgeFirstSteps', ruleKey: 'learn.ruleFirstSteps' },
  STREAK_EXPLORER: { icon: 'streak', nameKey: 'learn.badgeStreakExplorer', ruleKey: 'learn.ruleStreakExplorer' },
  XP_STARTER: { icon: 'flash', nameKey: 'learn.badgeXpStarter', ruleKey: 'learn.ruleXpStarter' },
  QUIZ_STAR: { icon: 'star', nameKey: 'learn.badgeQuizStar', ruleKey: 'learn.ruleQuizStar' },
  LESSON_FINISHER: { icon: 'checklist', nameKey: 'learn.badgeLessonFinisher', ruleKey: 'learn.ruleLessonFinisher' },
  TEN_DAY_STREAK: { icon: 'calendarCheck', nameKey: 'learn.badgeTenDayStreak', ruleKey: 'learn.ruleTenDayStreak' },
  UNIT_CHAMPION: { icon: 'crown', nameKey: 'learn.badgeUnitChampion', ruleKey: 'learn.ruleUnitChampion' },
  XP_300: { icon: 'trophy', nameKey: 'learn.badgeXp300', ruleKey: 'learn.ruleXp300' },
}
