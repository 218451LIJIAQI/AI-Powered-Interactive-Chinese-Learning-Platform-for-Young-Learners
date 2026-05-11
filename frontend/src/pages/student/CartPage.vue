<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import type { Cart } from '../../types/commerce'
import { extractApiError } from '../../utils/apiError'
import { formatMoney } from '../../utils/money'

const { t } = useI18n()
const translateEnum = useEnumLabel()

const loading = ref(true)
const checkingOut = ref(false)
const cart = ref<Cart | null>(null)

const hasItems = computed(() => (cart.value?.items.length ?? 0) > 0)

async function loadCart() {
  loading.value = true
  try {
    cart.value = await commerceService.getCart()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    loading.value = false
  }
}

async function removeItem(cartItemId: number) {
  try {
    cart.value = await commerceService.removeCartItem(cartItemId)
    ElMessage.success(t('commerce.messages.cartUpdated'))
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  }
}

async function clearCart() {
  try {
    cart.value = await commerceService.clearCart()
    ElMessage.success(t('commerce.messages.cartCleared'))
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  }
}

async function checkoutCart() {
  checkingOut.value = true
  try {
    const result = await commerceService.checkoutCart()
    if (result.checkoutUrl) {
      window.location.assign(result.checkoutUrl)
      return
    }
    ElMessage.warning(result.message)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    checkingOut.value = false
  }
}

onMounted(() => {
  loadCart()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero :eyebrow="t('commerce.cartEyebrow')" :title="t('commerce.cartTitle')" :description="t('commerce.cartDescription')" />

    <el-card class="dashboard-panel" shadow="hover">
      <el-skeleton v-if="loading" animated :rows="6" />
      <template v-else>
        <el-empty v-if="!hasItems" :description="t('commerce.emptyCart')" />
        <template v-else>
          <el-table :data="cart?.items ?? []" class="phase-table">
            <el-table-column prop="title" :label="t('commerce.item')" min-width="220" />
            <el-table-column :label="t('commerce.itemType')" min-width="120">
              <template #default="{ row }">{{ translateEnum('managedContentType', row.itemType) }}</template>
            </el-table-column>
            <el-table-column :label="t('commerce.price')" min-width="120">
              <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column :label="t('commerce.subtotal')" min-width="120">
              <template #default="{ row }">{{ formatMoney(row.subtotal) }}</template>
            </el-table-column>
            <el-table-column min-width="140">
              <template #default="{ row }">
                <el-button type="danger" plain @click="removeItem(row.cartItemId)">{{ t('commerce.remove') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="cart-summary">
            <div>
              <strong>{{ t('commerce.total') }}: {{ formatMoney(cart?.totalAmount ?? 0) }}</strong>
            </div>
            <div class="table-actions">
              <el-button plain @click="clearCart">{{ t('commerce.clearCart') }}</el-button>
              <el-button type="primary" :loading="checkingOut" @click="checkoutCart">{{ t('commerce.checkout') }}</el-button>
            </div>
          </div>
        </template>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.cart-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 1rem;
}
</style>
