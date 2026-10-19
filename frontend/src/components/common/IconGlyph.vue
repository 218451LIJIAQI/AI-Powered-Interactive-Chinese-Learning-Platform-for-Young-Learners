<template>
  <component :is="resolved" :size="size" :stroke-width="ICON_STROKE_WIDTH" class="icon-glyph" />
</template>

<script setup lang="ts">
import {
  Archive,
  BadgeHelp,
  BarChart3,
  Bell,
  BookMarked,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  CalendarDays,
  ChartColumnBig,
  ChartNoAxesCombined,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  ClipboardList,
  Compass,
  Download,
  Ellipsis,
  FileBadge,
  FileText,
  Flag,
  Flame,
  Funnel,
  Gift,
  Globe2,
  GraduationCap,
  History,
  Image as ImageIcon,
  Eraser,
  KeyRound,
  Info,
  Lightbulb,
  LogOut,
  Menu,
  Mic,
  PenLine,
  Plus,
  RotateCcw,
  Square,
  Upload,
  UserCog,
  Volume2,
  House,
  Languages,
  LayoutDashboard,
  Library,
  Lock,
  MessageSquareText,
  MonitorDot,
  PlayCircle,
  Radio,
  RefreshCcw,
  School,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  TriangleAlert,
  Trophy,
  UserCheck,
  UserRound,
  Users,
  X,
  Zap,
} from '@lucide/vue'
import { computed, type Component } from 'vue'

const DEFAULT_ICON = BookOpen
const ICON_STROKE_WIDTH = 2.2
const warnedUnknownIcons = new Set<string>()

const iconMap: Readonly<Record<string, Component>> = {
  home: House,
  courses: Library,
  about: Sparkles,
  shield: ShieldCheck,
  learn: BookOpen,
  practice: Compass,
  classes: School,
  rewards: Gift,
  ai: Bot,
  profile: UserRound,
  dashboard: LayoutDashboard,
  content: BookMarked,
  students: GraduationCap,
  users: Users,
  verification: FileBadge,
  moderation: MessageSquareText,
  analytics: ChartNoAxesCombined,
  settings: Settings,
  search: Search,
  bell: Bell,
  streak: Flame,
  play: PlayCircle,
  fun: Sparkles,
  live: Radio,
  teacher: GraduationCap,
  verified: UserCheck,
  help: CircleHelp,
  file: FileText,
  chart: BarChart3,
  admin: BriefcaseBusiness,
  trophy: Trophy,
  star: Star,
  language: Languages,
  globe: Globe2,
  status: CheckCircle2,
  flag: Flag,
  clip: ClipboardList,
  progress: ChartColumnBig,
  safe: Lock,
  quiz: BadgeHelp,
  filter: Funnel,
  download: Download,
  more: Ellipsis,
  chevronLeft: ChevronLeft,
  chevronRight: ChevronRight,
  chevronDown: ChevronDown,
  calendar: CalendarDays,
  monitor: MonitorDot,
  refresh: RefreshCcw,
  history: History,
  alert: TriangleAlert,
  close: X,
  flash: Zap,
  mic: Mic,
  stop: Square,
  upload: Upload,
  image: ImageIcon,
  pen: PenLine,
  eraser: Eraser,
  hint: Lightbulb,
  retry: RotateCcw,
  account: UserCog,
  key: KeyRound,
  audio: Volume2,
  menu: Menu,
  logout: LogOut,
  info: Info,
  plus: Plus,
  archive: Archive,
  trash: Trash2,
} as const

interface Props {
  name: string
  size?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 20,
})

function warnUnknownIconName(name: string): void {
  if (!import.meta.env.DEV || warnedUnknownIcons.has(name)) {
    return
  }

  warnedUnknownIcons.add(name)
  console.warn(`[IconGlyph] Unknown icon name: "${name}". Falling back to BookOpen.`)
}

const resolved = computed<Component>(() => {
  const icon = iconMap[props.name]
  if (!icon) {
    warnUnknownIconName(props.name)
  }
  return icon ?? DEFAULT_ICON
})
</script>

<style scoped>
/* Zero-specificity defaults so layout rules can hide or resize icons without fighting scoped styles. */
:where(.icon-glyph) {
  display: block;
  flex: 0 0 auto;
}
</style>
