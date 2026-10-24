<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Graph } from '@antv/x6'
import { ElMessage } from 'element-plus'
import DevicePalette, { type DeviceItem } from '../components/DevicePalette.vue'
import PropertyInspector, { type SelectedNodeData, type SelectedEdgeData, type SelectedZoneData } from '../components/PropertyInspector.vue'
import SimulationConsole, { type LogItem } from '../components/SimulationConsole.vue'
import { getTopologyDetailApi, saveTopologyApi, runSimulationApi } from '../api/topology'

const route = useRoute()
const router = useRouter()

const containerRef = ref<HTMLDivElement | null>(null)
let graph: Graph | null = null

const topoId = ref<number | null>(route.query.id ? Number(route.query.id) : null)
const topoName = ref<string>('电力边缘采集系统')
const saving = ref(false)

// 当前选中的节点、链路与安全大区数据
const selectedNode = ref<SelectedNodeData | null>(null)
const selectedEdge = ref<SelectedEdgeData | null>(null)
const selectedZone = ref<SelectedZoneData | null>(null)

// 右键上下文菜单状态
const contextMenu = ref<{
  visible: boolean
  x: number
  y: number
  targetCellId: string
  targetType: 'node' | 'edge' | 'zone'
  targetTitle: string
}>({
  visible: false,
  x: 0,
  y: 0,
  targetCellId: '',
  targetType: 'node',
  targetTitle: ''
})

const closeContextMenu = () => {
  contextMenu.value.visible = false
}

// 统一删除右键目标图元
const handleDeleteContextCell = () => {
  if (!graph || !contextMenu.value.targetCellId) return
  const cell = graph.getCellById(contextMenu.value.targetCellId)
  if (cell) {
    const title = contextMenu.value.targetTitle || '选中图元'
    cell.remove()
    ElMessage.success(`已删除: ${title}`)
    addLog(`INFO: 已移除画布图元 [${title}]`, 'info')

    // 清空被删除图元的属性检查器选中态
    if (selectedNode.value?.id === cell.id) selectedNode.value = null
    if (selectedEdge.value?.id === cell.id) selectedEdge.value = null
    if (selectedZone.value?.id === cell.id) selectedZone.value = null
  }
  closeContextMenu()
}

const logs = ref<LogItem[]>([
  { id: '1', time: '10:00:01', text: 'INFO: 拓扑工作区就绪，等待加载方案数据...', type: 'info' }
])

const packetVisible = ref(false)
const passBadgeVisible = ref(false)

onMounted(async () => {
  if (!containerRef.value) return

  // 初始化 X6 图引擎
  graph = new Graph({
    container: containerRef.value,
    autoResize: true,
    background: { color: '#151d28' },
    grid: {
      size: 15,
      visible: true,
      type: 'mesh',
      args: { color: '#1f2a3a', thickness: 1 }
    },
    panning: { enabled: true, eventTypes: ['leftMouseDown'], modifiers: 'ctrl' },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
    connecting: {
      snap: true,
      allowBlank: false,
      allowLoop: false,
      highlight: true,
      connector: 'rounded',
      router: 'orth',
      createEdge() {
        return graph!.createEdge({
          attrs: {
            line: {
              stroke: '#38bdf8',
              strokeWidth: 2,
              targetMarker: { name: 'classic', size: 7 }
            }
          }
        })
      }
    }
  })

  // 监听图元点击事件：选中节点或大区底板
  graph.on('node:click', ({ node }) => {
    // 1. 若点击的是安全大区背景底板
    if (node.id.startsWith('zone_')) {
      const attrs = node.getAttrs()
      const body = attrs?.body as any || {}
      const label = attrs?.label as any || {}
      const data = node.getData() || {}

      selectedNode.value = null
      selectedEdge.value = null
      selectedZone.value = {
        id: node.id,
        name: label.text || data.name || '通用大区',
        borderColor: body.stroke || '#60a5fa',
        bgColor: body.fill || 'rgba(96, 165, 250, 0.12)',
        isDashed: body.strokeDasharray && body.strokeDasharray !== '0',
        fontSize: label.fontSize || 11
      }
      return
    }

    // 2. 若点击的是普通设备节点
    const data = node.getData() || {}
    const attrs = node.getAttrs()
    const titleText = (attrs?.title as any)?.text || data.name || '未命名设备'
    const subText = (attrs?.sub as any)?.text || data.ip || '192.168.1.1'

    selectedEdge.value = null
    selectedZone.value = null
    selectedNode.value = {
      id: node.id,
      name: titleText,
      type: data.type || 'HOST',
      zone: data.zone || 'IT_ZONE',
      ip: data.ip || subText,
      subnet: data.subnet || '255.255.255.0',
      acls: data.acls || []
    }
  })

  // 监听图元点击事件：选中链路
  graph.on('edge:click', ({ edge }) => {
    selectedNode.value = null
    selectedZone.value = null
    selectedEdge.value = {
      id: edge.id,
      bandwidth: '1000',
      latencyMs: 2
    }
    closeContextMenu()
  })

  // 监听图元右键事件：节点与大区底板
  graph.on('node:contextmenu', ({ node, e }) => {
    e.preventDefault()
    const isZone = node.id.startsWith('zone_')
    const attrs = node.getAttrs()
    const title = isZone
      ? ((attrs?.label as any)?.text || '大区底板')
      : ((attrs?.title as any)?.text || node.getData()?.name || '设备节点')

    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      targetCellId: node.id,
      targetType: isZone ? 'zone' : 'node',
      targetTitle: title
    }
  })

  // 监听图元右键事件：链路
  graph.on('edge:contextmenu', ({ edge, e }) => {
    e.preventDefault()
    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      targetCellId: edge.id,
      targetType: 'edge',
      targetTitle: '物理网络链路'
    }
  })

  // 点击空白处取消选择并关闭右键菜单
  graph.on('blank:click', () => {
    selectedNode.value = null
    selectedEdge.value = null
    selectedZone.value = null
    closeContextMenu()
  })

  graph.on('blank:contextmenu', ({ e }) => {
    e.preventDefault()
    closeContextMenu()
  })

  // 容器拖拽接收设备与大区底板
  const el = containerRef.value
  el.addEventListener('dragover', (e) => e.preventDefault())
  el.addEventListener('drop', (e) => {
    e.preventDefault()
    const dataStr = e.dataTransfer?.getData('application/json')
    if (!dataStr || !graph) return

    try {
      const item: DeviceItem = JSON.parse(dataStr)
      const rect = el.getBoundingClientRect()

      if (item.isZone && item.zoneStyle) {
        // 拖入的是安全大区背景底板
        const zoneId = 'zone_' + Math.random().toString(36).substring(2, 6)
        createZoneBox(
          zoneId,
          item.name,
          e.clientX - rect.left - item.zoneStyle.defaultW / 2,
          e.clientY - rect.top - item.zoneStyle.defaultH / 2,
          item.zoneStyle.defaultW,
          item.zoneStyle.defaultH,
          item.zoneStyle.bgColor,
          item.zoneStyle.borderColor,
          item.zoneStyle.isDashed
        )
        addLog(`INFO: 已向画布添加背景大区底板 [${item.name}]`, 'info')
      } else {
        // 拖入的是普通设备节点
        createDeviceNode(item.type, item.name, item.icon, item.color, e.clientX - rect.left - 55, e.clientY - rect.top - 30)
        addLog(`INFO: 已向画布添加设备节点 [${item.name}]`, 'info')
      }
    } catch (parseErr) {
      console.error('解析拖拽数据失败', parseErr)
    }
  })

  // 根据路由 ID 拉取数据库方案，否则载入默认 Demo
  if (topoId.value) {
    await loadTopologyFromBackend(topoId.value)
  } else {
    loadDemoTopology()
  }
})

// 处理右侧面板修改节点属性后的动态回写
const handleUpdateNode = (updated: SelectedNodeData) => {
  if (!graph) return
  const node = graph.getCellById(updated.id)
  if (node && node.isNode()) {
    // 1. 回写节点业务 data
    node.setData({
      name: updated.name,
      type: updated.type,
      zone: updated.zone,
      ip: updated.ip,
      subnet: updated.subnet,
      acls: updated.acls
    })

    // 2. 实时刷新 SVG 图元上的文字与副标题 IP
    node.setAttrByPath('title/text', updated.name)
    node.setAttrByPath('sub/text', updated.ip)
  }
}

// 处理右侧面板修改链路属性后的动态回写
const handleUpdateEdge = (updated: SelectedEdgeData) => {
  if (!graph) return
  const edge = graph.getCellById(updated.id)
  if (edge && edge.isEdge()) {
    edge.setData({
      bandwidth: updated.bandwidth,
      latencyMs: updated.latencyMs
    })
  }
}

// 处理右侧面板修改大区底板属性后的动态回写
const handleUpdateZone = (updated: SelectedZoneData) => {
  if (!graph) return
  const zoneNode = graph.getCellById(updated.id)
  if (zoneNode && zoneNode.isNode()) {
    // 1. 同步业务数据
    zoneNode.setData({
      name: updated.name,
      borderColor: updated.borderColor,
      bgColor: updated.bgColor,
      isDashed: updated.isDashed
    })

    // 2. 同步 SVG 外观属性 (标题文本、颜色、边框颜色、虚线边框、字号)
    zoneNode.setAttrByPath('label/text', updated.name)
    zoneNode.setAttrByPath('label/fill', updated.borderColor)
    zoneNode.setAttrByPath('label/fontSize', updated.fontSize || 11)
    zoneNode.setAttrByPath('body/stroke', updated.borderColor)
    zoneNode.setAttrByPath('body/fill', updated.bgColor)
    zoneNode.setAttrByPath('body/strokeDasharray', updated.isDashed ? '5 5' : '0')
  }
}

// 从后端加载拓扑
const loadTopologyFromBackend = async (id: number) => {
  try {
    const res = await getTopologyDetailApi(id)
    topoName.value = res.data.name
    if (res.data.canvasData && graph) {
      const data = JSON.parse(res.data.canvasData)
      if (data.cells && data.cells.length > 0) {
        graph.fromJSON(data)
        graph.zoomToFit({ padding: 30 })
        addLog(`INFO: 成功从数据库载入拓扑方案 [${res.data.name}] (包含 ${data.cells.length} 个图元)`, 'success')
        return
      }
    }
    loadDemoTopology()
  } catch (error: any) {
    ElMessage.error('加载拓扑失败，载入默认模板')
    loadDemoTopology()
  }
}

// 保存拓扑回数据库
const handleSaveTopology = async () => {
  if (!graph) return
  saving.value = true
  try {
    const json = graph.toJSON()
    const nodes = graph.getNodes().filter(n => !n.id.startsWith('zone_'))
    const edges = graph.getEdges()

    await saveTopologyApi({
      id: topoId.value || undefined,
      name: topoName.value,
      canvasData: JSON.stringify(json),
      nodeCount: nodes.length,
      edgeCount: edges.length
    })

    ElMessage.success('拓扑画布已成功持久化至 MySQL net_topology 表！')
    addLog(`SUCCESS: 拓扑数据序列化保存成功 (设备: ${nodes.length}, 链路: ${edges.length})`, 'success')
  } catch (err: any) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 创建安全大区 (SVG 标准图元)
const createZoneBox = (id: string, title: string, x: number, y: number, width: number, height: number, bgColor: string, borderColor: string, isDashed = false) => {
  if (!graph) return
  return graph.addNode({
    id,
    x,
    y,
    width,
    height,
    zIndex: 1,
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'text', selector: 'label' }
    ],
    attrs: {
      body: {
        fill: bgColor,
        stroke: borderColor,
        strokeWidth: 2,
        strokeDasharray: isDashed ? '5 5' : '0',
        rx: 8,
        ry: 8
      },
      label: {
        text: title,
        fill: borderColor,
        fontSize: 11,
        fontWeight: 'bold',
        textAnchor: 'start',
        textVerticalAnchor: 'top',
        refX: 12,
        refY: 8
      }
    }
  })
}

// 创建设备节点 (SVG 标准图元)
const createDeviceNode = (type: string, name: string, icon: string, color: string, x: number, y: number, subtext = '') => {
  if (!graph) return null
  const id = 'dev_' + Math.random().toString(36).substring(2, 8)
  return graph.addNode({
    id,
    x,
    y,
    width: 110,
    height: 60,
    zIndex: 10,
    data: {
      type,
      name,
      ip: type === 'HOST' ? '192.168.1.10' : type === 'FIREWALL' ? '192.168.1.1' : type === 'TDENGINE' ? '10.1.1.5' : '192.168.1.254',
      acls: type === 'FIREWALL' ? [
        { id: 'acl_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '放行TDengine时序库端口' },
        { id: 'acl_2', priority: 2, action: 'DENY', destPort: 22, protocol: 'TCP', description: '禁止SSH登录' }
      ] : []
    },
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'text', selector: 'icon' },
      { tagName: 'text', selector: 'title' },
      { tagName: 'text', selector: 'sub' }
    ],
    attrs: {
      body: {
        fill: '#1e293b',
        stroke: color,
        strokeWidth: 1.5,
        rx: 6,
        ry: 6,
        filter: {
          name: 'dropShadow',
          args: { dx: 0, dy: 3, blur: 5, color: '#000000', opacity: 0.5 }
        }
      },
      icon: {
        text: icon,
        fontSize: 20,
        textAnchor: 'middle',
        textVerticalAnchor: 'middle',
        refX: 20,
        refY: '50%'
      },
      title: {
        text: name,
        fill: '#ffffff',
        fontSize: 11,
        fontWeight: 600,
        textAnchor: 'start',
        textVerticalAnchor: 'middle',
        refX: 38,
        refY: 22
      },
      sub: {
        text: subtext || type,
        fill: '#94a3b8',
        fontSize: 9,
        fontFamily: 'monospace',
        textAnchor: 'start',
        textVerticalAnchor: 'middle',
        refX: 38,
        refY: 40
      }
    },
    ports: {
      groups: {
        top: { position: 'top', attrs: { circle: { r: 3, magnet: true, stroke: '#38bdf8', fill: '#0f172a' } } },
        bottom: { position: 'bottom', attrs: { circle: { r: 3, magnet: true, stroke: '#38bdf8', fill: '#0f172a' } } },
        left: { position: 'left', attrs: { circle: { r: 3, magnet: true, stroke: '#38bdf8', fill: '#0f172a' } } },
        right: { position: 'right', attrs: { circle: { r: 3, magnet: true, stroke: '#38bdf8', fill: '#0f172a' } } }
      },
      items: [{ id: 'p_l', group: 'left' }, { id: 'p_r', group: 'right' }, { id: 'p_t', group: 'top' }, { id: 'p_b', group: 'bottom' }]
    }
  })
}

// 载入默认标准拓扑
const loadDemoTopology = () => {
  if (!graph) return
  graph.clearCells()

  // 1. 绘制 4 大安全区域底板
  createZoneBox('zone_it', '管理信息大区 (IT Zone)', 30, 30, 230, 260, 'rgba(147, 197, 253, 0.1)', '#60a5fa', false)
  createZoneBox('zone_dmz', '核心边界隔离区 (DMZ)', 290, 30, 160, 260, 'rgba(254, 202, 202, 0.12)', '#f87171', true)
  createZoneBox('zone_ot', '生产控制大区 (OT Zone)', 480, 30, 260, 220, 'rgba(187, 247, 208, 0.12)', '#4ade80', false)
  createZoneBox('zone_phy', '物理设备层', 480, 270, 260, 100, 'rgba(254, 240, 138, 0.12)', '#facc15', false)

  // 2. 注入各分区内的设备
  const pc = createDeviceNode('HOST', '研发办公PC', '💻', '#38bdf8', 50, 140, '192.168.1.10')!
  const grafana = createDeviceNode('SERVER', 'Grafana看板', '🖥️', '#38bdf8', 120, 70, '(Server)')!
  const itSw = createDeviceNode('SWITCH', 'IT交换机', '🔲', '#38bdf8', 160, 140, 'SW-01')!

  const fw = createDeviceNode('FIREWALL', '工业核心防火墙', '🧱', '#ef4444', 315, 140, '(Firewall)')!

  const tdengine = createDeviceNode('TDENGINE', 'TDengine时序库', '🛢️', '#06b6d4', 590, 60, '(Server)')!
  const mqtt = createDeviceNode('SERVER', 'MQTT Broker', '📟', '#10b981', 590, 130, '(Server)')!
  const neuron = createDeviceNode('NEURON', 'Neuron网关', '🔄', '#22c55e', 590, 190, '(Gateway)')!

  createDeviceNode('SENSOR', '烟雾传感器', '🌡️', '#f59e0b', 500, 300, '(Sensor)')
  createDeviceNode('SENSOR', '温湿传感器', '🌡️', '#f59e0b', 620, 300, '(Sensor)')

  // 3. 构建拓扑连线
  graph.addEdge({ source: { cell: pc.id, port: 'p_r' }, target: { cell: itSw.id, port: 'p_l' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })
  graph.addEdge({ source: { cell: grafana.id, port: 'p_b' }, target: { cell: itSw.id, port: 'p_t' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })

  graph.addEdge({
    id: 'edge_it_fw',
    source: { cell: itSw.id, port: 'p_r' },
    target: { cell: fw.id, port: 'p_l' },
    attrs: { line: { stroke: '#38bdf8', strokeWidth: 2.5 } }
  })

  graph.addEdge({
    id: 'edge_fw_ot',
    source: { cell: fw.id, port: 'p_r' },
    target: { cell: mqtt.id, port: 'p_l' },
    attrs: { line: { stroke: '#38bdf8', strokeWidth: 2.5 } }
  })

  graph.addEdge({ source: { cell: mqtt.id, port: 'p_t' }, target: { cell: tdengine.id, port: 'p_b' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })
  graph.addEdge({ source: { cell: neuron.id, port: 'p_t' }, target: { cell: mqtt.id, port: 'p_b' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })

  graph.zoomToFit({ padding: 30 })
}

// 执行连通性仿真：先自动保存最新拓扑，再触发 Java 后端真实 BFS 算法
const handleSimulate = async () => {
  if (!graph) return
  passBadgeVisible.value = false
  packetVisible.value = true

  addLog('TRACE: 正在向 Spring Boot 仿真引擎同步最新图元拓扑...', 'trace')

  try {
    // 1. 先保存画布最新状态
    await handleSaveTopology()

    const nodes = graph.getNodes().filter(n => !n.id.startsWith('zone_'))
    const hostNode = nodes.find(n => n.getData()?.type === 'HOST') || nodes[0]
    const targetNode = nodes.find(n => n.getData()?.type === 'TDENGINE' || n.getData()?.type === 'SERVER') || nodes[nodes.length - 1]

    if (!hostNode || !targetNode) {
      ElMessage.warning('画布中缺少源设备或目标设备，无法测试')
      return
    }

    addLog(`TRACE: 触发后端真实 BFS 寻路测试: [${hostNode.getData()?.name || hostNode.id}] ➔ [${targetNode.getData()?.name || targetNode.id}] (端口: 6030)`, 'trace')

    // 2. 调用后端真实仿真计算 API
    const res = await runSimulationApi({
      topologyId: topoId.value || 1,
      sourceNodeId: hostNode.id,
      targetNodeId: targetNode.id,
      destPort: 6030,
      protocol: 'TCP'
    })

    const data = res.data
    // 渲染后端回显日志
    if (data.logs && data.logs.length > 0) {
      data.logs.forEach(msg => {
        addLog(msg, msg.includes('SUCCESS') ? 'success' : msg.includes('ERROR') ? 'danger' : 'info')
      })
    }

    if (data.reachable) {
      passBadgeVisible.value = true
      ElMessage.success('后端 BFS 寻路判定：网络完全连通！')
    } else if (data.blocked) {
      ElMessage.error(`被防火墙 ACL 规则 [${data.blockedRuleId}] 拦截阻断！`)
    }
  } catch (err: any) {
    ElMessage.error(err.message || '仿真请求失败')
  }
}

const addLog = (text: string, type: 'info' | 'trace' | 'success' | 'danger') => {
  const now = new Date()
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
  logs.value.push({ id: String(Date.now() + Math.random()), time, text, type })
}

const handleBackToList = () => {
  router.push('/network-sim')
}

const zoomIn = () => graph?.zoom(0.1)
const zoomOut = () => graph?.zoom(-0.1)
const zoomFit = () => graph?.zoomToFit({ padding: 30 })
</script>

<template>
  <div class="network-sim-view">
    <!-- 顶部状态小标题与操作栏 -->
    <div class="view-header">
      <div class="header-left">
        <button class="btn-back" @click="handleBackToList">⬅ 返回方案列表</button>
        <span class="divider">|</span>
        <div class="topo-title">
          <span>仿真拓扑视图：</span>
          <span class="highlight">{{ topoName }}</span>
        </div>
      </div>
      <div class="view-actions">
        <button class="btn-action-primary" :disabled="saving" @click="handleSaveTopology">
          💾 {{ saving ? '保存中...' : '保存拓扑' }}
        </button>
        <button title="自适应画布" @click="zoomFit">⛶</button>
        <button title="放大" @click="zoomIn">🔍+</button>
        <button title="缩小" @click="zoomOut">🔍-</button>
        <button title="载入默认拓扑" @click="loadDemoTopology">↺ 默认拓扑</button>
      </div>
    </div>

    <!-- 工作区主体 -->
    <div class="view-workspace">
      <!-- 左侧设备库 -->
      <DevicePalette />

      <!-- 中间画布与控制台 -->
      <div class="canvas-area">
        <div ref="containerRef" class="x6-wrap"></div>

        <!-- 悬浮报文气泡与通畅浮标 -->
        <div v-if="packetVisible" class="packet-bubble">ICMP Ping 报文 (Port: 6030)</div>
        <div v-if="passBadgeVisible" class="badge-pass">✔ 链路畅通</div>

        <!-- 底部控制台 -->
        <SimulationConsole :logs="logs" />

        <!-- 自定义右键快捷菜单 -->
        <div
          v-if="contextMenu.visible"
          class="custom-context-menu"
          :style="{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }"
          @click.stop
        >
          <div class="menu-header">
            <span class="menu-icon">
              {{ contextMenu.targetType === 'zone' ? '🔲' : contextMenu.targetType === 'edge' ? '⚡' : '🖥️' }}
            </span>
            <span class="menu-title" :title="contextMenu.targetTitle">{{ contextMenu.targetTitle }}</span>
          </div>
          <div class="menu-divider"></div>
          <div class="menu-item is-danger" @click="handleDeleteContextCell">
            <span class="item-icon">🗑️</span>
            <span>删除此{{ contextMenu.targetType === 'zone' ? '大区底板' : contextMenu.targetType === 'edge' ? '链路' : '节点' }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧属性检查器 (支持点选设备节点、物理链路与安全大区底板编辑) -->
      <PropertyInspector
        :selected-node="selectedNode"
        :selected-edge="selectedEdge"
        :selected-zone="selectedZone"
        @update-node="handleUpdateNode"
        @update-edge="handleUpdateEdge"
        @update-zone="handleUpdateZone"
        @simulate="handleSimulate"
      />
    </div>
  </div>
</template>

<style scoped>
.network-sim-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #121820;
  color: #e2e8f0;
  overflow: hidden;
}

.view-header {
  height: 38px;
  background: #18202b;
  border-bottom: 1px solid #2b394b;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-back {
  background: #243040;
  border: 1px solid #334155;
  color: #94a3b8;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
}
.btn-back:hover {
  color: #fff;
  border-color: #38bdf8;
}

.divider {
  color: #334155;
}

.topo-title .highlight {
  color: #38bdf8;
  font-weight: 600;
}

.view-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-action-primary {
  background: #0284c7;
  color: #fff;
  border: none;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.btn-action-primary:hover {
  background: #0369a1;
}

.view-actions button {
  background: transparent;
  border: none;
  color: #8e9cae;
  cursor: pointer;
  font-size: 12px;
}

.view-actions button:hover {
  color: #fff;
}

.view-workspace {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

.canvas-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  background: #151d28;
}

.x6-wrap {
  flex: 1;
  width: 100%;
  height: 100%;
}

.packet-bubble {
  position: absolute;
  top: 145px;
  left: 235px;
  background: #0f172a;
  border: 1px solid #38bdf8;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 10px;
  color: #38bdf8;
  font-family: monospace;
  font-weight: bold;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.4);
  pointer-events: none;
  z-index: 10;
}

.badge-pass {
  position: absolute;
  top: 120px;
  left: 415px;
  background: #14532d;
  color: #4ade80;
  border: 1px solid #22c55e;
  border-radius: 12px;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: bold;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0,0,0,0.4);
}

/* 自定义右键菜单样式 */
.custom-context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 150px;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 4px 0;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.45);
  font-size: 12px;
  animation: fadeIn 0.12s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

.menu-header {
  padding: 6px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #94a3b8;
  font-size: 11px;
  background: #0f172a;
}

.menu-title {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.menu-divider {
  height: 1px;
  background: #334155;
  margin: 3px 0;
}

.menu-item {
  padding: 7px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.15s;
}

.menu-item:hover {
  background: #334155;
}

.menu-item.is-danger {
  color: #f87171;
}

.menu-item.is-danger:hover {
  background: rgba(239, 68, 68, 0.18);
  color: #ef4444;
}

.item-icon {
  font-size: 13px;
}
</style>
