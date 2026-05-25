import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

export const options = {
  scenarios: {
    baseline: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: Number(__ENV.K6_TARGET_VUS || 10) },
        { duration: '3m', target: Number(__ENV.K6_TARGET_VUS || 10) },
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

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080'
const email = __ENV.TEST_STUDENT_EMAIL
const password = __ENV.TEST_STUDENT_PASSWORD

const aiLatency = new Trend('ai_chat_latency')
const authFailures = new Rate('auth_failures')

function login() {
  if (!email || !password) {
    return null
  }

  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  )

  authFailures.add(response.status !== 200)
  if (response.status !== 200) {
    return null
  }

  return response.json('data.token')
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
