<template>
  <!-- Icons are decorative: the text or the aria-label of their control says what they mean, so assistive technology
       skips them instead of announcing an unnamed image. -->
  <component
    :is="resolved"
    :size="size"
    :stroke-width="ICON_STROKE_WIDTH"
    class="icon-glyph"
    aria-hidden="true"
    focusable="false"
  />
</template>

<script lang="ts">
import {
  Archive,
  BadgeHelp,
  BarChart3,
  Bell,
  BookMarked,
  BookOpen,
  BookText,
  Bot,
  BriefcaseBusiness,
  Brush,
  CalendarCheck,
  CalendarDays,
  ChartColumnBig,
  ChartNoAxesCombined,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleCheck,
  CircleHelp,
  CircleX,
  Clock,
  Compass,
  Copy,
  Crown,
  Download,
  ExternalLink,
  Eye,
  EyeOff,
  FileText,
  Flag,
  Flame,
  Globe2,
  GraduationCap,
  Hand,
  Headphones,
  History,
  House,
  Inbox,
  Info,
  Languages,
  Layers,
  LayoutDashboard,
  LifeBuoy,
  Lightbulb,
  Link,
  ListChecks,
  Lock,
  LockOpen,
  LogOut,
  Map as MapIcon,
  Mail,
  MessageCircle,
  Mic,
  PanelLeft,
  Paperclip,
  PartyPopper,
  PenLine,
  Pencil,
  PlayCircle,
  Plus,
  Puzzle,
  RefreshCcw,
  RotateCcw,
  Route,
  School,
  ScrollText,
  Search,
  Send,
  Settings,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Square,
  Star,
  Target,
  Trash2,
  TriangleAlert,
  Trophy,
  Undo2,
  Upload,
  UserCog,
  UserPlus,
  UserRound,
  Users,
  UtensilsCrossed,
  Video,
  Volume2,
  WandSparkles,
  X,
  Zap,
} from '@lucide/vue'
import type { Component } from 'vue'

const DEFAULT_ICON = BookOpen
const ICON_STROKE_WIDTH = 2.2
const warnedUnknownIcons = new Set<string>()

/** Also holds the unit icons the backend sends as `iconKey` (see `LearnPathContent`). */
const iconMap = {
  home: House,
  sparkles: Sparkles,
  shield: ShieldCheck,
  learn: BookOpen,
  practice: Compass,
  classes: School,
  ai: Bot,
  profile: UserRound,
  dashboard: LayoutDashboard,
  sidebar: PanelLeft,
  content: BookMarked,
  students: GraduationCap,
  users: Users,
  analytics: ChartNoAxesCombined,
  settings: Settings,
  search: Search,
  bell: Bell,
  streak: Flame,
  play: PlayCircle,
  help: CircleHelp,
  file: FileText,
  chart: BarChart3,
  trophy: Trophy,
  star: Star,
  language: Languages,
  globe: Globe2,
  status: CircleCheck,
  flag: Flag,
  progress: ChartColumnBig,
  quiz: BadgeHelp,
  download: Download,
  chevronLeft: ChevronLeft,
  chevronRight: ChevronRight,
  chevronDown: ChevronDown,
  chevronUp: ChevronUp,
  calendar: CalendarDays,
  refresh: RefreshCcw,
  history: History,
  alert: TriangleAlert,
  close: X,
  flash: Zap,
  mic: Mic,
  stop: Square,
  upload: Upload,
  pen: PenLine,
  hint: Lightbulb,
  retry: RotateCcw,
  account: UserCog,
  audio: Volume2,
  logout: LogOut,
  info: Info,
  plus: Plus,
  archive: Archive,
  trash: Trash2,
  audit: ScrollText,
  check: Check,
  lock: Lock,
  unlock: LockOpen,
  target: Target,
  map: MapIcon,
  route: Route,
  listening: Headphones,
  layers: Layers,
  puzzle: Puzzle,
  brush: Brush,
  story: BookText,
  chat: MessageCircle,
  send: Send,
  video: Video,
  clock: Clock,
  edit: Pencil,
  copy: Copy,
  eye: Eye,
  eyeOff: EyeOff,
  external: ExternalLink,
  link: Link,
  userPlus: UserPlus,
  mail: Mail,
  attach: Paperclip,
  magic: WandSparkles,
  celebrate: PartyPopper,
  crown: Crown,
  calendarCheck: CalendarCheck,
  support: LifeBuoy,
  inbox: Inbox,
  correct: CircleCheck,
  incorrect: CircleX,
  undo: Undo2,
  checklist: ListChecks,
  hand: Hand,
  utensils: UtensilsCrossed,
  shopping: ShoppingBag,
  briefcase: BriefcaseBusiness,
} as const satisfies Record<string, Component>

export type IconName = keyof typeof iconMap
</script>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  name: IconName | (string & {})
  size?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 20,
})

const resolved = computed<Component>(() => {
  const icon = Object.hasOwn(iconMap, props.name) ? iconMap[props.name as IconName] : undefined
  if (!icon && import.meta.env.DEV && !warnedUnknownIcons.has(props.name)) {
    warnedUnknownIcons.add(props.name)
    console.warn(`[IconGlyph] Unknown icon name: "${props.name}". Falling back to BookOpen.`)
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
