import { expect, test } from '@playwright/test'

test('visitors can open the course catalog from the landing page', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Explore Courses' }).first().click()

  await expect(page).toHaveURL(/\/courses(?:\?|$)/)
  await expect(page.getByRole('heading', { name: 'Explore Courses' })).toBeVisible()
  await expect(page.getByText('Preview catalog mode')).toBeVisible()
})

test('course filters and card actions follow the selected language', async ({ page }) => {
  await page.goto('/courses')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '探索课程' })).toBeVisible()
  await expect(page.getByPlaceholder('搜索课程')).toBeVisible()
  await expect(page.getByRole('button', { name: '查看课程' }).first()).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Teroka Kursus' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Lihat kursus' }).first()).toBeVisible()
})

test('course details explain the next steps in the selected language', async ({ page }) => {
  await page.goto('/courses')
  await page.getByRole('button', { name: 'View course' }).first().click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('button', { name: '返回课程目录' })).toBeVisible()
  await expect(page.getByText('学习体验概览')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('button', { name: 'Kembali ke katalog' })).toBeVisible()
  await expect(page.getByText('Apa yang boleh dijangka')).toBeVisible()
})

test('terms explain free learning access in the selected language', async ({ page }) => {
  await page.goto('/terms')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '免费学习访问' })).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Akses pembelajaran percuma' })).toBeVisible()
})

test('language selection updates the document language', async ({ page }) => {
  await page.goto('/auth')
  await expect(page).toHaveTitle('Sign in | MandarinSpark')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')
  // FR-I18N-05: the browser tab title follows the interface language.
  await expect(page).toHaveTitle('登录 | MandarinSpark')
  await expect(page.getByRole('button', { name: '切换到英语' })).toBeVisible()
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')
  await expect(page).toHaveTitle('登录 | MandarinSpark')
})

test('sign-in and registration follow the selected language', async ({ page }) => {
  await page.goto('/auth')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await page.getByRole('tab', { name: '创建账号' }).click()
  await expect(page.getByRole('heading', { name: '创建账号' })).toBeVisible()
  await expect(page.getByLabel('偏好语言')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Cipta akaun' })).toBeVisible()
  await expect(page.getByLabel('Bahasa Pilihan')).toBeVisible()
})

test('account recovery remains translated across both steps', async ({ page }) => {
  await page.goto('/forgot-password')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '忘记密码？' })).toBeVisible()
  await page.getByRole('link', { name: '重置密码' }).click()
  await expect(page.getByRole('heading', { name: '重置密码' })).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Tetapkan semula kata laluan' })).toBeVisible()
})

test('translated account pages fit a narrow phone viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/auth')
  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()

  for (const path of ['/auth', '/forgot-password', '/reset-password']) {
    await page.goto(path)
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  }
})

test('registration opens terms and privacy in the selected language', async ({ page }) => {
  await page.goto('/auth?tab=register')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()
  await page.getByRole('link', { name: '服务条款' }).click()
  await expect(page.getByRole('heading', { name: '服务条款' })).toBeVisible()

  await page.goto('/privacy')
  await expect(page.getByRole('heading', { name: '隐私声明' })).toBeVisible()
  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Notis Privasi' })).toBeVisible()
})

test('landing page copy follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()
  await expect(page.getByRole('button', { name: '探索课程' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '精选课程' })).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('button', { name: 'Teroka Kursus' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Kursus Pilihan' })).toBeVisible()
})

test('student preview opens the learning workspace', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()

  await expect(page).toHaveURL(/\/student(?:\/?|\?)/)
  await expect(page.getByText('My Learning Path')).toBeVisible()
})

test('student home content follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByText('我的学习路径')).toBeVisible()
  await expect(page.getByText('练习工具')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByText('Laluan Pembelajaran Saya')).toBeVisible()
  await expect(page.getByText('Alat Latihan')).toBeVisible()
})

test('teacher dashboard content follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Teacher' }).click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '教学仪表盘' })).toBeVisible()
  await expect(page.getByText('内容概况')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Papan Pemuka Pengajaran' })).toBeVisible()
  await expect(page.getByText('Ringkasan Kandungan')).toBeVisible()
})

test('admin dashboard content follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Admin' }).click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '管理员控制中心' })).toBeVisible()
  await expect(page.getByText('平台分析概况')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Pusat Kawalan Pentadbir' })).toBeVisible()
  await expect(page.getByText('Ringkasan Analitik Platform')).toBeVisible()
})

test('admin analytics content follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Admin' }).click()
  await page.goto('/admin/analytics')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '分析概览' })).toBeVisible()
  await expect(page.getByText('教师审批明细')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Gambaran Analitik' })).toBeVisible()
  await expect(page.getByText('Pecahan Kelulusan Guru')).toBeVisible()
})

test('admin content management follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Admin' }).click()
  await page.goto('/admin/content')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '内容管理' })).toBeVisible()
  await expect(page.getByText('内容列表')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Pengurusan Kandungan' })).toBeVisible()
  await expect(page.getByText('Senarai kandungan')).toBeVisible()
})

for (const [role, topbar, sidebar] of [
  ['Student', '学生学习之旅', '学生'],
  ['Teacher', '教师工作区', '教师'],
  ['Admin', '管理中心', '管理员'],
] as const) {
  test(`${role.toLowerCase()} workspace shell follows Chinese language selection`, async ({ page }) => {
    await page.goto('/auth')
    await page.getByRole('button', { name: `Preview ${role}` }).click()
    await page
      .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
      .click()

    await expect(page.locator('.topbar-role-badge')).toContainText(topbar)
    await expect(page.locator('.sidebar-intro-badge')).toContainText(sidebar)
  })
}

test('all teacher workspace pages expose Chinese headings', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Teacher' }).click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  for (const [path, heading] of [
    ['/teacher/dashboard', '教学仪表盘'],
    ['/teacher/classes', '我的课堂'],
    ['/teacher/content', '内容管理'],
    ['/teacher/activities', '练习活动'],
    ['/teacher/students', '学生'],
    ['/teacher/feedback', '反馈中心'],
    ['/teacher/profile', '教师资料'],
  ] as const) {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: heading })).toBeVisible()
  }
})

test('all admin workspace pages expose Chinese headings', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Admin' }).click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  for (const [path, heading] of [
    ['/admin/dashboard', '管理员控制中心'],
    ['/admin/users', '用户'],
    ['/admin/teacher-verification', '教师审核'],
    ['/admin/content', '内容管理'],
    ['/admin/content-review', '内容审核'],
    ['/admin/activities', '练习活动库'],
    ['/admin/moderation', '报告与审核'],
    ['/admin/analytics', '分析概览'],
    ['/admin/settings', '设置与审计日志'],
  ] as const) {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: heading })).toBeVisible()
  }
})

test('system pages follow the persisted language', async ({ page }) => {
  await page.goto('/auth')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await page.goto('/route-that-does-not-exist')
  await expect(page.getByRole('heading', { name: '找不到此页面。' })).toBeVisible()

  await page.goto('/unauthorized')
  await expect(page.getByRole('heading', { name: '当前账号无权访问此空间。' })).toBeVisible()
})

const OFFICIAL_TITLE = 'AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia'

test('landing footer shows the official title and links to the Responsible AI guidance', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.public-footer').getByText(OFFICIAL_TITLE)).toBeVisible()
  await page.locator('.public-footer').getByRole('link', { name: 'Responsible AI Guidance' }).click()

  await expect(page).toHaveURL(/\/responsible-ai$/)
  await expect(page.getByRole('heading', { name: 'Responsible AI Guidance', level: 1 })).toBeVisible()
})

test('responsible AI guidance explains limits, data handling and reporting in every language', async ({ page }) => {
  await page.goto('/responsible-ai')
  await expect(page.getByRole('heading', { name: 'Responsible AI Guidance', level: 1 })).toBeVisible()
  await expect(page.getByText(OFFICIAL_TITLE)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'AI can be wrong' })).toBeVisible()
  await expect(page.getByText(/store=false/)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Report a response' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Read the Privacy Notice' })).toBeVisible()

  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()
  await expect(page.getByRole('heading', { name: '负责任 AI 使用指南', level: 1 })).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Panduan AI Bertanggungjawab', level: 1 })).toBeVisible()
})

test('privacy notice shows the official title, PDPA reference and Responsible AI link', async ({ page }) => {
  await page.goto('/privacy')
  await expect(page.getByRole('heading', { name: 'Privacy Notice', level: 1 })).toBeVisible()
  await expect(page.getByTestId('official-title')).toHaveText(OFFICIAL_TITLE)
  await expect(page.getByText(/Personal Data Protection Act 2010/)).toBeVisible()
  await page.getByRole('link', { name: 'Read the Responsible AI Guidance' }).click()
  await expect(page).toHaveURL(/\/responsible-ai$/)
})

test('registration asks for one adult consent declaration', async ({ page }) => {
  await page.goto('/auth?tab=register')
  await expect(page.getByText('I am 18 or older and agree to the')).toBeVisible()
  await expect(page.locator('.auth-checkbox input[type="checkbox"]')).toHaveCount(1)
  await expect(page.locator('.auth-role-card').first()).toContainText('Learner')
})

test('student AI helper labels AI output, shows the disclaimer and offers reporting', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/ai-helper')

  await expect(page.getByTestId('ai-disclaimer').first()).toContainText(
    'AI responses may contain errors and do not replace your teacher.',
  )
  await expect(page.getByTestId('ai-chat-reply').getByTestId('ai-result-label')).toHaveText(/AI-assisted/)
  await expect(page.getByTestId('ai-vocabulary-result').getByTestId('ai-report-toggle')).toBeVisible()
  await expect(page.getByText('Safe Guidance')).toHaveCount(0)

  // FR-AIGEN-03: the whole practice quiz renders and is checked locally, never saved.
  const quiz = page.getByTestId('ai-quiz-result')
  await expect(quiz.getByTestId('ai-result-label')).toHaveText(/AI-assisted/)
  await expect(quiz.getByTestId('ai-quiz-question')).toHaveCount(5)
  await expect(quiz.getByTestId('ai-practice-not-saved')).toContainText('not saved')
  await quiz.getByTestId('ai-quiz-check').click()
  await expect(quiz.getByTestId('ai-quiz-score')).toContainText('/ 5')
  await expect(quiz.getByTestId('ai-report-toggle')).toBeVisible()

  // FR-AIGEN-08: the story shows its vocabulary list and comprehension questions.
  const story = page.getByTestId('ai-story-result')
  await expect(story.getByTestId('ai-story-vocabulary-item')).toHaveCount(5)
  await expect(story.getByTestId('ai-story-question')).toHaveCount(3)
  await story.getByTestId('ai-story-reveal').first().click()
  await expect(story.getByTestId('ai-story-answer').first()).toContainText('By bus')

  await page.getByTestId('ai-chat-reply').getByTestId('ai-report-toggle').click()
  const reasonOptions = page.getByTestId('ai-chat-reply').locator('select option')
  await expect(reasonOptions).toHaveText([
    'Incorrect content',
    'Made-up information (hallucination)',
    'Unsafe content',
    'Inappropriate content',
    'Other',
  ])
})

test('teacher content shows review states and the reviewer reason, without publish-directly copy', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Teacher' }).click()
  await page.goto('/teacher/content')

  await expect(page.getByRole('heading', { name: 'Content Management' })).toBeVisible()
  await expect(page.getByText('Please add the pinyin for each direction phrase', { exact: false })).toBeVisible()
  await expect(page.getByText('In review').first()).toBeVisible()
  await expect(page.getByText(/publish(es)? directly/i)).toHaveCount(0)
})

test('teacher practice activities show stages, rejection reasons and fill a story from an AI draft', async ({
  page,
}) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Teacher' }).click()
  await page.goto('/teacher/activities')

  await expect(page.getByRole('heading', { name: 'Practice Activities' })).toBeVisible()
  await page.getByRole('button', { name: /Short Story: Catching the LRT/ }).click()
  await expect(page.getByTestId('content-review-notice')).toContainText('The second question has two correct options')

  const aiPanel = page.getByTestId('teacher-ai-draft-story')
  await expect(aiPanel.getByTestId('ai-result-label').first()).toHaveText(/AI-assisted/)
  await aiPanel.getByTestId('teacher-ai-draft-topic').fill('Lunch at the canteen')
  await aiPanel.getByTestId('teacher-ai-draft-generate').click()
  await expect(aiPanel.getByTestId('teacher-ai-draft-result')).toContainText('Not saved')
  await expect(page.getByTestId('activity-story-text')).toHaveValue(/食堂/)
})

test('admin content review queue shows the lesson body and requires a reason to reject', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Admin' }).click()
  await page.goto('/admin/content-review')

  await expect(page.getByRole('heading', { name: 'Content Review' })).toBeVisible()
  await page.getByTestId('review-queue-row').filter({ hasText: 'Lantern Festival' }).click()
  await expect(page.getByTestId('content-review-lesson-body')).toContainText('灯笼')
  await expect(page.getByTestId('review-approve')).toBeVisible()
  await expect(page.getByTestId('review-reject')).toBeDisabled()
  await page.getByTestId('review-reject-reason').fill('Add an audio example for each word.')
  await expect(page.getByTestId('review-reject')).toBeEnabled()

  await page.getByTestId('review-queue-row').filter({ hasText: 'Ordering Food Check' }).click()
  await expect(page.getByTestId('content-review-detail')).toContainText('我要这个。')
})

test('student practice shows the learning stage of the current activity', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/practice')

  await expect(page.getByTestId('learning-stage-tag').first()).toHaveText('Guided')
})

test('the topbar bell opens the role notifications page with filters and mark-as-read', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await expect(page.getByTestId('topbar-notifications')).toContainText('2')
  await page.getByTestId('topbar-notifications').click()

  await expect(page).toHaveURL(/\/student\/notifications$/)
  await expect(page.getByRole('heading', { name: 'Notifications', level: 1 })).toBeVisible()
  await expect(page.getByTestId('notification-item')).toHaveCount(3)

  await page.getByTestId('notifications-filter-unread').click()
  await expect(page.getByTestId('notification-item')).toHaveCount(2)
  await page.getByTestId('notification-mark-read').first().click()
  await expect(page.getByText('Preview mode: the change is shown here but not saved.')).toBeVisible()

  for (const role of ['Teacher', 'Admin'] as const) {
    await page.goto('/auth')
    await page.getByRole('button', { name: `Preview ${role}` }).click()
    await page.getByTestId('topbar-notifications').click()
    await expect(page).toHaveURL(new RegExp(`/${role.toLowerCase()}/notifications$`))
    await expect(page.getByTestId('notification-item').first()).toBeVisible()
  }
})

test('account settings validate the password change and stay unsaved in preview', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/account')

  await expect(page.getByRole('heading', { name: 'Account and security', level: 1 })).toBeVisible()
  await page.getByTestId('account-currentPassword').fill('Password123')
  await page.getByTestId('account-newPassword').fill('short')
  await page.getByTestId('account-confirmPassword').fill('different')
  await page.getByTestId('account-change-password').click()
  await expect(page.getByText('Password must be between 8 and 72 characters.')).toBeVisible()

  await page.getByTestId('account-newPassword').fill('Password123')
  await page.getByTestId('account-confirmPassword').fill('Password123')
  await page.getByTestId('account-change-password').click()
  await expect(page.getByText('New password must be different from the current password.')).toBeVisible()

  for (const role of ['teacher', 'admin'] as const) {
    await page.goto('/auth')
    await page.getByRole('button', { name: `Preview ${role === 'teacher' ? 'Teacher' : 'Admin'}` }).click()
    await page.goto(`/${role}/account`)
    await expect(page.getByTestId('account-profile-form')).toBeVisible()
  }

  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()
  await expect(page.getByRole('heading', { name: '账号与安全', level: 1 })).toBeVisible()
})

test('student lesson shows the body text, mastery and quizzes, and the quiz supports hints, text answers and retakes', async ({
  page,
}) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/learn?lessonId=10')

  await expect(page.getByTestId('lesson-body')).toContainText('你好')
  await expect(page.getByTestId('lesson-body').locator('[lang="zh-CN"]').first()).toBeVisible()
  await expect(page.getByTestId('mastery-chip').first()).toHaveText(/In progress/)
  await expect(page.getByTestId('lesson-quizzes')).toContainText('Starter Mandarin Mega Quiz')
  // FR-LRN-02 breadcrumb and FR-CONT-05 safe external link in the lesson body.
  await expect(page.getByTestId('lesson-breadcrumb')).toContainText('Mandarin for Beginners')
  await expect(page.getByTestId('lesson-breadcrumb')).toContainText('Unit 1: First Conversations')
  const lessonLink = page.getByTestId('lesson-body').getByRole('link', { name: /example\.com\/mandarin/ })
  await expect(lessonLink).toHaveAttribute('target', '_blank')
  await expect(lessonLink).toHaveAttribute('rel', 'noopener noreferrer')

  await page.goto('/student/quiz/1')
  await expect(page.getByTestId('quiz-breadcrumb')).toContainText('Unit 1: First Conversations')
  await expect(page.getByTestId('quiz-breadcrumb')).toContainText('Greetings')
  await expect(page.getByTestId('quiz-question')).toBeVisible()
  await page.getByTestId('quiz-hint-button').click()
  await expect(page.getByTestId('quiz-hint-text')).toBeVisible()

  let sawTextAnswer = false
  for (let index = 0; index < 12; index += 1) {
    const textAnswer = page.getByTestId('quiz-text-answer')
    if (await textAnswer.isVisible()) {
      sawTextAnswer = true
      await textAnswer.fill('好')
    } else {
      await page.getByTestId('quiz-option').first().click()
    }
    if (index < 11) await page.getByRole('button', { name: 'Next', exact: true }).click()
  }
  expect(sawTextAnswer).toBe(true)
  await expect(page.getByTestId('quiz-submit')).toBeEnabled()
  await page.getByTestId('quiz-submit').click()

  await expect(page.getByTestId('quiz-result')).toBeVisible()
  await expect(page.getByTestId('quiz-result-score')).toContainText('/ 24')
  await page.getByTestId('quiz-retake').click()
  await expect(page.getByTestId('quiz-result')).toHaveCount(0)
  await expect(page.getByTestId('quiz-submit')).toBeDisabled()
})

test('student practice grades matching attempts and offers a non-graded tracing pad', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/practice?activity=matching')

  const player = page.getByTestId('practice-player')
  await expect(player).toBeVisible()
  const leftItems = player.getByTestId('matching-left-item')
  const rightOptions = player.getByTestId('matching-right-option')
  const count = await leftItems.count()
  expect(count).toBeGreaterThan(1)
  for (let index = 0; index < count; index += 1) {
    await leftItems.nth(index).click()
    await rightOptions.nth(index).click()
  }
  await player.getByTestId('activity-submit').click()
  await expect(player.getByTestId('activity-result')).toBeVisible()
  await expect(player.getByTestId('activity-retry')).toBeVisible()

  await page.goto('/student/practice?activity=tracing')
  const canvas = page.getByTestId('tracing-canvas')
  await expect(canvas).toBeVisible()
  await expect(page.getByTestId('tracing-practice-only')).toBeVisible()
  await expect(page.getByTestId('activity-submit')).toBeDisabled()
  await canvas.scrollIntoViewIfNeeded()
  const box = await canvas.boundingBox()
  if (!box) throw new Error('tracing canvas has no size')
  await page.mouse.move(box.x + box.width * 0.3, box.y + box.height * 0.3)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width * 0.6, box.y + box.height * 0.6, { steps: 8 })
  await page.mouse.up()
  await expect(page.getByTestId('tracing-stroke-count')).toContainText('1')
  await page.getByTestId('activity-submit').click()
  await expect(page.getByTestId('activity-result')).toBeVisible()
})

test('student progress shows lesson mastery and practice submissions show the review state', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/progress')

  await expect(page.getByTestId('progress-lesson-row')).toHaveCount(4)
  await expect(
    page.getByTestId('progress-lesson-row').getByTestId('mastery-chip').filter({ hasText: 'Mastered' }),
  ).toHaveCount(1)
  await expect(page.getByTestId('progress-weak-area').first()).toBeVisible()

  await page.goto('/student/practice-submissions')
  await expect(page.getByTestId('submission-form-pronunciation')).toBeVisible()
  await expect(page.getByTestId('submission-form-handwriting')).toBeVisible()
  await expect(page.getByTestId('submission-item')).toHaveCount(3)
  await expect(page.getByTestId('submission-review-status').first()).toContainText(/teacher/i)
  await expect(page.getByTestId('submission-teacher-feedback').filter({ hasText: 'neutral tone' })).toHaveCount(1)
  await expect(page.getByTestId('ai-result-label').first()).toBeVisible()
})

test('student classes list every upcoming class with its own seats and action', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Student' }).click()
  await page.goto('/student/classes')

  const cards = page.getByTestId('student-class-card')
  await expect(cards).toHaveCount(4)
  for (let index = 0; index < 4; index += 1) {
    await expect(cards.nth(index).getByTestId('student-class-action')).toBeVisible()
    await expect(cards.nth(index).getByTestId('student-class-seats')).toBeVisible()
  }
  await expect(cards.filter({ hasText: 'Ordering Food' }).getByTestId('student-class-action')).toBeDisabled()
  await expect(cards.filter({ hasText: 'Ordering Food' }).getByTestId('student-class-seats')).toHaveText('Full')
  await expect(cards.filter({ hasText: 'Pinyin and Tones Clinic' }).getByTestId('student-class-seats')).toHaveText(
    'No seat limit',
  )
  await expect(cards.filter({ hasText: 'University Life' }).getByTestId('student-class-seats')).toHaveText(
    '3 of 20 seats left',
  )
})

test('admin users search and paging run against the whole directory', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Admin' }).click()
  await page.goto('/admin/users')

  await expect(page.getByTestId('admin-users-row')).toHaveCount(10)
  await expect(page.getByTestId('admin-users-page-label')).toContainText('Page 1 of 2')
  await page.getByTestId('admin-users-next-page').click()
  await expect(page.getByTestId('admin-users-page-label')).toContainText('Page 2 of 2')
  await expect(page.getByTestId('admin-users-row')).toHaveCount(2)

  await page.getByTestId('admin-users-search').fill('chen')
  await expect(page.getByTestId('admin-users-page-label')).toContainText('Page 1 of 1')
  await expect(page.getByTestId('admin-users-row')).toHaveCount(2)
})

test('teacher roster shows lesson mastery and support reasons; feedback lists practice submissions', async ({
  page,
}) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Teacher' }).click()
  await page.goto('/teacher/students')

  await expect(page.getByTestId('student-lesson-mastery').first()).toBeVisible()
  await expect(page.getByText('@example.com')).toHaveCount(0)
  await expect(page.getByTestId('needs-support-indicator')).toHaveCount(1)
  await page.getByRole('button', { name: /Zhang Yi/ }).click()
  await expect(page.getByTestId('student-support-status')).toContainText('Quiz average below 60%')
  await expect(page.getByTestId('student-support-status')).toContainText('No activity for 14 days')

  await page.goto('/teacher/feedback')
  const section = page.getByTestId('practice-review-section')
  await expect(section).toBeVisible()
  await expect(section.getByTestId('practice-review-item').first()).toBeVisible()
  await expect(section.getByTestId('ai-result-label').first()).toBeVisible()
  await expect(section.getByTestId('practice-review-form').first()).toBeVisible()
})
