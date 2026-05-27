<template>
  <div class="public-page public-catalog-page">
    <DataNotice
      :using-fallback="catalogUsingFallback"
      :error="catalogNoticeError"
      :success="cartSuccess"
      fallback-title="Preview catalog mode"
      fallback-message="You are browsing safe preview data for courses, filters, and cart flow. Sign in to use live student checkout."
      error-title="Catalog needs attention"
      success-title="Cart updated"
      :detail="catalogNoticeDetail"
    />

    <section class="public-catalog-shell">
      <div class="public-catalog-main">
        <div class="public-catalog-header">
          <div class="public-catalog-header-copy">
            <span class="public-catalog-kicker">Find the right Mandarin path</span>
            <h1>Explore Courses</h1>
            <p>
              Compare beginner-friendly lessons, teacher-led support, and kid-safe formats designed for real family
              decision-making.
            </p>
          </div>

          <div class="public-catalog-header-meta">
            <article>
              <strong>{{ matchedCourseCount }}</strong>
              <span>courses matched</span>
            </article>
            <article>
              <strong>{{ categories.length }}</strong>
              <span>learning themes</span>
            </article>
            <article>
              <strong>{{ cart?.items.length ?? 0 }}</strong>
              <span>in your cart</span>
            </article>
          </div>
        </div>

        <section class="public-catalog-search">
          <div class="public-search-row">
            <label class="public-search-input">
              <IconGlyph name="search" :size="18" />
              <input v-model="filters.keyword" type="text" placeholder="Search courses" />
            </label>
            <button
              class="btn btn-primary public-filter-button"
              type="button"
              :aria-expanded="filtersOpen"
              @click="filtersOpen = !filtersOpen"
            >
              {{ filtersOpen ? 'Hide filters' : 'Show filters' }}
            </button>
            <button class="public-clear-button" type="button" @click="clearFilters">Clear All</button>
          </div>

          <div v-show="filtersOpen" class="public-filter-grid">
            <label class="public-filter-field">
              <span>Category</span>
              <select v-model="filters.categoryId">
                <option value="">All Categories</option>
                <option v-for="category in categories" :key="category.id" :value="String(category.id)">
                  {{ category.name }}
                </option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>Level</span>
              <select v-model="filters.levelId">
                <option value="">All Levels</option>
                <option v-for="level in levels" :key="level.id" :value="String(level.id)">{{ level.name }}</option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>Activity Type</span>
              <select v-model="filters.learningType">
                <option value="">All Activities</option>
                <option value="RECORDED">Recorded</option>
                <option value="LIVE">Live Support</option>
                <option value="HYBRID">Hybrid</option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>Format</span>
              <select v-model="filters.accessType">
                <option value="">All Formats</option>
                <option value="FREE">Free</option>
                <option value="PAID">Paid</option>
              </select>
            </label>

            <label class="public-filter-field">
              <span>Sort by</span>
              <select v-model="filters.sort">
                <option value="newest">Recently added</option>
                <option value="title">Title</option>
              </select>
            </label>
          </div>
        </section>

        <section class="public-catalog-feature-strip">
          <PublicFeatureCard
            icon="safe"
            title="Kid-Safe & Ad-Free"
            description="Safe learning environment"
            tone="purple"
          />
          <PublicFeatureCard
            icon="teacher"
            title="Expert Teachers"
            description="Certified and experienced"
            tone="blue"
          />
          <PublicFeatureCard icon="fun" title="Fun & Engaging" description="Games, stories, and practice" tone="gold" />
          <PublicFeatureCard
            icon="progress"
            title="Progress You Can See"
            description="Track growth step by step"
            tone="purple"
          />
        </section>

        <section class="public-course-list">
          <StatePanel
            v-if="!filteredCourses.length"
            icon="courses"
            title="No courses matched your filters"
            message="Try clearing one filter or searching for beginner Mandarin, pinyin, culture, or live support."
            eyebrow="No matches"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="clearFilters">Clear filters</button>
            </template>
          </StatePanel>

          <PublicCourseShowcaseCard
            v-for="course in filteredCourses"
            :key="course.id"
            :course="course"
            :action-label="courseActionLabel(course)"
            @view="router.push(`/courses/${course.id}`)"
            @action="handleCourseAction(course)"
          />

          <nav v-if="courseTotalPages > 1" class="public-catalog-pagination" aria-label="Course result pages">
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="currentPage <= 0 || coursesResource.loading.value"
              @click="goToCoursePage(currentPage - 1)"
            >
              Previous
            </button>
            <span>Page {{ currentPage + 1 }} of {{ courseTotalPages }}</span>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="currentPage >= courseTotalPages - 1 || coursesResource.loading.value"
              @click="goToCoursePage(currentPage + 1)"
            >
              Next
            </button>
          </nav>
        </section>
      </div>

      <aside class="public-catalog-side">
        <div v-if="canUseCart" class="public-cart-card">
          <div class="public-cart-header">
            <strong>My Course Cart ({{ cart?.items.length ?? 0 }})</strong>
            <button v-if="cart?.items.length" class="public-clear-button" type="button" @click="clearCart">
              Clear
            </button>
          </div>

          <p class="public-cart-note">{{ cartNote }}</p>

          <div class="public-cart-items">
            <p v-if="!cart?.items.length" class="muted public-cart-empty">Your cart is empty.</p>
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
                  Remove
                </button>
              </div>
            </div>
          </div>

          <div class="public-cart-summary">
            <div class="row-between">
              <span>Subtotal</span>
              <strong v-if="cart">{{ formatCurrency(cart.totalAmount, cart.currencyCode) }}</strong>
              <span v-else class="muted">--</span>
            </div>
            <div class="row-between public-cart-total">
              <span>Total</span>
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
            <span>Info</span>
            <span>{{ cartActionHint }}</span>
          </div>
        </div>

        <div v-else class="panel stack public-catalog-signin-card">
          <strong>{{ cartAccessTitle }}</strong>
          <p class="muted public-cart-empty">
            {{ cartAccessDescription }}
          </p>
          <button v-if="!auth.isAuthenticated" class="btn btn-primary" type="button" @click="goToAuth">Sign in</button>
        </div>

        <PublicInfoBanner
          icon="safe"
          title="Safe. Positive. Effective."
          description="Designed for young learners and trusted by parents."
          tone="green"
        />

        <div class="public-help-card">
          <div>
            <strong>Need help choosing?</strong>
            <p>Try our AI Helper for personalized recommendations.</p>
          </div>
          <button class="btn btn-secondary" type="button" @click="goToAiHelperSignIn">Ask AI Helper</button>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Unauthenticated course catalogue — the main discovery surface.
 *
 * <h2>Filters</h2>
 * <p>Search keyword, learning level, category, access type
 * (free / paid), learning type (live / recorded / hybrid) and sort
 * order are all mirrored into the URL via Vue Router so a filtered
 * view can be shared / bookmarked. Reactive {@code watch} on the
 * filter object debounces requests so each keystroke does not
 * spam the backend.</p>
 *
 * <h2>Pagination</h2>
 * <p>Hard-clamped page size so a malicious caller cannot ask for
 * 10 000 rows at once — the actual cap is enforced server-side by
 * {@code PaginationUtils}.</p>
 *
 * <h2>CTA behaviour</h2>
 * <p>"View details" routes to {@link CourseDetailPage}; paid
 * "Enroll" routes through the auth flow first if the visitor is
 * unauthenticated, then through checkout once signed in.</p>
 */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

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
import { useAuthStore } from '@/stores/auth'
import type { CartItemResponse, CartResponse, LearningCategory, LearningLevel, PublicCourseSummary } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatCurrency } from '@/utils/format'
import { safeHttpUrl } from '@/utils/url'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const filters = reactive({
  keyword: queryText(route.query.keyword),
  sort: validSort(queryText(route.query.sort)),
  levelId: validPositiveIdQuery(route.query.levelId),
  categoryId: validPositiveIdQuery(route.query.categoryId),
  learningType: validEnumQuery(route.query.learningType, ['RECORDED', 'LIVE', 'HYBRID']),
  accessType: validEnumQuery(route.query.accessType, ['FREE', 'PAID']),
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
    return canUseCart.value
      ? 'Course browsing still works while cart or catalog requests recover.'
      : 'Public visitors can still review course information and sign-in options.'
  }

  if (catalogUsingFallback.value) {
    return canUseCart.value
      ? 'Cart actions are limited while preview data is active.'
      : 'Live cart and enrolment open after student sign-in.'
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
    .filter((course) => {
      if (keyword && !`${course.title} ${course.shortDescription ?? ''}`.toLowerCase().includes(keyword)) return false
      if (filters.levelId && String(course.level.id) !== filters.levelId) return false
      if (filters.categoryId && String(course.category.id) !== filters.categoryId) return false
      if (filters.learningType && course.learningType !== filters.learningType) return false
      if (filters.accessType && course.accessType !== filters.accessType) return false
      return true
    })
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
  if (!canUseCart.value) return 'Sign in to checkout'
  if (cartResource.loading.value) return 'Loading cart...'
  if (cartResource.usingFallback.value) return 'Preview cart only'
  if (!cart.value?.items.length) return 'Cart empty'
  return 'Checkout'
})

const cartActionDisabled = computed(
  () =>
    !isStudentShopper.value ||
    cartResource.loading.value ||
    cartResource.usingFallback.value ||
    !cart.value?.items.length ||
    Boolean(cartResource.error.value),
)

const cartNote = computed(() => {
  if (!canUseCart.value) return 'Sign in as a student to use the live cart.'
  if (cartResource.usingFallback.value) return 'Preview mode is showing demo cart data.'
  if (cartResource.error.value) return `Cart unavailable: ${cartResource.error.value}`
  return 'Your cart is synced with the live backend.'
})

const cartActionHint = computed(() => {
  if (!canUseCart.value) return 'Live cart and checkout are available after sign-in.'
  if (cartResource.usingFallback.value) return 'Preview data is read-only.'
  return 'Checkout redirects to the payment page and returns here on success.'
})

const cartAccessTitle = computed(() =>
  signedInWithoutStudentRole.value ? 'Student checkout only' : 'Use the live cart',
)
const cartAccessDescription = computed(() =>
  signedInWithoutStudentRole.value
    ? 'You can browse public course information, but purchases and the live cart are available only to student accounts.'
    : 'Sign in as a student to save courses, manage checkout, and return to lessons after payment.',
)

function courseActionLabel(course: PublicCourseSummary) {
  if (signedInWithoutStudentRole.value) return 'View course'
  if (!canUseCart.value) return 'Sign in'
  if (auth.isPreviewSession && !auth.isAuthenticated) return 'Preview'
  if (course.accessType === 'FREE') return 'View course'
  return 'Add to cart'
}

function handleCourseAction(course: PublicCourseSummary) {
  if (signedInWithoutStudentRole.value) {
    router.push(`/courses/${course.id}`)
    return
  }

  if (!canUseCart.value) {
    goToAuth()
    return
  }

  if (auth.isPreviewSession && !auth.isAuthenticated) {
    router.push(`/courses/${course.id}`)
    return
  }

  if (course.accessType === 'FREE') {
    router.push(`/courses/${course.id}`)
    return
  }

  void addCourseToCart(course)
}

async function addCourseToCart(course: PublicCourseSummary) {
  if (!isStudentShopper.value) {
    if (signedInWithoutStudentRole.value) return
    goToAuth()
    return
  }

  if (cartBusy.value) return

  if (cartResource.usingFallback.value) {
    cartSuccess.value = null
    cartResource.error.value = 'Cart preview data is read-only. Refresh live cart data before changing it.'
    return
  }

  cartBusy.value = true
  try {
    cartSuccess.value = null
    cartResource.error.value = null
    await studentApi.addCartItem({ itemType: course.type, itemId: course.contentId })
    await cartResource.reload()
    cartSuccess.value = `${course.title} was added to your cart.`
  } catch (reason) {
    cartSuccess.value = null
    cartResource.error.value = resolveApiError(reason, 'Unable to add this course to the cart.')
  } finally {
    cartBusy.value = false
  }
}

async function removeCartItem(cartItemId: number) {
  if (!isStudentShopper.value) {
    if (signedInWithoutStudentRole.value) return
    goToAuth()
    return
  }

  if (cartBusy.value) return

  if (cartResource.usingFallback.value) {
    cartSuccess.value = null
    cartResource.error.value = 'Cart preview data is read-only. Refresh live cart data before changing it.'
    return
  }

  cartBusy.value = true
  try {
    cartSuccess.value = null
    cartResource.error.value = null
    await studentApi.removeCartItem(cartItemId)
    await cartResource.reload()
    cartSuccess.value = 'The course was removed from your cart.'
  } catch (reason) {
    cartSuccess.value = null
    cartResource.error.value = resolveApiError(reason, 'Unable to remove this item right now.')
  } finally {
    cartBusy.value = false
  }
}

async function clearCart() {
  if (!isStudentShopper.value) {
    if (signedInWithoutStudentRole.value) return
    goToAuth()
    return
  }

  if (cartBusy.value) return

  if (cartResource.usingFallback.value) {
    cartSuccess.value = null
    cartResource.error.value = 'Cart preview data is read-only. Refresh live cart data before changing it.'
    return
  }

  cartBusy.value = true
  try {
    cartSuccess.value = null
    cartResource.error.value = null
    await studentApi.clearCart()
    await cartResource.reload()
    cartSuccess.value = 'Your cart is now empty.'
  } catch (reason) {
    cartSuccess.value = null
    cartResource.error.value = resolveApiError(reason, 'Unable to clear your cart right now.')
  } finally {
    cartBusy.value = false
  }
}

async function checkoutCart() {
  if (!isStudentShopper.value) {
    if (signedInWithoutStudentRole.value) return
    goToAuth()
    return
  }

  if (cartBusy.value) return

  if (cartResource.usingFallback.value) {
    cartSuccess.value = null
    cartResource.error.value = 'Cart preview data cannot be checked out. Refresh live cart data first.'
    return
  }

  if (!cart.value?.items.length) {
    return
  }

  cartBusy.value = true
  try {
    cartSuccess.value = null
    cartResource.error.value = null
    const response = await studentApi.checkoutCart()
    if (!response.paymentAvailable) {
      cartResource.error.value = response.message || 'Payment provider is unavailable. Please try again later.'
      return
    }

    const validatedUrl = safeHttpUrl(response.checkoutUrl)
    if (validatedUrl) {
      window.location.assign(validatedUrl)
      return
    }

    if (response.paymentId != null) {
      void router.push({ name: 'payment-success', query: { paymentId: String(response.paymentId) } })
      return
    }

    cartResource.error.value =
      'Checkout completed but no redirect or payment reference was returned. Please contact support.'
  } catch (reason) {
    cartResource.error.value = resolveApiError(reason, 'Unable to start checkout.')
  } finally {
    cartBusy.value = false
  }
}

function clearFilters() {
  cartSuccess.value = null
  filters.keyword = ''
  filters.sort = 'newest'
  filters.levelId = ''
  filters.categoryId = ''
  filters.learningType = ''
  filters.accessType = ''
}

function goToCoursePage(page: number) {
  const lastPage = Math.max(0, courseTotalPages.value - 1)
  currentPage.value = Math.min(Math.max(page, 0), lastPage)
}

function scheduleCourseReload() {
  if (courseReloadTimer != null) window.clearTimeout(courseReloadTimer)
  courseReloadTimer = window.setTimeout(() => {
    void coursesResource.reload()
  }, 250)
}

function syncCatalogQuery() {
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

function queryText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function validSort(value: string) {
  return value === 'title' ? 'title' : 'newest'
}

function validPositiveIdQuery(value: unknown) {
  const text = queryText(value)
  return optionalPositiveId(text) == null ? '' : text
}

function optionalPositiveId(value: string) {
  const numeric = Number(value)
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : undefined
}

function validEnumQuery(value: unknown, validValues: readonly string[]) {
  const text = queryText(value)
  return validValues.includes(text) ? text : ''
}

function validPageQuery(value: unknown) {
  const numeric = Number(queryText(value))
  return Number.isSafeInteger(numeric) && numeric >= 0 ? numeric : 0
}

function goToAuth() {
  router.push({ name: 'auth', query: { redirect: '/courses' } })
}

function goToAiHelperSignIn() {
  router.push({ name: 'auth', query: { redirect: '/student/ai-helper' } })
}

function cartThumb(title: string) {
  if (title.includes('Mandarin')) return 'panda'
  if (title.includes('Everyday')) return 'family'
  return 'general'
}

function cartMetaTags(item: CartItemResponse) {
  return [formatCartItemType(item.itemType), item.teacherOwned ? 'Teacher content' : 'Platform content']
}

function formatCartItemType(itemType: string) {
  return itemType
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}
</script>
