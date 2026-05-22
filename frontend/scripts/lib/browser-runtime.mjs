import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'

const windowsProgramFiles = [
  process.env['ProgramFiles'],
  process.env['ProgramFiles(x86)'],
  process.env.LOCALAPPDATA,
].filter(Boolean)

function unique(values) {
  return [...new Set(values.filter(Boolean))]
}

function browserCandidates() {
  if (process.platform === 'win32') {
    return unique([
      process.env.CHROME_PATH,
      process.env.QA_CHROME_PATH,
      ...windowsProgramFiles.flatMap((root) => [
        path.join(root, 'Google', 'Chrome', 'Application', 'chrome.exe'),
        path.join(root, 'Google', 'Chrome Beta', 'Application', 'chrome.exe'),
        path.join(root, 'Chromium', 'Application', 'chrome.exe'),
        path.join(root, 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
        path.join(root, 'BraveSoftware', 'Brave-Browser', 'Application', 'brave.exe'),
      ]),
    ])
  }

  if (process.platform === 'darwin') {
    return unique([
      process.env.CHROME_PATH,
      process.env.QA_CHROME_PATH,
      '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
      '/Applications/Google Chrome Beta.app/Contents/MacOS/Google Chrome Beta',
      '/Applications/Chromium.app/Contents/MacOS/Chromium',
      '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
      '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser',
      path.join(os.homedir(), 'Applications', 'Google Chrome.app', 'Contents', 'MacOS', 'Google Chrome'),
    ])
  }

  return unique([
    process.env.CHROME_PATH,
    process.env.QA_CHROME_PATH,
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/snap/bin/chromium',
    '/usr/bin/microsoft-edge',
    '/usr/bin/brave-browser',
  ])
}

export function resolveBrowserExecutablePath() {
  const candidates = browserCandidates()
  const match = candidates.find((candidate) => existsSync(candidate))

  if (!match) {
    const formattedCandidates = candidates.map((candidate) => `- ${candidate}`).join('\n')
    throw new Error(
      `No Chromium-compatible browser executable was found. Set CHROME_PATH to an installed browser, or install one of:\n${formattedCandidates}`,
    )
  }

  return match
}

export async function waitForHttpOk(url, timeoutMs = 30000, intervalMs = 500) {
  const started = Date.now()
  let lastError

  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch(url)
      if (response.ok) {
        return response
      }
      lastError = new Error(`${response.status} ${response.statusText}`)
    } catch (error) {
      lastError = error
    }

    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }

  throw new Error(`Timed out waiting for ${url}: ${lastError?.message || 'request failed'}`)
}
