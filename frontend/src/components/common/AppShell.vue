<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import KidSparkles from './KidSparkles.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'
import MascotPanda from './MascotPanda.vue'
import { resetUnreadCount } from '../../composables/useNotificationUnreadCount'
import { STORAGE_KEYS } from '../../constants/storageKeys'
import { useAuthStore } from '../../stores/authStore'
import { safeGetItem, safeSetItem } from '../../utils/safeStorage'

type NavItem = {
  key: string
  labelKey: string
  icon: string
  routeName?: string
  disabled?: boolean
  chip?: string
  badgeCount?: number
}

type NavGroup = {
  key: string
  labelKey: string
  emoji?: string
  items: NavItem[]
}

const props = withDefaults(
  defineProps<{
    variant: 'public' | 'student' | 'teacher' | 'admin'
    /** Optional shell tagline rendered next to the brand mark.
     *  Most product surfaces should leave this blank — each page already has
     *  its own `<PageHero>` that owns the page title. The shell topbar is
     *  brand + actions, like Linear / Vercel / Stripe. */
    title?: string
    subtitle?: string
    navItems?: NavItem[]
    navGroups?: NavGroup[]
    footerLinks?: { labelKey: string; routeName: string }[]
  }>(),
  {
    title: '',
    subtitle: '',
    navItems: undefined,
    navGroups: undefined,
    footerLinks: undefined,
  },
)

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const isPublic = computed(() => props.variant === 'public')
const isStudent = computed(() => props.variant === 'student')
const useGroupedNav = computed(() => Array.isArray(props.navGroups) && props.navGroups.length > 0)
const flatItems = computed<NavItem[]>(() => {
  if (props.navGroups && props.navGroups.length) {
    return props.navGroups.flatMap((group) => group.items)
  }
  return props.navItems ?? []
})

const currentUserName = computed(() => authStore.currentUser?.fullName ?? '')
const activeKey = computed(() => String(route.meta.navKey ?? route.name ?? ''))
const firstName = computed(() => {
  const name = authStore.currentUser?.fullName ?? ''
  return name.split(' ')[0] || name
})
const mascotBubble = computed(() => {
  if (!isStudent.value) {
    return ''
  }
  return firstName.value ? t('kid.mascotGreeting', { name: firstName.value }) : t('kid.mascotGreetingDefault')
})

/* ------------------------------------------------------------------ */
/* Group expand/collapse state with localStorage persistence          */
/* ------------------------------------------------------------------ */

const collapseStorageKey = computed(() => `${STORAGE_KEYS.NAV_COLLAPSE_PREFIX}:${props.variant}`)
const openGroupKeys = ref<Set<string>>(new Set())

function isGroupOpen(key: string) {
  return openGroupKeys.value.has(key)
}

function persistOpenGroups() {
  safeSetItem(collapseStorageKey.value, JSON.stringify([...openGroupKeys.value]))
}

function toggleGroup(key: string) {
  if (openGroupKeys.value.has(key)) {
    openGroupKeys.value.delete(key)
  } else {
    openGroupKeys.value.add(key)
  }
  // Re-assign so reactivity tracks the change
  openGroupKeys.value = new Set(openGroupKeys.value)
  persistOpenGroups()
}

function expandAll() {
  if (!props.navGroups) return
  openGroupKeys.value = new Set(props.navGroups.map((g) => g.key))
  persistOpenGroups()
}

function collapseAll() {
  openGroupKeys.value = new Set()
  persistOpenGroups()
}

function findGroupForActiveItem(): string | undefined {
  if (!props.navGroups) return undefined
  for (const group of props.navGroups) {
    for (const item of group.items) {
      if (item.key === activeKey.value || item.routeName === activeKey.value) {
        return group.key
      }
    }
  }
  return undefined
}

function ensureActiveGroupOpen() {
  const groupKey = findGroupForActiveItem()
  if (groupKey && !openGroupKeys.value.has(groupKey)) {
    openGroupKeys.value.add(groupKey)
    openGroupKeys.value = new Set(openGroupKeys.value)
    persistOpenGroups()
  }
}

onMounted(() => {
  // Restore from storage if present. `JSON.parse` can still throw on tampered
  // data even though `safeGetItem` swallows storage-level errors, so we keep
  // the parsing call inside a try/catch.
  let restored = false
  const raw = safeGetItem(collapseStorageKey.value)
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (Array.isArray(parsed)) {
        openGroupKeys.value = new Set(parsed.filter((v): v is string => typeof v === 'string'))
        restored = true
      }
    } catch {
      // Corrupted JSON — fall through to defaults; the next persistOpenGroups
      // call will overwrite the bad value.
    }
  }

  // First-time visitors: open the group containing the active item by default.
  if (!restored && props.navGroups) {
    const active = findGroupForActiveItem()
    if (active) {
      openGroupKeys.value.add(active)
    } else if (props.navGroups[0]) {
      openGroupKeys.value.add(props.navGroups[0].key)
    }
    persistOpenGroups()
  } else {
    ensureActiveGroupOpen()
  }
})

// When the route changes, make sure the active item's group is visible.
watch(activeKey, () => ensureActiveGroupOpen())

/* ------------------------------------------------------------------ */
/* Search filter                                                       */
/* ------------------------------------------------------------------ */

const searchQuery = ref('')
const searchActive = computed(() => searchQuery.value.trim().length > 0)

const filteredGroups = computed(() => {
  if (!props.navGroups) return [] as NavGroup[]
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return props.navGroups
  return props.navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => t(item.labelKey).toLowerCase().includes(q)),
    }))
    .filter((group) => group.items.length > 0)
})

const flattenedSearchItems = computed<NavItem[]>(() =>
  filteredGroups.value.flatMap((group) => group.items),
)

const totalSearchResults = computed(() => flattenedSearchItems.value.length)

function handleSearchKeydown(event: Event) {
  // el-input forwards both `Event` and `KeyboardEvent` so we narrow defensively
  // before reading keyboard-specific fields.
  if (!(event instanceof KeyboardEvent)) return

  if (event.key === 'Escape') {
    searchQuery.value = ''
    return
  }
  if (event.key === 'Enter' && flattenedSearchItems.value.length > 0) {
    event.preventDefault()
    const first = flattenedSearchItems.value[0]
    if (first && !first.disabled) {
      handleItemClick(first)
    }
  }
}

/* ------------------------------------------------------------------ */
/* Navigation handlers                                                 */
/* ------------------------------------------------------------------ */

async function handleNavigate(routeName?: string, disabled?: boolean) {
  if (!routeName || disabled) {
    if (disabled) {
      ElMessage.info(t('common.comingSoon'))
    }
    return
  }

  await router.push({ name: routeName })
}

function handleItemClick(item: NavItem) {
  // Clear the search after navigating so the menu returns to its grouped view.
  if (searchQuery.value) {
    searchQuery.value = ''
  }
  handleNavigate(item.routeName, item.disabled)
}

async function handleLogout() {
  await authStore.logout()
  resetUnreadCount()
  ElMessage.success(t('auth.messages.logoutSuccess'))
  await router.push('/login')
}

function isItemActive(item: NavItem) {
  return item.key === activeKey.value || item.routeName === activeKey.value
}
</script>

<template>
  <div class="app-shell" :class="`app-shell--${variant}`">
    <KidSparkles v-if="isStudent" :count="10" />
    <header class="shell-topbar">
      <div class="shell-brand">
        <router-link to="/" class="brand-mark" :aria-label="t('common.appName')">
          <span class="brand-mark__orb" aria-hidden="true" />
          <span class="brand-mark__name">{{ t('common.appName') }}</span>
        </router-link>
        <!-- Optional tagline: most pages omit this and let their PageHero own
             the page title. Kept available so individual layouts can opt in. -->
        <div v-if="title" class="shell-brand__copy">
          <span class="shell-brand__title">{{ title }}</span>
          <span v-if="subtitle" class="shell-brand__subtitle">{{ subtitle }}</span>
        </div>
      </div>

      <MascotPanda
        v-if="isStudent"
        size="sm"
        class="shell-mascot"
        :bubble="mascotBubble"
      />

      <div class="shell-topbar__actions">
        <div v-if="!isPublic && currentUserName" class="header-user-chip">
          <el-icon><UserFilled /></el-icon>
          <span>{{ currentUserName }}</span>
        </div>
        <LanguageSwitcher />
        <el-button v-if="!isPublic" plain size="small" @click="handleLogout">
          {{ t('auth.logout') }}
        </el-button>
      </div>
    </header>

    <div class="shell-body" :class="{ 'shell-body--public': isPublic }">
      <aside class="shell-sidebar">
        <!-- Grouped sidebar (used by student/teacher/admin) -->
        <nav v-if="useGroupedNav" class="shell-menu kid-nav" aria-label="Main navigation">
          <div class="kid-nav__search">
            <el-input
              v-model="searchQuery"
              :placeholder="t('kid.navSearchPlaceholder')"
              size="default"
              clearable
              @keydown="handleSearchKeydown"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>

          <!-- Search results view -->
          <template v-if="searchActive">
            <div v-if="totalSearchResults === 0" class="kid-nav__empty">
              {{ t('kid.navSearchEmpty') }}
            </div>
            <div v-else class="kid-nav__results">
              <button
                v-for="item in flattenedSearchItems"
                :key="item.key"
                type="button"
                class="kid-nav-item"
                :class="{ 'is-active': isItemActive(item), 'is-disabled': item.disabled }"
                :disabled="item.disabled"
                @click="handleItemClick(item)"
              >
                <el-icon class="kid-nav-item__icon"><component :is="item.icon" /></el-icon>
                <span class="kid-nav-item__label">{{ t(item.labelKey) }}</span>
                <el-tag
                  v-if="item.chip"
                  size="small"
                  effect="plain"
                  round
                  class="kid-nav-item__chip"
                >
                  {{ item.chip }}
                </el-tag>
                <el-badge
                  v-if="typeof item.badgeCount === 'number' && item.badgeCount > 0"
                  :value="item.badgeCount"
                  class="kid-nav-item__badge"
                />
              </button>
            </div>
          </template>

          <!-- Grouped collapsible view -->
          <template v-else>
            <div
              v-for="group in navGroups"
              :key="group.key"
              class="kid-nav-group"
              :class="{ 'is-open': isGroupOpen(group.key) }"
            >
              <button
                type="button"
                class="kid-nav-group__header"
                :aria-expanded="isGroupOpen(group.key)"
                @click="toggleGroup(group.key)"
              >
                <span class="kid-nav-group__emoji" aria-hidden="true">{{ group.emoji ?? '' }}</span>
                <span class="kid-nav-group__label">{{ t(group.labelKey) }}</span>
                <span class="kid-nav-group__count">{{ group.items.length }}</span>
                <el-icon class="kid-nav-group__chevron"><ArrowDownBold /></el-icon>
              </button>

              <div class="kid-nav-group__body">
                <div class="kid-nav-group__inner">
                  <button
                    v-for="item in group.items"
                    :key="item.key"
                    type="button"
                    class="kid-nav-item"
                    :class="{ 'is-active': isItemActive(item), 'is-disabled': item.disabled }"
                    :disabled="item.disabled"
                    @click="handleItemClick(item)"
                  >
                    <el-icon class="kid-nav-item__icon"><component :is="item.icon" /></el-icon>
                    <span class="kid-nav-item__label">{{ t(item.labelKey) }}</span>
                    <el-tag
                      v-if="item.chip"
                      size="small"
                      effect="plain"
                      round
                      class="kid-nav-item__chip"
                    >
                      {{ item.chip }}
                    </el-tag>
                    <el-badge
                      v-if="typeof item.badgeCount === 'number' && item.badgeCount > 0"
                      :value="item.badgeCount"
                      class="kid-nav-item__badge"
                    />
                  </button>
                </div>
              </div>
            </div>

            <div class="kid-nav__actions">
              <button type="button" class="kid-nav__action-btn" @click="expandAll">
                {{ t('kid.navExpandAll') }}
              </button>
              <button type="button" class="kid-nav__action-btn" @click="collapseAll">
                {{ t('kid.navCollapseAll') }}
              </button>
            </div>
          </template>
        </nav>

        <!-- Flat sidebar (legacy, used by public layout) -->
        <el-menu v-else :default-active="activeKey" class="shell-menu">
          <el-menu-item
            v-for="item in flatItems"
            :key="item.key"
            :index="item.routeName ?? item.key"
            :disabled="item.disabled"
            @click="handleNavigate(item.routeName, item.disabled)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ t(item.labelKey) }}</span>
            <el-tag v-if="item.chip" size="small" effect="plain" round class="shell-menu__chip">
              {{ item.chip }}
            </el-tag>
            <el-badge
              v-if="typeof item.badgeCount === 'number' && item.badgeCount > 0"
              :value="item.badgeCount"
              class="shell-menu__badge"
            />
          </el-menu-item>
        </el-menu>
      </aside>

      <div class="shell-main">
        <main class="shell-content">
          <router-view v-slot="{ Component, route: r }">
            <transition name="kid-page" mode="out-in">
              <component :is="Component" :key="r.fullPath" />
            </transition>
          </router-view>
        </main>

        <footer v-if="isPublic" class="public-footer">
          <div class="public-footer__brand">{{ t('common.appName') }}</div>
          <div class="public-footer__links">
            <router-link
              v-for="link in footerLinks"
              :key="link.routeName"
              :to="{ name: link.routeName }"
              class="public-footer__link"
            >
              {{ t(link.labelKey) }}
            </router-link>
          </div>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
}

/* Brand-block typography overrides for the optional tagline. Title is small
 * and quiet — a contextual label, not a hero. */
.shell-brand {
  align-items: center;
}

:deep(.shell-brand__copy) {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 14px;
  border-left: 1px solid var(--border-color);
}

.shell-brand__title {
  font-size: 0.92rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-primary);
  line-height: 1.2;
}

.shell-brand__subtitle {
  font-size: 0.78rem;
  color: var(--text-tertiary);
  line-height: 1.4;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shell-mascot {
  margin: 0 8px;
  align-self: center;
}

@media (max-width: 960px) {
  .shell-mascot,
  :deep(.shell-brand__copy) {
    display: none;
  }
}
</style>
