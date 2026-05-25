import { expect, test } from '@playwright/test'

test.describe('public smoke flow', () => {
  test('loads the landing page and auth surface', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/MandarinSpark/)

    await page.goto('/auth')
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible()
  })
})
