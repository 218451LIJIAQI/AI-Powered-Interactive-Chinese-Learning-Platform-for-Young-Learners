import { expect, test, type Page } from '@playwright/test'

function collectClientErrors(page: Page) {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text())
    }
  })
  page.on('pageerror', (error) => {
    errors.push(error.message)
  })
  return errors
}

async function stubApiAsSignedOut(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: false,
        message: 'Please sign in again to continue.',
      }),
    })
  })
}

test.describe('public smoke flow', () => {
  test('loads the landing page and auth surface', async ({ page }) => {
    const clientErrors = collectClientErrors(page)

    await page.goto('/')
    await expect(page).toHaveTitle(/MandarinSpark/)
    await expect(page.getByRole('heading', { name: /spark a love for mandarin/i })).toBeVisible()

    await page.goto('/catalog')
    await expect(page).toHaveURL(/\/courses$/)
    await expect(page.getByRole('heading', { name: 'Explore Courses' })).toBeVisible()

    await page.goto('/auth')
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible()
    expect(clientErrors).toEqual([])
  })

  test('redirects protected guest routes through auth safely', async ({ page }) => {
    await stubApiAsSignedOut(page)
    const clientErrors = collectClientErrors(page)

    await page.goto('/student/home')

    await expect(page).toHaveURL(/\/auth\?redirect=\/student\/home$/)
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible()
    await expect(page.getByText(/Preview learning dashboard|Preview data mode/i)).toHaveCount(0)
    expect(clientErrors).toEqual([])
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

  test('teacher and admin preview sessions are read-only and token-free', async ({ page }) => {
    const previewCases = [
      {
        button: /preview teacher/i,
        route: /\/teacher\/dashboard/,
        role: 'TEACHER',
        notice: /Preview data mode|Verification pending|read-only/i,
      },
      {
        button: /preview admin/i,
        route: /\/admin\/dashboard/,
        role: 'ADMIN',
        notice: /Preview data mode|Showing safe preview data/i,
      },
    ]

    for (const previewCase of previewCases) {
      await page.goto('/auth')
      await page.getByRole('button', { name: previewCase.button }).click()

      await expect(page).toHaveURL(previewCase.route)
      await expect(page.getByText(previewCase.notice).first()).toBeVisible()
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
          previewRole: previewCase.role,
        })
    }
  })

  test('key preview routes render on mobile and desktop without console errors', async ({ page }) => {
    await stubApiAsSignedOut(page)
    const clientErrors = collectClientErrors(page)
    const viewports = [
      { width: 390, height: 844 },
      { width: 1280, height: 900 },
    ]

    for (const viewport of viewports) {
      await page.setViewportSize(viewport)
      await page.goto('/auth')
      await page.getByRole('button', { name: /preview student/i }).click()
      await expect(page).toHaveURL(/\/student\/home/)
      await expect(page.getByText('Preview learning dashboard')).toBeVisible()

      await page.goto('/student/ai-helper')
      await expect(page.getByText('Preview AI helper')).toBeVisible()
      await page.getByLabel('Ask SparkBot a Mandarin question').fill('How do you say hello?')
      await page.getByRole('button', { name: /send question to sparkbot/i }).click()
      await expect(page.getByText(/did not send a live AI request/i)).toBeVisible()
    }

    expect(clientErrors).toEqual([])
  })
})
