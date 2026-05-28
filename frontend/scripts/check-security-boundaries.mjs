import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')
const nginxConfigPath = path.join(frontendRoot, 'nginx.conf')

const checks = [
  {
    name: 'direct window.open',
    pattern: /\bwindow\s*\.\s*open\s*\(/,
    allowFiles: new Set([path.join('src', 'utils', 'url.ts')]),
    remediation: 'Use openSafeHttpUrl from src/utils/url.ts so URLs are validated and opener isolation is enforced.',
  },
  {
    name: 'raw innerHTML assignment',
    pattern: /\binnerHTML\b/,
    allowFiles: new Set(),
    remediation: 'Render data through Vue bindings/components instead of injecting HTML.',
  },
  {
    name: 'insertAdjacentHTML',
    pattern: /\binsertAdjacentHTML\s*\(/,
    allowFiles: new Set(),
    remediation: 'Render data through Vue bindings/components instead of injecting HTML.',
  },
  {
    name: 'document.write',
    pattern: /\bdocument\s*\.\s*write\s*\(/,
    allowFiles: new Set(),
    remediation: 'Avoid parser-time HTML injection APIs.',
  },
  {
    name: 'eval',
    pattern: /\beval\s*\(/,
    allowFiles: new Set(),
    remediation: 'Use explicit parsing or dispatch tables instead of executing strings.',
  },
  {
    name: 'Function constructor',
    pattern: /\bnew\s+Function\s*\(/,
    allowFiles: new Set(),
    remediation: 'Use explicit parsing or dispatch tables instead of executing strings.',
  },
  {
    name: 'Vue v-html',
    pattern: /\bv-html\b/,
    allowFiles: new Set(),
    remediation: 'Render structured content through components; do not inject HTML into the DOM.',
  },
]

async function listSourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = []

  for (const entry of entries) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'test' || entry.name === '__tests__') continue
      files.push(...(await listSourceFiles(entryPath)))
      continue
    }

    if (!/\.(ts|vue|js|mjs)$/.test(entry.name)) continue
    if (/\.(test|spec)\.(ts|vue|js|mjs)$/.test(entry.name)) continue
    files.push(entryPath)
  }

  return files
}

const findings = []
for (const filePath of await listSourceFiles(sourceRoot)) {
  const relativePath = path.relative(frontendRoot, filePath).split(path.sep).join('/')
  const normalizedRelativePath = path.relative(frontendRoot, filePath)
  const contents = await readFile(filePath, 'utf8')

  for (const check of checks) {
    if (check.allowFiles.has(normalizedRelativePath)) continue

    const lines = contents.split(/\r?\n/)
    lines.forEach((line, index) => {
      if (check.pattern.test(line)) {
        findings.push({
          check,
          line: index + 1,
          path: relativePath,
        })
      }
    })
  }
}

if (findings.length > 0) {
  console.error('Frontend security boundary check failed.')
  for (const finding of findings) {
    console.error(`- ${finding.path}:${finding.line} uses ${finding.check.name}. ${finding.check.remediation}`)
  }
  process.exit(1)
}

const nginxConfig = await readFile(nginxConfigPath, 'utf8')
const requiredHeaders = [
  'Content-Security-Policy',
  'X-Content-Type-Options "nosniff"',
  'X-Frame-Options "DENY"',
  'Referrer-Policy "strict-origin-when-cross-origin"',
  'Permissions-Policy "geolocation=(), microphone=(), camera=(), payment=(), usb=()"',
]

const nginxFindings = []
for (const header of requiredHeaders) {
  if (!nginxConfig.includes(`add_header ${header}`)) {
    nginxFindings.push(`nginx.conf is missing add_header ${header}.`)
  }
}
if (nginxConfig.includes("'unsafe-eval'")) {
  nginxFindings.push("nginx.conf must not allow 'unsafe-eval' in the Content-Security-Policy.")
}
if (!/location = \/api\/v1\/payments\/webhooks\/stripe\s*\{[\s\S]*client_max_body_size 256k;/.test(nginxConfig)) {
  nginxFindings.push('nginx.conf must keep the Stripe webhook route capped at 256k.')
}

if (nginxFindings.length > 0) {
  console.error('Nginx security header check failed.')
  for (const finding of nginxFindings) {
    console.error(`- ${finding}`)
  }
  process.exit(1)
}

console.log('Frontend security boundary check passed.')
