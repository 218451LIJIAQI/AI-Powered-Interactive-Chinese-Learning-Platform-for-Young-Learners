import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

export const options = {
  scenarios: {
    baseline: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: targetVus() },
        { duration: '3m', target: targetVus() },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1500'],
    checks: ['rate>0.98'],
  },
}

const baseUrl = normalizeBaseUrl(__ENV.BASE_URL || 'http://localhost:8080')
const email = __ENV.TEST_STUDENT_EMAIL
const password = __ENV.TEST_STUDENT_PASSWORD

const aiLatency = new Trend('ai_chat_latency')
const authFailures = new Rate('auth_failures')

function targetVus() {
  const parsed = Number(__ENV.K6_TARGET_VUS || 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 10
}

function normalizeBaseUrl(rawValue) {
  const value = String(rawValue || '')
    .trim()
    .replace(/\/+$/, '')
  if (!/^https?:\/\//i.test(value)) {
    throw new Error('BASE_URL must be an absolute http or https URL.')
  }
  if (/^https?:\/\/[^/?#]*@/i.test(value)) {
    throw new Error('BASE_URL must not include embedded credentials.')
  }
  if (/[?#]/.test(value)) {
    throw new Error('BASE_URL must not include a query string or fragment.')
  }
  return value
}

function login() {
  if (!email || !password) {
    return null
  }

  const response = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  })

  authFailures.add(response.status !== 200)
  if (response.status !== 200) {
    return null
  }

  const token = response.json('data.token')
  return typeof token === 'string' && token.length > 0 ? token : null
}

export default function () {
  const health = http.get(`${baseUrl}/api/v1/health`)
  check(health, {
    'health is ok': (response) => response.status === 200,
  })

  const token = login()
  if (token) {
    const start = Date.now()
    const ai = http.post(
      `${baseUrl}/api/v1/ai/chat`,
      JSON.stringify({ message: 'Please say hello in simple Chinese.' }),
      {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      },
    )
    aiLatency.add(Date.now() - start)
    check(ai, {
      'ai chat accepted or gracefully limited': (response) => [200, 429, 503].includes(response.status),
    })

    const catalog = http.get(`${baseUrl}/api/v1/public/courses`)
    check(catalog, {
      'catalog responds': (response) => response.status === 200,
    })
  }

  sleep(1)
}
