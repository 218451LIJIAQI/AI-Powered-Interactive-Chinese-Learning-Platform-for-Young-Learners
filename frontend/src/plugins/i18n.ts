import { readonly, watch } from 'vue'
import { createI18n } from 'vue-i18n'

import type { AppLanguage, UserRole } from '@/types/api'
import { normalizeAppLanguage } from '@/utils/locale'
import { safeGetItem, safeSetItem, storageKeys } from '@/utils/storage'

import { aiSafetyMessages } from './aiSafetyMessages'
import { practiceTypeMessages } from './practiceTypeMessages'
import { xpLevelMessages } from './xpLevelMessages'

export type Translate = (key: string, values?: Record<string, unknown>, plural?: number) => string

const messages = {
  en: {
    aiSafety: aiSafetyMessages.en,
    practiceType: practiceTypeMessages.en,
    xpLevel: xpLevelMessages.en,
    common: {
      dailyGoal: 'Daily goal',
      checkAnswer: 'Check',
      skip: 'Skip',
      seeResult: 'See my result',
      practiseAgain: 'Practise again',
      reviewMistakes: 'Review mistakes',
      choiceKeysHint: 'Press 1–{count} to choose · Enter to check',
      errorTitle: 'Something went wrong',
      loadingTitle: 'Loading your content',
      loadingMessage: 'Just a moment while we get things ready for you!',
      exploreClasses: 'Explore classes',
      undo: 'Undo',
      clearSearch: 'Clear search',
      search: 'Search',
      clearFilters: 'Clear filters',
      open: 'Open',
      signIn: 'Sign in',
      sending: 'Sending…',
      exportCsv: 'Export CSV',
      edit: 'Edit',
      close: 'Close',
      cancel: 'Cancel',
      breadcrumb: 'Breadcrumb',
      retry: 'Try again',
      allowMicrophone: 'Allow microphone',
      notNow: 'Not now',
      showPassword: 'Show password',
      helloMeaning: 'hello',
      opensInNewTab: '(opens in a new tab)',
    },
    roles: {
      STUDENT: 'Learner',
      ADMIN: 'Admin',
    } satisfies Record<UserRole, string>,
    hsk: {
      level1: 'HSK 1 (Basic)',
      level2: 'HSK 2 (Survival)',
      level3: 'HSK 3 (Conversational)',
      number: 'HSK {level}',
    },
    chrome: {
      skipToMain: 'Skip to main content',
      primaryNavigation: 'Primary',
      workspaceNavigation: 'Workspace navigation',
      language: 'Interface language',
      switchEnglish: 'Switch to English',
      switchMalay: 'Switch to Bahasa Melayu',
      switchChinese: 'Switch to Simplified Chinese',
    },
    nav: {
      home: 'Home',
      ai: 'AI',
      support: 'Support',
      auditLog: 'Audit log',
      howItWorks: 'How it works',
      help: 'Help',
      learn: 'Learn',
      practice: 'Practice',
      classes: 'Classes',
      aiHelper: 'AI Helper',
      dashboard: 'Dashboard',
      content: 'Content',
      contentSections: 'Content sections',
      learningPath: 'Learning path',
      practiceLibrary: 'Practice library',
      users: 'Users',
      analytics: 'Analytics',
      progress: 'Progress',
    },
    tracingPad: {
      keyboard: 'With a keyboard: Space starts or ends a stroke, the arrow keys draw.',
    },
    pagination: {
      previous: 'Previous',
      next: 'Next',
      pageOf: 'Page {page} of {total}',
    },
    shell: {
      createAccount: 'Create account',
      openMyHome: 'Open my home',
      footerNavigation: 'Footer',
      footerContact: 'Contact us',
      footerCopyright: '© {year} MandarinSpark',
      footerBye: 'see you again',
    },
  },
  ms: {
    aiSafety: aiSafetyMessages.ms,
    practiceType: practiceTypeMessages.ms,
    xpLevel: xpLevelMessages.ms,
    common: {
      dailyGoal: 'Matlamat harian',
      checkAnswer: 'Semak',
      skip: 'Langkau',
      seeResult: 'Lihat keputusan saya',
      practiseAgain: 'Berlatih lagi',
      reviewMistakes: 'Ulang kaji kesilapan',
      choiceKeysHint: 'Tekan 1–{count} untuk memilih · Enter untuk menyemak',
      errorTitle: 'Sesuatu tidak kena',
      loadingTitle: 'Memuatkan kandungan anda',
      loadingMessage: 'Tunggu sebentar sementara kami menyediakan semuanya untuk anda!',
      exploreClasses: 'Terokai kelas',
      undo: 'Buat asal',
      clearSearch: 'Kosongkan carian',
      search: 'Cari',
      clearFilters: 'Kosongkan penapis',
      open: 'Buka',
      signIn: 'Log masuk',
      sending: 'Menghantar…',
      exportCsv: 'Eksport CSV',
      edit: 'Sunting',
      close: 'Tutup',
      cancel: 'Batal',
      breadcrumb: 'Laluan halaman',
      retry: 'Cuba lagi',
      allowMicrophone: 'Benarkan mikrofon',
      notNow: 'Bukan sekarang',
      showPassword: 'Tunjukkan kata laluan',
      helloMeaning: 'helo',
      opensInNewTab: '(dibuka dalam tab baharu)',
    },
    roles: {
      STUDENT: 'Pelajar',
      ADMIN: 'Pentadbir',
    } satisfies Record<UserRole, string>,
    hsk: {
      level1: 'HSK 1 (Asas)',
      level2: 'HSK 2 (Harian)',
      level3: 'HSK 3 (Perbualan)',
      number: 'HSK {level}',
    },
    chrome: {
      skipToMain: 'Langkau ke kandungan utama',
      primaryNavigation: 'Navigasi utama',
      workspaceNavigation: 'Navigasi ruang kerja',
      language: 'Bahasa antara muka',
      switchEnglish: 'Tukar kepada bahasa Inggeris',
      switchMalay: 'Tukar kepada Bahasa Melayu',
      switchChinese: 'Tukar kepada bahasa Cina Ringkas',
    },
    nav: {
      home: 'Laman Utama',
      ai: 'AI',
      support: 'Sokongan',
      auditLog: 'Log audit',
      howItWorks: 'Cara ia berfungsi',
      help: 'Bantuan',
      learn: 'Belajar',
      practice: 'Latihan',
      classes: 'Kelas',
      aiHelper: 'Pembantu AI',
      dashboard: 'Papan Pemuka',
      content: 'Kandungan',
      contentSections: 'Bahagian kandungan',
      learningPath: 'Laluan pembelajaran',
      practiceLibrary: 'Pustaka latihan',
      users: 'Pengguna',
      analytics: 'Analitik',
      progress: 'Kemajuan',
    },
    tracingPad: {
      keyboard: 'Dengan papan kekunci: Space memulakan atau menamatkan strok, kekunci anak panah melukis.',
    },
    pagination: {
      previous: 'Sebelumnya',
      next: 'Seterusnya',
      pageOf: 'Halaman {page} daripada {total}',
    },
    shell: {
      createAccount: 'Cipta akaun',
      openMyHome: 'Buka laman saya',
      footerNavigation: 'Navigasi kaki halaman',
      footerContact: 'Hubungi kami',
      footerCopyright: '© {year} MandarinSpark',
      footerBye: 'jumpa lagi',
    },
  },
  'zh-CN': {
    aiSafety: aiSafetyMessages['zh-CN'],
    practiceType: practiceTypeMessages['zh-CN'],
    xpLevel: xpLevelMessages['zh-CN'],
    common: {
      dailyGoal: '每日目标',
      checkAnswer: '检查',
      skip: '跳过',
      seeResult: '查看结果',
      practiseAgain: '再练一次',
      reviewMistakes: '复习错题',
      choiceKeysHint: '按 1–{count} 选择 · 按 Enter 检查',
      errorTitle: '出现问题',
      loadingTitle: '正在加载内容',
      loadingMessage: '请稍候，我们正在为你准备内容！',
      exploreClasses: '浏览课堂',
      undo: '撤销',
      clearSearch: '清除搜索',
      search: '搜索',
      clearFilters: '清除筛选',
      open: '打开',
      signIn: '登录',
      sending: '正在发送…',
      exportCsv: '导出 CSV',
      edit: '编辑',
      close: '关闭',
      cancel: '取消',
      breadcrumb: '页面路径',
      retry: '重试',
      allowMicrophone: '允许使用麦克风',
      notNow: '暂不',
      showPassword: '显示密码',
      helloMeaning: '打招呼',
      opensInNewTab: '（在新标签页中打开）',
    },
    roles: {
      STUDENT: '学习者',
      ADMIN: '管理员',
    } satisfies Record<UserRole, string>,
    hsk: {
      level1: 'HSK 1（基础）',
      level2: 'HSK 2（日常）',
      level3: 'HSK 3（会话）',
      number: 'HSK {level}',
    },
    chrome: {
      skipToMain: '跳转到主要内容',
      primaryNavigation: '主导航',
      workspaceNavigation: '工作区导航',
      language: '界面语言',
      switchEnglish: '切换到英语',
      switchMalay: '切换到马来语',
      switchChinese: '切换到简体中文',
    },
    nav: {
      home: '首页',
      ai: 'AI',
      support: '支持',
      auditLog: '审计日志',
      howItWorks: '使用方式',
      help: '帮助',
      learn: '学习',
      practice: '练习',
      classes: '课堂',
      aiHelper: 'AI 助手',
      dashboard: '仪表盘',
      content: '内容',
      contentSections: '内容分区',
      learningPath: '学习路径',
      practiceLibrary: '练习库',
      users: '用户',
      analytics: '分析',
      progress: '进度',
    },
    tracingPad: {
      keyboard: '使用键盘：空格键开始或结束一笔，方向键书写。',
    },
    pagination: {
      previous: '上一页',
      next: '下一页',
      pageOf: '第 {page} 页，共 {total} 页',
    },
    shell: {
      createAccount: '创建账号',
      openMyHome: '进入我的主页',
      footerNavigation: '页脚导航',
      footerContact: '联系我们',
      footerCopyright: '© {year} MandarinSpark',
      footerBye: '下次见',
    },
  },
} as const

export const i18n = createI18n({
  legacy: false,
  locale: normalizeAppLanguage(safeGetItem(storageKeys.language)) ?? 'en',
  fallbackLocale: 'en',
  messages,
})

export const appLocale = readonly(i18n.global.locale)

function syncDocumentLang(locale: AppLanguage): void {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale
  }
}

// Keep <html lang> (and therefore apiClientMessage / serverMessageOr) aligned on load and if locale is mutated directly.
const stopLangSync = watch(
  () => i18n.global.locale.value,
  (locale) => syncDocumentLang(locale),
  { immediate: true, flush: 'sync' },
)
import.meta.hot?.dispose(stopLangSync)

export function setLocale(locale: AppLanguage): void {
  i18n.global.locale.value = locale
  syncDocumentLang(locale)
  safeSetItem(storageKeys.language, locale)
}

/** Apply the account's preferred language when the app supports it. */
export function applyPreferredLanguage(value: string | null | undefined): void {
  const locale = normalizeAppLanguage(value)
  if (locale) setLocale(locale)
}
