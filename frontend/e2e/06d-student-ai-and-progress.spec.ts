import { AMIRA, MY_CLASS, numberIn, rankName, sidebarStatus, signInAmira, toast } from './studentSupport'
import { API_BASE_URL, apiAuthHeaders, bestEffortApi, expect, sidebar, test } from './support'

// Pure API test: uses only the request fixture outside the serial browser suite so no browser page is started.
test('the AI endpoints say the AI is unavailable instead of making up an answer', async ({ request }) => {
  const headers = await apiAuthHeaders(request, AMIRA)
  const call = async (path: string, data: object) => {
    const response = await request.post(`${API_BASE_URL}${path}`, { headers, data })
    expect(response.status(), path).toBe(200)
    return ((await response.json()) as { data: Record<string, unknown> }).data
  }
  const unavailable = 'AI tutor is currently unavailable. Please try again later.'

  const status = await request.get(`${API_BASE_URL}/ai/status`, { headers })
  expect(((await status.json()) as { data: { available: boolean } }).data.available).toBe(false)
  // The chat answers as a server-sent event stream; its final `complete` event carries the outcome.
  const stream = await request.post(`${API_BASE_URL}/ai/chat/stream`, {
    headers,
    data: { message: 'How do you say hello in Mandarin?' },
  })
  expect(stream.status()).toBe(200)
  const events = await stream.text()
  expect(events).toContain('event:complete')
  expect(events).toContain('"status":"UNAVAILABLE"')
  expect(events).toContain(unavailable)
  expect(
    await call('/ai/conversation/reply', {
      scenario: 'MEET_XIAOMEI',
      turns: [{ speaker: 'XIAOMEI', text: '你好！我是小美。' }],
      languagePreference: 'en',
      message: '你好，我叫阿米拉。',
    }),
  ).toMatchObject({ status: 'UNAVAILABLE', message: unavailable, reply: null })
  expect(await call('/ai/quiz-generator', { topic: 'Numbers', numberOfQuestions: 3 })).toMatchObject({
    status: 'UNAVAILABLE',
    questions: [],
  })
  expect(await call('/ai/story-generator', { topic: 'My family' })).toMatchObject({
    status: 'UNAVAILABLE',
    lines: [],
    comprehensionQuestions: [],
  })
})

test.describe('Notifications, AI Helper, and Progress', () => {
  test.describe.configure({ mode: 'serial' })

  test('Mark all as read happens at once, and Undo makes them unread again', async ({ page, request }) => {
    await signInAmira(page)
    const bell = page.getByRole('banner').getByRole('link', { name: /^Notifications/ })
    await expect(bell).toHaveAccessibleName(/^Notifications - \d+ unread$/)
    const unread = await numberIn(bell.getAttribute('aria-label'), /(\d+) unread/)
    await bell.click()

    await expect(page).toHaveURL(/\/student\/notifications$/)
    const filters = page.getByRole('group', { name: 'Show notifications' })
    const unreadChip = filters.getByRole('button', { name: /^Unread · \d+$/ })
    await expect(unreadChip).toHaveText(`Unread · ${unread}`)
    const unreadRows = page.getByRole('listitem').filter({ has: page.getByRole('button', { name: /^Mark as read: / }) })
    await expect(unreadRows).toHaveCount(unread)

    // No confirm step: it is done at once, and the toast can take it back.
    await page.clock.install()
    let markedIds: number[] = []
    try {
      const readAll = page.waitForResponse(
        (response) =>
          response.url().endsWith('/api/v1/notifications/read-all') && response.request().method() === 'PATCH',
      )
      await page.getByRole('button', { name: 'Mark all as read' }).click()
      const response = await readAll
      expect(response.ok(), `PATCH /api/v1/notifications/read-all returned HTTP ${response.status()}`).toBe(true)
      markedIds = ((await response.json()) as { data?: number[] }).data ?? []
      const marked = toast(page, 'All notifications marked as read.')
      await expect(marked).toBeVisible()
      const undo = marked.getByRole('button', { name: 'Undo' })
      await undo.hover()
      await expect(unreadChip).toHaveText('Unread · 0')
      await expect(unreadRows).toHaveCount(0)
      await expect(bell).toHaveAccessibleName('Notifications')
      await expect(page.getByRole('button', { name: 'Mark all as read' })).toHaveCount(0)

      await undo.focus()
      await page.mouse.move(0, 0)
      await page.clock.fastForward(4500)
      await expect(undo).toBeFocused()
      await undo.press('Enter')
      await expect(unreadChip).toHaveText(`Unread · ${unread}`)
      await expect(unreadRows).toHaveCount(unread)
      await expect(bell).toHaveAccessibleName(`Notifications - ${unread} unread`)
      markedIds = []
    } finally {
      if (markedIds.length > 0) {
        await bestEffortApi(request, AMIRA, (headers) =>
          request.patch(`${API_BASE_URL}/notifications/unread`, { headers, data: { ids: markedIds } }),
        )
      }
    }
  })

  test('every AI Helper tab says the AI helpers are unavailable and offers nothing to send', async ({ page }) => {
    await signInAmira(page)
    // S9.12: one banner on every tab says both helpers are away and what still works, with the way to Practice.
    const AWAY = 'SparkBot and Xiaomei can’t answer right now.'
    const TABS = [
      { tab: 'Talk with Xiaomei', path: 'talk', section: 'Talk with Xiaomei', action: 'Speak your reply in Chinese' },
      { tab: 'Ask SparkBot', path: 'ask', section: 'Chat with SparkBot', action: 'Send' },
      { tab: 'Quick quiz', path: 'quiz', section: 'Quick quiz', action: 'Make quiz' },
      { tab: 'Story', path: 'story', section: 'Story', action: 'Make story' },
      {
        tab: 'Pronunciation & handwriting',
        path: 'voice',
        section: 'Check your speaking or handwriting',
        action: 'Check pronunciation',
      },
    ] as const

    await sidebar(page).getByRole('link', { name: 'AI Helper' }).click()
    await expect(page.getByRole('heading', { name: 'AI Helper', level: 1 })).toBeAttached()
    const tabs = page.getByRole('navigation', { name: 'AI Helper sections' })
    await expect(tabs.getByRole('link')).toHaveText(TABS.map(({ tab }) => tab))

    const banner = page.getByTestId('ai-blocked')
    await expect(banner).toContainText(AWAY)
    const bannerLayout = () =>
      banner.evaluate(async (element) => {
        await element.ownerDocument.fonts.ready
        const { top, height, bottom } = element.getBoundingClientRect()
        const panel = element.nextElementSibling!
        return { top, height, gapBelow: panel.getBoundingClientRect().top - bottom }
      })
    const initialLayout = await bannerLayout()

    for (const { tab, path, section, action } of TABS) {
      await tabs.getByRole('link', { name: tab }).click()
      await expect(page).toHaveURL(new RegExp(`/student/ai/${path}$`))
      await expect(banner).toContainText(AWAY)
      await expect(banner).toContainText('Lessons, practice and lesson quizzes still work.')
      await expect(banner.getByRole('link', { name: 'Go to Practice' })).toBeVisible()
      const panel = page.getByRole('region', { name: section })
      await expect(panel).toBeVisible()
      const currentLayout = await bannerLayout()
      expect(Math.abs(currentLayout.top - initialLayout.top), `${tab} banner top`).toBeLessThanOrEqual(1)
      expect(Math.abs(currentLayout.height - initialLayout.height), `${tab} banner height`).toBeLessThanOrEqual(1)
      expect(Math.abs(currentLayout.gapBelow - initialLayout.gapBelow), `${tab} banner gap`).toBeLessThanOrEqual(1)
      await expect(panel.getByRole('button', { name: action, exact: true })).toBeDisabled()
    }
    await tabs.getByRole('link', { name: 'Ask SparkBot' }).click()
    await expect(page.getByRole('textbox', { name: 'Your question' })).toBeDisabled()
    await expect(page.getByRole('textbox', { name: 'Your question' })).toHaveAttribute(
      'placeholder',
      'SparkBot is unavailable right now',
    )
  })

  test('pronunciation and handwriting handle successful and unreadable practice checks in the browser', async ({
    page,
  }) => {
    try {
      await page.route('**/api/v1/ai/status', async (route) => {
        const response = await route.fetch()
        const body = await response.json()
        Object.assign(body.data, { speechCheckAvailable: true, handwritingCheckAvailable: true })
        await route.fulfill({ response, json: body })
      })
      let photosSubmitted = 0
      const submissions: Array<{
        type: string | null
        targetText: string | null
        languagePreference: string | null
        fileName: string | null
      }> = []
      const multipartField = (body: string, name: string): string | null =>
        new RegExp(`Content-Disposition:\\s*form-data;\\s*name="${name}"\\r?\\n\\r?\\n([^\\r\\n]+)`).exec(body)?.[1] ??
        null
      const multipartFilename = (body: string, name: string): string | null =>
        new RegExp(`Content-Disposition:\\s*form-data;\\s*name="${name}";\\s*filename="([^"]+)"`).exec(body)?.[1] ??
        null

      await page.route(/\/api\/v1\/ai\/practice-checks(?:$|\?)/, async (route) => {
        if (route.request().method() !== 'POST') return route.continue()
        const body = route.request().postDataBuffer()?.toString('utf8') ?? ''
        const submittedType = multipartField(body, 'type')
        const submittedTarget = multipartField(body, 'targetText')
        submissions.push({
          type: submittedType,
          targetText: submittedTarget,
          languagePreference: multipartField(body, 'languagePreference'),
          fileName: multipartFilename(body, 'file'),
        })
        const isRecording = submittedType === 'RECORDING'
        const check = {
          id: 999,
          type: isRecording ? 'RECORDING' : 'HANDWRITING',
          lessonTitle: null,
          targetText: submittedTarget ?? (isRecording ? '山' : '好'),
          checkedAt: '2026-11-18T09:00:00',
          score: 84,
          clarityScore: null,
          paceScore: null,
          headline: 'One small adjustment',
          tips: [],
          ...(isRecording
            ? {
                pronunciation: {
                  accuracy: 83,
                  fluency: 79,
                  completeness: 100,
                  issues: [{ text: '山', kind: 'TONE_MISMATCH', expectedTone: 1, detectedTone: 4 }],
                },
              }
            : { feedback: 'The 女 component is wide.', tips: ['Make 女 narrower to leave room for 子.'] }),
        }
        const data =
          !isRecording && ++photosSubmitted === 1
            ? {
                status: 'FAILED',
                message:
                  'We could not read the handwriting clearly. Retake the photo in good light with the whole character in frame.',
              }
            : { status: 'SUCCESS', check }
        await route.fulfill({ json: { success: true, data } })
      })

      await signInAmira(page, '/student/ai/voice')
      const panel = page.getByRole('region', { name: 'Check your speaking or handwriting' })
      const modes = panel.getByRole('group', { name: 'Type' })
      await expect(modes.getByRole('button')).toHaveText(['Recording', 'Handwriting'])
      await panel.getByRole('textbox', { name: 'Target text' }).fill('山')
      const input = panel.locator('input[type="file"]')
      await input.setInputFiles({ name: 'voice.webm', mimeType: 'audio/webm', buffer: Buffer.from('audio fixture') })
      await panel.getByRole('button', { name: 'Check pronunciation' }).click()

      const result = page.getByTestId('ai-check-result')
      await expect(result).toContainText('Completeness')
      await expect(result).toContainText('Aim for tone 1. This sounded like tone 4.')
      await result.getByRole('button', { name: 'Try again' }).click()
      await modes.getByRole('button', { name: 'Handwriting' }).click()
      await panel.getByRole('textbox', { name: 'Target text' }).fill('好')

      const photo = {
        name: 'handwriting.png',
        mimeType: 'image/png',
        buffer: Buffer.from(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+nm1sAAAAASUVORK5CYII=',
          'base64',
        ),
      }
      await input.setInputFiles(photo)
      await panel.getByRole('button', { name: 'Check with SparkBot' }).click()
      await expect(panel.getByRole('alert')).toContainText('Retake the photo in good light')
      await expect(result).toHaveCount(0)
      await input.setInputFiles(photo)
      await panel.getByRole('button', { name: 'Check with SparkBot' }).click()
      await expect(result).toContainText('The 女 component is wide.')
      await expect(result).toContainText('Make 女 narrower to leave room for 子.')
      expect(submissions).toEqual([
        { type: 'RECORDING', targetText: '山', languagePreference: 'en', fileName: 'voice.webm' },
        { type: 'HANDWRITING', targetText: '好', languagePreference: 'en', fileName: 'handwriting.png' },
        { type: 'HANDWRITING', targetText: '好', languagePreference: 'en', fileName: 'handwriting.png' },
      ])
    } finally {
      try {
        await page.unrouteAll({ behavior: 'ignoreErrors' })
      } catch {
        // Best-effort teardown so route cleanup never masks the primary test failure.
      }
    }
  })

  test('Progress shows her rank, streak and badges', async ({ page }) => {
    await signInAmira(page)
    const xp = await numberIn(sidebarStatus(page).innerText(), /(\d+) XP/)
    await expect(sidebarStatus(page)).toContainText(rankName(xp))
    await sidebar(page).getByRole('link', { name: 'Progress' }).click()

    await expect(page).toHaveURL(/\/student\/progress$/)
    await expect(page.getByRole('heading', { name: 'Progress', level: 1 })).toBeAttached()
    const rank = page.getByRole('region', { name: 'Your rank' })
    await expect(rank).toContainText(rankName(xp))
    await expect(rank.getByRole('progressbar', { name: 'Progress to the next rank' })).toHaveAttribute(
      'aria-valuenow',
      String(xp % 100),
    )
    await expect(rank).toContainText(`${100 - (xp % 100)} XP to ${rankName(xp + 100)}`)

    const streak = page.getByRole('region', { name: /^\d+ day streak$/ })
    await expect(streak).toContainText(/Best: \d+ days/)
    await expect(streak.getByRole('table')).toBeVisible()

    const badges = page.getByRole('region', { name: 'Badges' })
    await expect(badges.getByRole('button')).toHaveCount(8)
    await expect(badges.getByRole('button', { name: /^First Steps .*Earned$/ })).toBeVisible()
    await expect(badges.getByRole('button', { name: /^XP Starter .*Earned$/ })).toBeVisible()
    expect(xp).toBeGreaterThanOrEqual(50)
    await expect(badges.getByRole('button', { name: /^300 XP / })).toHaveAccessibleName(
      xp >= 300 ? /Earned$/ : /Not earned yet$/,
    )
  })

  test('the Skills card shows her real numbers and points her to the weakest skill', async ({ page, request }) => {
    const response = await request.get(`${API_BASE_URL}/students/progress/skills`, {
      headers: await apiAuthHeaders(request, AMIRA),
    })
    expect(response.status()).toBe(200)
    type Skills = { data: { skills: { skill: string; percent: number; weeklyChange: number }[]; weakest: string } }
    const { skills, weakest } = ((await response.json()) as Skills).data
    const SKILLS: Record<string, { label: string; tab: string }> = {
      VOCABULARY: { label: 'Vocabulary', tab: 'flashcards' },
      LISTENING: { label: 'Listening', tab: 'listening' },
      CHARACTERS: { label: 'Characters', tab: 'tracing' },
      SENTENCES: { label: 'Sentences', tab: 'drag-drop' },
    }
    expect(skills.map(({ skill }) => skill).sort()).toEqual(Object.keys(SKILLS).sort())

    await signInAmira(page, '/student/progress')
    const card = page.getByRole('region', { name: 'Skills' })
    for (const { skill, percent, weeklyChange } of skills) {
      const { label } = SKILLS[skill]!
      const row = card.getByRole('listitem').filter({ hasText: label })
      await expect(row).toContainText(`${percent}%`)
      if (percent === 0) await expect(row).toContainText('Not started')
      else if (weeklyChange > 0) await expect(row).toContainText(`+${weeklyChange} this week`)
      await expect(row.getByRole('progressbar', { name: label })).toHaveAttribute('aria-valuenow', String(percent))
    }
    const { label, tab } = SKILLS[weakest]!
    await expect(card).toContainText(`Weakest right now: ${label}.`)
    await card.getByRole('link', { name: `Practise ${label.toLowerCase()}` }).click()
    await expect(page).toHaveURL(new RegExp(`/student/practice/${tab}$`))
  })

  test('My learning plan shows her goal, this week and the path to it', async ({ page }) => {
    await signInAmira(page, '/student/progress')
    const plan = page.getByRole('region', { name: 'My learning plan' })
    await expect(plan).toContainText('Travel to Shanghai')
    await expect(plan).toContainText(/by \w{3} \d{1,2} \w{3,4} \d{4} · week 2 of 10/)
    await expect(plan).toContainText('HSK 1 → HSK 2')
    await expect(plan.getByRole('progressbar', { name: 'Plan progress' })).toHaveAttribute('aria-valuenow', /^\d+$/)
    await plan.getByRole('link', { name: 'View my plan' }).click()

    await expect(page).toHaveURL(/\/student\/progress\/plan$/)
    await expect(page.getByRole('heading', { name: 'My learning plan', level: 1 })).toBeVisible()
    await expect(page.getByText(/^Travel to Shanghai · by .+ · week \d+ of \d+$/)).toBeVisible()
    const glance = page.getByRole('region', { name: 'Your plan at a glance' })
    await expect(glance.locator('.page-eyebrow')).toHaveText(['Mandarin level', 'Plan progress', 'This week', 'Pace'])
    await expect(glance).toContainText('HSK 1 → HSK 2')
    await expect(glance).toContainText('1–2 hours a day')
    const steps = page.getByRole('region', { name: 'Your path' }).getByRole('list', { name: 'The steps of your plan' })
    await expect(steps).toContainText('Learn · Unit 1 · Pinyin and tones · 拼音')
    await expect(steps).toContainText(new RegExp(`Class ·\\s*${MY_CLASS}`))
    await expect(steps).toContainText('Learn · Unit 2 · Characters and first words · 汉字')
    await expect(steps).toContainText('Your goal · Travel to Shanghai')
    await expect(steps).not.toContainText(/live session/i)
    await expect(steps).not.toContainText(/teacher/i)
    const thisWeek = page.getByRole('region', { name: 'This week', exact: true })
    await expect(thisWeek.getByRole('list', { name: 'This week’s to-dos' }).getByRole('listitem')).not.toHaveCount(0)
  })
})
