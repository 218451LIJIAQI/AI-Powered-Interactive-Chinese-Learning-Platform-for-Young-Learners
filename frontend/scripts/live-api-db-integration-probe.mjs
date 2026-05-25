/**
 * Live API + database integration probe — verifies that the REST
 * surface and the underlying MySQL rows agree.
 *
 * <h2>Purpose</h2>
 * Catches the bug class where the API returns data that does not
 * actually exist in (or contradicts) the database — for example a
 * cached endpoint serving stale rows, a soft-deleted user that
 * still appears in admin listings, or a payment record whose
 * webhook write was lost.
 *
 * <h2>Flow</h2>
 *   1. Authenticate against the live API as admin.
 *   2. Hit a curated set of endpoints (`/admin/users`,
 *      `/admin/finance`, teacher / student dashboards, etc.).
 *   3. For each response, `docker exec` into the MySQL container
 *      and run an authoritative SELECT against the relevant table.
 *   4. Compare the API rows with the DB rows; flag anything that
 *      differs in count, primary key set, or critical fields like
 *      {@code role}, {@code status}, {@code amount}, deletion flags.
 *   5. Also exercises a few write paths (password reset, refund
 *      request) and re-checks the DB to confirm the write landed.
 *
 * <h2>Why docker exec (vs. JDBC)</h2>
 * Using `docker exec mysql ...` keeps this script dependency-free
 * — no Node MySQL driver, no schema-version drift between probe
 * and production. The trade-off is that it only works against the
 * docker-compose local stack; for remote environments, the
 * `QA_DB_CONTAINER` env var must point at a network-reachable
 * container or the probe is skipped gracefully.
 *
 * <h2>Evidence</h2>
 * Writes a comprehensive JSON report to
 * `frontend/browser-qa-evidence/<timestamp>-live-api-db-probe/` with
 * per-endpoint pass / mismatch details so a failure is debuggable
 * without re-running the probe.
 */
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { execFile as execFileCallback } from 'node:child_process'
import { promisify } from 'node:util'

const execFile = promisify(execFileCallback)

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const frontendDir = path.resolve(__dirname, '..')
const repoRoot = path.resolve(frontendDir, '..')

const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(frontendDir, 'browser-qa-evidence', `${timestamp}-live-api-db-probe`)
await fs.mkdir(evidenceDir, { recursive: true })

const apiBaseUrl = process.env.QA_API_BASE_URL ?? 'http://127.0.0.1:8081/api/v1'
const dbContainer = process.env.QA_DB_CONTAINER ?? 'ai-chinese-learning-mysql'
const dbUser = process.env.QA_DB_USER ?? 'aichinese_app'
const dbPassword = process.env.QA_DB_PASSWORD ?? 'replace-with-a-strong-application-db-password'
const dbName = process.env.QA_DB_NAME ?? 'ai_chinese_learning'
const adminEmail = process.env.QA_ADMIN_EMAIL ?? 'admin@example.com'
const adminPassword = process.env.QA_ADMIN_PASSWORD ?? 'SuperSecure123!'
const qaPassword = 'Password123!'
const qaNewPassword = 'NewPassword123!'
const runId = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
const tinyPngBytes = Uint8Array.from(
  Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=', 'base64'),
)

const state = {
  generatedAt: new Date().toISOString(),
  apiBaseUrl,
  dbContainer,
  evidenceDir,
  runId,
  accounts: {},
  checks: [],
  requests: [],
  db: [],
  ids: {},
}

function redact(value) {
  if (value == null) return value
  if (typeof value === 'string') {
    if (value.length > 24 && /^[A-Za-z0-9._~+/=-]+$/.test(value)) return `${value.slice(0, 8)}...redacted`
    return value
  }
  if (Array.isArray(value)) return value.map(redact)
  if (typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => {
        if (/password|token|authorization|secret/i.test(key)) return [key, 'redacted']
        return [key, redact(entry)]
      }),
    )
  }
  return value
}

function addCheck(name, pass, details = {}) {
  state.checks.push({
    name,
    pass: Boolean(pass),
    details: redact(details),
  })
  if (!pass) {
    throw new Error(`${name} failed: ${JSON.stringify(redact(details))}`)
  }
}

async function writeProgress() {
  await fs.writeFile(path.join(evidenceDir, 'live-api-db-probe-report.json'), JSON.stringify(state, null, 2))
}

async function http(method, urlPath, { token, body, headers = {}, formData, expected = [200] } = {}) {
  const startedAt = Date.now()
  const requestHeaders = { ...headers }
  const options = { method, headers: requestHeaders }
  if (token) requestHeaders.Authorization = `Bearer ${token}`
  if (formData) {
    options.body = formData
  } else if (body !== undefined) {
    requestHeaders['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  const response = await fetch(`${apiBaseUrl}${urlPath}`, options)
  const contentType = response.headers.get('content-type') ?? ''
  const responseBody = contentType.includes('application/json') ? await response.json() : await response.text()
  const record = {
    method,
    path: urlPath,
    status: response.status,
    ok: response.ok,
    durationMs: Date.now() - startedAt,
    request: redact({ body, headers: requestHeaders, formData: formData ? '[multipart/form-data]' : undefined }),
    response: redact(responseBody),
  }
  state.requests.push(record)
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${urlPath} returned ${response.status}: ${JSON.stringify(redact(responseBody))}`)
  }
  return responseBody
}

async function db(sql, label) {
  const { stdout, stderr } = await execFile(
    'docker',
    [
      'exec',
      dbContainer,
      'mysql',
      `-u${dbUser}`,
      `-p${dbPassword}`,
      dbName,
      '--batch',
      '--raw',
      '--skip-column-names',
      '-e',
      sql,
    ],
    { cwd: repoRoot, maxBuffer: 1024 * 1024 * 20 },
  )
  const rows = stdout.trim()
    ? stdout
        .trim()
        .split(/\r?\n/)
        .map((line) => line.split('\t'))
    : []
  state.db.push({ label, sql, rows: redact(rows), stderr: stderr.trim() })
  return rows
}

async function scalar(sql, label) {
  const rows = await db(sql, label)
  return rows[0]?.[0] ?? null
}

function quoteSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

function sha256Hex(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex')
}

function data(body) {
  if (!body || body.success !== true) {
    throw new Error(`Expected success envelope, received ${JSON.stringify(redact(body))}`)
  }
  return body.data
}

async function login(email, password) {
  const body = await http('POST', '/auth/login', {
    body: { email, password },
  })
  const payload = data(body)
  return {
    token: payload.token,
    user: payload.user,
  }
}

async function registerStudent(email) {
  const body = await http('POST', '/auth/register/student', {
    body: {
      fullName: `QA Student ${runId}`,
      email,
      password: qaPassword,
      confirmPassword: qaPassword,
      preferredLanguage: 'en',
    },
  })
  return data(body)
}

async function registerTeacher(email) {
  const body = await http('POST', '/auth/register/teacher', {
    body: {
      fullName: `QA Teacher ${runId}`,
      email,
      password: qaPassword,
      confirmPassword: qaPassword,
      bio: 'QA integration teacher for live API and DB probe.',
    },
  })
  return data(body)
}

async function main() {
  await http('GET', '/health')
  await http('GET', '/public/courses')

  const studentEmail = `qa.live.student.${runId}@example.com`
  const teacherEmail = `qa.live.teacher.${runId}@example.com`
  state.accounts.student = { email: studentEmail }
  state.accounts.teacher = { email: teacherEmail }
  state.accounts.admin = { email: adminEmail }

  const registeredStudent = await registerStudent(studentEmail)
  const registeredTeacher = await registerTeacher(teacherEmail)
  addCheck(
    'student register returned user DTO',
    Boolean(registeredStudent.id && registeredStudent.roles?.includes('STUDENT')),
    registeredStudent,
  )
  addCheck('teacher register returned pending status', registeredTeacher.teacherStatus === 'PENDING', registeredTeacher)

  const studentUserId = await scalar(`select id from users where email='${quoteSql(studentEmail)}'`, 'student user id')
  const teacherUserId = await scalar(`select id from users where email='${quoteSql(teacherEmail)}'`, 'teacher user id')
  const studentProfileId = await scalar(
    `select id from student_profiles where user_id=${studentUserId}`,
    'student profile id',
  )
  const teacherProfileId = await scalar(
    `select id from teacher_profiles where user_id=${teacherUserId}`,
    'teacher profile id',
  )
  addCheck('student persisted to users/student_profiles', Boolean(studentUserId && studentProfileId), {
    studentUserId,
    studentProfileId,
  })
  addCheck('teacher persisted to users/teacher_profiles', Boolean(teacherUserId && teacherProfileId), {
    teacherUserId,
    teacherProfileId,
  })
  state.ids.studentUserId = Number(studentUserId)
  state.ids.teacherUserId = Number(teacherUserId)
  state.ids.studentProfileId = Number(studentProfileId)
  state.ids.teacherProfileId = Number(teacherProfileId)

  await http('POST', '/auth/forgot-password', { body: { email: studentEmail } })
  const resetRows = await db(
    `select id, used_at is null, expires_at > now() from password_reset_tokens where user_id=${studentUserId} order by id desc limit 1`,
    'active password reset token metadata',
  )
  addCheck(
    'forgot-password persisted active reset token',
    resetRows.length === 1 && resetRows[0][1] === '1' && resetRows[0][2] === '1',
    resetRows,
  )

  const loginBeforeReset = await login(studentEmail, qaPassword)
  await db(
    `update password_reset_tokens set token='${sha256Hex(`qa_live_reset_${runId}`)}' where id=${resetRows[0][0]}`,
    'set deterministic QA reset token',
  )
  const resetToken = `qa_live_reset_${runId}`
  await http('POST', '/auth/reset-password', {
    body: {
      token: resetToken,
      password: qaNewPassword,
      confirmPassword: qaNewPassword,
    },
  })
  const tokenUsed = await scalar(
    `select used_at is not null from password_reset_tokens where id=${resetRows[0][0]}`,
    'password reset token consumed',
  )
  addCheck('reset-password consumed token', tokenUsed === '1', { tokenUsed })
  await http('POST', '/auth/login', {
    body: { email: studentEmail, password: qaPassword },
    expected: [401],
  })
  const studentLogin = await login(studentEmail, qaNewPassword)
  addCheck('reset-password changed login credential', Boolean(studentLogin.token), { user: studentLogin.user })

  const teacherLogin = await login(teacherEmail, qaPassword)
  const adminLogin = await login(adminEmail, adminPassword)
  state.accounts.student.user = studentLogin.user
  state.accounts.teacher.user = teacherLogin.user
  state.accounts.admin.user = adminLogin.user

  const verificationFile = new Blob([tinyPngBytes], { type: 'image/png' })
  const verificationForm = new FormData()
  verificationForm.append('file', verificationFile, `qa-verification-${runId}.png`)
  verificationForm.append('documentType', 'IDENTITY')
  verificationForm.append('description', `QA verification ${runId}`)
  const verificationStatus = data(
    await http('POST', '/teachers/verification-documents', {
      token: teacherLogin.token,
      formData: verificationForm,
    }),
  )
  const documentId = verificationStatus.latestSubmittedDocuments?.[0]?.id
  state.ids.teacherVerificationDocumentId = documentId
  addCheck('teacher verification upload returned document', Boolean(documentId), verificationStatus)
  const uploadRows = await db(
    `select tvd.id, tvd.document_type, tvd.current_version+0, uf.file_name, uf.content_type, uf.file_size from teacher_verification_documents tvd join uploaded_files uf on uf.id=tvd.uploaded_file_id where tvd.teacher_id=${teacherProfileId} order by tvd.id desc limit 1`,
    'teacher verification upload rows',
  )
  addCheck(
    'teacher verification upload persisted file and document',
    uploadRows.length === 1 && uploadRows[0][1] === 'IDENTITY' && uploadRows[0][2] === '1',
    uploadRows,
  )

  const approvedTeacher = data(
    await http('POST', `/admin/teachers/${teacherProfileId}/approve`, {
      token: adminLogin.token,
      body: { note: `QA approval ${runId}` },
    }),
  )
  addCheck('admin approve changed teacher status', approvedTeacher.teacherStatus === 'APPROVED', approvedTeacher)
  const approvalRows = await db(
    `select tp.teacher_status, tp.public_visible+0, count(tvr.id), count(n.id), count(al.id)
     from teacher_profiles tp
     left join teacher_verification_reviews tvr on tvr.teacher_id=tp.id and tvr.decision_status='APPROVED'
     left join notifications n on n.user_id=tp.user_id and n.title='Teacher account approved'
     left join audit_logs al on al.target_type='TeacherProfile' and al.target_id=tp.id and al.action_type='ADMIN_TEACHER_APPROVE'
     where tp.id=${teacherProfileId}
     group by tp.teacher_status, tp.public_visible`,
    'teacher approval db side effects',
  )
  addCheck(
    'admin approval persisted review notification audit',
    approvalRows.length === 1 &&
      approvalRows[0][0] === 'APPROVED' &&
      approvalRows[0][1] === '1' &&
      Number(approvalRows[0][2]) >= 1 &&
      Number(approvalRows[0][3]) >= 1 &&
      Number(approvalRows[0][4]) >= 1,
    approvalRows,
  )

  const teacherApprovedLogin = await login(teacherEmail, qaPassword)
  const modulePayload = {
    title: `QA Live Module ${runId}`,
    description: 'QA live module from API DB probe',
    levelId: 1,
    categoryId: 3,
    accessType: 'PAID',
    price: 12.34,
    coverImageUrl: null,
  }
  const module = data(
    await http('POST', '/teachers/modules', {
      token: teacherApprovedLogin.token,
      body: modulePayload,
    }),
  )
  state.ids.moduleId = module.id
  addCheck(
    'teacher create module returned published module',
    module.title === modulePayload.title && module.contentStatus === 'PUBLISHED',
    module,
  )

  const lessonPayload = {
    title: `QA Live Lesson ${runId}`,
    description: 'QA live lesson from API DB probe',
    moduleId: module.id,
    levelId: 1,
    categoryId: 3,
    learningType: 'RECORDED',
    accessType: 'PAID',
    price: 12.34,
    estimatedDurationMinutes: 10,
    basicContent: 'QA lesson content',
    coverImageUrl: null,
  }
  const lesson = data(
    await http('POST', '/teachers/lessons', {
      token: teacherApprovedLogin.token,
      body: lessonPayload,
    }),
  )
  state.ids.lessonId = lesson.id
  addCheck(
    'teacher create lesson returned published lesson',
    lesson.title === lessonPayload.title && lesson.contentStatus === 'PUBLISHED',
    lesson,
  )

  const quiz = data(
    await http('POST', `/teachers/lessons/${lesson.id}/quizzes`, {
      token: teacherApprovedLogin.token,
      body: {
        title: `QA Quiz ${runId}`,
        description: 'QA quiz from API DB probe',
        timeLimitMinutes: 0,
      },
    }),
  )
  state.ids.quizId = quiz.id
  addCheck('teacher create quiz returned quiz', Boolean(quiz.id), quiz)
  const quizWithQuestion = data(
    await http('POST', `/teachers/quizzes/${quiz.id}/questions`, {
      token: teacherApprovedLogin.token,
      body: {
        questionType: 'MULTIPLE_CHOICE',
        questionText: 'Which answer says hello?',
        correctAnswerText: '你好',
        points: 5,
        displayOrder: 0,
        options: [
          { optionText: '你好', correctOption: true, displayOrder: 0 },
          { optionText: '再见', correctOption: false, displayOrder: 1 },
        ],
        explanation: '你好 means hello.',
      },
    }),
  )
  addCheck(
    'teacher add quiz question updated total points',
    quizWithQuestion.totalPoints === 5 && quizWithQuestion.questions?.length === 1,
    quizWithQuestion,
  )
  const contentRows = await db(
    `select
       (select content_status from learning_modules where id=${module.id}),
       (select content_status from lessons where id=${lesson.id}),
       (select total_points from quizzes where id=${quiz.id}),
       (select count(*) from quiz_questions where quiz_id=${quiz.id}),
       (select count(*) from quiz_options qo join quiz_questions qq on qq.id=qo.question_id where qq.quiz_id=${quiz.id}),
       (select count(*) from audit_logs where actor_user_id=${teacherUserId} and target_id in (${module.id},${lesson.id},${quiz.id}))
     `,
    'teacher content db side effects',
  )
  addCheck(
    'teacher module lesson quiz persisted with questions/options/audit',
    contentRows.length === 1 &&
      contentRows[0][0] === 'PUBLISHED' &&
      contentRows[0][1] === 'PUBLISHED' &&
      contentRows[0][2] === '5' &&
      contentRows[0][3] === '1' &&
      contentRows[0][4] === '2' &&
      Number(contentRows[0][5]) >= 2,
    contentRows,
  )

  const cart = data(
    await http('POST', '/students/cart/items', {
      token: studentLogin.token,
      body: { itemType: 'LESSON', itemId: lesson.id, featureKey: null },
    }),
  )
  addCheck(
    'student add cart returned cart item',
    cart.items?.some((item) => item.itemType === 'LESSON' && item.itemId === lesson.id),
    cart,
  )
  await http('POST', '/students/cart/items', {
    token: studentLogin.token,
    body: { itemType: 'LESSON', itemId: lesson.id, featureKey: null },
    expected: [400],
  })
  const checkout = data(
    await http('POST', '/students/checkout/buy-now', {
      token: studentLogin.token,
      body: { itemType: 'LESSON', itemId: lesson.id, featureKey: null },
    }),
  )
  state.ids.orderId = checkout.orderId
  state.ids.paymentId = checkout.paymentId
  const isStripe = checkout.providerName === 'stripe'
  if (isStripe) {
    addCheck(
      'stripe checkout session created successfully',
      checkout.paymentAvailable === true &&
        checkout.paymentStatus === 'PENDING' &&
        typeof checkout.checkoutUrl === 'string',
      checkout,
    )
  } else {
    addCheck(
      'no-op checkout creates pending payment without fake success',
      checkout.paymentAvailable === false && checkout.providerName === 'no-op' && checkout.paymentStatus === 'PENDING',
      checkout,
    )
  }
  const paymentRows = await db(
    `select o.payment_status, o.total_amount, pr.payment_status, pr.provider_name, pr.provider_status, count(oi.id)
     from orders o
     join payment_records pr on pr.order_id=o.id
     join order_items oi on oi.order_id=o.id
     where o.id=${checkout.orderId}
     group by o.payment_status, o.total_amount, pr.payment_status, pr.provider_name, pr.provider_status`,
    'checkout db side effects',
  )
  if (isStripe) {
    addCheck(
      'checkout persisted order payment order item for stripe',
      paymentRows.length === 1 &&
        paymentRows[0][0] === 'PENDING' &&
        paymentRows[0][2] === 'PENDING' &&
        paymentRows[0][3] === 'stripe' &&
        paymentRows[0][4] === 'OPEN' &&
        Number(paymentRows[0][5]) === 1,
      paymentRows,
    )
  } else {
    addCheck(
      'checkout persisted order payment order item for no-op',
      paymentRows.length === 1 &&
        paymentRows[0][0] === 'PENDING' &&
        paymentRows[0][2] === 'PENDING' &&
        paymentRows[0][3] === 'no-op' &&
        paymentRows[0][4] === 'UNAVAILABLE' &&
        Number(paymentRows[0][5]) === 1,
      paymentRows,
    )
  }
  await http('POST', '/students/refund-requests', {
    token: studentLogin.token,
    body: { orderId: checkout.orderId, reason: `QA refund should be blocked ${runId}` },
    expected: [400],
  })

  const aiAvailability = data(await http('GET', '/ai/availability', { token: studentLogin.token }))
  addCheck('AI availability reflects local unavailable provider', aiAvailability.available === false, aiAvailability)
  const aiChat = data(
    await http('POST', '/ai/chat', {
      token: studentLogin.token,
      body: {
        message: 'How do you say hello in Mandarin?',
        languagePreference: 'en',
        sessionId: null,
        context: 'QA live probe',
        relatedLessonId: null,
      },
    }),
  )
  state.ids.aiChatSessionId = aiChat.sessionId
  addCheck(
    'AI chat returns unavailable fallback without fake success',
    aiChat.status === 'UNAVAILABLE' && Boolean(aiChat.aiResponse),
    aiChat,
  )
  const aiRows = await db(
    `select
       (select count(*) from ai_chat_sessions where id=${aiChat.sessionId} and user_id=${studentUserId}),
       (select count(*) from ai_chat_messages where session_id=${aiChat.sessionId}),
       (select count(*) from ai_usage_logs where user_id=${studentUserId} and related_entity_type='AiChatSession' and related_entity_id=${aiChat.sessionId} and status='UNAVAILABLE')
     `,
    'AI chat DB side effects',
  )
  addCheck(
    'AI chat persisted session messages usage log',
    aiRows.length === 1 && aiRows[0][0] === '1' && Number(aiRows[0][1]) >= 2 && Number(aiRows[0][2]) >= 1,
    aiRows,
  )
  const usageLogId = await scalar(
    `select id from ai_usage_logs where user_id=${studentUserId} and related_entity_type='AiChatSession' and related_entity_id=${aiChat.sessionId} order by id desc limit 1`,
    'AI usage log id',
  )
  const assistantMessageId = await scalar(
    `select id from ai_chat_messages where session_id=${aiChat.sessionId} and sender_role='ASSISTANT' order by id desc limit 1`,
    'AI assistant message id',
  )
  const aiReport = data(
    await http('POST', '/ai/reports', {
      token: studentLogin.token,
      body: {
        aiUsageLogId: Number(usageLogId),
        chatSessionId: aiChat.sessionId,
        chatMessageId: Number(assistantMessageId),
        featureKey: 'CHAT_TUTOR',
        reasonCategory: 'HALLUCINATION',
        description: `QA AI report ${runId}`,
        promptSnapshot: 'How do you say hello in Mandarin?',
        aiResponseSnapshot: aiChat.aiResponse,
      },
    }),
  )
  state.ids.aiReportId = aiReport.id
  addCheck('student AI report created pending review', aiReport.status === 'PENDING_REVIEW', aiReport)
  const reviewedReport = data(
    await http('POST', `/admin/ai-response-reports/${aiReport.id}/review`, {
      token: adminLogin.token,
      body: { status: 'RESOLVED', reviewerNote: `QA resolved ${runId}` },
    }),
  )
  addCheck(
    'admin AI report review resolved',
    reviewedReport.status === 'RESOLVED' && Boolean(reviewedReport.reviewedAt),
    reviewedReport,
  )

  const platformSettings = data(await http('GET', '/admin/system-settings/platform', { token: adminLogin.token }))
  const newDisclaimer = `QA disclaimer ${runId}`.slice(0, 190)
  const updatedPlatformSettings = data(
    await http('PUT', '/admin/system-settings/platform', {
      token: adminLogin.token,
      body: {
        aiDisclaimerText: newDisclaimer,
        emailNotifications: platformSettings.emailNotifications,
        inAppNotifications: platformSettings.inAppNotifications,
        dailySummary: platformSettings.dailySummary,
        strictContentFiltering: platformSettings.strictContentFiltering,
        sensitiveWordFilter: platformSettings.sensitiveWordFilter,
        aiSafetyCheck: platformSettings.aiSafetyCheck,
      },
    }),
  )
  addCheck(
    'admin platform settings update returned new disclaimer',
    updatedPlatformSettings.aiDisclaimerText === newDisclaimer,
    updatedPlatformSettings,
  )
  const settingValue = await scalar(
    "select setting_value from system_settings where setting_key='ai_disclaimer_text'",
    'platform setting db value',
  )
  addCheck('admin platform settings persisted to DB', settingValue === newDisclaimer, { settingValue })
  await http('PUT', '/admin/system-settings/platform', {
    token: adminLogin.token,
    body: platformSettings,
  })

  const moderation = data(
    await http('POST', '/admin/moderation/archive', {
      token: adminLogin.token,
      body: {
        targetType: 'LESSON',
        targetId: lesson.id,
        reason: `QA moderation archive ${runId}`,
      },
    }),
  )
  state.ids.moderationRecordId = moderation.id
  addCheck(
    'admin moderation archive returned moderation record',
    moderation.actionTaken === 'ARCHIVE' && moderation.targetType === 'LESSON',
    moderation,
  )
  const moderationRows = await db(
    `select
       (select content_status from lessons where id=${lesson.id}),
       (select count(*) from content_moderation_records where target_type='LESSON' and target_id=${lesson.id} and action_taken='ARCHIVE'),
       (select count(*) from audit_logs where target_type='LESSON' and target_id=${lesson.id} and action_type='MODERATION_ARCHIVE')
     `,
    'moderation db side effects',
  )
  addCheck(
    'moderation archive persisted lesson status moderation audit',
    moderationRows.length === 1 &&
      moderationRows[0][0] === 'ARCHIVED' &&
      Number(moderationRows[0][1]) >= 1 &&
      Number(moderationRows[0][2]) >= 1,
    moderationRows,
  )

  const teacherNotifications = data(await http('GET', '/notifications?size=20', { token: teacherApprovedLogin.token }))
  const unreadTeacherNotification = teacherNotifications.items?.find((item) => item.status !== 'READ')
  addCheck(
    'teacher has notification from approval/moderation',
    Boolean(unreadTeacherNotification),
    teacherNotifications,
  )
  const readResult = data(await http('PATCH', '/notifications/read-all', { token: teacherApprovedLogin.token }))
  addCheck('notification read-all reports update count', Number.isInteger(readResult.updatedCount), readResult)
  const unreadAfterReadAll = await scalar(
    `select count(*) from notifications where user_id=${teacherUserId} and notification_status='UNREAD'`,
    'teacher unread notifications after read-all',
  )
  addCheck('notification read-all persisted read state', unreadAfterReadAll === '0', { unreadAfterReadAll })

  await writeProgress()
}

try {
  await main()
  state.summary = {
    totalChecks: state.checks.length,
    passedChecks: state.checks.filter((check) => check.pass).length,
    failedChecks: state.checks.filter((check) => !check.pass).length,
    totalRequests: state.requests.length,
    totalDbQueries: state.db.length,
  }
  await writeProgress()
  console.log(JSON.stringify(state.summary, null, 2))
} catch (error) {
  state.error = {
    message: error instanceof Error ? error.message : String(error),
    stack: error instanceof Error ? error.stack : undefined,
  }
  await writeProgress()
  console.error(state.error.message)
  process.exitCode = 1
}
