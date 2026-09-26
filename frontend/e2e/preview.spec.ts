import { expect, test } from '@playwright/test'

test('families can open the course catalog from the landing page', async ({ page }) => {
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
  await expect(page.getByRole('button', { name: '查看详情' }).first()).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Teroka Kursus' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Lihat Butiran' }).first()).toBeVisible()
})

test('course details explain the next steps in the selected language', async ({ page }) => {
  await page.goto('/courses')
  await page.getByRole('button', { name: 'View Details' }).first().click()
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('button', { name: '返回课程目录' })).toBeVisible()
  await expect(page.getByText('课程包含的体验')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('button', { name: 'Kembali ke katalog' })).toBeVisible()
  await expect(page.getByText('Apa yang boleh dijangka')).toBeVisible()
})

test('cancelled checkout explains access in the selected language', async ({ page }) => {
  await page.goto('/payment/cancel')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '支付已取消' })).toBeVisible()
  await expect(page.getByText('结算已取消，未解锁新的访问权限。')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Pembayaran dibatalkan' })).toBeVisible()
})

test('language selection updates the document language', async ({ page }) => {
  await page.goto('/auth')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.locator('html')).toHaveAttribute('lang', 'zh')
  await expect(page.getByRole('button', { name: '切换到英语' })).toBeVisible()
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh')
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
  await expect(page.getByRole('heading', { name: '隐私政策' })).toBeVisible()
  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Dasar Privasi' })).toBeVisible()
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

test('teacher earnings content follows Chinese and Malay language selection', async ({ page }) => {
  await page.goto('/auth')
  await page.getByRole('button', { name: 'Preview Teacher' }).click()
  await page.goto('/teacher/earnings')
  await page
    .getByRole('button', { name: /Switch to Simplified Chinese|切换到简体中文|Tukar kepada bahasa Cina Ringkas/ })
    .click()

  await expect(page.getByRole('heading', { name: '教师收益' })).toBeVisible()
  await expect(page.getByText('收益记录')).toBeVisible()

  await page.getByRole('button', { name: /Switch to Bahasa Melayu|切换到马来语|Tukar kepada Bahasa Melayu/ }).click()
  await expect(page.getByRole('heading', { name: 'Pendapatan Guru' })).toBeVisible()
  await expect(page.getByText('Entri pendapatan')).toBeVisible()
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
    ['/teacher/students', '学生'],
    ['/teacher/feedback', '反馈中心'],
    ['/teacher/profile', '教师资料'],
    ['/teacher/earnings', '教师收益'],
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
    ['/admin/activities', '活动资源库'],
    ['/admin/moderation', '报告与审核'],
    ['/admin/analytics', '分析概览'],
    ['/admin/settings', '设置与审计日志'],
    ['/admin/finance', '财务'],
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
  await expect(page.getByRole('heading', { name: '糟糕，这条学习路径不存在。' })).toBeVisible()

  await page.goto('/unauthorized')
  await expect(page.getByRole('heading', { name: '当前账号无权访问此空间。' })).toBeVisible()
})
