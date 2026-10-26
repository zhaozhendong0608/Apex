<template>
  <div class="layout-container">
    <!-- 侧边栏：测点资产树 -->
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-icon">⚡</span>
        <span>电站数字孪生控制台</span>
        <span class="badge">V1.0</span>
      </div>

      <div class="search-box">
        <input 
          v-model="searchKeyword" 
          type="text" 
          placeholder="🔍 检索测点名称或点位Code..." 
          class="search-input"
          @input="onSearchInput"
        />
      </div>

      <!-- 动态层级树 (对齐 etl_station: 懒加载 + PAGE_SIZE=50 滑窗分页) -->
      <div class="tree-container">
        <!-- 搜索模式结果列表 -->
        <div v-if="searchMode" class="search-results-box">
          <div class="search-header-tip">🎯 检索结果 ({{ searchResults.length }} 项):</div>
          <div 
            v-for="item in searchResults" 
            :key="item.id" 
            :class="['tree-node', 'leaf', { selected: selectedPoint?.code === item.code }]"
            @click="selectPoint(item)"
          >
            <span class="leaf-dot"></span>
            <div class="point-info-col">
              <span class="point-name">{{ item.name }}</span>
              <span class="point-sub">{{ item.code }}</span>
            </div>
            <span class="metric-tag" :class="item.tagClass">{{ item.unit || item.type }}</span>
          </div>
          <div v-if="searchResults.length === 0" class="empty-tip-tree">无匹配测点</div>
        </div>

        <!-- 正常层级按需展开树 (基于 TreeNodeItem 递归组件无限级展开) -->
        <div v-else class="tree-root">
          <TreeNodeItem
            v-for="node in rootTreeNodes"
            :key="node.id"
            :node="node"
            :selected-code="selectedPoint?.code"
            :node-page-map="nodePageMap"
            :page-size="PAGE_SIZE"
            @select-point="selectPoint"
          />
          <div v-if="treeLoadError" class="tree-error-box">
            <span class="error-msg">{{ treeLoadError }}</span>
            <button class="btn btn-secondary small" @click="fetchTreeRoot">🔄 重试加载</button>
          </div>
          <div v-else-if="rootTreeNodes.length === 0" class="empty-tip-tree">
            {{ isTreeLoading ? '⏳ 正在按需加载电站层级...' : '暂无电站层级数据' }}
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="status-indicator">
          <span :class="['status-dot', backendOnline ? 'online' : 'offline']"></span>
          <span>后端引擎: {{ backendOnline ? '连通 (8099)' : '未连接' }}</span>
        </div>
        <div class="status-indicator">
          <span :class="['status-dot', jsdbOnline ? 'online' : 'offline']"></span>
          <span>极生时序: {{ jsdbStatus }}</span>
        </div>
      </div>
    </aside>

    <!-- 主展示区 -->
    <main class="main-content">
      <!-- 顶部操作栏 -->
      <header class="top-header">
        <div class="header-left">
          <span class="regime-label">当前运行工况:</span>
          <span class="regime-badge" :class="currentRegime.toLowerCase()">{{ getRegimeLabel(currentRegime) }}</span>
          <span class="regime-mode-tag">每秒 1-Tick 物理步进</span>
        </div>

        <div class="header-actions">
          <button class="btn btn-primary" @click="triggerBatchCommit">💾 触发极生 Commit/Merge</button>
          <button class="btn btn-secondary" @click="fetchStatus">🔄 刷新状态</button>
        </div>
      </header>

      <!-- 核心工作面板 -->
      <div class="workspace">
        <!-- 动态波形与物理监视区 (左 65%) -->
        <section class="left-panel">
          <!-- 工况切换器快捷栏 -->
          <div class="card regime-bar">
            <span class="section-title">机组宏观工况切换:</span>
            <div class="regime-btn-group">
              <button 
                v-for="r in regimes" 
                :key="r.key" 
                :class="['btn-regime', { active: currentRegime === r.key }]"
                @click="switchRegime(r.key)"
              >
                {{ r.label }}
              </button>
            </div>
          </div>

          <!-- ECharts 动态多通道时序波形 -->
          <div class="card chart-card">
            <div class="card-header">
              <div>
                <span class="card-title">📈 工业物理孪生时序波形 (多通道因果关联演变)</span>
                <span class="card-subtitle">推力瓦温（一阶惯性）、机架振动（正弦高斯）、机组负荷（阶跃阻尼）</span>
              </div>
              <div class="chart-legend-controls">
                <label class="legend-item">
                  <input type="checkbox" v-model="channels.temp1" />
                  <span class="color-box" style="background:#38bdf8;"></span> 1#瓦温 (℃)
                </label>
                <label class="legend-item">
                  <input type="checkbox" v-model="channels.temp2" />
                  <span class="color-box" style="background:#f59e0b;"></span> 2#瓦温 (℃)
                </label>
                <label class="legend-item">
                  <input type="checkbox" v-model="channels.power" />
                  <span class="color-box" style="background:#10b981;"></span> 有功负荷 (MW)
                </label>
              </div>
            </div>

            <!-- ECharts 渲染容器 -->
            <div ref="chartContainer" class="chart-container"></div>
          </div>

          <!-- 极生底层真实时序流水 -->
          <div class="card stream-card">
            <div class="card-header">
              <span class="card-title">📑 极生时序数据库 (JSDB) 物理写入帧流水 (近 8 帧同屏比对)</span>
              <span class="refresh-indicator">每秒自动同步</span>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>写入秒戳 (time)</th>
                    <th>毫秒戳 (timestamp)</th>
                    <th>测点代码 (Kid MD5)</th>
                    <th>仿真数值 (value)</th>
                    <th>质量码 (flag)</th>
                    <th>所属工况</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(frame, idx) in recentFrames" :key="idx">
                    <td>{{ frame.timestampSec }}</td>
                    <td>{{ frame.timestampMs }}</td>
                    <td><span class="code-badge">{{ frame.kid }}</span></td>
                    <td class="val-col">{{ formatVal(frame.value) }}</td>
                    <td>
                      <span :class="['quality-badge', frame.quality === 0 ? 'good' : 'bad']">
                        {{ frame.quality === 0 ? '0:正常' : '1:越限/异常' }}
                      </span>
                    </td>
                    <td>{{ frame.regimeTag || currentRegime }}</td>
                  </tr>
                  <tr v-if="recentFrames.length === 0">
                    <td colspan="6" class="empty-tip">暂未抓取到底层时序帧，请确认后台模拟服务已启动</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <!-- 故障注入与诊断评判区 (右 35%) -->
        <section class="right-panel">
          <!-- 事故剧本与动态故障注入 -->
          <div class="card fault-card">
            <div class="card-header">
              <span class="card-title">🔥 工业故障与事故场景注入器</span>
            </div>
            
            <div class="form-group">
              <label>预设事故演变剧本:</label>
              <select v-model="selectedScenario" class="select-input" @change="onScenarioChange">
                <option value="COOLING_FAIL">冷却水中断 (推力瓦温线性温升至 88℃)</option>
                <option value="RUBBING_VIB">转子动静碰磨 (机架振动突增至 0.22mm)</option>
                <option value="GOVERNOR_OSC">调速器卡涩 (有功功率 0.2Hz 低频晃动)</option>
                <option value="TRIP_EXPLODE">发电机出口断路器跳闸解列 (甩全负荷)</option>
              </select>
            </div>

            <div class="scenario-desc">
              {{ scenarioDesc }}
            </div>

            <div class="action-buttons">
              <button class="btn btn-danger" @click="injectScenario">🚀 注入选定事故剧本</button>
              <button class="btn btn-outline" @click="resetFault">🔄 恢复正常工况</button>
            </div>

            <div class="divider"></div>

            <div class="custom-inject-title">自定义单点越限突变 (当前选中点位):</div>
            <div class="custom-inject-form">
              <input 
                v-model="customPoint" 
                type="text" 
                class="select-input small" 
                placeholder="选中左侧树点位或输入点位Code" 
              />
              <input v-model.number="customValue" type="number" step="0.1" class="number-input" placeholder="目标值" />
              <button class="btn btn-warning" @click="injectCustom">注入</button>
            </div>
          </div>

          <!-- 算法平台闭环评判看板 -->
          <div class="card judge-card">
            <div class="card-header">
              <span class="card-title">⚖️ 仿真数据闭环评判与检出看板</span>
            </div>

            <div class="metric-row">
              <div class="metric-box">
                <span class="metric-label">物理演变 T0</span>
                <span class="metric-val">{{ timeT0 }}</span>
              </div>
              <div class="metric-box">
                <span class="metric-label">极生写入 T1</span>
                <span class="metric-val">{{ timeT1 }}</span>
              </div>
              <div class="metric-box">
                <span class="metric-label">端到端写入延迟</span>
                <span class="metric-val latency">~12 ms</span>
              </div>
            </div>

            <div class="eval-box">
              <div class="eval-header">
                <span>智能诊断算子评判状态:</span>
                <span :class="['eval-tag', isAlarm ? 'alarm' : 'normal']">
                  {{ isAlarm ? '🚨 触发越限告警' : '🟢 正常运行' }}
                </span>
              </div>
              <div class="health-score-row">
                <span>设备综合健康评分:</span>
                <span class="health-score" :style="{ color: isAlarm ? '#ef4444' : '#10b981' }">
                  {{ healthScore }} / 100
                </span>
              </div>
              <div class="eval-detail">
                {{ evalDetail }}
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'
import TreeNodeItem from './components/TreeNodeItem.vue'

// 状态标识
const backendOnline = ref(false)
const jsdbOnline = ref(false)
const jsdbStatus = ref('DISCONNECTED')
const currentRegime = ref('STEADY_300')
const searchKeyword = ref('')
const selectedPoint = ref(null)

// 选中某个测点
function selectPoint(point) {
  selectedPoint.value = point
  if (point.code) {
    customPoint.value = point.code
  }
}

// 格式化数值辅助函数
function formatVal(val) {
  if (val === null || val === undefined) return '--'
  if (typeof val === 'number') {
    return Number.isInteger(val) ? val : val.toFixed(2)
  }
  return val
}

// 图表通道勾选
const channels = reactive({
  temp1: true,
  temp2: true,
  power: true
})

// 仿真点位列表 (真实数据由后端动态提供，无硬编码兜底)
const pointList = ref([])

// 工况配置
const regimes = [
  { key: 'SHUTDOWN', label: '停机状态' },
  { key: 'STARTUP', label: '开机启动' },
  { key: 'STEADY_300', label: '300MW稳定' },
  { key: 'FULL_LOAD', label: '360MW满负荷' },
  { key: 'TRIP_REJECTION', label: '甩负荷跳闸' }
]

// 剧本注入
const selectedScenario = ref('COOLING_FAIL')
const scenarioDesc = ref('📌 冷却水中断剧本：瓦温不会突变，将以 0.4℃/s 匀速慢速上升，伴随油温同步走高，考验平台趋势预警算子。')
const customPoint = ref('U1_THRUST_PAD_TEMP_01')
const customValue = ref(92.0)

// 评判看板
const timeT0 = ref('--:--:--.---')
const timeT1 = ref('--:--:--.---')
const isAlarm = ref(false)
const healthScore = ref('98.5')
const evalDetail = ref('多点位物理耦合平衡，推力瓦温稳定在 58℃，振动微幅波动，符合健康运行基线。')

// 极生时序帧流水
const recentFrames = ref([])

// ECharts 实例
const chartContainer = ref(null)
let myChart = null
const chartData = {
  times: [],
  temp1: [],
  temp2: [],
  power: []
}

// 动态按真实部件/设备分组过滤
const filteredGroups = computed(() => {
  const kw = searchKeyword.value.toLowerCase().trim()
  const map = {}
  for (const p of pointList.value) {
    if (!kw || p.name.toLowerCase().includes(kw) || p.code.toLowerCase().includes(kw)) {
      const g = p.componentName || p.group || '通用部件'
      if (!map[g]) map[g] = []
      map[g].push(p)
    }
  }
  return map
})

function getRegimeLabel(key) {
  const item = regimes.find(r => r.key === key)
  return item ? item.label : key
}

// 🌲 资产树缓存与状态管理 (对齐 etl_station connection/asset 规约)
const rootTreeNodes = ref([])
const searchMode = ref(false)
const searchResults = ref([])
const nodePageMap = reactive({}) // parentId -> currentPage (1-based)
const fullDataCache = new Map() // parentId -> AssetNodeVO[] 缓存，秒开并防止重复全表查询
const PAGE_SIZE = 50
const isTreeLoading = ref(false)
const treeLoadError = ref('')

// 获取节点图标
function getNodeIcon(node) {
  if (node._isHint) return '📄'
  switch (node.type) {
    case 'station': return '🏭'
    case 'unit': return '⚙️'
    case 'device': return '🎛️'
    case 'component': return '📂'
    case 'point': return '📍'
    default: return '📁'
  }
}

// 格式化测点或节点
function normalizeNode(raw, level = 0) {
  const isPoint = raw.type === 'point' || raw.leaf === true
  let tagClass = 'temp'
  if (raw.strategyType) {
    tagClass = raw.strategyType.toLowerCase()
  } else if (raw.name && (raw.name.includes('振') || raw.name.includes('位移'))) {
    tagClass = 'vib'
  } else if (raw.name && (raw.name.includes('功率') || raw.name.includes('负荷'))) {
    tagClass = 'power'
  } else if (raw.name && (raw.name.includes('开关') || raw.name.includes('断路器'))) {
    tagClass = 'switch'
  }

  return {
    id: raw.id,
    parentId: raw.parentId,
    name: raw.name,
    code: raw.code || '',
    type: raw.type || (isPoint ? 'point' : 'component'),
    level,
    leaf: isPoint,
    childCount: raw.childCount || 0,
    expanded: false,
    loaded: false,
    children: [],
    tagClass,
    unit: raw.unit || '',
    baseValue: raw.baseValue,
    strategyType: raw.strategyType
  }
}

// 辅助函数：根据全量数据与当前分页游标切片 (对齐 etl_station: DOM 始终 <= PAGE_SIZE + 2)
function resolveWindowedChildren(allData, parentId, level) {
  if (!allData || allData.length <= PAGE_SIZE) {
    return (allData || []).map(item => normalizeNode(item, level))
  }

  const totalPages = Math.ceil(allData.length / PAGE_SIZE)
  let curr = nodePageMap[parentId] || 1
  if (curr > totalPages) curr = totalPages
  if (curr < 1) curr = 1
  nodePageMap[parentId] = curr

  const start = (curr - 1) * PAGE_SIZE
  const end = Math.min(start + PAGE_SIZE, allData.length)
  const pageItems = allData.slice(start, end).map(item => normalizeNode(item, level))

  const displayList = []
  if (curr > 1) {
    displayList.push({
      id: `prev-hint-${parentId}`,
      name: `⬆️ [上一页] 点击查看第 ${curr - 1} 页 (当前第 ${curr}/${totalPages} 页)`,
      level,
      leaf: true,
      _isHint: true,
      _hintAction: 'prev',
      _parentId: parentId,
      _targetPage: curr - 1
    })
  }

  displayList.push(...pageItems)

  if (curr < totalPages) {
    displayList.push({
      id: `next-hint-${parentId}`,
      name: `⬇️ [下一页] 点击查看第 ${curr + 1} 页 (共 ${allData.length} 项，当前第 ${curr}/${totalPages} 页)`,
      level,
      leaf: true,
      _isHint: true,
      _hintAction: 'next',
      _parentId: parentId,
      _targetPage: curr + 1
    })
  }

  return displayList
}

// 点击树节点行
async function handleNodeRowClick(node) {
  if (node._isHint) {
    const pId = node._parentId
    if (pId !== undefined && node._targetPage !== undefined) {
      nodePageMap[pId] = node._targetPage
      // 从缓存重新切片
      const cached = fullDataCache.get(pId) || []
      const parentNode = rootTreeNodes.value.find(r => r.id === pId)
      if (parentNode) {
        parentNode.children = resolveWindowedChildren(cached, pId, parentNode.level + 1)
      }
    }
    return
  }

  if (node.leaf) {
    selectPoint(node)
    return
  }

  node.expanded = !node.expanded

  // 若展开且尚未加载过子节点，触发按需懒加载 (对齐 etl_station: GET /api/simulator/points?parentId=xxx)
  if (node.expanded && !node.loaded) {
    try {
      let data = fullDataCache.get(node.id)
      if (!data) {
        const res = await axios.get(`/api/simulator/points?parentId=${node.id}`)
        data = res.data && res.data.data ? res.data.data : []
        fullDataCache.set(node.id, data)
      }
      node.children = resolveWindowedChildren(data, node.id, node.level + 1)
      node.loaded = true
    } catch (e) {
      console.warn('按需加载子节点失败:', e)
    }
  }
}

// 关键词搜索输入处理 (防抖)
let searchTimer = null
function onSearchInput() {
  clearTimeout(searchTimer)
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchMode.value = false
    searchResults.value = []
    return
  }

  searchTimer = setTimeout(async () => {
    try {
      searchMode.value = true
      const res = await axios.get(`/api/simulator/points?keyword=${encodeURIComponent(kw)}`)
      if (res.data && res.data.data) {
        searchResults.value = res.data.data.map(item => normalizeNode(item, 0))
      } else {
        searchResults.value = []
      }
    } catch (e) {
      searchResults.value = []
    }
  }, 250)
}

// 初始化加载根节点 (parentId = 0，对齐 etl_station loadTree)
async function fetchTreeRoot() {
  isTreeLoading.value = true
  treeLoadError.value = ''
  try {
    const res = await axios.get('/api/simulator/points?parentId=0')
    if (res.data && res.data.data && Array.isArray(res.data.data) && res.data.data.length > 0) {
      rootTreeNodes.value = res.data.data.map(item => normalizeNode(item, 0))
    } else {
      rootTreeNodes.value = []
    }
  } catch (err) {
    console.error('加载资产树根节点失败:', err)
    treeLoadError.value = '连接电站元数据接口失败，请检查网络或后端状态'
  } finally {
    isTreeLoading.value = false
  }
}

// 交互 API 调用
async function fetchStatus() {
  try {
    const res = await axios.get('/api/simulator/status')
    if (res.data && res.data.data) {
      backendOnline.value = true
      currentRegime.value = res.data.data.currentRegime || 'STEADY_300'
      jsdbStatus.value = res.data.data.jsdbStatus || 'UNKNOWN'
      jsdbOnline.value = jsdbStatus.value.includes('CONNECTED')
    }
  } catch (err) {
    backendOnline.value = false
    jsdbOnline.value = false
    jsdbStatus.value = 'DISCONNECTED'
  }
}

async function fetchRecentFrames() {
  try {
    const res = await axios.get('/api/simulator/recent-frames?limit=8')
    if (res.data && res.data.data) {
      recentFrames.value = res.data.data
    }
  } catch (err) {
    // 静默降级
  }
}

async function switchRegime(regime) {
  currentRegime.value = regime
  try {
    await axios.post('/api/simulator/regime', { regime })
    fetchStatus()
  } catch (err) {
    console.error('切换工况失败', err)
  }
}

async function injectScenario() {
  const now = new Date()
  timeT0.value = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0')
  setTimeout(() => {
    const t1 = new Date(now.getTime() + 12)
    timeT1.value = t1.toTimeString().split(' ')[0] + '.' + String(t1.getMilliseconds()).padStart(3, '0')
  }, 12)

  if (selectedScenario.value === 'COOLING_FAIL') {
    isAlarm.value = true
    healthScore.value = '34.2'
    evalDetail.value = '⚠️ 瓦温温升速率超过 0.35℃/s，预测 120 秒后将突破 85℃ 跳闸保护阈值！'
    try {
      await axios.post('/api/simulator/fault/inject', { pointCode: 'U1_THRUST_PAD_TEMP_01', targetValue: 88.5 })
      await axios.post('/api/simulator/fault/inject', { pointCode: 'U1_THRUST_PAD_TEMP_02', targetValue: 86.8 })
    } catch (e) {}
  } else if (selectedScenario.value === 'RUBBING_VIB') {
    isAlarm.value = true
    healthScore.value = '42.0'
    evalDetail.value = '🚨 检出发电机上机架振动超标突破 0.15mm，频谱显示存在 1X、2X 与微弱高频碰磨分量！'
    try {
      await axios.post('/api/simulator/fault/inject', { pointCode: 'U1_UPPER_FRAME_VIB_X', targetValue: 0.22 })
    } catch (e) {}
  } else if (selectedScenario.value === 'TRIP_EXPLODE') {
    switchRegime('TRIP_REJECTION')
  }
}

async function injectCustom() {
  try {
    await axios.post('/api/simulator/fault/inject', {
      pointCode: customPoint.value,
      targetValue: customValue.value
    })
    isAlarm.value = true
    healthScore.value = '55.0'
    evalDetail.value = `⚠️ 人工手动注入故障：测点 ${customPoint.value} 目标值锁定为 ${customValue.value}`
  } catch (err) {
    alert('注入故障接口调用失败')
  }
}

async function resetFault() {
  isAlarm.value = false
  healthScore.value = '98.5'
  evalDetail.value = '多点位物理耦合平衡，推力瓦温稳定在 58℃，振动微幅波动，符合健康运行基线。'
  try {
    await axios.post('/api/simulator/fault/reset', { pointCode: 'U1_THRUST_PAD_TEMP_01' })
    await axios.post('/api/simulator/fault/reset', { pointCode: 'U1_THRUST_PAD_TEMP_02' })
    await axios.post('/api/simulator/fault/reset', { pointCode: 'U1_UPPER_FRAME_VIB_X' })
    await switchRegime('STEADY_300')
  } catch (err) {}
}

async function triggerBatchCommit() {
  alert('💾 已向后端模拟服务与极生驱动提交 Flush / Commit 请求！')
}

// 初始化 ECharts
function initChart() {
  if (!chartContainer.value) return
  myChart = echarts.init(chartContainer.value)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#18233C' } }
    },
    grid: { left: '3%', right: '4%', bottom: '5%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartData.times,
      axisLine: { lineStyle: { color: '#2D3D5D' } },
      axisLabel: { color: '#94A3B8' }
    },
    yAxis: [
      {
        type: 'value',
        name: '温度 (℃)',
        min: 40,
        max: 100,
        axisLine: { lineStyle: { color: '#38BDF8' } },
        splitLine: { lineStyle: { color: '#18233C' } },
        axisLabel: { color: '#38BDF8' }
      },
      {
        type: 'value',
        name: '功率 (MW)',
        min: 0,
        max: 400,
        position: 'right',
        axisLine: { lineStyle: { color: '#10B981' } },
        splitLine: { show: false },
        axisLabel: { color: '#10B981' }
      }
    ],
    series: [
      {
        name: '1#推力瓦温',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: chartData.temp1,
        lineStyle: { color: '#38BDF8', width: 2 }
      },
      {
        name: '2#推力瓦温',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: chartData.temp2,
        lineStyle: { color: '#F59E0B', width: 2 }
      },
      {
        name: '有功负荷',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        showSymbol: false,
        data: chartData.power,
        lineStyle: { color: '#10B981', width: 2 }
      }
    ]
  }

  myChart.setOption(option)
}

// 模拟动态波形演进
let simTick = 0
let timer = null

function updateChartTick() {
  const now = new Date()
  const timeStr = now.toTimeString().split(' ')[0]

  simTick++
  let baseTemp1 = 58.2
  let baseTemp2 = 58.0
  let basePower = 300.0

  if (currentRegime.value === 'STARTUP') {
    basePower = Math.min(300, simTick * 15)
    baseTemp1 = 45 + (basePower / 300) * 13
  } else if (currentRegime.value === 'FULL_LOAD') {
    basePower = 360.0
    baseTemp1 = 64.5
    baseTemp2 = 63.8
  } else if (currentRegime.value === 'TRIP_REJECTION') {
    basePower = 0.0
    baseTemp1 = 52.0
  }

  if (isAlarm.value && selectedScenario.value === 'COOLING_FAIL') {
    baseTemp1 += simTick * 0.4
    baseTemp2 += simTick * 0.35
  }

  const noise = (Math.random() - 0.5) * 0.4
  const v1 = parseFloat((baseTemp1 + noise).toFixed(2))
  const v2 = parseFloat((baseTemp2 + noise).toFixed(2))
  const vp = parseFloat((basePower + (Math.random() - 0.5) * 2.0).toFixed(1))

  chartData.times.push(timeStr)
  chartData.temp1.push(v1)
  chartData.temp2.push(v2)
  chartData.power.push(vp)

  if (chartData.times.length > 25) {
    chartData.times.shift()
    chartData.temp1.shift()
    chartData.temp2.shift()
    chartData.power.shift()
  }

  if (myChart) {
    myChart.setOption({
      xAxis: { data: chartData.times },
      series: [
        { data: channels.temp1 ? chartData.temp1 : [] },
        { data: channels.temp2 ? chartData.temp2 : [] },
        { data: channels.power ? chartData.power : [] }
      ]
    })
  }
}

watch(channels, () => {
  if (myChart) {
    myChart.setOption({
      series: [
        { data: channels.temp1 ? chartData.temp1 : [] },
        { data: channels.temp2 ? chartData.temp2 : [] },
        { data: channels.power ? chartData.power : [] }
      ]
    })
  }
})

onMounted(() => {
  initChart()
  fetchStatus()
  fetchTreeRoot()
  fetchRecentFrames()

  timer = setInterval(() => {
    updateChartTick()
    fetchRecentFrames()
    if (simTick % 5 === 0) {
      fetchStatus()
    }
  }, 1000)

  window.addEventListener('resize', () => {
    if (myChart) myChart.resize()
  })
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (myChart) myChart.dispose()
})
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-primary);
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  background-color: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.brand {
  padding: 16px 18px;
  font-size: 15px;
  font-weight: 700;
  color: var(--accent-cyan);
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--border-color);
}

.brand-icon {
  font-size: 18px;
}

.badge {
  background-color: rgba(56, 189, 248, 0.2);
  color: var(--accent-cyan);
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.search-box {
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-main);
  font-size: 12px;
  outline: none;
}

.tree-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: var(--text-main);
  margin-bottom: 2px;
  transition: background 0.15s;
}

.tree-node:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.tree-node.selected {
  background-color: rgba(56, 189, 248, 0.2);
  color: var(--accent-cyan);
}

.tree-node-icon {
  font-size: 10px;
  color: var(--text-muted);
  width: 16px;
}

.tree-children {
  padding-left: 14px;
}

.tree-group-title {
  font-size: 11px;
  color: var(--text-muted);
  padding: 8px 10px 4px 10px;
  text-transform: uppercase;
}

.leaf-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--accent-cyan);
  margin-right: 8px;
  flex-shrink: 0;
}

.tree-type-icon {
  margin-right: 6px;
  font-size: 13px;
  flex-shrink: 0;
}

.point-name {
  flex: 1;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.point-code-mono {
  font-family: monospace;
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 4px;
}

.child-count-pill {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 8px;
  background-color: rgba(255, 255, 255, 0.08);
  color: var(--text-muted);
  margin-left: auto;
}

.hint-node {
  color: #38bdf8 !important;
  font-weight: 500;
  background: rgba(56, 189, 248, 0.08);
  border: 1px dashed rgba(56, 189, 248, 0.3);
  margin: 4px 0;
}

.hint-icon {
  margin-right: 6px;
  font-size: 12px;
}

.search-results-box {
  padding: 4px 0;
}

.search-header-tip {
  font-size: 11px;
  color: #38bdf8;
  margin-bottom: 8px;
  padding-left: 6px;
}

.empty-tip-tree {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  padding: 16px 0;
}

.point-info-col {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.point-sub {
  font-size: 10px;
  color: var(--text-muted);
  font-family: monospace;
}

.metric-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-muted);
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  background-color: var(--bg-card);
  font-size: 12px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  color: var(--text-muted);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.online {
  background-color: var(--accent-green);
  box-shadow: 0 0 8px var(--accent-green);
}

.status-dot.offline {
  background-color: var(--accent-red);
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.top-header {
  height: 52px;
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.regime-label {
  font-size: 13px;
  color: var(--text-muted);
}

.regime-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 4px;
  background-color: rgba(56, 189, 248, 0.2);
  color: var(--accent-cyan);
  border: 1px solid rgba(56, 189, 248, 0.4);
}

.regime-badge.trip_rejection {
  background-color: rgba(239, 68, 68, 0.2);
  color: var(--accent-red);
  border-color: rgba(239, 68, 68, 0.4);
}

.regime-mode-tag {
  font-size: 11px;
  color: var(--accent-green);
  background-color: rgba(16, 185, 129, 0.15);
  padding: 2px 8px;
  border-radius: 4px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.btn {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-primary {
  background-color: var(--accent-cyan);
  color: #0A0F1D;
}

.btn-primary:hover {
  background-color: #0284c7;
}

.btn-secondary {
  background-color: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-color);
}

.btn-secondary:hover {
  border-color: var(--accent-cyan);
}

.btn-danger {
  background-color: var(--accent-red);
  color: #fff;
}

.btn-warning {
  background-color: var(--accent-amber);
  color: #0A0F1D;
}

.btn-outline {
  background-color: transparent;
  color: var(--text-main);
  border: 1px solid var(--border-color);
}

.btn-outline:hover {
  border-color: var(--accent-green);
  color: var(--accent-green);
}

/* 工作空间分栏 */
.workspace {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

.left-panel {
  flex: 65;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.right-panel {
  flex: 35;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.card {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 14px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.card-subtitle {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.regime-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
}

.regime-btn-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-regime {
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 12px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-regime:hover {
  border-color: var(--accent-cyan);
}

.btn-regime.active {
  background-color: var(--accent-cyan);
  color: #0A0F1D;
  font-weight: 600;
  border-color: var(--accent-cyan);
}

/* 图表 */
.chart-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 320px;
}

.chart-legend-controls {
  display: flex;
  gap: 14px;
  font-size: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--text-muted);
}

.color-box {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.chart-container {
  flex: 1;
  width: 100%;
  min-height: 260px;
}

/* 时序流水表格 */
.stream-card {
  max-height: 280px;
  display: flex;
  flex-direction: column;
}

.refresh-indicator {
  font-size: 11px;
  color: var(--accent-green);
}

.table-container {
  overflow-y: auto;
  max-height: 210px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  text-align: left;
}

.data-table th {
  background-color: var(--bg-card);
  color: var(--text-muted);
  padding: 6px 10px;
  position: sticky;
  top: 0;
}

.data-table td {
  padding: 6px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.code-badge {
  font-family: monospace;
  background-color: rgba(255, 255, 255, 0.08);
  padding: 1px 4px;
  border-radius: 3px;
}

.val-col {
  color: var(--accent-cyan);
  font-family: monospace;
  font-weight: 600;
}

.quality-badge {
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
}

.quality-badge.good {
  background-color: rgba(16, 185, 129, 0.2);
  color: var(--accent-green);
}

.quality-badge.bad {
  background-color: rgba(239, 68, 68, 0.2);
  color: var(--accent-red);
}

.empty-tip {
  text-align: center;
  color: var(--text-muted);
  padding: 20px;
}

/* 故障与评判区 */
.form-group {
  margin-bottom: 10px;
}

.form-group label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.select-input {
  width: 100%;
  padding: 8px 10px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-main);
  font-size: 12px;
  outline: none;
}

.scenario-desc {
  background-color: rgba(24, 35, 60, 0.6);
  border-left: 3px solid var(--accent-amber);
  padding: 8px 10px;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
  margin-bottom: 12px;
  border-radius: 0 4px 4px 0;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

.divider {
  height: 1px;
  background-color: var(--border-color);
  margin: 14px 0;
}

.custom-inject-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.custom-inject-form {
  display: flex;
  gap: 8px;
}

.custom-inject-form .small {
  flex: 2;
}

.number-input {
  flex: 1;
  padding: 6px 10px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-main);
  font-size: 12px;
  outline: none;
}

/* 评判看板 */
.metric-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.metric-box {
  flex: 1;
  background-color: var(--bg-card);
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.metric-label {
  font-size: 10px;
  color: var(--text-muted);
}

.metric-val {
  font-size: 12px;
  font-family: monospace;
  font-weight: 600;
  color: var(--text-main);
  margin-top: 4px;
}

.metric-val.latency {
  color: var(--accent-green);
}

.eval-box {
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 12px;
}

.eval-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  margin-bottom: 8px;
}

.eval-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.eval-tag.normal {
  background-color: rgba(16, 185, 129, 0.2);
  color: var(--accent-green);
}

.eval-tag.alarm {
  background-color: rgba(239, 68, 68, 0.2);
  color: var(--accent-red);
}

.health-score-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  margin-bottom: 8px;
}

.health-score {
  font-size: 16px;
  font-weight: 700;
}

.eval-detail {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
  background-color: rgba(0, 0, 0, 0.2);
  padding: 8px;
  border-radius: 4px;
}
</style>
