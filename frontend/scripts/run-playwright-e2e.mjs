import { spawn } from 'node:child_process'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const defaultHost = process.env.E2E_HOST?.trim() || '127.0.0.1'
const defaultPort = Number.parseInt(process.env.E2E_PORT || '4173', 10)

function parseExplicitBaseUrl() {
  const rawBaseUrl = process.env.E2E_BASE_URL?.trim()
  if (!rawBaseUrl) return null

  try {
    return new URL(rawBaseUrl)
  } catch {
    throw new Error(`E2E_BASE_URL must be an absolute URL. Received: ${rawBaseUrl}`)
  }
}

function canBind(host, port) {
  return new Promise((resolve) => {
    const server = net.createServer()
    server.once('error', () => resolve(false))
    server.once('listening', () => {
      server.close(() => resolve(true))
    })
    server.listen(port, host)
  })
}

async function findAvailablePort(host, startPort) {
  if (!Number.isInteger(startPort) || startPort < 1 || startPort > 65_535) {
    throw new Error(`E2E_PORT must be an integer from 1 to 65535. Received: ${process.env.E2E_PORT}`)
  }

  for (let port = startPort; port <= startPort + 50 && port <= 65_535; port += 1) {
    if (await canBind(host, port)) {
      return port
    }
  }

  throw new Error(`No available E2E port found from ${startPort} to ${Math.min(startPort + 50, 65_535)}.`)
}

const explicitBaseUrl = parseExplicitBaseUrl()
if (explicitBaseUrl) {
  process.env.E2E_BASE_URL = explicitBaseUrl.href.replace(/\/$/, '')
  process.env.E2E_HOST = explicitBaseUrl.hostname
  process.env.E2E_PORT = explicitBaseUrl.port || (explicitBaseUrl.protocol === 'https:' ? '443' : '80')
} else {
  const port = await findAvailablePort(defaultHost, defaultPort)
  process.env.E2E_HOST = defaultHost
  process.env.E2E_PORT = String(port)
  process.env.E2E_BASE_URL = `http://${defaultHost}:${port}`
}

const playwrightBin = process.platform === 'win32' ? 'playwright.cmd' : 'playwright'
const child = spawn(playwrightBin, ['test', ...process.argv.slice(2)], {
  cwd: frontendRoot,
  env: process.env,
  shell: process.platform === 'win32',
  stdio: 'inherit',
})

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal)
    return
  }
  process.exit(code ?? 1)
})
