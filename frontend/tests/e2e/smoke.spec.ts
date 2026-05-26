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
})
