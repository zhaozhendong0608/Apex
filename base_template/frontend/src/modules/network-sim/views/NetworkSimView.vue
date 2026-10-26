<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Graph, Node } from '@antv/x6'
import { Transform } from '@antv/x6-plugin-transform'
import { Snapline } from '@antv/x6-plugin-snapline'
import { Export } from '@antv/x6-plugin-export'
import { ElMessage, type MessageHandler } from 'element-plus'
import DevicePalette, { type DeviceItem } from '../components/DevicePalette.vue'
import PropertyInspector, { type SelectedNodeData, type SelectedEdgeData, type SelectedZoneData } from '../components/PropertyInspector.vue'
import SimulationConsole, { type LogItem } from '../components/SimulationConsole.vue'
import ImportTopologyModal from '../components/ImportTopologyModal.vue'
import { getTopologyDetailApi, saveTopologyApi, runSimulationApi } from '../api/topology'

// 消息提示防重叠防遮挡管理
let activeMessage: MessageHandler | null = null
const showToast = (type: 'success' | 'warning' | 'error' | 'info', message: string, duration = 3000) => {
  if (activeMessage) {
    activeMessage.close()
  }
  activeMessage = ElMessage({
    type,
    message,
    duration,
    grouping: true,
    offset: 45
  })
}

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
  isDisabled?: boolean
}>({
  visible: false,
  x: 0,
  y: 0,
  targetCellId: '',
  targetType: 'node',
  targetTitle: '',
  isDisabled: false
})

const closeContextMenu = () => {
  contextMenu.value.visible = false
}

// 应用图元的故障/正常外观与醒目标签
const applyCellFaultStyle = (cell: any, disabled: boolean) => {
  if (cell.isEdge()) {
    if (disabled) {
      cell.attr('line/stroke', '#ef4444')
      cell.attr('line/strokeDasharray', '6 4')
      cell.attr('line/strokeWidth', 2.5)
      cell.setLabels([
        {
          attrs: {
            text: {
              text: '⚡ [链路切断]',
              fill: '#ef4444',
              fontSize: 10,
              fontWeight: 'bold',
              textAnchor: 'middle'
            },
            rect: {
              fill: '#0f172a',
              stroke: '#ef4444',
              strokeWidth: 1,
              rx: 3,
              ry: 3
            }
          },
          position: 0.5
        }
      ])
    } else {
      cell.attr('line/stroke', '#38bdf8')
      cell.attr('line/strokeDasharray', '0')
      cell.attr('line/strokeWidth', 2)
      cell.setLabels([])
    }
  } else if (cell.isNode() && !cell.id.startsWith('zone_')) {
    if (disabled) {
      cell.attr('body/stroke', '#ef4444')
      cell.attr('body/strokeWidth', 2)
      cell.attr('body/strokeDasharray', '4 3')
      cell.attr('sub/fill', '#ef4444')
      cell.attr('sub/text', '[已宕机离线]')
      cell.attr('badge/text', '⚡ 宕机')
      cell.attr('badge/fill', '#f87171')
      cell.attr('badge/display', 'block')
    } else {
      const data = cell.getData() || {}
      cell.attr('body/stroke', data.color || '#38bdf8')
      cell.attr('body/strokeWidth', 1.5)
      cell.attr('body/strokeDasharray', '0')
      cell.attr('sub/fill', '#94a3b8')
      cell.attr('sub/text', data.ip || '192.168.1.1')
      cell.attr('badge/display', 'none')
    }
  }
}

// 切换图元故障状态 (供右键菜单调用)
const toggleContextCellFault = () => {
  if (!graph || !contextMenu.value.targetCellId) return
  const cell = graph.getCellById(contextMenu.value.targetCellId)
  if (!cell) return

  const currentData = cell.getData() || {}
  const nextDisabled = !currentData.disabled

  cell.setData({
    ...currentData,
    disabled: nextDisabled
  })

  applyCellFaultStyle(cell, nextDisabled)

  const title = contextMenu.value.targetTitle
  if (nextDisabled) {
    ElMessage.warning(`已将 [${title}] 注入为故障/宕机状态！`)
    addLog(`WARN: 注入故障！图元 [${title}] 状态置为不可用 (disabled=true)`, 'danger')
  } else {
    ElMessage.success(`[${title}] 已恢复正常运行！`)
    addLog(`INFO: 恢复运行，图元 [${title}] 已解除故障 (disabled=false)`, 'success')
  }

  // 同步更新已选中右侧面板属性
  if (selectedNode.value?.id === cell.id) {
    selectedNode.value.disabled = nextDisabled
  }
  if (selectedEdge.value?.id === cell.id) {
    selectedEdge.value.disabled = nextDisabled
  }

  closeContextMenu()
}

// 统一根据 ID 删除图元（供右键与 Delete 键盘事件复用）
const deleteCellById = (cellId: string, cellTitle?: string) => {
  if (!graph || !cellId) return
  const cell = graph.getCellById(cellId)
  if (cell) {
    const title = cellTitle || (cell.isNode() ? (cell.getData()?.name || '节点') : '链路')
    cell.remove()
    ElMessage.success(`已删除: ${title}`)
    addLog(`INFO: 已移除画布图元 [${title}]`, 'info')

    if (selectedNode.value?.id === cellId) selectedNode.value = null
    if (selectedEdge.value?.id === cellId) selectedEdge.value = null
    if (selectedZone.value?.id === cellId) selectedZone.value = null
  }
}

// 右键菜单触发删除
const handleDeleteContextCell = () => {
  deleteCellById(contextMenu.value.targetCellId, contextMenu.value.targetTitle)
  closeContextMenu()
}

// 键盘 Delete / Backspace 快捷删除当前选中的图元
const handleKeyDown = (e: KeyboardEvent) => {
  // 如果焦点在 input、textarea 或 select 内部，不触发图元删除
  const activeEl = document.activeElement
  if (activeEl && ['INPUT', 'TEXTAREA', 'SELECT'].includes(activeEl.tagName)) {
    return
  }

  if (e.key === 'Delete' || e.key === 'Backspace') {
    const currentSelectedId = selectedNode.value?.id || selectedEdge.value?.id || selectedZone.value?.id
    if (currentSelectedId) {
      e.preventDefault()
      const title = selectedNode.value?.name || selectedZone.value?.name || (selectedEdge.value ? '物理网络链路' : '')
      deleteCellById(currentSelectedId, title)
    }
  }
}

const logs = ref<LogItem[]>([
  { id: '1', time: '10:00:01', text: 'INFO: 拓扑工作区就绪，等待加载方案数据...', type: 'info' }
])

const packetVisible = ref(false)
const passBadgeVisible = ref(false)

// 阻断红色脉冲警告圈与拦截气泡状态
const blockedPulse = ref<{
  visible: boolean
  x: number
  y: number
  width: number
  height: number
  nodeId: string
}>({
  visible: false,
  x: 0,
  y: 0,
  width: 110,
  height: 60,
  nodeId: ''
})

const blockedTooltip = ref<{
  visible: boolean
  x: number
  y: number
  ruleId: string
  reason: string
  nodeName: string
}>({
  visible: false,
  x: 0,
  y: 0,
  ruleId: '',
  reason: '',
  nodeName: ''
})

// 动态将图元的世界坐标转换为当前画布容器内的视口像素坐标
const updateBlockedVisualPosition = () => {
  if (!graph || !blockedPulse.value.visible || !blockedPulse.value.nodeId) return
  const cell = graph.getCellById(blockedPulse.value.nodeId)
  if (!cell || !cell.isNode()) return

  const bbox = cell.getBBox()
  // 转换图元坐标至画布容器相对像素坐标
  const p = graph.localToClient(bbox.x, bbox.y)
  const containerRect = containerRef.value?.getBoundingClientRect()
  if (!containerRect) return

  const zoom = graph.zoom()
  const relX = p.x - containerRect.left
  const relY = p.y - containerRect.top
  const scaledW = bbox.width * zoom
  const scaledH = bbox.height * zoom

  blockedPulse.value.x = relX
  blockedPulse.value.y = relY
  blockedPulse.value.width = scaledW
  blockedPulse.value.height = scaledH

  // 悬浮气泡置于节点上方居中
  blockedTooltip.value.x = relX + scaledW / 2
  blockedTooltip.value.y = relY - 12
}

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
    panning: {
      enabled: true,
      eventTypes: ['leftMouseDown', 'rightMouseDown']
    },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
    connecting: {
      snap: true,
      allowBlank: false,
      allowLoop: false,
      allowNode: true,
      allowPort: true,
      allowMulti: true, // 允许同一端口/节点之间连接多条链路，彻底支持随意自由多连
      allowEdge: false,
      highlight: true,
      connector: 'rounded',
      router: 'orth',
      validateConnection({ sourceCell, targetCell }) {
        // 禁止大区底板参与连线
        if (sourceCell?.id.startsWith('zone_') || targetCell?.id.startsWith('zone_')) {
          return false
        }
        // 禁止自连
        if (sourceCell?.id === targetCell?.id) {
          return false
        }
        return true
      },
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

  // 监听连线端点改接事件：当把已有连线拖拽重新连接到其他设备时触发
  graph.on('edge:connected', ({ type }) => {
    addLog(`INFO: 物理链路已成功重新挂载至新设备 (改接端点: ${type === 'source' ? '源端' : '宿端'})`, 'info')
  })

  // 启用对角/四角自由缩放插件 (仅对大区底板 zone_ 生效)
  graph.use(
    new Transform({
      resizing: {
        enabled: (node: Node) => node.id.startsWith('zone_'),
        minWidth: 140,
        minHeight: 100,
        orthogonal: false,
        restrict: false,
        autoScroll: false
      }
    })
  )

  // 启用智能对齐辅助线与吸附插件 (Snapline)
  graph.use(
    new Snapline({
      enabled: true,
      tolerance: 8,
      sharp: true,
      className: 'custom-snapline'
    })
  )

  // 启用高清导出插件 (Export)
  graph.use(new Export())

  // 记录大区移动前的子节点相对位置，实现大区拖动时联动移动内部所有设备节点
  let movingZoneChildren: Array<{ node: Node; offsetX: number; offsetY: number }> = []

  graph.on('node:mousedown', ({ node }) => {
    if (node.id.startsWith('zone_')) {
      const zoneBBox = node.getBBox()
      movingZoneChildren = []

      // 找出当前在大区几何矩形范围内的所有设备节点
      graph!.getNodes().forEach(n => {
        if (!n.id.startsWith('zone_')) {
          const nBBox = n.getBBox()
          // 只要设备节点的中心点落在大区底板内部，即视为大区归属节点
          const centerX = nBBox.x + nBBox.width / 2
          const centerY = nBBox.y + nBBox.height / 2
          if (
            centerX >= zoneBBox.x &&
            centerX <= zoneBBox.x + zoneBBox.width &&
            centerY >= zoneBBox.y &&
            centerY <= zoneBBox.y + zoneBBox.height
          ) {
            movingZoneChildren.push({
              node: n,
              offsetX: nBBox.x - zoneBBox.x,
              offsetY: nBBox.y - zoneBBox.y
            })
          }
        }
      })
    } else {
      movingZoneChildren = []
    }
  })

  // 当大区位置改变时，实时位移内部子节点
  graph.on('node:change:position', ({ node, current }) => {
    if (node.id.startsWith('zone_') && movingZoneChildren.length > 0 && current) {
      movingZoneChildren.forEach(item => {
        item.node.setPosition(current.x + item.offsetX, current.y + item.offsetY)
      })
    }
    if (blockedPulse.value.visible && node.id === blockedPulse.value.nodeId) {
      updateBlockedVisualPosition()
    }
  })

  // 画布平移与缩放时同步更新警告脉冲与气泡视口坐标
  graph.on('translate', () => {
    if (blockedPulse.value.visible) updateBlockedVisualPosition()
  })
  graph.on('scale', () => {
    if (blockedPulse.value.visible) updateBlockedVisualPosition()
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
        fontSize: label.fontSize || 11,
        subnetPrefix: data.subnetPrefix || (node.id.includes('it') ? '192.168.1' : node.id.includes('dmz') ? '10.0.0' : node.id.includes('ot') ? '172.16.1' : node.id.includes('field') ? '172.16.2' : '192.168.10'),
        gateway: data.gateway || `${data.subnetPrefix || '192.168.1'}.1`
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
      gateway: data.gateway || '',
      disabled: Boolean(data.disabled),
      acls: data.acls || [],
      routes: data.routes || [],
      natRules: data.natRules || []
    }
  })

  // 监听图元点击事件：选中链路并反查源宿节点真实名称，同时挂载连线端点自由改接手柄 (Arrowhead Tools)
  graph.on('edge:click', ({ edge }) => {
    // 先清理此前连线上的手柄工具
    graph!.getEdges().forEach(e => e.removeTools())

    // 为当前选中的链路挂载源端与宿端改接手柄
    edge.addTools([
      {
        name: 'source-arrowhead',
        args: {
          attrs: {
            fill: '#38bdf8',
            stroke: '#0f172a',
            strokeWidth: 1.5,
            cursor: 'move'
          }
        }
      },
      {
        name: 'target-arrowhead',
        args: {
          attrs: {
            fill: '#38bdf8',
            stroke: '#0f172a',
            strokeWidth: 1.5,
            cursor: 'move'
          }
        }
      }
    ])

    selectedNode.value = null
    selectedZone.value = null

    const sourceCell = edge.getSourceCell()
    const targetCell = edge.getTargetCell()

    let sourceName = '源设备'
    let targetName = '宿设备'

    if (sourceCell && sourceCell.isNode()) {
      const sData = sourceCell.getData() || {}
      sourceName = sData.name || (sourceCell.getAttrs()?.title as any)?.text || sourceCell.id
    }
    if (targetCell && targetCell.isNode()) {
      const tData = targetCell.getData() || {}
      targetName = tData.name || (targetCell.getAttrs()?.title as any)?.text || targetCell.id
    }

    selectedEdge.value = {
      id: edge.id,
      sourceName,
      targetName,
      bandwidth: edge.getData()?.bandwidth || '1000',
      latencyMs: edge.getData()?.latencyMs ?? 2,
      disabled: Boolean(edge.getData()?.disabled)
    }
    closeContextMenu()
  })

  // 监听图元右键事件：节点与大区底板
  graph.on('node:contextmenu', ({ node, e }) => {
    e.preventDefault()
    const isZone = node.id.startsWith('zone_')
    const attrs = node.getAttrs()
    const data = node.getData() || {}
    const title = isZone
      ? ((attrs?.label as any)?.text || '大区底板')
      : ((attrs?.title as any)?.text || data.name || '设备节点')

    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      targetCellId: node.id,
      targetType: isZone ? 'zone' : 'node',
      targetTitle: title,
      isDisabled: Boolean(data.disabled)
    }
  })

  // 监听图元右键事件：链路
  graph.on('edge:contextmenu', ({ edge, e }) => {
    e.preventDefault()
    const data = edge.getData() || {}
    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      targetCellId: edge.id,
      targetType: 'edge',
      targetTitle: '物理网络链路',
      isDisabled: Boolean(data.disabled)
    }
  })

  // 点击空白处取消选择并关闭右键菜单，清除所有连线改接工具
  graph.on('blank:click', () => {
    graph!.getEdges().forEach(e => e.removeTools())
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
        const dropX = e.clientX - rect.left - 55
        const dropY = e.clientY - rect.top - 30
        const centerDropX = dropX + 55
        const centerDropY = dropY + 30

        // 智能检测当前放置位置是否位于某个大区底板范围内
        const zoneNodes = graph.getNodes().filter(n => n.id.startsWith('zone_'))
        const matchedZone = zoneNodes.find(z => {
          const zBox = z.getBBox()
          return centerDropX >= zBox.x && centerDropX <= zBox.x + zBox.width &&
                 centerDropY >= zBox.y && centerDropY <= zBox.y + zBox.height
        })

        let inheritedIp: string | undefined
        let inheritedZone: string | undefined
        let inheritedGateway: string | undefined

        if (matchedZone) {
          const zData = matchedZone.getData() || {}
          const prefix = zData.subnetPrefix || (
            matchedZone.id.includes('it') ? '192.168.1' :
            matchedZone.id.includes('dmz') ? '10.0.0' :
            matchedZone.id.includes('ot') ? '172.16.1' :
            matchedZone.id.includes('field') ? '172.16.2' : '192.168.10'
          )
          inheritedZone = matchedZone.id.includes('it') ? 'IT_ZONE' :
                          matchedZone.id.includes('dmz') ? 'DMZ_ZONE' :
                          matchedZone.id.includes('ot') ? 'OT_ZONE' : 'PHY_ZONE'
          inheritedGateway = zData.gateway || `${prefix}.1`

          // 自动分配该大区内未占用的末位 IP (Host ID)
          const existingIps = graph.getNodes()
            .filter(n => !n.id.startsWith('zone_'))
            .map(n => n.getData()?.ip as string)
            .filter(ip => ip && ip.startsWith(`${prefix}.`))

          let hostId = 10
          while (existingIps.includes(`${prefix}.${hostId}`) && hostId < 254) {
            hostId++
          }
          inheritedIp = `${prefix}.${hostId}`
          addLog(`INFO: 设备 [${item.name}] 落在 [${(matchedZone.getAttrs()?.label as any)?.text || matchedZone.id}]，自动继承网段 ${prefix}.0/24 (网关: ${inheritedGateway})，智能分配 IP: ${inheritedIp}`, 'info')
        }

        createDeviceNode(item.type, item.name, item.icon, item.color, dropX, dropY, {
          ip: inheritedIp,
          zone: inheritedZone,
          gateway: inheritedGateway
        })
        addLog(`INFO: 已向画布添加设备节点 [${item.name}]`, 'info')
        refreshAvailableNodes()
      }
    } catch (parseErr) {
      console.error('解析拖拽数据失败', parseErr)
    }
  })

  // 挂载键盘 Delete / Backspace 监听
  window.addEventListener('keydown', handleKeyDown)

  // 根据路由 ID 拉取数据库方案，否则载入默认 Demo
  if (topoId.value) {
    await loadTopologyFromBackend(topoId.value)
  } else {
    loadDemoTopology()
  }
  refreshAvailableNodes()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
  if (graph) {
    graph.dispose()
  }
})

// 处理右侧面板修改节点属性后的动态回写
const handleUpdateNode = (updated: SelectedNodeData) => {
  if (!graph) return
  const node = graph.getCellById(updated.id)
  if (node && node.isNode()) {
    const isFault = Boolean(updated.disabled)
    // 1. 回写节点业务 data
    node.setData({
      name: updated.name,
      type: updated.type,
      zone: updated.zone,
      ip: updated.ip,
      subnet: updated.subnet,
      gateway: updated.gateway,
      disabled: isFault,
      acls: updated.acls,
      routes: updated.routes || [],
      natRules: updated.natRules || []
    })

    // 2. 实时刷新 SVG 图元上的文字与副标题 IP 以及故障外观
    node.setAttrByPath('title/text', updated.name)
    node.setAttrByPath('sub/text', updated.ip)
    applyCellFaultStyle(node, isFault)
  }
}

// 处理右侧面板修改链路属性后的动态回写
const handleUpdateEdge = (updated: SelectedEdgeData) => {
  if (!graph) return
  const edge = graph.getCellById(updated.id)
  if (edge && edge.isEdge()) {
    const isFault = Boolean(updated.disabled)
    edge.setData({
      bandwidth: updated.bandwidth,
      latencyMs: updated.latencyMs,
      disabled: isFault
    })
    applyCellFaultStyle(edge, isFault)
  }
}

// 处理右侧面板修改大区底板属性后的动态回写
const handleUpdateZone = (updated: SelectedZoneData) => {
  if (!graph) return
  const zoneNode = graph.getCellById(updated.id)
  if (zoneNode && zoneNode.isNode()) {
    // 1. 同步业务数据 (包含网段前缀与默认网关)
    zoneNode.setData({
      name: updated.name,
      subnetPrefix: updated.subnetPrefix,
      gateway: updated.gateway,
      borderColor: updated.borderColor,
      bgColor: updated.bgColor,
      isDashed: updated.isDashed,
      fontSize: updated.fontSize
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
      try {
        const data = JSON.parse(res.data.canvasData)
        if (data.cells && data.cells.length > 0) {
          graph.fromJSON(data)
          graph.zoomToFit({ padding: 30 })
          addLog(`INFO: 成功从数据库载入拓扑方案 [${res.data.name}] (包含 ${data.cells.length} 个图元)`, 'success')
          return
        }
      } catch (parseErr) {
        console.warn('解析历史画布数据失败，初始化为空画布', parseErr)
      }
    }
    // 全新创建的拓扑：保持纯净空白画布，不强制填充默认 Demo
    addLog(`INFO: 已打开全新拓扑方案 [${res.data.name}]。当前画布为空白，请从左侧拖拽资产或使用上方“📥 逆向导入”！`, 'info')
  } catch (error: any) {
    ElMessage.error(error.message || '加载拓扑详情失败')
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

// 创建安全大区 (SVG 标准图元，支持配置子网前缀与默认网关)
const createZoneBox = (
  id: string,
  title: string,
  x: number,
  y: number,
  width: number,
  height: number,
  bgColor: string,
  borderColor: string,
  isDashed = false,
  options: {
    subnetPrefix?: string
    gateway?: string
    fontSize?: number
  } = {}
) => {
  if (!graph) return
  const prefix = options.subnetPrefix || (
    id.includes('it') ? '192.168.1' :
    id.includes('dmz') ? '10.0.0' :
    id.includes('ot') ? '172.16.1' :
    id.includes('field') ? '172.16.2' : '192.168.10'
  )
  const defaultGw = options.gateway || `${prefix}.1`

  return graph.addNode({
    id,
    x,
    y,
    width,
    height,
    zIndex: 1,
    data: {
      name: title,
      subnetPrefix: prefix,
      gateway: defaultGw,
      borderColor,
      bgColor,
      isDashed,
      fontSize: options.fontSize || 11
    },
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
        fontSize: options.fontSize || 11,
        fontWeight: 'bold',
        textAnchor: 'start',
        textVerticalAnchor: 'top',
        refX: 12,
        refY: 8
      }
    }
  })
}

// 创建设备节点 (SVG 标准图元，支持传入完整工业配置参数)
const createDeviceNode = (
  type: string,
  name: string,
  icon: string,
  color: string,
  x: number,
  y: number,
  options: {
    ip?: string
    subnet?: string
    gateway?: string
    zone?: string
    acls?: any[]
    routes?: any[]
    natRules?: any[]
    subtext?: string
  } = {}
) => {
  if (!graph) return null
  const id = 'dev_' + Math.random().toString(36).substring(2, 8)

  const defaultIp = options.ip || (
    type === 'HOST' ? '192.168.1.10' :
    type === 'ROUTER' ? '192.168.1.1' :
    type === 'FIREWALL' ? '10.0.0.1' :
    type === 'GAP' ? '10.0.0.2' :
    type === 'PLC' ? '172.16.1.50' :
    type === 'TDENGINE' ? '172.16.1.100' :
    type === 'SERVER' ? '192.168.1.5' : '192.168.1.254'
  )

  const defaultSubnet = options.subnet || '255.255.255.0'
  const defaultZone = options.zone || (
    ['HOST', 'SERVER', 'ROUTER'].includes(type) ? 'IT_ZONE' :
    ['FIREWALL', 'GAP'].includes(type) ? 'DMZ_ZONE' :
    ['SWITCH', 'PLC', 'NEURON', 'TDENGINE'].includes(type) ? 'OT_ZONE' : 'PHY_ZONE'
  )

  const defaultGw = options.gateway || (
    defaultZone === 'IT_ZONE' ? '192.168.1.1' :
    defaultZone === 'DMZ_ZONE' ? '10.0.0.1' :
    defaultZone === 'OT_ZONE' ? '172.16.1.1' : '172.16.2.1'
  )

  const defaultAcls = options.acls || (
    type === 'FIREWALL' ? [
      { id: 'acl_fw_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '放行TDengine时序写入与查询' },
      { id: 'acl_fw_2', priority: 2, action: 'PERMIT', destPort: 1883, protocol: 'TCP', description: '放行工业MQTT物联数据采集' },
      { id: 'acl_fw_3', priority: 3, action: 'DENY', destPort: 22, protocol: 'TCP', description: '阻断跨大区SSH远程管理' }
    ] :
    type === 'GAP' ? [
      { id: 'gap_rule_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '单向摆渡：时序指标归档' },
      { id: 'gap_rule_2', priority: 2, action: 'DENY', destPort: 80, protocol: 'TCP', description: '阻断反向HTTP穿透请求' }
    ] :
    type === 'ROUTER' ? [
      { id: 'rt_acl_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '默认放行核心业务网段' }
    ] : []
  )

  const defaultRoutes = options.routes || (
    type === 'ROUTER' ? [
      { id: 'rt_route_1', destSubnet: '172.16.1.0', mask: '255.255.255.0', nextHopIp: '10.0.0.1', description: '前往OT核心生产网段(下一跳边界防火墙)' },
      { id: 'rt_route_2', destSubnet: '0.0.0.0', mask: '0.0.0.0', nextHopIp: '10.0.0.1', description: '默认缺省路由网关' }
    ] : []
  )

  const defaultNatRules = options.natRules || (
    type === 'FIREWALL' ? [
      { id: 'nat_fw_1', type: 'SNAT', origSrc: '192.168.1.0/24', transSrc: '10.0.0.1', description: 'IT管理区出境源地址伪装(SNAT)' }
    ] : []
  )

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
      ip: defaultIp,
      subnet: defaultSubnet,
      gateway: defaultGw,
      zone: defaultZone,
      acls: defaultAcls,
      routes: defaultRoutes,
      natRules: defaultNatRules
    },
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'text', selector: 'icon' },
      { tagName: 'text', selector: 'title' },
      { tagName: 'text', selector: 'sub' },
      { tagName: 'text', selector: 'badge' }
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
      badge: {
        text: '',
        fill: '#ffffff',
        fontSize: 9,
        fontWeight: 'bold',
        textAnchor: 'end',
        refX: 104,
        refY: 10,
        display: 'none'
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
        text: options.subtext || defaultIp,
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
        top: {
          position: 'top',
          attrs: {
            circle: {
              r: 4.5,
              magnet: true,
              stroke: '#38bdf8',
              strokeWidth: 1.5,
              fill: '#0f172a',
              cursor: 'crosshair',
              style: {
                transition: 'transform 0.15s, stroke 0.15s'
              }
            }
          }
        },
        bottom: {
          position: 'bottom',
          attrs: {
            circle: {
              r: 4.5,
              magnet: true,
              stroke: '#38bdf8',
              strokeWidth: 1.5,
              fill: '#0f172a',
              cursor: 'crosshair',
              style: {
                transition: 'transform 0.15s, stroke 0.15s'
              }
            }
          }
        },
        left: {
          position: 'left',
          attrs: {
            circle: {
              r: 4.5,
              magnet: true,
              stroke: '#38bdf8',
              strokeWidth: 1.5,
              fill: '#0f172a',
              cursor: 'crosshair',
              style: {
                transition: 'transform 0.15s, stroke 0.15s'
              }
            }
          }
        },
        right: {
          position: 'right',
          attrs: {
            circle: {
              r: 4.5,
              magnet: true,
              stroke: '#38bdf8',
              strokeWidth: 1.5,
              fill: '#0f172a',
              cursor: 'crosshair',
              style: {
                transition: 'transform 0.15s, stroke 0.15s'
              }
            }
          }
        }
      },
      items: [
        { id: 'p_l', group: 'left' },
        { id: 'p_r', group: 'right' },
        { id: 'p_t', group: 'top' },
        { id: 'p_b', group: 'bottom' }
      ]
    }
  })
}

// 载入默认标准工业分级拓扑 (IT大区 ➔ 核心路由 ➔ DMZ防火墙与隔离网闸 ➔ OT生产大区工业交换机/PLC/网关)
const loadDemoTopology = () => {
  if (!graph) return
  graph.clearCells()

  // 1. 绘制 4 大工业级安全防护分区底板 (显式注入专业网段前缀与默认网关)
  createZoneBox('zone_it', '管理信息大区 (IT Zone - 192.168.1.0/24)', 30, 30, 240, 280, 'rgba(147, 197, 253, 0.08)', '#60a5fa', false, {
    subnetPrefix: '192.168.1',
    gateway: '192.168.1.1'
  })
  createZoneBox('zone_dmz', '边界隔离与DMZ (10.0.0.0/24)', 300, 30, 180, 280, 'rgba(254, 202, 202, 0.1)', '#f87171', true, {
    subnetPrefix: '10.0.0',
    gateway: '10.0.0.1'
  })
  createZoneBox('zone_ot', '生产控制大区 (OT Zone - 172.16.1.0/24)', 510, 30, 280, 280, 'rgba(187, 247, 208, 0.08)', '#4ade80', false, {
    subnetPrefix: '172.16.1',
    gateway: '172.16.1.1'
  })
  createZoneBox('zone_field', '现场过程监控与物理层 (172.16.2.0/24)', 510, 330, 280, 110, 'rgba(254, 240, 138, 0.08)', '#facc15', false, {
    subnetPrefix: '172.16.2',
    gateway: '172.16.2.1'
  })

  // 2. 注入各分区内的标准工业设备 (配置详细业务参数、IP及真实安全策略)
  // [IT 区]
  const pc = createDeviceNode('HOST', '工程师站PC', '💻', '#38bdf8', 50, 130, {
    ip: '192.168.1.10',
    subnet: '255.255.255.0',
    zone: 'IT_ZONE',
    subtext: '192.168.1.10'
  })!
  const grafana = createDeviceNode('SERVER', '监控大屏服务', '🖥️', '#a855f7', 50, 210, {
    ip: '192.168.1.5',
    subnet: '255.255.255.0',
    zone: 'IT_ZONE',
    subtext: '192.168.1.5:3000'
  })!
  const itRouter = createDeviceNode('ROUTER', 'IT核心路由器', '🌐', '#38bdf8', 150, 130, {
    ip: '192.168.1.1',
    subnet: '255.255.255.0',
    zone: 'IT_ZONE',
    subtext: '网关 192.168.1.1',
    acls: [
      { id: 'rt_acl_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '放行访问OT时序数据库' },
      { id: 'rt_acl_2', priority: 2, action: 'PERMIT', destPort: 1883, protocol: 'TCP', description: '放行边缘MQTT遥测消息' }
    ]
  })!

  // [DMZ 隔离区]
  const fw = createDeviceNode('FIREWALL', '边界防火墙', '🧱', '#ef4444', 335, 80, {
    ip: '10.0.0.1',
    subnet: '255.255.255.0',
    zone: 'DMZ_ZONE',
    subtext: 'DMZ-FW01',
    acls: [
      { id: 'fw_rule_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '放行TDengine数据湖写入' },
      { id: 'fw_rule_2', priority: 2, action: 'PERMIT', destPort: 1883, protocol: 'TCP', description: '放行工业MQTT数据汇聚' },
      { id: 'fw_rule_3', priority: 3, action: 'DENY', destPort: 22, protocol: 'TCP', description: '严禁跨区SSH远程调试' },
      { id: 'fw_rule_4', priority: 4, action: 'DENY', destPort: 3389, protocol: 'TCP', description: '严禁跨区RDP远程桌面' }
    ]
  })!
  const gap = createDeviceNode('GAP', '单向安全网闸', '🛡️', '#f97316', 335, 180, {
    ip: '10.0.0.2',
    subnet: '255.255.255.0',
    zone: 'DMZ_ZONE',
    subtext: '单向隔离摆渡',
    acls: [
      { id: 'gap_1', priority: 1, action: 'PERMIT', destPort: 6030, protocol: 'TCP', description: '工业OT向IT单向摆渡时序指标' },
      { id: 'gap_2', priority: 2, action: 'DENY', destPort: 80, protocol: 'TCP', description: '阻断所有外部反向控制信令' }
    ]
  })!

  // [OT 生产控制区]
  const otSw = createDeviceNode('SWITCH', '工业核心交换机', '🔲', '#0ea5e9', 530, 130, {
    ip: '172.16.1.1',
    subnet: '255.255.255.0',
    zone: 'OT_ZONE',
    subtext: '三层核心交换机'
  })!
  const tdengine = createDeviceNode('TDENGINE', '时序数据库', '🛢️', '#06b6d4', 660, 60, {
    ip: '172.16.1.100',
    subnet: '255.255.255.0',
    zone: 'OT_ZONE',
    subtext: '端口: 6030'
  })!
  const neuron = createDeviceNode('NEURON', 'Neuron工业网关', '🔄', '#22c55e', 660, 140, {
    ip: '172.16.1.10',
    subnet: '255.255.255.0',
    zone: 'OT_ZONE',
    subtext: 'Modbus/OPC-UA'
  })!
  const plc = createDeviceNode('PLC', '产线控制器PLC', '🎛️', '#8b5cf6', 660, 220, {
    ip: '172.16.1.50',
    subnet: '255.255.255.0',
    zone: 'OT_ZONE',
    subtext: 'S7-1500 工控机'
  })!

  // [物理层]
  const sensor1 = createDeviceNode('SENSOR', '振动传感器', '🌡️', '#f59e0b', 530, 360, {
    ip: '172.16.2.11',
    subnet: '255.255.255.0',
    zone: 'PHY_ZONE',
    subtext: '4-20mA 模拟量'
  })!
  const sensor2 = createDeviceNode('SENSOR', '温湿传感器', '🌡️', '#f59e0b', 660, 360, {
    ip: '172.16.2.12',
    subnet: '255.255.255.0',
    zone: 'PHY_ZONE',
    subtext: 'Modbus-RTU 485'
  })!

  // 3. 构建规范连线
  // IT 内部汇聚连线
  graph.addEdge({ source: { cell: pc.id, port: 'p_r' }, target: { cell: itRouter.id, port: 'p_l' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })
  graph.addEdge({ source: { cell: grafana.id, port: 'p_r' }, target: { cell: itRouter.id, port: 'p_b' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })

  // 跨区域边界路由与隔离连线 (经由路由器 ➔ 防火墙/网闸)
  graph.addEdge({
    id: 'edge_router_fw',
    source: { cell: itRouter.id, port: 'p_r' },
    target: { cell: fw.id, port: 'p_l' },
    attrs: { line: { stroke: '#38bdf8', strokeWidth: 2.5 } }
  })
  graph.addEdge({
    id: 'edge_router_gap',
    source: { cell: itRouter.id, port: 'p_r' },
    target: { cell: gap.id, port: 'p_l' },
    attrs: { line: { stroke: '#f97316', strokeWidth: 2, strokeDasharray: '4 4' } }
  })

  // DMZ ➔ OT 工业交换机
  graph.addEdge({
    id: 'edge_fw_otsw',
    source: { cell: fw.id, port: 'p_r' },
    target: { cell: otSw.id, port: 'p_l' },
    attrs: { line: { stroke: '#38bdf8', strokeWidth: 2.5 } }
  })
  graph.addEdge({
    id: 'edge_gap_otsw',
    source: { cell: gap.id, port: 'p_r' },
    target: { cell: otSw.id, port: 'p_l' },
    attrs: { line: { stroke: '#f97316', strokeWidth: 2 } }
  })

  // OT 内部互联与工业采集链路
  graph.addEdge({ source: { cell: otSw.id, port: 'p_r' }, target: { cell: tdengine.id, port: 'p_l' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })
  graph.addEdge({ source: { cell: otSw.id, port: 'p_r' }, target: { cell: neuron.id, port: 'p_l' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })
  graph.addEdge({ source: { cell: otSw.id, port: 'p_r' }, target: { cell: plc.id, port: 'p_l' }, attrs: { line: { stroke: '#38bdf8', strokeWidth: 2 } } })

  // 物理传感器接入控制器与网关
  graph.addEdge({ source: { cell: sensor1.id, port: 'p_t' }, target: { cell: plc.id, port: 'p_b' }, attrs: { line: { stroke: '#f59e0b', strokeWidth: 1.5, strokeDasharray: '2 2' } } })
  graph.addEdge({ source: { cell: sensor2.id, port: 'p_t' }, target: { cell: neuron.id, port: 'p_b' }, attrs: { line: { stroke: '#f59e0b', strokeWidth: 1.5, strokeDasharray: '2 2' } } })

  graph.zoomToFit({ padding: 30 })
}

// 提取画布中可用设备节点列表供演练控制台选择
const availableNodes = ref<Array<{ id: string; name: string; ip: string; type: string }>>([])

const refreshAvailableNodes = () => {
  if (!graph) return
  const nodes = graph.getNodes().filter(n => !n.id.startsWith('zone_'))
  availableNodes.value = nodes.map(n => {
    const data = n.getData() || {}
    const attrs = n.getAttrs()
    return {
      id: n.id,
      name: (attrs?.title as any)?.text || data.name || n.id,
      ip: (attrs?.sub as any)?.text || data.ip || '192.168.1.1',
      type: data.type || 'HOST'
    }
  })
}

// 仿真计算中状态
const simulating = ref(false)

// 清理高亮与流光特效 (保留故障注入状态外观)
const resetEdgeStyles = () => {
  if (!graph) return
  graph.getEdges().forEach(edge => {
    const isFault = Boolean(edge.getData()?.disabled)
    if (isFault) {
      edge.attr('line/stroke', '#ef4444')
      edge.attr('line/strokeWidth', 2)
      edge.attr('line/strokeDasharray', '5 5')
      edge.attr('line/style', {})
    } else {
      edge.attr('line/stroke', '#38bdf8')
      edge.attr('line/strokeWidth', 2)
      edge.attr('line/strokeDasharray', '0')
      edge.attr('line/style', {})
    }
  })
  graph.getNodes().forEach(n => {
    if (!n.id.startsWith('zone_')) {
      const isFault = Boolean(n.getData()?.disabled)
      if (isFault) {
        n.attr('body/stroke', '#ef4444')
        n.attr('body/strokeWidth', 2)
        n.attr('body/strokeDasharray', '4 3')
      } else {
        n.attr('body/stroke', n.getData()?.color || '#38bdf8')
        n.attr('body/strokeWidth', 1.5)
        n.attr('body/strokeDasharray', '0')
      }
    }
  })
}

// 执行端到端连通性仿真：支持从演练控制台传入自定义源端、宿端、端口与协议
const handleSimulate = async (customParams?: { sourceId: string; targetId: string; destPort: number; protocol: string }) => {
  if (!graph) return
  passBadgeVisible.value = false
  packetVisible.value = false
  blockedPulse.value.visible = false
  blockedTooltip.value.visible = false
  simulating.value = true

  // 重置此前的高亮连线与节点样式
  resetEdgeStyles()

  try {
    // 1. 先保存画布最新状态
    await handleSaveTopology()
    refreshAvailableNodes()

    let srcId = customParams?.sourceId
    let dstId = customParams?.targetId
    let dPort = customParams?.destPort ?? 6030
    let proto = customParams?.protocol ?? 'TCP'

    const nodes = graph.getNodes().filter(n => !n.id.startsWith('zone_'))
    const hostNode = nodes.find(n => n.id === srcId) || nodes.find(n => n.getData()?.type === 'HOST') || nodes[0]
    const targetNode = nodes.find(n => n.id === dstId) || nodes.find(n => n.getData()?.type === 'TDENGINE' || n.getData()?.type === 'SERVER') || nodes[nodes.length - 1]

    if (!hostNode || !targetNode) {
      ElMessage.warning('画布中缺少源设备或目标设备，无法测试')
      return
    }

    srcId = hostNode.id
    dstId = targetNode.id

    const srcName = hostNode.getData()?.name || hostNode.id
    const dstName = targetNode.getData()?.name || targetNode.id

    addLog(`TRACE: 触发后端真实 BFS 寻路测试: [${srcName}] ➔ [${dstName}] | 端口: ${dPort}, 协议: ${proto}`, 'trace')

    // 2. 调用后端真实仿真计算 API
    const res = await runSimulationApi({
      topologyId: topoId.value || 1,
      sourceNodeId: srcId,
      targetNodeId: dstId,
      destPort: dPort,
      protocol: proto
    })

    const data = res.data

    // 渲染后端回显日志
    if (data.logs && data.logs.length > 0) {
      data.logs.forEach(msg => {
        addLog(msg, msg.includes('SUCCESS') ? 'success' : msg.includes('ERROR') ? 'danger' : 'info')
      })
    }

    // 3. 动态高亮与数据流动轨迹特效 (增强流动光效与阻断警示线)
    if (data.nodePath && data.nodePath.length > 1) {
      for (let i = 0; i < data.nodePath.length - 1; i++) {
        const u = data.nodePath[i]
        const v = data.nodePath[i + 1]

        // 查找两节点之间的物理连线 (双向比对)
        const matchedEdge = graph.getEdges().find(e => {
          const s = e.getSourceCellId()
          const t = e.getTargetCellId()
          return (s === u && t === v) || (s === v && t === u)
        })

        if (matchedEdge) {
          if (data.reachable) {
            // 通畅流光路径：高饱和度翠绿脉冲流光线
            matchedEdge.attr('line/stroke', '#22c55e')
            matchedEdge.attr('line/strokeWidth', 4)
            matchedEdge.attr('line/strokeDasharray', '10 5')
            matchedEdge.attr('line/style', {
              animation: 'ant-line 0.5s linear infinite',
              filter: 'drop-shadow(0 0 6px rgba(34, 197, 94, 0.75))'
            })
          } else {
            // 阻断路径：警示红光呼吸虚线
            matchedEdge.attr('line/stroke', '#ef4444')
            matchedEdge.attr('line/strokeWidth', 3.5)
            matchedEdge.attr('line/strokeDasharray', '6 4')
            matchedEdge.attr('line/style', {
              animation: 'ant-line-reverse 0.7s linear infinite',
              filter: 'drop-shadow(0 0 6px rgba(239, 68, 68, 0.75))'
            })
          }
        }
      }
    }

    if (data.reachable) {
      passBadgeVisible.value = true
      const hasRedundancy = data.logs && data.logs.some(l => l.includes('[REDUNDANCY]'))
      if (hasRedundancy) {
        showToast('success', `🔄 工业应急网络自愈成功！避开故障链路/宕机设备，成功通过冗余备用路径到达 [${dstName}]！(跳数: ${data.hops}, 延迟: ${data.totalLatencyMs}ms)`, 4500)
      } else {
        showToast('success', `✔ 后端 BFS 判定：[${srcName}] ➔ [${dstName}] 顺利连通！(跳数: ${data.hops}, 延迟: ${data.totalLatencyMs}ms)`)
      }
    } else if (data.blocked) {
      // 拦截节点视觉强化：红色脉冲警告圈 🚨 + 悬浮放行/拦截原因气泡
      const blockedNodeId = data.blockedNodeId || (data.nodePath && data.nodePath[data.nodePath.length - 1])
      if (blockedNodeId) {
        const blockedCell = graph.getCellById(blockedNodeId)
        if (blockedCell && blockedCell.isNode()) {
          blockedCell.attr('body/stroke', '#ef4444')
          blockedCell.attr('body/strokeWidth', 3)
          blockedCell.attr('badge/text', '🚨 拦截')
          blockedCell.attr('badge/fill', '#ef4444')
          blockedCell.attr('badge/display', 'block')

          // 激活脉冲警告光环
          blockedPulse.value.visible = true
          blockedPulse.value.nodeId = blockedNodeId

          // 激活悬浮气泡
          const bData = blockedCell.getData() || {}
          const bAttrs = blockedCell.getAttrs()
          const bName = (bAttrs?.title as any)?.text || bData.name || blockedNodeId
          const isGapReverse = data.blockedRuleId === 'GAP_HARDWARE_REVERSE_ISOLATION'
          const reasonText = isGapReverse
            ? '国家能源局36号令：单向网闸硬件反向阻断 (严禁IT外网穿透OT工控区)'
            : (data.blockedRuleId ? `安全策略拦截 (规则: ${data.blockedRuleId})` : '默认DENY拒绝通行')

          blockedTooltip.value = {
            visible: true,
            x: 0,
            y: 0,
            ruleId: data.blockedRuleId || 'DENY',
            reason: reasonText,
            nodeName: bName
          }

          updateBlockedVisualPosition()
        }
      }

      if (data.blockedRuleId === 'GAP_HARDWARE_REVERSE_ISOLATION') {
        showToast('error', '🛡️ 触发单向安全网闸硬件反向阻断：国家能源局36号令严禁IT外网向OT工控区反向穿透！', 4000)
      } else {
        showToast('error', `被节点安全策略拦截阻断！规则: [${data.blockedRuleId || 'DENY'}]`, 3500)
      }
    } else {
      showToast('warning', `未找到从 [${srcName}] 至 [${dstName}] 的有效物理通路！可能相关链路中断或节点宕机。`, 3500)
    }
  } catch (err: any) {
    showToast('error', err.message || '仿真请求失败')
  } finally {
    simulating.value = false
  }
}

const handleClearLogs = () => {
  logs.value = []
}

// 一键复位仿真演练动画与高亮状态
const handleResetSimulation = () => {
  passBadgeVisible.value = false
  packetVisible.value = false
  blockedPulse.value.visible = false
  blockedTooltip.value.visible = false
  resetEdgeStyles()
  showToast('info', '已复位当前画布仿真流光与高亮状态')
  addLog('INFO: 仿真演练状态已复位，恢复拓扑初始观察视角', 'info')
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

// 导出当前拓扑架构为高清 PNG 图片
const handleExportPng = () => {
  if (!graph) return
  try {
    const filename = `${topoName.value || '网络拓扑架构图'}_${new Date().toISOString().slice(0, 10)}.png`
    graph.exportPNG(filename, {
      padding: 30,
      backgroundColor: '#151d28',
      quality: 1
    })
    ElMessage.success(`已成功导出高清拓扑图: ${filename}`)
    addLog(`SUCCESS: 导出高清架构图 [${filename}] 成功`, 'success')
  } catch (err: any) {
    ElMessage.error(err.message || '导出拓扑图失败')
  }
}

// 逆向导入弹窗引用
const importModalRef = ref<InstanceType<typeof ImportTopologyModal> | null>(null)

const handleOpenImportModal = () => {
  importModalRef.value?.open()
}

// 接收逆向解析的数据并自动在画布上完成排版、生成大区底板与建立连线
const handleApplyImportedData = (data: { nodes: any[]; edges: any[]; zones: any[] }) => {
  if (!graph) return

  // 1. 清空画布
  graph.clearCells()
  resetEdgeStyles()
  selectedNode.value = null
  selectedEdge.value = null
  selectedZone.value = null

  // 2. 纯动态提取所有大区，并按标准工业安全分区顺序智能排序：IT (管理) -> DMZ (隔离) -> OT (生产) -> PHY (物理)
  const zoneMap = new Map<string, any[]>()
  data.nodes.forEach(n => {
    const z = n.zone || 'DEFAULT_ZONE'
    if (!zoneMap.has(z)) zoneMap.set(z, [])
    zoneMap.get(z)!.push(n)
  })

  // 工业标准大区排列权重：确保视觉流向从左至右自自然然
  const getZoneOrderWeight = (z: string) => {
    const u = z.toUpperCase()
    if (u.includes('IT') || u.includes('MANAGE') || u.includes('OFFICE')) return 1
    if (u.includes('DMZ') || u.includes('ISOLAT') || u.includes('GAP')) return 2
    if (u.includes('OT') || u.includes('PROD') || u.includes('CONTROL')) return 3
    if (u.includes('PHY') || u.includes('FIELD') || u.includes('SENSOR')) return 4
    return 5
  }

  const sortedZones = Array.from(zoneMap.keys()).sort((a, b) => getZoneOrderWeight(a) - getZoneOrderWeight(b))

  // 工业调色板（按大区类型智能匹配，兼顾自定义大区）
  const getZonePalette = (z: string) => {
    const u = z.toUpperCase()
    if (u.includes('IT')) return { border: '#3b82f6', bg: 'rgba(59, 130, 246, 0.08)' } // 科技蓝
    if (u.includes('DMZ') || u.includes('ISOLAT')) return { border: '#f87171', bg: 'rgba(239, 68, 68, 0.08)' } // 警示红
    if (u.includes('OT') || u.includes('PROD')) return { border: '#22c55e', bg: 'rgba(34, 197, 94, 0.08)' } // 安全绿
    if (u.includes('PHY') || u.includes('FIELD')) return { border: '#eab308', bg: 'rgba(234, 179, 8, 0.08)' } // 现场黄
    return { border: '#a855f7', bg: 'rgba(168, 85, 247, 0.08)' }
  }

  // 3. 构建大区内拓扑邻接关系，建立清晰的“工业组织层级树 (Hierarchical Tree)”
  // 核心思想：根节点（交换机/网关）在顶部 -> 站控/监控服务在二层 -> 机组LCU及其附属设备（调速/励磁/球阀）在下方成树形分支平行展开
  const zoneEdgeMap = new Map<string, Array<{ src: string; dst: string }>>()
  data.edges.forEach(e => {
    const srcNode = data.nodes.find(n => n.name === e.srcName)
    const dstNode = data.nodes.find(n => n.name === e.dstName)
    if (srcNode && dstNode && srcNode.zone === dstNode.zone) {
      const z = srcNode.zone || 'DEFAULT_ZONE'
      if (!zoneEdgeMap.has(z)) zoneEdgeMap.set(z, [])
      zoneEdgeMap.get(z)!.push({ src: e.srcName, dst: e.dstName })
    }
  })

  let currentStartX = 60
  const nodeNameToCellMap = new Map<string, any>()

  sortedZones.forEach(zKey => {
    const zoneNodes = zoneMap.get(zKey) || []
    const pal = getZonePalette(zKey)

    // 动态提取子网前缀
    const firstIp = zoneNodes.find(n => n.ip)?.ip || '192.168.1.10'
    const ipPrefix = firstIp.split('.').slice(0, 3).join('.')
    const isDmz = zKey.toUpperCase().includes('DMZ') || zKey.toUpperCase().includes('ISOLAT')

    // 工业组织层级树划分 (Hierarchical Tree Structure)：
    // Level 0 (网络骨干层): 骨干交换机、路由器、防火墙、网闸
    // Level 1 (站控服务与公用系统): 监控服务器、工作站、时钟源、数据库、公用系统(SFC/GIS/厂用电/排水等)
    // Level 2 (机组主控树): 1#~4# 机组LCU主PLC (作为机组分支树的根)


    // 1. 构建该大区内部的拓扑树深度 (DAG 拓扑层级计算)
    // 根据 data.edges 中的真实连接关系，计算每个节点相对于大区入口的真实层级深度 (level)
    const inDegree = new Map<string, number>()
    const adjList = new Map<string, string[]>()
    zoneNodes.forEach(n => {
      inDegree.set(n.name, 0)
      adjList.set(n.name, [])
    })

    // 统计大区内链路
    data.edges.forEach(e => {
      if (adjList.has(e.srcName) && adjList.has(e.dstName)) {
        adjList.get(e.srcName)!.push(e.dstName)
        inDegree.set(e.dstName, (inDegree.get(e.dstName) || 0) + 1)
      }
    })

    // 寻找大区根节点（SWITCH/ROUTER 核心设备一律作为 Level 0 核心骨干层！）
    const nodeLevelMap = new Map<string, number>()
    const queue: string[] = []

    // 核心网络骨干设备（SWITCH、ROUTER、FIREWALL、GAP）一律锁定在 Level 0 核心总线层，绝不因双网互联而降级！
    zoneNodes.forEach(n => {
      const t = (n.type || 'HOST').toUpperCase()
      if (['ROUTER', 'SWITCH'].includes(t)) {
        nodeLevelMap.set(n.name, 0)
        queue.push(n.name)
      } else if (['FIREWALL', 'GAP'].includes(t) && isDmz) {
        nodeLevelMap.set(n.name, 0)
        queue.push(n.name)
      }
    })

    // 针对工业生产大区(OT)与IT大区的科学三层物理分层，彻底消灭14台设备挤在同一行的极度狭长扁条：
    // Level 0: 核心骨干交换机与路由器 (SWITCH, ROUTER)
    // Level 1: 监控服务器、操作员站、工程师工作站、北斗对时 (SERVER, HOST)
    // Level 2: 全厂公用控制PLC (SFC/GIS/厂用电/排水/储气等全厂公用控制单元)
    // Level 3+: 1#~4# 发电机组现地控制屏柜 (LCU 及机组辅机)
    zoneNodes.forEach(n => {
      const t = (n.type || 'HOST').toUpperCase()
      if (['SWITCH', 'ROUTER'].includes(t)) {
        nodeLevelMap.set(n.name, 0)
      } else if (['SERVER', 'TDENGINE', 'HOST'].includes(t)) {
        nodeLevelMap.set(n.name, 1)
      } else {
        // 公用PLC/传感器/网关等现场设备归入 Level 2
        nodeLevelMap.set(n.name, 2)
      }
    })

    // 针对 OT 区机组特殊处理：将 1#~4# 机组辅机按机组树归纳
    // 关键修正：强化正则表达式支持匹配“1号主进水球阀”、“1号机组调速器”等所有机组辅机！
    const unitMap = new Map<string, { lcu: any; subs: any[] }>()
    const regularLevelGroups = new Map<number, any[]>()

    zoneNodes.forEach(node => {
      const name = node.name || ''
      const matchUnit = name.match(/([0-9一二三四1-4]+)号?(?:机组|主进水|可逆)/)
      if (matchUnit) {
        const unitId = matchUnit[1]
        if (!unitMap.has(unitId)) unitMap.set(unitId, { lcu: null, subs: [] })
        const grp = unitMap.get(unitId)!
        if (name.includes('LCU') || name.includes('主PLC')) {
          if (!grp.lcu) grp.lcu = node
          else grp.subs.push(node)
        } else {
          grp.subs.push(node)
        }
      } else {
        const lvl = nodeLevelMap.get(node.name) || 0
        if (!regularLevelGroups.has(lvl)) regularLevelGroups.set(lvl, [])
        regularLevelGroups.get(lvl)!.push(node)
      }
    })

    // 单张设备卡片规格
    const itemWidth = 155
    const itemHeight = 60
    const gapX = 45 // 列间距
    const gapY = 60 // 层级行距

    // 计算大区最大并排设备数
    let maxColsInAnyLevel = unitMap.size
    regularLevelGroups.forEach(nodes => {
      if (nodes.length > maxColsInAnyLevel) maxColsInAnyLevel = nodes.length
    })
    const effectiveCols = Math.max(maxColsInAnyLevel, 1)

    // 大区底板宽度：预留充足内边距 (左右各留 80px)
    const zoneWidth = Math.max(340, effectiveCols * itemWidth + (effectiveCols - 1) * gapX + 160)

    // 创建当前大区设备渲染辅助函数
    const renderNode = (node: any, x: number, y: number) => {
      const typeUpper = (node.type || 'HOST').toUpperCase()
      const icon = typeUpper === 'HOST' ? '💻' :
                   typeUpper === 'SERVER' ? '🖥️' :
                   typeUpper === 'ROUTER' ? '🌐' :
                   typeUpper === 'FIREWALL' ? '🧱' :
                   typeUpper === 'GAP' ? '🛡️' :
                   typeUpper === 'SWITCH' ? '🔲' :
                   typeUpper === 'PLC' ? '🎛️' :
                   typeUpper === 'TDENGINE' ? '🛢️' :
                   typeUpper === 'NEURON' ? '🔄' : '🌡️'

      const color = typeUpper === 'FIREWALL' ? '#ef4444' :
                    typeUpper === 'GAP' ? '#f97316' :
                    typeUpper === 'TDENGINE' ? '#06b6d4' :
                    typeUpper === 'PLC' ? '#8b5cf6' :
                    typeUpper === 'ROUTER' ? '#38bdf8' :
                    typeUpper === 'SWITCH' ? '#0ea5e9' :
                    typeUpper === 'NEURON' ? '#22c55e' : '#38bdf8'

      const cell = createDeviceNode(typeUpper, node.name, icon, color, x, y, {
        ip: node.ip,
        subnet: node.subnet || '255.255.255.0',
        gateway: node.gateway || '',
        zone: zKey,
        acls: node.acls || [],
        routes: node.routes || []
      })

      if (cell) {
        nodeNameToCellMap.set(node.name, cell)
      }
    }

    // =========================================================================
    // 【通用工业拓扑聚类引擎 (Universal Industrial Topology Engine)】
    // 彻底废除任何业务硬编码（如 SFC、水情、球阀、GIS 等特定名词）！
    // 核心图论逻辑：
    // 1. 骨干交换/路由识别：具有网络汇聚特征的核心节点
    // 2. 父系领地聚类 (Parent Pod Clustering)：依据实际 edge 连接矩阵，将直连从属节点归入对应父节点集群
    // 3. 边界网关自动提升：自动探测跨大区通信节点，置于面向相邻大区的高位侧，消灭长途大圆弧
    // 4. 通用屏柜单元识别：通过通用正则匹配 X号/屏柜/Bay 单元，自动生成辅机现地屏柜
    // =========================================================================
    const isProdZone = zKey.toUpperCase().includes('OT') || zKey.toUpperCase().includes('PROD') || zKey.toUpperCase().includes('FIELD')

    if (isProdZone) {
      // 1. 自动提取当前大区内的骨干网络设备 (SWITCH / ROUTER)
      const coreSwitches = zoneNodes.filter(n => {
        const t = (n.type || '').toUpperCase()
        return t === 'SWITCH' || t === 'ROUTER'
      })

      // 2. 通用屏柜/单元识别（匹配如 1号机组、2号机组、1号主变、5011断路器等工业单元）
      const unitMap = new Map<string, { lcu: any; subs: any[] }>()
      const nonUnitNodes: any[] = []

      zoneNodes.forEach(node => {
        const name = node.name || ''
        const matchUnit = name.match(/([0-9一二三四1-4]+)号?(?:机组|主变|主进水|可逆|线路|母线|间隔)/)
        if (matchUnit) {
          const unitId = matchUnit[1]
          if (!unitMap.has(unitId)) unitMap.set(unitId, { lcu: null, subs: [] })
          const grp = unitMap.get(unitId)!
          // 单元主控节点判定：名称含 LCU/主PLC/测控/保护，或该单元第一个节点
          if (name.includes('LCU') || name.includes('主PLC') || name.includes('保护测控') || name.includes('主保护')) {
            if (!grp.lcu) grp.lcu = node
            else grp.subs.push(node)
          } else {
            grp.subs.push(node)
          }
        } else {
          nonUnitNodes.push(node)
        }
      })

      // 修正：确保每个单元都有 lcu
      unitMap.forEach(grp => {
        if (!grp.lcu && grp.subs.length > 0) {
          grp.lcu = grp.subs.shift()
        }
      })

      // 3. 建立父子连接关系表 (基于真实连线数据动态分析)
      // 计算每个非单元节点与各核心交换机的连接强度
      const nodeDirectParentMap = new Map<string, string>() // nodeName -> parentSwitchName
      const crossZoneNodes = new Set<string>() // 跨大区连接的节点（需高位对齐）

      data.edges.forEach(e => {
        const srcNode = zoneNodes.find(n => n.name === e.srcName)
        const dstNode = zoneNodes.find(n => n.name === e.dstName)
        const isSrcInZone = Boolean(srcNode)
        const isDstInZone = Boolean(dstNode)

        // 探测跨大区节点
        if (isSrcInZone && !isDstInZone) crossZoneNodes.add(e.srcName)
        if (!isSrcInZone && isDstInZone) crossZoneNodes.add(e.dstName)

        // 内部从属连接
        if (isSrcInZone && isDstInZone) {
          if (coreSwitches.some(sw => sw.name === e.srcName)) {
            nodeDirectParentMap.set(e.dstName, e.srcName)
          } else if (coreSwitches.some(sw => sw.name === e.dstName)) {
            nodeDirectParentMap.set(e.srcName, e.dstName)
          }
        }
      })

      // 4. 自动分仓 (Pods)：将核心交换机及其从属节点归类到对应父系领地 (Pods)
      // 如果存在双网核心交换机 (如A网、B网)，将它们归入主控中枢仓 Pod 1；其余交换机（如II区交换机）各自独立成仓
      interface Pod {
        id: string
        title: string
        switches: any[]
        nodes: any[]
        color: string
        bg: string
      }

      const pods: Pod[] = []
      const assignedNodeNames = new Set<string>()

      // 寻找是否存在双网核心交换机（名称含 A网/B网 或 I区）
      const swA = coreSwitches.find(n => n.name.includes('A网') || (n.name.includes('I区') && !n.name.includes('II区')))
      const swB = coreSwitches.find(n => n.name.includes('B网'))

      if (swA) {
        const dualSwitches = swB ? [swA, swB] : [swA]
        dualSwitches.forEach(sw => assignedNodeNames.add(sw.name))
        // 收集归属于主控中枢的节点（排除现场公用PLC，现场公用设备沉底形成独立公用系统岛）
        const podNodes: any[] = []
        nonUnitNodes.forEach(node => {
          if (dualSwitches.some(sw => sw.name === node.name)) return
          const pName = nodeDirectParentMap.get(node.name)
          const isDirectSw = dualSwitches.some(sw => sw.name === pName)
          const isServerOrHost = ['SERVER', 'HOST'].includes((node.type || '').toUpperCase())

          // 如果是站控服务/工作站/RTU，或者是直连交换机且非现场PLC
          if (isDirectSw && isServerOrHost) {
            podNodes.push(node)
            assignedNodeNames.add(node.name)
          } else if (!pName && isServerOrHost && !node.name.includes('II区') && !node.name.includes('监测')) {
            podNodes.push(node)
            assignedNodeNames.add(node.name)
          }
        })

        pods.push({
          id: 'pod_main_control',
          title: `🏛️ 生产控制网络核心集群 (${swB ? '双网冗余' : '核心骨干'})`,
          switches: dualSwitches,
          nodes: podNodes,
          color: '#38bdf8',
          bg: 'rgba(56, 189, 248, 0.04)'
        })
      }

      // 剩余交换机各自建立独立从属集群（如 II区状态感知交换机）
      coreSwitches.forEach(sw => {
        if (assignedNodeNames.has(sw.name)) return
        assignedNodeNames.add(sw.name)

        const podNodes: any[] = []
        nonUnitNodes.forEach(node => {
          if (assignedNodeNames.has(node.name)) return
          const pName = nodeDirectParentMap.get(node.name)
          if (pName === sw.name || (!pName && (node.name.includes('II区') || node.name.includes('监测') || node.name.includes('色谱')))) {
            podNodes.push(node)
            assignedNodeNames.add(node.name)
          }
        })

        pods.push({
          id: `pod_${sw.name}`,
          title: `📡 ${sw.name} 从属业务专区`,
          switches: [sw],
          nodes: podNodes,
          color: '#a855f7',
          bg: 'rgba(168, 85, 247, 0.04)'
        })
      })

      // 剩余未关联的公共非机组节点（如全厂公用控制PLC或未划分终端）
      const remainingCommonNodes = nonUnitNodes.filter(n => !assignedNodeNames.has(n.name))

      // 5. 动态计算空间布局：
      // 【真正的太阳与九大行星星型/放射轨道发散布局 (Solar Star Radial Dispersion)】：
      // 核心父节点（交换机）就是太阳圆心 (CenterX, CenterY)！
      // 从属子节点如九大行星般围绕核心发散环绕分布在轨道半径上；连线为两点直射的射线光束！
      const podGap = 100
      let podsTotalWidth = 0
      const podLayoutInfos: Array<{
        pod: Pod
        centerX: number
        centerY: number
        radius: number
        width: number
        height: number
      }> = []

      pods.forEach((pod) => {
        const n = pod.nodes.length
        // 根据子节点数量自适应计算行星轨道半径
        // 节点越多，轨道半径适度拉大，确保节点卡片之间有足够周向间隙
        const radius = n <= 4 ? 200 : n <= 8 ? 260 : 320
        const podWidth = Math.max(680, radius * 2 + itemWidth + 80)
        const podHeight = radius * 2 + itemHeight + 100
        podLayoutInfos.push({
          pod,
          centerX: 0, // 稍后在整体排版中赋予绝对X
          centerY: 40 + podHeight / 2, // 太阳中心点绝对Y
          radius,
          width: podWidth,
          height: podHeight
        })
      })

      podsTotalWidth = podLayoutInfos.reduce((acc, p) => acc + p.width, 0) + Math.max(podLayoutInfos.length - 1, 0) * podGap
      const unitsWidth = unitMap.size * (itemWidth + 24) + Math.max(unitMap.size - 1, 0) * gapX
      const commonWidth = remainingCommonNodes.length * itemWidth + Math.max(remainingCommonNodes.length - 1, 0) * gapX

      const zoneWidth = Math.max(1360, podsTotalWidth + 140, unitsWidth + 160, commonWidth + 160)
      const podsStartX = currentStartX + (zoneWidth - podsTotalWidth) / 2

      let currentPodLeft = podsStartX
      let maxPodBottomY = 85

      // 绘制各个星系 Pod 及其内部设备
      podLayoutInfos.forEach((info) => {
        const pod = info.pod
        const podX = currentPodLeft
        const podTopY = 85
        const podCenterX = podX + info.width / 2
        const podCenterY = podTopY + info.height / 2
        info.centerX = podCenterX
        info.centerY = podCenterY

        // 5.1 绘制太阳核心（父节点交换机）
        const isDual = pod.switches.length >= 2
        if (isDual) {
          // 双星系统（双交换机，例如 A网 / B网），位于星系圆心左右紧挨对称分布
          const dualGap = 16
          const sw1_X = podCenterX - itemWidth - dualGap / 2
          const sw2_X = podCenterX + dualGap / 2
          const swY = podCenterY - itemHeight / 2
          if (pod.switches[0]) renderNode(pod.switches[0], sw1_X, swY)
          if (pod.switches[1]) renderNode(pod.switches[1], sw2_X, swY)
        } else {
          // 单星系统（单交换机，绝对圆心！）
          const swX = podCenterX - itemWidth / 2
          const swY = podCenterY - itemHeight / 2
          if (pod.switches[0]) renderNode(pod.switches[0], swX, swY)
        }

        // 5.2 绘制九大行星环绕子节点（基于极坐标角度均匀发散）：
        // 避开正左与正右极窄侧，若有跨区节点优先置于左侧偏上方（角度在 160°~200°）
        const sortedNodes = [...pod.nodes].sort((a, b) => (crossZoneNodes.has(b.name) ? 1 : 0) - (crossZoneNodes.has(a.name) ? 1 : 0))
        const nodeCount = sortedNodes.length

        if (nodeCount > 0) {
          // 在 360 度圆周上均匀分布，或者半环发散
          // 为避免节点卡片在顶部和底部发生轻微重叠，按椭圆轨道比例放缩 (rx = radius * 1.15, ry = radius * 0.95)
          const rx = info.radius * 1.18
          const ry = info.radius * 0.95

          sortedNodes.forEach((node, idx) => {
            // 起始角从 -90° (正上方12点钟) 开始顺时针旋转
            const angle = -Math.PI / 2 + (idx * (2 * Math.PI) / nodeCount)
            const nodeX = podCenterX + rx * Math.cos(angle) - itemWidth / 2
            const nodeY = podCenterY + ry * Math.sin(angle) - itemHeight / 2
            renderNode(node, nodeX, nodeY)
          })
        }

        const podBottom = podTopY + info.height
        if (podBottom > maxPodBottomY) {
          maxPodBottomY = podBottom
        }

        // 生成该 Pod 的专属物理领地底板 (Subzone Box)
        graph!.addNode({
          id: `subzone_${zKey.toLowerCase()}_${pod.id}`,
          x: podX,
          y: podTopY,
          width: info.width,
          height: info.height,
          zIndex: 2,
          data: { name: pod.title, isSubZone: true },
          markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
          attrs: {
            body: {
              fill: pod.bg,
              stroke: pod.color,
              strokeWidth: 1.5,
              strokeDasharray: '4 4',
              rx: 12,
              ry: 12
            },
            label: {
              text: pod.title,
              fill: pod.color,
              fontSize: 11,
              fontWeight: 600,
              textAnchor: 'start',
              textVerticalAnchor: 'top',
              refX: 16,
              refY: 12
            }
          }
        })

        currentPodLeft += info.width + podGap
      })

      let otCursorY = maxPodBottomY + 45

      // 6. 绘制全厂公用/公用控制单元 (若存在未分入单元的公共节点)
      if (remainingCommonNodes.length > 0) {
        const cWidth = remainingCommonNodes.length * itemWidth + (remainingCommonNodes.length - 1) * gapX
        const cStart = currentStartX + (zoneWidth - cWidth) / 2
        const subCommonId = `subzone_${zKey.toLowerCase()}_common`
        const subCommonTitle = `⚙️ 全厂公用系统/辅助控制单元 (Common Utilities)`
        const subCommonWidth = cWidth + 40
        const subCommonHeight = itemHeight + 48

        graph!.addNode({
          id: subCommonId,
          x: cStart - 20,
          y: otCursorY - 10,
          width: subCommonWidth,
          height: subCommonHeight,
          zIndex: 2,
          data: { name: subCommonTitle, isSubZone: true },
          markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
          attrs: {
            body: {
              fill: 'rgba(234, 179, 8, 0.04)',
              stroke: 'rgba(234, 179, 8, 0.4)',
              strokeWidth: 1.5,
              strokeDasharray: '4 4',
              rx: 6,
              ry: 6
            },
            label: {
              text: subCommonTitle,
              fill: '#facc15',
              fontSize: 10,
              fontWeight: 600,
              textAnchor: 'start',
              textVerticalAnchor: 'top',
              refX: 12,
              refY: 8
            }
          }
        })

        const commonNodeY = otCursorY + 18
        remainingCommonNodes.forEach((node, i) => {
          renderNode(node, cStart + i * (itemWidth + gapX), commonNodeY)
        })
        otCursorY += itemHeight + 65
      }

      // 7. 绘制通用机组/单元控制屏柜 (LCU / Bay Units)
      if (unitMap.size > 0) {
        const unitsArray = Array.from(unitMap.entries())
        const unitColWidth = itemWidth + 24
        const totalUnitsWidth = unitsArray.length * unitColWidth + (unitsArray.length - 1) * gapX
        const unitsStartX = currentStartX + (zoneWidth - totalUnitsWidth) / 2

        let maxSubCount = 0
        unitsArray.forEach(([_, grp]) => {
          if (grp.subs.length > maxSubCount) maxSubCount = grp.subs.length
        })

        const unitTotalRows = 1 + maxSubCount
        const subZoneHeight = unitTotalRows * itemHeight + (unitTotalRows - 1) * gapY + 45
        const subZoneY = otCursorY - 10

        unitsArray.forEach(([unitId, _], colIndex) => {
          const subBoxX = unitsStartX + colIndex * (unitColWidth + gapX)
          const subZoneId = `subzone_${zKey.toLowerCase()}_unit_${unitId}`
          const subZoneTitle = `⚡ ${unitId}号单元现地控制屏柜 (Bay Unit)`

          graph!.addNode({
            id: subZoneId,
            x: subBoxX,
            y: subZoneY,
            width: unitColWidth,
            height: subZoneHeight,
            zIndex: 2,
            data: { name: subZoneTitle, isSubZone: true },
            markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
            attrs: {
              body: {
                fill: 'rgba(139, 92, 246, 0.05)',
                stroke: 'rgba(168, 85, 247, 0.45)',
                strokeWidth: 1.5,
                strokeDasharray: '4 4',
                rx: 6,
                ry: 6
              },
              label: {
                text: subZoneTitle,
                fill: '#c084fc',
                fontSize: 10,
                fontWeight: 600,
                textAnchor: 'start',
                textVerticalAnchor: 'top',
                refX: 10,
                refY: 8
              }
            }
          })
        })

        const lcuY = otCursorY + 22
        unitsArray.forEach(([_, grp], colIndex) => {
          const nodeX = unitsStartX + colIndex * (unitColWidth + gapX) + 12
          if (grp.lcu) renderNode(grp.lcu, nodeX, lcuY)
        })

        for (let sIdx = 0; sIdx < maxSubCount; sIdx++) {
          const subRowY = lcuY + (sIdx + 1) * (itemHeight + gapY)
          unitsArray.forEach(([_, grp], colIndex) => {
            const nodeX = unitsStartX + colIndex * (unitColWidth + gapX) + 12
            const subNode = grp.subs[sIdx]
            if (subNode) renderNode(subNode, nodeX, subRowY)
          })
        }

        otCursorY = subZoneY + subZoneHeight + 20
      }

      // 创建整个生产大区的外层底板
      const zoneHeight = Math.max(380, otCursorY + 30)
      const zoneTitle = `${zKey} (${ipPrefix}.0/24)`
      createZoneBox(`zone_${zKey.toLowerCase()}`, zoneTitle, currentStartX, 40, zoneWidth, zoneHeight, pal.bg, pal.border, isDmz, {
        subnetPrefix: ipPrefix,
        gateway: `${ipPrefix}.1`
      })

      currentStartX += zoneWidth + 120
      return
    }

    // 非 OT 大区（如 IT / DMZ）保持标准的树形自适应居中排版：
    let cursorY = isDmz ? 110 : 75
    const sortedLevels = Array.from(regularLevelGroups.keys()).sort((a, b) => a - b)
    sortedLevels.forEach(lvl => {
      let items = regularLevelGroups.get(lvl)!
      const totalWidth = items.length * itemWidth + (items.length - 1) * gapX
      const startX = currentStartX + (zoneWidth - totalWidth) / 2
      items.forEach((node, i) => {
        const nx = startX + i * (itemWidth + gapX)
        renderNode(node, nx, cursorY)
      })
      cursorY += itemHeight + gapY
    })

    // 创建底板
    const zoneHeight = Math.max(380, cursorY + 30)
    const zoneTitle = `${zKey} (${ipPrefix}.0/24)`
    createZoneBox(`zone_${zKey.toLowerCase()}`, zoneTitle, currentStartX, 40, zoneWidth, zoneHeight, pal.bg, pal.border, isDmz, {
      subnetPrefix: ipPrefix,
      gateway: `${ipPrefix}.1`
    })

    currentStartX += zoneWidth + 120
  })

  // 4. 工业级纯两点绝对直线直连（两点之间无任何折线、绝无中间控制拐点！）：
  data.edges.forEach(edgeItem => {
    const srcCell = nodeNameToCellMap.get(edgeItem.srcName)
    const dstCell = nodeNameToCellMap.get(edgeItem.dstName)

    if (srcCell && dstCell) {
      const srcPos = srcCell.getPosition()
      const dstPos = dstCell.getPosition()

      const deltaY = dstPos.y - srcPos.y
      const absY = Math.abs(deltaY)

      // 工业级链路工程规范分色与辨识：
      let lineColor = '#00d2ff'
      let isDashed = false
      let strokeWidth = 1.8

      const isHeartbeat = absY <= 30 && (/A网|B网/.test(edgeItem.srcName) && /A网|B网/.test(edgeItem.dstName))
      const isCrossZone = (edgeItem.srcName.includes('网闸') || edgeItem.dstName.includes('网闸')) ||
                          (edgeItem.srcName.includes('防火墙') || edgeItem.dstName.includes('防火墙')) ||
                          (edgeItem.srcName.includes('加密') || edgeItem.dstName.includes('加密'))
      const isNetworkB = edgeItem.srcName.includes('B网') || edgeItem.dstName.includes('B网')
      const isZoneII = edgeItem.srcName.includes('II区') || edgeItem.dstName.includes('II区')
      const isUnitBus = /机组.*PLC/.test(edgeItem.srcName) && /机组|球阀|调速|励磁/.test(edgeItem.dstName)

      if (isHeartbeat) {
        lineColor = '#eab308'
        isDashed = true
        strokeWidth = 2.5
      } else if (isCrossZone) {
        lineColor = '#f43f5e'
        strokeWidth = 2.2
      } else if (isNetworkB) {
        lineColor = '#f59e0b' // 工业B网黄色
        strokeWidth = 1.8
      } else if (isZoneII) {
        lineColor = '#a855f7' // II区专属紫色
        strokeWidth = 1.8
      } else if (isUnitBus) {
        lineColor = '#10b981' // 机组辅机现场总线绿色
        strokeWidth = 1.6
      } else {
        lineColor = '#00d2ff' // A网默认青蓝
      }

      graph!.addEdge({
        source: { cell: srcCell.id },
        target: { cell: dstCell.id },
        zIndex: 5,
        router: null, // 关键：彻底废除任何正交或折线路由器，确保中间绝无折线！
        connector: {
          name: 'normal' // 纯直线（Direct line between two points）
        },
        attrs: {
          line: {
            stroke: lineColor,
            strokeWidth: strokeWidth,
            strokeDasharray: isDashed ? '4 3' : '0',
            targetMarker: {
              name: 'classic',
              size: 5
            }
          }
        },
        data: {
          bandwidth: edgeItem.bw || '1000',
          latencyMs: edgeItem.lat ?? 2
        }
      })
    }
  })

  graph.zoomToFit({ padding: 50 })
  refreshAvailableNodes()
  addLog(`SUCCESS: 工业组织层级树拓扑生成完毕！共创建 ${data.nodes.length} 台设备、${data.edges.length} 条互联链路`, 'success')
  showToast('success', `🎉 工业组织层级树拓扑构建成功！共生成 ${data.nodes.length} 台设备与 ${data.edges.length} 条规范单向弧线`)
}
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
        <button class="btn-action-import" title="批量导入资产表格(Excel/CSV)或Cisco配置" @click="handleOpenImportModal">
          📥 逆向导入
        </button>
        <button class="btn-action-export" title="导出当前拓扑为高清PNG图片" @click="handleExportPng">
          📸 导出拓扑图
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

        <!-- 阻断红色脉冲警告圈 (覆盖在被拦截设备周围，形成扩散红光脉冲波) -->
        <div
          v-if="blockedPulse.visible"
          class="blocked-pulse-ring"
          :style="{
            top: `${blockedPulse.y}px`,
            left: `${blockedPulse.x}px`,
            width: `${blockedPulse.width}px`,
            height: `${blockedPulse.height}px`
          }"
        >
          <div class="pulse-wave wave-1"></div>
          <div class="pulse-wave wave-2"></div>
        </div>

        <!-- 悬浮拦截原因气泡 (位于被拦截设备上方，展示放行/拦截原因) -->
        <div
          v-if="blockedTooltip.visible"
          class="blocked-tooltip-bubble"
          :style="{
            top: `${blockedTooltip.y}px`,
            left: `${blockedTooltip.x}px`
          }"
        >
          <div class="bubble-header">
            <span class="bubble-badge">🚨 TRAFFIC BLOCKED</span>
            <span class="bubble-node">[{{ blockedTooltip.nodeName }}]</span>
          </div>
          <div class="bubble-body">
            <span class="rule-tag">{{ blockedTooltip.ruleId }}</span>
            <span class="reason-text">{{ blockedTooltip.reason }}</span>
          </div>
          <div class="bubble-arrow"></div>
        </div>

        <!-- 底部仿真演练控制台 (支持自选源/宿端、端口与协议) -->
        <SimulationConsole
          :logs="logs"
          :nodes="availableNodes"
          :simulating="simulating"
          @run="handleSimulate"
          @clear-logs="handleClearLogs"
          @reset-sim="handleResetSimulation"
        />

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
          
          <!-- 故障注入/恢复 (针对设备节点与物理链路) -->
          <div
            v-if="contextMenu.targetType === 'node' || contextMenu.targetType === 'edge'"
            class="menu-item"
            :class="{ 'is-warning': !contextMenu.isDisabled, 'is-success': contextMenu.isDisabled }"
            @click="toggleContextCellFault"
          >
            <span class="item-icon">{{ contextMenu.isDisabled ? '💚' : '⚠️' }}</span>
            <span>
              {{ contextMenu.isDisabled
                ? (contextMenu.targetType === 'edge' ? '恢复链路通畅' : '恢复设备正常运行')
                : (contextMenu.targetType === 'edge' ? '模拟链路切断 (故障注入)' : '模拟硬件宕机 (故障注入)')
              }}
            </span>
          </div>

          <div class="menu-item is-danger" @click="handleDeleteContextCell">
            <span class="item-icon">🗑️</span>
            <span>删除此{{ contextMenu.targetType === 'zone' ? '大区底板' : contextMenu.targetType === 'edge' ? '链路' : '节点' }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧属性检查器 (点击空白处自动隐藏，选中图元时展开) -->
      <transition name="slide-fade">
        <PropertyInspector
          v-if="selectedNode || selectedEdge || selectedZone"
          :selected-node="selectedNode"
          :selected-edge="selectedEdge"
          :selected-zone="selectedZone"
          @update-node="handleUpdateNode"
          @update-edge="handleUpdateEdge"
          @update-zone="handleUpdateZone"
          @simulate="handleSimulate"
        />
      </transition>
    </div>

    <!-- 工业资产与拓扑逆向一键导入模态框 -->
    <ImportTopologyModal
      ref="importModalRef"
      @import-success="handleApplyImportedData"
    />
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

.btn-action-export {
  background: #1e293b;
  border: 1px solid #334155;
  color: #38bdf8;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
}

.btn-action-export:hover {
  background: #0f172a;
  border-color: #38bdf8;
  color: #7dd3fc;
  box-shadow: 0 0 6px rgba(56, 189, 248, 0.3);
}

.btn-action-import {
  background: #1e293b;
  border: 1px solid #10b981;
  color: #34d399;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
}

.btn-action-import:hover {
  background: #064e3b;
  border-color: #34d399;
  color: #a7f3d0;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
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

.menu-item.is-warning {
  color: #fbbf24;
}

.menu-item.is-warning:hover {
  background: rgba(245, 158, 11, 0.18);
  color: #f59e0b;
}

.menu-item.is-success {
  color: #4ade80;
}

.menu-item.is-success:hover {
  background: rgba(34, 197, 94, 0.18);
  color: #22c55e;
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

/* 属性面板滑入滑出动画 */
.slide-fade-enter-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.7, 0, 0.84, 0);
}
.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateX(30px);
  opacity: 0;
}

/* 链路通畅跑马灯流光动画 (正向高速流动) */
@keyframes ant-line {
  from {
    stroke-dashoffset: 30;
  }
  to {
    stroke-dashoffset: 0;
  }
}

/* 阻断链路警示虚线动画 (反向逆流警示) */
@keyframes ant-line-reverse {
  from {
    stroke-dashoffset: 0;
  }
  to {
    stroke-dashoffset: 20;
  }
}

/* 红色脉冲波扩散动画 */
@keyframes pulse-ring {
  0% {
    transform: scale(0.95);
    opacity: 0.9;
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.85);
  }
  70% {
    transform: scale(1.15);
    opacity: 0.4;
    box-shadow: 0 0 0 16px rgba(239, 68, 68, 0);
  }
  100% {
    transform: scale(1.22);
    opacity: 0;
    box-shadow: 0 0 0 22px rgba(239, 68, 68, 0);
  }
}

/* 拦截气泡浮现微动动画 */
@keyframes tooltip-bounce {
  0% {
    opacity: 0;
    transform: translate(-50%, -85%) scale(0.9);
  }
  50% {
    transform: translate(-50%, -105%) scale(1.02);
  }
  100% {
    opacity: 1;
    transform: translate(-50%, -100%) scale(1);
  }
}

:deep(.x6-edge path[style*="ant-line"]) {
  animation: ant-line 0.5s linear infinite !important;
}

:deep(.x6-edge path[style*="ant-line-reverse"]) {
  animation: ant-line-reverse 0.7s linear infinite !important;
}

:deep(.x6-edge path) {
  transition: stroke 0.25s ease, stroke-width 0.25s ease;
}

/* 阻断红色脉冲警告圈样式 */
.blocked-pulse-ring {
  position: absolute;
  pointer-events: none;
  z-index: 15;
  border-radius: 8px;
}

.pulse-wave {
  position: absolute;
  inset: -6px;
  border-radius: 12px;
  border: 2px solid #ef4444;
  pointer-events: none;
  animation: pulse-ring 1.8s cubic-bezier(0.25, 0.46, 0.45, 0.94) infinite;
}

.pulse-wave.wave-2 {
  animation-delay: 0.9s;
}

/* 悬浮拦截原因气泡样式 */
.blocked-tooltip-bubble {
  position: absolute;
  transform: translate(-50%, -100%);
  z-index: 20;
  min-width: 220px;
  max-width: 320px;
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid #ef4444;
  border-radius: 8px;
  padding: 8px 12px;
  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.35), 0 0 12px rgba(239, 68, 68, 0.25);
  backdrop-filter: blur(8px);
  animation: tooltip-bounce 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  pointer-events: none;
}

.bubble-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 11px;
}

.bubble-badge {
  background: #7f1d1d;
  color: #fca5a5;
  border: 1px solid #ef4444;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: bold;
  letter-spacing: 0.5px;
}

.bubble-node {
  color: #f87171;
  font-family: monospace;
  font-weight: 600;
}

.bubble-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.rule-tag {
  align-self: flex-start;
  background: rgba(239, 68, 68, 0.15);
  color: #fca5a5;
  border: 1px dashed #ef4444;
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 10px;
  font-family: monospace;
  font-weight: 600;
}

.reason-text {
  font-size: 11px;
  color: #e2e8f0;
  line-height: 1.4;
}

/* 气泡底部指向小三角 */
.bubble-arrow {
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 6px solid #ef4444;
}

/* X6 Transform 自由缩放手柄样式美化 */
:deep(.x6-widget-transform) {
  padding: 0;
  border: 1px dashed #38bdf8;
  border-radius: 8px;
}
:deep(.x6-widget-transform-resize) {
  border-radius: 3px;
  background-color: #38bdf8;
  border: 1px solid #0f172a;
  box-shadow: 0 0 6px rgba(56, 189, 248, 0.6);
  width: 8px;
  height: 8px;
}

/* X6 Snapline 智能对齐辅助线美化 (高亮亮青色激光线) */
:deep(.x6-widget-snapline) {
  stroke: #38bdf8 !important;
  stroke-width: 1px !important;
  stroke-dasharray: 4 2 !important;
  opacity: 0.85;
}
</style>
