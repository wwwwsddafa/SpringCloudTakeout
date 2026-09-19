<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardStats, getDailyStats, getTrendStats, getProductRanking, type DashboardStats, type DailyStats, type TrendStats, type ProductRanking } from '@/api/ops'
import AdminLayout from './AdminLayout.vue'
import { getErrorMsg } from '@/utils/error'

const dashboard = ref<DashboardStats | null>(null)
const daily = ref<DailyStats | null>(null)
const trend = ref<TrendStats | null>(null)
const loading = ref(false)
const error = ref('')
const selectedDate = ref(new Date().toISOString().split('T')[0])
const trendDays = ref(7)
const ranking = ref<ProductRanking | null>(null)
const rankingTopN = ref(10)
const rankingDate = ref<string>(new Date().toISOString().split('T')[0])

const pageTypeMap: Record<string, string> = {
  HOME: '首页',
  PRODUCT_LIST: '商品列表',
  PRODUCT_DETAIL: '商品详情',
  CART: '购物车',
  ORDER: '下单结算',
  ORDER_DETAIL: '订单详情',
  USER_CENTER: '个人中心',
  SEARCH: '搜索页',
  OTHER: '其他',
}

const statCards = computed(() => {
  if (!dashboard.value) return []
  return [
    { label: '浏览量', value: formatNumber(dashboard.value.totalPv), desc: '页面总访问次数', color: '#4a90e2' },
    { label: '访客数', value: formatNumber(dashboard.value.totalUv), desc: '独立访客数量', color: '#50c878' },
    { label: '订单数', value: formatNumber(dashboard.value.totalOrders), desc: '总订单量', color: '#ff6b35' },
    { label: '营业额', value: '¥' + formatMoney(dashboard.value.totalRevenue), desc: '总营收金额', color: '#e74c3c' },
    { label: '下单转化率', value: (dashboard.value.conversionRate ?? 0) + '%', desc: '访客→下单', color: '#9b59b6' },
    { label: '笔单价', value: '¥' + formatMoney(dashboard.value.avgOrderAmount), desc: '平均每单金额', color: '#f39c12' },
    { label: '活跃用户', value: formatNumber(dashboard.value.activeUsers), desc: '近期有操作的用户', color: '#1abc9c' },
    { label: '新用户', value: formatNumber(dashboard.value.newUsers), desc: '今日新增注册', color: '#3498db' },
  ]
})

function formatNumber(n: number | undefined | null): string {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  if (n >= 1000) return (n / 1000).toFixed(1) + '千'
  return String(n)
}

function formatMoney(n: number | undefined | null): string {
  if (n == null) return '0.00'
  return n.toFixed(2)
}

async function fetchDashboard() {
  try {
    const res = await getDashboardStats()
    dashboard.value = res.data.data
    console.log('[OpsStats] dashboard 数据:', JSON.stringify(dashboard.value))
    console.log('[OpsStats] deviceDistribution:', JSON.stringify(dashboard.value?.deviceDistribution))
  } catch (err: any) {
    const msg = getErrorMsg(err, '获取运营大盘失败')
    console.error('获取运营大盘失败:', msg, err)
    throw new Error(msg)
  }
}

async function fetchDaily() {
  if (!selectedDate.value) return
  try {
    const res = await getDailyStats(selectedDate.value)
    daily.value = res.data.data
  } catch (err: any) {
    const msg = getErrorMsg(err, '获取每日统计失败')
    console.error('获取每日统计失败:', msg, err)
    throw new Error(msg)
  }
}

async function fetchTrend() {
  try {
    const res = await getTrendStats(trendDays.value)
    trend.value = res.data.data
    console.log('[OpsStats] trend 数据已加载:', JSON.stringify(trend.value))
  } catch (err: any) {
    trend.value = null
    const msg = getErrorMsg(err, '获取趋势数据失败')
    console.error('获取趋势数据失败:', msg, err)
    throw new Error(msg)
  }
}

async function fetchRanking() {
  try {
    const res = await getProductRanking(rankingDate.value, rankingTopN.value)
    ranking.value = res.data.data
  } catch (err: any) {
    const msg = getErrorMsg(err, '获取排行榜失败')
    console.error('获取排行榜失败:', msg, err)
    throw new Error(msg)
  }
}

async function fetchAll() {
  loading.value = true
  error.value = ''
  const timeoutPromise = new Promise((_, reject) => {
    setTimeout(() => reject(new Error('请求超时，请检查网络连接')), 10000)
  })
  try {
    await Promise.race([
      Promise.all([fetchDashboard(), fetchDaily(), fetchTrend(), fetchRanking()]),
      timeoutPromise
    ])
  } catch (err: any) {
    error.value = err.message || '加载数据失败，请检查后端服务是否正常运行'
  } finally {
    loading.value = false
    nextTick(() => {
      renderDeviceChart()
      renderTrendCharts()
    })
  }
}

function onDateChange() {
  fetchDaily()
}

async function onTrendDaysChange() {
  await fetchTrend()
  nextTick(() => {
    renderTrendCharts()
  })
}

const chartPvRef = ref<HTMLDivElement>()
const chartOrderRef = ref<HTMLDivElement>()
const chartRevenueRef = ref<HTMLDivElement>()
const chartDeviceRef = ref<HTMLDivElement>()
const chartDeviceTrendRef = ref<HTMLDivElement>()
let chartPvInst: echarts.ECharts | null = null
let chartOrderInst: echarts.ECharts | null = null
let chartRevenueInst: echarts.ECharts | null = null
let chartDeviceInst: echarts.ECharts | null = null
let chartDeviceTrendInst: echarts.ECharts | null = null

function buildLineChart(dom: HTMLDivElement, dataList: { date: string; value: number }[], color: string) {
  const chart = echarts.init(dom)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dataList.map(d => d.date) },
    yAxis: { type: 'value' },
    series: [{
      type: 'line',
      smooth: true,
      data: dataList.map(d => d.value),
      label: { show: true, position: 'top', formatter: '{c}' },
      itemStyle: { color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color },
          { offset: 1, color: 'rgba(255,255,255,0)' }
        ])
      }
    }]
  })
  return chart
}

const deviceColorMap: Record<string, string> = {
  mobile: '#1890ff',
  desktop: '#52c41a',
  tablet: '#fa8c16',
  ios: '#eb2f96',
  android: '#722ed1',
  wechat: '#07c160',
  other: '#bfbfbf',
}

const deviceNameMap: Record<string, string> = {
  mobile: '手机端',
  desktop: '电脑端',
  tablet: '平板',
  ios: 'iOS设备',
  android: '安卓设备',
  wechat: '微信内置',
  other: '其他',
}

function renderDeviceChart() {
  if (!dashboard.value?.deviceDistribution || !chartDeviceRef.value) return
  const dd = dashboard.value.deviceDistribution
  const data = Object.entries(dd)
    .map(([key, value]) => ({
      name: deviceNameMap[key] || key,
      value: value || 0,
      itemStyle: { color: deviceColorMap[key] || '#999' },
    }))
    .filter(d => d.value > 0)
  if (chartDeviceInst) { chartDeviceInst.dispose(); chartDeviceInst = null }
  chartDeviceInst = echarts.init(chartDeviceRef.value)
  chartDeviceInst.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 10 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{c} ({d}%)' },
      data,
    }],
  })
}

function renderDeviceTrendChart() {
  if (!trend.value?.deviceTrend?.length || !chartDeviceTrendRef.value) return
  const dates = trend.value.deviceTrend.map(d => d.date)
  if (chartDeviceTrendInst) { chartDeviceTrendInst.dispose(); chartDeviceTrendInst = null }
  chartDeviceTrendInst = echarts.init(chartDeviceTrendRef.value)
  chartDeviceTrendInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['手机端', '电脑端', '平板'], bottom: 10 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [
      {
        name: '手机端',
        type: 'line',
        smooth: true,
        data: trend.value.deviceTrend.map(d => d.mobile || 0),
        itemStyle: { color: '#1890ff' },
        label: { show: true, position: 'top', formatter: '{c}' },
      },
      {
        name: '电脑端',
        type: 'line',
        smooth: true,
        data: trend.value.deviceTrend.map(d => d.desktop || 0),
        itemStyle: { color: '#52c41a' },
        label: { show: true, position: 'top', formatter: '{c}' },
      },
      {
        name: '平板',
        type: 'line',
        smooth: true,
        data: trend.value.deviceTrend.map(d => d.tablet || 0),
        itemStyle: { color: '#fa8c16' },
        label: { show: true, position: 'top', formatter: '{c}' },
      },
    ],
  })
}

function renderTrendCharts() {
  console.log('[OpsStats] renderTrendCharts 被调用, trend:', !!trend.value)
  if (!trend.value) return
  try {
    if (chartPvInst) { chartPvInst.dispose(); chartPvInst = null }
    if (chartOrderInst) { chartOrderInst.dispose(); chartOrderInst = null }
    if (chartRevenueInst) { chartRevenueInst.dispose(); chartRevenueInst = null }
    if (chartDeviceTrendInst) { chartDeviceTrendInst.dispose(); chartDeviceTrendInst = null }
    if (chartPvRef.value) {
      chartPvInst = buildLineChart(chartPvRef.value, trend.value.pvTrend || [], '#1890ff')
    }
    if (chartOrderRef.value) {
      chartOrderInst = buildLineChart(chartOrderRef.value, trend.value.orderTrend || [], '#52c41a')
    }
    if (chartRevenueRef.value) {
      chartRevenueInst = buildLineChart(chartRevenueRef.value, trend.value.revenueTrend || [], '#fa8c16')
    }
    renderDeviceTrendChart()
    console.log('[OpsStats] 图表渲染完成')
  } catch (err) {
    console.error('渲染趋势图表失败:', err)
  }
}

watch(trend, renderTrendCharts, { flush: 'post' })

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    fetchAll()
  }
}

onMounted(() => {
  fetchAll()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  chartPvInst?.dispose()
  chartOrderInst?.dispose()
  chartRevenueInst?.dispose()
  chartDeviceInst?.dispose()
  chartDeviceTrendInst?.dispose()
})
</script>

<template>
  <AdminLayout>
    <div class="ops-stats">
      <div v-if="loading" class="loading">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>
      <div v-else-if="error" class="error-state">
        <p class="error-title">⚠️ {{ error }}</p>
        <p class="error-hint">请检查后端服务是否已启动</p>
        <button class="btn-retry" @click="fetchAll">重新加载</button>
      </div>
      <template v-else>
        <!-- 核心指标卡片 -->
        <div class="stat-cards">
          <div v-for="card in statCards" :key="card.label" class="stat-card" :style="{ borderTopColor: card.color }">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
            <div class="stat-desc">{{ card.desc }}</div>
          </div>
        </div>

        <!-- 各页面访问量 + 设备分布 -->
        <div v-if="dashboard?.pvByPageType" class="section">
          <h3 class="section-title">各页面访问量</h3>
          <div class="page-type-bars">
            <div v-for="(value, key) in dashboard.pvByPageType" :key="key" class="bar-item">
              <span class="bar-label">{{ pageTypeMap[key] || key }}</span>
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :style="{ width: (value / Math.max(...Object.values(dashboard.pvByPageType)) * 100) + '%' }"
                ></div>
              </div>
              <span class="bar-value">{{ formatNumber(value) }} 次</span>
            </div>
          </div>
        </div>

        <!-- 设备分布 -->
        <div class="section">
          <h3 class="section-title">设备分布</h3>
          <div v-if="dashboard?.deviceDistribution" ref="chartDeviceRef" class="chart-container"></div>
          <div v-else class="empty-data">暂无数据</div>
        </div>

        <!-- 24小时访问趋势 -->
        <div v-if="dashboard?.hourlyPv?.length" class="section">
          <h3 class="section-title">今日各时段访问量</h3>
          <div class="hourly-chart">
            <div
              v-for="item in dashboard.hourlyPv"
              :key="item.hour"
              class="hour-bar"
              :style="{ height: (item.value / Math.max(...dashboard.hourlyPv.map(h => h.value)) * 180) + 'px' }"
              :title="`${item.hour}:00 - ${item.value} 次`"
            >
              <span class="hour-value">{{ item.value }}</span>
              <span class="hour-label">{{ item.hour }}时</span>
            </div>
          </div>
        </div>

        <!-- 每日统计 -->
        <div class="section">
          <div class="section-header">
            <h3 class="section-title">每日统计</h3>
            <input type="date" v-model="selectedDate" @change="onDateChange" class="date-picker" />
          </div>
          <div v-if="daily" class="daily-stats">
            <div class="daily-summary">
              <div class="summary-item">
                <span class="summary-label">日期</span>
                <span class="summary-value">{{ daily.statDate }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">浏览量</span>
                <span class="summary-value">{{ formatNumber(daily.totalPv) }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">访客数</span>
                <span class="summary-value">{{ formatNumber(daily.totalUv) }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">订单数</span>
                <span class="summary-value">{{ formatNumber(daily.totalOrders) }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">营业额</span>
                <span class="summary-value">¥{{ formatMoney(daily.totalRevenue) }}</span>
              </div>
            </div>
            <!-- 历史设备分布 -->
            <div v-if="daily.deviceDistribution && Object.keys(daily.deviceDistribution).length" class="daily-device-dist">
              <h4>当日设备分布</h4>
              <div class="device-dist-grid">
                <div
                  v-for="(value, key) in daily.deviceDistribution"
                  :key="key"
                  class="device-dist-item"
                >
                  <span class="device-dist-dot" :style="{ backgroundColor: deviceColorMap[key] || '#999' }"></span>
                  <span class="device-dist-name">{{ deviceNameMap[key] || key }}</span>
                  <span class="device-dist-value">{{ formatNumber(value) }}</span>
                </div>
              </div>
            </div>
            <div v-if="daily.topProducts?.length" class="top-products">
              <h4>热门商品排行</h4>
              <table class="data-table">
                <thead>
                  <tr>
                    <th>商品名称</th>
                    <th>浏览量</th>
                    <th>访客数</th>
                    <th>订单数</th>
                    <th>销售额</th>
                    <th>点赞</th>
                    <th>评价</th>
                    <th>评分</th>
                    <th>转化率</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in daily.topProducts" :key="p.fid">
                    <td>{{ p.fname }}</td>
                    <td>{{ formatNumber(p.pvCount) }}</td>
                    <td>{{ formatNumber(p.uvCount) }}</td>
                    <td>{{ formatNumber(p.orderCount) }}</td>
                    <td>¥{{ formatMoney(p.salesAmount) }}</td>
                    <td>{{ formatNumber(p.likeCount) }}</td>
                    <td>{{ formatNumber(p.reviewCount) }}</td>
                    <td>{{ p.avgStar }} 分</td>
                    <td>{{ p.conversionRate }}%</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div v-else class="empty-data">暂无数据</div>
        </div>

        <!-- 菜品排行榜 -->
        <div class="section">
          <div class="section-header">
            <h3 class="section-title">菜品排行榜</h3>
            <div class="header-controls">
              <input type="date" v-model="rankingDate" @change="fetchRanking" class="date-picker" />
              <select v-model="rankingTopN" @change="fetchRanking" class="days-select">
                <option :value="5">Top 5</option>
                <option :value="10">Top 10</option>
                <option :value="20">Top 20</option>
              </select>
            </div>
          </div>
          <div v-if="ranking" class="ranking-grid">
            <div class="ranking-card">
              <h4>下单次数排行</h4>
              <table class="data-table">
                <thead>
                  <tr><th>#</th><th>商品</th><th>下单</th><th>已付款</th><th>浏览量</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(item, idx) in ranking.topByOrderCount" :key="item.fid">
                    <td class="rank-num">{{ idx + 1 }}</td>
                    <td>{{ item.fname }}</td>
                    <td>{{ formatNumber(item.orderCount) }}</td>
                    <td>{{ formatNumber(item.paidOrderCount) }}</td>
                    <td>{{ formatNumber(item.pvCount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="ranking-card">
              <h4>浏览量排行</h4>
              <table class="data-table">
                <thead>
                  <tr><th>#</th><th>商品</th><th>浏览量</th><th>下单</th><th>购物车</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(item, idx) in ranking.topByPvCount" :key="item.fid">
                    <td class="rank-num">{{ idx + 1 }}</td>
                    <td>{{ item.fname }}</td>
                    <td>{{ formatNumber(item.pvCount) }}</td>
                    <td>{{ formatNumber(item.orderCount) }}</td>
                    <td>{{ formatNumber(item.cartCount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="ranking-card">
              <h4>评价最多排行</h4>
              <table class="data-table">
                <thead>
                  <tr><th>#</th><th>商品</th><th>评价数</th><th>评分</th><th>销售额</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(item, idx) in ranking.topByReviewCount" :key="item.fid">
                    <td class="rank-num">{{ idx + 1 }}</td>
                    <td>{{ item.fname }}</td>
                    <td>{{ formatNumber(item.reviewCount) }}</td>
                    <td>{{ item.avgStar }} 分</td>
                    <td>¥{{ formatMoney(item.salesAmount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="ranking-card">
              <h4>口碑最好排行</h4>
              <table class="data-table">
                <thead>
                  <tr><th>#</th><th>商品</th><th>评分</th><th>评价数</th><th>下单</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(item, idx) in ranking.topByAvgStar" :key="item.fid">
                    <td class="rank-num">{{ idx + 1 }}</td>
                    <td>{{ item.fname }}</td>
                    <td>{{ item.avgStar }} 分</td>
                    <td>{{ formatNumber(item.reviewCount) }}</td>
                    <td>{{ formatNumber(item.orderCount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="ranking-card">
              <h4>购物车添加排行</h4>
              <table class="data-table">
                <thead>
                  <tr><th>#</th><th>商品</th><th>购物车</th><th>下单</th><th>已付款</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(item, idx) in ranking.topByCartCount" :key="item.fid">
                    <td class="rank-num">{{ idx + 1 }}</td>
                    <td>{{ item.fname }}</td>
                    <td>{{ formatNumber(item.cartCount) }}</td>
                    <td>{{ formatNumber(item.orderCount) }}</td>
                    <td>{{ formatNumber(item.paidOrderCount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="ranking-card">
              <h4>已付款订单排行</h4>
              <table class="data-table">
                <thead>
                  <tr><th>#</th><th>商品</th><th>已付款</th><th>下单</th><th>销售额</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(item, idx) in ranking.topByPaidOrderCount" :key="item.fid">
                    <td class="rank-num">{{ idx + 1 }}</td>
                    <td>{{ item.fname }}</td>
                    <td>{{ formatNumber(item.paidOrderCount) }}</td>
                    <td>{{ formatNumber(item.orderCount) }}</td>
                    <td>¥{{ formatMoney(item.salesAmount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div v-else class="empty-data">暂无排行榜数据</div>
        </div>

        <!-- 趋势数据 -->
        <div class="section">
          <div class="section-header">
            <h3 class="section-title">趋势图表</h3>
            <select v-model="trendDays" @change="onTrendDaysChange" class="days-select">
              <option :value="7">最近7天</option>
              <option :value="14">最近14天</option>
              <option :value="30">最近30天</option>
            </select>
          </div>
          <div v-if="trend" class="trend-data">
            <div class="trend-section">
              <h4>浏览量趋势</h4>
              <div ref="chartPvRef" class="chart-container"></div>
            </div>
            <div class="trend-section">
              <h4>订单趋势</h4>
              <div ref="chartOrderRef" class="chart-container"></div>
            </div>
            <div class="trend-section">
              <h4>营业额趋势</h4>
              <div ref="chartRevenueRef" class="chart-container"></div>
            </div>
          </div>
          <div v-else class="empty-data">暂无数据</div>
        </div>

        <!-- 设备趋势 -->
        <div class="section">
          <div class="section-header">
            <h3 class="section-title">设备趋势</h3>
            <select v-model="trendDays" @change="onTrendDaysChange" class="days-select">
              <option :value="7">最近7天</option>
              <option :value="14">最近14天</option>
              <option :value="30">最近30天</option>
            </select>
          </div>
          <div v-if="trend?.deviceTrend?.length">
            <div ref="chartDeviceTrendRef" class="chart-container"></div>
          </div>
          <div v-else class="empty-data">暂无数据</div>
        </div>
      </template>
    </div>
  </AdminLayout>
</template>

<style scoped>
.ops-stats {
  max-width: 1200px;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
  color: #999;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 12px;
  text-align: center;
}

.error-title {
  font-size: 18px;
  color: #e74c3c;
  margin: 0;
}

.error-hint {
  font-size: 14px;
  color: #999;
  margin: 0;
}

.btn-retry {
  padding: 10px 24px;
  background: #ff6b35;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
}

.btn-retry:hover {
  opacity: 0.9;
}

.empty-data {
  text-align: center;
  padding: 40px;
  color: #999;
  font-size: 14px;
}

/* 核心指标卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border-top: 3px solid;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  text-align: center;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-desc {
  font-size: 12px;
  color: #aaa;
}

/* 通用区块 */
.section {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.date-picker, .days-select {
  padding: 6px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  outline: none;
}

/* 页面访问量分布 */
.page-type-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-label {
  width: 80px;
  font-size: 14px;
  color: #555;
  text-align: right;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 24px;
  background: #f0f4f8;
  border-radius: 12px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #4a90e2, #6db3f2);
  border-radius: 12px;
  transition: width 0.4s ease;
}

.bar-value {
  width: 60px;
  text-align: left;
  font-size: 13px;
  color: #888;
  flex-shrink: 0;
}

/* 24小时趋势 */
.hourly-chart {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 220px;
  padding-bottom: 28px;
  overflow-x: auto;
}

.hour-bar {
  flex: 1;
  min-width: 26px;
  background: linear-gradient(180deg, #4a90e2, #89bff0);
  border-radius: 4px 4px 0 0;
  position: relative;
  transition: height 0.3s;
}

.hour-value {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  color: #333;
  font-weight: 600;
  white-space: nowrap;
}

.hour-label {
  position: absolute;
  bottom: -22px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: #999;
  white-space: nowrap;
}

/* 每日统计 */
.daily-stats {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.daily-summary {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 20px;
  background: #f8f9fa;
  border-radius: 8px;
  min-width: 100px;
}

.summary-label {
  font-size: 12px;
  color: #999;
}

.summary-value {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.top-products h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #333;
}

/* 每日设备分布 */
.daily-device-dist h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #333;
}

.device-dist-grid {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.device-dist-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.device-dist-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.device-dist-name {
  font-size: 14px;
  color: #555;
}

.device-dist-value {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th,
.data-table td {
  padding: 10px 12px;
  text-align: center;
  border-bottom: 1px solid #eee;
}

.data-table th {
  background: #f8f9fa;
  font-weight: 600;
  color: #555;
}

.data-table td {
  color: #666;
}

.data-table tbody tr:hover {
  background: #f8f9fa;
}

/* 趋势图表 */
.trend-data {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.trend-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #555;
  text-align: center;
}

.chart-container {
  width: 100%;
  height: 300px;
}

/* 排行榜 */
.header-controls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ranking-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.ranking-card {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
}

.ranking-card h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #333;
  text-align: center;
}

.rank-num {
  font-weight: 700;
  color: #ff6b35;
  width: 30px;
}
</style>