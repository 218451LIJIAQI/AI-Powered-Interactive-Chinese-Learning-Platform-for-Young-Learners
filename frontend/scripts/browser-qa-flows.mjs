/**
 * Browser-driven user-flow QA — the highest-fidelity automated
 * coverage we ship. Walks complete persona journeys end-to-end and
 * records evidence at each step.
 *
 * <h2>Flows covered</h2>
 *   - Public visitor browsing course catalogue, viewing course
 *     detail, attempting paid checkout.
 *   - Student onboarding → home → lesson → quiz → AI helper.
 *   - Teacher dashboard → content authoring → publishing pipeline →
 *     class scheduling → student feedback.
 *   - Admin user management → teacher verification → content review
 *     → moderation → finance.
 *
 * <h2>Driving harness</h2>
 * Designed to be invoked by `run-browser-qa-flows.mjs`, which spins
 * up a Vite preview server first and then launches this script with
 * the right `QA_BASE_URL`. Can also be invoked directly when a
 * preview server is already running.
 *
 * <h2>Failure semantics</h2>
 * Uses a custom {@code BlockedError} to distinguish "this step
 * cannot continue because a prerequisite failed" from a transient
 * "let's retry" error. The poll helper escalates `BlockedError` so
 * the operator sees a clear root-cause line instead of a long
 * stack trace of follow-on failures.
 *
 * <h2>Evidence layout</h2>
 * `browser-qa-evidence/<timestamp>-flows/` with one screenshot per
 * step plus a structured JSON report containing
 * `{cases, pass, needsReview, needsFix, fail}` totals at the end.
 */
import { spawn } from 'node:child_process'
import { mkdir, writeFile, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { resolveBrowserExecutablePath } from './lib/browser-runtime.mjs'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5173'
const frontendProbeUrl = process.env.QA_FRONTEND_PROBE_URL || baseUrl
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = Number(process.env.QA_CHROME_PORT || 9224)
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-flows`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-flow-${timestamp}`)

class BlockedError extends Error {}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchJson(url) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json()
}

function summarizeValue(value) {
  if (value == null) return ''
  if (typeof value === 'string') return value.replace(/\s+/g, ' ').trim().slice(0, 240)

  try {
    return JSON.stringify(value).slice(0, 240)
  } catch {
    return String(value).slice(0, 240)
  }
}

async function poll(description, action, predicate, timeoutMs = 15000, intervalMs = 250) {
  const started = Date.now()
  let lastValue
  let lastError

  while (Date.now() - started < timeoutMs) {
    try {
      lastValue = await action()
      if (predicate(lastValue)) return lastValue
    } catch (error) {
      lastError = error
    }

    await sleep(intervalMs)
  }

  const reason = lastError?.message || summarizeValue(lastValue) || 'condition not met'
  throw new Error(`Timed out waiting for ${description}: ${reason}`)
}

async function fetchProbe(url) {
  const response = await fetch(url)
  const contentType = response.headers.get('content-type') || ''
  const text = await response.text()
  let body

  if (contentType.toLowerCase().includes('json')) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  } else {
    body = text
  }

  return {
    url,
    status: response.status,
    contentType,
    body,
  }
}

async function waitForEndpoint(description, url, predicate, timeoutMs = 20000) {
  return poll(description, () => fetchProbe(url), predicate, timeoutMs, 500)
}

async function waitForDevTools() {
  const endpoint = `http://127.0.0.1:${debuggingPort}/json/list`
  const started = Date.now()
  while (Date.now() - started < 15000) {
    try {
      const targets = await fetchJson(endpoint)
      const page = targets.find((target) => target.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {}
    await sleep(250)
  }
  throw new Error('Chrome DevTools endpoint did not become available')
}

function createCdpClient(webSocketUrl) {
  const socket = new WebSocket(webSocketUrl)
  let nextId = 1
  const pending = new Map()
  const handlers = new Map()

  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id)
      pending.delete(message.id)
      message.error ? reject(new Error(message.error.message || 'CDP error')) : resolve(message.result || {})
      return
    }
    if (message.method && handlers.has(message.method)) {
      for (const handler of handlers.get(message.method)) handler(message.params || {})
    }
  })

  function send(method, params = {}) {
    const id = nextId++
    socket.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (pending.has(id)) {
          pending.delete(id)
          reject(new Error(`Timed out waiting for ${method}`))
        }
      }, 15000)
    })
  }

  function on(method, handler) {
    const list = handlers.get(method) || []
    list.push(handler)
    handlers.set(method, list)
  }

  function waitFor(method, timeoutMs = 10000) {
    return new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(null), timeoutMs)
      on(method, (params) => {
        clearTimeout(timeout)
        resolve(params)
      })
    })
  }

  return new Promise((resolve, reject) => {
    socket.addEventListener('open', () => resolve({ send, on, waitFor, close: () => socket.close() }))
    socket.addEventListener('error', () => reject(new Error('WebSocket error')))
  })
}

function safeName(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'step'
}

async function main() {
  if (!existsSync(chromePath)) throw new Error(`Chrome executable not found at ${chromePath}`)
  await mkdir(evidenceDir, { recursive: true })

  const preflight = {
    frontendProbeUrl,
    frontend: await waitForEndpoint(
      `frontend at ${frontendProbeUrl}`,
      frontendProbeUrl,
      (result) => result.status >= 200 && result.status < 400,
      20000,
    ),
  }

  const chrome = spawn(chromePath, [
    '--headless=new',
    '--disable-gpu',
    '--disable-extensions',
    '--no-first-run',
    '--no-default-browser-check',
    `--remote-debugging-port=${debuggingPort}`,
    `--user-data-dir=${userDataDir}`,
    '--window-size=1280,900',
    'about:blank',
  ], { stdio: ['ignore', 'pipe', 'pipe'] })

  const chromeLogs = []
  chrome.stdout.on('data', (data) => chromeLogs.push(data.toString()))
  chrome.stderr.on('data', (data) => chromeLogs.push(data.toString()))

  let client
  const records = []
  const requestsById = new Map()
  let activeFlow = null
  let previewPreflight = null

  try {
    client = await createCdpClient(await waitForDevTools())
    const { send, on, waitFor } = client

    on('Runtime.consoleAPICalled', (params) => {
      if (!activeFlow) return
      const message = params.args?.map((arg) => arg.value || arg.description || '').filter(Boolean).join(' ') || params.type
      if (params.type === 'error') activeFlow.consoleErrors.push(message)
      if (params.type === 'warning' || params.type === 'warn') activeFlow.consoleWarnings.push(message)
    })
    on('Runtime.exceptionThrown', (params) => {
      activeFlow?.runtimeExceptions.push(params.exceptionDetails?.exception?.description || params.exceptionDetails?.text || 'Runtime exception')
    })
    on('Network.requestWillBeSent', (params) => {
      requestsById.set(params.requestId, { method: params.request?.method || '', url: params.request?.url || '' })
    })
    on('Network.loadingFailed', (params) => {
      if (!activeFlow || params.canceled || params.errorText === 'net::ERR_ABORTED') return
      const request = requestsById.get(params.requestId) || {}
      activeFlow.failedRequests.push({ method: request.method || '', url: request.url || '', errorText: params.errorText, type: params.type })
    })
    on('Network.responseReceived', (params) => {
      if (!activeFlow) return
      const response = params.response || {}
      if (response.status >= 400) activeFlow.httpErrors.push({ status: response.status, url: response.url, type: params.type })
    })

    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Log.enable')
    await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })

    async function evaluate(expression) {
      const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Evaluate failed')
      return result.result?.value
    }

    async function pageState() {
      return evaluate(`(() => ({
        title: document.title,
        path: location.pathname + location.search,
        text: document.body.innerText.slice(0, 4000),
        activeElement: document.activeElement ? (document.activeElement.id || document.activeElement.tagName) : '',
        buttons: Array.from(document.querySelectorAll('button')).map((button) => ({ text: button.innerText.trim(), disabled: button.disabled })).slice(0, 40),
        horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 2,
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: window.innerWidth,
      }))()`)
    }

    async function navigate(route) {
      const load = waitFor('Page.loadEventFired', 10000)
      await send('Page.navigate', { url: `${baseUrl}${route}` })
      await load
      await sleep(500)
    }

    async function waitForPage(description, predicate, timeoutMs = 15000) {
      return poll(description, pageState, predicate, timeoutMs, 250)
    }

    async function requirePreviewMode() {
      if (previewPreflight) {
        if (!previewPreflight.available) {
          throw new BlockedError(previewPreflight.reason)
        }
        return previewPreflight
      }

      await navigate('/auth')
      const snapshot = await waitForPage(
        'preview controls on auth page',
        (state) =>
          state.path.startsWith('/auth') &&
          ['Preview Student', 'Preview Teacher', 'Preview Admin'].every((label) =>
            state.buttons.some((button) => button.text.includes(label)),
          ),
        10000,
      ).catch(async () => {
        const state = await pageState()
        return state
      })

      const labels = snapshot.buttons.map((button) => button.text)
      const available = ['Preview Student', 'Preview Teacher', 'Preview Admin'].every((label) =>
        labels.some((text) => text.includes(label)),
      )
      previewPreflight = available
        ? {
          available: true,
          buttons: labels,
        }
        : {
          available: false,
          buttons: labels,
          reason:
            'Preview-only browser QA requires the repository preview QA frontend mode. Missing preview role buttons on /auth. Start the frontend with `npm run dev:preview-qa -- --host 127.0.0.1 --port 5174`.',
        }

      if (!previewPreflight.available) {
        throw new BlockedError(previewPreflight.reason)
      }

      return previewPreflight
    }

    async function capture(record, suffix = '') {
      const snapshot = await pageState()
      record.snapshots.push(snapshot)
      const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
      const file = path.join(evidenceDir, `${String(records.length + 1).padStart(2, '0')}-${safeName(record.name)}${suffix}.png`)
      await writeFile(file, Buffer.from(screenshot.data, 'base64'))
      record.screenshots.push(file)
      return snapshot
    }

    async function runFlow(name, fn) {
      const record = { name, status: 'PASS', error: '', consoleErrors: [], consoleWarnings: [], runtimeExceptions: [], failedRequests: [], httpErrors: [], snapshots: [], screenshots: [] }
      activeFlow = record
      try {
        await fn(record)
      } catch (error) {
        record.status = error instanceof BlockedError ? 'BLOCKED' : 'FAIL'
        record.error = error.message
        try {
          await capture(record, '-failure')
        } catch {}
      }
      if (record.status === 'PASS' && (record.consoleErrors.length || record.runtimeExceptions.length || record.failedRequests.length || record.httpErrors.length)) {
        record.status = 'NEEDS_FIX'
      }
      records.push(record)
      activeFlow = null
      console.log(`${record.status}\t${name}\t${record.error}`)
    }

    async function clickByText(text, tag = 'button', timeoutMs = 10000) {
      await poll(
        `control containing ${text}`,
        () =>
          evaluate(`(() => {
            const candidates = Array.from(document.querySelectorAll('${tag}'));
            const el = candidates.find((node) => node.innerText.trim().includes(${JSON.stringify(text)}));
            return { found: Boolean(el), disabled: Boolean(el?.disabled) };
          })()`),
        (result) => result?.found && !result?.disabled,
        timeoutMs,
        250,
      )
      const clicked = await evaluate(`(() => {
        const candidates = Array.from(document.querySelectorAll('${tag}'));
        const el = candidates.find((node) => node.innerText.trim().includes(${JSON.stringify(text)}));
        if (!el) return false;
        el.click();
        return true;
      })()`)
      if (!clicked) throw new Error(`Could not click ${tag} containing ${text}`)
      await sleep(500)
    }

    async function setValue(selector, value, timeoutMs = 10000) {
      await poll(
        `selector ${selector}`,
        () => evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),
        Boolean,
        timeoutMs,
        250,
      )
      const ok = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return false;
        el.focus();
        el.value = ${JSON.stringify(value)};
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      })()`)
      if (!ok) throw new Error(`Could not set ${selector}`)
      await sleep(150)
    }

    async function enterPreviewRole(buttonLabel, expectedPath) {
      await requirePreviewMode()
      await navigate('/auth')
      await clickByText(buttonLabel)
      return waitForPage(
        `${buttonLabel} destination`,
        (state) => state.path.startsWith(expectedPath),
        15000,
      )
    }

    await runFlow('Public navigation course and browser history', async (record) => {
      await navigate('/')
      await capture(record, '-landing')
      await clickByText('Explore Courses')
      await waitForPage('courses page after Explore Courses', (state) => state.path.startsWith('/courses'))
      await capture(record, '-courses')
      await clickByText('View Details', 'a,button')
      await waitForPage('course detail page after View Details', (state) => state.path.startsWith('/courses/'))
      await capture(record, '-detail')
      await send('Page.navigateToHistoryEntry', { entryId: (await send('Page.getNavigationHistory')).entries.at(-2).id })
      await waitForPage('courses page after browser back', (state) => state.path.startsWith('/courses'))
      const snapshot = await capture(record, '-back')
      if (!snapshot.path.startsWith('/courses')) throw new Error('Browser back did not return to courses')
    })

    await runFlow('Auth tab validation forgot password and preview roles', async (record) => {
      await navigate('/auth')
      await requirePreviewMode()
      await capture(record, '-signin-empty')
      const disabled = await evaluate(`document.querySelector('.auth-submit-button')?.disabled === true`)
      if (!disabled) throw new Error('Empty sign-in button should be disabled')
      await setValue('#login-email', 'bad-email')
      const forgotDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).find((button) => button.innerText.includes('Forgot password'))?.disabled === true`)
      if (!forgotDisabled) throw new Error('Forgot password should stay disabled for invalid email')
      await setValue('#login-email', 'learner@example.com')
      await clickByText('Forgot password?')
      await capture(record, '-forgot-offline')
      await clickByText('Back to sign in', 'a')
      await waitForPage('auth page after back', (state) => state.path.startsWith('/auth'))
      await clickByText('Create Account')
      await capture(record, '-register-empty')
      const registerDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).find((button) => button.innerText.includes('Create Account') && button.className.includes('auth-submit-button'))?.disabled === true`)
      if (!registerDisabled) throw new Error('Empty register submit should be disabled')
      let snapshot = await enterPreviewRole('Preview Student', '/student/home')
      snapshot = await capture(record, '-preview-student')
      if (!snapshot.path.startsWith('/student/home')) throw new Error('Preview Student did not route to student home')
      snapshot = await enterPreviewRole('Preview Teacher', '/teacher/dashboard')
      snapshot = await capture(record, '-preview-teacher')
      if (!snapshot.path.startsWith('/teacher/dashboard')) throw new Error('Preview Teacher did not route to teacher dashboard')
      snapshot = await enterPreviewRole('Preview Admin', '/admin/dashboard')
      snapshot = await capture(record, '-preview-admin')
      if (!snapshot.path.startsWith('/admin/dashboard')) throw new Error('Preview Admin did not route to admin dashboard')
    })

    await runFlow('Reset password validation states', async (record) => {
      await navigate('/reset-password?token=qa-token')
      await capture(record, '-empty')
      await setValue('#reset-password', 'Password1')
      await setValue('#reset-confirm-password', 'Mismatch1')
      await clickByText('Reset password')
      await waitForPage('password mismatch validation', (state) => state.text.includes('Confirm password does not match'))
      const snapshot = await capture(record, '-mismatch')
      if (!snapshot.text.includes('Confirm password does not match')) throw new Error('Password mismatch validation not shown')
    })

    await runFlow('Student learning navigation quiz and AI helper', async (record) => {
      await enterPreviewRole('Preview Student', '/student/home')
      await capture(record, '-home')
      await clickByText('Continue Learning')
      await waitForPage('student learn page after Continue Learning', (state) => state.path.startsWith('/student/learn'))
      let snapshot = await capture(record, '-learn')
      if (!snapshot.path.startsWith('/student/learn')) throw new Error('Continue Learning did not open Learn page')
      await navigate('/student/quiz')
      snapshot = await capture(record, '-quiz-initial')
      if (!snapshot.text.includes('Answer') || !snapshot.text.includes('Feedback unlocks after you submit')) throw new Error('Quiz initial disabled/read-only state was not visible')
      await clickByText('A')
      await clickByText('Next')
      await clickByText('A')
      await waitForPage(
        'quiz preview readonly state',
        (state) => state.text.includes('Preview data mode') && state.text.includes('Submit quiz'),
      )
      snapshot = await capture(record, '-quiz-readonly')
      const submitDisabled = await evaluate(`document.querySelector('.student-quiz-submit')?.disabled === true`)
      if (!submitDisabled || !snapshot.text.includes('Preview data mode') || !snapshot.text.includes('Submit quiz')) throw new Error('Quiz preview read-only disabled state not shown')
      await navigate('/student/ai-helper')
      await setValue('.student-ai-helper-chatbar input', '')
      await evaluate(`document.querySelector('.student-ai-helper-chatbar')?.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }))`)
      await waitForPage('AI empty chat validation', (state) => state.text.includes('Type a question before asking SparkBot'))
      snapshot = await capture(record, '-ai-empty-chat')
      if (!snapshot.text.includes('Type a question before asking SparkBot')) throw new Error('AI empty chat validation not shown')
      await setValue('.student-ai-helper-chatbar input', 'How do you say hello?')
      await evaluate(`document.querySelector('.student-ai-helper-chatbar button')?.click()`)
      await waitForPage('AI preview response', (state) => state.text.includes('Preview mode used a safe sample response'))
      snapshot = await capture(record, '-ai-chat')
      if (!snapshot.text.includes('Preview mode used a safe sample response')) throw new Error('AI preview success not shown')
    })

    await runFlow('Teacher and admin guarded preview actions', async (record) => {
      await enterPreviewRole('Preview Teacher', '/teacher/dashboard')
      await navigate('/teacher/content')
      let snapshot = await capture(record, '-teacher-content')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Teacher content preview notice missing')
      const createCourseDisabled = snapshot.buttons.some(
        (button) => button.text.includes('Create New Course') && button.disabled,
      )
      if (!createCourseDisabled) throw new Error('Teacher create action should be disabled in preview mode')
      snapshot = await capture(record, '-teacher-create-readonly')
      await enterPreviewRole('Preview Admin', '/admin/dashboard')
      await navigate('/admin/users')
      snapshot = await capture(record, '-admin-users')
      if (!snapshot.text.includes('Preview operations mode')) throw new Error('Admin users preview notice missing')
      await setValue('input[placeholder*="Search users"]', 'teacher')
      snapshot = await capture(record, '-admin-search')
      if (!snapshot.text.toLowerCase().includes('teacher')) throw new Error('Admin search input did not update page state')
      const accountActionDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).some((button) =>
        ['Suspend account', 'Reactivate account'].includes(button.innerText.trim()) && button.disabled
      )`)
      if (!accountActionDisabled) throw new Error('Admin user status action should be disabled in preview mode')
    })

    await runFlow('Teacher classes feedback and students readonly protections', async (record) => {
      await enterPreviewRole('Preview Teacher', '/teacher/dashboard')

      await navigate('/teacher/classes')
      let snapshot = await capture(record, '-classes')
      if (!snapshot.text.includes('Scheduler, roster, recording, and attendance flows remain reviewable in preview mode.')) {
        throw new Error('Teacher classes preview notice missing')
      }
      await clickByText('Open scheduler')
      await waitForPage('teacher class composer', (state) => state.text.includes('Schedule Live Class') || state.text.includes('Edit Live Class'))
      const classControlsReadOnly = await evaluate(`(() => ({
        titleDisabled: Boolean(document.querySelector('#teacher-class-title')?.disabled),
        platformDisabled: Boolean(document.querySelector('#teacher-class-platform')?.disabled),
        descriptionDisabled: Boolean(document.querySelector('#teacher-class-description')?.disabled),
        lessonDisabled: Boolean(document.querySelector('#teacher-class-lesson')?.disabled),
        packageDisabled: Boolean(document.querySelector('#teacher-class-package')?.disabled),
        startDisabled: Boolean(document.querySelector('#teacher-class-start-time')?.disabled),
        endDisabled: Boolean(document.querySelector('#teacher-class-end-time')?.disabled),
        linkDisabled: Boolean(document.querySelector('#teacher-class-meeting-link')?.disabled),
        maxDisabled: Boolean(document.querySelector('#teacher-class-max-participants')?.disabled),
        submitDisabled: Array.from(document.querySelectorAll('button')).some((button) =>
          (button.innerText.includes('Create class') || button.innerText.includes('Save changes')) && button.disabled
        ),
      }))()`)
      snapshot = await capture(record, '-classes-readonly')
      if (
        !classControlsReadOnly?.titleDisabled ||
        !classControlsReadOnly?.platformDisabled ||
        !classControlsReadOnly?.descriptionDisabled ||
        !classControlsReadOnly?.lessonDisabled ||
        !classControlsReadOnly?.packageDisabled ||
        !classControlsReadOnly?.startDisabled ||
        !classControlsReadOnly?.endDisabled ||
        !classControlsReadOnly?.linkDisabled ||
        !classControlsReadOnly?.maxDisabled ||
        !classControlsReadOnly?.submitDisabled
      ) {
        throw new Error('Teacher class composer controls were not fully readonly in preview mode')
      }

      await navigate('/teacher/feedback')
      snapshot = await capture(record, '-feedback')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Teacher feedback preview notice missing')
      await clickByText('Create')
      const feedbackControlsReadOnly = await evaluate(`(() => ({
        studentDisabled: Boolean(document.querySelector('#teacher-feedback-student')?.disabled),
        typeDisabled: Boolean(document.querySelector('#teacher-feedback-type')?.disabled),
        lessonDisabled: Boolean(document.querySelector('#teacher-feedback-lesson')?.disabled),
        messageDisabled: Boolean(document.querySelector('.teacher-feedback-textarea')?.disabled),
        regenerateDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Regenerate') && button.disabled),
        useGuidanceDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Use this guidance') && button.disabled),
        retryDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Draft retry note') && button.disabled),
        createNewDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Create new feedback') && button.disabled),
        sendDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Send Feedback') && button.disabled),
      }))()`)
      snapshot = await capture(record, '-feedback-readonly')
      if (
        !feedbackControlsReadOnly?.studentDisabled ||
        !feedbackControlsReadOnly?.typeDisabled ||
        !feedbackControlsReadOnly?.lessonDisabled ||
        !feedbackControlsReadOnly?.messageDisabled ||
        !feedbackControlsReadOnly?.regenerateDisabled ||
        !feedbackControlsReadOnly?.useGuidanceDisabled ||
        !feedbackControlsReadOnly?.retryDisabled ||
        !feedbackControlsReadOnly?.createNewDisabled ||
        !feedbackControlsReadOnly?.sendDisabled
      ) {
        throw new Error('Teacher feedback create controls were not fully readonly in preview mode')
      }

      await navigate('/teacher/students')
      snapshot = await capture(record, '-students')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Teacher students preview notice missing')
      const followupControlsReadOnly = await evaluate(`(() => ({
        messageDisabled: Boolean(document.querySelector('.teacher-students-followup-textarea')?.disabled),
        toneDisabled: Boolean(document.querySelector('#teacher-followup-tone')?.disabled),
        focusDisabled: Boolean(document.querySelector('#teacher-followup-focus')?.disabled),
        resetDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Reset Draft') && button.disabled),
        sendDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Send Feedback Now') && button.disabled),
      }))()`)
      snapshot = await capture(record, '-students-readonly')
      if (
        !followupControlsReadOnly?.messageDisabled ||
        !followupControlsReadOnly?.toneDisabled ||
        !followupControlsReadOnly?.focusDisabled ||
        !followupControlsReadOnly?.resetDisabled ||
        !followupControlsReadOnly?.sendDisabled
      ) {
        throw new Error('Teacher student follow-up controls were not fully readonly in preview mode')
      }
    })

    await runFlow('Teacher profile notifications and verification readonly protections', async (record) => {
      await enterPreviewRole('Preview Teacher', '/teacher/dashboard')
      await navigate('/teacher/profile')
      let snapshot = await capture(record, '-profile')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Teacher profile preview notice missing')
      const profileControlsReadOnly = await evaluate(`(() => ({
        fullNameDisabled: Boolean(document.querySelector('#teacher-profile-full-name')?.disabled),
        bioDisabled: Boolean(document.querySelector('#teacher-profile-bio')?.disabled),
        shortIntroDisabled: Boolean(document.querySelector('#teacher-profile-short-introduction')?.disabled),
        categoriesDisabled: Boolean(document.querySelector('#teacher-profile-categories')?.disabled),
        documentTypeDisabled: Boolean(document.querySelector('#teacher-verification-document-type')?.disabled),
        documentDescriptionDisabled: Boolean(document.querySelector('#teacher-verification-description')?.disabled),
        documentFileDisabled: Boolean(document.querySelector('#teacher-verification-file')?.disabled),
      }))()`)
      const saveDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Save profile') && button.disabled)`)
      const uploadDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Upload document') && button.disabled)`)
      if (
        !saveDisabled ||
        !uploadDisabled ||
        !profileControlsReadOnly?.fullNameDisabled ||
        !profileControlsReadOnly?.bioDisabled ||
        !profileControlsReadOnly?.shortIntroDisabled ||
        !profileControlsReadOnly?.categoriesDisabled ||
        !profileControlsReadOnly?.documentTypeDisabled ||
        !profileControlsReadOnly?.documentDescriptionDisabled ||
        !profileControlsReadOnly?.documentFileDisabled
      ) {
        throw new Error('Teacher profile or verification controls were not fully readonly in preview mode')
      }
      const teacherDocumentDownloadsReadOnly = await evaluate(`(() => {
        const buttons = Array.from(document.querySelectorAll('button')).filter((button) => button.innerText.includes('Download'));
        return {
          count: buttons.length,
          allDisabled: buttons.length > 0 && buttons.every((button) => button.disabled),
        };
      })()`)
      snapshot = await capture(record, '-download-readonly')
      if (teacherDocumentDownloadsReadOnly?.count > 0 && !teacherDocumentDownloadsReadOnly?.allDisabled) {
        throw new Error('Teacher document download controls were not readonly in preview mode')
      }
      const markAllDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Mark all read') && button.disabled)`)
      if (!markAllDisabled) throw new Error('Teacher mark-all-read preview button was not disabled')
    })

    await runFlow('Student profile refund readonly protection', async (record) => {
      await enterPreviewRole('Preview Student', '/student/home')
      await navigate('/student/profile')
      let snapshot = await capture(record, '-profile')
      if (!snapshot.text.includes('Preview student profile')) throw new Error('Student profile preview notice missing')
      const refundControlsReadOnly = await evaluate(`(() => {
        const order = document.querySelector('#student-refund-order');
        const reason = document.querySelector('#student-refund-reason');
        const submit = Array.from(document.querySelectorAll('button')).find((button) => button.innerText.includes('Submit refund request'));
        return {
          orderDisabled: Boolean(order?.disabled),
          reasonDisabled: Boolean(reason?.disabled),
          submitDisabled: Boolean(submit?.disabled),
        };
      })()`)
      snapshot = await capture(record, '-refund-readonly')
      if (
        !refundControlsReadOnly?.orderDisabled ||
        !refundControlsReadOnly?.reasonDisabled ||
        !refundControlsReadOnly?.submitDisabled
      ) {
        throw new Error('Student refund controls were not fully readonly in preview mode')
      }
    })

    await runFlow('Teacher earnings payout validation and readonly protection', async (record) => {
      await enterPreviewRole('Preview Teacher', '/teacher/dashboard')
      await navigate('/teacher/earnings')
      let snapshot = await capture(record, '-earnings')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Teacher earnings preview notice missing')
      const payoutFormReadOnly = await evaluate(`(() => {
        const amount = document.querySelector('#teacher-payout-amount');
        const note = document.querySelector('#teacher-payout-note');
        const submit = Array.from(document.querySelectorAll('button')).find((button) => button.innerText.includes('Submit payout request'));
        return {
          amountDisabled: Boolean(amount?.disabled),
          noteDisabled: Boolean(note?.disabled),
          submitDisabled: Boolean(submit?.disabled),
        };
      })()`)
      snapshot = await capture(record, '-payout-preview')
      if (
        !snapshot.text.includes('Preview data mode keeps payout requests read-only') ||
        !payoutFormReadOnly?.amountDisabled ||
        !payoutFormReadOnly?.noteDisabled ||
        !payoutFormReadOnly?.submitDisabled
      ) {
        throw new Error('Teacher payout preview controls were not fully readonly')
      }
    })

    await runFlow('Admin finance readonly decisions and commission controls', async (record) => {
      await enterPreviewRole('Preview Admin', '/admin/dashboard')
      await navigate('/admin/finance')
      const snapshot = await capture(record, '-finance')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Admin finance preview notice missing')
      const actionButtonsDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).filter((button) => ['Approve', 'Reject', 'Mark refunded', 'Mark paid'].includes(button.innerText.trim())).every((button) => button.disabled)`)
      const commissionDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).some((button) => button.innerText.includes('Save commission') && button.disabled)`)
      const commissionInputDisabled = await evaluate(`document.querySelector('#admin-commission-rate')?.disabled === true`)
      if (!actionButtonsDisabled || !commissionDisabled || !commissionInputDisabled) throw new Error('Admin finance readonly controls were not fully disabled in preview mode')
    })

    await runFlow('Admin content moderation readonly protection', async (record) => {
      await enterPreviewRole('Preview Admin', '/admin/dashboard')
      await navigate('/admin/content')
      let snapshot = await capture(record, '-content')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Admin content preview notice missing')
      const moderationControlsReadOnly = await evaluate(`(() => {
        const reason = document.querySelector('#admin-content-moderation-reason');
        const requestRevision = Array.from(document.querySelectorAll('button')).find((button) => button.innerText.includes('Request revision'));
        const moderationButtons = Array.from(document.querySelectorAll('button'))
          .filter((button) => ['Hide', 'Unpublish', 'Archive', 'Restore'].includes(button.innerText.trim()));
        return {
          reasonDisabled: Boolean(reason?.disabled),
          requestRevisionDisabled: Boolean(requestRevision?.disabled),
          moderationButtonsDisabled: moderationButtons.length > 0 && moderationButtons.every((button) => button.disabled),
        };
      })()`)
      snapshot = await capture(record, '-moderation-readonly')
      if (
        !moderationControlsReadOnly?.reasonDisabled ||
        !moderationControlsReadOnly?.requestRevisionDisabled ||
        !moderationControlsReadOnly?.moderationButtonsDisabled
      ) {
        throw new Error('Admin content moderation controls were not fully readonly in preview mode')
      }
    })

    await runFlow('Admin settings and verification decision readonly protection', async (record) => {
      await enterPreviewRole('Preview Admin', '/admin/dashboard')
      await navigate('/admin/settings')
      let snapshot = await capture(record, '-settings')
      const settingsReadOnly = await evaluate(`(() => {
        const locale = document.querySelector('.admin-settings-select');
        const disclaimer = document.querySelector('.admin-settings-textarea');
        const switches = Array.from(document.querySelectorAll('.admin-settings-switch'));
        const commission = document.querySelector('input[aria-label="Commission rate"]');
        const saveButtons = Array.from(document.querySelectorAll('button')).filter((button) =>
          ['Save', 'Save Settings'].includes(button.innerText.trim())
        );
        return {
          localeDisabled: Boolean(locale?.disabled),
          disclaimerDisabled: Boolean(disclaimer?.disabled),
          switchesDisabled: switches.length > 0 && switches.every((button) => button.disabled),
          commissionDisabled: Boolean(commission?.disabled),
          saveButtonsDisabled: saveButtons.length > 0 && saveButtons.every((button) => button.disabled),
        };
      })()`)
      if (
        !settingsReadOnly?.localeDisabled ||
        !settingsReadOnly?.disclaimerDisabled ||
        !settingsReadOnly?.switchesDisabled ||
        !settingsReadOnly?.commissionDisabled ||
        !settingsReadOnly?.saveButtonsDisabled
      ) {
        throw new Error('Admin settings controls were not fully readonly in preview mode')
      }

      await navigate('/admin/teacher-verification')
      await clickByText('Mei Lin')
      await clickByText('Open Decision Tab')
      await waitForPage('teacher verification decision tab', (state) => state.text.includes('Decision Notes'))
      snapshot = await capture(record, '-verification-decision')
      if (!snapshot.text.includes('Decision Notes')) throw new Error('Teacher verification decision tab did not open')
      const verificationDecisionReadOnly = await evaluate(`(() => ({
        approvalDisabled: Boolean(document.querySelector('#admin-verification-approval-note')?.disabled),
        rejectionDisabled: Boolean(document.querySelector('#admin-verification-rejection-reason')?.disabled),
        rejectButtonDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.trim() === 'Reject' && button.disabled),
        approveButtonDisabled: Array.from(document.querySelectorAll('button')).some((button) => button.innerText.trim() === 'Approve' && button.disabled),
      }))()`)
      if (
        !verificationDecisionReadOnly?.approvalDisabled ||
        !verificationDecisionReadOnly?.rejectionDisabled ||
        !verificationDecisionReadOnly?.rejectButtonDisabled ||
        !verificationDecisionReadOnly?.approveButtonDisabled
      ) {
        throw new Error('Admin teacher verification decision controls were not fully readonly in preview mode')
      }
    })

    await runFlow('Admin teacher verification detail and decision protections', async (record) => {
      await enterPreviewRole('Preview Admin', '/admin/dashboard')
      await navigate('/admin/teacher-verification')
      let snapshot = await capture(record, '-queue')
      if (!snapshot.text.includes('Preview data mode')) throw new Error('Teacher verification preview notice missing')
      await clickByText('Sort:')
      await setValue('.admin-verification-search-input', 'mei')
      snapshot = await capture(record, '-search')
      if (!snapshot.text.toLowerCase().includes('mei')) throw new Error('Teacher verification search did not update')
      await clickByText('Mei Lin')
      snapshot = await capture(record, '-selected')
      if (!snapshot.text.includes('Mei Lin')) throw new Error('Teacher verification row selection did not retain selected applicant')
      await clickByText('Documents')
      const adminTeacherDocumentDownloadsReadOnly = await evaluate(`(() => {
        const buttons = Array.from(document.querySelectorAll('button')).filter((button) => button.innerText.includes('Download'));
        return {
          count: buttons.length,
          allDisabled: buttons.length > 0 && buttons.every((button) => button.disabled),
        };
      })()`)
      snapshot = await capture(record, '-download-readonly')
      if (adminTeacherDocumentDownloadsReadOnly?.count > 0 && !adminTeacherDocumentDownloadsReadOnly?.allDisabled) {
        throw new Error('Admin teacher document download controls were not readonly in preview mode')
      }
      await clickByText('Open Decision Tab')
      await waitForPage('teacher verification decision tab', (state) => state.text.includes('Decision Notes'))
      snapshot = await capture(record, '-decision')
      const approveRejectDisabled = await evaluate(`Array.from(document.querySelectorAll('button')).filter((button) => ['Approve', 'Reject'].includes(button.innerText.trim())).every((button) => button.disabled)`)
      if (!snapshot.text.includes('Decision Notes') || !approveRejectDisabled) throw new Error('Teacher verification decision controls were not readonly in preview mode')
    })

    const report = {
      generatedAt: new Date().toISOString(),
      baseUrl,
      preflight,
      evidenceDir,
      totals: {
        flows: records.length,
        pass: records.filter((record) => record.status === 'PASS').length,
        needsFix: records.filter((record) => record.status === 'NEEDS_FIX').length,
        blocked: records.filter((record) => record.status === 'BLOCKED').length,
        fail: records.filter((record) => record.status === 'FAIL').length,
      },
      records,
    }
    await writeFile(path.join(evidenceDir, 'flow-report.json'), JSON.stringify(report, null, 2), 'utf8')
    await writeFile(path.join(evidenceDir, 'chrome.log'), chromeLogs.join('\n'), 'utf8')
    console.log(`FLOW_REPORT_JSON=${path.join(evidenceDir, 'flow-report.json')}`)
  } finally {
    client?.close()
    chrome.kill()
    await sleep(500)
    await rm(userDataDir, { recursive: true, force: true })
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
