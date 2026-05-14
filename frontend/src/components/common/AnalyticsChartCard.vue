<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useI18n } from 'vue-i18n'

import type { ChartOption } from '../../utils/analytics'

// Register only the chart types and components actually used in the project,
// so the tree-shaken `echarts/core` build is roughly half the size of the
// monolithic `echarts` import.
use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TitleComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    option?: ChartOption | null
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
// Use the return type of `init` directly so the ref aligns with whatever the
// modular echarts/core build exposes (avoids private-field clashes between
// `ECharts` and `EChartsType`).
const chart = shallowRef<ReturnType<typeof init> | null>(null)
let resizeObserver: ResizeObserver | null = null

function handleResize() {
  chart.value?.resize()
}

async function renderChart() {
  await nextTick()

  // When the chart slot is replaced by the loading skeleton / error alert /
  // empty placeholder, the underlying <div> is removed by v-else and the
  // ECharts instance is left holding a detached canvas. Disposing here
  // releases that canvas so the next time we re-enter the chart branch we
  // construct a brand-new instance against the fresh element. Without
  // this dispose, switching loading=false → loading=true → loading=false
  // leaks one chart instance per cycle and the second render can throw
  // because setOption() fires against the orphaned DOM node.
  if (!chartElement.value || props.loading || props.error || props.empty || !props.option) {
    chart.value?.dispose()
    chart.value = null
    return
  }

  if (!chart.value) {
    chart.value = init(chartElement.value)
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
