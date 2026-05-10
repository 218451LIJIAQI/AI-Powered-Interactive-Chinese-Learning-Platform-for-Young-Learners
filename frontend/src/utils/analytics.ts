import type { EChartsOption } from 'echarts'

import type { AnalyticsBreakdownItem, AnalyticsTrendPoint } from '../types/analytics'

type Translator = (key: string, named?: Record<string, unknown>) => string
type AxisTooltipPoint = {
  axisValueLabel?: string
  value?: unknown
}
type PieTooltipPoint = {
  name?: string
  value?: unknown
}

const enumLabelMap: Record<string, string> = {
  ACTION_TAKEN: 'analytics.enums.ACTION_TAKEN',
  APPROVED: 'analytics.enums.APPROVED',
  ARCHIVED: 'analytics.enums.ARCHIVED',
  CANCELLED: 'analytics.enums.CANCELLED',
  CLOSED: 'analytics.enums.CLOSED',
  COMPLETED: 'analytics.enums.COMPLETED',
  DRAFT: 'analytics.enums.DRAFT',
  DISMISSED: 'analytics.enums.DISMISSED',
  FAILED: 'analytics.enums.FAILED',
  FLAGGED: 'analytics.enums.FLAGGED',
  HIDDEN: 'analytics.enums.HIDDEN',
  HYBRID: 'analytics.enums.HYBRID',
  IN_PROGRESS: 'analytics.enums.IN_PROGRESS',
  LIVE: 'analytics.enums.LIVE',
  OPEN: 'analytics.enums.OPEN',
  PAID: 'analytics.enums.PAID',
  PENDING: 'analytics.enums.PENDING',
  PUBLISHED: 'analytics.enums.PUBLISHED',
  RECORDED: 'analytics.enums.RECORDED',
  REFUNDED: 'analytics.enums.REFUNDED',
  REJECTED: 'analytics.enums.REJECTED',
  RESOLVED: 'analytics.enums.RESOLVED',
  REVIEWED: 'analytics.enums.REVIEWED',
  SUBMITTED: 'analytics.enums.SUBMITTED',
  SUCCESS: 'analytics.enums.SUCCESS',
  UNKNOWN: 'analytics.enums.UNKNOWN',
  VISIBLE: 'analytics.enums.VISIBLE',
}

function cssVar(name: string, fallback: string) {
  if (typeof window === 'undefined') {
    return fallback
  }

  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function chartPalette() {
  return [
    cssVar('--primary', '#ff8f42'),
    cssVar('--secondary', '#3bb3c3'),
    cssVar('--teacher-accent', '#567eff'),
    cssVar('--admin-accent', '#22c55e'),
    '#f59e0b',
    '#ec4899',
  ]
}

function axisColor() {
  return cssVar('--text-secondary', '#5d7186')
}

function splitLineColor() {
  return cssVar('--border-color', 'rgba(23, 50, 77, 0.12)')
}

function gridColor() {
  return splitLineColor()
}

export function formatAnalyticsKey(key: string) {
  return key
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function translateAnalyticsKey(key: string, t: Translator) {
  if (/^\d+$/.test(key)) {
    return t('analytics.ratingBucket', { count: Number(key) })
  }

  const translationKey = enumLabelMap[key]
  return translationKey ? t(translationKey) : formatAnalyticsKey(key)
}

export function normalizeBreakdown(items: AnalyticsBreakdownItem[], desiredKeys: string[]) {
  const valueByKey = new Map(items.map((item) => [item.key, item.value]))
  return desiredKeys.map((key) => ({
    key,
    value: valueByKey.get(key) ?? 0,
  }))
}

export function hasTrendData(points: AnalyticsTrendPoint[]) {
  return points.some((point) => Number(point.value) > 0)
}

export function hasBreakdownData(items: AnalyticsBreakdownItem[]) {
  return items.some((item) => item.value > 0)
}

export function buildLineChartOption(
  points: AnalyticsTrendPoint[],
  t: Translator,
  options?: {
    color?: string
    seriesName?: string
    valueSuffix?: string
    decimals?: number
    area?: boolean
  },
): EChartsOption {
  const palette = chartPalette()
  const lineColor = options?.color ?? palette[1]
  const decimals = options?.decimals ?? 0

  return {
    color: [lineColor],
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const point = (Array.isArray(params) ? params[0] : params) as AxisTooltipPoint | undefined
        const value = Number(point?.value ?? 0)
        const suffix = options?.valueSuffix ?? ''
        return `${point?.axisValueLabel ?? ''}<br/>${options?.seriesName ?? t('analytics.series.value')}: ${value.toFixed(decimals)}${suffix}`
      },
    },
    grid: {
      left: 12,
      right: 12,
      bottom: 8,
      top: 24,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.label),
      axisLine: { lineStyle: { color: gridColor() } },
      axisLabel: { color: axisColor() },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: axisColor(),
        formatter: (value: number) => `${value}${options?.valueSuffix ?? ''}`,
      },
      splitLine: { lineStyle: { color: splitLineColor() } },
    },
    series: [
      {
        name: options?.seriesName ?? t('analytics.series.value'),
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: points.map((point) => Number(point.value)),
        lineStyle: {
          width: 3,
        },
        itemStyle: {
          color: lineColor,
        },
        areaStyle: options?.area
          ? {
              opacity: 0.16,
              color: lineColor,
            }
          : undefined,
      },
    ],
  }
}

export function buildBarChartOption(
  points: AnalyticsTrendPoint[],
  t: Translator,
  options?: {
    color?: string
    seriesName?: string
    valueSuffix?: string
    decimals?: number
  },
): EChartsOption {
  const palette = chartPalette()
  const barColor = options?.color ?? palette[2]
  const decimals = options?.decimals ?? 0

  return {
    color: [barColor],
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const point = (Array.isArray(params) ? params[0] : params) as AxisTooltipPoint | undefined
        const value = Number(point?.value ?? 0)
        const suffix = options?.valueSuffix ?? ''
        return `${point?.axisValueLabel ?? ''}<br/>${options?.seriesName ?? t('analytics.series.value')}: ${value.toFixed(decimals)}${suffix}`
      },
    },
    grid: {
      left: 12,
      right: 12,
      bottom: 8,
      top: 24,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.label),
      axisLine: { lineStyle: { color: gridColor() } },
      axisLabel: { color: axisColor() },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: axisColor(),
        formatter: (value: number) => `${value}${options?.valueSuffix ?? ''}`,
      },
      splitLine: { lineStyle: { color: splitLineColor() } },
    },
    series: [
      {
        name: options?.seriesName ?? t('analytics.series.value'),
        type: 'bar',
        barWidth: '48%',
        itemStyle: {
          borderRadius: [10, 10, 2, 2],
          color: barColor,
        },
        data: points.map((point) => Number(point.value)),
      },
    ],
  }
}

export function buildDonutChartOption(
  items: AnalyticsBreakdownItem[],
  t: Translator,
  options?: {
    centerLabel?: string
  },
): EChartsOption {
  const palette = chartPalette()

  return {
    color: palette,
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const point = params as PieTooltipPoint
        return `${point.name ?? ''}: ${point.value ?? 0}`
      },
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      textStyle: {
        color: axisColor(),
      },
    },
    series: [
      {
        name: options?.centerLabel ?? t('analytics.series.distribution'),
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['50%', '46%'],
        label: {
          formatter: '{b}',
          color: axisColor(),
        },
        labelLine: {
          length: 12,
          length2: 10,
        },
        data: items.map((item) => ({
          name: translateAnalyticsKey(item.key, t),
          value: item.value,
        })),
      },
    ],
  }
}

export function currency(value: number) {
  return `RM ${Number(value ?? 0).toFixed(2)}`
}
