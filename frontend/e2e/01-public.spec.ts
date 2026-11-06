import { expect, test } from '@playwright/test'

import { switchLanguage } from './support'

// The demo profile publishes 8 classes; Li Wei's "Chinese Culture and Festivals" is in review and "Study Group
// Mandarin" is a draft, so visitors see neither (05-admin approves the first one later in the run).
const PUBLISHED_CLASSES = [
  'Mandarin for Campus Life',
  'Chinese Through Film',
  'Everyday Conversations',
  'Shopping and Prices',
  'Study Abroad Survival',
  'Travel Mandarin',
  'Basic Work Mandarin',
  'Serving Customers',
]

test('visitors open the class catalogue from the landing page', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('main').getByRole('link', { name: 'Explore classes' }).click()

  await expect(page).toHaveURL(/\/classes$/)
  await expect(page.getByRole('heading', { name: 'Explore classes', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: '8 classes matched' })).toBeVisible()
  for (const title of PUBLISHED_CLASSES) {
    await expect(page.getByRole('link', { name: title, exact: true })).toBeVisible()
  }
  await expect(page.getByRole('link', { name: 'Chinese Culture and Festivals' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Study Group Mandarin' })).toHaveCount(0)
})

test('Theme chips filter the class catalogue', async ({ page }) => {
  await page.goto('/classes')
  const themes = page.getByRole('group', { name: 'Filter classes by theme' })
  await expect(themes.getByRole('button')).toHaveText([
    'All · 8',
    'Travel · 2',
    'Work and career · 2',
    'Study or exchange · 2',
    'Daily life · 1',
    'Personal interest · 1',
  ])
  await expect(themes.getByRole('button', { name: 'All · 8' })).toHaveAttribute('aria-pressed', 'true')

  await themes.getByRole('button', { name: 'Travel · 2' }).click()
  await expect(themes.getByRole('button', { name: 'Travel · 2' })).toHaveAttribute('aria-pressed', 'true')
  await expect(themes.getByRole('button', { name: 'All · 8' })).toHaveAttribute('aria-pressed', 'false')
  await expect(page.getByRole('heading', { name: '2 classes matched' })).toBeVisible()
  for (const title of ['Travel Mandarin', 'Shopping and Prices']) {
    await expect(page.getByRole('link', { name: title, exact: true })).toBeVisible()
  }
  await expect(page.getByRole('link', { name: 'Mandarin for Campus Life' })).toHaveCount(0)

  // A chip with one class says so in the singular.
  await themes.getByRole('button', { name: 'Personal interest · 1' }).click()
  await expect(page.getByRole('heading', { name: '1 class matched' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Chinese Through Film', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Travel Mandarin' })).toHaveCount(0)

  await themes.getByRole('button', { name: 'All · 8' }).click()
  await expect(page.getByRole('heading', { name: '8 classes matched' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Travel Mandarin', exact: true })).toBeVisible()
})

test('a class page shows the teacher, units and lessons, and asks visitors to sign in', async ({ page }) => {
  await page.goto('/classes')
  await page.getByRole('link', { name: 'Mandarin for Campus Life' }).click()

  await expect(page).toHaveURL(/\/classes\/\d+$/)
  const main = page.getByRole('main')
  await expect(page.getByRole('heading', { name: 'Mandarin for Campus Life', level: 1 })).toBeVisible()
  await expect(main).toContainText('Study or exchange · 2 units · 5 lessons · Best after Unit 3')
  await expect(main).toContainText('Li Wei')
  await expect(main).toContainText('Verified')

  const inside = page.getByRole('region', { name: 'What’s inside' })
  await expect(inside.getByRole('heading', { name: 'Unit 1: First Conversations' })).toBeVisible()
  await expect(inside.getByRole('heading', { name: 'Unit 2: Campus Life' })).toBeVisible()
  await expect(inside.getByRole('listitem')).toHaveText([
    'Meeting Classmates',
    'Room Numbers',
    'University Life',
    'Ordering Food at the Canteen',
    'Finding Places',
  ])

  await expect(main.getByRole('link', { name: 'Create account to start' })).toBeVisible()
  await main.getByRole('link', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/auth\?tab=signin&redirect=\/student\/classes\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Welcome back', level: 1 })).toBeVisible()
})

test('the Help page loads from the header', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Help' }).click()

  await expect(page).toHaveURL(/\/help$/)
  await expect(page.getByRole('heading', { name: 'Help', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Getting started' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Send us a message' })).toBeVisible()
})

test('the landing page follows the language switch and switches back', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Spark your progress in Mandarin.', level: 1 })).toBeVisible()

  await switchLanguage(page, '中文')
  await expect(page.getByRole('heading', { name: '点亮你的中文进步。', level: 1 })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')

  await switchLanguage(page, 'BM')
  await expect(page.getByRole('heading', { name: 'Nyalakan kemajuan Mandarin anda.', level: 1 })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'ms')

  await switchLanguage(page, 'EN')
  await expect(page.getByRole('heading', { name: 'Spark your progress in Mandarin.', level: 1 })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
})
