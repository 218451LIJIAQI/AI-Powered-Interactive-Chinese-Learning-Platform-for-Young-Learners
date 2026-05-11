import { createApp } from 'vue'
import { ElMessage } from 'element-plus'
import {
  // Direct usages in components/templates
  ArrowDownBold,
  Avatar,
  Bell,
  Brush,
  Calendar,
  ChatDotRound,
  ChatLineRound,
  ChatRound,
  CircleCheck,
  Coin,
  Collection,
  CollectionTag,
  Connection,
  CreditCard,
  DataAnalysis,
  DataLine,
  DocumentChecked,
  DocumentCopy,
  DocumentDelete,
  Edit,
  EditPen,
  Files,
  Film,
  Finished,
  Headset,
  Histogram,
  House,
  MagicStick,
  Memo,
  Microphone,
  Notebook,
  Operation,
  QuestionFilled,
  Reading,
  RefreshLeft,
  Search,
  Service,
  Setting,
  ShoppingCart,
  Tickets,
  TrophyBase,
  Upload,
  User,
  UserFilled,
  VideoCamera,
  VideoCameraFilled,
  VideoPlay,
  Wallet,
  Warning,
} from '@element-plus/icons-vue'

import App from './App.vue'
import { i18n } from './i18n'
import { router } from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/authStore'
import './styles/main.css'
import './styles/kid-theme.css'

const app = createApp(App)

// Element Plus components and their styles are auto-imported on demand by the
// `unplugin-vue-components` resolver configured in vite.config.ts, so there is
// no longer a global `app.use(ElementPlus)` call. Function APIs such as
// `ElMessage` / `ElMessageBox` are auto-imported by `unplugin-auto-import`.
//
// Element Plus icons are registered globally below. Templates reference them
// dynamically via `<component :is="item.icon" />` (string-based icon names
// from the route nav config), which the resolver cannot statically pick up.
//
// Important: only the icons actually used in the project are imported here.
// We previously bulk-imported the entire `@element-plus/icons-vue` barrel
// (~600 icons / ~80 kB JS), but Vite cannot tree-shake the
// `Object.entries(...).forEach(app.component)` pattern. Listing each icon
// explicitly drops the unused ~550 icons from the bundle.
const iconRegistry = {
  ArrowDownBold,
  Avatar,
  Bell,
  Brush,
  Calendar,
  ChatDotRound,
  ChatLineRound,
  ChatRound,
  CircleCheck,
  Coin,
  Collection,
  CollectionTag,
  Connection,
  CreditCard,
  DataAnalysis,
  DataLine,
  DocumentChecked,
  DocumentCopy,
  DocumentDelete,
  Edit,
  EditPen,
  Files,
  Film,
  Finished,
  Headset,
  Histogram,
  House,
  MagicStick,
  Memo,
  Microphone,
  Notebook,
  Operation,
  QuestionFilled,
  Reading,
  RefreshLeft,
  Search,
  Service,
  Setting,
  ShoppingCart,
  Tickets,
  TrophyBase,
  Upload,
  User,
  UserFilled,
  VideoCamera,
  VideoCameraFilled,
  VideoPlay,
  Wallet,
  Warning,
} as const

for (const [name, component] of Object.entries(iconRegistry)) {
  app.component(name, component)
}

app.use(pinia)
app.use(router)
app.use(i18n)

// Global error handler: a single, friendly safety net for unexpected runtime
// errors that escape per-component try/catch blocks. Without this, Vue logs
// errors to the console but the user sees nothing — so a rendering error in a
// rare branch becomes a silent dead screen. We surface a translated toast and
// re-log to the console so production errors stay debuggable.
app.config.errorHandler = (err, _instance, info) => {
  const message = err instanceof Error && err.message
    ? err.message
    : i18n.global.t('common.unexpectedError')
  ElMessage.error(message)
  // eslint-disable-next-line no-console
  console.error('[vue:errorHandler]', info, err)
}

const authStore = useAuthStore(pinia)
if (authStore.token) {
  authStore.fetchCurrentUser().catch(() => {
    authStore.clearAuthState()
  })
} else {
  authStore.clearAuthState()
}

app.mount('#app')
