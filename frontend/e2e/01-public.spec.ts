import { expect, test } from '@playwright/test'

import { switchLanguage } from './support'

// The demo profile publishes 8 classes; Li Wei's "Chinese Culture and Festivals" is in review and "Campus Mandarin"
// is a draft, so visitors see neither (05-admin approves the first one later in the run).
const PUBLISHED_CLASSES = [
  'Mandarin for Beginners',
  'HSK 1 Prep',
  'Everyday Conversations',
  'Pinyin: Read and Write',
  'Chinese Characters 101',
  'Travel Mandarin',
  'Mandarin at Work',
  'Numbers, Money and Time',
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
  await expect(page.getByRole('link', { name: 'Campus Mandarin' })).toHaveCount(0)
})

test('search and filters narrow the class catalogue', async ({ page }) => {
  await page.goto('/classes')
  const search = page.getByRole('searchbox', { name: 'Search classes' })

  await search.fill('Travel')
  await expect(page.getByRole('heading', { name: '1 class matched' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Travel Mandarin' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Mandarin for Beginners' })).toHaveCount(0)

  await search.fill('')
  await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('Beginner 2')
  await expect(page.getByRole('heading', { name: '3 classes matched' })).toBeVisible()
  for (const title of ['Everyday Conversations', 'Travel Mandarin', 'Mandarin at Work']) {
    await expect(page.getByRole('link', { name: title })).toBeVisible()
  }
  await expect(page.getByRole('link', { name: 'HSK 1 Prep' })).toHaveCount(0)
})

test('a class page shows the teacher, units and lessons, and asks visitors to sign in', async ({ page }) => {
  await page.goto('/classes')
  await page.getByRole('link', { name: 'Mandarin for Beginners' }).click()

  await expect(page).toHaveURL(/\/classes\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Mandarin for Beginners', level: 1 })).toBeVisible()
  const aside = page.getByRole('complementary')
  await expect(aside.getByRole('heading', { name: 'About the teacher' })).toBeVisible()
  await expect(aside).toContainText('Li Wei')
  await expect(aside).toContainText('Verified teacher')

  await expect(page.getByRole('heading', { name: 'Units and lessons' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'First Conversations' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Campus Life' })).toBeVisible()
  // Each lesson row says what it holds ("3 activities · 1 quiz"); live sessions below reuse some lesson titles.
  const lessons = page.getByRole('listitem').filter({ hasText: /activities · \d+ quiz/ })
  await expect(lessons).toHaveText([
    /^Greetings/,
    /^Numbers 1–10/,
    /^University Life/,
    /^Ordering Food at the Canteen/,
    /^Finding Places/,
  ])

  await expect(aside.getByRole('heading', { name: 'Start this class' })).toBeVisible()
  await expect(aside.getByRole('link', { name: 'Create account to start' })).toBeVisible()
  await aside.getByRole('link', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/auth\?tab=signin&redirect=\/student\/classes\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
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

  await switchLanguage(page, '简体中文')
  await expect(page.getByRole('heading', { name: '点亮你的中文进步。', level: 1 })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')

  await switchLanguage(page, 'BM')
  await expect(page.getByRole('heading', { name: 'Nyalakan kemajuan Mandarin anda.', level: 1 })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'ms')

  await switchLanguage(page, 'EN')
  await expect(page.getByRole('heading', { name: 'Spark your progress in Mandarin.', level: 1 })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
})
