<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    option?: EChartsOption | null
    loading?: boolean
    error?: string
    empty?: boolean
    emptyDescription?: string
    height?: string
  }>(),
  {
    subtitle: '',
    option: null,
    loading: false,
    error: '',
    empty: false,
    emptyDescription: '',
    height: '320px',
  },
)

const { t } = useI18n()
const chartElement = ref<HTMLDivElement | null>(null)
const chart = shallowRef<ECharts | null>(null)
let resizeObserver: ResizeObserver | null = null

function handleResize() {
  chart.value?.resize()
}

async function renderChart() {
  await nextTick()

  if (!chartElement.value || props.loading || props.error || props.empty || !props.option) {
    chart.value?.clear()
    return
  }

  if (!chart.value) {
    chart.value = echarts.init(chartElement.value)
  }

  chart.value.setOption(props.option, true)
  chart.value.resize()
}

onMounted(async () => {
  await renderChart()

  if (typeof ResizeObserver !== 'undefined' && chartElement.value) {
    resizeObserver = new ResizeObserver(() => handleResize())
    resizeObserver.observe(chartElement.value)
  }

  window.addEventListener('resize', handleResize)
})

watch(
  () => [props.option, props.loading, props.error, props.empty],
  async () => {
    await renderChart()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', handleResize)
  chart.value?.dispose()
  chart.value = null
})
</script>

<template>
  <el-card class="dashboard-panel analytics-panel" shadow="hover">
    <div class="panel-header">
      <div>
        <p class="section-label">{{ t('analytics.sectionLabel') }}</p>
        <h2>{{ title }}</h2>
        <p v-if="subtitle" class="analytics-panel__subtitle">{{ subtitle }}</p>
      </div>
    </div>

    <el-skeleton v-if="loading" animated :rows="6" />
    <el-alert v-else-if="error" :title="error" type="warning" show-icon :closable="false" />
    <el-empty
      v-else-if="empty"
      :description="emptyDescription || t('analytics.emptyMessage')"
      class="analytics-panel__empty"
    />
    <div v-else ref="chartElement" class="analytics-chart" :style="{ height }" />
  </el-card>
</template>
