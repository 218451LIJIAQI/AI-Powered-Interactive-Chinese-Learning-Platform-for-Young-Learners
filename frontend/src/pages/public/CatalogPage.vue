<template>
  <div class="public-page public-catalog-page">
    <DataNotice
      :using-fallback="catalogUsingFallback"
      :error="catalogNoticeError"
      :success="cartSuccess"
      :fallback-title="t('catalog.fallbackTitle')"
      :fallback-message="t('catalog.fallbackMessage')"
      :error-title="t('catalog.errorTitle')"
      :success-title="t('catalog.successTitle')"
      :detail="catalogNoticeDetail"
    />

    <section class="public-catalog-shell">
      <div class="public-catalog-main">
        <div class="public-catalog-header">
          <div class="public-catalog-header-copy">
            <span class="public-catalog-kicker">{{ t('catalog.kicker') }}</span>
            <h1>{{ t('catalog.title') }}</h1>
            <p>{{ t('catalog.intro') }}</p>
          </div>

          <div class="public-catalog-header-meta">
            <article>
              <strong>{{ matchedCourseCount }}</strong>
              <span>{{ t('catalog.coursesMatched') }}</span>
            </article>
            <article>
              <strong>{{ categories.length }}</strong>
              <span>{{ t('catalog.learningThemes') }}</span>
            </article>
            <article>
              <strong>{{ cart?.items.length ?? 0 }}</strong>
              <span>{{ t('catalog.inCart') }}</span>
            </article>
          </div>
        </div>

        <section class="public-catalog-search">
          <div class="public-search-row">
            <label class="public-search-input">
              <IconGlyph name="search" :size="18" />
              <input v-model="filters.keyword" type="text" :placeholder="t('catalog.searchPlaceholder')" />
            </label>
            <button
              class="btn btn-primary public-filter-button"
              type="button"
              :aria-expanded="filtersOpen"
              @click="toggleFilters"
            >
              {{ filterToggleLabel }}
            </button>
            <button class="public-clear-button" type="button" @click="clearFilters">{{ t('catalog.clearAll') }}</button>
          </div>

          <div v-show="filtersOpen" class="public-filter-grid">
            <label class="public-filter-field">
              <span>{{ t('catalog.category') }}</span>
              <select v-model="filters.categoryId">
                <option value="">{{ t('catalog.allCategories') }}</option>
                <option v-for="category in categories" :key="category.id" :value="String(category.id)">
                  {{ category.name }}
                </option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>{{ t('catalog.level') }}</span>
              <select v-model="filters.levelId">
                <option value="">{{ t('catalog.allLevels') }}</option>
                <option v-for="level in levels" :key="level.id" :value="String(level.id)">{{ level.name }}</option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>{{ t('catalog.activityType') }}</span>
              <select v-model="filters.learningType">
                <option value="">{{ t('catalog.allActivities') }}</option>
                <option value="RECORDED">{{ t('catalog.recorded') }}</option>
                <option value="LIVE">{{ t('catalog.liveSupport') }}</option>
                <option value="HYBRID">{{ t('catalog.hybrid') }}</option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>{{ t('catalog.format') }}</span>
              <select v-model="filters.accessType">
                <option value="">{{ t('catalog.allFormats') }}</option>
                <option value="FREE">{{ t('catalog.free') }}</option>
                <option value="PAID">{{ t('catalog.paid') }}</option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>{{ t('catalog.sortBy') }}</span>
              <select v-model="filters.sort">
                <option value="newest">{{ t('catalog.recentlyAdded') }}</option>
                <option value="title">{{ t('catalog.sortTitle') }}</option>
              </select>
            </label>
          </div>
        </section>

        <section class="public-catalog-feature-strip">
          <PublicFeatureCard
            icon="safe"
            :title="t('catalog.safeFeatureTitle')"
            :description="t('catalog.safeFeatureCopy')"
            tone="purple"
          />
          <PublicFeatureCard
            icon="teacher"
            :title="t('catalog.teachersFeatureTitle')"
            :description="t('catalog.teachersFeatureCopy')"
            tone="blue"
          />
          <PublicFeatureCard
            icon="fun"
            :title="t('catalog.funFeatureTitle')"
            :description="t('catalog.funFeatureCopy')"
            tone="gold"
          />
          <PublicFeatureCard
            icon="progress"
            :title="t('catalog.progressFeatureTitle')"
            :description="t('catalog.progressFeatureCopy')"
            tone="purple"
          />
        </section>

        <section class="public-course-list">
          <StatePanel
            v-if="!filteredCourses.length"
            icon="courses"
            :title="t('catalog.noMatchesTitle')"
            :message="t('catalog.noMatchesCopy')"
            :eyebrow="t('catalog.noMatchesEyebrow')"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="clearFilters">
                {{ t('catalog.clearFilters') }}
              </button>
            </template>
          </StatePanel>

          <PublicCourseShowcaseCard
            v-for="course in filteredCourses"
            :key="course.id"
            :course="course"
            :action-label="courseActionLabel(course)"
            @view="openCourseDetail(course.id)"
            @action="handleCourseAction(course)"
          />

          <nav v-if="courseTotalPages > 1" class="public-catalog-pagination" :aria-label="t('catalog.resultPages')">
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="currentPage <= 0 || coursesResource.loading.value"
              @click="goToCoursePage(currentPage - 1)"
            >
              {{ t('catalog.previous') }}
            </button>
            <span>{{ t('catalog.pageOf', { page: currentPage + 1, total: courseTotalPages }) }}</span>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="currentPage >= courseTotalPages - 1 || coursesResource.loading.value"
              @click="goToCoursePage(currentPage + 1)"
            >
              {{ t('catalog.next') }}
            </button>
          </nav>
        </section>
      </div>

      <aside class="public-catalog-side">
        <div v-if="canUseCart" class="public-cart-card">
          <div class="public-cart-header">
            <strong>{{ t('catalog.myCart', { count: cart?.items.length ?? 0 }) }}</strong>
            <button v-if="cart?.items.length" class="public-clear-button" type="button" @click="clearCart">
              {{ t('catalog.clear') }}
            </button>
          </div>

          <p class="public-cart-note">{{ cartNote }}</p>

          <div class="public-cart-items">
            <p v-if="!cart?.items.length" class="muted public-cart-empty">{{ t('catalog.cartEmptyText') }}</p>
            <div v-for="item in cart?.items ?? []" :key="item.cartItemId" class="public-cart-item">
              <CourseSceneThumb :size="80" :variant="cartThumb(item.title)" />
              <div class="public-cart-item-copy">
                <strong>{{ item.title }}</strong>
                <div class="public-cart-item-tags">
                  <span v-for="tag in cartMetaTags(item)" :key="tag">{{ tag }}</span>
                </div>
              </div>
              <div class="public-cart-item-actions">
                <strong>{{ formatCurrency(item.subtotal, item.currencyCode) }}</strong>
                <button class="public-clear-button" type="button" @click="removeCartItem(item.cartItemId)">
                  {{ t('catalog.remove') }}
                </button>
              </div>
            </div>
          </div>

          <div class="public-cart-summary">
            <div class="row-between">
              <span>{{ t('catalog.subtotal') }}</span>
              <strong v-if="cart">{{ formatCurrency(cart.totalAmount, cart.currencyCode) }}</strong>
              <span v-else class="muted">--</span>
            </div>
            <div class="row-between public-cart-total">
              <span>{{ t('catalog.total') }}</span>
              <strong v-if="cart">{{ formatCurrency(cart.totalAmount, cart.currencyCode) }}</strong>
              <span v-else class="muted">--</span>
            </div>
          </div>

          <button
            class="btn btn-accent btn-block public-cart-checkout"
            type="button"
            :disabled="cartActionDisabled"
            @click="checkoutCart"
          >
            {{ cartCheckoutLabel }}
          </button>

          <div class="public-cart-disclaimer">
            <span>{{ t('catalog.info') }}</span>
            <span>{{ cartActionHint }}</span>
          </div>
        </div>

        <div v-else class="panel stack public-catalog-signin-card">
          <strong>{{ cartAccessTitle }}</strong>
          <p class="muted public-cart-empty">
            {{ cartAccessDescription }}
          </p>
          <button v-if="!auth.isAuthenticated" class="btn btn-primary" type="button" @click="goToAuth">
            {{ t('catalog.signIn') }}
          </button>
        </div>

        <PublicInfoBanner
          icon="safe"
          :title="t('catalog.safeBannerTitle')"
          :description="t('catalog.safeBannerCopy')"
          tone="green"
        />

        <div class="public-help-card">
          <div>
            <strong>{{ t('catalog.helpTitle') }}</strong>
            <p>{{ t('catalog.helpCopy') }}</p>
          </div>
          <button class="btn btn-secondary" type="button" @click="goToAiHelperSignIn">{{ t('catalog.askAi') }}</button>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { publicApi, studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PublicCourseShowcaseCard from '@/components/public/PublicCourseShowcaseCard.vue'
import PublicFeatureCard from '@/components/public/PublicFeatureCard.vue'
import PublicInfoBanner from '@/components/public/PublicInfoBanner.vue'
import { useResource } from '@/composables/useResource'
import { cartDemo, categories as demoCategories, levels as demoLevels, publicCourses } from '@/demo/data'
import { catalogMessages } from '@/plugins/catalogMessages'
import { useAuthStore } from '@/stores/auth'
import type { CartItemResponse, CartResponse, LearningCategory, LearningLevel, PublicCourseSummary } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatCurrency } from '@/utils/format'
import { safeHttpUrl } from '@/utils/url'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { catalog: catalogMessages.en },
    ms: { catalog: catalogMessages.ms },
    zh: { catalog: catalogMessages.zh },
  },
})

type CatalogSort = 'newest' | 'title'
type LearningTypeFilter = '' | 'RECORDED' | 'LIVE' | 'HYBRID'
type AccessTypeFilter = '' | 'FREE' | 'PAID'
type CartThumbVariant = 'family' | 'general' | 'panda'

const learningTypeOptions: readonly Exclude<LearningTypeFilter, ''>[] = ['RECORDED', 'LIVE', 'HYBRID']
const accessTypeOptions: readonly Exclude<AccessTypeFilter, ''>[] = ['FREE', 'PAID']

const filters = reactive({
  keyword: queryText(route.query.keyword),
  sort: validSort(queryText(route.query.sort)),
  levelId: validPositiveIdQuery(route.query.levelId),
  categoryId: validPositiveIdQuery(route.query.categoryId),
  learningType: validEnumQuery(route.query.learningType, learningTypeOptions),
  accessType: validEnumQuery(route.query.accessType, accessTypeOptions),
})
const filtersOpen = ref(true)
const cartSuccess = ref<string | null>(null)
const cartBusy = ref(false)
const currentPage = ref(validPageQuery(route.query.page))
let courseReloadTimer: number | undefined

const coursesResource = useResource(
  () =>
    publicApi.listCourses({
      keyword: filters.keyword.trim() || undefined,
      sort: filters.sort,
      levelId: optionalPositiveId(filters.levelId),
      categoryId: optionalPositiveId(filters.categoryId),
      learningType: filters.learningType || undefined,
      accessType: filters.accessType || undefined,
      page: currentPage.value,
      size: 20,
    }),
  {
    items: publicCourses,
    page: 0,
    size: 20,
    totalItems: publicCourses.length,
    totalPages: 1,
  },
)
const levelsResource = useResource(() => publicApi.getLevels(), demoLevels)
const categoriesResource = useResource(() => publicApi.getCategories(), demoCategories)

const isStudentShopper = computed(() => auth.isAuthenticated && auth.hasRole('STUDENT'))
const signedInWithoutStudentRole = computed(() => auth.isAuthenticated && !auth.hasRole('STUDENT'))
const canUseCart = computed(() => isStudentShopper.value || (auth.isPreviewSession && auth.hasRole('STUDENT')))
const cartResource = useResource(() => studentApi.cart(), cartDemo, false)
watch(
  canUseCart,
  (enabled) => {
    if (enabled && !cartResource.hasData.value && !cartResource.loading.value) {
      void cartResource.reload()
    }
  },
  { immediate: true },
)

const levels = computed<LearningLevel[]>(
  () => levelsResource.data.value ?? (levelsResource.usingFallback.value ? demoLevels : []),
)
const categories = computed<LearningCategory[]>(
  () => categoriesResource.data.value ?? (categoriesResource.usingFallback.value ? demoCategories : []),
)
const courses = computed<PublicCourseSummary[]>(
  () => coursesResource.data.value?.items ?? (coursesResource.usingFallback.value ? publicCourses : []),
)
const cart = computed<CartResponse | null>(
  () => cartResource.data.value ?? (cartResource.usingFallback.value ? cartDemo : null),
)
const courseTotalPages = computed(() =>
  coursesResource.usingFallback.value ? 1 : Math.max(1, coursesResource.data.value?.totalPages ?? 1),
)

const catalogUsingFallback = computed(
  () =>
    coursesResource.usingFallback.value ||
    levelsResource.usingFallback.value ||
    categoriesResource.usingFallback.value ||
    cartResource.usingFallback.value,
)
const catalogNoticeError = computed(
  () =>
    coursesResource.error.value ||
    levelsResource.error.value ||
    categoriesResource.error.value ||
    (canUseCart.value ? cartResource.error.value : null),
)
const catalogNoticeDetail = computed(() => {
  if (catalogNoticeError.value) {
    return canUseCart.value ? t('catalog.browsingRecoveryCart') : t('catalog.browsingRecoveryPublic')
  }

  if (catalogUsingFallback.value) {
    return canUseCart.value ? t('catalog.previewCartLimited') : t('catalog.liveAfterSignIn')
  }

  if (cartSuccess.value) {
    return cartActionHint.value
  }

  return null
})

const filteredCourses = computed(() => {
  if (!coursesResource.usingFallback.value) return courses.value

  const keyword = filters.keyword.trim().toLowerCase()

  return courses.value
    .filter((course) => courseMatchesFallbackFilters(course, keyword))
    .sort((left, right) => {
      if (filters.sort === 'title') return left.title.localeCompare(right.title)
      return right.contentId - left.contentId
    })
})
const matchedCourseCount = computed(() =>
  coursesResource.usingFallback.value ? filteredCourses.value.length : (coursesResource.data.value?.totalItems ?? 0),
)

watch(
  filters,
  () => {
    if (currentPage.value !== 0) {
      currentPage.value = 0
      return
    }
    syncCatalogQuery()
    scheduleCourseReload()
  },
  { deep: true },
)

watch(currentPage, () => {
  syncCatalogQuery()
  void coursesResource.reload()
})

onBeforeUnmount(() => {
  if (courseReloadTimer != null) window.clearTimeout(courseReloadTimer)
})

const cartCheckoutLabel = computed(() => {
  if (!canUseCart.value) return t('catalog.signInCheckout')
  if (cartResource.loading.value) return t('catalog.loadingCart')
  if (cartResource.usingFallback.value) return t('catalog.previewCartOnly')
  if (!cart.value?.items.length) return t('catalog.cartEmpty')
  return t('catalog.checkout')
})
const filterToggleLabel = computed(() => (filtersOpen.value ? t('catalog.hideFilters') : t('catalog.showFilters')))

const cartActionDisabled = computed(
  () =>
    !isStudentShopper.value ||
    cartResource.loading.value ||
    cartResource.usingFallback.value ||
    !cart.value?.items.length ||
    Boolean(cartResource.error.value),
)

const cartNote = computed(() => {
  if (!canUseCart.value) return t('catalog.signInStudentCart')
  if (cartResource.usingFallback.value) return t('catalog.previewCartNote')
  if (cartResource.error.value) return t('catalog.cartUnavailable', { error: cartResource.error.value })
  return t('catalog.syncedCart')
})

const cartActionHint = computed(() => {
  if (!canUseCart.value) return t('catalog.checkoutAfterSignIn')
  if (cartResource.usingFallback.value) return t('catalog.previewReadOnly')
  return t('catalog.checkoutHint')
})

const cartAccessTitle = computed(() =>
  signedInWithoutStudentRole.value ? t('catalog.studentCheckoutOnly') : t('catalog.useLiveCart'),
)
const cartAccessDescription = computed(() =>
  signedInWithoutStudentRole.value ? t('catalog.nonStudentCartCopy') : t('catalog.studentCartCopy'),
)

function courseActionLabel(course: PublicCourseSummary): string {
  if (signedInWithoutStudentRole.value) return t('catalog.viewCourse')
  if (!canUseCart.value) return t('catalog.signIn')
  if (auth.isPreviewSession && !auth.isAuthenticated) return t('catalog.preview')
  if (course.accessType === 'FREE') return t('catalog.viewCourse')
  return t('catalog.addToCart')
}

function handleCourseAction(course: PublicCourseSummary): void {
  if (signedInWithoutStudentRole.value) {
    openCourseDetail(course.id)
    return
  }

  if (!canUseCart.value) {
    goToAuth()
    return
  }

  if (auth.isPreviewSession && !auth.isAuthenticated) {
    openCourseDetail(course.id)
    return
  }

  if (course.accessType === 'FREE') {
    openCourseDetail(course.id)
    return
  }

  void addCourseToCart(course)
}

async function addCourseToCart(course: PublicCourseSummary): Promise<void> {
  await mutateCart(
    () => studentApi.addCartItem({ itemType: course.type, itemId: course.contentId }),
    t('catalog.addedToCart', { title: course.title }),
    t('catalog.addFailed'),
  )
}

async function removeCartItem(cartItemId: number): Promise<void> {
  await mutateCart(() => studentApi.removeCartItem(cartItemId), t('catalog.removedFromCart'), t('catalog.removeFailed'))
}

async function clearCart(): Promise<void> {
  await mutateCart(() => studentApi.clearCart(), t('catalog.cartCleared'), t('catalog.clearFailed'))
}

function canStartCartAction(fallbackMessage: string): boolean {
  if (!isStudentShopper.value) {
    if (!signedInWithoutStudentRole.value) goToAuth()
    return false
  }

  if (cartBusy.value) return false

  if (cartResource.usingFallback.value) {
    cartSuccess.value = null
    cartResource.error.value = fallbackMessage
    return false
  }

  return true
}

async function mutateCart(action: () => Promise<unknown>, successMessage: string, errorMessage: string): Promise<void> {
  if (!canStartCartAction(t('catalog.previewEditBlocked'))) return

  cartBusy.value = true
  try {
    cartSuccess.value = null
    cartResource.error.value = null
    await action()
    await cartResource.reload()
    cartSuccess.value = successMessage
  } catch (reason) {
    cartSuccess.value = null
    cartResource.error.value = resolveApiError(reason, errorMessage)
  } finally {
    cartBusy.value = false
  }
}

async function checkoutCart(): Promise<void> {
  if (!canStartCartAction(t('catalog.previewCheckoutBlocked'))) return

  if (!cart.value?.items.length) {
    return
  }

  cartBusy.value = true
  try {
    cartSuccess.value = null
    cartResource.error.value = null
    const response = await studentApi.checkoutCart()
    if (!response.paymentAvailable) {
      cartResource.error.value = response.message || t('catalog.paymentUnavailable')
      return
    }

    const validatedUrl = safeHttpUrl(response.checkoutUrl)
    if (validatedUrl) {
      window.location.assign(validatedUrl)
      return
    }

    if (response.paymentId != null) {
      openPaymentSuccess(response.paymentId)
      return
    }

    cartResource.error.value = t('catalog.missingCheckoutReference')
  } catch (reason) {
    cartResource.error.value = resolveApiError(reason, t('catalog.checkoutFailed'))
  } finally {
    cartBusy.value = false
  }
}

function clearFilters(): void {
  cartSuccess.value = null
  filters.keyword = ''
  filters.sort = 'newest'
  filters.levelId = ''
  filters.categoryId = ''
  filters.learningType = ''
  filters.accessType = ''
}

function goToCoursePage(page: number): void {
  const lastPage = Math.max(0, courseTotalPages.value - 1)
  currentPage.value = Math.min(Math.max(page, 0), lastPage)
}

function scheduleCourseReload(): void {
  if (courseReloadTimer != null) window.clearTimeout(courseReloadTimer)
  courseReloadTimer = window.setTimeout(() => {
    void coursesResource.reload()
  }, 250)
}

function syncCatalogQuery(): void {
  const query: Record<string, string> = {}
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.sort !== 'newest') query.sort = filters.sort
  if (filters.levelId) query.levelId = filters.levelId
  if (filters.categoryId) query.categoryId = filters.categoryId
  if (filters.learningType) query.learningType = filters.learningType
  if (filters.accessType) query.accessType = filters.accessType
  if (currentPage.value > 0) query.page = String(currentPage.value)
  void router.replace({ query })
}

function queryText(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function validSort(value: string): CatalogSort {
  return value === 'title' ? 'title' : 'newest'
}

function validPositiveIdQuery(value: unknown): string {
  const text = queryText(value)
  return optionalPositiveId(text) == null ? '' : text
}

function optionalPositiveId(value: string): number | undefined {
  const numeric = Number(value)
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : undefined
}

function validEnumQuery<T extends string>(value: unknown, validValues: readonly T[]): T | '' {
  const text = queryText(value)
  return validValues.includes(text as T) ? (text as T) : ''
}

function validPageQuery(value: unknown): number {
  const numeric = Number(queryText(value))
  return Number.isSafeInteger(numeric) && numeric >= 0 ? numeric : 0
}

function toggleFilters(): void {
  filtersOpen.value = !filtersOpen.value
}

function openCourseDetail(courseId: string): void {
  void router.push(`/courses/${courseId}`)
}

function openPaymentSuccess(paymentId: number): void {
  void router.push({ name: 'payment-success', query: { paymentId: String(paymentId) } })
}

function goToAuth(): void {
  void router.push({ name: 'auth', query: { redirect: '/courses' } })
}

function goToAiHelperSignIn(): void {
  void router.push({ name: 'auth', query: { redirect: '/student/ai-helper' } })
}

function cartThumb(title: string): CartThumbVariant {
  if (title.includes('Mandarin')) return 'panda'
  if (title.includes('Everyday')) return 'family'
  return 'general'
}

function cartMetaTags(item: CartItemResponse): string[] {
  return [
    formatCartItemType(item.itemType),
    item.teacherOwned ? t('catalog.teacherContent') : t('catalog.platformContent'),
  ]
}

function courseMatchesFallbackFilters(course: PublicCourseSummary, keyword: string): boolean {
  if (keyword && !`${course.title} ${course.shortDescription ?? ''}`.toLowerCase().includes(keyword)) return false
  if (filters.levelId && String(course.level.id) !== filters.levelId) return false
  if (filters.categoryId && String(course.category.id) !== filters.categoryId) return false
  if (filters.learningType && course.learningType !== filters.learningType) return false
  if (filters.accessType && course.accessType !== filters.accessType) return false
  return true
}

function formatCartItemType(itemType: string): string {
  const keyByType: Record<string, string> = {
    LESSON: 'lessonType',
    MODULE: 'moduleType',
    PACKAGE: 'packageType',
  }
  const key = keyByType[itemType]
  if (key) return t(`catalog.${key}`)
  return itemType
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}
</script>
