import { expect, test } from '@playwright/test'

test.describe('public smoke flow', () => {
  test('loads the landing page and auth surface', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/MandarinSpark/)

    await page.goto('/auth')
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible()
  })

  test('redirects protected guest routes through auth safely', async ({ page }) => {
    await page.goto('/student/home')

    await expect(page).toHaveURL(/\/auth\?redirect=\/student\/home$/)
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible()
  })

  test('preview role uses session state without persisted bearer tokens', async ({ page }) => {
    await page.goto('/auth')
    await page.getByRole('button', { name: /preview student/i }).click()

    await expect(page).toHaveURL(/\/student\/home/)
    await expect
      .poll(() =>
        page.evaluate(() => ({
          localToken: window.localStorage.getItem('mandarin-spark.token'),
          sessionToken: window.sessionStorage.getItem('mandarin-spark.token'),
          previewRole: window.sessionStorage.getItem('mandarin-spark.preview-role'),
        })),
      )
      .toEqual({
        localToken: null,
        sessionToken: null,
        previewRole: 'STUDENT',
      })
  })

  test('preview student practice recommendations open the requested activity type', async ({ page }) => {
    await page.goto('/auth')
    await page.getByRole('button', { name: /preview student/i }).click()

    await expect(page).toHaveURL(/\/student\/home/)
    await page.getByRole('button', { name: /^Listening$/ }).click()

    await expect(page).toHaveURL(/\/student\/practice\?activity=listening-exercise/)
    await expect(page.getByRole('heading', { name: 'Listen and Choose' })).toBeVisible()
    await expect(page.getByText('Level 1 of 4').first()).toBeVisible()
    await expect(page.getByText('Preview activity loaded')).toBeVisible()
    await page.getByRole('button', { name: /complete & next/i }).click()
    await expect(page.getByRole('heading', { name: 'Listen for Greetings' })).toBeVisible()
    await expect(page.getByText('Level 2 of 4').first()).toBeVisible()

    await page.goto('/student/practice?activity=drag-drop-activity')
    await expect(page.getByRole('heading', { name: 'Build the Sentence' })).toBeVisible()

    await page.goto('/student/practice?activity=story')
    await expect(page.getByRole('heading', { name: 'Short Story: Saying Hello' })).toBeVisible()

    await page.goto('/student/practice?activity=writing')
    await expect(page.getByRole('heading', { name: 'Trace 好' })).toBeVisible()

    await page.goto('/student/quiz')
    await expect(page.getByText('Question 1 of 12')).toBeVisible()
  })
})
