<template>
  <component :is="resolved" :size="size" :stroke-width="2.2" class="icon-glyph" />
</template>

<script setup lang="ts">
/**
 * App-wide icon component that resolves a semantic name
 * (e.g. `"learn"`, `"trophy"`, `"moderation"`) into a Lucide Vue
 * icon. Centralising the lookup table means swapping the icon set
 * (or adjusting a single icon) happens in one place — every
 * component just references `<IconGlyph name="..." />`.
 *
 * Names are kebab-or-camelCase semantic terms rather than the
 * Lucide internal name, so a Lucide rename doesn't require touching
 * every call site. Unknown names fall back to `BookOpen` to avoid
 * a runtime crash on a typo.
 */
import {
  BadgeHelp,
  Banknote,
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
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  CircleHelp,
  ClipboardList,
  Compass,
  Database,
  Download,
  Ellipsis,
  FileBadge,
  FileText,
  Flame,
  Funnel,
  Gift,
  Globe2,
  GraduationCap,
  HandCoins,
  History,
  Home,
  House,
  Languages,
  LayoutDashboard,
  Library,
  Lock,
  Megaphone,
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
  Sword,
  TriangleAlert,
  Trophy,
  UserRound,
  UserCheck,
  Users,
  WalletCards,
  X,
  Zap,
} from '@lucide/vue'
import { computed, type Component } from 'vue'

const props = withDefaults(
  defineProps<{
    name: string
    size?: number
  }>(),
  {
    size: 20,
  },
)

const iconMap: Record<string, Component> = {
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
  finance: WalletCards,
  earnings: HandCoins,
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
  money: Banknote,
  chart: BarChart3,
  admin: BriefcaseBusiness,
  trophy: Trophy,
  star: Star,
  language: Languages,
  globe: Globe2,
  announcement: Megaphone,
  status: CheckCircle2,
  moderationFlag: Sword,
  clip: ClipboardList,
  hero: Home,
  progress: ChartColumnBig,
  safe: Lock,
  quiz: BadgeHelp,
  filter: Funnel,
  download: Download,
  more: Ellipsis,
  chevronLeft: ChevronLeft,
  chevronRight: ChevronRight,
  chevronDown: ChevronDown,
  chevronUp: ChevronUp,
  calendar: CalendarDays,
  monitor: MonitorDot,
  database: Database,
  refresh: RefreshCcw,
  history: History,
  alert: TriangleAlert,
  close: X,
  flash: Zap,
}

const resolved = computed(() => iconMap[props.name] ?? BookOpen)
</script>

<style scoped>
.icon-glyph {
  display: block;
  flex: 0 0 auto;
}
</style>
