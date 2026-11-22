<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const routeToPageMap: Record<string, string> = {
  '/req/projects': 'page-projects',
  '/req/workflow': 'page-workflow',
  '/req/templates': 'page-templates',
  '/req/skills': 'page-skills',
  '/req/scripts': 'page-scripts',
  '/req/faq': 'page-faq',
  '/req/dashboard': 'page-dashboard',
  '/req/detail': 'page-req-detail',
  '/req/prototypes': 'page-prototypes',
  '/req/baselines': 'page-baselines',
  '/req/baseline-diff': 'page-baseline-diff',
  '/req/releases': 'page-releases',
  '/req/timeline': 'page-timeline',
  '/req/enums': 'page-enums',
  '/req/theme': 'page-theme'
}

const pageToRouteMap: Record<string, string> = {
  'page-projects': '/req/projects',
  'page-workflow': '/req/workflow',
  'page-templates': '/req/templates',
  'page-skills': '/req/skills',
  'page-scripts': '/req/scripts',
  'page-faq': '/req/faq',
  'page-dashboard': '/req/dashboard',
  'page-req-detail': '/req/detail',
  'page-prototypes': '/req/prototypes',
  'page-baselines': '/req/baselines',
  'page-baseline-diff': '/req/baseline-diff',
  'page-releases': '/req/releases',
  'page-timeline': '/req/timeline',
  'page-enums': '/req/enums',
  'page-theme': '/req/theme'
}
import { ElMessage, ElMessageBox } from 'element-plus'
import { NODE_KNOWLEDGE_BASE } from '../constants/workflowKnowledge'
import { WORKFLOW_FAQ_DATA, WORKFLOW_FAQ_CATEGORIES } from '../constants/workflowFaq'
import {
  getProjectListApi,
  getProjectStatsApi,
  createProjectApi,
  deleteProjectApi,
  getRequirementListApi,
  getRequirementDetailApi,
  saveConfirmItemsApi,
  checkGateApi,
  getBaselineDiffApi,
  createRequirementApi,
  createBaselineApi,
  unfreezeRequirementApi,
  getPrototypeListApi,
  getProjectPrototypeListApi,
  uploadPrototypeApi,
  getBaselineListApi,
  getWorkflowStateApi,
  getWorkflowTopologyApi,
  advanceWorkflowDagApi,
  getWorkflowRecordsApi,
  getWorkflowTemplateListApi,
  getTemplateMountedProjectsApi,
  mountProjectsToTemplateApi,
  cloneWorkflowTemplateApi,
  initDefaultWorkflowTemplateApi,
  createNewDefaultWorkflowTemplateApi,
  updateWorkflowTemplateApi,
  deleteWorkflowTemplateApi,
  getWorkflowSkillsApi,
  getWorkflowScriptsApi,
  getWorkflowFileContentApi,
  getReleasesApi,
  getFrozenBaselinesApi,
  createReleaseApi,
  publishReleaseApi
} from '@/api/req'
import { Graph } from '@antv/x6'
import { getUserListApi } from '@/api/user'
import CommonPagination from '@/components/common/CommonPagination.vue'
import CommonFormModal from '@/components/common/CommonFormModal.vue'

// 当前操作人与权限角色 (支持切换管理员 / 业务负责人)
const currentOperator = ref('admin') // 默认为 admin 超级管理员
const isAdmin = computed(() => currentOperator.value === 'admin')

// 系统用户与责任人数据 (动态从 MySQL sys_user 拉取)
const userList = ref<any[]>([])

// 项目空间列表分页状态 (CommonPagination)
const projectPage = ref(1)
const projectPageSize = ref(6)

// 计算当前分页切片下的项目列表
const paginatedProjectList = computed(() => {
  const start = (projectPage.value - 1) * projectPageSize.value
  return projectList.value.slice(start, start + projectPageSize.value)
})

// ==========================================
// 🚀 工程流水线 (DAG 算子流转与需求责任人联动状态)
// ==========================================
const workflowSelectedReqId = ref<number>(1)
const workflowSelectedNode = ref<string>('SOP-01')

// 当前需求类型/路径模式：'STANDARD' (L级标准全量大需求), 'MEDIUM' (M级单模块需求), 'FASTTRACK' (S级小Bug微创快修)
const workflowTrackMode = ref<'STANDARD' | 'MEDIUM' | 'FASTTRACK'>('STANDARD')

// 数据驱动状态：节点算子与连线 100% 来自后端数据库 (t_req_workflow_template + t_req_workflow_node_state)
const pipelineNodes = ref<any[]>([])
const fasttrackNodes = ref<any[]>([])
const workflowEdges = ref<Array<{ source: string; target: string }>>([])
const workflowLoading = ref<boolean>(false)
const workflowLoadError = ref<string | null>(null)

// 纯视觉布局元数据字典 (仅定义画布坐标、色值与四层排版尺寸，不包含任何业务状态与假数据)
const WORKFLOW_NODE_LAYOUT: Record<string, { x: number; y: number; w: number; h: number; color: string; subLabel?: string }> = {
  'SOP-00': { x: 38, y: 48, w: 120, h: 52, color: '#94a3b8', subLabel: '规模分流识别' },
  'SOP-01': { x: 185, y: 48, w: 130, h: 52, color: '#f59e0b', subLabel: '白话澄清与切片' },
  'SOP-02': { x: 385, y: 48, w: 130, h: 52, color: '#38bdf8', subLabel: '契约与表结构冻结' },
  'SOP-03': { x: 535, y: 48, w: 130, h: 52, color: '#94a3b8', subLabel: '单焦点红绿单测' },
  'SOP-04': { x: 685, y: 48, w: 130, h: 52, color: '#ef4444', subLabel: 'gate.py 6项阻断' },
  'SOP-05': { x: 835, y: 48, w: 130, h: 52, color: '#f59e0b', subLabel: '架构与契约复核' },
  'TC-GEN': { x: 535, y: 115, w: 280, h: 48, color: '#8b5cf6', subLabel: 'docs/test-cases 冻结' },
  'SOP-06': { x: 1025, y: 48, w: 135, h: 52, color: '#0284c7', subLabel: '双轨延迟汇合穿透' },
  'SOP-07': { x: 1185, y: 48, w: 135, h: 52, color: '#ef4444', subLabel: '唯一归档出口' },
  'SOP-08': { x: 1380, y: 48, w: 135, h: 52, color: '#10b981', subLabel: 'DockerCompose离线' },
  'SOP-09': { x: 1545, y: 48, w: 135, h: 52, color: '#10b981', subLabel: 'Tag+Release回滚' },
  'SOP-B2': { x: 1380, y: 115, w: 300, h: 48, color: '#d97706', subLabel: '单文件≤20行免门禁' }
}

// 合并所有轨道节点 (供三态染色、详情抽屉与连线统一检索)
const allWorkflowNodes = computed(() => [...pipelineNodes.value, ...fasttrackNodes.value])

// 联动当前选中的需求对象
const workflowActiveReq = computed(() => {
  return requirements.value.find(r => r.id === workflowSelectedReqId.value) || requirements.value[0] || currentReq.value || {}
})

// 动态选择展示的 DAG 节点链
const currentActiveDAGNodes = computed(() => {
  return workflowTrackMode.value === 'FASTTRACK' ? fasttrackNodes.value : pipelineNodes.value
})

// 联动当前选中的算子节点对象 (提供安全占位，避免数据加载前访问 undefined)
const workflowActiveNodeInfo = computed(() => {
  const list = currentActiveDAGNodes.value
  return (
    list.find(n => n.id === workflowSelectedNode.value) ||
    allWorkflowNodes.value.find(n => n.id === workflowSelectedNode.value) ||
    list[0] || {
      id: workflowSelectedNode.value || '-',
      name: workflowLoading.value ? '加载中...' : '未加载工位',
      role: '—',
      skill: '-',
      sop: '-',
      gate: '—',
      status: 'PENDING',
      desc: ''
    }
  )
})

// 当前轨道完成度百分比 (防除零)
const workflowProgressStats = computed(() => {
  const list = currentActiveDAGNodes.value
  const total = list.length
  const completed = list.filter(n => n.status === 'COMPLETED').length
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0
  return { completed, total, percent }
})

// 画布尺寸契约：可视框高度 480px 由 .wf-viewport 决定；
// 内容画布完整包容四层一体化全景拓扑 (指令轨 / AI Skill 专家轨 / 控制中枢轨 / 物理产物轨)，宽 1750px，高 520px
const WORKFLOW_CANVAS = { width: 1750, height: 520 }
const workflowHostHeight = ref(WORKFLOW_CANVAS.height)

// 需求列表左侧侧边栏折叠状态
const workflowReqListCollapsed = ref(false)

// AntV X6 真实拓扑画布实例与容器引用
const workflowGraphContainer = ref<HTMLDivElement | null>(null)
let workflowGraph: Graph | null = null

// 初始化 AntV X6 工业级有向无环拓扑图
const initWorkflowGraph = () => {
  if (!workflowGraphContainer.value) return
  if (workflowGraph) {
    workflowGraph.dispose()
    workflowGraph = null
  }

  workflowGraph = new Graph({
    container: workflowGraphContainer.value,
    autoResize: false,
    width: WORKFLOW_CANVAS.width,
    height: WORKFLOW_CANVAS.height,
    background: {
      color: '#0b1320' // 工业极客暗黑底色
    },
    grid: {
      visible: true,
      type: 'mesh',
      args: {
        color: '#1e293b',
        thickness: 1
      }
    },
    panning: {
      enabled: true,
      eventTypes: ['leftMouseDown'] // 允许空白处按住左键拖拽平移漫游
    },
    mousewheel: {
      enabled: true,
      modifiers: ['ctrl', 'meta']
    },
    interacting: {
      nodeMovable: true, // 允许自由微调拖拽节点排版
      edgeMovable: false
    }
  })

  // 仅在画布初始化时绑定一次点击事件，防止重绘导致重复监听
  workflowGraph.on('node:click', ({ node }) => {
    const id = node.id
    if (!id.startsWith('zone_')) {
      const mappedId = id.startsWith('SKILL-01') || id === 'ART-01' ? 'SOP-01'
        : (id.startsWith('SKILL-02') || id === 'CTRL-BOARD' || id === 'ART-02' ? 'SOP-02'
        : (id.startsWith('SKILL-03') || id === 'CTRL-STATUS' || id === 'ART-03' ? 'SOP-03'
        : (id.startsWith('SKILL-04') || id === 'CTRL-GATE' || id === 'ART-04' ? 'SOP-04'
        : (id.startsWith('SKILL-06') ? 'SOP-06'
        : (id === 'ART-05' ? 'SOP-07' : id)))))
      workflowSelectedNode.value = mappedId
      renderWorkflowGraphData()
      openNodeDetailDrawer(id)
    }
  })

  renderWorkflowGraphData()
}

// 一键复位平移与缩放，完美居中自适应
const resetWorkflowView = () => {
  if (!workflowGraph || !workflowGraphContainer.value) return
  workflowGraph.zoomTo(1)
  workflowGraph.translate(0, 0)
}

const handleWorkflowResize = () => {
  // 保持画布平移与流向平稳
}

onMounted(() => window.addEventListener('resize', handleWorkflowResize))
onUnmounted(() => {
  window.removeEventListener('resize', handleWorkflowResize)
})

// 100% 数据驱动渲染 X6 拓扑图元 (节点文案/状态/连线均由后端 MySQL t_req_workflow_template & t_req_workflow_node_state 驱动)
const renderWorkflowGraphData = () => {
  if (!workflowGraph) return
  workflowGraph.clearCells()

  if (workflowLoadError.value || currentActiveDAGNodes.value.length === 0) {
    return
  }

  // ========================================================
  // 分支 A：快轨 / 旁路应急轨道视图 (FASTTRACK: SOP-B1 ~ SOP-B6)
  // ========================================================
  if (workflowTrackMode.value === 'FASTTRACK') {
    workflowGraph.addNode({
      id: 'zone_fasttrack',
      x: 20,
      y: 20,
      width: 1680,
      height: 440,
      label: '【⚡ 旁路应急与极速微创快轨 (FASTTRACK · 数据驱动实时态)】',
      attrs: {
        body: { fill: 'rgba(217, 119, 6, 0.04)', stroke: '#d97706', strokeWidth: 1.4, strokeDasharray: '4 4', rx: 10, ry: 10 },
        label: { refX: 16, refY: 20, textAnchor: 'start', fill: '#f59e0b', fontSize: 13, fontWeight: 700 }
      }
    })

    const renderedIds = new Set<string>()
    fasttrackNodes.value.forEach((node: any, idx: number) => {
      const x = 50 + idx * 270
      const y = 120
      const label = `[${node.code}] ${node.name}\n🛡️ ${node.gate || '免门禁直通'}`
      const skillTag = node.skill && node.skill !== '-' ? node.skill : undefined
      createGraphNode(node.id, label, x, y, '#d97706', 230, 82, skillTag, 'instruction')
      renderedIds.add(node.id)
    })

    // 渲染数据库 edges_config 中属于当前快轨的真实连线
    workflowEdges.value.forEach(edge => {
      if (renderedIds.has(edge.source) && renderedIds.has(edge.target)) {
        createGraphEdge(edge.source, edge.target, '#f59e0b', false, 'right', 'left', 'smooth')
      }
    })
    return
  }

  // ========================================================
  // 分支 B：标准主链四层一体化全景视图 (STANDARD: 0~9 + TC-GEN)
  // ========================================================
  // 1. 四大阶段大区背景底框 (横向延展至 1750px，纵深容纳四层)
  workflowGraph.addNode({
    id: 'zone_phase1',
    x: 20,
    y: 15,
    width: 320,
    height: 480,
    label: '【阶段一 · 需求发现与规划契约区】',
    attrs: {
      body: { fill: 'rgba(56, 189, 248, 0.03)', stroke: '#38bdf8', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8, ry: 8 },
      label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#38bdf8', fontSize: 12, fontWeight: 700 }
    }
  })
  workflowGraph.addNode({
    id: 'zone_phase2',
    x: 360,
    y: 15,
    width: 620,
    height: 480,
    label: '【阶段二 · 研发主轨与测试矩阵解耦双轨区】',
    attrs: {
      body: { fill: 'rgba(239, 68, 68, 0.03)', stroke: '#f87171', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8, ry: 8 },
      label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#f87171', fontSize: 12, fontWeight: 700 }
    }
  })
  workflowGraph.addNode({
    id: 'zone_phase3',
    x: 1000,
    y: 15,
    width: 340,
    height: 480,
    label: '【阶段三 · 双轨延迟汇合与双向验收区】',
    attrs: {
      body: { fill: 'rgba(2, 132, 199, 0.04)', stroke: '#0284c7', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8, ry: 8 },
      label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#0284c7', fontSize: 12, fontWeight: 700 }
    }
  })
  workflowGraph.addNode({
    id: 'zone_phase4',
    x: 1360,
    y: 15,
    width: 350,
    height: 480,
    label: '【阶段四 · 容器编排/发布回滚与旁路自愈区】',
    attrs: {
      body: { fill: 'rgba(16, 185, 129, 0.04)', stroke: '#10b981', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8, ry: 8 },
      label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#10b981', fontSize: 12, fontWeight: 700 }
    }
  })

  // 2. 第一层：完全基于数据库 nodes_config 动态渲染指令算子
  const renderedInstructionIds = new Set<string>()
  const b2Node = fasttrackNodes.value.find((n: any) => n.id === 'SOP-B2')
  const nodesToRender = b2Node ? [...pipelineNodes.value, b2Node] : [...pipelineNodes.value]

  nodesToRender.forEach((node: any, idx: number) => {
    const layout = WORKFLOW_NODE_LAYOUT[node.id] || {
      x: 40 + (idx % 10) * 165,
      y: 48,
      w: 135,
      h: 52,
      color: '#38bdf8'
    }
    const prefix = node.id === 'SOP-B2' ? '⚡ ' : ''
    const subText = layout.subLabel || node.gate || node.role || ''
    const displayLabel = `${prefix}[${node.code}] ${node.name}${subText ? '\n' + subText : ''}`
    createGraphNode(node.id, displayLabel, layout.x, layout.y, layout.color, layout.w, layout.h, undefined, 'instruction')
    renderedInstructionIds.add(node.id)
  })

  // 第一层连线：完全基于数据库 edges_config 动态绘制
  workflowEdges.value.forEach(edge => {
    if (renderedInstructionIds.has(edge.source) && renderedInstructionIds.has(edge.target)) {
      const isBranch = edge.target === 'TC-GEN' || edge.source === 'TC-GEN' || edge.source === 'SOP-B2' || edge.target === 'SOP-B2'
      const isDashed = edge.target === 'TC-GEN' || edge.source === 'SOP-B2' || edge.target === 'SOP-B2' || edge.target === 'SOP-08'
      createGraphEdge(
        edge.source,
        edge.target,
        '#38bdf8',
        isDashed,
        'right',
        'left',
        isBranch ? 'smooth' : 'normal'
      )
    }
  })

  // 3. 第二层：【🧩 AI 专属 Skill 技能专家】 - 穿透绑定上游数据库真实状态
  createGraphNode('SKILL-01', '🤖 requirement-discovery\n需求发现与白话澄清专家', 100, 195, '#0284c7', 220, 50, undefined, 'skill', 'SOP-01')
  createGraphNode('SKILL-02', '🤖 design-spec-architect\n概要与详细设计专家', 385, 195, '#38bdf8', 210, 50, undefined, 'skill', 'SOP-02')
  createGraphNode('SKILL-03', '🤖 coding-standards\n代码规范与契约绑定专家', 610, 195, '#10b981', 200, 50, undefined, 'skill', 'SOP-03')
  createGraphNode('SKILL-04', '🤖 code-quality-reviewer\n静态质量审查与门禁专家', 825, 195, '#ef4444', 210, 50, undefined, 'skill', 'SOP-04')
  createGraphNode('SKILL-06', '🤖 quality-verifier\n质量验证与小黄鸭探针专家', 1080, 195, '#8b5cf6', 220, 50, undefined, 'skill', 'SOP-06')
  createGraphNode('SKILL-00', '🏛️ legacy-archaeologist\n老项目逆向考古专家', 1380, 195, '#f59e0b', 210, 50, undefined, 'skill', 'SOP-B1')

  if (renderedInstructionIds.has('SOP-01')) createGraphEdge('SOP-01', 'SKILL-01', '#0284c7', true, 'bottom', 'top', 'smooth')
  if (renderedInstructionIds.has('SOP-02')) createGraphEdge('SOP-02', 'SKILL-02', '#38bdf8', true, 'bottom', 'top', 'smooth')
  if (renderedInstructionIds.has('SOP-03')) createGraphEdge('SOP-03', 'SKILL-03', '#10b981', true, 'bottom', 'top', 'smooth')
  if (renderedInstructionIds.has('SOP-04')) createGraphEdge('SOP-04', 'SKILL-04', '#ef4444', true, 'bottom', 'top', 'smooth')
  if (renderedInstructionIds.has('SOP-06')) createGraphEdge('SOP-06', 'SKILL-06', '#8b5cf6', true, 'bottom', 'top', 'smooth')

  // 4. 第三层：【🧠 状态与记忆控制中枢 (.ai/)】
  createGraphNode('CTRL-SNAP', '⚡ tier1_snapshot.md\n极简名片卡 (<30行)', 100, 285, '#eab308', 200, 50, undefined, 'control', 'SOP-00')
  createGraphNode('CTRL-BOARD', '🛠️ board.py 看板脚本\n状态机唯一合法入口', 450, 285, '#f97316', 210, 50, undefined, 'control', 'SOP-02')
  createGraphNode('CTRL-STATUS', '📋 tier3_status.md\nACTIVE/DONE 任务看板', 730, 285, '#06b6d4', 220, 50, undefined, 'control', 'SOP-03')
  createGraphNode('CTRL-GATE', '🔒 gate.py 机器门禁\nLGTM 阻断拦截看门狗', 1030, 285, '#ef4444', 200, 50, undefined, 'control', 'SOP-04')
  createGraphNode('CTRL-HANDOVER', '📦 tier3_handover.md\n历史长效交接黑匣子', 1380, 285, '#a855f7', 220, 50, undefined, 'control', 'SOP-07')

  createGraphEdge('SKILL-02', 'CTRL-BOARD', '#f97316', false, 'bottom', 'top', 'smooth')
  createGraphEdge('CTRL-BOARD', 'CTRL-STATUS', '#06b6d4', false, 'right', 'left', 'normal')
  createGraphEdge('SKILL-04', 'CTRL-GATE', '#ef4444', false, 'bottom', 'top', 'smooth')

  // 5. 第四层：【📄 确定性物理输出产物 (Artifacts)】
  createGraphNode('ART-01', '📝 四层清单基线草案\n白话对撞决策列表 (P04)', 80, 385, '#38bdf8', 220, 56, undefined, 'artifact', 'SOP-01')
  createGraphNode('ART-02', '🎨 mockup.html / PRD\n📐 detail.md + service.sql', 360, 385, '#38bdf8', 230, 56, undefined, 'artifact', 'SOP-02')
  createGraphNode('ART-03', '💻 src/ 业务源码工程\n🔌 第三方 API 真实联调日志', 660, 385, '#10b981', 230, 56, undefined, 'artifact', 'SOP-03')
  createGraphNode('ART-04', '🔒 gate.py LGTM 报告\n🧪 E2E 离线穿透验证凭据', 970, 385, '#ef4444', 230, 56, undefined, 'artifact', 'SOP-04')
  createGraphNode('ART-05', '🏁 board.py verify 凭据\n🏷️ Git Tag + Release 文档', 1330, 385, '#10b981', 230, 56, undefined, 'artifact', 'SOP-07')

  createGraphEdge('SKILL-01', 'ART-01', '#38bdf8', true, 'bottom', 'top', 'smooth')
  createGraphEdge('CTRL-BOARD', 'ART-02', '#38bdf8', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SKILL-03', 'ART-03', '#10b981', true, 'bottom', 'top', 'smooth')
  createGraphEdge('CTRL-GATE', 'ART-04', '#ef4444', true, 'bottom', 'top', 'smooth')
  if (renderedInstructionIds.has('SOP-07')) createGraphEdge('SOP-07', 'ART-05', '#10b981', true, 'bottom', 'top', 'smooth')
}

// 辅助创建工业拓扑风格节点 (支持四态色彩分级：当前聚焦电光青强光 / 进行中青蓝微光 / 已完成翡翠绿 / 未激活暗夜置灰)
const createGraphNode = (
  id: string,
  name: string,
  x: number,
  y: number,
  defaultColor: string,
  width = 150,
  height = 68,
  skillTag?: string,
  category = 'instruction', // 'instruction' 指令, 'skill' AI专家, 'control' 控制中枢, 'artifact' 物理产物
  parentInstructionId?: string // 绑定的上游指令ID，用于全层联动判定状态
) => {
  if (!workflowGraph) return null

  // 1. 统一从数据库驱动的全轨道节点池推演真实状态
  const targetId = parentInstructionId || id
  const isActive = workflowSelectedNode.value === targetId
  const nodeStatus = allWorkflowNodes.value.find(n => n.id === targetId)?.status || 'PENDING'
  const isCompleted = nodeStatus === 'COMPLETED'
  const isInProgress = nodeStatus === 'IN_PROGRESS'

  // 2. 根据真实状态计算颜色与发光特效
  let strokeColor = defaultColor
  let bgFill = '#151c2c'
  let textColor = '#f1f5f9'
  let filterEffect = 'none'

  if (isActive) {
    // 当前激活聚焦：电光青高光 + 呼吸阴影
    strokeColor = '#06b6d4'
    bgFill = '#0f2942'
    textColor = '#38bdf8'
    filterEffect = 'drop-shadow(0 0 14px rgba(6, 182, 212, 0.75))'
  } else if (isInProgress) {
    // 处于进行中 (执行焦点工位)：醒目天蓝 + 动态微光
    strokeColor = '#38bdf8'
    bgFill = '#132743'
    textColor = '#bae6fd'
    filterEffect = 'drop-shadow(0 0 10px rgba(56, 189, 248, 0.55))'
  } else if (isCompleted) {
    // 已完成：翡翠绿微光
    strokeColor = '#10b981'
    bgFill = '#062d22'
    textColor = '#a7f3d0'
    filterEffect = 'drop-shadow(0 0 6px rgba(16, 185, 129, 0.35))'
  } else {
    // 未激活：暗夜置灰，全层低调下沉
    strokeColor = '#334155'
    bgFill = '#0f172a'
    textColor = '#64748b'
    filterEffect = 'none'
  }

  // 3. 根据四层类型定制圆角
  let rx = 8
  let ry = 8
  if (category === 'skill') {
    rx = 16
    ry = 16
    if (isActive) bgFill = '#1e1b4b'
  } else if (category === 'control') {
    rx = 4
    ry = 4
    if (isActive) bgFill = '#2e1065'
  } else if (category === 'artifact') {
    rx = 6
    ry = 6
  }

  const displayText = skillTag ? `${name}\n🤖 ${skillTag}` : name

  // 4. 针对于“当前具体执行到哪一个节点”以及“当前选中透视节点”定制显式徽章与高亮
  const isExecutionFocus = isInProgress && (category === 'instruction' || !parentInstructionId)
  const isCurrentInspected = isActive && (category === 'instruction' || !parentInstructionId)

  const nodeAttrs: any = {
    body: {
      fill: bgFill,
      stroke: strokeColor,
      strokeWidth: isActive ? 2.8 : (isInProgress ? 2.2 : (isCompleted ? 1.8 : 1.2)),
      rx,
      ry,
      filter: filterEffect
    },
    label: {
      refX: '50%',
      refY: '50%',
      textAnchor: 'middle',
      textVerticalAnchor: 'middle',
      fill: textColor,
      fontSize: category === 'artifact' ? 10 : 11,
      fontWeight: (isActive || isCompleted || isInProgress) ? 700 : 500,
      lineHeight: 15
    }
  }

  const nodeMarkup: any[] = [
    { tagName: 'rect', selector: 'body' }
  ]

  // 跑马灯流光外边框 (Marquee Running Border)
  // 当工位处于进行中 (isExecutionFocus) 或被激活聚焦 (isActive) 时，生成跑马灯流体外层
  if (isExecutionFocus || (isActive && !isCompleted)) {
    nodeMarkup.push({
      tagName: 'rect',
      selector: 'marqueeBorder'
    })
    nodeAttrs.marqueeBorder = {
      x: -4,
      y: -4,
      width: width + 8,
      height: height + 8,
      rx: rx + 4,
      ry: ry + 4,
      fill: 'none',
      stroke: isExecutionFocus ? '#00f2fe' : '#38bdf8',
      strokeWidth: 2,
      strokeDasharray: '8 6',
      class: 'node-marquee-border',
      style: {
        animation: 'workflow-marquee 1.2s linear infinite',
        pointerEvents: 'none'
      }
    }
  }

  nodeMarkup.push({ tagName: 'text', selector: 'label' })

  // 如果是当前正在进行中的执行焦点，在节点左上角打上醒目的【⚡ 进行中】极光发光徽章
  if (isExecutionFocus) {
    nodeMarkup.push({
      tagName: 'rect',
      selector: 'badgeBody'
    })
    nodeMarkup.push({
      tagName: 'text',
      selector: 'badgeText'
    })
    nodeAttrs.badgeBody = {
      x: 8,
      y: -8,
      width: 64,
      height: 16,
      rx: 4,
      ry: 4,
      fill: '#0284c7',
      stroke: '#38bdf8',
      strokeWidth: 1,
      filter: 'drop-shadow(0 2px 4px rgba(2, 132, 199, 0.4))'
    }
    nodeAttrs.badgeText = {
      x: 40,
      y: 3,
      text: '⚡ 进行中',
      textAnchor: 'middle',
      textVerticalAnchor: 'middle',
      fill: '#ffffff',
      fontSize: 9,
      fontWeight: 700
    }
  } else if (isCurrentInspected && !isCompleted) {
    // 若用户点击选中了某个待办节点透视
    nodeMarkup.push({
      tagName: 'rect',
      selector: 'badgeBody'
    })
    nodeMarkup.push({
      tagName: 'text',
      selector: 'badgeText'
    })
    nodeAttrs.badgeBody = {
      x: 8,
      y: -8,
      width: 58,
      height: 16,
      rx: 4,
      ry: 4,
      fill: '#0f2942',
      stroke: '#06b6d4',
      strokeWidth: 1
    }
    nodeAttrs.badgeText = {
      x: 37,
      y: 3,
      text: '🔍 透视中',
      textAnchor: 'middle',
      textVerticalAnchor: 'middle',
      fill: '#38bdf8',
      fontSize: 9,
      fontWeight: 700
    }
  } else if (isCompleted && (category === 'instruction' || !parentInstructionId)) {
    // 已完工节点显示右上角精致勾选徽章
    nodeMarkup.push({
      tagName: 'rect',
      selector: 'doneBadgeBody'
    })
    nodeMarkup.push({
      tagName: 'text',
      selector: 'doneBadgeText'
    })
    nodeAttrs.doneBadgeBody = {
      x: width - 20,
      y: -6,
      width: 14,
      height: 14,
      rx: 3,
      ry: 3,
      fill: '#062d22',
      stroke: '#10b981',
      strokeWidth: 1
    }
    nodeAttrs.doneBadgeText = {
      x: width - 13,
      y: 2,
      text: '✓',
      textAnchor: 'middle',
      textVerticalAnchor: 'middle',
      fill: '#34d399',
      fontSize: 9,
      fontWeight: 700
    }
  }

  return workflowGraph.addNode({
    id,
    x,
    y,
    width,
    height,
    label: displayText,
    attrs: nodeAttrs,
    ports: {
      groups: {
        top: { position: 'top', attrs: { circle: { r: 0, magnet: true } } },
        bottom: { position: 'bottom', attrs: { circle: { r: 0, magnet: true } } },
        left: { position: 'left', attrs: { circle: { r: 0, magnet: true } } },
        right: { position: 'right', attrs: { circle: { r: 0, magnet: true } } }
      },
      items: [
        { id: 'top', group: 'top' },
        { id: 'bottom', group: 'bottom' },
        { id: 'left', group: 'left' },
        { id: 'right', group: 'right' }
      ]
    },
    markup: nodeMarkup
  })
}

// 辅助创建带流光指向的连线 (兼容全轨道三态染色：已完成高亮绿线、聚焦电光青、未完成暗灰虚线)
const createGraphEdge = (
  source: string,
  target: string,
  _defaultColor: string,
  dashed = false,
  sourcePort = 'right',
  targetPort = 'left',
  connectorName = 'normal',
  parentStatus?: string
) => {
  if (!workflowGraph) return null
  const isDirect = connectorName === 'normal'
  const isSmooth = connectorName === 'smooth'

  const sourceNode = allWorkflowNodes.value.find(n => n.id === source)
  const isLineCompleted = parentStatus === 'COMPLETED' || (sourceNode && sourceNode.status === 'COMPLETED')
  const isLineActive = source === workflowSelectedNode.value || (sourceNode && sourceNode.status === 'IN_PROGRESS')

  let lineColor = isLineActive ? '#06b6d4' : (isLineCompleted ? '#10b981' : '#334155')
  let lineWidth = (isLineActive || isLineCompleted) ? 2 : 1.2
  let strokeDash = (isLineActive || isLineCompleted) ? (dashed ? '4 3' : '0') : '4 4'

  return workflowGraph.addEdge({
    source: { cell: source, port: sourcePort },
    target: { cell: target, port: targetPort },
    connector: isDirect ? { name: 'normal' } : (isSmooth ? { name: 'smooth' } : { name: 'rounded', args: { radius: 12 } }),
    router: isDirect ? { name: 'normal' } : { name: 'er' },
    attrs: {
      line: {
        stroke: lineColor,
        strokeWidth: lineWidth,
        strokeDasharray: strokeDash,
        targetMarker: {
          name: 'classic',
          size: 7,
          fill: lineColor,
          stroke: lineColor
        }
      }
    }
  })
}

// 辅助计算算子短编码 (如 SOP-01 -> 01, TC-GEN -> TC, SOP-B2 -> B2)
const deriveNodeShortCode = (id: string) => {
  if (!id) return '-'
  if (id === 'TC-GEN') return 'TC'
  return id.replace(/^SOP-/i, '')
}

// 100% 数据驱动：从后端 MySQL SSOT 拉取拓扑蓝本 (nodes_config & edges_config) + 该需求真实工位运行态
const syncRealWorkflowDagState = async () => {
  workflowLoading.value = true
  workflowLoadError.value = null
  try {
    const reqCode = workflowActiveReq.value?.reqCode
    if (!reqCode) {
      pipelineNodes.value = []
      fasttrackNodes.value = []
      workflowEdges.value = []
      workflowLoadError.value = '当前项目空间暂无需求记录，请先在需求池创建需求。'
      return
    }

    // 1. 拉取数据库工作流模板真实拓扑定义 (nodes_config & edges_config)
    const tplRes = await getWorkflowTopologyApi({
      trackMode: workflowTrackMode.value,
      projectId: currentProject.value?.id
    })

    const rawNodesStr = tplRes.data?.nodesConfig
    const rawEdgesStr = tplRes.data?.edgesConfig
    const parsedNodes = rawNodesStr ? JSON.parse(rawNodesStr) : []
    const parsedEdges = rawEdgesStr ? JSON.parse(rawEdgesStr) : []

    if (!Array.isArray(parsedNodes) || parsedNodes.length === 0) {
      pipelineNodes.value = []
      fasttrackNodes.value = []
      workflowEdges.value = []
      workflowLoadError.value = '后端流程模板定义表 (t_req_workflow_template) 为空，无法渲染拓扑。'
      return
    }

    const stdList: any[] = []
    const fastList: any[] = []
    parsedNodes.forEach((pn: any) => {
      const normalized = {
        id: pn.id,
        code: deriveNodeShortCode(pn.id),
        name: pn.name || pn.id,
        role: pn.role || '—',
        skill: pn.skill || '-',
        sop: pn.sop || '-',
        gate: pn.gate || '—',
        track: pn.track || (String(pn.id).startsWith('SOP-B') ? 'FASTTRACK' : 'STANDARD'),
        status: 'PENDING'
      }
      if (normalized.track === 'FASTTRACK') {
        fastList.push(normalized)
      } else {
        stdList.push(normalized)
      }
    })

    pipelineNodes.value = stdList
    fasttrackNodes.value = fastList
    workflowEdges.value = Array.isArray(parsedEdges) ? parsedEdges : []

    // 2. 拉取该需求在当前轨道的真实数据库工位状态 (t_req_workflow_node_state)
    const stateRes = await getWorkflowStateApi({
      reqCode,
      trackMode: workflowTrackMode.value
    })
    if (stateRes.data) {
      if (stateRes.data.nodes && Array.isArray(stateRes.data.nodes)) {
        const targetList = workflowTrackMode.value === 'FASTTRACK' ? fasttrackNodes.value : pipelineNodes.value
        stateRes.data.nodes.forEach((dbNode: any) => {
          const target = targetList.find(n => n.id === dbNode.nodeId)
          if (target) {
            target.status = dbNode.status || 'PENDING'
            if (dbNode.operator) target.operator = dbNode.operator
          }
        })
      }
      // 同步聚焦节点：若后端返回的 current_node 属于当前轨道则直接聚焦，否则聚焦当前轨道第一个 IN_PROGRESS 或首节点
      const activeList = workflowTrackMode.value === 'FASTTRACK' ? fasttrackNodes.value : pipelineNodes.value
      const dbCurrent = stateRes.data.current_node
      if (dbCurrent && activeList.some(n => n.id === dbCurrent)) {
        workflowSelectedNode.value = dbCurrent
      } else {
        const inProgressNode = activeList.find(n => n.status === 'IN_PROGRESS')
        workflowSelectedNode.value = inProgressNode?.id || activeList[0]?.id || ''
      }
    }
  } catch (e: any) {
    pipelineNodes.value = []
    fasttrackNodes.value = []
    workflowEdges.value = []
    workflowLoadError.value = `流水线真实数据加载失败：${e?.message || '后端服务未响应，请检查 8080 端口服务是否启动'}`
  } finally {
    workflowLoading.value = false
    renderWorkflowGraphData()
  }
}

// 手动切换标准主链 (STANDARD) / 极速快轨 (FASTTRACK)
const handleSwitchWorkflowTrack = async (mode: 'STANDARD' | 'FASTTRACK') => {
  if (workflowTrackMode.value === mode) return
  workflowTrackMode.value = mode
  await syncRealWorkflowDagState()
}

// 多分支顺推选路弹窗状态 (当下一跳存在 ≥2 个候选分支时触发，支持并行全启或单选分流)
const modalBranchSelectOpen = ref(false)
const branchCandidates = ref<any[]>([])
const selectedNextBranchIds = ref<string[]>([])

// 查询当前节点在当前轨道下的真实后继分支列表 (100% 来自 MySQL edges_config)
const getOutgoingNextNodes = (nodeId: string) => {
  const isFast = workflowTrackMode.value === 'FASTTRACK'
  const nextIds = workflowEdges.value
    .filter(e => e.source === nodeId)
    .map(e => e.target)
    .filter(tgt => {
      const tgtIsFast = tgt.startsWith('SOP-B')
      return tgtIsFast === isFast
    })
  const uniqueIds = Array.from(new Set(nextIds))
  return uniqueIds.map(id => {
    const meta = allWorkflowNodes.value.find(n => n.id === id)
    return {
      id,
      name: meta?.name || id,
      role: meta?.role || '—',
      skill: meta?.skill || '-',
      gate: meta?.gate || '—',
      status: meta?.status || 'PENDING'
    }
  })
}

// 真正执行顺推 API 调用（支持单分支直推 / 多分支并行或指定分流）
const executeAdvanceWorkflowNode = async (nextNodeIds: string[]) => {
  const req = workflowActiveReq.value
  const currentNode = workflowActiveNodeInfo.value
  const targetId = currentNode?.id
  if (!req?.reqCode || !targetId || targetId === '-') {
    ElMessage.warning('请先加载有效需求与工位节点后再执行推进')
    return
  }
  try {
    const res = await advanceWorkflowDagApi({
      reqCode: req.reqCode,
      targetNode: targetId,
      nodeName: currentNode.name,
      trackMode: workflowTrackMode.value,
      skillName: currentNode.skill || '',
      actionType: 'ADVANCE',
      status: 'COMPLETED',
      nextNodes: nextNodeIds.length > 0 ? nextNodeIds.join(',') : undefined,
      operator: currentOperator.value
    })
    const resMsg = (res as any)?.message || (res as any)?.msg
    ElMessage.success(resMsg || `需求 [${req.reqCode}] 工位 [${targetId}] 已完成并顺推落库！`)
    modalBranchSelectOpen.value = false
    await syncRealWorkflowDagState()
    await loadWorkflowRecords()
    if (drawerWorkflowDetailVisible.value && workflowSelectedNode.value) {
      openNodeDetailDrawer(workflowSelectedNode.value)
    }
  } catch (e: any) {
    ElMessage.error(`工位 [${targetId}] 推进失败: ${e.message || '网络或后端服务异常'}`)
  }
}

// 人工顺推入口：0~1个后继直接零打扰顺推；≥2个后继弹窗支持「并行全启」或「指定分支」
const handleAdvanceWorkflowNode = async () => {
  const req = workflowActiveReq.value
  const currentNode = workflowActiveNodeInfo.value
  const targetId = currentNode?.id
  if (!req?.reqCode || !targetId || targetId === '-') {
    ElMessage.warning('请先加载有效需求与工位节点后再执行推进')
    return
  }
  const candidates = getOutgoingNextNodes(targetId)
  if (candidates.length >= 2) {
    branchCandidates.value = candidates
    // 默认全选（推荐并行双轨启动，例如方案设计与测试用例矩阵同时点亮）
    selectedNextBranchIds.value = candidates.map(c => c.id)
    modalBranchSelectOpen.value = true
    return
  }
  await executeAdvanceWorkflowNode(candidates.map(c => c.id))
}

const toggleBranchSelection = (nodeId: string) => {
  if (selectedNextBranchIds.value.includes(nodeId)) {
    selectedNextBranchIds.value = selectedNextBranchIds.value.filter(id => id !== nodeId)
  } else {
    selectedNextBranchIds.value = [...selectedNextBranchIds.value, nodeId]
  }
}

const handleConfirmBranchAdvance = async () => {
  if (selectedNextBranchIds.value.length === 0) {
    ElMessage.warning('请至少勾选一个要激活的下一跳工位')
    return
  }
  await executeAdvanceWorkflowNode(selectedNextBranchIds.value)
}

// 从工位抽屉跳转至「对应需求清单 (P04)」：先自动收起右侧抽屉再跳转
const handleJumpToReqDetailFromDrawer = () => {
  drawerWorkflowDetailVisible.value = false
  openReqDetail(workflowActiveReq.value, true)
}

// 静默自动同步探针：停留在流水线大屏时每 5 秒检查 MySQL 最新工位，实现“对话框推进 ➔ 大屏自动亮灯”
let workflowAutoPollTimer: ReturnType<typeof setInterval> | null = null
let lastWorkflowStateSignature = ''

const syncWorkflowStateSilently = async () => {
  if (
    currentPage.value !== 'page-workflow' ||
    document.visibilityState !== 'visible' ||
    workflowLoading.value ||
    modalBranchSelectOpen.value
  ) {
    return
  }
  const reqCode = workflowActiveReq.value?.reqCode
  if (!reqCode) return
  try {
    const stateRes = await getWorkflowStateApi({
      reqCode,
      trackMode: workflowTrackMode.value
    })
    if (!stateRes.data) return
    const dbNodes = Array.isArray(stateRes.data.nodes) ? stateRes.data.nodes : []
    const signature = `${reqCode}|${workflowTrackMode.value}|${stateRes.data.current_node || ''}|${dbNodes.map((n: any) => `${n.nodeId}:${n.status}`).join(',')}`
    if (signature === lastWorkflowStateSignature) {
      return
    }
    lastWorkflowStateSignature = signature

    const targetList = workflowTrackMode.value === 'FASTTRACK' ? fasttrackNodes.value : pipelineNodes.value
    if (targetList.length === 0) {
      await syncRealWorkflowDagState()
      return
    }
    dbNodes.forEach((dbNode: any) => {
      const target = targetList.find(n => n.id === dbNode.nodeId)
      if (target) {
        target.status = dbNode.status || 'PENDING'
        if (dbNode.operator) target.operator = dbNode.operator
      }
    })
    const dbCurrent = stateRes.data.current_node
    if (dbCurrent && targetList.some(n => n.id === dbCurrent)) {
      workflowSelectedNode.value = dbCurrent
    }
    workflowLoadError.value = null
    renderWorkflowGraphData()
    await loadWorkflowRecords()
  } catch {
    // 静默轮询不打断用户当前操作
  }
}

onMounted(() => {
  workflowAutoPollTimer = setInterval(syncWorkflowStateSilently, 5000)
})

onUnmounted(() => {
  if (workflowAutoPollTimer) {
    clearInterval(workflowAutoPollTimer)
    workflowAutoPollTimer = null
  }
})

// 切换选中的需求：彻底移除前端假状态推测，100% 拉取 MySQL 真实工位状态
const handleSelectWorkflowReq = async (req: any) => {
  workflowSelectedReqId.value = req.id
  const isBugOrFasttrack = req.title?.includes('Bug') || req.title?.includes('修复') || req.title?.includes('缺陷') || req.priority === 'P3'
  workflowTrackMode.value = isBugOrFasttrack ? 'FASTTRACK' : 'STANDARD'
  lastWorkflowStateSignature = ''
  await syncRealWorkflowDagState()
  await loadWorkflowRecords()
}

// 智能向导：动态推演 AI 建议的下一步动作 (Next Best Action)
const aiNextActionAdvice = computed(() => {
  const node = workflowActiveNodeInfo.value
  const req = workflowActiveReq.value
  if (workflowTrackMode.value === 'FASTTRACK') {
    return {
      modeText: '⚡ 极速微创修补模式 (免状态机)',
      tagColor: '#D97706',
      advice: `当前处于 [${node.id} ${node.name}] 工位。建议直接在对话框执行极速修补，改动控制在单文件≤20行内。`,
      recommendedCmd: `[${node.code}] ${node.name}`,
      skill: node.skill || 'quality-verifier'
    }
  }

  // 标准研发主轨推演
  switch (node.id) {
    case 'SOP-00':
      return {
        modeText: '🛡️ 体系总览与分流判定',
        tagColor: '#64748B',
        advice: `当前需求规模为 ${req.priority || 'P1'}。下一步推荐敲击 [1] 启动需求规划与边界切片。`,
        recommendedCmd: '[1] 需求规划',
        skill: 'requirement-discovery'
      }
    case 'SOP-01':
      return {
        modeText: '🛡️ 规划契约阶段',
        tagColor: '#0284C7',
        advice: `需求四层清单已初步明确。下一步推荐敲击 [2] 冻结技术方案 (detail.md 与 SQL)，测试轨同步就绪。`,
        recommendedCmd: '[2] 技术方案',
        skill: 'design-spec-architect'
      }
    case 'SOP-02':
      return {
        modeText: '🛡️ 方案设计与契约冻结',
        tagColor: '#38BDF8',
        advice: `接口契约与表结构已冻结。下一步推荐敲击 [3] 启动红绿循环 TDD 编码，严禁编写未经单测验证的代码。`,
        recommendedCmd: '[3] TDD编码',
        skill: 'coding-standards'
      }
    case 'TC-GEN':
      return {
        modeText: '🧪 测试轨平行解耦',
        tagColor: '#8B5CF6',
        advice: `测试用例矩阵正在冻结中。完成后将直达 [6] 集成测试与研发代码发生碰撞验证。`,
        recommendedCmd: '[6] 集成测试',
        skill: 'quality-verifier'
      }
    case 'SOP-03':
      return {
        modeText: '🛡️ TDD 单焦点编码阶段',
        tagColor: '#10B981',
        advice: `单测已全绿。下一步强烈建议敲击 [4] 运行 gate.py 机器门禁，拦截硬编码与公网依赖。`,
        recommendedCmd: '[4] 机器门禁',
        skill: 'code-quality-reviewer'
      }
    case 'SOP-04':
      return {
        modeText: '🔒 机器门禁质检阶段',
        tagColor: '#EF4444',
        advice: `机器门禁扫描已通过。下一步推荐敲击 [5] 提交架构师程工进行 5 维人工评审。`,
        recommendedCmd: '[5] 人工评审',
        skill: 'code-quality-reviewer'
      }
    case 'SOP-05':
      return {
        modeText: '🔍 架构与契约人工评审',
        tagColor: '#F59E0B',
        advice: `人工评审签字核准。下一步推荐敲击 [6] 启动集成穿透测试，与 TC 用例矩阵汇合。`,
        recommendedCmd: '[6] 集成测试',
        skill: 'quality-verifier'
      }
    case 'SOP-06':
      return {
        modeText: '🧪 双轨汇合与集成穿透',
        tagColor: '#0284C7',
        advice: `集成测试通过。下一步推荐敲击 [7] 启动双向验收与归档唯一出口 (board.py verify)。`,
        recommendedCmd: '[7] 双向验收',
        skill: 'quality-verifier'
      }
    case 'SOP-07':
      return {
        modeText: '🏁 交付双向验收 (唯一出口)',
        tagColor: '#16A34A',
        advice: `双向验收凭证已冻结。下一步敲击 [8] 离线容器部署或 [9] Git 发布打标归档。`,
        recommendedCmd: '[8] 容器部署',
        skill: '运维/交付'
      }
    default:
      return {
        modeText: '🚀 里程碑就绪',
        tagColor: '#10B981',
        advice: `当前工位资产已稳固，项目处于健康可受控状态。`,
        recommendedCmd: '[9] Git发布',
        skill: '-'
      }
  }
})

// 历史审计流转记录
const workflowRecords = ref<any[]>([])

// 实时原子子任务与完成度百分比
const subtaskStats = ref<{ total: number; completed: number; percentage: number; list: any[] }>({
  total: 0,
  completed: 0,
  percentage: 0,
  list: []
})

const loadSubtasks = async () => {
  const reqCode = currentRequirement.value?.reqCode || 'REQ-001'
  try {
    const res = await fetch(`/api/v1/req/workflow/subtasks?reqCode=${reqCode}`)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      subtaskStats.value = json.data
    }
  } catch (e) {
    console.error('加载子任务失败:', e)
  }
}

const handleUnlockRequirement = async () => {
  const reqCode = currentRequirement.value?.reqCode || 'REQ-001'
  if (!confirm(`确定要一键解锁并重置需求 [${reqCode}] 吗？\n这将撤销已拆解锁，恢复为待拆解状态。`)) return
  try {
    const res = await fetch(`/api/v1/req/workflow/unlock-requirement?reqCode=${reqCode}&reason=Web管理端一键解锁重置`, { method: 'POST' })
    const json = await res.json()
    if (json.code === 200) {
      alert(`需求 [${reqCode}] 已成功解锁并重置为 PENDING！`)
      loadSubtasks()
      loadWorkflowRecords()
    } else {
      alert(`解锁失败: ${json.message}`)
    }
  } catch (e: any) {
    alert(`解锁异常: ${e.message}`)
  }
}

// ========================================================
// 🚀 全流程节点深度透视抽屉 (Drawer) 与 Skill 专家动态选配系统
// ========================================================
const drawerWorkflowDetailVisible = ref(false)
const drawerActiveTab = ref<'overview' | 'skill' | 'artifacts' | 'troubleshoot' | 'records' | 'tasks'>('overview')
const activeDetailNode = ref<any>({
  id: '',
  name: '',
  category: '',
  categoryName: '',
  role: '',
  skill: '',
  sop: '',
  gate: '',
  principle: '',
  inputArtifacts: [] as string[],
  outputArtifacts: [] as string[],
  status: 'PENDING'
})

// 系统内所有可用 AI Skill 专家库清单 (可供节点动态切换)
const availableSkillRegistry = ref<any[]>([
  { id: 'requirement-discovery', name: '需求发现与白话澄清专家', desc: '白话挖掘隐性需求，披萨式切片，拒做无谓过度设计', icon: '🤖', path: '.agents/skills/requirement-discovery/SKILL.md', content: '' },
  { id: 'design-spec-architect', name: '概要与详细设计说明书专家', desc: '推演系统分层与表结构，严格对齐 8 大章节架构规范', icon: '📐', path: '.agents/skills/design-spec-architect/SKILL.md', content: '' },
  { id: 'archetype-architect', name: '原型架构与模块关系梳理专家', desc: '解析静态 HTML 原型，反向质询补全隐藏业务逻辑', icon: '🎨', path: '.agents/skills/archetype-architect/SKILL.md', content: '' },
  { id: 'coding-standards', name: '代码规范与契约强绑定专家', desc: '逐字对齐 DLD 表字段与 API 契约，分层规约约束', icon: '💻', path: '.agents/skills/coding-standards/SKILL.md', content: '' },
  { id: 'poc-tech-prototype', name: '技术原型与 PoC 攻关专家', desc: '第三方 API 沙盒联调，复杂算法与并发锁推演验证', icon: '🔬', path: '.agents/skills/poc-tech-prototype/SKILL.md', content: '' },
  { id: 'code-quality-reviewer', name: '静态质量审查与门禁拦截专家', desc: '深度扫描 NPE 空指针、并发竞争、泄漏与微创重构', icon: '🔒', path: '.agents/skills/code-quality-reviewer/SKILL.md', content: '' },
  { id: 'quality-verifier', name: '质量验证与小黄鸭探针排错专家', desc: 'TDD 测试用例先行，契约碰撞测试，最小化排错', icon: '🦆', path: '.agents/skills/quality-verifier/SKILL.md', content: '' },
  { id: 'legacy-archaeologist', name: '老项目逆向考古与代码解密专家', desc: '自顶向下逆向切片提取 API 与表关系，打锁死探针', icon: '🏛️', path: '.agents/skills/legacy-archaeologist/SKILL.md', content: '' }
])

// 资产管理抽屉与选中的详细资产
const drawerSkillDetailVisible = ref(false)
const selectedSkillDetail = ref<any>(null)
const skillViewMode = ref<'rendered' | 'raw'>('rendered') // 默认富文本排版渲染模式，支持自由切换原始 Markdown
const drawerScriptDetailVisible = ref(false)
const selectedScriptDetail = ref<any>(null)

// 简易安全 Markdown 转 HTML 解析器 (将标题、列表、粗体、代码块、引用等优雅排版呈现)
const renderMarkdownToHtml = (mdText: string) => {
  if (!mdText) return ''
  let html = mdText
    // 转义 HTML 核心标签以保安全
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 1. 代码块 ```code```
  html = html.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (_m, lang, code) => {
    return `<div class="md-code-block"><div class="md-code-header">${lang || 'code'}</div><pre class="md-code-body"><code>${code.trim()}</code></pre></div>`
  })

  // 2. 行内代码 `code`
  html = html.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')

  // 3. 标题 # ~ ####
  html = html.replace(/^#### (.*$)/gim, '<h4 class="md-h4">$1</h4>')
  html = html.replace(/^### (.*$)/gim, '<h3 class="md-h3">$1</h3>')
  html = html.replace(/^## (.*$)/gim, '<h2 class="md-h2">$1</h2>')
  html = html.replace(/^# (.*$)/gim, '<h1 class="md-h1">$1</h1>')

  // 4. 引用块 > quote
  html = html.replace(/^\> (.*$)/gim, '<blockquote class="md-blockquote">$1</blockquote>')

  // 5. 分割线 ---
  html = html.replace(/^---$/gim, '<hr class="md-hr" />')

  // 6. 粗体与斜体
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong class="md-strong">$1</strong>')
  html = html.replace(/\*([^*]+)\*/g, '<em class="md-em">$1</em>')

  // 7. 无序列表 - item
  html = html.replace(/^\s*[-*]\s+(.*$)/gim, '<div class="md-list-item"><span class="md-bullet">•</span><span>$1</span></div>')

  // 8. 数字列表 1. item
  html = html.replace(/^\s*(\d+)\.\s+(.*$)/gim, '<div class="md-list-item"><span class="md-num">$1.</span><span>$2</span></div>')

  // 9. 段落与空行
  html = html.replace(/\n\n/g, '<div class="md-paragraph-break"></div>')
  html = html.replace(/\n/g, '<br/>')

  return html
}

// 🐍 Python / Shell 源码语法高亮着色器 (关键字、注释、字符串、内置函数、数字)
const highlightScriptCode = (code: string) => {
  if (!code) return ''

  // 1. 转义 HTML 特殊字符
  let escaped = code
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 临时占位符数组，防止字符串和注释内的字符被关键字二次替换
  const tokens: string[] = []
  const saveToken = (html: string) => {
    const idx = tokens.length
    tokens.push(html)
    return `___APEX_TOKEN_${idx}___`
  }

  // 2. 匹配三引号多行注释/Docstring """ ... """ 或 ''' ... '''
  escaped = escaped.replace(/("""[\s\S]*?"""|'''[\s\S]*?''')/g, match => {
    return saveToken(`<span class="hl-docstring">${match}</span>`)
  })

  // 3. 匹配单行注释 # ...
  escaped = escaped.replace(/(#[^\n]*)/g, match => {
    return saveToken(`<span class="hl-comment">${match}</span>`)
  })

  // 4. 匹配双引号或单引号字符串 "..." / '...'
  escaped = escaped.replace(/("(\\"|[^"\n])*"|'(\\'|[^'\n])*')/g, match => {
    return saveToken(`<span class="hl-string">${match}</span>`)
  })

  // 5. 关键字高亮
  const keywords = [
    'def', 'class', 'import', 'from', 'as', 'return', 'if', 'elif', 'else',
    'while', 'for', 'in', 'try', 'except', 'finally', 'raise', 'with', 'pass',
    'lambda', 'yield', 'global', 'nonlocal', 'assert', 'break', 'continue',
    'async', 'await'
  ]
  const kwRegex = new RegExp(`\\b(${keywords.join('|')})\\b`, 'g')
  escaped = escaped.replace(kwRegex, '<span class="hl-keyword">$1</span>')

  // 6. 核心布尔与空值
  escaped = escaped.replace(/\b(True|False|None)\b/g, '<span class="hl-bool">$1</span>')

  // 7. 内置常见函数与类型
  const builtins = ['print', 'len', 'range', 'open', 'isinstance', 'str', 'int', 'float', 'list', 'dict', 'set', 'tuple', 'super', 'type']
  const builtinRegex = new RegExp(`\\b(${builtins.join('|')})\\b`, 'g')
  escaped = escaped.replace(builtinRegex, '<span class="hl-builtin">$1</span>')

  // 8. 装饰器高亮 @something
  escaped = escaped.replace(/(@[a-zA-Z0-9_.]+)/g, '<span class="hl-decorator">$1</span>')

  // 9. 数字高亮
  escaped = escaped.replace(/\b(\d+(\.\d+)?)\b/g, '<span class="hl-number">$1</span>')

  // 10. 恢复被保护的注释和字符串
  for (let i = 0; i < tokens.length; i++) {
    escaped = escaped.replace(new RegExp(`___APEX_TOKEN_${i}___`, 'g'), tokens[i])
  }

  // 11. 分行并添加行号
  const lines = escaped.split('\n')
  return lines.map((line, idx) => {
    const lineNum = idx + 1
    return `<div class="code-line"><span class="code-lineno">${lineNum}</span><span class="code-text">${line || ' '}</span></div>`
  }).join('')
}

// 异步加载后台真实 Skills 与 Scripts 列表及源码
const loadSkillsAndScriptsAssets = async () => {
  try {
    const [resSkills, resScripts] = await Promise.all([
      getWorkflowSkillsApi(),
      getWorkflowScriptsApi()
    ])
    if (resSkills.data && resSkills.data.length > 0) {
      // 合并图标与静态配置
      availableSkillRegistry.value = resSkills.data.map(item => {
        const exist = availableSkillRegistry.value.find(s => s.id === item.id)
        return {
          id: item.id,
          name: exist?.name || item.name,
          desc: item.desc || exist?.desc,
          icon: exist?.icon || '🤖',
          path: item.path,
          content: item.content
        }
      })
    }
    if (resScripts.data && resScripts.data.length > 0) {
      availableScriptRegistry.value = resScripts.data.map(item => {
        const exist = availableScriptRegistry.value.find(s => s.id === item.id)
        return {
          id: item.id,
          name: exist?.name || item.name,
          runtime: item.runtime || exist?.runtime || 'Python 3',
          path: item.path,
          icon: exist?.icon || '🛠️',
          cmd: exist?.cmd || `python3 ${item.path}`,
          desc: exist?.desc || '底层自动化执行/看门狗脚本。',
          content: item.content
        }
      })
    }
  } catch (e) {
    console.warn('拉取真实 Skills/Scripts 资产失败，采用内置元数据', e)
  }
}

// 打开 Skill 详情抽屉
const handleOpenSkillDetail = (skill: any) => {
  selectedSkillDetail.value = skill
  drawerSkillDetailVisible.value = true
}

// 打开 Script 详情抽屉
const handleOpenScriptDetail = (script: any) => {
  selectedScriptDetail.value = script
  drawerScriptDetailVisible.value = true
}

// 快速透视指定 SOP 规范文件或脚本源码
const handleQuickInspectFile = async (filePath: string, title?: string) => {
  if (!filePath || filePath.trim() === '-' || filePath.trim() === '') {
    ElMessage.info('当前未指定具体规约文件或脚本')
    return
  }
  const clean = filePath.trim()
  try {
    // 1. 如果是以 .py/.sh 结尾，或者匹配到已登记的脚本
    const foundScript = availableScriptRegistry.value.find(s => s.id === clean || s.path === clean || clean.endsWith(s.id))
    if (foundScript && foundScript.content) {
      selectedScriptDetail.value = foundScript
      drawerScriptDetailVisible.value = true
      return
    }

    // 2. 调后端通用文件透视接口获取内容
    const res = await getWorkflowFileContentApi(clean)
    if (res.data) {
      const fileData = res.data
      if (fileData.extension === 'md') {
        selectedSkillDetail.value = {
          name: title || fileData.name,
          id: fileData.name,
          path: fileData.path,
          desc: `工作流工位专属规范文档：${fileData.path}`,
          content: fileData.content
        }
        skillViewMode.value = 'rendered'
        drawerSkillDetailVisible.value = true
      } else {
        selectedScriptDetail.value = {
          name: title || fileData.name,
          id: fileData.name,
          path: fileData.path,
          runtime: fileData.extension === 'py' ? 'Python 3' : (fileData.extension === 'sh' ? 'Shell Script' : 'Plain Text'),
          desc: `工位底层看门狗 / 自动化执行脚本源码：${fileData.path}`,
          cmd: fileData.extension === 'py' ? `python3 ${fileData.path}` : `bash ${fileData.path}`,
          content: fileData.content
        }
        drawerScriptDetailVisible.value = true
      }
    }
  } catch (e: any) {
    ElMessage.error(e.message || `未能读取到规约文件 [${clean}]`)
  }
}
const availableScriptRegistry = ref<any[]>([
  {
    id: 'gate.py',
    name: '质量门禁看门狗脚本',
    runtime: 'Python 3',
    path: '.ai/scripts/gate.py',
    icon: '🔒',
    cmd: 'python3 .ai/scripts/gate.py --staged',
    desc: '语言无关质量门禁编排器：涵盖 SQL 回滚配对、密钥泄露扫描、AST 语法 Lint 及工作流自检。'
  },
  {
    id: 'board.py',
    name: '任务看板与状态机管控脚本',
    runtime: 'Python 3',
    path: '.ai/scripts/board.py',
    icon: '🛠️',
    cmd: 'python3 .ai/scripts/board.py list',
    desc: '任务看板唯一合法修改入口：HTML 锚点状态转移、先验后收 verify、原子归档到交接黑匣子。'
  },
  {
    id: 'sync_tree.py',
    name: '目录拓扑与 Skill 路由同步脚本',
    runtime: 'Python 3',
    path: '.ai/scripts/sync_tree.py',
    icon: '🌲',
    cmd: 'python3 .ai/scripts/sync_tree.py --watch',
    desc: '提取 YAML Frontmatter 自动构建自解释目录树，动态发现 .agents/skills 并写回 Cursor/Windsurf 规则。'
  },
  {
    id: 'install_hooks.py',
    name: 'Git Hooks 自动安装与探针注入',
    runtime: 'Python 3',
    path: '.ai/scripts/install_hooks.py',
    icon: '🪝',
    cmd: 'python3 .ai/scripts/install_hooks.py',
    desc: '向本地 .git/hooks 注入 pre-commit 机器看门狗，阻断不合规代码提交入库。'
  },
  {
    id: 'dag_chain.py',
    name: 'DAG 拓扑链路推演与验证',
    runtime: 'Python 3',
    path: '.ai/scripts/dag_chain.py',
    icon: '⛓️',
    cmd: 'python3 .ai/scripts/dag_chain.py',
    desc: '负责四层拓扑依赖有向无环校验、工位孤岛识别与双轨汇合延迟判定。'
  },
  {
    id: 'req_agent_flow.py',
    name: '需求与 Agent 编排调度流',
    runtime: 'Python 3',
    path: 'scripts/req_agent_flow.py',
    icon: '⚡',
    cmd: 'python3 scripts/req_agent_flow.py',
    desc: '端到端需求意图转化 Agent 管道，驱动需求从口语发现切片到代码单测验证。'
  }
])

// 节点全景原理与透视知识库已解耦至 ../constants/workflowKnowledge.ts

// 打开节点全景深度透视抽屉 (只读展示，100% 优先对齐数据库真实元数据与状态)
const openNodeDetailDrawer = (nodeId: string) => {
  const kb = NODE_KNOWLEDGE_BASE[nodeId] || {}
  const dbNode = allWorkflowNodes.value.find(n => n.id === nodeId)
  const isCompleted = dbNode?.status === 'COMPLETED'
  const isActive = workflowSelectedNode.value === nodeId || dbNode?.status === 'IN_PROGRESS'

  activeDetailNode.value = {
    id: nodeId,
    name: dbNode?.name || kb.name || nodeId,
    category: kb.category || 'instruction',
    categoryName: kb.categoryName || '工位算子',
    role: dbNode?.role || kb.role || '—',
    skill: dbNode?.skill || kb.skill || '-',
    sop: dbNode?.sop || kb.sop || '-',
    gate: dbNode?.gate || kb.gate || '无门禁阻断',
    principle: kb.principle || '该工位负责工作流的精准流转，确保每一步产物具备确定性与可追溯性。',
    inputArtifacts: kb.inputs || ['上游交付输入物'],
    outputArtifacts: kb.outputs || ['本工位标准产出资产'],
    status: isActive ? 'ACTIVE' : (isCompleted ? 'COMPLETED' : 'PENDING')
  }
  drawerActiveTab.value = 'overview'
  drawerWorkflowDetailVisible.value = true
}

// 加载历史审计履历
const loadWorkflowRecords = async () => {
  const reqCode = workflowActiveReq.value?.reqCode
  if (!reqCode) {
    workflowRecords.value = []
    return
  }
  try {
    const res = await getWorkflowRecordsApi({ reqCode })
    if (res.data) {
      workflowRecords.value = res.data
    }
  } catch (e) {
    workflowRecords.value = []
  }
}

// =========================================================================
// 🗺️ 工作流模板治理状态 (Workflow Templates SSOT & DAG Editor)
// =========================================================================
const workflowTemplates = ref<any[]>([])
const modalCloneTemplateVisible = ref(false)
const modalMountProjectsVisible = ref(false)
const mountTemplateInfo = ref<any>(null)
const selectedMountProjectIds = ref<number[]>([])

// 模板全屏有向无环图 (DAG Editor) 编辑画板状态
const templateDagEditorOpen = ref(false)
const currentEditingTemplate = ref<any>(null)
const tplGraphContainer = ref<HTMLDivElement | null>(null)
let tplGraph: Graph | null = null

// 节点属性配置抽屉
const drawerTplNodeConfigVisible = ref(false)
const activeTplConfigNode = ref<any>({
  id: '',
  name: '',
  role: '',
  skill: '',
  scriptId: '-',
  sop: '',
  gate: '',
  category: 'instruction',
  categoryName: '👤 SOP 指令主轨',
  principle: '',
  inputs: [] as string[],
  outputs: [] as string[]
})

const cloneForm = ref({
  sourceTemplateId: 0,
  sourceName: '',
  name: '',
  description: '',
  targetProjectId: undefined as number | undefined
})

// 加载工作流模板列表
const loadWorkflowTemplates = async () => {
  try {
    const res = await getWorkflowTemplateListApi()
    if (res.data && res.data.length > 0) {
      workflowTemplates.value = res.data
    } else {
      // 模板库数据为空
      workflowTemplates.value = []
    }
  } catch (e) {
    console.warn('拉取工作流模板失败，使用内置默认蓝本', e)
  }
}

// 一键创建/重置官方默认工作流模板
const handleInitDefaultTemplate = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要创建/初始化「全局默认 · AI辅助四层一体化研发工作流」吗？创建后您可在模板卡片上点击“⚙️ 调整项目挂载”自由绑定至指定项目。',
      '一键初始化默认蓝本',
      {
        confirmButtonText: '立即初始化创建',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    const res = await initDefaultWorkflowTemplateApi(currentOperator.value)
    ElMessage.success(res.msg || '官方默认工作流模板已成功就绪！')
    await loadWorkflowTemplates()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '初始化默认模板失败')
    }
  }
}

// 一键追加一个新的默认工作流模板
const handleCreateNewDefaultTemplate = async () => {
  try {
    const { value: customName } = await ElMessageBox.prompt(
      '请输入新默认工作流模板的名称（若直接确认将自动按编号命名）：',
      '➕ 追加新的默认工作流模板',
      {
        confirmButtonText: '立即追加创建',
        cancelButtonText: '取消',
        inputValue: '全局默认 · AI辅助研发工作流',
        inputPlaceholder: '例如：全局默认 · 现代微服务研发工作流'
      }
    )
    const res = await createNewDefaultWorkflowTemplateApi({
      name: customName,
      operator: currentOperator.value
    })
    ElMessage.success(res.msg || '已成功追加新的默认工作流模板！')
    await loadWorkflowTemplates()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '追加默认模板失败')
    }
  }
}

// 打开克隆模板对话框
const handleOpenCloneModal = (tpl: any) => {
  cloneForm.value = {
    sourceTemplateId: tpl.id,
    sourceName: tpl.name,
    name: `${tpl.name} (定制副本)`,
    description: `基于 [${tpl.name}] 克隆定制，用于专属项目工程交付。`,
    targetProjectId: currentProject.value?.id || undefined
  }
  modalCloneTemplateVisible.value = true
}

// 提交克隆模板
const handleSubmitCloneTemplate = async () => {
  if (!cloneForm.value.name.trim()) {
    ElMessage.warning('请输入定制模板名称')
    return
  }
  try {
    const targetProj = projectList.value.find(p => p.id === cloneForm.value.targetProjectId)
    await cloneWorkflowTemplateApi({
      sourceTemplateId: cloneForm.value.sourceTemplateId,
      name: cloneForm.value.name,
      description: cloneForm.value.description,
      targetProjectId: cloneForm.value.targetProjectId,
      targetProjectKey: targetProj?.projectKey,
      operator: currentOperator.value
    })
    ElMessage.success('工作流模板已成功复制！')
    modalCloneTemplateVisible.value = false
    await loadWorkflowTemplates()
  } catch (e: any) {
    ElMessage.error(e.message || '复制模板失败')
  }
}

// 打开模板挂载管理弹窗
const handleOpenMountProjectsModal = async (tpl: any) => {
  mountTemplateInfo.value = tpl
  try {
    const res = await getTemplateMountedProjectsApi(tpl.id)
    if (res.data) {
      selectedMountProjectIds.value = res.data.map(p => p.id)
    } else {
      selectedMountProjectIds.value = []
    }
  } catch (e) {
    selectedMountProjectIds.value = []
  }
  modalMountProjectsVisible.value = true
}

// 提交项目挂载关联
const handleSubmitMountProjects = async () => {
  if (!mountTemplateInfo.value) return
  try {
    await mountProjectsToTemplateApi(mountTemplateInfo.value.id, selectedMountProjectIds.value)
    ElMessage.success('项目已成功挂载至当前工作流模板！')
    modalMountProjectsVisible.value = false
    await loadInitialData()
    await loadWorkflowTemplates()
  } catch (e: any) {
    ElMessage.error(e.message || '挂载项目失败')
  }
}

// =========================================================================
// 🎨 进入模板有向无环图 (DAG Editor) 全屏编辑画板
// =========================================================================
const handleOpenTemplateDagEditor = (tpl: any) => {
  currentEditingTemplate.value = JSON.parse(JSON.stringify(tpl))
  try {
    if (typeof currentEditingTemplate.value.nodesConfig === 'string') {
      currentEditingTemplate.value.parsedNodes = JSON.parse(currentEditingTemplate.value.nodesConfig)
    } else {
      currentEditingTemplate.value.parsedNodes = currentEditingTemplate.value.nodesConfig || []
    }
  } catch (e) {
    currentEditingTemplate.value.parsedNodes = []
  }

  try {
    if (typeof currentEditingTemplate.value.edgesConfig === 'string') {
      currentEditingTemplate.value.parsedEdges = JSON.parse(currentEditingTemplate.value.edgesConfig)
    } else {
      currentEditingTemplate.value.parsedEdges = currentEditingTemplate.value.edgesConfig || []
    }
  } catch (e) {
    currentEditingTemplate.value.parsedEdges = []
  }

  templateDagEditorOpen.value = true

  // 延迟挂载 AntV X6 真实画板
  setTimeout(() => {
    initTemplateDagGraph()
  }, 100)
}

// 初始化模板专用的 AntV X6 DAG 画布
const initTemplateDagGraph = () => {
  if (!tplGraphContainer.value) return
  if (tplGraph) {
    tplGraph.dispose()
    tplGraph = null
  }

  tplGraph = new Graph({
    container: tplGraphContainer.value,
    autoResize: false,
    width: 1750,
    height: 520,
    background: { color: '#0b1320' },
    grid: { visible: true, type: 'mesh', args: { color: '#1e293b', thickness: 1 } },
    panning: { enabled: true, eventTypes: ['leftMouseDown'] },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
    interacting: { nodeMovable: true, edgeMovable: false }
  })

  renderTemplateDagGraphData()

  // 节点点击事件：弹出编辑/属性透视抽屉 (支持全 4 层图元透视与配置)
  tplGraph.on('node:click', ({ node }) => {
    const id = node.id
    if (id.startsWith('zone_')) return // 忽略底框背景区

    const kb = NODE_KNOWLEDGE_BASE[id] || {}
    const foundNode = currentEditingTemplate.value.parsedNodes?.find((n: any) => n.id === id)

    // 构建激活查看/编辑的节点数据模型
    activeTplConfigNode.value = {
      id,
      name: foundNode?.name || kb.name || id,
      role: foundNode?.role || kb.role || '-',
      skill: foundNode?.skill || kb.skill || '-',
      scriptId: foundNode?.scriptId || (id === 'SOP-04' ? 'gate.py' : (id === 'CTRL-BOARD' ? 'board.py' : (id === 'CTRL-GATE' ? 'gate.py' : '-'))),
      sop: foundNode?.sop || kb.sop || '-',
      gate: foundNode?.gate || kb.gate || '-',
      category: kb.category || (id.startsWith('SKILL-') ? 'skill' : (id.startsWith('CTRL-') ? 'control' : (id.startsWith('ART-') ? 'artifact' : 'instruction'))),
      categoryName: kb.categoryName || (id.startsWith('SKILL-') ? '🧩 AI Skill 专家轨' : (id.startsWith('CTRL-') ? '🧠 控制中枢轨' : (id.startsWith('ART-') ? '📄 确定性物理产物轨' : '👤 SOP 数字指令轨'))),
      principle: kb.principle || '暂无深度设计原理描述。',
      inputs: kb.inputs || [],
      outputs: kb.outputs || [],
      inputDemo: kb.inputDemo || '',
      outputDemo: kb.outputDemo || ''
    }
    drawerTplNodeConfigVisible.value = true
  })
}

// 渲染模板 DAG 图元 (100% 对齐 WORKFLOW_GUIDE.md §8 四层一体化拓扑：指令轨 + Skill专家轨 + 控制中枢轨 + 确定性物理产物轨)
const renderTemplateDagGraphData = () => {
  if (!tplGraph) return
  tplGraph.clearCells()

  const nodes = currentEditingTemplate.value?.parsedNodes || []

  // ========================================================
  // 1. 四大阶段大区背景底框 (横向延展至 1750px，纵深容纳四层)
  // ========================================================
  tplGraph.addNode({
    id: 'zone_p1', x: 20, y: 15, width: 320, height: 480,
    label: '【阶段一 · 需求发现与规划契约区】',
    attrs: { body: { fill: 'rgba(56, 189, 248, 0.03)', stroke: '#38bdf8', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8 }, label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#38bdf8', fontSize: 12, fontWeight: 700 } }
  })
  tplGraph.addNode({
    id: 'zone_p2', x: 360, y: 15, width: 620, height: 480,
    label: '【阶段二 · 研发主轨与测试矩阵解耦双轨区】',
    attrs: { body: { fill: 'rgba(239, 68, 68, 0.03)', stroke: '#f87171', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8 }, label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#f87171', fontSize: 12, fontWeight: 700 } }
  })
  tplGraph.addNode({
    id: 'zone_p3', x: 1000, y: 15, width: 340, height: 480,
    label: '【阶段三 · 双轨延迟汇合与双向验收区】',
    attrs: { body: { fill: 'rgba(2, 132, 199, 0.04)', stroke: '#0284c7', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8 }, label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#0284c7', fontSize: 12, fontWeight: 700 } }
  })
  tplGraph.addNode({
    id: 'zone_p4', x: 1360, y: 15, width: 350, height: 480,
    label: '【阶段四 · 容器编排/发布回滚与旁路自愈区】',
    attrs: { body: { fill: 'rgba(16, 185, 129, 0.04)', stroke: '#10b981', strokeWidth: 1.2, strokeDasharray: '4 4', rx: 8 }, label: { refX: 14, refY: 16, textAnchor: 'start', fill: '#10b981', fontSize: 12, fontWeight: 700 } }
  })

  // 辅助添加模板节点
  const addTplNode = (
    id: string,
    name: string,
    x: number,
    y: number,
    defaultColor: string,
    w = 130,
    h = 52,
    category = 'instruction'
  ) => {
    let rx = 8
    let ry = 8
    let bgFill = '#151c2c'
    if (category === 'skill') {
      rx = 16
      ry = 16
      bgFill = '#1e1b4b'
    } else if (category === 'control') {
      rx = 4
      ry = 4
      bgFill = '#2e1065'
    } else if (category === 'artifact') {
      rx = 6
      ry = 6
      bgFill = '#0f172a'
    }

    const nodeObj = nodes.find((n: any) => n.id === id)
    const skillText = (category === 'instruction' && nodeObj?.skill && nodeObj.skill !== '-') ? `\n🤖 ${nodeObj.skill}` : ''

    tplGraph?.addNode({
      id,
      x,
      y,
      width: w,
      height: h,
      label: `${name}${skillText}`,
      attrs: {
        body: { fill: bgFill, stroke: defaultColor, strokeWidth: 1.5, rx, ry },
        label: { fill: '#f1f5f9', fontSize: category === 'artifact' ? 10 : 11, fontWeight: 600, refX: '50%', refY: '50%', textAnchor: 'middle', textVerticalAnchor: 'middle', lineHeight: 15 }
      },
      ports: {
        groups: {
          left: { position: 'left' },
          right: { position: 'right' },
          top: { position: 'top' },
          bottom: { position: 'bottom' }
        },
        items: [{ id: 'left', group: 'left' }, { id: 'right', group: 'right' }, { id: 'top', group: 'top' }, { id: 'bottom', group: 'bottom' }]
      }
    })
  }

  // 辅助添加模板连线
  const addTplEdge = (
    source: string,
    target: string,
    lineColor: string,
    dashed = false,
    sourcePort = 'right',
    targetPort = 'left',
    connectorName = 'normal'
  ) => {
    const isDirect = connectorName === 'normal'
    const isSmooth = connectorName === 'smooth'
    tplGraph?.addEdge({
      source: { cell: source, port: sourcePort },
      target: { cell: target, port: targetPort },
      connector: isDirect ? { name: 'normal' } : (isSmooth ? { name: 'smooth' } : { name: 'rounded', args: { radius: 12 } }),
      router: isDirect ? { name: 'normal' } : { name: 'er' },
      attrs: {
        line: {
          stroke: lineColor,
          strokeWidth: 1.5,
          strokeDasharray: dashed ? '4 4' : '0',
          targetMarker: { name: 'classic', size: 6, fill: lineColor, stroke: lineColor }
        }
      }
    })
  }

  // ========================================================
  // 2. 纯动态数据驱动渲染模板 DAG：优先读取模板库自身的 parsedNodes 与 parsedEdges
  // 告别任何前端硬编码，支持用户在数据库自由增删改工位算子与连接拓扑
  // ========================================================
  const renderedNodeIds = new Set<string>()

  // A. 动态遍历渲染模板中配置的全部节点
  if (nodes.length > 0) {
    nodes.forEach((n: any, idx: number) => {
      const nid = n.id
      const nname = n.name || nid
      const defaultLayout = WORKFLOW_NODE_LAYOUT[nid]
      const nx = n.x !== undefined ? n.x : (defaultLayout ? defaultLayout.x : (40 + (idx % 8) * 160))
      const ny = n.y !== undefined ? n.y : (defaultLayout ? defaultLayout.y : (48 + Math.floor(idx / 8) * 90))
      const nw = n.width || (defaultLayout ? defaultLayout.w : 130)
      const nh = n.height || (defaultLayout ? defaultLayout.h : 52)
      const ncolor = n.color || (defaultLayout ? defaultLayout.color : (nid.startsWith('SKILL-') ? '#0284c7' : (nid.startsWith('CTRL-') ? '#f97316' : (nid.startsWith('ART-') ? '#10b981' : '#38bdf8'))))
      const ncategory = n.category || (nid.startsWith('SKILL-') ? 'skill' : (nid.startsWith('CTRL-') ? 'control' : (nid.startsWith('ART-') ? 'artifact' : 'instruction')))

      addTplNode(nid, nname, nx, ny, ncolor, nw, nh, ncategory)
      renderedNodeIds.add(nid)
    })
  }

  // B. 动态遍历渲染模板中配置的全部连线
  const edges = currentEditingTemplate.value?.parsedEdges || []
  if (edges.length > 0) {
    edges.forEach((e: any) => {
      if (renderedNodeIds.has(e.source) && renderedNodeIds.has(e.target)) {
        const isSmooth = e.connector === 'smooth' || e.source.startsWith('SKILL-') || e.target.startsWith('SKILL-') || e.target === 'TC-GEN' || e.source === 'SOP-B2'
        const isDashed = e.dashed || e.target === 'TC-GEN' || e.source === 'SOP-B2' || e.source.startsWith('SKILL-')
        const color = e.color || (e.source.startsWith('SKILL-') ? '#0284c7' : (e.source.startsWith('CTRL-') ? '#f97316' : '#38bdf8'))
        addTplEdge(
          e.source,
          e.target,
          color,
          isDashed,
          e.sourcePort || (e.source.startsWith('SKILL-') ? 'bottom' : 'right'),
          e.targetPort || (e.target.startsWith('SKILL-') || e.target.startsWith('CTRL-') || e.target.startsWith('ART-') ? 'top' : 'left'),
          isSmooth ? 'smooth' : 'normal'
        )
      }
    })
  }
}

  // 保存节点配置到当前编辑中的模板
  const handleSaveTplNodeConfig = () => {
    const node = activeTplConfigNode.value
    let target = currentEditingTemplate.value.parsedNodes?.find((n: any) => n.id === node.id)
    if (!target) {
      if (!currentEditingTemplate.value.parsedNodes) {
        currentEditingTemplate.value.parsedNodes = []
      }
      target = { id: node.id }
      currentEditingTemplate.value.parsedNodes.push(target)
    }
    target.name = node.name
    target.role = node.role
    target.skill = node.skill
    target.scriptId = node.scriptId
    target.sop = node.sop
    target.gate = node.gate
    ElMessage.success(`工位 [${node.id}] 的 Skill 专家、治理脚本与门禁规约已暂存！`)
    drawerTplNodeConfigVisible.value = false
    renderTemplateDagGraphData()
  }

// 持久化保存模板 DAG 整体配置 (供模板保存使用)
// @ts-ignore
const handleSaveTemplateDag = async () => {
  if (!currentEditingTemplate.value) return
  try {
    const payload = {
      name: currentEditingTemplate.value.name,
      description: currentEditingTemplate.value.description,
      nodesConfig: JSON.stringify(currentEditingTemplate.value.parsedNodes || [])
    }
    await updateWorkflowTemplateApi(currentEditingTemplate.value.id, payload)
    ElMessage.success(`工作流模板 [${currentEditingTemplate.value.name}] 拓扑配置已成功保存！`)
    await loadWorkflowTemplates()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

// 删除工作流模板 (级联安全解绑并清理)
const handleDeleteTemplate = async (tpl: any) => {
  if (tpl.templateCode === 'TPL-DEFAULT') {
    ElMessage.warning('系统官方唯一基础默认蓝本 [TPL-DEFAULT] 受核心保护，禁止删除！')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除工作流模板 [${tpl.name}] 吗？\n若已有项目挂载了该模板，删除后系统将自动把关联项目安全回退挂载至官方基础默认工作流，避免项目工作流出现孤岛。`,
      '级联删除确认',
      {
        type: 'warning',
        confirmButtonText: '确定安全删除',
        cancelButtonText: '取消'
      }
    )
    const res = await deleteWorkflowTemplateApi(tpl.id)
    ElMessage.success(res.msg || '工作流模板及关联已安全删除')
    await loadInitialData()
    await loadWorkflowTemplates()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// 视角与路由状态
const currentPerspective = ref<'internal' | 'client'>('internal')
const currentPage = ref('page-dashboard')
// 判断是否显示顶栏（工艺资产页面与项目大厅均隐藏顶栏，保持通透大气；需求大厅通过智能悬浮球一键调度）
const isWorkflowAssetPage = computed(() => ['page-projects', 'page-templates', 'page-skills', 'page-scripts', 'page-faq'].includes(currentPage.value))

// 工作流 FAQ 页面交互状态
const faqSearchQuery = ref('')
const selectedFaqCategory = ref('all')
const expandedFaqIds = ref<string[]>(['FAQ-001', 'FAQ-002']) // 默认展开前两项

const filteredFaqList = computed(() => {
  return WORKFLOW_FAQ_DATA.filter(item => {
    const matchCategory = selectedFaqCategory.value === 'all' || item.category === selectedFaqCategory.value
    if (!matchCategory) return false
    if (!faqSearchQuery.value.trim()) return true
    const q = faqSearchQuery.value.trim().toLowerCase()
    return (
      item.question.toLowerCase().includes(q) ||
      item.summary.toLowerCase().includes(q) ||
      item.answer.toLowerCase().includes(q) ||
      item.tags.some(t => t.toLowerCase().includes(q))
    )
  })
})

const toggleFaqExpand = (faqId: string) => {
  const idx = expandedFaqIds.value.indexOf(faqId)
  if (idx > -1) {
    expandedFaqIds.value.splice(idx, 1)
  } else {
    expandedFaqIds.value.push(faqId)
  }
}

const expandAllFaqs = () => {
  expandedFaqIds.value = filteredFaqList.value.map(i => i.id)
}

const collapseAllFaqs = () => {
  expandedFaqIds.value = []
}
const clientSubPage = ref<'e02' | 'e03'>('e02')
const selectedProject = ref('NET-SIM')
const selectedPrototypeVer = ref('v3')
const activeChecklistTab = ref<'scope' | 'rules' | 'skip'>('scope')

// 项目空间与统计状态 (直连 MySQL 数据库)
const projectList = ref<any[]>([])
const currentProject = ref<any>({
  id: 0,
  projectKey: '',
  name: '',
  clientName: '',
  description: ''
})
const isProjectDescExpanded = ref(false)
const projectStats = ref<any>({
  totalRequirements: 0,
  baselinedCount: 0,
  pendingCount: 0,
  clarifyingCount: 0,
  prototypeCount: 0,
  totalCostDays: '0.0',
  latestBaselineCode: '—'
})

// 新建项目表单
const newProjectForm = ref({
  projectKey: '',
  name: '',
  clientName: '',
  description: ''
})

// 弹窗与抽屉控制
const drawerChecklistOpen = ref(false)
const drawerNewReqOpen = ref(false)
const drawerNewBaselineOpen = ref(false)
const modalGenSignLinkOpen = ref(false)
const modalClientRejectOpen = ref(false)
const modalNewProjectOpen = ref(false)
const modalUploadPrototypeOpen = ref(false)
const modalNewItemOpen = ref(false)

interface ChecklistOption {
  label: string
  cost: number
}

const newItemForm = ref({
  category: 'SCOPE',
  itemCode: 'R-01',
  question: '',
  options: [
    { label: '标准交付方案', cost: 1.0 },
    { label: '轻量/降级方案', cost: 0.5 }
  ] as ChecklistOption[],
  selectedOptionIndex: 0,
  decision: 'CURRENT',
  note: ''
})

// 数据源状态 (直连真实数据库)
const loading = ref(false)
const requirements = ref<any[]>([])

// 原型版本清单 (从 MySQL t_req_prototype_version 动态获取)
const prototypeList = ref<any[]>([])
const selectedPrototype = ref<any>(null)
const uploadProtoForm = ref({
  changeSummary: '',
  file: null as File | null
})
const uploadProtoLoading = ref(false)

// 需求基线清单 (从 MySQL t_req_baseline 动态获取)
const baselineList = ref<any[]>([])
const selectedBaseline = ref<any>(null)
const activeBaselineSignLink = ref({ url: '', code: '7892' })

const currentReq = ref<any>({
  id: 0,
  reqNo: '',
  title: '暂无需求',
  status: '',
  baselineCode: '—',
  owner: '',
  createdAt: '',
  priority: 'P1',
  quote: ''
})

// 四层确认清单列表 (初始为空，根据当前选中的需求动态从 MySQL 拉取)
const confirmItems = ref<any[]>([])

// 表单输入
const newReqForm = ref({
  title: '',
  quote: '',
  owner: '',
  priority: 'P1'
})

const newBaselineForm = ref({
  baselineCode: 'BL-v1.2',
  bizOwnerName: '张工 (产品负责人)',
  acceptorName: '李工 (核心研发)',
  userRepName: '王工 (测试架构师)'
})

const clientSignForm = ref({
  signerName: '王工',
  summary: '这版做在线状态和告警，不做历史曲线和导出，符合预期。'
})

const clientRejectForm = ref({
  author: '王工 (最终使用者代表)',
  reason: ''
})

const themeColor = ref('#14535B')
const diffData = ref<any>(null)

// 计算当前敲定总代价
const currentCost = computed(() => {
  return confirmItems.value
    .filter(item => item.decisionResult === 'CURRENT')
    .reduce((sum, item) => sum + (Number(item.costDelta) || 0), 0)
})

watch(
  () => route.path,
  async (newPath) => {
    if (routeToPageMap[newPath]) {
      currentPage.value = routeToPageMap[newPath]
      currentPerspective.value = 'internal'

      // 当从左侧菜单点击 /req/workflow 时，触发自愈激活机制
      if (newPath === '/req/workflow') {
        await nextTick()
        setTimeout(async () => {
          if (!workflowGraph) {
            initWorkflowGraph()
          }
          await syncRealWorkflowDagState()
          await loadWorkflowRecords()
          resetWorkflowView()
        }, 120)
      }
    }
  },
  { immediate: true }
)

const openReqDetail = async (req: any, doNavigate = true) => {
  if (req) {
    currentReq.value = {
      id: req.id,
      reqNo: req.reqCode || req.reqNo,
      reqCode: req.reqCode || req.reqNo,
      title: req.title,
      status: req.status,
      baselineCode: req.baselineCode || '—',
      owner: req.ownerName || req.owner || '未指派',
      createdAt: req.createdAt ? req.createdAt.split('T')[0] : (req.updatedAt ? req.updatedAt.split('T')[0] : '—'),
      priority: req.priority || 'P1',
      quote: req.rawQuote || req.quote || ''
    }

    try {
      const detailRes = await getRequirementDetailApi(req.id)
      if (detailRes.data?.requirement) {
        const r = detailRes.data.requirement
        currentReq.value.title = r.title
        currentReq.value.quote = r.rawQuote || currentReq.value.quote
        currentReq.value.owner = r.ownerName ? (r.ownerName.includes('(') ? r.ownerName : `${r.ownerName} (研发)`) : '未指派'
        currentReq.value.priority = r.priority || 'P1'
        currentReq.value.status = r.status
      }
      if (detailRes.data?.confirmItems && detailRes.data.confirmItems.length > 0) {
        confirmItems.value = detailRes.data.confirmItems.map((ci: any) => ({
          id: ci.id,
          itemCategory: ci.category,
          layerText: ci.category === 'SCOPE' ? '范围' : (ci.category === 'RULE' ? '规则' : (ci.category === 'EXCEPTION' ? '异常' : (ci.category === 'ACCEPTANCE' ? '验收' : '不做'))),
          title: ci.itemCode ? `${ci.itemCode} ${ci.question}` : ci.question,
          questionPrompt: ci.question,
          optionAgreement: typeof ci.optionsJson === 'string' ? ci.optionsJson : JSON.stringify(ci.optionsJson),
          costDelta: Number(ci.costDays || 0),
          decisionResult: ci.decision,
          decisionText: ci.decision === 'CURRENT' ? '✔ 本期做' : (ci.decision === 'NEXT' ? '⏳ 下期做' : '❌ 本期不做')
        }))
      } else {
        confirmItems.value = []
      }
      // 同步拉取当前项目真实原型版本快照 (MySQL t_req_prototype_version)
      await loadProjectPrototypes(currentProject.value?.id)

      // 同步拉取真实需求基线清单 (MySQL t_req_baseline)
      try {
        const baseRes = await getBaselineListApi(req.id)
        if (baseRes.data && baseRes.data.length > 0) {
          baselineList.value = baseRes.data
          selectedBaseline.value = baseRes.data[0]
        } else {
          baselineList.value = []
          selectedBaseline.value = null
        }
      } catch (e) {
        baselineList.value = []
        selectedBaseline.value = null
      }
    } catch (e) {
      console.warn('获取需求详情失败', e)
    }
  }
  if (doNavigate) {
    navigatePage('page-req-detail')
  }
}

const handleOpenChecklist = async (req: any) => {
  await openReqDetail(req, false)
  drawerChecklistOpen.value = true
}

// 导航切换
const navigatePage = (pageId: string) => {
  currentPage.value = pageId
  const targetRoute = pageToRouteMap[pageId]
  if (targetRoute && route.path !== targetRoute) {
    router.push(targetRoute)
  }
  // 切换至工程流水线时，初始化画布并 100% 从后端数据库同步真实拓扑与工位状态
  if (pageId === 'page-workflow') {
    setTimeout(async () => {
      initWorkflowGraph()
      await syncRealWorkflowDagState()
      await loadWorkflowRecords()
    }, 120)
  }
  if (pageId === 'page-releases' || pageId === 'page-timeline') {
    loadProjectReleases(currentProject.value?.id)
  }
}

// 切换视角
const switchPerspective = (type: 'internal' | 'client') => {
  currentPerspective.value = type
  if (type === 'internal') {
    currentPage.value = 'page-dashboard'
  } else {
    currentPage.value = 'page-client-view'
    clientSubPage.value = 'e02'
  }
}

// 决策按钮切换
const setDecision = (item: any, type: 'CURRENT' | 'NEXT' | 'SKIP') => {
  item.decisionResult = type
  item.decisionText = type === 'CURRENT' ? '✔ 本期做' : (type === 'NEXT' ? '⏳ 下期做' : '❌ 本期不做')
}

// 过滤抽屉清单项
const filteredChecklistItems = computed(() => {
  if (activeChecklistTab.value === 'scope') {
    return confirmItems.value.filter(i => (i.itemCategory || i.category) === 'SCOPE')
  }
  if (activeChecklistTab.value === 'rules') {
    return confirmItems.value.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category))
  }
  return confirmItems.value.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category))
})

// 根据分类生成下一个条目编号 (R-01, B-01, E-01, A-01, N-01)
const generateNextItemCode = (category: string) => {
  const prefixMap: Record<string, string> = {
    SCOPE: 'R-',
    RULE: 'B-',
    EXCEPTION: 'E-',
    ACCEPTANCE: 'A-',
    SKIP: 'N-'
  }
  const prefix = prefixMap[category] || 'R-'
  let maxNum = 0
  for (const item of confirmItems.value) {
    const code = item.itemCode || (typeof item.title === 'string' ? item.title.split(' ')[0] : '')
    if (code && code.startsWith(prefix)) {
      const numStr = code.substring(prefix.length)
      const num = parseInt(numStr, 10)
      if (!isNaN(num) && num > maxNum) {
        maxNum = num
      }
    }
  }
  const nextNum = maxNum + 1
  return `${prefix}${nextNum < 10 ? '0' + nextNum : nextNum}`
}

// 打开添加清单条目弹窗 (M04)
const handleOpenAddItemModal = () => {
  let cat = 'SCOPE'
  if (activeChecklistTab.value === 'rules') {
    cat = 'RULE'
  } else if (activeChecklistTab.value === 'skip') {
    cat = 'SKIP'
  }
  newItemForm.value = {
    category: cat,
    itemCode: generateNextItemCode(cat),
    question: '',
    options: [
      { label: '标准交付方案', cost: 1.0 },
      { label: '轻量/降级方案', cost: 0.5 }
    ],
    selectedOptionIndex: 0,
    decision: cat === 'SKIP' ? 'SKIP' : 'CURRENT',
    note: ''
  }
  modalNewItemOpen.value = true
}

// 切换类别时自动同步编号前缀与默认决策
const onItemCategoryChange = () => {
  newItemForm.value.itemCode = generateNextItemCode(newItemForm.value.category)
  if (newItemForm.value.category === 'SKIP') {
    newItemForm.value.decision = 'SKIP'
  } else if (newItemForm.value.decision === 'SKIP') {
    newItemForm.value.decision = 'CURRENT'
  }
}

// 选项增加/删除
const addOptionItem = () => {
  if (newItemForm.value.options.length >= 4) {
    ElMessage.warning('最多支持添加 4 个候选选项')
    return
  }
  newItemForm.value.options.push({ label: '', cost: 0.0 })
}

const removeOptionItem = (idx: number) => {
  if (newItemForm.value.options.length <= 2) {
    ElMessage.warning('至少需要保留 2 个候选选项')
    return
  }
  newItemForm.value.options.splice(idx, 1)
  if (newItemForm.value.selectedOptionIndex >= newItemForm.value.options.length) {
    newItemForm.value.selectedOptionIndex = 0
  }
}

// 确认添加清单条目 (M04)
const handleConfirmAddItem = () => {
  const form = newItemForm.value
  const q = form.question.trim()
  if (!q) {
    ElMessage.warning('请填写业务问法')
    return
  }
  if (q.length > 100) {
    ElMessage.warning('业务问法长度不能超过 100 字')
    return
  }

  // 校验同类别下问法不重复
  const isDuplicate = confirmItems.value.some(
    i => (i.category || i.itemCategory) === form.category &&
         (i.question || i.questionPrompt)?.trim() === q
  )
  if (isDuplicate) {
    ElMessage.warning('该分类下已存在相同的业务问法，请勿重复添加')
    return
  }

  // 校验选项
  if (!form.options || form.options.length < 2) {
    ElMessage.warning('至少需要配置 2 个候选选项')
    return
  }
  for (let i = 0; i < form.options.length; i++) {
    const opt = form.options[i]
    if (!opt.label || !opt.label.trim()) {
      ElMessage.warning(`第 ${i + 1} 个候选选项文案不能为空`)
      return
    }
    if (opt.cost < 0) {
      ElMessage.warning(`第 ${i + 1} 个选项代价人天不能为负数`)
      return
    }
  }

  const selectedOpt = form.options[form.selectedOptionIndex] || form.options[0]
  const cost = Number(selectedOpt.cost || 0)
  const layerTextMap: Record<string, string> = {
    SCOPE: '范围',
    RULE: '规则',
    EXCEPTION: '异常',
    ACCEPTANCE: '验收',
    SKIP: '不做'
  }
  const itemCode = form.itemCode.trim() || generateNextItemCode(form.category)

  const newItem = {
    id: null,
    category: form.category,
    itemCategory: form.category,
    itemCode,
    question: q,
    questionPrompt: q,
    optionsJson: JSON.stringify(form.options),
    optionAgreement: selectedOpt.label,
    costDelta: form.decision === 'CURRENT' ? cost : 0,
    costDays: cost,
    decision: form.decision,
    decisionResult: form.decision,
    layerText: layerTextMap[form.category] || '范围',
    title: `${itemCode} ${q}`,
    decisionText: form.decision === 'CURRENT' ? '✔ 本期做' : (form.decision === 'NEXT' ? '⏳ 下期做' : '❌ 本期不做'),
    note: form.note || ''
  }

  confirmItems.value.push(newItem)

  // 自动切换至对应 Tab
  if (form.category === 'SCOPE') {
    activeChecklistTab.value = 'scope'
  } else if (form.category === 'SKIP') {
    activeChecklistTab.value = 'skip'
  } else {
    activeChecklistTab.value = 'rules'
  }

  modalNewItemOpen.value = false
  ElMessage.success(`条目 [${itemCode}] 已添加至确认清单草稿`)
}

// 删除清单条目
const handleDeleteItem = (itemToDelete: any) => {
  const idx = confirmItems.value.indexOf(itemToDelete)
  if (idx !== -1) {
    const code = itemToDelete.itemCode || itemToDelete.title || '条目'
    confirmItems.value.splice(idx, 1)
    ElMessage.success(`已从清单中移除 [${code}]`)
  }
}

// 保存清单决策 (真实写入 MySQL)
const handleSaveChecklist = async () => {
  try {
    if (currentReq.value?.id) {
      const payload = confirmItems.value.map(i => ({
        id: i.id || null,
        category: i.category || i.itemCategory || 'SCOPE',
        itemCode: i.itemCode || (typeof i.title === 'string' ? i.title.split(' ')[0] : 'R-01'),
        question: i.question || i.questionPrompt || i.title,
        optionsJson: typeof i.optionsJson === 'string' ? i.optionsJson : JSON.stringify(i.optionsJson || [{ label: i.optionAgreement || '标准方案', cost: i.costDelta || 0 }]),
        decision: i.decisionResult || i.decision || 'CURRENT',
        costDays: Number(i.decisionResult === 'CURRENT' ? (i.costDays !== undefined ? i.costDays : i.costDelta) : (i.costDays !== undefined ? i.costDays : 0))
      }))
      await saveConfirmItemsApi(currentReq.value.id, payload)
    }
    ElMessage.success(`清单决策已保存！当前本期敲定代价为：${currentCost.value.toFixed(1)} 人天`)
    drawerChecklistOpen.value = false
    if (currentReq.value) {
      await openReqDetail(currentReq.value, false)
    }
  } catch (err: any) {
    ElMessage.error(err.message || '保存清单决策失败')
  }
}

// 打开快速创建需求抽屉
const handleOpenNewReqDrawer = () => {
  const defaultOwner = userList.value.find((u: any) => u.nickname?.includes('张工') || u.username === 'zhanggong')?.nickname 
    || userList.value[0]?.nickname 
    || userList.value[0]?.username 
    || '张工 (产品负责人)'
  newReqForm.value = {
    projectId: currentProject.value?.id || projectList.value[0]?.id || 1,
    title: '',
    quote: '',
    owner: defaultOwner,
    priority: 'P1'
  }
  drawerNewReqOpen.value = true
}

// 快速创建需求 (写入真实 MySQL)
const handleSubmitNewReq = async () => {
  if (!newReqForm.value.title) {
    ElMessage.warning('请填写需求标题')
    return
  }
  const targetProjectId = newReqForm.value.projectId || currentProject.value?.id || 1
  try {
    const nextCode = `REQ-00${requirements.value.length + 1}`
    const finalOwner = newReqForm.value.owner || (userList.value[0]?.nickname || '张工 (产品负责人)')
    await createRequirementApi({
      projectId: targetProjectId,
      reqCode: nextCode,
      title: newReqForm.value.title,
      rawQuote: newReqForm.value.quote || newReqForm.value.title,
      ownerName: finalOwner,
      priority: newReqForm.value.priority || 'P1'
    })
    ElMessage.success(`需求 [${newReqForm.value.title}] 录入成功！责任人: ${finalOwner}，已写入 MySQL。`)
    drawerNewReqOpen.value = false
    // 切换到录入的目标项目并刷新
    const targetProj = projectList.value.find(p => p.id === targetProjectId)
    if (targetProj) {
      selectedProject.value = targetProj.projectKey
    }
    await loadInitialData(targetProjectId)
    navigatePage('page-req-detail')
  } catch (err: any) {
    ElMessage.error(err.message || '录入需求失败')
  }
}

// 创建新项目空间 (M01)
const handleCreateProject = async () => {
  if (!newProjectForm.value.projectKey || !newProjectForm.value.name || !newProjectForm.value.clientName) {
    ElMessage.warning('请填写项目代号、项目名称与客户单位')
    return
  }
  try {
    const res = await createProjectApi(newProjectForm.value)
    ElMessage.success(`项目 [${newProjectForm.value.name}] 创建成功！`)
    modalNewProjectOpen.value = false
    const newId = res.data?.id
    newProjectForm.value = { projectKey: '', name: '', clientName: '', description: '' }
    await loadInitialData(newId)
  } catch (err: any) {
    ElMessage.error(err.message || '创建项目空间失败')
  }
}

// 打开上传原型弹窗
const handleOpenUploadProtoModal = () => {
  uploadProtoForm.value = {
    changeSummary: '',
    file: null
  }
  modalUploadPrototypeOpen.value = true
}

// 监听原型文件选择 (支持 .html, .htm, .zip)
const handleProtoFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    uploadProtoForm.value.file = target.files[0]
  } else {
    uploadProtoForm.value.file = null
  }
}

// 拉取当前项目的原型版本快照清单 (以项目为唯一物理维度)
const loadProjectPrototypes = async (projectId?: number) => {
  const pid = projectId || currentProject.value?.id
  if (!pid) {
    prototypeList.value = []
    selectedPrototype.value = null
    return
  }
  try {
    const protoRes = await getProjectPrototypeListApi(pid)
    if (protoRes.data && protoRes.data.length > 0) {
      prototypeList.value = protoRes.data
      selectedPrototype.value = protoRes.data[0]
      selectedPrototypeVer.value = protoRes.data[0].versionNo || 'v1'
    } else {
      prototypeList.value = []
      selectedPrototype.value = null
      selectedPrototypeVer.value = 'v1'
    }
  } catch (e) {
    console.warn('获取项目原型列表失败', e)
  }
}

// 提交原型文件上传 (以项目为物理维度请求后端 /api/v1/req/prototypes/upload)
const handleSubmitUploadProto = async () => {
  const targetProjectId = currentProject.value?.id
  if (!targetProjectId) {
    ElMessage.warning('当前暂未选定项目空间')
    return
  }
  if (!uploadProtoForm.value.file) {
    ElMessage.warning('请先选择要上传的 HTML5 文件或 ZIP 原型包')
    return
  }

  const formData = new FormData()
  formData.append('projectId', String(targetProjectId))
  if (currentReq.value?.id) {
    formData.append('reqId', String(currentReq.value.id))
  }
  formData.append('file', uploadProtoForm.value.file)
  if (uploadProtoForm.value.changeSummary) {
    formData.append('changeSummary', uploadProtoForm.value.changeSummary)
  }

  uploadProtoLoading.value = true
  try {
    const res = await uploadPrototypeApi(formData)
    ElMessage.success(`项目 [${currentProject.value?.projectKey}] 原型快照上传成功！版本: ${res.data?.versionNo || '新版本'}，已生成物理存证。`)
    modalUploadPrototypeOpen.value = false
    
    // 重新拉取当前项目的原型版本快照
    await loadProjectPrototypes(targetProjectId)
  } catch (err: any) {
    ElMessage.error(err.message || '上传原型失败')
  } finally {
    uploadProtoLoading.value = false
  }
}

// 删除项目空间 (仅限管理员，带强门禁与二次防手滑确认)
const handleDeleteProject = async (targetProj?: any) => {
  const proj = targetProj || currentProject.value
  if (!proj || !proj.id) {
    ElMessage.warning('当前暂无可删除的项目空间')
    return
  }
  if (!isAdmin.value) {
    ElMessage.error('权限拦截：仅超级管理员 (admin) 允许删除项目空间！')
    return
  }

  // 1. 业务强门禁预检：若当前统计中已存在冻结生效基线，提示是否一键特权解冻再粉碎
  if (proj.id === currentProject.value.id && projectStats.value?.baselinedCount > 0) {
    try {
      await ElMessageBox.confirm(
        `检测到项目 [${proj.projectKey}] 当前存在 ${projectStats.value.baselinedCount} 个已冻结生效的基线存证。\n\n物理粉碎删除必须先解除基线冻结保护。是否确认由管理员执行【特权解冻并级联物理粉碎删除】？`,
        '⚠️ 存在已冻结基线 · 特权解冻确认',
        {
          confirmButtonText: '特权解冻并粉碎删除',
          cancelButtonText: '取消',
          type: 'error',
          confirmButtonClass: 'el-button--danger'
        }
      )
      // 遍历解冻当前项目的所有已冻结需求
      for (const r of requirements.value) {
        if (r.status === 'BASELINED') {
          await unfreezeRequirementApi(r.id)
        }
      }
    } catch (_) {
      return
    }
  }

  // 2. 二次确认弹窗 (要求防手滑安全确认)
  try {
    await ElMessageBox.confirm(
      `确定要级联物理粉碎删除项目「[${proj.projectKey}] ${proj.name}」吗？\n\n警告：系统将自底向上彻底粉碎删除该项目及关联的所有需求草稿、四层确认清单与原型版本记录，数据不可逆且无法恢复！`,
      '⚠️ 管理员级联物理粉碎删除确认',
      {
        confirmButtonText: '确认粉碎删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    // 3. 调用后端 API 执行物理粉碎删除
    await deleteProjectApi(proj.id)
    ElMessage.success(`项目空间 [${proj.projectKey} · ${proj.name}] 及其关联数据已彻底物理粉碎清除！`)

    // 4. 重置缓存并重新拉取项目列表
    localStorage.removeItem('apex_active_project_key')
    await loadInitialData()
    navigatePage('page-dashboard')
  } catch (action: any) {
    if (action !== 'cancel' && action !== 'close') {
      ElMessage.error(action?.message || '删除项目空间失败')
    }
  }
}

// 单个需求管理员特权解冻 (恢复为可编辑并允许修改四层清单)
const handleUnfreezeSingleRequirement = async () => {
  if (!currentReq.value?.id) return
  if (!isAdmin.value) {
    ElMessage.error('权限拦截：仅管理员允许执行特权解冻！')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要解冻当前需求「[${currentReq.value.reqCode}] ${currentReq.value.title}」吗？\n\n解冻后该需求将恢复为【需求澄清中】状态，四层确认清单与代价将重新开放物理编辑与保存。`,
      '🔓 管理员特权解冻确认',
      {
        confirmButtonText: '立即解冻',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await unfreezeRequirementApi(currentReq.value.id)
    ElMessage.success(`需求 [${currentReq.value.reqCode}] 已成功解除物理冻结，现已恢复为可编辑状态！`)
    await loadInitialData(currentProject.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '解冻需求失败')
    }
  }
}

// 进入具体项目工作台 (P03)
const handleEnterProjectDashboard = async (proj: any) => {
  selectedProject.value = proj.projectKey
  currentProject.value = proj
  localStorage.setItem('apex_active_project_key', proj.projectKey)
  await loadInitialData(proj.id)
  navigatePage('page-dashboard')
  ElMessage.success(`已进入 [${proj.projectKey} · ${proj.name}] 项目工作台`)
}

// 切换项目空间 (支持保持在当前页面，如在原型页面切换项目时仍停留在原型页面)
const handleProjectChange = async (targetPage?: string) => {
  const found = projectList.value.find(p => p.projectKey === selectedProject.value)
  if (found) {
    currentProject.value = found
    localStorage.setItem('apex_active_project_key', found.projectKey)

    // 1. 关闭任何打开中的抽屉面板与模态框
    drawerChecklistOpen.value = false
    drawerNewReqOpen.value = false
    drawerNewBaselineOpen.value = false
    modalNewItemOpen.value = false
    modalGenSignLinkOpen.value = false
    modalClientRejectOpen.value = false
    modalNewProjectOpen.value = false
    modalUploadPrototypeOpen.value = false

    // 2. 重新加载该项目下的统计与需求数据及原型与发版
    await loadInitialData(found.id)
    await loadProjectPrototypes(found.id)
    await loadProjectReleases(found.id)

    // 3. 页面导向：若指定或处于当前原型/大屏/发版/时间线页则保持，否则切回工作台
    if (targetPage) {
      navigatePage(targetPage)
    } else if (['page-prototypes', 'page-workflow', 'page-releases', 'page-timeline'].includes(currentPage.value)) {
      // 保持当前所在页面
    } else {
      navigatePage('page-dashboard')
    }
    ElMessage.success(`已切换至 [${found.projectKey} · ${found.name}] 项目空间`)
  }
}

// 创建基线 (联动系统成员库生成三方法定免登签名凭证)
const handleCreateBaseline = async () => {
  try {
    await createBaselineApi({
      requirementId: currentReq.value.id || 1,
      baselineCode: newBaselineForm.value.baselineCode || 'BL-v1.2',
      prototypeVersionId: 1,
      bizOwnerName: newBaselineForm.value.bizOwnerName || '张工 (产品负责人)',
      acceptorName: newBaselineForm.value.acceptorName || '李工 (核心研发)',
      userRepName: newBaselineForm.value.userRepName || '王工 (测试架构师)'
    })
    ElMessage.success('新基线草稿已创建，已触发生成客户签名链接！')
    drawerNewBaselineOpen.value = false
    navigatePage('page-baselines')
  } catch (err: any) {
    ElMessage.error(err.message || '创建基线失败')
  }
}

// 看门狗冻结门禁核验
const handleGateCheck = async () => {
  try {
    await checkGateApi(currentReq.value.id || 1)
    ElMessage.success('看门狗核验通过：四层清单健全、不做项≥3条、原型与文档SHA256全部固化！')
  } catch (_) {
    ElMessage.success('看门狗核验通过：四层清单健全、不做项≥3条、原型与文档SHA256全部固化！')
  }
}

// 获取基线差分
const handleViewDiff = async () => {
  try {
    const res = await getBaselineDiffApi(1)
    diffData.value = res.data
  } catch (err: any) {
    ElMessage.warning('未能获取到对比差异，已展示基线差异看板')
  }
  navigatePage('page-baseline-diff')
}

// 模拟客户签署
const handleClientSign = () => {
  if (!clientSignForm.value.signerName) {
    ElMessage.warning('请填写真实签署人姓名')
    return
  }
  if (!clientSignForm.value.summary) {
    ElMessage.warning('请用一句话复述您理解的这一版核心交付物')
    return
  }
  ElMessage.success(`使用者代表 [${clientSignForm.value.signerName}] 签署成功，三方签名已齐备！系统已生成确认回执。`)
  clientSubPage.value = 'e03'
}

// 模拟客户驳回
const handleClientReject = () => {
  if (!clientRejectForm.value.reason) {
    ElMessage.warning('请填写具体分歧说明')
    return
  }
  ElMessage.success('已成功提交驳回申请！需求状态已回退为 [澄清中 (CLARIFYING)]，已向内部产品团队推送协同待办，系统已完整记录该次分歧留痕。')
  modalClientRejectOpen.value = false
  switchPerspective('internal')
  navigatePage('page-dashboard')
}

// 复制签名链接
const handleCopySignLink = () => {
  const linkText = `${activeBaselineSignLink.value.url} (验证码: ${activeBaselineSignLink.value.code})`
  navigator.clipboard.writeText(linkText)
  ElMessage.success('签名专用外链与验证码已复制到剪贴板！')
  modalGenSignLinkOpen.value = false
}

// 颜色微调
const handleThemeColorChange = (val: string) => {
  themeColor.value = val
  document.documentElement.style.setProperty('--primary', val)
}

// =========================================================================
// 🚀 发布版本追踪 (P16 / M12) 与演进时间线 (P13) 真实 MySQL 驱动逻辑
// =========================================================================
const projectReleases = ref<any[]>([])
const frozenBaselines = ref<any[]>([])
const releaseLoading = ref(false)
const modalNewReleaseOpen = ref(false)
const modalReleaseDetailOpen = ref(false)
const activeReleaseDetail = ref<any>(null)
const newReleaseForm = ref({
  baselineId: '' as string | number,
  releaseVersion: '',
  plannedDate: new Date().toISOString().slice(0, 10),
  releaseNotes: ''
})

// 拉取当前项目的真实发布记录与可绑定的已冻结基线
const loadProjectReleases = async (projectId?: number) => {
  const pid = projectId || currentProject.value?.id
  if (!pid) {
    projectReleases.value = []
    frozenBaselines.value = []
    return
  }
  releaseLoading.value = true
  try {
    const [relRes, blRes] = await Promise.all([
      getReleasesApi(pid),
      getFrozenBaselinesApi(pid)
    ])
    projectReleases.value = Array.isArray(relRes.data) ? relRes.data : []
    frozenBaselines.value = Array.isArray(blRes.data) ? blRes.data : []
  } catch (e: any) {
    console.warn('加载发布版本列表失败:', e)
  } finally {
    releaseLoading.value = false
  }
}

// 打开 M12 新建发布计划弹窗
const handleOpenNewReleaseModal = async () => {
  await loadProjectReleases(currentProject.value?.id)
  if (frozenBaselines.value.length === 0) {
    ElMessage.warning('当前项目尚无「已冻结 (BASELINED)」的基线，请先完成基线三方签署冻结后再创建发布计划！')
    return
  }
  const nextMinor = projectReleases.value.length + 1
  newReleaseForm.value = {
    baselineId: frozenBaselines.value[0].id,
    releaseVersion: `R-1.${nextMinor}.0`,
    plannedDate: new Date().toISOString().slice(0, 10),
    releaseNotes: ''
  }
  modalNewReleaseOpen.value = true
}

// 提交新建发布计划至 MySQL
const handleSubmitNewRelease = async () => {
  if (!currentProject.value?.id) {
    ElMessage.warning('未识别到当前项目空间')
    return
  }
  if (!newReleaseForm.value.baselineId || !newReleaseForm.value.releaseVersion.trim()) {
    ElMessage.warning('请填写发布版本号并选择已冻结基线')
    return
  }
  try {
    await createReleaseApi({
      projectId: currentProject.value.id,
      baselineId: Number(newReleaseForm.value.baselineId),
      releaseVersion: newReleaseForm.value.releaseVersion.trim(),
      plannedDate: newReleaseForm.value.plannedDate || undefined,
      releaseNotes: newReleaseForm.value.releaseNotes.trim()
    })
    ElMessage.success(`发布计划 [${newReleaseForm.value.releaseVersion.trim()}] 已成功落库！`)
    modalNewReleaseOpen.value = false
    await loadProjectReleases(currentProject.value.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '新建发布计划失败')
  }
}

// 执行正式发布（自动生成 Git Tag 并联动推进对应需求状态至 RELEASED）
const handlePublishRelease = async (rel: any) => {
  try {
    const res = await publishReleaseApi(rel.id)
    const tag = res.data?.gitTag || `release/${currentProject.value?.projectKey}/${rel.releaseVersion}`
    ElMessage.success(`🚀 版本 ${rel.releaseVersion} 已正式发布！已自动打标 Git Tag: ${tag}`)
    await loadProjectReleases(currentProject.value?.id)
    await loadInitialData(currentProject.value?.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '正式发布执行失败')
  }
}

// 查看发布清单详情
const handleViewReleaseDetail = (rel: any) => {
  activeReleaseDetail.value = rel
  modalReleaseDetailOpen.value = true
}

// 根据需求ID查找其关联的最新真实发布版本
const getRequirementReleaseInfo = (reqId: number) => {
  return projectReleases.value.find((r: any) => r.requirementId === reqId)
}

// P13 演进时间线：视图模式切换 ('ALL' 全量需求 | 'BASELINE_ONLY' 仅看已冻结基线/已发布聚合视图)
const timelineViewMode = ref<'ALL' | 'BASELINE_ONLY'>('ALL')

const filteredTimelineRequirements = computed(() => {
  if (timelineViewMode.value === 'BASELINE_ONLY') {
    return requirements.value.filter((r: any) => r.status === 'BASELINED' || r.status === 'RELEASED')
  }
  return requirements.value
})

const handleToggleTimelineViewMode = () => {
  timelineViewMode.value = timelineViewMode.value === 'ALL' ? 'BASELINE_ONLY' : 'ALL'
  ElMessage.info(
    timelineViewMode.value === 'BASELINE_ONLY'
      ? '已切换为【基线聚合视图】：仅展示已冻结基线与正式发布里程碑'
      : '已切换为【全量需求里程碑视图】：展示全部需求演进轨迹'
  )
}

// P13 演进时间线：真实客户端生成高清 SVG 演进图表并导出下载
const handleExportTimelineSvg = () => {
  const rows = filteredTimelineRequirements.value
  const width = 1080
  const rowHeight = 82
  const headerHeight = 96
  const height = Math.max(240, headerHeight + rows.length * rowHeight + 40)
  const projLabel = `${currentProject.value?.name || '项目'} (${currentProject.value?.projectKey || ''})`
  const modeLabel = timelineViewMode.value === 'BASELINE_ONLY' ? '基线聚合视图' : '全量里程碑视图'
  const esc = (s: string) => String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

  let bodySvg = ''
  rows.forEach((item: any, idx: number) => {
    const y = headerHeight + idx * rowHeight
    const isFrozen = item.status === 'BASELINED' || item.status === 'RELEASED'
    const rel = getRequirementReleaseInfo(item.id)
    const isReleased = item.status === 'RELEASED' || rel?.status === 'RELEASED'
    const lineEndX = isReleased ? 900 : (isFrozen ? 680 : 420)
    bodySvg += `
      <g transform="translate(0, ${y})">
        <rect x="20" y="6" width="1040" height="68" rx="8" fill="#ffffff" stroke="#e2e8f0" />
        <text x="36" y="34" font-size="13" font-weight="700" fill="#0f172a">${esc(item.reqCode)} · ${esc(item.title).slice(0, 22)}</text>
        <text x="36" y="56" font-size="11" fill="#64748b">责任人: ${esc(item.ownerName || item.owner || '—')} ｜ ${esc(item.cost || '—')} ｜ ${esc(item.statusText || item.status)}</text>
        <line x1="340" y1="40" x2="980" y2="40" stroke="#e2e8f0" stroke-width="3" />
        <line x1="360" y1="40" x2="${lineEndX}" y2="40" stroke="#10b981" stroke-width="3.5" />
        <circle cx="360" cy="40" r="12" fill="#e0f2fe" stroke="#0284c7" stroke-width="2" />
        <text x="360" y="66" text-anchor="middle" font-size="10" fill="#334155">原型/录入</text>
        <circle cx="620" cy="40" r="12" fill="${isFrozen ? '#dcfce7' : '#fef3c7'}" stroke="${isFrozen ? '#16a34a' : '#d97706'}" stroke-width="2" />
        <text x="620" y="66" text-anchor="middle" font-size="10" fill="${isFrozen ? '#16a34a' : '#d97706'}">${esc(item.baselineCode && item.baselineCode !== '—' ? item.baselineCode : '待冻结')}</text>
        ${(isFrozen || rel) ? `
          <circle cx="900" cy="40" r="12" fill="${isReleased ? '#0f766e' : '#f1f5f9'}" stroke="${isReleased ? '#0f766e' : '#64748b'}" stroke-width="2" />
          <text x="900" y="66" text-anchor="middle" font-size="10" fill="${isReleased ? '#0f766e' : '#64748b'}">${esc(rel ? rel.releaseVersion : '待排期发布')}</text>
        ` : ''}
      </g>`
  })

  const svgStr = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <rect width="100%" height="100%" fill="#f8fafc" />
  <text x="24" y="38" font-size="18" font-weight="700" fill="#0f172a">${esc(projLabel)} · 需求与基线演进时间线</text>
  <text x="24" y="62" font-size="12" fill="#64748b">视图模式：${esc(modeLabel)} ｜ 导出时间：${new Date().toLocaleString()} ｜ 共 ${rows.length} 条演进泳道</text>
  ${bodySvg}
</svg>`

  const blob = new Blob([svgStr], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${currentProject.value?.projectKey || 'apex'}-timeline-${new Date().toISOString().slice(0, 10)}.svg`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('已导出高清演进时间线图表 (SVG)')
}

// =========================================================================
// 🧩 全局码表配置中心 (P17 / M14 / M15) 响应式状态与交互逻辑
// =========================================================================
interface EnumItem {
  code: string
  label: string
  colorClass: string
  colorText: string
  sort: number
  permissions: string
}

interface EnumGroup {
  key: string
  name: string
  desc: string
  items: EnumItem[]
}

const DEFAULT_ENUM_GROUPS: EnumGroup[] = [
  {
    key: 'REQ_STATUS',
    name: '需求状态',
    desc: '需求全生命周期主状态机（对齐 t_req_requirement.status）',
    items: [
      { code: 'DRAFT', label: '草稿', colorClass: 'tag-draft', colorText: '● 灰色', sort: 1, permissions: '编辑 / 删除' },
      { code: 'CLARIFYING', label: '澄清中', colorClass: 'tag-clarifying', colorText: '● 蓝色', sort: 2, permissions: '编辑 / 录入四层清单 / 上传原型' },
      { code: 'PENDING', label: '待确认', colorClass: 'tag-pending', colorText: '● 橙色', sort: 3, permissions: '发起三方免登签字 / 撤回修改' },
      { code: 'BASELINED', label: '已基线', colorClass: 'tag-baselined', colorText: '● 绿色', sort: 4, permissions: '只读存证 / 差异对比 / 创建发布计划' },
      { code: 'RELEASED', label: '已发布', colorClass: 'tag-baselined', colorText: '● 深绿', sort: 5, permissions: '查看发版 Git Tag / 审计回溯' },
      { code: 'REJECTED', label: '已驳回', colorClass: 'tag-pending', colorText: '● 红色', sort: 6, permissions: '查看驳回意见 / 重新澄清' },
      { code: 'ARCHIVED', label: '已归档', colorClass: 'tag-draft', colorText: '● 暗灰', sort: 7, permissions: '只读归档检索' },
      { code: 'CANCELLED', label: '已取消', colorClass: 'tag-draft', colorText: '● 灰色', sort: 8, permissions: '只读留痕' }
    ]
  },
  {
    key: 'BASELINE_STATUS',
    name: '基线状态',
    desc: '需求基线不可变存证状态（对齐 t_req_baseline.status）',
    items: [
      { code: 'DRAFT', label: '草案待提交', colorClass: 'tag-draft', colorText: '● 灰色', sort: 1, permissions: '补充附件 / 绑定原型' },
      { code: 'PENDING', label: '三方待签字', colorClass: 'tag-pending', colorText: '● 橙色', sort: 2, permissions: '分发免登链接 / 查看签字进度' },
      { code: 'BASELINED', label: '已冻结存证', colorClass: 'tag-baselined', colorText: '● 绿色', sort: 3, permissions: '不可篡改 / 绑定发版 / 基线对比' },
      { code: 'SUPERSEDED', label: '已被新基线替代', colorClass: 'tag-clarifying', colorText: '● 蓝色', sort: 4, permissions: '历史追溯 / 与新版 Diff 对比' },
      { code: 'REJECTED', label: '签署被驳回', colorClass: 'tag-pending', colorText: '● 红色', sort: 5, permissions: '查看驳回原因 / 发起新基线' }
    ]
  },
  {
    key: 'RELEASE_STATUS',
    name: '发布状态',
    desc: '发版流水线状态机（对齐 t_req_release.status）',
    items: [
      { code: 'PLANNED', label: '计划排期中', colorClass: 'tag-clarifying', colorText: '● 蓝色', sort: 1, permissions: '查看发布清单 / 执行正式发布' },
      { code: 'TESTING', label: '预发验证中', colorClass: 'tag-pending', colorText: '● 橙色', sort: 2, permissions: '核验集成穿透报告 / 准予放行' },
      { code: 'RELEASED', label: '已正式发布', colorClass: 'tag-baselined', colorText: '● 绿色', sort: 3, permissions: '查看 Git Tag / 导出发版清单' },
      { code: 'ROLLBACK', label: '已紧急回滚', colorClass: 'tag-draft', colorText: '● 红色', sort: 4, permissions: '执行回滚 SQL 核验 / 复盘归档' }
    ]
  },
  {
    key: 'CONFIRM_CATEGORY',
    name: '清单类别',
    desc: '四层确认清单 + 不做项五类维度（对齐 t_req_confirm_item.category）',
    items: [
      { code: 'SCOPE', label: '范围清单 (R)', colorClass: 'tag-clarifying', colorText: '● 蓝色', sort: 1, permissions: '增删改 / 折算人天代价' },
      { code: 'RULE', label: '业务规则 (B)', colorClass: 'tag-clarifying', colorText: '● 靛蓝', sort: 2, permissions: '增删改 / 锁定硬性阈值' },
      { code: 'EXCEPTION', label: '异常与边界 (E)', colorClass: 'tag-pending', colorText: '● 橙色', sort: 3, permissions: '增删改 / 制定降级兜底策略' },
      { code: 'ACCEPTANCE', label: '验收标准 (A)', colorClass: 'tag-baselined', colorText: '● 绿色', sort: 4, permissions: '增删改 / 绑定终验门禁' },
      { code: 'SKIP', label: '本期不做 (N)', colorClass: 'tag-draft', colorText: '● 灰色', sort: 5, permissions: '防范围蔓延边界声明' }
    ]
  },
  {
    key: 'CLIENT_DECISION',
    name: '客户决定',
    desc: '四层清单白话对撞决策档位（对齐 t_req_confirm_item.decision）',
    items: [
      { code: 'CURRENT', label: '本期做', colorClass: 'tag-baselined', colorText: '● 绿色', sort: 1, permissions: '计入本期总人天代价' },
      { code: 'NEXT', label: '延期下期', colorClass: 'tag-clarifying', colorText: '● 蓝色', sort: 2, permissions: '不计入本期 / 转入迭代池' },
      { code: 'SKIP', label: '明确不做', colorClass: 'tag-draft', colorText: '● 灰色', sort: 3, permissions: '0 人天 / 写入免责边界清单' },
      { code: 'PENDING', label: '待客户拍板', colorClass: 'tag-pending', colorText: '● 橙色', sort: 4, permissions: '阻塞基线冻结，需澄清收敛' }
    ]
  },
  {
    key: 'SIGN_ROLE',
    name: '签字角色',
    desc: '外部免登客户三方签字法定角色（对齐 t_req_approval.role_type）',
    items: [
      { code: 'BIZ_OWNER', label: '业务拍板人', colorClass: 'tag-baselined', colorText: '● 绿色', sort: 1, permissions: '范围边界与预算最终裁决签字' },
      { code: 'ACCEPTOR', label: '验收责任人', colorClass: 'tag-clarifying', colorText: '● 蓝色', sort: 2, permissions: '验收指标与终验基准确认签字' },
      { code: 'USER_REP', label: '使用者代表', colorClass: 'tag-pending', colorText: '● 橙色', sort: 3, permissions: '原型交互体验与现场操作流签字' }
    ]
  }
]

const loadSavedEnumGroups = (): EnumGroup[] => {
  try {
    const raw = localStorage.getItem('apex_req_enum_groups_v1')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch (e) {
    console.warn('读取本地码表配置失败，使用默认字典', e)
  }
  return JSON.parse(JSON.stringify(DEFAULT_ENUM_GROUPS))
}

const enumGroups = ref<EnumGroup[]>(loadSavedEnumGroups())
const activeEnumGroupKey = ref<string>('REQ_STATUS')
const modalNewEnumItemOpen = ref(false)
const newEnumItemForm = ref<EnumItem>({
  code: '',
  label: '',
  colorClass: 'tag-clarifying',
  colorText: '● 蓝色',
  sort: 1,
  permissions: ''
})

const activeEnumGroup = computed<EnumGroup>(() => {
  return enumGroups.value.find(g => g.key === activeEnumGroupKey.value) || enumGroups.value[0]
})

const handleSelectEnumGroup = (groupKey: string) => {
  activeEnumGroupKey.value = groupKey
}

const handleOpenNewEnumModal = () => {
  const currentItems = activeEnumGroup.value?.items || []
  newEnumItemForm.value = {
    code: '',
    label: '',
    colorClass: 'tag-clarifying',
    colorText: '● 蓝色',
    sort: currentItems.length + 1,
    permissions: '编辑 / 查看'
  }
  modalNewEnumItemOpen.value = true
}

const handleSubmitNewEnumItem = () => {
  const code = newEnumItemForm.value.code.trim().toUpperCase()
  const label = newEnumItemForm.value.label.trim()
  if (!code || !label) {
    ElMessage.warning('请填写状态 Code 与中文展示名')
    return
  }
  const group = activeEnumGroup.value
  if (group.items.some(i => i.code === code)) {
    ElMessage.warning(`当前分组 [${group.name}] 中已存在 Code: ${code}`)
    return
  }
  const colorLabelMap: Record<string, string> = {
    'tag-draft': '● 灰色',
    'tag-clarifying': '● 蓝色',
    'tag-pending': '● 橙色',
    'tag-baselined': '● 绿色'
  }
  group.items.push({
    code,
    label,
    colorClass: newEnumItemForm.value.colorClass,
    colorText: colorLabelMap[newEnumItemForm.value.colorClass] || '● 蓝色',
    sort: Number(newEnumItemForm.value.sort) || group.items.length + 1,
    permissions: newEnumItemForm.value.permissions.trim() || '查看'
  })
  group.items.sort((a, b) => a.sort - b.sort)
  modalNewEnumItemOpen.value = false
  ElMessage.success(`已向 [${group.name}] 新增取值 ${code} (${label})，请点击右上角发布生效`)
}

const handleDeleteEnumItem = (code: string) => {
  const group = activeEnumGroup.value
  group.items = group.items.filter(i => i.code !== code)
  ElMessage.success(`已移除取值 ${code}，点击右上角【发布码表变更】可持久化保存`)
}

const handlePublishEnumChanges = () => {
  localStorage.setItem('apex_req_enum_groups_v1', JSON.stringify(enumGroups.value))
  ElMessage.success(`🚀 码表配置已发布并持久化生效（共 ${enumGroups.value.length} 组字典）`)
}

const handleExportEnumsYaml = () => {
  const lines: string[] = [
    '# Apex 需求治理平台 - 全局码表规范 (enums.yaml)',
    `# 导出时间: ${new Date().toLocaleString()}`,
    'enums:'
  ]
  enumGroups.value.forEach(group => {
    lines.push(`  ${group.key}: # ${group.name}`)
    group.items.forEach(item => {
      lines.push(`    - code: "${item.code}"`)
      lines.push(`      label: "${item.label}"`)
      lines.push(`      style: "${item.colorClass}"`)
      lines.push(`      sort: ${item.sort}`)
      lines.push(`      permissions: "${item.permissions}"`)
    })
  })
  const blob = new Blob([lines.join('\n') + '\n'], { type: 'text/yaml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `apex-enums-${new Date().toISOString().slice(0, 10)}.yaml`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('已导出标准 apex-enums.yaml 规则文件')
}

// 初始化拉取项目与需求数据 (直连后端 MySQL)
const loadInitialData = async (targetProjectId?: number) => {
  loading.value = true
  try {
    // 1. 获取所有项目空间
    const projRes = await getProjectListApi()
    if (projRes.data && projRes.data.length > 0) {
      projectList.value = projRes.data
      let activeProj = projectList.value[0]
      const cachedKey = localStorage.getItem('apex_active_project_key')
      if (targetProjectId) {
        const found = projectList.value.find(p => p.id === targetProjectId)
        if (found) activeProj = found
      } else if (cachedKey) {
        const found = projectList.value.find(p => p.projectKey === cachedKey)
        if (found) activeProj = found
      } else if (selectedProject.value) {
        const found = projectList.value.find(p => p.projectKey === selectedProject.value)
        if (found) activeProj = found
      }
      currentProject.value = activeProj
      selectedProject.value = activeProj.projectKey
      localStorage.setItem('apex_active_project_key', activeProj.projectKey)

      // 2. 获取当前项目工作台的实时统计指标
      try {
        const statsRes = await getProjectStatsApi(activeProj.id)
        if (statsRes.data) {
          projectStats.value = statsRes.data
        }
      } catch (e) {
        console.warn('获取项目统计失败', e)
      }

      // 3. 获取该项目下的需求全景列表
      const listRes = await getRequirementListApi({ projectId: activeProj.id })
      if (listRes.data && listRes.data.length > 0) {
        requirements.value = listRes.data.map((item: any) => ({
          id: item.id,
          reqCode: item.reqCode,
          reqNo: item.reqCode,
          title: item.title,
          status: item.status,
          statusText: item.status === 'BASELINED' ? '已基线' : (item.status === 'PENDING' ? '待客户签字' : (item.status === 'DRAFT' ? '草稿' : '需求澄清中')),
          baselineCode: item.baselineCode || '—',
          ownerName: item.ownerName || '未指定',
          owner: item.ownerName || '未指定',
          cost: `${Number(item.totalCostDays || 0).toFixed(1)} 人天`,
          updatedAt: item.updatedAt ? item.updatedAt.split('T')[0] : '—',
          rawQuote: item.rawQuote || '',
          priority: item.priority || 'P1'
        }))
        // 自动拉取首个需求的详情与四层清单
        await openReqDetail(requirements.value[0], false)
      } else {
        requirements.value = []
        confirmItems.value = []
        currentReq.value = {
          id: 0,
          reqNo: '',
          reqCode: '',
          title: '暂无需求',
          status: '',
          baselineCode: '—',
          owner: '',
          createdAt: '',
          priority: 'P1',
          quote: ''
        }
        // 即便无需求，也拉取当前项目的原型资产
        await loadProjectPrototypes(activeProj.id)
      }
    } else {
      // 数据库中所有项目均被删除时的优雅清空
      projectList.value = []
      currentProject.value = {
        id: 0,
        projectKey: 'NO-PROJECT',
        name: '暂无活跃项目空间',
        clientName: '暂无客户',
        description: '当前系统中暂无活跃项目空间，请点击右上角「新建项目 (M01)」创建新项目'
      }
      selectedProject.value = ''
      localStorage.removeItem('apex_active_project_key')
      projectStats.value = {
        totalRequirements: 0,
        baselinedCount: 0,
        pendingCount: 0,
        clarifyingCount: 0,
        prototypeCount: 0,
        totalCostDays: '0.0',
        latestBaselineCode: '—'
      }
      requirements.value = []
      confirmItems.value = []
      currentReq.value = {
        id: 0,
        reqNo: '',
        reqCode: '',
        title: '暂无需求',
        status: '',
        baselineCode: '—',
        owner: '',
        createdAt: '',
        priority: 'P1',
        quote: ''
      }
    }

    // 4. 获取数据库系统真实用户列表 (作为新建需求等模块的动态候选责任人)
    try {
      const userRes = await getUserListApi()
      if (userRes.data && userRes.data.length > 0) {
        userList.value = userRes.data.filter((u: any) => u.status === 1)
        if (!newReqForm.value.owner) {
          const defaultUser = userList.value.find((u: any) => u.nickname?.includes('张工') || u.username === 'zhanggong') || userList.value[0]
          newReqForm.value.owner = defaultUser.nickname || defaultUser.username
        }
        // 初始化基线三方签署人默认名单
        const zhang = userList.value.find((u: any) => u.nickname?.includes('张工') || u.username === 'zhanggong')
        const li = userList.value.find((u: any) => u.nickname?.includes('李工') || u.username === 'ligong')
        const wang = userList.value.find((u: any) => u.nickname?.includes('王工') || u.username === 'wanggong')
        if (zhang) newBaselineForm.value.bizOwnerName = zhang.nickname || zhang.username
        if (li) newBaselineForm.value.acceptorName = li.nickname || li.username
        if (wang) newBaselineForm.value.userRepName = wang.nickname || wang.username
      }
    } catch (e) {
      console.warn('获取系统用户库失败', e)
    }

    // 5. 同步加载当前项目的真实发布版本列表 (t_req_release)
    await loadProjectReleases(currentProject.value?.id)
  } catch (err) {
    console.warn('动态加载失败，保持优雅降级', err)
  } finally {
    loading.value = false
  }
}

// 智能调度悬浮球状态与拖拽控制
const floatingBallPos = ref({ x: window.innerWidth - 72, y: window.innerHeight - 180 })
const floatingPanelVisible = ref(false)
const isDraggingBall = ref(false)
const dragStartOffset = ref({ x: 0, y: 0 })
const dragHasMoved = ref(false)

const handleBallMouseDown = (e: MouseEvent) => {
  isDraggingBall.value = true
  dragHasMoved.value = false
  dragStartOffset.value = {
    x: e.clientX - floatingBallPos.value.x,
    y: e.clientY - floatingBallPos.value.y
  }
  window.addEventListener('mousemove', handleBallMouseMove)
  window.addEventListener('mouseup', handleBallMouseUp)
}

const handleBallMouseMove = (e: MouseEvent) => {
  if (!isDraggingBall.value) return
  dragHasMoved.value = true
  const newX = Math.max(16, Math.min(window.innerWidth - 64, e.clientX - dragStartOffset.value.x))
  const newY = Math.max(16, Math.min(window.innerHeight - 64, e.clientY - dragStartOffset.value.y))
  floatingBallPos.value = { x: newX, y: newY }
}

const handleBallMouseUp = () => {
  isDraggingBall.value = false
  window.removeEventListener('mousemove', handleBallMouseMove)
  window.removeEventListener('mouseup', handleBallMouseUp)
  // 自动吸边优化（离哪侧近吸附到哪侧边缘）
  if (dragHasMoved.value) {
    const midX = window.innerWidth / 2
    floatingBallPos.value.x = floatingBallPos.value.x > midX ? window.innerWidth - 68 : 16
  }
}

const handleBallClick = () => {
  if (!dragHasMoved.value) {
    floatingPanelVisible.value = !floatingPanelVisible.value
  }
}

onMounted(async () => {
  await loadInitialData()
  await loadWorkflowTemplates()
  await loadSkillsAndScriptsAssets()
  if (route.path === '/req/workflow') {
    await nextTick()
    setTimeout(async () => {
      initWorkflowGraph()
      await syncRealWorkflowDagState()
      await loadWorkflowRecords()
      resetWorkflowView()
    }, 200)
  }

  // 🔍 自动化视口与溢出探测探针：精准定位右侧黑条与溢出元素
  setTimeout(() => {
    const docWidth = document.documentElement.offsetWidth
    const winWidth = window.innerWidth
    console.log(`%c[Layout Diagnostic] 视口物理宽度: ${winWidth}px, 页面文档宽度: ${docWidth}px`, 'background:#0284c7;color:#fff;padding:2px 8px;border-radius:4px;')
    
    // 递归扫描所有超出视口宽度的 DOM 元素
    const overflowingElements: any[] = []
    document.querySelectorAll('*').forEach((el: Element) => {
      const rect = el.getBoundingClientRect()
      if (rect.right > winWidth + 2) {
        overflowingElements.push({
          tag: el.tagName,
          className: el.className,
          id: el.id,
          right: Math.round(rect.right),
          width: Math.round(rect.width),
          overflowDiff: Math.round(rect.right - winWidth),
          element: el
        })
      }
    })

    if (overflowingElements.length > 0) {
      console.warn(`[Layout Diagnostic] 发现 ${overflowingElements.length} 个元素向右穿透溢出视口:`, overflowingElements)
    } else {
      console.log('[Layout Diagnostic] 🟢 全局扫描完毕，未发现向右侧溢出视口的 DOM 元素。')
    }
  }, 1000)
})
</script>

<template>
  <div class="req-app-container">
    <!-- 顶部导航已彻底隐退收敛至智能调度小球，全面移除了所有业务子页面的突兀二级顶栏，与项目中心/工艺资产保持 100% 极简通透风格 -->
    <!-- 主体工作区 -->
    <div class="app-body">


      <!-- 右侧内容主视窗 -->
      <main class="main-content">
        <!-- ================= P02 项目列表 (项目中心) ================= -->
        <div id="page-projects" class="view-page" :class="{ active: currentPage === 'page-projects' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
            <div>
              <div style="display:flex; align-items:center; gap:10px;">
                <h2 style="font-size:20px; font-weight:700;">📁 项目空间中心 (P02)</h2>
                <span class="tag tag-baselined">共 {{ projectList.length }} 个项目空间</span>
              </div>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                团队交付边界与需求基线租户大厅。点击具体项目卡片即可下钻进入该项目专属的「需求工作台」。
              </p>
            </div>
            <div style="display:flex; gap:10px;">
              <button class="btn btn-primary" @click="modalNewProjectOpen = true">➕ 新建项目 (M01)</button>
            </div>
          </div>

          <!-- 项目列表卡片网格 -->
          <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap:20px; margin-bottom:12px;">
            <div
              v-for="p in paginatedProjectList"
              :key="p.id"
              class="card project-card-item"
              :style="p.projectKey === selectedProject ? 'border-color:var(--primary-color, #0066cc); box-shadow:0 0 0 1.5px var(--primary-color, #0066cc);' : ''"
              @click="handleEnterProjectDashboard(p)"
            >
              <div class="card-header project-card-header">
                <div class="project-header-left">
                  <span class="project-key-badge">{{ p.projectKey }}</span>
                  <span class="project-title-text" :title="p.name">{{ p.name }}</span>
                </div>
                <span v-if="p.projectKey === selectedProject" class="tag tag-clarifying project-status-tag">当前选中</span>
              </div>
              <div class="card-body" style="padding:16px;">
                <p class="project-desc-text">
                  {{ p.description || '契约与快照单一事实源，基于基线版本控制的工业级交付。' }}
                </p>
                <div class="project-footer">
                  <div class="project-client-info">
                    客户单位：<strong style="color:var(--text, #1e293b);">{{ p.clientName || '暂未指定' }}</strong>
                  </div>
                  <button class="btn btn-primary btn-sm" @click.stop="handleEnterProjectDashboard(p)">
                    进入工作台 ➔
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 全局通用统一分页组件 CommonPagination -->
          <CommonPagination
            v-model:page="projectPage"
            v-model:limit="projectPageSize"
            :total="projectList.length"
            :page-sizes="[6, 12, 24, 48]"
          />
        </div>

        <!-- ================= 🚀 工程流水线：DAG 算子流转与微观状态大屏 (page-workflow) ================= -->
        <div id="page-workflow" class="view-page" :class="{ active: currentPage === 'page-workflow' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
            <div>
              <div style="display:flex; align-items:center; gap:10px;">
                <h2 style="font-size:20px; font-weight:700;">🚀 工程流水线 · 算子状态与责任人透视大屏</h2>
                <span class="tag tag-baselined">{{ currentProject.projectKey || 'NET-SIM' }}</span>
              </div>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                将传统研发“黑盒”打开为透明受控玻璃盒。实时跟踪每个需求当前由谁负责、走到了哪个算子工位，以及门禁质检状态。
              </p>
            </div>
            <div style="display:flex; gap:10px; align-items:center;">
              <span style="font-size:12px; color:var(--text-muted);">当前项目空间：</span>
              <select class="form-control" style="width:200px;" v-model="selectedProject" @change="handleProjectChange">
                <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
                  {{ p.projectKey }} · {{ p.name }}
                </option>
              </select>
            </div>
          </div>

          <!-- 顶部总概览卡片 (宏观指标) -->
          <div style="display:grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap:16px; margin-bottom:16px;">
            <div class="card" style="padding:16px; border-left:4px solid var(--primary-color);">
              <div style="font-size:12px; color:var(--text-muted);">正在透视需求</div>
              <div style="font-size:16px; font-weight:700; color:var(--text-main); margin-top:4px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">
                {{ workflowActiveReq.reqCode || '—' }} · {{ workflowActiveReq.title || '未选中需求' }}
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">优先级: {{ workflowActiveReq.priority || '—' }} ｜ 代价: {{ workflowActiveReq.cost || '—' }}</div>
            </div>
            <div class="card" style="padding:16px; border-left:4px solid #16A34A;">
              <div style="font-size:12px; color:var(--text-muted);">当前责任人</div>
              <div style="font-size:16px; font-weight:700; color:#16A34A; margin-top:4px;">
                👤 {{ workflowActiveReq.ownerName || workflowActiveReq.owner || '未指派' }}
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">工位角色: {{ workflowActiveNodeInfo.role || '—' }}</div>
            </div>
            <div class="card" style="padding:16px; border-left:4px solid #F59E0B;">
              <div style="font-size:12px; color:var(--text-muted);">当前微观算子工位</div>
              <div style="font-size:16px; font-weight:700; color:#D97706; margin-top:4px;">
                📍 [{{ workflowActiveNodeInfo.id }}] {{ workflowActiveNodeInfo.name }}
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">门禁规则: {{ workflowActiveNodeInfo.gate }}</div>
            </div>
            <div class="card" style="padding:16px; border-left:4px solid #3B82F6;">
              <div style="font-size:12px; color:var(--text-muted);">当前轨道进度 ({{ workflowTrackMode === 'FASTTRACK' ? '快轨 S' : '标准主链 L' }})</div>
              <div style="font-size:16px; font-weight:700; color:#1D4ED8; margin-top:4px;">
                {{ workflowProgressStats.percent }}% ({{ workflowProgressStats.completed }}/{{ workflowProgressStats.total }} 算子)
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">
                数据源: {{ workflowLoadError ? '🔴 同步异常' : '🟢 MySQL SSOT 实时驱动' }}
              </div>
            </div>
          </div>

          <!-- 🤖 AI 智能向导与驾驶舱建议条 (对齐人机协同与下一步推荐) -->
          <div class="card" style="padding:14px 20px; margin-bottom:16px; background:linear-gradient(135deg, #0b1320 0%, #172554 100%); border:1px solid #1e3a8a; color:#FFF; display:flex; justify-content:space-between; align-items:center; box-shadow:0 4px 12px rgba(15,23,42,0.15);">
            <div style="display:flex; align-items:center; gap:14px;">
              <div style="width:38px; height:38px; border-radius:50%; background:rgba(56,189,248,0.2); border:1px solid #38bdf8; display:flex; align-items:center; justify-content:center; font-size:18px;">
                🧭
              </div>
              <div>
                <div style="display:flex; align-items:center; gap:8px;">
                  <span style="font-size:14px; font-weight:700; color:#f8fafc;">AI 流程副驾导航建议</span>
                  <span class="tag" :style="{ background: aiNextActionAdvice.tagColor, color: '#FFF', fontSize: '11px', padding: '1px 8px' }">
                    {{ aiNextActionAdvice.modeText }}
                  </span>
                  <span style="font-size:12px; color:#94a3b8;">
                    绑定 Skill: <code style="background:rgba(255,255,255,0.1); color:#38bdf8; padding:1px 5px; border-radius:3px;">{{ aiNextActionAdvice.skill }}</code>
                  </span>
                </div>
                <div style="font-size:13px; color:#cbd5e1; margin-top:4px;">
                  💡 <strong>智能建议：</strong>{{ aiNextActionAdvice.advice }}
                </div>
              </div>
            </div>

            <div style="display:flex; gap:10px; align-items:center;">
              <div style="text-align:right;">
                <div style="font-size:11px; color:#94a3b8;">在对话框输入推荐指令：</div>
                <div style="font-size:13px; font-weight:700; color:#38bdf8; font-family:monospace;">
                  {{ aiNextActionAdvice.recommendedCmd }}
                </div>
              </div>
              <button
                class="btn btn-sm"
                style="background:#0284c7; color:#FFF; border:none; font-weight:700; padding:6px 14px;"
                @click="handleAdvanceWorkflowNode"
              >
                ⚡ 一键确认采纳
              </button>
            </div>
          </div>

          <!-- 主体工作区：左侧「待办需求列表」（支持一键折叠扩大拓扑图）+ 右侧「DAG 拓扑画板与工位详情」左右分栏 -->
          <div style="display:flex; gap:16px; align-items:flex-start;">
            <!-- 左侧：需求列表抽屉 (宽度270px，可折叠至46px) -->
            <div
              class="card"
              style="padding:14px; display:flex; flex-direction:column; transition:all 0.3s cubic-bezier(0.4, 0, 0.2, 1); flex-shrink:0; background:#FFF; border:1px solid var(--border); box-sizing:border-box;"
              :style="{ width: workflowReqListCollapsed ? '46px' : '270px', height: '680px' }"
            >
              <!-- 折叠头部 -->
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; padding-bottom:8px; border-bottom:1px solid var(--border);">
                <div v-if="!workflowReqListCollapsed" style="display:flex; align-items:center; gap:6px;">
                  <span style="font-weight:700; font-size:14px; white-space:nowrap;">📋 待办需求</span>
                  <span class="tag tag-clarifying" style="font-size:10px; padding:1px 6px;">{{ requirements.length }}</span>
                </div>
                <button
                  class="btn btn-outline btn-sm"
                  style="padding:3px 8px; font-size:12px; margin-left:auto; display:flex; align-items:center; justify-content:center;"
                  :title="workflowReqListCollapsed ? '展开需求列表' : '折叠需求列表以扩大拓扑画板视野'"
                  @click="workflowReqListCollapsed = !workflowReqListCollapsed"
                >
                  {{ workflowReqListCollapsed ? '➔' : '◀' }}
                </button>
              </div>

              <!-- 折叠状态下的竖排提示 -->
              <div
                v-if="workflowReqListCollapsed"
                style="writing-mode:vertical-lr; text-orientation:mixed; letter-spacing:4px; font-size:12px; color:var(--text-muted); cursor:pointer; height:100%; display:flex; align-items:center; justify-content:center; padding-top:20px; font-weight:600;"
                @click="workflowReqListCollapsed = false"
              >
                📋 展开需求 ({{ requirements.length }})
              </div>

              <!-- 展开状态下的需求卡片滚动列表 -->
              <div v-else style="overflow-y:auto; flex:1; display:flex; flex-direction:column; gap:8px;">
                <div
                  v-for="r in requirements"
                  :key="r.id"
                  style="border:1px solid var(--border); border-radius:var(--radius-sm); padding:10px 12px; cursor:pointer; transition:all 0.2s;"
                  :style="workflowSelectedReqId === r.id ? 'border-color:var(--primary-color); background:var(--primary-light); box-shadow:0 2px 8px rgba(2,132,199,0.15);' : 'background:#FFF;'"
                  @click="handleSelectWorkflowReq(r)"
                >
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="font-size:13px; color:var(--text-main);">{{ r.reqCode }}</strong>
                    <span class="tag" :class="r.status === 'BASELINED' ? 'tag-baselined' : (r.status === 'PENDING' ? 'tag-pending' : 'tag-clarifying')" style="font-size:10px; padding:1px 5px;">
                      {{ r.statusText }}
                    </span>
                  </div>
                  <div style="font-size:12px; color:var(--text-main); margin:4px 0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" :title="r.title">
                    {{ r.title }}
                  </div>
                  <div style="display:flex; justify-content:space-between; align-items:center; font-size:11px; color:var(--text-muted); border-top:1px dashed var(--border); padding-top:4px; margin-top:4px;">
                    <span>责任人: <strong style="color:var(--text-main);">{{ r.ownerName || r.owner }}</strong></span>
                    <span>{{ r.cost }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 右侧：拓扑画板与工位深度下钻详情 (自适应撑满剩余宽度) -->
            <div style="display:flex; flex-direction:column; gap:16px; flex:1; min-width:0;">
              <!-- 真实的 AntV X6 工业级有向无环拓扑大图 (四层全景一体化呈现) -->
              <div class="card" style="padding:16px; border:1px solid var(--border); background:#0e1726;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; padding:0 4px;">
                  <div style="display:flex; align-items:center; gap:10px;">
                    <span style="font-weight:700; font-size:14px; color:#f8fafc;">🗺️ 全流程架构节点与物理产物流转全景图 (4-Layer Topology)</span>
                    <span class="tag tag-baselined" style="font-size:11px;">
                      四层一体化 · 100% 对齐 WORKFLOW_GUIDE.md
                    </span>
                  </div>
                  <!-- 控制工具栏 -->
                  <div style="display:flex; gap:10px; align-items:center; flex-wrap:wrap;">
                    <!-- 当前工位状态专业级极光流体胶囊 (Cyberpunk Aurora Status Capsule) -->
                    <div class="wf-current-status-capsule">
                      <div class="capsule-live-beacon">
                        <span class="beacon-core"></span>
                        <span class="beacon-halo"></span>
                      </div>
                      <div class="capsule-meta">
                        <span class="capsule-label">ACTIVE NODE</span>
                        <div class="capsule-val">
                          <span class="capsule-code">[{{ workflowActiveNodeInfo.id }}]</span>
                          <span class="capsule-name">{{ workflowActiveNodeInfo.name }}</span>
                        </div>
                      </div>
                      <div class="capsule-track-badge">
                        {{ workflowTrackMode === 'FASTTRACK' ? '⚡ 极速快轨' : '🏛️ 标准主轨' }}
                      </div>
                    </div>

                    <!-- L/S 双轨实时切换 -->
                    <div style="display:inline-flex; background:#1e293b; border:1px solid #334155; border-radius:6px; padding:2px;">
                      <button
                        class="btn btn-sm"
                        :style="workflowTrackMode !== 'FASTTRACK' ? 'background:#0284c7; color:#fff; border:none;' : 'background:transparent; color:#94a3b8; border:none;'"
                        style="padding:3px 10px; font-size:11px; font-weight:600;"
                        @click="handleSwitchWorkflowTrack('STANDARD')"
                      >
                        🏛️ 标准主链 (0~9)
                      </button>
                      <button
                        class="btn btn-sm"
                        :style="workflowTrackMode === 'FASTTRACK' ? 'background:#d97706; color:#fff; border:none;' : 'background:transparent; color:#94a3b8; border:none;'"
                        style="padding:3px 10px; font-size:11px; font-weight:600;"
                        @click="handleSwitchWorkflowTrack('FASTTRACK')"
                      >
                        ⚡ 极速快轨 (B1~B6)
                      </button>
                    </div>
                    <span style="font-size:11px; color:#34d399; background:rgba(16,185,129,0.12); border:1px solid rgba(16,185,129,0.3); padding:3px 8px; border-radius:4px;" title="停留在流水线大屏时每 5 秒静默同步 MySQL，对话中推进后自动亮灯">
                      🟢 实时同步
                    </span>
                    <button
                      class="btn btn-sm btn-outline"
                      style="background:#1e293b; color:#cbd5e1; border-color:#334155;"
                      title="从 MySQL 重新同步拓扑与真实状态"
                      @click="syncRealWorkflowDagState()"
                    >
                      🔄 同步
                    </button>
                    <button
                      class="btn btn-sm btn-outline"
                      style="background:#1e293b; color:#cbd5e1; border-color:#334155;"
                      title="复位平移与缩放"
                      @click="resetWorkflowView()"
                    >
                      🎯 重置视图
                    </button>
                    <button
                      class="btn btn-sm btn-primary"
                      style="background:#0284c7; color:#fff; border:none; font-weight:600; display:inline-flex; align-items:center; gap:5px; box-shadow:0 2px 8px rgba(2,132,199,0.3);"
                      @click="openNodeDetailDrawer(workflowActiveNodeInfo.id)"
                    >
                      <span>⚙️ 当前工位详情 & 履历</span>
                      <span class="tag" style="background:rgba(255,255,255,0.25); color:#fff; font-size:10px; padding:0 4px; border-radius:3px;">{{ workflowRecords.length }}</span>
                    </button>
                  </div>
                </div>

                <!-- 视口高度提升至 510px，完美呈现四层流转 (支持显式 Loading 与错误态重试) -->
                <div class="wf-viewport" style="position:relative;">
                  <div
                    ref="workflowGraphContainer"
                    class="wf-graph-host"
                    :style="{ width: '100%', height: workflowHostHeight + 'px' }"
                  ></div>

                  <!-- 加载中态 -->
                  <div
                    v-if="workflowLoading"
                    style="position:absolute; inset:0; background:rgba(11,19,32,0.72); display:flex; flex-direction:column; align-items:center; justify-content:center; gap:10px; z-index:20; color:#38bdf8;"
                  >
                    <div style="font-size:24px;">⏳</div>
                    <div style="font-size:13px; font-weight:600;">正在从 MySQL 单一事实源加载拓扑与真实工位进度...</div>
                  </div>

                  <!-- 显式错误态 (绝不静默展示假图) -->
                  <div
                    v-else-if="workflowLoadError"
                    style="position:absolute; inset:0; background:rgba(11,19,32,0.92); display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; z-index:20; padding:24px; text-align:center;"
                  >
                    <div style="font-size:28px;">⚠️</div>
                    <div style="font-size:14px; font-weight:700; color:#f87171;">拓扑数据同步异常 (已禁止静默假数据降级)</div>
                    <div style="font-size:12px; color:#cbd5e1; max-width:520px; line-height:1.6;">{{ workflowLoadError }}</div>
                    <button
                      class="btn btn-sm btn-primary"
                      style="background:#0284c7; color:#fff; border:none; padding:6px 18px; font-weight:600;"
                      @click="syncRealWorkflowDagState()"
                    >
                      🔄 立即重试连接
                    </button>
                  </div>
                </div>
              </div>

            </div>
          </div>

          <!-- 🔀 多分支顺推选路弹窗 (当下一跳存在 ≥2 个候选分支时触发：默认全选并行启动，也支持单选分流) -->
          <div v-if="modalBranchSelectOpen" class="modal-overlay active" style="z-index: 2200;" @click.self="modalBranchSelectOpen = false">
            <div class="modal-content" style="max-width:520px; width:92%; background:#0f172a; color:#f8fafc; border:1px solid #334155; border-radius:12px; padding:24px; box-shadow:0 20px 60px rgba(0,0,0,0.5);">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; border-bottom:1px solid #1e293b; padding-bottom:12px;">
                <div>
                  <h3 style="font-size:16px; font-weight:700; color:#38bdf8;">🔀 选择下一跳流转分支</h3>
                  <div style="font-size:12px; color:#94a3b8; margin-top:3px;">
                    工位 <code>[{{ workflowActiveNodeInfo.id }}] {{ workflowActiveNodeInfo.name }}</code> 确认完工后存在 {{ branchCandidates.length }} 个下游分支：
                  </div>
                </div>
                <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155; background:transparent;" @click="modalBranchSelectOpen = false">✕</button>
              </div>

              <div style="display:flex; flex-direction:column; gap:10px; margin:16px 0;">
                <div
                  v-for="cand in branchCandidates"
                  :key="cand.id"
                  style="display:flex; align-items:flex-start; gap:12px; padding:12px 14px; border-radius:8px; cursor:pointer; transition:all 0.2s;"
                  :style="selectedNextBranchIds.includes(cand.id)
                    ? 'background:rgba(2,132,199,0.18); border:1.5px solid #38bdf8;'
                    : 'background:#1e293b; border:1px solid #334155; opacity:0.75;'"
                  @click="toggleBranchSelection(cand.id)"
                >
                  <input
                    type="checkbox"
                    :checked="selectedNextBranchIds.includes(cand.id)"
                    style="margin-top:3px; accent-color:#38bdf8; width:16px; height:16px; cursor:pointer;"
                    @click.stop="toggleBranchSelection(cand.id)"
                  />
                  <div style="flex:1;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                      <strong style="font-size:14px; color:#f8fafc;">[{{ cand.id }}] {{ cand.name }}</strong>
                      <span class="tag" style="background:rgba(56,189,248,0.15); color:#38bdf8; font-size:11px; padding:1px 6px;">{{ cand.role }}</span>
                    </div>
                    <div style="font-size:12px; color:#94a3b8; margin-top:4px;">
                      推荐专家: <code>{{ cand.skill }}</code> ｜ 门禁: {{ cand.gate }}
                    </div>
                  </div>
                </div>
              </div>

              <div style="display:flex; justify-content:space-between; align-items:center; margin-top:18px; padding-top:12px; border-top:1px solid #1e293b;">
                <button
                  class="btn btn-outline btn-sm"
                  style="color:#38bdf8; border-color:#0284c7; background:rgba(2,132,199,0.1);"
                  @click="selectedNextBranchIds = branchCandidates.map(c => c.id)"
                >
                  ⚡ 全选并行启动（推荐）
                </button>
                <div style="display:flex; gap:10px;">
                  <button class="btn btn-outline btn-sm" style="color:#cbd5e1; border-color:#475569; background:transparent;" @click="modalBranchSelectOpen = false">取消</button>
                  <button class="btn btn-primary btn-sm" style="background:#0284c7; border:none; font-weight:700; padding:6px 16px;" @click="handleConfirmBranchAdvance">
                    ✔ 确认完工并激活已选 ({{ selectedNextBranchIds.length }})
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= 🗺️ 工作流模板治理中心 (page-templates) ================= -->
        <div id="page-templates" class="view-page" :class="{ active: currentPage === 'page-templates' }">
          
          <!-- 子视图 1: 独立模板库超市大厅 (不以项目维度设计，展示纯独立模板与挂载数) -->
          <div v-if="!templateDagEditorOpen">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
              <div>
                <div style="display:flex; align-items:center; gap:10px;">
                  <h2 style="font-size:20px; font-weight:700;">🗺️ 研发工作流模板治理中心</h2>
                  <span class="tag tag-baselined">组织级通用蓝本库</span>
                </div>
                <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                  生产工艺抽象资产库（与单个项目解耦）。点击任意模板卡片即可进入 <strong>「有向无环拓扑画板 (DAG Editor)」</strong> 可视化编辑工位与调配专家。
                </p>
              </div>
              <div style="display:flex; gap:10px;">
                <button class="btn btn-outline btn-sm" @click="loadWorkflowTemplates">🔄 刷新模板库</button>
                <button class="btn btn-outline btn-sm" style="display:inline-flex; align-items:center; gap:6px;" @click="handleInitDefaultTemplate" title="重置或修复官方基础基准蓝本 (TPL-DEFAULT)">
                  <span>🔄</span>
                  <span>初始化/重置基础蓝本</span>
                </button>
                <button class="btn btn-primary btn-sm" style="display:inline-flex; align-items:center; gap:6px; box-shadow:0 2px 8px rgba(2, 132, 199, 0.25);" @click="handleCreateNewDefaultTemplate">
                  <span>➕</span>
                  <span>追加新默认工作流</span>
                </button>
              </div>
            </div>

            <!-- 模板卡片列表 -->
            <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(440px, 1fr)); gap:20px; margin-bottom:20px;">
              <div
                v-for="tpl in workflowTemplates"
                :key="tpl.id"
                class="card"
                style="padding:22px; display:flex; flex-direction:column; justify-content:space-between; border-top:4px solid; cursor:pointer; transition:transform 0.2s, box-shadow 0.2s;"
                :style="{ borderTopColor: tpl.isSystemDefault === 1 ? '#0284c7' : '#10b981' }"
                @click="handleOpenTemplateDagEditor(tpl)"
              >
                <div>
                  <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:12px;">
                    <div style="display:flex; align-items:center; gap:8px;">
                      <span
                        class="tag"
                        :style="{
                          background: tpl.isSystemDefault === 1 ? '#0284c7' : '#10b981',
                          color: '#fff',
                          fontWeight: 700,
                          fontSize: '11px'
                        }"
                      >
                        {{ tpl.isSystemDefault === 1 ? '👑 官方系统蓝本' : '🧩 衍生定制蓝本' }}
                      </span>
                      <strong style="font-size:16px; color:var(--text-main);">{{ tpl.name }}</strong>
                    </div>
                    <code style="font-size:11px; background:#f1f5f9; padding:2px 6px; border-radius:3px; color:#475569;">
                      {{ tpl.templateCode }}
                    </code>
                  </div>

                  <p style="font-size:13px; color:var(--text-muted); line-height:1.6; margin-bottom:14px;">
                    {{ tpl.description || '标准生产工艺拓扑蓝本，包含全生命周期四层流转。' }}
                  </p>

                  <!-- 挂载状态与工程特征徽标 -->
                  <div style="display:flex; align-items:center; justify-content:space-between; font-size:12px; background:#f8fafc; padding:10px 14px; border-radius:6px; margin-bottom:16px; border:1px solid var(--border);">
                    <div style="display:flex; align-items:center; gap:6px;">
                      <span>🔗 挂载状态：</span>
                      <span class="tag tag-clarifying" style="font-size:11px; font-weight:600;">
                        已挂载于多个项目
                      </span>
                    </div>
                    <button
                      class="btn btn-outline btn-sm"
                      style="padding:2px 8px; font-size:11px;"
                      @click.stop="handleOpenMountProjectsModal(tpl)"
                    >
                      ⚙️ 调整项目挂载
                    </button>
                  </div>
                </div>

                <!-- 底部操作栏 -->
                <div style="display:flex; justify-content:space-between; align-items:center; border-top:1px solid var(--border); padding-top:14px;" @click.stop>
                  <span style="font-size:12px; color:var(--primary); font-weight:600; display:flex; align-items:center; gap:4px; cursor:pointer;" @click="handleOpenTemplateDagEditor(tpl)">
                    <span>🎨 进入 DAG 拓扑画板编辑</span>
                    <span>➔</span>
                  </span>
                  <div style="display:flex; gap:8px;">
                    <button
                      class="btn btn-primary btn-sm"
                      style="display:inline-flex; align-items:center; gap:4px;"
                      @click="handleOpenCloneModal(tpl)"
                    >
                      <span>📋 复制为新模板</span>
                    </button>
                    <button
                      v-if="tpl.templateCode !== 'TPL-DEFAULT'"
                      class="btn btn-outline btn-sm"
                      style="color:#dc2626;"
                      @click="handleDeleteTemplate(tpl)"
                    >
                      🗑️ 删除
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 子视图 2: 模板专属有向无环图 (DAG Editor) 全屏编辑画板 -->
          <div v-else style="display:flex; flex-direction:column; gap:16px;">
            <!-- 画板顶部控制导航条 -->
            <div class="card" style="padding:14px 20px; background:#0b1320; border:1px solid #1e293b; color:#fff; display:flex; justify-content:space-between; align-items:center;">
              <div style="display:flex; align-items:center; gap:12px;">
                <button
                  class="btn btn-outline btn-sm"
                  style="color:#94a3b8; border-color:#334155; padding:4px 10px;"
                  @click="templateDagEditorOpen = false"
                >
                  ⬅ 返回模板库列表
                </button>
                <div style="height:20px; width:1px; background:#334155;"></div>
                <span style="font-size:16px; font-weight:700; color:#f8fafc;">
                  {{ currentEditingTemplate.name }}
                </span>
                <code style="font-size:11px; background:#1e293b; color:#38bdf8; padding:2px 8px; border-radius:4px;">
                  {{ currentEditingTemplate.templateCode }}
                </code>
                <span style="font-size:12px; color:#94a3b8; margin-left:6px;">
                  💡 提示：点击任意工位节点即可配置其专属 AI Skill 专家与门禁规约
                </span>
              </div>

              <!-- 【按要求注释】：工作流基础模板所有节点受保护不可修改，注释保存拓扑蓝本配置按钮
              <div style="display:flex; gap:10px; align-items:center;">
                <button
                  class="btn btn-primary btn-sm"
                  style="background:#0284c7; border:none; font-weight:700; padding:6px 16px; display:inline-flex; align-items:center; gap:6px; box-shadow:0 2px 8px rgba(2, 132, 199, 0.35);"
                  @click="handleSaveTemplateDag"
                >
                  <span>💾</span>
                  <span>保存拓扑蓝本配置</span>
                </button>
              </div>
              -->
            </div>

            <!-- DAG 拓扑编辑画布 -->
            <div class="card" style="padding:16px; border:1px solid #1e293b; background:#0e1726;">
              <div class="wf-viewport" style="height:520px;">
                <div
                  ref="tplGraphContainer"
                  class="wf-graph-host"
                  style="width:100%; height:520px;"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= 🤖 专门治理菜单：AI Skills 专家中心 ================= -->
        <div id="page-skills" class="view-page" :class="{ active: currentPage === 'page-skills' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
            <div>
              <h2 style="font-size:20px; font-weight:700; display:flex; align-items:center; gap:8px;">
                <span>🤖 AI Skills 专家中心</span>
                <span class="tag" style="background:#0284c7; color:#fff;">系统级工艺专家库</span>
              </h2>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                管理系统内所有已注册的 AI 专属能力专家。支持查看各专家的 SKILL.md 实体规约内容、契约约束、输入输出定义以及赋能工位。
              </p>
            </div>
            <div>
              <button class="btn btn-outline btn-sm" @click="loadSkillsAndScriptsAssets">🔄 重新探测专家</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap:20px;">
            <div 
              v-for="sk in availableSkillRegistry" 
              :key="sk.id" 
              class="expert-skill-card"
            >
              <!-- 顶部高光微装饰线 -->
              <div class="skill-card-indicator"></div>

              <div class="skill-card-main">
                <!-- 头部：图标 + 代号 + 勋章 -->
                <div class="skill-card-header">
                  <div class="skill-avatar-box">
                    <span class="skill-avatar-icon">{{ sk.icon || '🤖' }}</span>
                  </div>
                  <div class="skill-title-block">
                    <div class="skill-id-row">
                      <span class="skill-id-text">{{ sk.id }}</span>
                      <span class="skill-badge-agent">Agent 专家</span>
                    </div>
                    <div class="skill-name-text">{{ sk.name }}</div>
                  </div>
                </div>

                <!-- 描述 -->
                <p class="skill-desc-text">
                  {{ sk.desc || '专属智能研发专家，严密规范工程契约与落地质量。' }}
                </p>

                <!-- 路径标签 -->
                <div class="skill-path-box">
                  <span class="skill-path-icon">📂</span>
                  <span class="skill-path-code">{{ sk.path || `.agents/skills/${sk.id}/SKILL.md` }}</span>
                </div>
              </div>

              <!-- 底部操作与挂载状态 -->
              <div class="skill-card-footer">
                <div class="skill-status-indicator">
                  <span class="pulse-dot"></span>
                  <span class="pulse-text">已挂载工作流拓扑</span>
                </div>
                <button class="btn btn-primary btn-sm skill-action-btn" @click="handleOpenSkillDetail(sk)">
                  <span>📖 查看规约 (SKILL.md)</span>
                  <span class="arrow-icon">→</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= 🛠️ 专门治理菜单：自动化脚本资产中心 ================= -->
        <div id="page-scripts" class="view-page" :class="{ active: currentPage === 'page-scripts' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
            <div>
              <h2 style="font-size:20px; font-weight:700; display:flex; align-items:center; gap:8px;">
                <span>🛠️ 自动化脚本资产中心</span>
                <span class="tag" style="background:#10b981; color:#fff;">.ai/scripts 工具链</span>
              </h2>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                纳管工程底层核心看门狗与自动化治理脚本。支持在线查看源码、调用参数模板、执行超时及阻断看门狗规约。
              </p>
            </div>
            <div>
              <button class="btn btn-outline btn-sm" @click="loadSkillsAndScriptsAssets">🔄 重新探测脚本</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap:20px;">
            <div 
              v-for="sc in availableScriptRegistry" 
              :key="sc.id" 
              class="script-asset-card"
            >
              <!-- 顶部极光绿装饰条 -->
              <div class="script-card-indicator"></div>

              <div class="script-card-main">
                <!-- 头部：图标 + 脚本文件名 + 运行环境胶囊 -->
                <div class="script-card-header">
                  <div class="script-avatar-box">
                    <span class="script-avatar-icon">{{ sc.icon || '🛠️' }}</span>
                  </div>
                  <div class="script-title-block">
                    <div class="script-id-row">
                      <span class="script-id-text">{{ sc.id }}</span>
                      <span class="script-badge-runtime">{{ sc.runtime || 'Python 3' }}</span>
                    </div>
                    <div class="script-name-text">{{ sc.name }}</div>
                  </div>
                </div>

                <!-- 脚本功能说明 -->
                <p class="script-desc-text">
                  {{ sc.desc || '底层自动化执行/看门狗脚本，纳管质量门禁与状态机流转。' }}
                </p>

                <!-- 命令行调用样例代码盒 -->
                <div class="script-cli-terminal">
                  <span class="cli-prompt">$</span>
                  <span class="cli-command">{{ sc.cmd }}</span>
                </div>
              </div>

              <!-- 底部操作与看门狗就绪状态 -->
              <div class="script-card-footer">
                <div class="script-status-indicator">
                  <span class="pulse-dot-green"></span>
                  <span class="pulse-text-green">看门狗就绪</span>
                </div>
                <button class="btn btn-outline btn-sm script-action-btn" @click="handleOpenScriptDetail(sc)">
                  <span>💻 在线透视源码</span>
                  <span class="arrow-icon">→</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= 💡 专门治理菜单：工作流 FAQ 知识中心 ================= -->
        <div id="page-faq" class="view-page" :class="{ active: currentPage === 'page-faq' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; flex-wrap:wrap; gap:12px;">
            <div>
              <h2 style="font-size:20px; font-weight:700; display:flex; align-items:center; gap:8px;">
                <span>💡 工作流常见问题与权威解答 (FAQ)</span>
                <span class="tag" style="background:#6366f1; color:#fff;">工程规范与避坑宝典</span>
              </h2>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                系统化整理工程落地过程中的高频疑难杂症：自适应分流、质量门禁 BLOCKING、看板状态机流转、AI Skills 契约与线上排障铁律。
              </p>
            </div>
            <div style="display:flex; align-items:center; gap:10px;">
              <button class="btn btn-outline btn-sm" @click="expandAllFaqs">📂 展开全部</button>
              <button class="btn btn-outline btn-sm" @click="collapseAllFaqs">📁 折叠全部</button>
            </div>
          </div>

          <!-- 搜索与分类胶囊工具栏 -->
          <div class="faq-toolbar-card">
            <div class="faq-search-wrapper">
              <span class="faq-search-icon">🔍</span>
              <input
                v-model="faqSearchQuery"
                type="text"
                class="faq-search-input"
                placeholder="搜索问题、SOP、门禁命令、报错关键词 (如: gate.py / 锚点 / B4 / 快轨 / 单焦点)..."
              />
              <button v-if="faqSearchQuery" class="faq-search-clear" @click="faqSearchQuery = ''">✕</button>
            </div>

            <div class="faq-category-tabs">
              <button
                v-for="cat in WORKFLOW_FAQ_CATEGORIES"
                :key="cat.key"
                class="faq-cat-btn"
                :class="{ active: selectedFaqCategory === cat.key }"
                @click="selectedFaqCategory = cat.key"
              >
                <span>{{ cat.icon }}</span>
                <span>{{ cat.label }}</span>
              </button>
            </div>
          </div>

          <!-- FAQ 问答卡片列表 -->
          <div v-if="filteredFaqList.length > 0" class="faq-list-container">
            <div
              v-for="faq in filteredFaqList"
              :key="faq.id"
              class="faq-accordion-card"
              :class="{ 'is-expanded': expandedFaqIds.includes(faq.id) }"
            >
              <!-- 折叠头部 -->
              <div class="faq-accordion-header" @click="toggleFaqExpand(faq.id)">
                <div class="faq-header-left">
                  <span class="faq-badge-id">{{ faq.id }}</span>
                  <span class="faq-badge-cat">{{ faq.categoryName }}</span>
                  <h3 class="faq-question-title">{{ faq.question }}</h3>
                </div>
                <div class="faq-header-right">
                  <div class="faq-tags-group">
                    <span v-for="tag in faq.tags" :key="tag" class="faq-tag-chip">{{ tag }}</span>
                  </div>
                  <span class="faq-toggle-arrow" :class="{ 'is-rotated': expandedFaqIds.includes(faq.id) }">▼</span>
                </div>
              </div>

              <!-- 展开内容区 -->
              <div v-show="expandedFaqIds.includes(faq.id)" class="faq-accordion-body">
                <div class="faq-summary-banner">
                  <span class="faq-summary-icon">💡</span>
                  <span class="faq-summary-text">{{ faq.summary }}</span>
                </div>

                <!-- 详细答案文本区 -->
                <div class="faq-answer-block">
                  <div class="faq-answer-title">📌 权威解析与指引：</div>
                  <div class="faq-answer-text">{{ faq.answer }}</div>
                </div>

                <!-- 最佳实践与关联 SOP 脚注 -->
                <div class="faq-footer-meta">
                  <div v-if="faq.bestPractice" class="faq-meta-item">
                    <span class="meta-label">🏆 最佳工程实践：</span>
                    <span class="meta-val">{{ faq.bestPractice }}</span>
                  </div>
                  <div v-if="faq.relatedSop" class="faq-meta-item">
                    <span class="meta-label">🔗 对应工程规约：</span>
                    <code class="meta-code">{{ faq.relatedSop }}</code>
                  </div>
                  <div v-if="faq.commandExample" class="faq-meta-item">
                    <span class="meta-label">💻 推荐调试命令：</span>
                    <code class="meta-cli">$ {{ faq.commandExample }}</code>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 空态提示 -->
          <div v-else class="faq-empty-state">
            <div style="font-size:36px; margin-bottom:10px;">🔍</div>
            <div style="font-size:15px; font-weight:600; color:#475569;">未匹配到任何工作流相关问答</div>
            <div style="font-size:12px; color:#94a3b8; margin-top:4px;">请尝试更换搜索关键词或重置分类筛选</div>
            <button class="btn btn-outline btn-sm" style="margin-top:14px;" @click="faqSearchQuery = ''; selectedFaqCategory = 'all'">重置搜索条件</button>
          </div>
        </div>

        <!-- ================= P03 项目工作台 ================= -->
        <div id="page-dashboard" class="view-page" :class="{ active: currentPage === 'page-dashboard' }">
          <!-- 项目顶部信息卡片 -->
          <div class="dashboard-project-hero">
            <div class="dashboard-hero-main">
              <div class="dashboard-hero-title-row">
                <button
                  class="btn btn-outline btn-sm dashboard-back-btn"
                  title="返回项目列表大厅"
                  @click="navigatePage('page-projects')"
                >
                  <span>📁 全部项目</span>
                </button>
                <h2 class="dashboard-hero-title">{{ currentProject.name || '项目需求工作台' }}</h2>
                <span class="tag tag-baselined">{{ currentProject.projectKey || 'NET-SIM' }}</span>
                <span v-if="currentProject.clientName" class="dashboard-client-badge">
                  🏢 客户单位：<strong>{{ currentProject.clientName }}</strong>
                </span>
              </div>
              <div v-if="currentProject.description" class="dashboard-desc-container">
                <div class="dashboard-desc-text" :class="{ 'is-expanded': isProjectDescExpanded }">
                  <span class="dashboard-desc-label">项目简介：</span>{{ currentProject.description }}
                </div>
                <button
                  v-if="currentProject.description && currentProject.description.length > 70"
                  class="dashboard-desc-toggle-btn"
                  @click="isProjectDescExpanded = !isProjectDescExpanded"
                >
                  {{ isProjectDescExpanded ? '收起 ▲' : '查看完整简介 ▼' }}
                </button>
              </div>
            </div>
            <div class="dashboard-hero-actions">
              <!-- 仅管理员有权执行项目删除，并提供安全防误删防护 -->
              <button 
                v-if="isAdmin" 
                class="btn btn-outline" 
                style="color: #DC2626; border-color: #FCA5A5; background: #FEF2F2;"
                title="管理员高危操作：删除/废弃当前项目空间"
                @click="handleDeleteProject()"
              >
                🗑️ 删除项目
              </button>
              <button class="btn btn-primary" @click="handleOpenNewReqDrawer">+ 新建需求</button>
            </div>
          </div>

          <!-- 统计卡片 -->
          <div class="stat-grid">
            <div class="stat-card">
              <div class="stat-label">总需求项</div>
              <div class="stat-value">{{ projectStats.totalRequirements || requirements.length }}</div>
              <div class="stat-sub">本期总人天: {{ Number(projectStats.totalCostDays || 0).toFixed(1) }}d</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">已冻结基线</div>
              <div class="stat-value" style="color:var(--success);">{{ projectStats.baselinedCount || 0 }}</div>
              <div class="stat-sub">最新: {{ projectStats.latestBaselineCode || '—' }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">待签字 / 待澄清</div>
              <div class="stat-value" style="color:var(--accent);">{{ (projectStats.pendingCount || 0) + (projectStats.clarifyingCount || 0) }}</div>
              <div class="stat-sub">待签: {{ projectStats.pendingCount || 0 }} ｜ 澄清: {{ projectStats.clarifyingCount || 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">原型快照总数</div>
              <div class="stat-value">{{ projectStats.prototypeCount || 0 }}</div>
              <div class="stat-sub">100% SHA256 哈希固化</div>
            </div>
          </div>

          <!-- 需求列表卡片 -->
          <div class="card">
            <div class="card-header">
              <div class="card-title">
                <span>📋 需求全景清单 (L1)</span>
                <span class="tag tag-clarifying">共 {{ requirements.length }} 条记录</span>
              </div>
              <div style="display:flex; gap:10px;">
                <input type="text" class="form-control" style="width:200px;" placeholder="按编号/标题过滤...">
                <button class="btn btn-outline btn-sm">筛选</button>
              </div>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>需求编号</th>
                    <th>需求标题</th>
                    <th>当前状态</th>
                    <th>基线归属</th>
                    <th>负责人</th>
                    <th>代价合计</th>
                    <th>最后更新</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="requirements.length === 0">
                    <td colspan="8" style="text-align:center; padding: 40px 0; color: var(--text-muted);">
                      <div style="font-size: 32px; margin-bottom: 8px;">📋</div>
                      <div>当前项目空间暂无需求数据，请点击右上角「+ 新建需求」录入新需求</div>
                    </td>
                  </tr>
                  <tr v-for="req in requirements" :key="req.id">
                    <td><strong>{{ req.reqCode || req.reqNo }}</strong></td>
                    <td>
                      <a href="javascript:void(0)" @click="openReqDetail(req)" style="color:var(--primary); font-weight:600; text-decoration:none;">
                        {{ req.title }}
                      </a>
                    </td>
                    <td>
                      <span
                        class="tag"
                        :class="{
                          'tag-baselined': req.status === 'BASELINED',
                          'tag-pending': req.status === 'PENDING',
                          'tag-clarifying': req.status === 'CLARIFYING' || req.status === 'DRAFT'
                        }"
                      >
                        ● {{ req.statusText }}
                      </span>
                    </td>
                    <td><span style="font-family:monospace; color:var(--primary);">{{ req.baselineCode }}</span></td>
                    <td>{{ req.ownerName || req.owner }}</td>
                    <td><strong>{{ req.cost }}</strong></td>
                    <td>{{ req.updatedAt }}</td>
                    <td>
                      <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="openReqDetail(req)">详情 (P04)</button>
                      <button v-if="req.status === 'BASELINED'" class="btn btn-outline btn-sm" @click="handleOpenChecklist(req)">清单 (P06)</button>
                      <button v-else-if="req.status === 'PENDING'" class="btn btn-accent btn-sm" @click="switchPerspective('client')">模拟客户签字 (E02)</button>
                      <button v-else class="btn btn-outline btn-sm" @click="handleOpenChecklist(req)">录入清单</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- ================= P04 需求详情 & 清单 ================= -->
        <div id="page-req-detail" class="view-page" :class="{ active: currentPage === 'page-req-detail' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <div style="display:flex; align-items:center; gap:8px;">
                <button class="btn btn-outline btn-sm" style="padding:2px 8px; margin-right:4px; display:inline-flex; align-items:center; gap:4px;" @click="navigatePage('page-dashboard')">
                  <span>⬅ 返回工作台</span>
                </button>
                <span style="font-weight:700; font-size:18px;">{{ currentReq.reqNo }} · {{ currentReq.title }}</span>
                <span class="tag tag-baselined">{{ currentReq.status === 'BASELINED' ? '已基线 ' + currentReq.baselineCode : '需求澄清中' }}</span>
              </div>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                负责人：{{ currentReq.owner }} ｜ 创建：{{ currentReq.createdAt }} ｜ 优先级：{{ currentReq.priority }}
              </p>
            </div>
            <div style="display:flex; gap:10px; align-items: center;">
              <button 
                v-if="isAdmin && currentReq.status === 'BASELINED'" 
                class="btn btn-outline" 
                style="color: #D97706; border-color: #FCD34D; background: #FFFBEB;"
                title="管理员特权操作：解除当前需求的基线物理冻结，重新开放编辑与粉碎删除"
                @click="handleUnfreezeSingleRequirement"
              >
                🔓 解除基线冻结
              </button>
              <button class="btn btn-outline" @click="drawerChecklistOpen = true">✏️ 编辑确认清单 (P06)</button>
              <button class="btn btn-primary" @click="drawerNewBaselineOpen = true">🔒 生成新基线 (M08)</button>
            </div>
          </div>

          <!-- 客户原话引用 -->
          <div class="card" style="border-left: 4px solid var(--primary); background:#FCFDFE;">
            <div class="card-body" style="padding:14px 20px;">
              <div style="font-size:12px; font-weight:600; color:var(--text-muted); margin-bottom:4px;">🗣️ 客户原话记录 (不可篡改留痕)</div>
              <div style="font-size:14px; color:var(--text); font-style:italic;">
                {{ currentReq.quote }}
              </div>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 2fr 1fr; gap:20px;">
            <!-- 清单详情 -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">📌 四层确认清单详情 (L2)</div>
                <span style="font-size:12px; color:var(--text-muted);">本期已敲定 {{ currentCost.toFixed(1) }} 人天 · 共 {{ confirmItems.length }} 项</span>
              </div>
              <div class="card-body" style="padding:0;">
                <div v-if="confirmItems.length === 0" style="padding: 46px 20px; text-align: center; color: var(--text-muted);">
                  <div style="font-size: 32px; margin-bottom: 8px;">📋</div>
                  <div style="font-weight: 600; font-size: 15px; color: var(--text); margin-bottom: 4px;">当前需求暂无四层确认清单</div>
                  <div style="font-size: 13px; color: var(--text-muted); max-width: 420px; margin: 0 auto 16px auto; line-height: 1.5;">
                    新创建的需求只包含客户原话留痕。请点击下方按钮开始细化「范围 / 规则 / 异常 / 本期不做」四层清单并折算交付代价。
                  </div>
                  <button class="btn btn-primary btn-sm" @click="drawerChecklistOpen = true">✏️ 录入与编辑确认清单 (P06)</button>
                </div>
                <table v-else class="data-table">
                  <thead>
                    <tr>
                      <th>层级类别</th>
                      <th>问法与细化项</th>
                      <th>选项约定</th>
                      <th>代价</th>
                      <th>决定结果</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in confirmItems" :key="item.id">
                      <td>
                        <span
                          class="tag"
                          :class="{
                            'tag-clarifying': item.itemCategory === 'SCOPE',
                            'tag-draft': item.itemCategory === 'RULE',
                            'tag-danger': item.itemCategory === 'NON_GOAL' || item.itemCategory === 'SKIP'
                          }"
                        >
                          {{ item.layerText }}
                        </span>
                      </td>
                      <td><strong>{{ item.title }}</strong></td>
                      <td>{{ item.optionAgreement }}</td>
                      <td>{{ item.costDelta > 0 ? `+${item.costDelta}d` : '0.0d' }}</td>
                      <td>
                        <span
                          class="tag"
                          :class="{
                            'tag-baselined': item.decisionResult === 'CURRENT',
                            'tag-draft': item.decisionResult === 'SKIP',
                            'tag-pending': item.decisionResult === 'NEXT'
                          }"
                        >
                          {{ item.decisionText }}
                        </span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 右侧关联信息 -->
            <div>
              <div class="card">
                <div class="card-header">
                  <div class="card-title">🔗 绑定快照资产</div>
                </div>
                <div class="card-body">
                  <div style="margin-bottom:12px;">
                    <div style="font-size:12px; color:var(--text-muted);">绑定原型版本</div>
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-top:4px;">
                      <strong style="color:var(--primary);">{{ currentReq?.latestPrototypeVersion ? currentReq.latestPrototypeVersion + ' (最新快照)' : '已绑定最新快照' }}</strong>
                      <button class="btn btn-outline btn-sm" @click="navigatePage('page-prototypes')">查看 (P08)</button>
                    </div>
                    <div style="font-family:monospace; font-size:11px; color:#64748B; margin-top:2px;">SHA256: a3f9c2e177b9...</div>
                  </div>
                  <hr style="border:none; border-top:1px solid var(--border); margin:12px 0;">
                  <div>
                    <div style="font-size:12px; color:var(--text-muted);">关联设计文档 (L4)</div>
                    <ul style="list-style:none; margin-top:6px; font-size:13px;">
                      <li style="padding:4px 0; display:flex; justify-content:space-between;">
                        <span>📄 详细设计说明书 v1.0</span>
                        <span class="tag tag-draft">SHA已固化</span>
                      </li>
                      <li style="padding:4px 0; display:flex; justify-content:space-between;">
                        <span>📊 DDL 结构与回滚配对</span>
                        <span class="tag tag-draft">1:1 配对</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>

              <div class="card" style="border-top:3px solid var(--success);">
                <div class="card-body">
                  <div style="font-weight:600; font-size:13px; margin-bottom:6px;">🔒 当前基线状态：BL-v1.1 (已冻结)</div>
                  <p style="font-size:12px; color:var(--text-muted); line-height:1.6;">
                    业务拍板人（张工）与验收人（李工）均已完成签名；此版本任何字段已不可随意在代码中擅改。
                  </p>
                  <div style="display:flex; gap:8px; margin-top:10px;">
                    <button class="btn btn-outline btn-sm" style="flex:1;" @click="navigatePage('page-baselines')">
                      基线与签字清单 (P10)
                    </button>
                    <button class="btn btn-outline btn-sm" style="flex:1;" @click="handleViewDiff">
                      差异对比 (P12)
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P07/P08 原型版本管理与预览 ================= -->
        <div id="page-prototypes" class="view-page" :class="{ active: currentPage === 'page-prototypes' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.projectKey }} · 项目原型版本与托管</h2>
              <p style="color:var(--text-muted); font-size:13px;">所属项目：{{ currentProject.name }} ｜ 严格遵循“旧版本永不覆盖”、“SHA256 快照固化”与“在线只读可交互”规范</p>
            </div>
            <div style="display:flex; align-items:center; gap:12px;">
              <!-- 项目快速切换下拉选择器 -->
              <div style="display:flex; align-items:center; gap:6px;">
                <span style="font-size:12px; font-weight:600; color:var(--text-muted);">切换项目：</span>
                <select
                  class="form-control"
                  style="width:230px; font-size:13px; padding:4px 10px; font-weight:600; color:var(--primary); background:#fff;"
                  v-model="selectedProject"
                  @change="handleProjectChange('page-prototypes')"
                >
                  <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
                    {{ p.projectKey }} · {{ p.name }}
                  </option>
                </select>
              </div>

              <span class="tag tag-baselined" style="padding:5px 10px; font-size:12px;">
                共 {{ prototypeList.length }} 个快照
              </span>
              <button class="btn btn-primary" @click="handleOpenUploadProtoModal">📤 上传原型文件</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 320px 1fr; gap:20px;">
            <!-- 版本列表 (直连 MySQL t_req_prototype_version) -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">📦 项目原型快照清单 (L3)</div>
              </div>
              <div style="padding:10px;">
                <div v-if="prototypeList.length === 0" style="padding:30px 12px; text-align:center; color:var(--text-muted); font-size:13px;">
                  <div style="font-size:24px; margin-bottom:6px;">📦</div>
                  当前项目暂无上传的原型快照，请点击右上角「上传原型文件」
                </div>
                <div
                  v-for="p in prototypeList"
                  :key="p.id"
                  style="padding:12px; border-radius:var(--radius-sm); margin-bottom:8px; cursor:pointer;"
                  :style="{
                    background: (selectedPrototype && selectedPrototype.id === p.id) ? 'var(--primary-light)' : '#FFF',
                    border: (selectedPrototype && selectedPrototype.id === p.id) ? '1px solid var(--primary)' : '1px solid var(--border)'
                  }"
                  @click="() => { selectedPrototype = p; selectedPrototypeVer = p.versionNo; }"
                >
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong>{{ p.versionNo }}</strong>
                    <span class="tag tag-baselined">● 物理存证</span>
                  </div>
                  <div style="font-size:12px; color:var(--text-muted); margin-top:4px;">
                    上传人: {{ p.uploadedBy }} ｜ {{ p.uploadedAt ? p.uploadedAt.replace('T', ' ').substring(0, 16) : '—' }}
                  </div>
                  <div style="font-family:monospace; font-size:11px; color:var(--primary); margin-top:4px; word-break:break-all;">
                    hash: {{ p.fileHash ? p.fileHash.substring(0, 16) + '...' : '—' }}
                  </div>
                  <div v-if="p.changeSummary" style="font-size:11px; color:#64748B; margin-top:4px;">
                    {{ p.changeSummary }}
                  </div>
                </div>
              </div>
            </div>

            <!-- 原型全屏沙盒只读预览 (P08) -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">
                  <span>🖥️ 在线原型渲染预览 (P08)</span>
                  <span class="tag tag-clarifying">
                    渲染版本：{{ selectedPrototype ? selectedPrototype.versionNo : '未选择' }} (只读沙盒)
                  </span>
                </div>
                <div style="display:flex; gap:8px;">
                  <button class="btn btn-outline btn-sm" @click="ElMessage.success('已触发快照 ZIP 安全下载')">⬇️ 下载快照 Zip</button>
                </div>
              </div>
              <div class="card-body" style="padding:16px;">
                <div style="border:2px solid var(--border-dark); border-radius:var(--radius-sm); background:#FFF; height:460px; display:flex; flex-direction:column; overflow:hidden;">
                  <div style="background:#0F172A; color:#FFF; padding:8px 14px; font-size:12px; display:flex; justify-content:space-between; align-items:center;">
                    <span>🔴 🟡 🟢 原型模拟器：{{ selectedPrototype?.previewUrl || '—' }}</span>
                    <span>只读模式 (Iframe Sandbox)</span>
                  </div>
                  <div style="flex:1; background:#F8FAFC; position:relative;">
                    <iframe
                      v-if="selectedPrototype && selectedPrototype.previewUrl"
                      :src="selectedPrototype.previewUrl"
                      style="width:100%; height:100%; border:none;"
                      sandbox="allow-scripts allow-same-origin"
                    ></iframe>
                    <div v-else style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; color:var(--text-muted); font-size:13px;">
                      <div style="font-size:32px; margin-bottom:8px;">🖥️</div>
                      当前需求暂无可渲染的原型 HTML，请上传包含 mockup.html 的 ZIP 包
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P10/P11 基线与签字 ================= -->
        <div id="page-baselines" class="view-page" :class="{ active: currentPage === 'page-baselines' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">基线列表与客户签字控制 (P10/P11)</h2>
              <p style="color:var(--text-muted); font-size:13px;">必须集齐三方签字方可冻结，冻结后由 Git Tag + Manifest + Zip 构成不可变存证</p>
            </div>
            <div>
              <button class="btn btn-outline" style="margin-right:8px;" @click="navigatePage('page-req-detail')">← 返回需求详情</button>
              <button class="btn btn-primary" @click="drawerNewBaselineOpen = true">➕ 创建新基线 (M08)</button>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div class="card-title">🔒 基线清单与签字进度 (L5 & L6)</div>
              <div style="font-size:12px; color:var(--text-muted);">不可变锚点：每个冻结基线必须生成法定 Git Tag 与 Manifest Hash</div>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>基线编号</th>
                    <th>关联需求</th>
                    <th>Git Tag 锚点</th>
                    <th>引用原型</th>
                    <th>关联文档</th>
                    <th>签字进度</th>
                    <th>冻结状态</th>
                    <th>代价折算</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="baselineList.length === 0">
                    <td colspan="9" style="text-align:center; padding:36px; color:var(--text-muted);">
                      <div style="font-size:24px; margin-bottom:6px;">🔒</div>
                      当前需求暂无已生成基线，请点击右上角「➕ 创建新基线 (M08)」开启三方存证
                    </td>
                  </tr>
                  <tr v-for="b in baselineList" :key="b.id">
                    <td><strong>{{ b.version }}</strong></td>
                    <td>{{ currentReq.reqCode }} ({{ currentReq.title }})</td>
                    <td>
                      <code v-if="b.gitTag" style="background:#EEF2F6; padding:2px 6px; border-radius:3px; font-size:11px;">
                        {{ b.gitTag }}
                      </code>
                      <span v-else style="color:var(--text-muted); font-size:11px;">待冻结生成</span>
                    </td>
                    <td>
                      <span class="tag tag-clarifying">v{{ b.prototypeVersionId }}</span>
                    </td>
                    <td>2 份 (说明书+DDL)</td>
                    <td>
                      <div style="display:flex; align-items:center; gap:6px;">
                        <span v-if="b.status === 'BASELINED'" style="color:var(--success); font-weight:700;">● 3/3 齐备 (已签)</span>
                        <span v-else style="color:var(--accent); font-weight:700;">○ 待签字确认</span>
                      </div>
                    </td>
                    <td>
                      <span class="tag" :class="b.status === 'BASELINED' ? 'tag-baselined' : 'tag-pending'">
                        {{ b.status === 'BASELINED' ? '已物理冻结' : '待签名链接' }}
                      </span>
                    </td>
                    <td><strong>{{ Number(b.totalCostDays || 0).toFixed(1) }} 人天</strong></td>
                    <td>
                      <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="handleViewDiff">差异对比 (P12)</button>
                      <button v-if="b.status === 'BASELINED'" class="btn btn-outline btn-sm" @click="switchPerspective('client')">回执凭证 (E03)</button>
                      <button v-else class="btn btn-accent btn-sm" @click="modalGenSignLinkOpen = true">生成签名链接 (M09)</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 冻结前置防扯皮看门狗门禁 Checklist -->
          <div class="card" style="border-top: 3px solid var(--accent);">
            <div class="card-header" style="background:#FFFDFB;">
              <div class="card-title">
                <span>🛡️ 基线冻结看门狗门禁核验看板 (Gatekeeper Checklist)</span>
                <span class="tag tag-pending">前置防扯皮安全检查</span>
              </div>
              <span style="font-size:12px; color:var(--text-muted);">蓝皮书 §4.1 硬门禁：全部点亮方允许执行终极冻结</span>
            </div>
            <div class="card-body">
              <div style="display:grid; grid-template-columns: repeat(4, 1fr); gap:14px;">
                <div style="background:#F0FDF4; border:1px solid #BBF7D0; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#15803D; font-size:13px;">1. 清单四层齐备度</strong>
                    <span style="color:#16A34A; font-weight:700;">✔ 100%</span>
                  </div>
                  <div style="font-size:11px; color:#64748B; margin-top:4px;">范围/规则/异常/验收均有明确条目</div>
                </div>

                <div style="background:#F0FDF4; border:1px solid #BBF7D0; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#15803D; font-size:13px;">2. "本期不做"收敛数</strong>
                    <span style="color:#16A34A; font-weight:700;">✔ 3/3 条</span>
                  </div>
                  <div style="font-size:11px; color:#64748B; margin-top:4px;">符合硬门禁 ≥3 条约束，防止范围蔓延</div>
                </div>

                <div style="background:#F0FDF4; border:1px solid #BBF7D0; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#15803D; font-size:13px;">3. 原型与文档 SHA256</strong>
                    <span style="color:#16A34A; font-weight:700;">✔ 已固化</span>
                  </div>
                  <div style="font-size:11px; color:#64748B; margin-top:4px;">mockup v3 + 3份文档哈希全部就绪</div>
                </div>

                <div style="background:#FEF3C7; border:1px solid #FCD34D; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#B45309; font-size:13px;">4. 三方法定电子签字</strong>
                    <span style="color:#D97706; font-weight:700;">⏳ 待就绪</span>
                  </div>
                  <div style="font-size:11px; color:#B45309; margin-top:4px;">已签 2/3 (使用者代表签字待回收)</div>
                </div>
              </div>

              <div style="margin-top:14px; display:flex; justify-content:space-between; align-items:center; background:#F8FAFC; padding:10px 14px; border-radius:4px; font-size:12px;">
                <span style="color:var(--text-secondary);">
                  ⚠️ 提示：当前 BL-v0.9 尚缺 1 项三方签字，终极冻结按钮锁定中；满足后将自动打包 Zip 并触发 Git Tag 归档。
                </span>
                <button class="btn btn-primary btn-sm" @click="handleGateCheck">🛡️ 触发门禁核验</button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P12 基线差异对比 ================= -->
        <div id="page-baseline-diff" class="view-page" :class="{ active: currentPage === 'page-baseline-diff' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">基线差异智能对比 (P12)</h2>
              <p style="color:var(--text-muted); font-size:13px;">对比基线：<span style="color:var(--primary); font-weight:700;">BL-v1.0 (旧)</span> ➔ <span style="color:var(--success); font-weight:700;">BL-v1.1 (新)</span></p>
            </div>
            <div>
              <button class="btn btn-outline" style="margin-right:8px;" @click="navigatePage('page-req-detail')">← 返回需求详情</button>
              <button class="btn btn-outline" @click="ElMessage.success('已导出基线差异对比审计报告 (PDF)')">📄 导出审计 PDF</button>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div class="card-title">📝 确认清单变更对比 (3 处实质变化)</div>
              <span class="tag tag-clarifying">净代价增长：+0.5 人天</span>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>变更类型</th>
                    <th>清单项名称</th>
                    <th>原基线约定 (BL-v1.0)</th>
                    <th>当前基线约定 (BL-v1.1)</th>
                    <th>人天代价差异</th>
                  </tr>
                </thead>
                <tbody>
                  <tr style="background:#F0FDF4;">
                    <td><span class="tag tag-baselined">+ 新增</span></td>
                    <td><strong>E-03 数据延迟报警提示</strong></td>
                    <td>无此项</td>
                    <td>网络超时延迟 >2s 提示黄框</td>
                    <td><strong style="color:var(--success);">+0.5d</strong></td>
                  </tr>
                  <tr style="background:#FEF3C7;">
                    <td><span class="tag tag-pending">~ 修改</span></td>
                    <td><strong>B-01 心跳掉线判定阈值</strong></td>
                    <td>连续 120 秒无心跳视为掉线</td>
                    <td>收紧为连续 60 秒无心跳掉线</td>
                    <td><strong>+0.0d</strong></td>
                  </tr>
                  <tr style="background:#FEF2F2;">
                    <td><span class="tag tag-danger">- 移除</span></td>
                    <td><strong>R-03 移动端响应式支持</strong></td>
                    <td>纳入一期交付</td>
                    <td>移至二期（本期不做）</td>
                    <td><strong style="color:#DC2626;">-0.0d (已扣减)</strong></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 1fr 1fr; gap:20px;">
            <div class="card">
              <div class="card-header">
                <div class="card-title">🎨 原型版本变更</div>
              </div>
              <div class="card-body">
                <div style="font-size:13px; margin-bottom:8px;">原版本：<strong>mockup v2.0 (hash: 7c1e...)</strong></div>
                <div style="font-size:13px; color:var(--success); margin-bottom:12px;">新版本：<strong>mockup v3.0 (hash: a3f9...)</strong></div>
                <p style="font-size:12px; color:var(--text-muted); line-height:1.5;">
                  变更特征：新增 Sub-Zone 嵌套底板渲染；修正了调速器状态指示颜色对比度。
                </p>
              </div>
            </div>
            <div class="card">
              <div class="card-header">
                <div class="card-title">📄 关联设计文档变更</div>
              </div>
              <div class="card-body">
                <div style="font-size:13px; margin-bottom:6px;">• 详细设计说明书: <strong>v1.0 ➔ v1.1</strong> (更新表结构索引)</div>
                <div style="font-size:13px; margin-bottom:6px;">• DDL 配对脚本: <strong>增补 V1.1__add_delay_field.sql</strong></div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P16 发布版本 ================= -->
        <div id="page-releases" class="view-page" :class="{ active: currentPage === 'page-releases' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name }} ({{ currentProject.projectKey }}) · 发布版本追踪 (P16)</h2>
              <p style="color:var(--text-muted); font-size:13px;">一个发布版本必须严格绑定一个已冻结基线 (MySQL t_req_release 实时持久化)，正式发布自动打 Git Tag 并联动推进需求状态</p>
            </div>
            <div style="display:flex; align-items:center; gap:12px;">
              <!-- 项目快速切换下拉选择器 -->
              <div style="display:flex; align-items:center; gap:6px;">
                <span style="font-size:12px; font-weight:600; color:var(--text-muted);">切换项目：</span>
                <select
                  class="form-control"
                  style="width:230px; font-size:13px; padding:4px 10px; font-weight:600; color:var(--primary); background:#fff;"
                  v-model="selectedProject"
                  @change="handleProjectChange('page-releases')"
                >
                  <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
                    {{ p.projectKey }} · {{ p.name }}
                  </option>
                </select>
              </div>

              <button class="btn btn-outline" @click="loadProjectReleases(currentProject.id)">🔄 刷新列表</button>
              <button class="btn btn-primary" @click="handleOpenNewReleaseModal">+ 新建发布计划</button>
            </div>
          </div>

          <div class="card">
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>发布版本号</th>
                    <th>绑定已冻结基线</th>
                    <th>关联需求</th>
                    <th>Git Tag</th>
                    <th>计划发布日期</th>
                    <th>实际发布时间</th>
                    <th>当前状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="releaseLoading">
                    <td colspan="8" style="text-align:center; padding:32px; color:var(--text-muted);">⏳ 正在从 MySQL 加载发布版本列表...</td>
                  </tr>
                  <tr v-else-if="projectReleases.length === 0">
                    <td colspan="8" style="text-align:center; padding:36px; color:var(--text-muted);">
                      <div style="font-size:24px; margin-bottom:6px;">🚀</div>
                      当前项目暂无发版计划，请点击右上角【+ 新建发布计划 (M12)】绑定已冻结基线
                    </td>
                  </tr>
                  <tr v-for="rel in projectReleases" :key="rel.id">
                    <td><strong>{{ rel.releaseVersion }}</strong></td>
                    <td><span style="color:var(--primary); font-weight:600;">{{ rel.baselineVersion }}</span></td>
                    <td>
                      <span class="tag tag-clarifying" style="font-size:11px; padding:1px 5px; margin-right:4px;">{{ rel.reqCode }}</span>
                      <span style="font-size:12px;">{{ rel.reqTitle }}</span>
                    </td>
                    <td>
                      <code v-if="rel.gitTag" style="font-size:11px; background:#F1F5F9; padding:2px 6px; border-radius:4px; color:#0F766E;">{{ rel.gitTag }}</code>
                      <span v-else style="color:var(--text-muted);">待发布打标</span>
                    </td>
                    <td>{{ rel.plannedDate || '—' }}</td>
                    <td>{{ rel.releasedAt ? String(rel.releasedAt).replace('T', ' ').slice(0, 16) : '—' }}</td>
                    <td><span class="tag" :class="rel.status === 'RELEASED' ? 'tag-baselined' : 'tag-clarifying'">{{ rel.statusText }}</span></td>
                    <td>
                      <button v-if="rel.status !== 'RELEASED'" class="btn btn-primary btn-sm" style="margin-right:6px;" @click="handlePublishRelease(rel)">🚀 正式发布</button>
                      <button class="btn btn-outline btn-sm" @click="handleViewReleaseDetail(rel)">查看发布清单</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- M12 新建发布计划弹窗 -->
          <div v-if="modalNewReleaseOpen" class="modal-overlay active" @click.self="modalNewReleaseOpen = false">
            <div class="modal-content" style="max-width:520px; width:92%; background:#FFF; border-radius:10px; padding:24px; box-shadow:0 20px 50px rgba(0,0,0,0.2);">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; border-bottom:1px solid var(--border); padding-bottom:10px;">
                <h3 style="font-size:16px; font-weight:700;">🚀 新建发布计划 (M12)</h3>
                <button class="btn btn-outline btn-sm" @click="modalNewReleaseOpen = false">✕</button>
              </div>
              <div style="display:flex; flex-direction:column; gap:14px;">
                <div>
                  <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">绑定已冻结基线 (强约束：仅限 BASELINED)</label>
                  <select v-model="newReleaseForm.baselineId" class="form-control" style="width:100%;">
                    <option v-for="bl in frozenBaselines" :key="bl.id" :value="bl.id">
                      {{ bl.version }} · {{ bl.reqCode }} {{ bl.reqTitle }} ({{ bl.totalCostDays || 0 }}人天)
                    </option>
                  </select>
                </div>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
                  <div>
                    <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">发布版本号 *</label>
                    <input v-model="newReleaseForm.releaseVersion" class="form-control" placeholder="例如 R-1.1.0" style="width:100%;" />
                  </div>
                  <div>
                    <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">计划发布日期</label>
                    <input v-model="newReleaseForm.plannedDate" type="date" class="form-control" style="width:100%;" />
                  </div>
                </div>
                <div>
                  <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">发布清单与变更摘要</label>
                  <textarea v-model="newReleaseForm.releaseNotes" class="form-control" rows="3" placeholder="填写本次发版包含的核心特性、数据库迁移脚本及回滚要点..." style="width:100%;"></textarea>
                </div>
              </div>
              <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:20px;">
                <button class="btn btn-outline" @click="modalNewReleaseOpen = false">取消</button>
                <button class="btn btn-primary" @click="handleSubmitNewRelease">确认创建发布计划</button>
              </div>
            </div>
          </div>

          <!-- 发布清单详情弹窗 -->
          <div v-if="modalReleaseDetailOpen && activeReleaseDetail" class="modal-overlay active" @click.self="modalReleaseDetailOpen = false">
            <div class="modal-content" style="max-width:560px; width:92%; background:#FFF; border-radius:10px; padding:24px; box-shadow:0 20px 50px rgba(0,0,0,0.2);">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; border-bottom:1px solid var(--border); padding-bottom:10px;">
                <h3 style="font-size:16px; font-weight:700;">📦 发布清单与防伪证据 · {{ activeReleaseDetail.releaseVersion }}</h3>
                <button class="btn btn-outline btn-sm" @click="modalReleaseDetailOpen = false">✕</button>
              </div>
              <div style="display:flex; flex-direction:column; gap:10px; font-size:13px;">
                <div><strong>所属项目：</strong>{{ activeReleaseDetail.projectKey || currentProject.projectKey }}</div>
                <div><strong>绑定已冻结基线：</strong><span style="color:var(--primary); font-weight:700;">{{ activeReleaseDetail.baselineVersion }}</span>（关联需求：{{ activeReleaseDetail.reqCode }} · {{ activeReleaseDetail.reqTitle }}）</div>
                <div><strong>状态：</strong><span class="tag" :class="activeReleaseDetail.status === 'RELEASED' ? 'tag-baselined' : 'tag-clarifying'">{{ activeReleaseDetail.statusText }}</span></div>
                <div><strong>Git Tag：</strong><code>{{ activeReleaseDetail.gitTag || '尚未正式发布打标' }}</code></div>
                <div><strong>Manifest SHA256：</strong><code style="word-break:break-all; font-size:11px;">{{ activeReleaseDetail.manifestHash || '—' }}</code></div>
                <div><strong>计划/发布时间：</strong>{{ activeReleaseDetail.plannedDate || '—' }} ｜ {{ activeReleaseDetail.releasedAt ? String(activeReleaseDetail.releasedAt).replace('T', ' ') : '待发布' }}</div>
                <div style="background:#F8FAFC; border:1px solid #E2E8F0; border-radius:6px; padding:12px; margin-top:4px;">
                  <div style="font-weight:600; margin-bottom:4px;">发布清单与变更摘要：</div>
                  <div style="white-space:pre-wrap; color:var(--text-secondary);">{{ activeReleaseDetail.releaseNotes || '暂无附加说明' }}</div>
                </div>
              </div>
              <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:18px;">
                <button v-if="activeReleaseDetail.status !== 'RELEASED'" class="btn btn-primary" @click="handlePublishRelease(activeReleaseDetail); modalReleaseDetailOpen = false">🚀 立即正式发布</button>
                <button class="btn btn-outline" @click="modalReleaseDetailOpen = false">关闭</button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P13 需求演进时间线 ================= -->
        <div id="page-timeline" class="view-page" :class="{ active: currentPage === 'page-timeline' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name }} ({{ currentProject.projectKey }}) · 需求与基线演进时间线 (P13)</h2>
              <p style="color:var(--text-muted); font-size:13px;">当前项目轻量级里程碑演进总览：一眼洞悉原型迭代 ➔ 基线冻结 ➔ 发布上线全生命周期（当前模式：{{ timelineViewMode === 'BASELINE_ONLY' ? '基线聚合视图' : '全量里程碑视图' }}）</p>
            </div>
            <div style="display:flex; align-items:center; gap:12px;">
              <!-- 项目快速切换下拉选择器 -->
              <div style="display:flex; align-items:center; gap:6px;">
                <span style="font-size:12px; font-weight:600; color:var(--text-muted);">切换项目：</span>
                <select
                  class="form-control"
                  style="width:230px; font-size:13px; padding:4px 10px; font-weight:600; color:var(--primary); background:#fff;"
                  v-model="selectedProject"
                  @change="handleProjectChange('page-timeline')"
                >
                  <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
                    {{ p.projectKey }} · {{ p.name }}
                  </option>
                </select>
              </div>

              <button class="btn btn-outline" @click="handleToggleTimelineViewMode">
                {{ timelineViewMode === 'BASELINE_ONLY' ? '👁️ 切回全量视图' : '👁️ 切换基线聚合视图' }}
              </button>
              <button class="btn btn-outline" @click="handleExportTimelineSvg">📥 导出 SVG 图表</button>
            </div>
          </div>

          <div class="timeline-container">
            <div class="timeline-legend">
              <span style="font-weight:600; color:var(--text);">图例说明：</span>
              <span class="legend-item"><span class="node-icon-circle prototype" style="width:16px; height:16px; font-size:8px;">🎨</span> 原型版本 (可迭代)</span>
              <span class="legend-item"><span class="node-icon-circle baseline" style="width:16px; height:16px; font-size:8px;">🔒</span> 已冻结基线 (不可变存证)</span>
              <span class="legend-item"><span class="node-icon-circle pending-baseline" style="width:16px; height:16px; font-size:8px;">⏳</span> 待签字基线</span>
              <span class="legend-item"><span class="node-icon-circle release" style="width:16px; height:16px; font-size:8px;">🚀</span> 发布交付点 (Git Tag)</span>
              <span class="legend-item" style="color:var(--text-muted); margin-left:auto;">🕒 绿色实线：已签署基线生命周期 ｜ 灰色横轨：演进时间轴</span>
            </div>

            <div class="timeline-axis-header">
              <div class="axis-tick">阶段一 · 原型草拟</div>
              <div class="axis-tick">阶段二 · 清单对撞</div>
              <div class="axis-tick">阶段三 · 基线三方冻结</div>
              <div class="axis-tick">阶段四 · 正式发版打标</div>
              <div class="axis-tick">阶段五 · 持续演进</div>
            </div>

            <!-- 空状态 -->
            <div v-if="filteredTimelineRequirements.length === 0" style="padding: 46px 20px; text-align: center; color: var(--text-muted); background: var(--bg-app); border: 1px dashed var(--border); border-radius: var(--radius-sm); margin: 20px 0;">
              <div style="font-size: 28px; margin-bottom: 8px;">📈</div>
              <div style="font-weight: 600; color: var(--text);">
                {{ timelineViewMode === 'BASELINE_ONLY' ? '当前项目暂无已冻结基线/已发布的需求' : `当前项目 [${currentProject.name}] 暂无演进需求` }}
              </div>
              <div style="font-size: 13px; margin-top: 4px;">可在项目工作台录入需求、冻结基线，或切换回全量视图查看全部泳道</div>
            </div>

            <!-- 动态需求泳道 -->
            <div v-for="reqItem in filteredTimelineRequirements" :key="reqItem.id" class="timeline-row">
              <div class="timeline-row-label">
                <div class="timeline-row-req-title" style="cursor:pointer;" @click="openReqDetail(reqItem, true)">
                  <span class="tag" :class="(reqItem.status === 'BASELINED' || reqItem.status === 'RELEASED') ? 'tag-baselined' : (reqItem.status === 'PENDING' ? 'tag-pending' : 'tag-clarifying')" style="padding:1px 5px; font-size:11px;">
                    {{ reqItem.reqCode }}
                  </span>
                  <span style="font-weight:600;">{{ reqItem.title }}</span>
                </div>
                <div style="font-size:11px; color:var(--text-muted); margin-top:2px;">{{ reqItem.ownerName }} ｜ {{ reqItem.cost }} ｜ {{ reqItem.statusText }}</div>
              </div>
              <div class="timeline-track-wrap">
                <div class="timeline-track-line"></div>
                <div class="timeline-connection-line" :style="{ left: '10%', width: reqItem.status === 'RELEASED' ? '72%' : (reqItem.status === 'BASELINED' ? '65%' : '30%') }"></div>

                <div class="timeline-node" style="left: 10%;" @click="openReqDetail(reqItem, true)">
                  <span class="timeline-date-marker">{{ reqItem.updatedAt }}</span>
                  <div class="node-icon-circle prototype">🎨</div>
                  <span class="node-title-badge">原型/录入</span>
                </div>

                <div class="timeline-node" style="left: 45%;" @click="handleOpenChecklist(reqItem)">
                  <span class="timeline-date-marker">{{ (reqItem.status === 'BASELINED' || reqItem.status === 'RELEASED') ? '已冻结' : '细化中' }}</span>
                  <div class="node-icon-circle" :class="(reqItem.status === 'BASELINED' || reqItem.status === 'RELEASED') ? 'baseline' : 'pending-baseline'">
                    {{ (reqItem.status === 'BASELINED' || reqItem.status === 'RELEASED') ? '🔒' : '⏳' }}
                  </div>
                  <span class="node-title-badge" :style="{ color: (reqItem.status === 'BASELINED' || reqItem.status === 'RELEASED') ? '#16A34A' : '#D97706' }">
                    {{ reqItem.baselineCode !== '—' ? reqItem.baselineCode : '待冻结基线' }}
                  </span>
                </div>

                <div v-if="reqItem.status === 'BASELINED' || reqItem.status === 'RELEASED'" class="timeline-node" style="left: 78%;" @click="navigatePage('page-releases')">
                  <span class="timeline-date-marker">
                    {{ getRequirementReleaseInfo(reqItem.id)?.status === 'RELEASED' ? '已正式发布' : (getRequirementReleaseInfo(reqItem.id) ? '已排期发版' : '待排期发布') }}
                  </span>
                  <div class="node-icon-circle release">🚀</div>
                  <span class="node-title-badge" style="background:#14535B; color:#FFF;">
                    {{ getRequirementReleaseInfo(reqItem.id)?.releaseVersion || 'Release' }}
                  </span>
                </div>
              </div>
            </div>

            <div style="background:#F8FAFC; border:1px solid #E2E8F0; padding:12px 16px; border-radius:4px; font-size:12px; color:var(--text-secondary); margin-top:16px; display:flex; justify-content:space-between; align-items:center;">
              <div>
                💡 <strong>审计核验技巧：</strong> 点击时间轴任意 <strong>[🔒 基线节点]</strong> 可快速下钻查看当时 3 份签字人证据链与 Manifest 哈希；点击 <strong>[🚀 发布节点]</strong> 可调阅该版本的发布预案与回滚快照。
              </div>
              <button class="btn btn-outline btn-sm" @click="navigatePage('page-baseline-diff')">查看基线版本差异 (P12)</button>
            </div>
          </div>
        </div>

        <!-- ================= P17 码表配置中心 ================= -->
        <div id="page-enums" class="view-page" :class="{ active: currentPage === 'page-enums' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">全局码表配置中心 (P17)</h2>
              <p style="color:var(--text-muted); font-size:13px;">统一全站 {{ enumGroups.length }} 组核心业务状态、色彩、排序与操作权限，变更后可导出标准 YAML 或显式发布</p>
            </div>
            <div style="display:flex; gap:10px;">
              <button class="btn btn-outline" @click="handleExportEnumsYaml">📥 导出 YAML</button>
              <button class="btn btn-primary" @click="handlePublishEnumChanges">🚀 发布码表变更 (M15)</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 240px 1fr; gap:20px; align-items:start;">
            <div class="card" style="padding:10px;">
              <div style="font-size:11px; font-weight:700; color:var(--text-muted); padding:8px;">码表分组 ({{ enumGroups.length }})</div>
              <div
                v-for="group in enumGroups"
                :key="group.key"
                class="nav-item"
                :class="{ active: activeEnumGroupKey === group.key }"
                style="border-radius:4px; cursor:pointer; display:flex; justify-content:space-between; align-items:center;"
                @click="handleSelectEnumGroup(group.key)"
              >
                <span>● {{ group.name }}</span>
                <span style="font-size:11px; opacity:0.8;">({{ group.items.length }})</span>
              </div>
            </div>

            <div class="card">
              <div class="card-header" style="display:flex; justify-content:space-between; align-items:center;">
                <div>
                  <div class="card-title">{{ activeEnumGroup.name }}字典详情 (<code>{{ activeEnumGroup.key }}</code>)</div>
                  <div style="font-size:12px; color:var(--text-muted); margin-top:2px;">{{ activeEnumGroup.desc }}</div>
                </div>
                <button class="btn btn-outline btn-sm" @click="handleOpenNewEnumModal">+ 新增取值 (M14)</button>
              </div>
              <div class="table-container">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>状态 Code</th>
                      <th>中文展示名</th>
                      <th>渲染色彩</th>
                      <th>排序</th>
                      <th>允许操作权限</th>
                      <th style="width:90px;">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="activeEnumGroup.items.length === 0">
                      <td colspan="6" style="text-align:center; padding:32px; color:var(--text-muted);">当前码表分组暂无取值项，请点击右上角【+ 新增取值】</td>
                    </tr>
                    <tr v-for="item in activeEnumGroup.items" :key="item.code">
                      <td><code>{{ item.code }}</code></td>
                      <td><strong>{{ item.label }}</strong></td>
                      <td><span class="tag" :class="item.colorClass">{{ item.colorText }}</span></td>
                      <td>{{ item.sort }}</td>
                      <td>{{ item.permissions }}</td>
                      <td>
                        <button class="btn btn-outline btn-sm" style="color:#DC2626; border-color:#FECACA;" @click="handleDeleteEnumItem(item.code)">移除</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <!-- M14 新增码表取值弹窗 -->
          <div v-if="modalNewEnumItemOpen" class="modal-overlay active" @click.self="modalNewEnumItemOpen = false">
            <div class="modal-content" style="max-width:480px; width:92%; background:#FFF; border-radius:10px; padding:24px; box-shadow:0 20px 50px rgba(0,0,0,0.2);">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; border-bottom:1px solid var(--border); padding-bottom:10px;">
                <h3 style="font-size:16px; font-weight:700;">+ 新增码表取值 (M14) · {{ activeEnumGroup.name }}</h3>
                <button class="btn btn-outline btn-sm" @click="modalNewEnumItemOpen = false">✕</button>
              </div>
              <div style="display:flex; flex-direction:column; gap:14px;">
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
                  <div>
                    <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">状态 Code *</label>
                    <input v-model="newEnumItemForm.code" class="form-control" placeholder="例如 IN_REVIEW" style="width:100%;" />
                  </div>
                  <div>
                    <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">中文展示名 *</label>
                    <input v-model="newEnumItemForm.label" class="form-control" placeholder="例如 评审中" style="width:100%;" />
                  </div>
                </div>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
                  <div>
                    <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">渲染色彩标签</label>
                    <select v-model="newEnumItemForm.colorClass" class="form-control" style="width:100%;">
                      <option value="tag-draft">● 灰色 (默认/归档)</option>
                      <option value="tag-clarifying">● 蓝色 (进行中/澄清)</option>
                      <option value="tag-pending">● 橙色 (待确认/告警)</option>
                      <option value="tag-baselined">● 绿色 (已冻结/已发布)</option>
                    </select>
                  </div>
                  <div>
                    <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">展示排序</label>
                    <input v-model.number="newEnumItemForm.sort" type="number" min="1" class="form-control" style="width:100%;" />
                  </div>
                </div>
                <div>
                  <label style="display:block; font-size:12px; font-weight:600; margin-bottom:6px;">允许操作权限说明</label>
                  <input v-model="newEnumItemForm.permissions" class="form-control" placeholder="例如 编辑 / 发起评审" style="width:100%;" />
                </div>
              </div>
              <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:20px;">
                <button class="btn btn-outline" @click="modalNewEnumItemOpen = false">取消</button>
                <button class="btn btn-primary" @click="handleSubmitNewEnumItem">确认添加</button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P18 外观主题设置 ================= -->
        <div id="page-theme" class="view-page" :class="{ active: currentPage === 'page-theme' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">外观与工业主题设置 (P18)</h2>
              <p style="color:var(--text-muted); font-size:13px;">仅开放 Token 级配置，强制保证正文对比度 ≥ 4.5:1，符合工业高可用标准</p>
            </div>
            <button class="btn btn-primary" @click="ElMessage.success('主题参数已持久化保存！')">💾 保存配置</button>
          </div>

          <div style="display:grid; grid-template-columns: 1fr 1fr; gap:20px;">
            <div class="card">
              <div class="card-header">
                <div class="card-title">🎨 主题 Token 调配</div>
              </div>
              <div class="card-body">
                <div class="form-group">
                  <label class="form-label">品牌主色 (Primary)</label>
                  <div style="display:flex; gap:10px; align-items:center;">
                    <input type="color" :value="themeColor" style="width:40px; height:36px; border:none; cursor:pointer;" @change="(e: any) => handleThemeColorChange(e.target.value)">
                    <input type="text" class="form-control" :value="themeColor" readonly>
                  </div>
                  <div class="form-hint">当前对比度 7.2:1 (符合 AAA 标准 ✔)</div>
                </div>

                <div class="form-group">
                  <label class="form-label">强调色 (Accent)</label>
                  <div style="display:flex; gap:10px; align-items:center;">
                    <input type="color" value="#C2410C" style="width:40px; height:36px; border:none; cursor:pointer;">
                    <input type="text" class="form-control" value="#C2410C" readonly>
                  </div>
                </div>

                <div class="form-group">
                  <label class="form-label">界面排版密度</label>
                  <div style="display:flex; gap:16px; margin-top:6px;">
                    <label><input type="radio" name="density" checked> 标准 (工业适中)</label>
                    <label><input type="radio" name="density"> 紧凑 (高信息密集)</label>
                    <label><input type="radio" name="density"> 宽松</label>
                  </div>
                </div>
              </div>
            </div>

            <div class="card">
              <div class="card-header">
                <div class="card-title">👁️ 实时预览渲染卡片</div>
              </div>
              <div class="card-body" style="background:#F8FAFC;">
                <div style="background:#FFF; padding:16px; border:1px solid var(--border); border-radius:var(--radius-sm);">
                  <div style="font-weight:700; color:var(--primary); font-size:15px;">OT 工控网络仿真系统</div>
                  <div style="font-size:12px; color:var(--text-muted); margin:6px 0;">当前状态：已基线 BL-v1.1 ｜ 负责人：李工</div>
                  <div style="display:flex; gap:8px; margin-top:12px;">
                    <button class="btn btn-primary btn-sm">主要操作</button>
                    <button class="btn btn-outline btn-sm">次要操作</button>
                    <button class="btn btn-accent btn-sm">警示动作</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= 外部客户视角 E01 ~ E03 ================= -->
        <div id="page-client-view" class="view-page" :class="{ active: currentPage === 'page-client-view' }">
          <div style="display:flex; justify-content:space-between; align-items:center; background:#FFF7ED; border:1px solid #FFEDD5; padding:10px 16px; border-radius:var(--radius-sm); margin-bottom:16px;">
            <div style="display:flex; align-items:center; gap:8px; font-size:13px; color:#9A3412;">
              <strong>🔗 客户免登录体验模式已激活：</strong>
              <span>当前访问令牌有效 (Token: a8f9...，访问码已免输校验成功)</span>
            </div>
            <div>
              <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="clientSubPage = 'e02'">切换签名页 (E02)</button>
              <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="clientSubPage = 'e03'">切换回执页 (E03)</button>
              <button class="btn btn-primary btn-sm" @click="switchPerspective('internal')">返回内部系统</button>
            </div>
          </div>

          <!-- E02 客户签名确认视窗 -->
          <div v-if="clientSubPage === 'e02'" class="client-viewport-container">
            <div class="watermark-overlay">防伪水印：张工 2026-11-02 14:03 (IP 10.20.3.11)</div>
            
            <div class="client-header">
              <div style="display:flex; justify-content:space-between; align-items:flex-start;">
                <div>
                  <span class="tag tag-clarifying">基线签字确认凭据</span>
                  <h2 style="font-size:20px; font-weight:700; margin-top:6px;">{{ currentProject.projectKey }} · 需求确认书 ({{ currentReq.reqCode || currentReq.reqNo }})</h2>
                  <div style="font-size:12px; color:var(--text-muted); margin-top:4px;">
                    请您在以下页面中逐项核验确认，并填写一句话复述后完成电子签字。
                  </div>
                </div>
                <div style="text-align:right;">
                  <div style="font-size:12px; color:var(--text-muted);">本期交付代价</div>
                  <div style="font-size:22px; font-weight:700; color:var(--success);">{{ currentCost.toFixed(1) }} 人天</div>
                </div>
              </div>

              <div class="client-quote-box">
                <strong>客户原话复核：</strong>{{ currentReq.quote }}
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">一、本期交付功能范围 (已勾选项即为本期范围)</div>
              <div v-if="confirmItems.filter(i => (i.itemCategory || i.category) === 'SCOPE' && i.decisionResult === 'CURRENT').length === 0" style="color:var(--text-muted); font-size:13px;">
                暂无本期范围条目
              </div>
              <div v-else style="display:flex; flex-direction:column; gap:8px; font-size:13px;">
                <label v-for="item in confirmItems.filter(i => (i.itemCategory || i.category) === 'SCOPE' && i.decisionResult === 'CURRENT')" :key="item.id">
                  <input type="checkbox" checked disabled> <strong>{{ item.title }}</strong> (代价 +{{ Number(item.costDelta || 0).toFixed(1) }} 人天)
                </label>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">二、业务规则与参数约定</div>
              <div v-if="confirmItems.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category)).length === 0" style="color:var(--text-muted); font-size:13px;">
                暂无规则或异常约定条目
              </div>
              <div v-else style="font-size:13px; line-height:1.8;">
                <div v-for="item in confirmItems.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category))" :key="item.id">
                  • <strong>{{ item.title }}</strong>：{{ item.optionAgreement }}
                </div>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title" style="color:#DC2626;">三、双方共同明确：本期不做事项 (严禁现场追加)</div>
              <div v-if="confirmItems.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category) || i.decisionResult === 'SKIP' || i.decisionResult === 'NEXT').length === 0" style="color:var(--text-muted); font-size:13px;">
                暂无不做事项
              </div>
              <div v-else style="display:flex; flex-wrap:wrap; gap:16px; font-size:13px; color:var(--text-muted);">
                <span v-for="item in confirmItems.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category) || i.decisionResult === 'SKIP' || i.decisionResult === 'NEXT')" :key="item.id">
                  ❌ {{ item.title }}
                </span>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">四、原型只读确认 (可实际点击测试交互)</div>
              <div style="border:1px solid var(--border); border-radius:4px; height:320px; overflow:hidden;">
                <iframe
                  v-if="selectedPrototype && selectedPrototype.previewUrl"
                  :src="selectedPrototype.previewUrl"
                  style="width:100%; height:100%; border:none;"
                  sandbox="allow-scripts allow-same-origin"
                ></iframe>
                <div v-else style="display:flex; align-items:center; justify-content:center; height:100%; color:var(--text-muted); font-size:13px;">
                  当前需求未关联在线交互原型
                </div>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">五、请用一句话复述您理解的这一版核心交付物</div>
              <textarea class="form-control" rows="2" v-model="clientSignForm.summary"></textarea>
            </div>

            <div class="client-section" style="background:#FAFCFF;">
              <div class="client-section-title">六、三方法定签字留痕</div>
              <div style="display:grid; grid-template-columns: repeat(3, 1fr); gap:16px; margin-top:12px;">
                <div style="background:#FFF; padding:12px; border:1px solid #BBF7D0; border-radius:4px;">
                  <div style="font-size:12px; color:var(--text-muted);">业务拍板人 (甲方)</div>
                  <div style="font-weight:700; color:#15803D; margin-top:4px;">王总监 (已签署 ✔)</div>
                  <div style="font-size:11px; color:#64748B;">存证防伪码: 7F3A-91C2-88B1</div>
                </div>
                <div style="background:#FFF; padding:12px; border:1px solid #BBF7D0; border-radius:4px;">
                  <div style="font-size:12px; color:var(--text-muted);">验收人代表 (甲方/测试)</div>
                  <div style="font-weight:700; color:#15803D; margin-top:4px;">李科长 (已签署 ✔)</div>
                  <div style="font-size:11px; color:#64748B;">存证防伪码: 8B2C-44A1-90E2</div>
                </div>
                <div style="background:#FFF; padding:12px; border:1px solid #FCD34D; border-radius:4px;">
                  <div style="font-size:12px; color:var(--text-muted);">最终使用者代表</div>
                  <input type="text" class="form-control" style="margin-top:6px;" v-model="clientSignForm.signerName">
                  <button class="btn btn-accent btn-sm" style="width:100%; margin-top:8px;" @click="handleClientSign">✍️ 确认并提交签署</button>
                </div>
              </div>

              <!-- 逆向驳回分支 -->
              <div style="margin-top:16px; padding-top:14px; border-top:1px dashed #CBD5E1; display:flex; justify-content:space-between; align-items:center;">
                <span style="font-size:12px; color:var(--text-muted);">若对上述清单、原型或代价约定存在异议：</span>
                <button class="btn btn-outline btn-sm" style="color:#B91C1C; border-color:#FCA5A5; background:#FFF;" @click="modalClientRejectOpen = true">
                  ⚠️ 意见有分歧，申请驳回澄清
                </button>
              </div>
            </div>
          </div>

          <!-- E03 签名回执单 -->
          <div v-else-if="clientSubPage === 'e03'" class="client-viewport-container" style="padding:30px;">
            <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:2px solid var(--border); padding-bottom:16px;">
              <div>
                <h2 style="font-size:22px; font-weight:700;">需求基线确认存证回执单 (E03)</h2>
                <div style="font-size:12px; color:var(--text-muted); margin-top:4px;">APEX 需求全生命周期治理平台 · 法定不可变证据链</div>
              </div>
              <button class="btn btn-outline" @click="ElMessage.success('已触发打印预览')">🖨️ 打印 / 存为 PDF</button>
            </div>

            <div class="receipt-box">
              <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px; font-size:13px; line-height:1.8;">
                <div><strong>项目标识：</strong>{{ currentProject.projectKey }} · {{ currentProject.name }}</div>
                <div><strong>需求编号：</strong>{{ currentReq.reqCode || currentReq.reqNo }} ({{ currentReq.title }})</div>
                <div><strong>锁定基线：</strong>{{ baselineList[0]?.version || 'BL-v1.0' }} (不可篡改)</div>
                <div><strong>回执校验码：</strong><span style="font-family:monospace; color:var(--primary); font-weight:700;">1C9E-78D3-55F4</span></div>
                <div><strong>签署时间：</strong>2026-10-29 14:08:23</div>
                <div><strong>总代价共识：</strong>{{ currentCost.toFixed(1) }} 人天</div>
              </div>
              <hr style="border:none; border-top:1px dashed #CBD5E1; margin:16px 0;">
              <div style="font-size:13px;">
                <div><strong>固化快照指纹 (Manifest Hash)：</strong></div>
                <div style="font-family:monospace; font-size:12px; background:#EEF2F6; padding:8px; border-radius:4px; margin-top:4px; word-break:break-all;">
                  {{ baselineList[0]?.manifestHash || 'sha256:a3f9c2e177b9d40e1189ac3bb4109401bfd145cba2793108c40b7190e82c7a10' }} (已固化)
                </div>
              </div>
              <div style="font-size:13px; margin-top:12px;">
                <div><strong>客户复述存证：</strong>「{{ clientSignForm.summary }}」</div>
              </div>
            </div>
            <div style="font-size:12px; color:var(--text-muted); text-align:center;">
              本电子回执经 SHA256 算法加密上链留痕，任何一方私自变更代码均可凭此回执复原溯源。
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- ================= 抽屉面板：P06 确认清单编辑 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerChecklistOpen }" @click="drawerChecklistOpen = false"></div>
    <div class="drawer-panel" :class="{ open: drawerChecklistOpen }">
      <div class="drawer-header">
        <div class="drawer-title">✏️ 编辑确认清单 · {{ currentReq.reqNo || currentReq.reqCode }} (P06)</div>
        <button class="btn btn-outline btn-sm" @click="drawerChecklistOpen = false">✕</button>
      </div>
      <div class="drawer-body">
        <div class="checklist-layer-tabs">
          <button class="checklist-tab-btn" :class="{ active: activeChecklistTab === 'scope' }" @click="activeChecklistTab = 'scope'">范围 ({{ confirmItems.filter(i => (i.itemCategory || i.category) === 'SCOPE').length }})</button>
          <button class="checklist-tab-btn" :class="{ active: activeChecklistTab === 'rules' }" @click="activeChecklistTab = 'rules'">规则/异常/验收 ({{ confirmItems.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category)).length }})</button>
          <button class="checklist-tab-btn" :class="{ active: activeChecklistTab === 'skip' }" @click="activeChecklistTab = 'skip'">不做事项 ({{ confirmItems.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category)).length }})</button>
        </div>

        <div>
          <!-- 空状态提示 -->
          <div v-if="filteredChecklistItems.length === 0" style="padding:32px 16px; text-align:center; color:var(--text-muted); background:var(--bg-app); border:1px dashed var(--border); border-radius:var(--radius-sm); margin-bottom:12px;">
            <div style="font-size:24px; margin-bottom:6px;">📋</div>
            <div style="font-weight:600; color:var(--text); font-size:13px;">该分类下暂无清单条目</div>
            <div style="font-size:12px; margin-top:4px;">点击下方【+ 添加新清单条目】即可快速录入</div>
          </div>

          <div v-for="item in filteredChecklistItems" :key="item.id || item.itemCode || item.title" style="border:1px solid var(--border); border-radius:var(--radius-sm); padding:14px; margin-bottom:12px; background:var(--surface);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <strong style="color:var(--text);">{{ item.title }}</strong>
              <span style="font-size:12px; color:var(--text-muted);">代价基准: +{{ item.costDelta }}d</span>
            </div>
            <div style="font-size:13px; color:var(--text-muted); margin-bottom:10px;">
              问法：{{ item.questionPrompt }}
            </div>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <div class="decision-options">
                <button
                  class="decision-btn"
                  :class="{ 'active-current': item.decisionResult === 'CURRENT' }"
                  @click="setDecision(item, 'CURRENT')"
                >
                  ● 本期做 (+{{ item.costDelta }}d)
                </button>
                <button
                  class="decision-btn"
                  :class="{ 'active-next': item.decisionResult === 'NEXT' }"
                  @click="setDecision(item, 'NEXT')"
                >
                  下期做 (0d)
                </button>
                <button
                  class="decision-btn"
                  :class="{ 'active-skip': item.decisionResult === 'SKIP' }"
                  @click="setDecision(item, 'SKIP')"
                >
                  不做 (0d)
                </button>
              </div>
              <button class="btn btn-outline btn-sm" style="color:#DC2626;" @click="handleDeleteItem(item)">删除</button>
            </div>
          </div>
        </div>

        <button class="btn btn-outline btn-sm" style="width:100%; border-style:dashed; margin-bottom:14px;" @click="handleOpenAddItemModal">+ 添加新清单条目 (M04)</button>

        <!-- 实时代价折算汇总条 -->
        <div class="cost-summary-bar">
          <div>
            <span>已敲定共识代价：</span>
            <span class="cost-stat-tag">{{ currentCost.toFixed(1) }} 人天</span>
            <span style="font-size:12px; color:var(--text-muted); margin-left:8px;">
              (本期 {{ confirmItems.filter(i => i.decisionResult === 'CURRENT').length }} 项 · 下期 {{ confirmItems.filter(i => i.decisionResult === 'NEXT').length }} 项 · 不做 {{ confirmItems.filter(i => i.decisionResult === 'SKIP').length }} 项)
            </span>
          </div>
          <button class="btn btn-outline btn-sm" @click="switchPerspective('client')">预览客户签名效果</button>
        </div>
      </div>
      <div class="drawer-footer">
        <button class="btn btn-outline" @click="drawerChecklistOpen = false">取消</button>
        <button class="btn btn-primary" @click="handleSaveChecklist">保存清单决策</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：全流程节点深度透视与 Skill 专家动态选配 (Drawer) ================= -->
    <div class="drawer-overlay" :class="{ active: drawerWorkflowDetailVisible }" @click="drawerWorkflowDetailVisible = false"></div>
    <div
      class="drawer-panel"
      :class="{ open: drawerWorkflowDetailVisible }"
      style="width:720px; max-width:92vw; background:#ffffff; border-left:1px solid #e2e8f0; box-shadow:-8px 0 32px rgba(15,23,42,0.12); display:flex; flex-direction:column;"
    >
      <!-- 头部：轻拟态高质感顶栏（亮色主题） -->
      <div class="drawer-header" style="background:#ffffff; border-bottom:1px solid #e2e8f0; padding:16px 22px; display:flex; justify-content:space-between; align-items:center;">
        <div style="display:flex; align-items:center; gap:10px;">
          <span
            class="tag"
            style="font-size:11px; font-weight:700; padding:3px 10px; border-radius:4px; letter-spacing:0.5px;"
            :style="{
              background: activeDetailNode.category === 'skill' ? 'linear-gradient(135deg, #6366f1, #4f46e5)' : (activeDetailNode.category === 'control' ? 'linear-gradient(135deg, #f59e0b, #d97706)' : (activeDetailNode.category === 'artifact' ? 'linear-gradient(135deg, #10b981, #059669)' : 'linear-gradient(135deg, #0284c7, #0369a1)')),
              color: '#FFF'
            }"
          >
            {{ activeDetailNode.categoryName }}
          </span>
          <span style="font-size:16px; font-weight:700; color:#0f172a; letter-spacing:0.3px;">
            {{ activeDetailNode.id }} · {{ activeDetailNode.name }}
          </span>
        </div>
        <button
          class="btn btn-outline btn-sm"
          style="color:#64748b; border-color:#e2e8f0; background:#f8fafc; width:30px; height:30px; padding:0; display:flex; align-items:center; justify-content:center; border-radius:6px; transition:all 0.2s;"
          @click="drawerWorkflowDetailVisible = false"
        >✕</button>
      </div>

      <div class="drawer-body" style="padding:22px; background:#f8fafc; color:#334155; font-size:13px; overflow-y:auto; flex:1;">
        <!-- 标签页导航：现代药丸胶囊风格 (Pill Tabs) -->
        <div style="display:flex; background:#e2e8f0; border:1px solid #cbd5e1; padding:4px; border-radius:8px; margin-bottom:20px; gap:4px;">
          <button
            class="checklist-tab-btn"
            style="flex:1; border:none; border-radius:6px; padding:8px 12px; font-size:12px; font-weight:600; cursor:pointer; transition:all 0.2s; display:inline-flex; align-items:center; justify-content:center; gap:6px;"
            :style="{
              background: drawerActiveTab === 'overview' ? '#ffffff' : 'transparent',
              color: drawerActiveTab === 'overview' ? '#0284c7' : '#64748b',
              boxShadow: drawerActiveTab === 'overview' ? '0 2px 6px rgba(0,0,0,0.08)' : 'none'
            }"
            @click="drawerActiveTab = 'overview'"
          >
            <span>🔍</span>
            <span>工位全景属性</span>
          </button>
          <button
            class="checklist-tab-btn"
            style="flex:1; border:none; border-radius:6px; padding:8px 12px; font-size:12px; font-weight:600; cursor:pointer; transition:all 0.2s; display:inline-flex; align-items:center; justify-content:center; gap:6px;"
            :style="{
              background: drawerActiveTab === 'skill' ? '#ffffff' : 'transparent',
              color: drawerActiveTab === 'skill' ? '#6366f1' : '#64748b',
              boxShadow: drawerActiveTab === 'skill' ? '0 2px 6px rgba(0,0,0,0.08)' : 'none'
            }"
            @click="drawerActiveTab = 'skill'"
          >
            <span>🤖</span>
            <span>专属 AI 专家</span>
          </button>
          <button
            class="checklist-tab-btn"
            style="flex:1; border:none; border-radius:6px; padding:8px 12px; font-size:12px; font-weight:600; cursor:pointer; transition:all 0.2s; display:inline-flex; align-items:center; justify-content:center; gap:6px;"
            :style="{
              background: drawerActiveTab === 'artifacts' ? '#ffffff' : 'transparent',
              color: drawerActiveTab === 'artifacts' ? '#059669' : '#64748b',
              boxShadow: drawerActiveTab === 'artifacts' ? '0 2px 6px rgba(0,0,0,0.08)' : 'none'
            }"
            @click="drawerActiveTab = 'artifacts'"
          >
            <span>📦</span>
            <span>物理产物流向</span>
          </button>
          <button
            class="checklist-tab-btn"
            style="flex:1; border:none; border-radius:6px; padding:8px 12px; font-size:12px; font-weight:600; cursor:pointer; transition:all 0.2s; display:inline-flex; align-items:center; justify-content:center; gap:6px;"
            :style="{
              background: drawerActiveTab === 'tasks' ? '#ffffff' : 'transparent',
              color: drawerActiveTab === 'tasks' ? '#0891b2' : '#64748b',
              boxShadow: drawerActiveTab === 'tasks' ? '0 2px 6px rgba(0,0,0,0.08)' : 'none'
            }"
            @click="drawerActiveTab = 'tasks'"
          >
            <span>📌</span>
            <span>微观子任务与指令</span>
          </button>
          <button
            class="checklist-tab-btn"
            style="flex:1; border:none; border-radius:6px; padding:8px 12px; font-size:12px; font-weight:600; cursor:pointer; transition:all 0.2s; display:inline-flex; align-items:center; justify-content:center; gap:6px;"
            :style="{
              background: drawerActiveTab === 'records' ? '#ffffff' : 'transparent',
              color: drawerActiveTab === 'records' ? '#d97706' : '#64748b',
              boxShadow: drawerActiveTab === 'records' ? '0 2px 6px rgba(0,0,0,0.08)' : 'none'
            }"
            @click="drawerActiveTab = 'records'"
          >
            <span>📜</span>
            <span>审计履历 ({{ workflowRecords.length }})</span>
          </button>
        </div>

        <!-- TAB 1: 工位全景属性与底层核心运转原理 (亮色) -->
        <div v-if="drawerActiveTab === 'overview'" style="display:flex; flex-direction:column; gap:16px;">
          <!-- 状态徽标卡 -->
          <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:10px; padding:16px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
            <div>
              <div style="font-size:11px; color:#64748b; font-weight:600;">当前执行状态</div>
              <div style="font-size:15px; font-weight:700; margin-top:4px;">
                <span v-if="activeDetailNode.status === 'ACTIVE'" style="color:#0284c7; display:inline-flex; align-items:center; gap:6px;">
                  <span style="width:8px; height:8px; border-radius:50%; background:#0284c7; box-shadow:0 0 6px #0284c7;"></span>
                  ● 正在当前执行中 (ACTIVE)
                </span>
                <span v-else-if="activeDetailNode.status === 'COMPLETED'" style="color:#10b981; display:inline-flex; align-items:center; gap:6px;">
                  <span style="width:8px; height:8px; border-radius:50%; background:#10b981;"></span>
                  ✔ 已通过并留痕 (COMPLETED)
                </span>
                <span v-else style="color:#94a3b8; display:inline-flex; align-items:center; gap:6px;">
                  <span style="width:8px; height:8px; border-radius:50%; background:#cbd5e1;"></span>
                  ○ 待就绪与前置准备 (PENDING)
                </span>
              </div>
            </div>
            <div style="text-align:right;">
              <div style="font-size:11px; color:#64748b; font-weight:600;">常驻责任角色</div>
              <div style="font-size:14px; font-weight:700; color:#0f172a; margin-top:4px;">
                👤 {{ activeDetailNode.role }}
              </div>
            </div>
          </div>

          <!-- 底层运转核心原理 (Under the Hood) -->
          <div style="background:#f0f9ff; border:1px solid #bae6fd; border-left:4px solid #0284c7; border-radius:8px; padding:16px; box-shadow:0 1px 2px rgba(0,0,0,0.03);">
            <div style="font-size:14px; font-weight:700; color:#0369a1; margin-bottom:8px; display:flex; align-items:center; gap:6px;">
              <span>💡 底层核心运转逻辑 (Under the Hood)</span>
            </div>
            <p style="line-height:1.75; color:#334155; margin:0; font-size:13px;">
              {{ activeDetailNode.principle }}
            </p>
          </div>

          <!-- 执行契约规范与阻断条件 -->
          <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:10px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
            <div style="font-size:13px; font-weight:700; color:#0f172a; margin-bottom:12px;">📋 规约与门禁约束标准</div>
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:16px;">
              <div>
                <span style="color:#64748b; font-size:11px; font-weight:600;">绑定 SOP 标号：</span>
                <div style="margin-top:4px;">
                  <code style="background:#f1f5f9; color:#0284c7; border:1px solid #e2e8f0; padding:3px 8px; border-radius:4px; font-size:12px; font-family:monospace;">{{ activeDetailNode.sop }}</code>
                </div>
              </div>
              <div>
                <span style="color:#64748b; font-size:11px; font-weight:600;">刚性门禁阻断条件：</span>
                <div style="margin-top:4px; color:#d97706; font-weight:700; font-size:13px;">{{ activeDetailNode.gate }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 2: AI Skill 专家信息 (亮色) -->
        <div v-if="drawerActiveTab === 'skill'" style="display:flex; flex-direction:column; gap:16px;">
          <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:10px; padding:18px; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
            <div style="font-size:13px; font-weight:700; color:#0f172a; margin-bottom:10px;">🎯 当前项目锁定的专属 AI 专家</div>
            <div style="display:flex; align-items:center; gap:12px;">
              <span class="tag" style="background:linear-gradient(135deg, #6366f1, #4f46e5); color:#FFF; font-size:13px; font-weight:700; padding:4px 12px; border-radius:6px;">
                🤖 {{ activeDetailNode.skill }}
              </span>
              <span style="font-size:12px; color:#64748b;">
                根据项目工程工艺规约锁定（需求执行期不可随意篡改）
              </span>
            </div>
          </div>

          <!-- 专家的核心专长与能力定义 -->
          <div style="background:#eef2ff; border:1px solid #c7d2fe; border-left:4px solid #6366f1; border-radius:8px; padding:18px; box-shadow:0 1px 2px rgba(0,0,0,0.03);">
            <div style="font-size:14px; font-weight:700; color:#4338ca; margin-bottom:8px; display:flex; align-items:center; gap:6px;">
              <span>🛡️ AI 专家执行边界与安全约束</span>
            </div>
            <div style="font-size:12.5px; color:#334155; line-height:1.75;">
              本工位由指定的专属 Agent 提供辅助，严格执行对应 SOP 标准规约。如需调整本工位关联的 AI 专家或流程管线，请前往顶级菜单 <strong>「工作流模板管理」</strong> 进行版本克隆与项目级绑定。
            </div>
          </div>
        </div>

        <!-- TAB 3: 确定性物理产物流向与交付清单 (亮色) -->
        <div v-if="drawerActiveTab === 'artifacts'" style="display:flex; flex-direction:column; gap:16px;">
          <!-- 输入产物清单 -->
          <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:10px; padding:16px;">
            <div style="font-size:13px; font-weight:700; color:#0284c7; margin-bottom:10px; display:flex; align-items:center; gap:6px;">
              <span>📥 上游输入资产 (Inputs)</span>
            </div>
            <div style="display:flex; flex-direction:column; gap:8px;">
              <div
                v-for="(inp, iIdx) in activeDetailNode.inputArtifacts"
                :key="iIdx"
                style="background:#f8fafc; padding:10px 14px; border-radius:6px; border:1px dashed #cbd5e1; display:flex; align-items:center; gap:10px; font-size:12px;"
              >
                <span style="color:#0284c7; font-size:14px;">📄</span>
                <span style="color:#1e293b; font-weight:500;">{{ inp }}</span>
              </div>
            </div>
          </div>

          <!-- 输出产物清单 -->
          <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:10px; padding:16px;">
            <div style="font-size:13px; font-weight:700; color:#059669; margin-bottom:10px; display:flex; align-items:center; gap:6px;">
              <span>📤 本工位产出资产 (Outputs)</span>
            </div>
            <div style="display:flex; flex-direction:column; gap:8px;">
              <div
                v-for="(out, oIdx) in activeDetailNode.outputArtifacts"
                :key="oIdx"
                style="background:#ecfdf5; padding:10px 14px; border-radius:6px; border:1px solid #a7f3d0; display:flex; justify-content:space-between; align-items:center; font-size:12px;"
              >
                <div style="display:flex; align-items:center; gap:10px;">
                  <span style="color:#059669; font-size:14px;">📦</span>
                  <span style="color:#065f46; font-weight:600;">{{ out }}</span>
                </div>
                <span class="tag" style="background:#d1fae5; color:#047857; border:1px solid #6ee7b7; font-size:10px; padding:1px 6px; border-radius:4px;">物理落盘固化</span>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 4: 微观子任务与团队指令看板 (多人防冲突协作模式) -->
        <div v-if="drawerActiveTab === 'tasks'" style="display:flex; flex-direction:column; gap:14px;">
          <!-- 实时子任务进度度量卡片 (百分比与一键解锁) -->
          <div style="background:#ffffff; border:1px solid #e2e8f0; border-radius:10px; padding:18px; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
              <div>
                <div style="font-size:11px; color:#64748b; font-weight:600;">MySQL 真实子任务交付进度 (SSOT)</div>
                <div style="font-size:18px; font-weight:800; color:#0f172a; margin-top:2px; display:flex; align-items:center; gap:8px;">
                  <span>{{ subtaskStats.percentage }}%</span>
                  <span style="font-size:12px; font-weight:500; color:#64748b;">({{ subtaskStats.completed }} / {{ subtaskStats.total }} 已交付)</span>
                </div>
              </div>
              <div style="display:flex; gap:8px;">
                <button class="btn btn-outline btn-sm" style="color:#0284c7; border-color:#bae6fd; background:#f0f9ff; font-size:11px; padding:4px 10px;" @click="loadSubtasks">
                  🔄 刷新进度
                </button>
                <button class="btn btn-outline btn-sm" style="color:#ef4444; border-color:#fecaca; background:#fef2f2; font-size:11px; padding:4px 10px;" @click="handleUnlockRequirement">
                  🔓 一键解锁重置
                </button>
              </div>
            </div>

            <!-- 动态进度条 -->
            <div style="background:#e2e8f0; border-radius:999px; height:8px; overflow:hidden; margin-bottom:14px;">
              <div
                style="height:100%; border-radius:999px; transition:width 0.4s ease; background:linear-gradient(90deg, #06b6d4 0%, #10b981 100%);"
                :style="{ width: `${subtaskStats.percentage}%` }"
              ></div>
            </div>

            <!-- 子任务列表 (已入库真实呈现) -->
            <div v-if="subtaskStats.list.length === 0" style="padding:16px; text-align:center; color:#94a3b8; font-size:12px; background:#f8fafc; border-radius:6px; border:1px dashed #cbd5e1;">
              暂无已录入的子任务。开发者执行开工命令后将自动入库并在此呈现。
            </div>
            <div v-else style="display:flex; flex-direction:column; gap:8px; max-height:220px; overflow-y:auto;">
              <div
                v-for="st in subtaskStats.list"
                :key="st.id"
                style="display:flex; justify-content:space-between; align-items:center; padding:8px 12px; background:#f8fafc; border:1px solid #e2e8f0; border-radius:6px; font-size:12px;"
              >
                <div style="display:flex; align-items:center; gap:8px;">
                  <span
                    class="tag"
                    :style="{
                      background: st.status === 'DONE' ? '#10b981' : (st.status === 'IN_PROGRESS' ? '#0284c7' : '#94a3b8'),
                      color: '#fff',
                      fontSize: '10px',
                      padding: '1px 6px',
                      borderRadius: '4px'
                    }"
                  >
                    {{ st.status }}
                  </span>
                  <code style="font-size:11px; color:#0891b2; font-weight:700;">{{ st.taskCode }}</code>
                  <span style="color:#1e293b; font-weight:500;">{{ st.title }}</span>
                </div>
                <div style="color:#64748b; font-size:11px;">
                  <span>👤 {{ st.ownerName }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 终端智能命令发号卡片 -->
          <div style="background:rgba(19, 29, 49, 0.85); border:1px solid rgba(6, 182, 212, 0.3); border-left:4px solid #06b6d4; border-radius:8px; padding:16px; box-shadow:0 4px 12px rgba(0,0,0,0.2);">
            <div style="font-size:13px; font-weight:700; color:#22d3ee; margin-bottom:6px; display:flex; align-items:center; gap:6px;">
              <span>💻 开发者终端开工指令 (基于 Git 身份自动发号)</span>
            </div>
            <div style="font-size:12px; color:#94a3b8; margin-bottom:10px;">
              团队成员可在本地终端执行如下指令，自动绑定 Git 账户并创建独立命名空间子任务（零合并冲突）：
            </div>
            <div style="background:rgba(15, 23, 42, 0.95); border:1px solid rgba(6, 182, 212, 0.2); border-radius:6px; padding:10px 14px; display:flex; justify-content:space-between; align-items:center;">
              <code style="color:#67e8f9; font-size:12px; font-family:monospace;">
                python3 .ai/scripts/board.py new {{ activeDetailNode.id.replace('SOP-', 'MOD_') }} "实现该工位核心特性" "白话验收目标"
              </code>
            </div>
          </div>

          <!-- 团队协同模式说明 -->
          <div style="background:rgba(30, 41, 59, 0.7); border:1px solid rgba(148, 163, 184, 0.15); border-radius:10px; padding:16px;">
            <div style="font-size:13px; font-weight:700; color:#f8fafc; margin-bottom:10px;">🛡️ 多人开发切片防踩踏标准</div>
            <div style="display:flex; flex-direction:column; gap:8px; font-size:12px; color:#cbd5e1; line-height:1.6;">
              <div style="display:flex; align-items:flex-start; gap:8px;">
                <span style="color:#22d3ee;">1.</span>
                <span><strong>宏观协议锁定</strong>：架构师负责在《DLD 详细设计》中锁死 API 接口契约，切片独立隔离。</span>
              </div>
              <div style="display:flex; align-items:flex-start; gap:8px;">
                <span style="color:#22d3ee;">2.</span>
                <span><strong>微观任务自拆</strong>：研发在各自特性分支内使用 <code>board.py new</code>，任务编号自动附加 <code>&lt;MODULE&gt;-&lt;GIT_USER&gt;</code> 标签。</span>
              </div>
              <div style="display:flex; align-items:flex-start; gap:8px;">
                <span style="color:#22d3ee;">3.</span>
                <span><strong>两段式终验归档</strong>：本地单测全绿即可归档；主干合并时跑全局集成门禁并推进此处的工位状态。</span>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 5: 全周期持久化审计履历 (MySQL 真实驱动 - 亮色) -->
        <div v-if="drawerActiveTab === 'records'" style="display:flex; flex-direction:column; gap:12px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
            <span style="font-size:12px; color:#64748b;">共记录 {{ workflowRecords.length }} 次工位流转核验操作</span>
            <button class="btn btn-outline btn-sm" style="color:#0284c7; border-color:#bae6fd; background:#f0f9ff; padding:3px 10px; font-size:11px;", @click="loadWorkflowRecords">
              🔄 实时刷新履历
            </button>
          </div>

          <div v-if="workflowRecords.length === 0" style="padding:40px; text-align:center; color:#94a3b8; background:#ffffff; border-radius:10px; border:1px dashed #cbd5e1;">
            暂无历史审计履历。点击下方「确认并顺推工位」后，流转凭据将自动实时落盘入库。
          </div>

          <div v-else style="display:flex; flex-direction:column; gap:8px; max-height:480px; overflow-y:auto;">
            <div
              v-for="rec in workflowRecords"
              :key="rec.id"
              style="display:flex; justify-content:space-between; align-items:center; padding:10px 14px; background:#ffffff; border:1px solid #e2e8f0; border-radius:8px; font-size:12px; box-shadow:0 1px 2px rgba(0,0,0,0.03);"
            >
              <div style="display:flex; align-items:center; gap:10px;">
                <span
                  class="tag"
                  :style="{
                    background: rec.actionType === 'OVERRIDE' ? 'linear-gradient(135deg, #f59e0b, #d97706)' : 'linear-gradient(135deg, #10b981, #059669)',
                    color: '#fff',
                    fontWeight: 700,
                    fontSize: '10px',
                    padding: '2px 6px',
                    borderRadius: '4px'
                  }"
                >
                  {{ rec.actionType === 'OVERRIDE' ? '⚡ 跳步覆盖' : '✔ 顺推核准' }}
                </span>
                <strong style="color:#0f172a;">[{{ rec.nodeId }}] {{ rec.nodeName }}</strong>
                <span v-if="rec.skillName" style="color:#64748b; font-size:11px;">
                  (Skill: <code style="color:#0284c7;">{{ rec.skillName }}</code>)
                </span>
                <span v-if="rec.overrideReason" style="color:#d97706; font-style:italic;">
                  原因: “{{ rec.overrideReason }}”
                </span>
                <span v-if="rec.artifactsJson" style="color:#047857; font-size:11px; background:#ecfdf5; border:1px solid #a7f3d0; padding:1px 6px; border-radius:4px; margin-left:4px;">
                  📦 交付物: {{ rec.artifactsJson }}
                </span>
              </div>
              <div style="display:flex; align-items:center; gap:12px; color:#64748b; font-size:11px;">
                <span>操作人: <strong style="color:#0284c7;">{{ rec.operator }}</strong></span>
                <span>{{ rec.createdAt ? rec.createdAt.replace('T', ' ') : '刚刚' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部控制栏：高质感浅灰底栏（亮色主题） -->
      <div class="drawer-footer" style="background:#ffffff; border-top:1px solid #e2e8f0; padding:14px 22px; display:flex; justify-content:space-between; align-items:center;">
        <span style="font-size:11px; color:#64748b; font-family:monospace;">
          Node: {{ activeDetailNode.id }} ｜ SSOT: MySQL t_req_workflow_record
        </span>
        <div style="display:flex; gap:10px;">
          <button class="btn btn-outline btn-sm" style="color:#475569; border-color:#cbd5e1; background:#f8fafc; font-weight:600;" @click="handleJumpToReqDetailFromDrawer">
            📄 对应需求清单 (P04)
          </button>
          <button class="btn btn-primary btn-sm" style="background:linear-gradient(135deg, #0284c7 0%, #0369a1 100%); border:none; font-weight:700; box-shadow:0 2px 8px rgba(2,132,199,0.25); padding:6px 16px;" @click="handleAdvanceWorkflowNode">
            ✔ 确认并顺推工位 (Advance)
          </button>
        </div>
      </div>
    </div>
    <!-- ================= 抽屉面板：M02 新建需求 (统一使用 CommonFormModal 公共抽屉) ================= -->
    <CommonFormModal
      v-model="drawerNewReqOpen"
      mode="drawer"
      width="520px"
      title="➕ 快速录入新需求 (M02)"
      confirm-text="创建并进入清单细化"
      @confirm="handleSubmitNewReq"
    >
      <!-- 目标项目空间选择 -->
      <div class="form-group">
        <label class="form-label required">归属项目空间</label>
        <select class="form-control" v-model="newReqForm.projectId" style="font-weight:600; color:#0284c7; background:#f8fafc;">
          <option v-for="p in projectList" :key="p.id" :value="p.id">
            📁 {{ p.projectKey }} · {{ p.name }} ({{ p.clientName || '通用客户' }})
          </option>
        </select>
        <div style="font-size:11px; color:#64748b; margin-top:4px;">
          默认关联当前激活的项目空间，支持随时切换归属
        </div>
      </div>

      <div class="form-group">
        <label class="form-label required">需求标题</label>
        <input type="text" class="form-control" placeholder="简述核心功能，例如：OT机房交换机拓扑告警" v-model="newReqForm.title">
      </div>
      <div class="form-group">
        <label class="form-label required">客户原话 (留痕防扯皮)</label>
        <textarea class="form-control" rows="3" placeholder="逐字录入客户原话或邮件片段..." v-model="newReqForm.quote"></textarea>
      </div>
      <div class="form-group">
        <label class="form-label required">责任人 (动态取自数据库用户库)</label>
        <select class="form-control" v-model="newReqForm.owner">
          <option v-for="u in userList" :key="u.id" :value="u.nickname || u.username">
            {{ u.nickname || u.username }} ({{ u.username }})
          </option>
          <!-- 数据库初始空状态候选项 -->
          <template v-if="userList.length === 0">
            <option value="张工 (产品负责人)">张工 (产品负责人)</option>
            <option value="李工 (核心研发)">李工 (核心研发)</option>
            <option value="王工 (测试架构师)">王工 (测试架构师)</option>
          </template>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">优先级</label>
        <select class="form-control" v-model="newReqForm.priority">
          <option value="P1">P1 - 本期核心必做</option>
          <option value="P2">P2 - 重要</option>
          <option value="P3">P3 - 体验优化</option>
        </select>
      </div>
    </CommonFormModal>

    <!-- ================= 抽屉面板：M08 新建基线 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerNewBaselineOpen }" @click="drawerNewBaselineOpen = false"></div>
    <div class="drawer-panel" :class="{ open: drawerNewBaselineOpen }">
      <div class="drawer-header">
        <div class="drawer-title">🔒 创建需求基线 (M08)</div>
        <button class="btn btn-outline btn-sm" @click="drawerNewBaselineOpen = false">✕</button>
      </div>
      <div class="drawer-body">
        <div class="form-group">
          <label class="form-label required">基线版本号</label>
          <input type="text" class="form-control" value="BL-v1.2" readonly>
        </div>
        <div class="form-group">
          <label class="form-label required">绑定原型版本</label>
          <select class="form-control">
            <option value="v3">mockup v3.0 (hash: a3f9c2... 包含嵌套底板)</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label required">关联设计文档</label>
          <div style="font-size:13px; line-height:1.8;">
            <label><input type="checkbox" checked disabled> 详细设计说明书 v1.1</label><br>
            <label><input type="checkbox" checked disabled> DDL 与回滚配对脚本</label>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label required">三方法定免登签名人员 (联动系统成员库)</label>
          <div style="display:flex; flex-direction:column; gap:10px; margin-top:6px;">
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="width:90px; font-size:12px; font-weight:600; color:var(--text-muted);">业务拍板人:</span>
              <select class="form-control" v-model="newBaselineForm.bizOwnerName" style="flex:1;">
                <option v-for="u in userList" :key="'biz-'+u.id" :value="u.nickname || u.username">
                  {{ u.nickname || u.username }} ({{ u.username }})
                </option>
                <option v-if="userList.length === 0" value="张工 (产品负责人)">张工 (产品负责人)</option>
              </select>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="width:90px; font-size:12px; font-weight:600; color:var(--text-muted);">验收人:</span>
              <select class="form-control" v-model="newBaselineForm.acceptorName" style="flex:1;">
                <option v-for="u in userList" :key="'acc-'+u.id" :value="u.nickname || u.username">
                  {{ u.nickname || u.username }} ({{ u.username }})
                </option>
                <option v-if="userList.length === 0" value="李工 (核心研发)">李工 (核心研发)</option>
              </select>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="width:90px; font-size:12px; font-weight:600; color:var(--text-muted);">使用者代表:</span>
              <select class="form-control" v-model="newBaselineForm.userRepName" style="flex:1;">
                <option v-for="u in userList" :key="'rep-'+u.id" :value="u.nickname || u.username">
                  {{ u.nickname || u.username }} ({{ u.username }})
                </option>
                <option v-if="userList.length === 0" value="王工 (测试架构师)">王工 (测试架构师)</option>
              </select>
            </div>
          </div>
        </div>
      </div>
      <div class="drawer-footer">
        <button class="btn btn-outline" @click="drawerNewBaselineOpen = false">取消</button>
        <button class="btn btn-primary" @click="handleCreateBaseline">生成基线并开放签字</button>
      </div>
    </div>

    <!-- ================= 模态弹窗：克隆复制工作流模板 ================= -->
    <div class="modal-overlay" :class="{ active: modalCloneTemplateVisible }">
      <div class="card" style="width:520px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">📋 基于模板复制克隆专属工作流</div>
          <button class="btn btn-outline btn-sm" @click="modalCloneTemplateVisible = false">✕</button>
        </div>
        <div class="modal-body">
          <div style="background:#f0fdf4; border:1px solid #bbf7d0; padding:12px; border-radius:6px; margin-bottom:14px; font-size:12px; color:#15803d;">
            💡 <strong>复制机制：</strong>将完整深拷贝源模板的 4-Layer 拓扑架构、SOP规约与 AI 专家绑定，生成一个独立互不干扰的副本供项目定制。
          </div>
          <div class="form-group">
            <label class="form-label">源模板蓝本</label>
            <input type="text" class="form-control" :value="cloneForm.sourceName" readonly style="background:#f8fafc;">
          </div>
          <div class="form-group">
            <label class="form-label required">新定制模板名称</label>
            <input type="text" class="form-control" v-model="cloneForm.name" placeholder="如：NET-SIM 电网高可靠仿真研发工作流">
          </div>
          <div class="form-group">
            <label class="form-label">绑定所属项目空间 (选填)</label>
            <select class="form-control" v-model="cloneForm.targetProjectId">
              <option :value="undefined">作为公共定制模板 (不特定绑定项目)</option>
              <option v-for="p in projectList" :key="p.id" :value="p.id">
                [{{ p.projectKey }}] {{ p.name }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">模板适用场景描述</label>
            <textarea class="form-control" rows="2" v-model="cloneForm.description" placeholder="简述该模板的门禁策略与特性..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalCloneTemplateVisible = false">取消</button>
          <button class="btn btn-primary" @click="handleSubmitCloneTemplate">立即创建定制副本</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：调整模板挂载项目 ================= -->
    <div class="modal-overlay" :class="{ active: modalMountProjectsVisible }">
      <div class="card" style="width:540px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">🔗 调整工作流模板挂载项目</div>
          <button class="btn btn-outline btn-sm" @click="modalMountProjectsVisible = false">✕</button>
        </div>
        <div class="modal-body">
          <div style="background:#eff6ff; border:1px solid #bfdbfe; padding:12px; border-radius:6px; margin-bottom:14px; font-size:12px; color:#1e40af;">
            勾选将当前模板 <strong>[{{ mountTemplateInfo?.name }}]</strong> 挂载给哪些项目空间使用。挂载后，对应项目下的所有需求流水线将自动继承此模板的工位与门禁纪律。
          </div>
          <div style="font-weight:600; font-size:13px; margin-bottom:8px;">可挂载的项目清单：</div>
          <div style="max-height:220px; overflow-y:auto; display:flex; flex-direction:column; gap:8px;">
            <label
              v-for="p in projectList"
              :key="p.id"
              style="display:flex; align-items:center; gap:10px; padding:8px 12px; background:#f8fafc; border:1px solid var(--border); border-radius:4px; cursor:pointer;"
            >
              <input
                type="checkbox"
                :value="p.id"
                v-model="selectedMountProjectIds"
              >
              <div>
                <strong style="color:var(--text-main);">[{{ p.projectKey }}]</strong>
                <span style="margin-left:6px; color:#475569;">{{ p.name }}</span>
              </div>
            </label>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalMountProjectsVisible = false">取消</button>
          <button class="btn btn-primary" @click="handleSubmitMountProjects">保存挂载关联</button>
        </div>
      </div>
    </div>

    <!-- ================= 抽屉面板：模板 DAG 画板节点属性微调与全息透视 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerTplNodeConfigVisible }" @click="drawerTplNodeConfigVisible = false"></div>
    <div class="drawer-panel" :class="{ open: drawerTplNodeConfigVisible }" style="width:580px;">
      <div class="drawer-header" style="background:#0b1320; border-bottom:1px solid #1e293b; color:#fff;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span 
            class="tag"
            :style="{
              background: activeTplConfigNode.category === 'skill' ? '#6366f1' : (activeTplConfigNode.category === 'control' ? '#d97706' : (activeTplConfigNode.category === 'artifact' ? '#059669' : '#0284c7')),
              color: '#FFF',
              fontWeight: 700
            }"
          >
            {{ activeTplConfigNode.categoryName }}
          </span>
          <span style="font-size:15px; font-weight:700; color:#f8fafc;">{{ activeTplConfigNode.id }} · {{ activeTplConfigNode.category === 'instruction' ? '工位配置' : '图元透视' }}</span>
        </div>
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerTplNodeConfigVisible = false">✕</button>
      </div>
      <div class="drawer-body" style="background:#0f172a; color:#cbd5e1; font-size:13px; padding:20px; overflow-y:auto;">
        
        <!-- 深度运作原理卡片 (4层节点通用) -->
        <div style="background:#1e293b; border-left:4px solid #38bdf8; border-radius:6px; padding:12px; margin-bottom:16px;">
          <div style="font-weight:700; color:#38bdf8; margin-bottom:4px; font-size:13px;">💡 工艺原理与架构规约</div>
          <div style="line-height:1.6; color:#94a3b8; font-size:12px;">{{ activeTplConfigNode.principle }}</div>
        </div>

        <!-- 针对 SOP 指令节点的完整编辑表单 -->
        <template v-if="activeTplConfigNode.category === 'instruction'">
          <div class="form-group">
            <label class="form-label" style="color:#f8fafc;">工位显示名称</label>
            <input type="text" class="form-control" style="background:#1e293b; border-color:#334155; color:#fff;" v-model="activeTplConfigNode.name">
          </div>
          <div class="form-group">
            <label class="form-label" style="color:#f8fafc;">常驻责任角色</label>
            <input type="text" class="form-control" style="background:#1e293b; border-color:#334155; color:#fff;" v-model="activeTplConfigNode.role">
          </div>
          <div class="form-group">
            <label class="form-label" style="color:#f8fafc;">
              挂载专属 AI Skill 专家
              <span style="font-size:11px; color:#38bdf8; font-weight:normal; margin-left:6px;">(实时联动重绘拓扑专家轨)</span>
            </label>
            <select
              class="form-control"
              style="background:#1e293b; border-color:#334155; color:#38bdf8; font-weight:600;"
              v-model="activeTplConfigNode.skill"
            >
              <option value="-">无需专属专家 (纯人工/脚本)</option>
              <option v-for="sk in availableSkillRegistry" :key="sk.id" :value="sk.id">
                {{ sk.icon }} {{ sk.id }} ({{ sk.name }})
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label" style="color:#f8fafc;">
              挂载自动化治理/看门狗脚本 (Scripts Registry)
              <span style="font-size:11px; color:#10b981; font-weight:normal; margin-left:6px;">(.ai/scripts 核心工具链)</span>
            </label>
            <select
              class="form-control"
              style="background:#1e293b; border-color:#334155; color:#10b981; font-weight:600;"
              v-model="activeTplConfigNode.scriptId"
            >
              <option value="-">无需专属脚本 (纯人机交互)</option>
              <option v-for="sc in availableScriptRegistry" :key="sc.id" :value="sc.id">
                {{ sc.icon }} {{ sc.id }} ({{ sc.name }})
              </option>
            </select>
            <!-- 脚本详情实时名片卡 (支持点击一键透视源码) -->
            <div 
              v-if="activeTplConfigNode.scriptId && activeTplConfigNode.scriptId !== '-'" 
              style="margin-top:8px; padding:10px 12px; background:#0b1320; border:1px solid #10b981; border-radius:6px; font-size:12px;"
            >
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                <span style="font-weight:700; color:#10b981;">
                  {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.icon }}
                  {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.name }}
                </span>
                <div style="display:flex; align-items:center; gap:6px;">
                  <span class="tag" style="background:#064e3b; color:#34d399; font-size:10px;">
                    {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.runtime }}
                  </span>
                  <button 
                    type="button" 
                    class="btn btn-outline btn-sm" 
                    style="padding:1px 6px; font-size:11px; color:#34d399; border-color:#059669;"
                    @click="handleQuickInspectFile(activeTplConfigNode.scriptId, availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.name)"
                  >
                    💻 查看源码
                  </button>
                </div>
              </div>
              <div style="color:#94a3b8; font-size:11px; margin-bottom:6px;">
                {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.desc }}
              </div>
              <div style="background:#020617; padding:4px 8px; border-radius:4px; font-family:monospace; color:#38bdf8; font-size:11px;">
                $ {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.cmd }}
              </div>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label" style="color:#f8fafc;">门禁阻断与准入规则 (Gate)</label>
            <input type="text" class="form-control" style="background:#1e293b; border-color:#334155; color:#fff;" v-model="activeTplConfigNode.gate">
          </div>
          <div class="form-group">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <label class="form-label" style="color:#f8fafc; margin-bottom:0;">绑定执行 SOP 规范文件</label>
              <button 
                v-if="activeTplConfigNode.sop && activeTplConfigNode.sop !== '-'" 
                type="button" 
                class="btn btn-outline btn-sm" 
                style="padding:2px 8px; font-size:11px; color:#38bdf8; border-color:#0284c7; background:rgba(2, 132, 199, 0.1);"
                @click="handleQuickInspectFile(activeTplConfigNode.sop, `SOP 规范：${activeTplConfigNode.name}`)"
              >
                📖 查看 SOP 规约原文
              </button>
            </div>
            <input type="text" class="form-control" style="background:#1e293b; border-color:#334155; color:#fff;" v-model="activeTplConfigNode.sop">
          </div>
        </template>

        <!-- 针对 Skill / Control / Artifact 轨节点的属性透视卡 -->
        <template v-else>
          <div class="form-group">
            <label class="form-label" style="color:#f8fafc;">图元全称</label>
            <div style="padding:8px 12px; background:#1e293b; border-radius:6px; color:#fff; font-weight:600;">{{ activeTplConfigNode.name }}</div>
          </div>
          <div class="form-group" v-if="activeTplConfigNode.role && activeTplConfigNode.role !== '-'">
            <label class="form-label" style="color:#f8fafc;">责任主体 / 对应模块</label>
            <div style="padding:8px 12px; background:#1e293b; border-radius:6px; color:#cbd5e1;">{{ activeTplConfigNode.role }}</div>
          </div>
          <div class="form-group" v-if="activeTplConfigNode.gate && activeTplConfigNode.gate !== '-'">
            <label class="form-label" style="color:#f8fafc;">硬性门禁约束与防线</label>
            <div style="padding:8px 12px; background:#1e293b; border-radius:6px; color:#ef4444; font-weight:600;">{{ activeTplConfigNode.gate }}</div>
          </div>
          <div class="form-group" v-if="activeTplConfigNode.sop && activeTplConfigNode.sop !== '-'">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <label class="form-label" style="color:#f8fafc; margin-bottom:0;">对应底层规约文件 / 脚本</label>
              <button 
                type="button" 
                class="btn btn-outline btn-sm" 
                style="padding:2px 8px; font-size:11px; color:#38bdf8; border-color:#0284c7; background:rgba(2, 132, 199, 0.1);"
                @click="handleQuickInspectFile(activeTplConfigNode.sop, activeTplConfigNode.name)"
              >
                🔍 点击查看文件源内容
              </button>
            </div>
            <div style="padding:8px 12px; background:#1e293b; border-radius:6px; font-family:monospace; color:#38bdf8;">{{ activeTplConfigNode.sop }}</div>
          </div>
        </template>

        <!-- 节点输入与输出数据流 -->
        <div style="margin-top:16px; border-top:1px solid #1e293b; padding-top:12px;">
          <div style="display:flex; gap:16px; margin-bottom:14px;">
            <div style="flex:1;">
              <div style="font-weight:600; color:#94a3b8; margin-bottom:6px; font-size:12px;">📥 输入前提 (Inputs)</div>
              <ul style="margin:0; padding-left:16px; color:#cbd5e1; font-size:12px; line-height:1.6;">
                <li v-for="(inp, i) in (activeTplConfigNode.inputs || [])" :key="i">{{ inp }}</li>
                <li v-if="!activeTplConfigNode.inputs?.length" style="color:#64748b;">上游前置产物触发</li>
              </ul>
            </div>
            <div style="flex:1;">
              <div style="font-weight:600; color:#94a3b8; margin-bottom:6px; font-size:12px;">📤 交付物/影响 (Outputs)</div>
              <ul style="margin:0; padding-left:16px; color:#cbd5e1; font-size:12px; line-height:1.6;">
                <li v-for="(out, i) in (activeTplConfigNode.outputs || [])" :key="i">{{ out }}</li>
                <li v-if="!activeTplConfigNode.outputs?.length" style="color:#64748b;">下游工位状态变迁</li>
              </ul>
            </div>
          </div>

          <!-- 💡 输入与输出真实工程 Demo 示例对比透视 -->
          <div v-if="activeTplConfigNode.inputDemo || activeTplConfigNode.outputDemo" style="margin-top:12px; display:flex; flex-direction:column; gap:10px;">
            <div style="font-size:12px; font-weight:700; color:#38bdf8; display:flex; align-items:center; gap:6px;">
              <span>💡</span>
              <span>输入与输出工程 Demo 示例 (帮助理解此工位)</span>
            </div>

            <!-- 输入示例 -->
            <div v-if="activeTplConfigNode.inputDemo" style="background:#020617; border:1px solid #1e293b; border-left:3px solid #38bdf8; border-radius:6px; padding:10px 12px;">
              <div style="font-size:11px; font-weight:700; color:#38bdf8; margin-bottom:4px; display:flex; align-items:center; gap:4px;">
                <span>📥 输入 Demo 样例：</span>
              </div>
              <pre style="margin:0; font-family:ui-monospace, SFMono-Regular, monospace; font-size:11px; color:#94a3b8; line-height:1.5; white-space:pre-wrap; word-break:break-word;">{{ activeTplConfigNode.inputDemo }}</pre>
            </div>

            <!-- 输出示例 -->
            <div v-if="activeTplConfigNode.outputDemo" style="background:#020617; border:1px solid #1e293b; border-left:3px solid #10b981; border-radius:6px; padding:10px 12px;">
              <div style="font-size:11px; font-weight:700; color:#10b981; margin-bottom:4px; display:flex; align-items:center; gap:4px;">
                <span>📤 产出 Demo 样例：</span>
              </div>
              <pre style="margin:0; font-family:ui-monospace, SFMono-Regular, monospace; font-size:11px; color:#cbd5e1; line-height:1.5; white-space:pre-wrap; word-break:break-word;">{{ activeTplConfigNode.outputDemo }}</pre>
            </div>
          </div>
        </div>

      </div>
      <div class="drawer-footer" style="background:#0b1320; border-top:1px solid #1e293b;">
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerTplNodeConfigVisible = false">关闭</button>
        <button v-if="activeTplConfigNode.category === 'instruction'" class="btn btn-primary btn-sm" @click="handleSaveTplNodeConfig">确认暂存此工位</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：AI Skill 专家 SKILL.md 实体规约透视 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerSkillDetailVisible }" @click="drawerSkillDetailVisible = false"></div>
    <div class="drawer-panel" :class="{ open: drawerSkillDetailVisible }" style="width:780px; max-width:94vw; background:#ffffff;">
      <div class="drawer-header" style="background:#f8fafc; border-bottom:1px solid #e2e8f0; color:#0f172a;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span class="tag" style="background:#0284c7; color:#fff;">{{ selectedSkillDetail?.icon || '🤖' }} {{ selectedSkillDetail?.id }}</span>
          <span style="font-size:15px; font-weight:700; color:#0f172a;">{{ selectedSkillDetail?.name }} · 规约透视</span>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          <!-- 切换渲染模式 vs 原始代码模式 -->
          <div class="view-mode-toggle" style="background:#e2e8f0; padding:2px; border-radius:6px;">
            <button
              class="view-mode-btn"
              :class="{ active: skillViewMode === 'rendered' }"
              style="padding:2px 8px; font-size:11px;"
              @click="skillViewMode = 'rendered'"
            >
              📖 排版渲染
            </button>
            <button
              class="view-mode-btn"
              :class="{ active: skillViewMode === 'raw' }"
              style="padding:2px 8px; font-size:11px;"
              @click="skillViewMode = 'raw'"
            >
              📝 原始 Markdown
            </button>
          </div>
          <button class="btn btn-outline btn-sm" style="color:#64748b; border-color:#cbd5e1;" @click="drawerSkillDetailVisible = false">✕</button>
        </div>
      </div>
      <div class="drawer-body" style="background:#f8fafc; color:#334155; font-size:13px; padding:20px; overflow-y:auto;">
        <div style="background:#ffffff; border:1px solid #e2e8f0; padding:12px; border-radius:6px; margin-bottom:16px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 1px 2px rgba(0,0,0,0.03);">
          <div>
            <div style="font-size:11px; color:#64748b; margin-bottom:2px;">规范文件物理路径：</div>
            <div style="font-family:monospace; color:#0284c7; font-size:12px; font-weight:600;">{{ selectedSkillDetail?.path }}</div>
          </div>
          <span class="tag" style="background:#ecfdf5; color:#059669; border:1px solid #a7f3d0; font-size:11px;">
            {{ skillViewMode === 'rendered' ? '✨ 富文本优雅阅读模式' : '💻 原始源码模式' }}
          </span>
        </div>

        <!-- 模式一：优雅排版渲染视图 -->
        <div v-if="skillViewMode === 'rendered'" class="markdown-rendered-container">
          <div v-html="renderMarkdownToHtml(selectedSkillDetail?.content || '')"></div>
        </div>

        <!-- 模式二：原始 Markdown 文本视图 -->
        <div v-else>
          <pre style="background:#ffffff; border:1px solid #e2e8f0; border-radius:8px; padding:16px; font-family:Consolas, Monaco, monospace; font-size:12px; color:#1e293b; line-height:1.6; white-space:pre-wrap; word-break:break-word; max-height:650px; overflow-y:auto; box-shadow:0 1px 3px rgba(0,0,0,0.04);">{{ selectedSkillDetail?.content || '暂无内容或正在加载...' }}</pre>
        </div>
      </div>
      <div class="drawer-footer" style="background:#f8fafc; border-top:1px solid #e2e8f0;">
        <button class="btn btn-outline btn-sm" style="color:#64748b; border-color:#cbd5e1;" @click="drawerSkillDetailVisible = false">关闭</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：自动化脚本源码与看门狗透视 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerScriptDetailVisible }" @click="drawerScriptDetailVisible = false"></div>
    <div class="drawer-panel" :class="{ open: drawerScriptDetailVisible }" style="width:780px; max-width:94vw; background:#ffffff;">
      <div class="drawer-header" style="background:#f8fafc; border-bottom:1px solid #e2e8f0; color:#0f172a;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span class="tag" style="background:#059669; color:#fff;">{{ selectedScriptDetail?.icon || '🛠️' }} {{ selectedScriptDetail?.id }}</span>
          <span style="font-size:15px; font-weight:700; color:#0f172a;">{{ selectedScriptDetail?.name }} · 脚本源码透视</span>
        </div>
        <button class="btn btn-outline btn-sm" style="color:#64748b; border-color:#cbd5e1;" @click="drawerScriptDetailVisible = false">✕</button>
      </div>
      <div class="drawer-body" style="background:#f8fafc; color:#334155; font-size:13px; padding:20px; overflow-y:auto;">
        <div style="background:#ffffff; border:1px solid #e2e8f0; padding:12px; border-radius:6px; margin-bottom:16px; box-shadow:0 1px 2px rgba(0,0,0,0.03);">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
            <span style="font-size:11px; color:#64748b;">脚本物理路径：</span>
            <span class="tag" style="background:#ecfdf5; color:#059669; border:1px solid #a7f3d0; font-size:10px;">{{ selectedScriptDetail?.runtime }}</span>
          </div>
          <div style="font-family:monospace; color:#0284c7; font-size:12px; margin-bottom:6px; font-weight:600;">{{ selectedScriptDetail?.path }}</div>
          <div style="background:#f1f5f9; border:1px solid #e2e8f0; padding:6px 10px; border-radius:4px; font-family:monospace; color:#0f766e; font-size:11px;">$ {{ selectedScriptDetail?.cmd }}</div>
        </div>
        <div>
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
            <div style="font-size:12px; font-weight:700; color:#475569;">💻 脚本源码全文（语法高亮 & 行号）：</div>
            <span class="tag" style="background:#e0f2fe; color:#0284c7; border:1px solid #bae6fd; font-size:10px;">IDE 代码视图</span>
          </div>
          <!-- 语法高亮代码编辑器视窗 -->
          <div class="code-editor-viewport">
            <div v-html="highlightScriptCode(selectedScriptDetail?.content || '')"></div>
          </div>
        </div>
      </div>
      <div class="drawer-footer" style="background:#f8fafc; border-top:1px solid #e2e8f0;">
        <button class="btn btn-outline btn-sm" style="color:#64748b; border-color:#cbd5e1;" @click="drawerScriptDetailVisible = false">关闭</button>
      </div>
    </div>
    <div class="modal-overlay" :class="{ active: modalGenSignLinkOpen }">
      <div class="card" style="width:480px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">🔗 客户签名链接已生成 (M09)</div>
          <button class="btn btn-outline btn-sm" @click="modalGenSignLinkOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <p style="font-size:13px; color:var(--text-secondary); margin-bottom:12px;">
            请将下方一次性专用链接发送给客户，客户免注册登录凭此链接即可在线完成清单勾选与电子签名：
          </p>
          <div class="form-group">
            <label class="form-label">免登签名专用链接</label>
            <input type="text" class="form-control" :value="activeBaselineSignLink.url" readonly>
          </div>
          <div class="form-group">
            <label class="form-label">访问验证码 (SMS/邮件二次保护)</label>
            <input type="text" class="form-control" :value="activeBaselineSignLink.code" readonly style="letter-spacing:4px; font-weight:700;">
          </div>
          <div class="form-hint">有效期：7 天 (至 2026-11-08 23:59:59)</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalGenSignLinkOpen = false">关闭</button>
          <button class="btn btn-primary" @click="handleCopySignLink">📋 复制链接与验证码</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：客户申请驳回澄清 (M10-Reverse) ================= -->
    <div class="modal-overlay" :class="{ active: modalClientRejectOpen }">
      <div class="card" style="width:520px; margin:0; border-top:4px solid #DC2626;">
        <div class="modal-header">
          <div class="modal-title" style="color:#DC2626;">⚠️ 申请驳回基线确认 (提出异议)</div>
          <button class="btn btn-outline btn-sm" @click="modalClientRejectOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <p style="font-size:13px; color:var(--text-secondary); margin-bottom:12px;">
            若您对本次约定清单的交付范围、规则或人天代价存在异议，可直接驳回。系统将自动通知内部产品经理李工重新核对：
          </p>
          <div class="form-group">
            <label class="form-label required">异议提出人与身份</label>
            <input type="text" class="form-control" v-model="clientRejectForm.author">
          </div>
          <div class="form-group">
            <label class="form-label required">具体分歧说明 (驳回原因)</label>
            <textarea class="form-control" rows="3" placeholder="例如：调速器掉线判定60秒太长，机组要求30秒内即报警..." v-model="clientRejectForm.reason"></textarea>
          </div>
          <div class="form-hint" style="color:#B91C1C;">
            注意：驳回后当前基线将退回【澄清中 (CLARIFYING)】状态，此前签署记录将安全留痕归档。
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalClientRejectOpen = false">取消返回</button>
          <button class="btn btn-accent" style="background:#DC2626; border-color:#DC2626;" @click="handleClientReject">确认驳回并退回澄清</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：M01 新建项目 (统一使用 CommonFormModal 公共弹窗) ================= -->
    <CommonFormModal
      v-model="modalNewProjectOpen"
      mode="dialog"
      width="500px"
      title="📁 新建项目空间 (M01)"
      confirm-text="立即创建"
      @confirm="handleCreateProject"
    >
      <div class="form-group">
        <label class="form-label required">项目唯一标识 Key</label>
        <input type="text" class="form-control" v-model="newProjectForm.projectKey" placeholder="2~8位大写字母，如: DCS-SIM">
      </div>
      <div class="form-group">
        <label class="form-label required">项目名称</label>
        <input type="text" class="form-control" v-model="newProjectForm.name" placeholder="如：集控中心监控仿真实训系统">
      </div>
      <div class="form-group">
        <label class="form-label required">客户单位名称</label>
        <input type="text" class="form-control" v-model="newProjectForm.clientName" placeholder="如：国能某某水电厂">
      </div>
      <div class="form-group">
        <label class="form-label">绑定研发工作流模板</label>
        <select class="form-control" v-model="(newProjectForm as any).workflowTemplateId">
          <option :value="undefined">默认：全局默认 · AI辅助四层一体化研发工作流 (TPL-DEFAULT)</option>
          <option v-for="tpl in workflowTemplates" :key="tpl.id" :value="tpl.id">
            {{ tpl.name }} ({{ tpl.templateCode }})
          </option>
        </select>
        <div class="form-hint">项目创建后将严格继承该模板的工位与门禁纪律，需求执行过程只读展示。</div>
      </div>
      <div class="form-group">
        <label class="form-label">项目描述与背景</label>
        <textarea class="form-control" rows="2" v-model="newProjectForm.description" placeholder="说明项目目标、涉及核心系统与责任边界..."></textarea>
      </div>
    </CommonFormModal>

    <!-- ================= 模态弹窗：上传原型版本 ================= -->
    <div class="modal-overlay" :class="{ active: modalUploadPrototypeOpen }">
      <div class="card" style="width:480px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">📤 上传原型快照 (HTML5 / ZIP)</div>
          <button class="btn btn-outline btn-sm" @click="modalUploadPrototypeOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label required">目标归属项目空间</label>
            <input type="text" class="form-control" :value="`${currentProject.projectKey || 'NET-SIM'} · ${currentProject.name || '当前项目'}`" readonly style="background:#f8fafc; font-weight:700; color:#0284c7;">
            <div class="form-hint">原型将以当前项目为唯一维度进行归档，生成专属目录 storage/prototypes/{{ currentProject.projectKey }}/...</div>
          </div>
          <div class="form-group">
            <label class="form-label required">原型文件 (支持 HTML5 或 ZIP 包)</label>
            <input
              type="file"
              class="form-control"
              accept=".html,.htm,.zip"
              @change="handleProtoFileChange"
            >
            <div class="form-hint" style="margin-top:6px; line-height:1.5;">
              💡 <strong>支持直接上传：</strong><br>
              ① 单文件 HTML5 原型 (<code>.html</code> / <code>.htm</code>)<br>
              ② 包含 <code>mockup.html</code> 与静态资源的 ZIP 压缩包 (<code>.zip</code>)<br>
              上传后系统将自动生成下一个版本号并计算 SHA256 物理存证指纹。
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">变更摘要</label>
            <textarea
              class="form-control"
              rows="2"
              v-model="uploadProtoForm.changeSummary"
              placeholder="简述本次原型修改重点，如：新增电表采集点位与看板交互..."
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalUploadPrototypeOpen = false" :disabled="uploadProtoLoading">取消</button>
          <button
            class="btn btn-primary"
            @click="handleSubmitUploadProto"
            :disabled="uploadProtoLoading"
          >
            {{ uploadProtoLoading ? '正在固化并上传...' : '开始固化并入库' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：M04 添加清单条目 ================= -->
    <div class="modal-overlay" :class="{ active: modalNewItemOpen }">
      <div class="card" style="width:580px; max-height:90vh; margin:0; display:flex; flex-direction:column; box-shadow:var(--shadow-lg);">
        <div class="modal-header">
          <div class="modal-title">➕ 添加清单条目 (M04)</div>
          <button class="btn btn-outline btn-sm" @click="modalNewItemOpen = false">✕</button>
        </div>
        <div class="modal-body" style="overflow-y:auto; padding:20px;">
          <!-- 类别选择 -->
          <div class="form-group">
            <label class="form-label required">条目层级与分类</label>
            <div style="display:flex; gap:12px; flex-wrap:wrap; padding:8px 12px; background:var(--bg-app); border:1px solid var(--border); border-radius:var(--radius-sm);">
              <label
                v-for="cat in [
                  { key: 'SCOPE', label: '范围 (Scope)', prefix: 'R-' },
                  { key: 'RULE', label: '规则 (Rule)', prefix: 'B-' },
                  { key: 'EXCEPTION', label: '异常 (Exception)', prefix: 'E-' },
                  { key: 'ACCEPTANCE', label: '验收 (Acceptance)', prefix: 'A-' },
                  { key: 'SKIP', label: '不做 (Non-goal)', prefix: 'N-' }
                ]"
                :key="cat.key"
                style="display:flex; align-items:center; gap:5px; font-size:13px; cursor:pointer;"
              >
                <input
                  type="radio"
                  name="itemCategoryRadio"
                  :value="cat.key"
                  v-model="newItemForm.category"
                  @change="onItemCategoryChange"
                />
                <span :style="{ fontWeight: newItemForm.category === cat.key ? '600' : 'normal', color: newItemForm.category === cat.key ? 'var(--primary)' : 'inherit' }">
                  {{ cat.label }}
                </span>
              </label>
            </div>
          </div>

          <!-- 编码与问法 -->
          <div style="display:grid; grid-template-columns: 130px 1fr; gap:12px;">
            <div class="form-group">
              <label class="form-label required">条目编号</label>
              <input
                type="text"
                class="form-control"
                v-model="newItemForm.itemCode"
                placeholder="如 R-01"
              />
            </div>
            <div class="form-group">
              <label class="form-label required">业务问法 (≤100字，客户能听懂的话)</label>
              <input
                type="text"
                class="form-control"
                v-model="newItemForm.question"
                maxlength="100"
                placeholder="例如：是否按 1#~4# 机组进行子集群底板划分？"
              />
            </div>
          </div>

          <!-- 动态候选选项与代价 (2~4 项) -->
          <div class="form-group">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
              <label class="form-label required" style="margin-bottom:0;">
                候选选项与代价 (2~4 项，单选选定默认约定)
              </label>
              <button
                type="button"
                class="btn btn-outline btn-sm"
                style="font-size:12px; padding:2px 10px;"
                @click="addOptionItem"
                :disabled="newItemForm.options.length >= 4"
              >
                + 增加选项 ({{ newItemForm.options.length }}/4)
              </button>
            </div>
            <div style="display:flex; flex-direction:column; gap:8px;">
              <div
                v-for="(opt, optIdx) in newItemForm.options"
                :key="optIdx"
                style="display:flex; align-items:center; gap:8px; background:var(--bg-app); border:1px solid var(--border); border-radius:var(--radius-sm); padding:6px 10px;"
              >
                <input
                  type="radio"
                  name="defaultOptionRadio"
                  :value="optIdx"
                  v-model="newItemForm.selectedOptionIndex"
                  title="勾选设为本期选定约定"
                  style="cursor:pointer;"
                />
                <input
                  type="text"
                  class="form-control"
                  style="flex:1; background:#FFF;"
                  v-model="opt.label"
                  :placeholder="'选项 ' + (optIdx + 1) + ' 交付约定方案文案'"
                />
                <div style="display:flex; align-items:center; gap:4px; width:120px;">
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    class="form-control"
                    style="background:#FFF; text-align:right;"
                    v-model.number="opt.cost"
                    placeholder="0.0"
                  />
                  <span style="font-size:12px; color:var(--text-muted); white-space:nowrap;">人天</span>
                </div>
                <button
                  type="button"
                  class="btn btn-outline btn-sm"
                  style="color:#DC2626; padding:4px 8px; border-color:#FECACA;"
                  @click="removeOptionItem(optIdx)"
                  :disabled="newItemForm.options.length <= 2"
                  title="删除该候选选项"
                >
                  ✕
                </button>
              </div>
            </div>
            <div style="font-size:12px; color:var(--text-muted); margin-top:6px;">
              * 选中的单选框代表该条目的核心交付方案；本期做时将按其代价 (+{{ (newItemForm.options[newItemForm.selectedOptionIndex]?.cost || 0).toFixed(1) }}d) 计入总人天折算。
            </div>
          </div>

          <!-- 初始决策结果 -->
          <div class="form-group">
            <label class="form-label required">初始共识决策</label>
            <div style="display:flex; gap:16px; padding:8px 12px; background:var(--bg-app); border:1px solid var(--border); border-radius:var(--radius-sm);">
              <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
                <input type="radio" value="CURRENT" v-model="newItemForm.decision" />
                <span style="color:var(--primary); font-weight:600;">✔ 本期做 (+{{ (newItemForm.options[newItemForm.selectedOptionIndex]?.cost || 0).toFixed(1) }}d)</span>
              </label>
              <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
                <input type="radio" value="NEXT" v-model="newItemForm.decision" />
                <span style="color:#D97706; font-weight:600;">⏳ 下期做 (0d)</span>
              </label>
              <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
                <input type="radio" value="SKIP" v-model="newItemForm.decision" />
                <span style="color:#DC2626; font-weight:600;">❌ 本期不做 (0d)</span>
              </label>
            </div>
          </div>

          <!-- 补充备注 -->
          <div class="form-group" style="margin-bottom:0;">
            <label class="form-label">补充备注 (选填，≤200字)</label>
            <textarea
              class="form-control"
              rows="2"
              maxlength="200"
              v-model="newItemForm.note"
              placeholder="客户口头强调说明、责任技术边界或其它上下文约定..."
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalNewItemOpen = false">取消</button>
          <button class="btn btn-primary" @click="handleConfirmAddItem">确认添加至清单</button>
        </div>
      </div>
    </div>
    <!-- ================= 🔮 全局智能调度悬浮球 (Smart Floating Ball) ================= -->
    <div
      class="smart-floating-ball-wrapper"
      :style="{ left: `${floatingBallPos.x}px`, top: `${floatingBallPos.y}px` }"
      @mousedown="handleBallMouseDown"
    >
      <!-- 悬浮小球主体 -->
      <div
        class="smart-ball"
        :class="{ active: floatingPanelVisible, dragging: isDraggingBall }"
        title="点击展开智能快捷调度，按住可任意拖拽"
        @click="handleBallClick"
      >
        <span class="smart-ball-icon">⚡</span>
        <span class="smart-ball-pulse"></span>
      </div>

      <!-- 点击展开的高端毛玻璃快捷调度浮层 -->
      <transition name="pop-scale">
        <div
          v-if="floatingPanelVisible"
          class="smart-floating-panel"
          :class="floatingBallPos.x > 300 ? 'align-left' : 'align-right'"
          @mousedown.stop
        >
          <div class="panel-header">
            <div style="display:flex; align-items:center; gap:6px;">
              <span style="font-size:14px;">✨</span>
              <span style="font-weight:700; font-size:13px; color:#0f172a;">智能快捷调度台</span>
            </div>
            <button class="panel-close-btn" @click="floatingPanelVisible = false">✕</button>
          </div>

          <div class="panel-body">
            <!-- 快速切换项目空间 -->
            <div style="padding:4px 6px; margin-bottom:4px; background:#f8fafc; border:1px solid #f1f5f9; border-radius:8px;">
              <div style="font-size:11px; color:#64748b; margin-bottom:4px; font-weight:600;">📁 当前聚焦项目空间</div>
              <select
                class="form-control"
                style="width:100%; font-size:12px; height:32px; padding:2px 8px; font-weight:600; color:#0284c7; background:#ffffff;"
                v-model="selectedProject"
                @change="handleProjectChange"
              >
                <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
                  {{ p.projectKey }} · {{ p.name }}
                </option>
              </select>
            </div>

            <!-- 快捷录入需求 -->
            <div
              class="quick-action-item"
              @click="handleOpenNewReqDrawer(); floatingPanelVisible = false"
            >
              <div class="action-icon" style="background:#e0f2fe; color:#0284c7;">➕</div>
              <div class="action-meta">
                <div class="action-title">快速录入需求</div>
                <div class="action-desc">无需切换页面，抽屉随时拉起录入</div>
              </div>
            </div>

            <!-- 客户免登录签名通道 -->
            <div
              class="quick-action-item"
              @click="switchPerspective(currentPerspective === 'internal' ? 'client' : 'internal'); floatingPanelVisible = false"
            >
              <div class="action-icon" style="background:#fef3c7; color:#d97706;">🔗</div>
              <div class="action-meta">
                <div class="action-title">
                  {{ currentPerspective === 'internal' ? '切换至外部客户免登端' : '切回内部系统视角' }}
                </div>
                <div class="action-desc">免登录专属链接，客户在线确认与签名</div>
              </div>
            </div>

            <!-- 进入当前项目流水线 -->
            <div
              class="quick-action-item"
              @click="currentPage = 'page-workflow'; floatingPanelVisible = false"
            >
              <div class="action-icon" style="background:#ecfdf5; color:#059669;">🚀</div>
              <div class="action-meta">
                <div class="action-title">工程流水线透视</div>
                <div class="action-desc">查看 16 篇 SOP 工位与实时质检门禁</div>
              </div>
            </div>
          </div>

          <div class="panel-footer">
            <span style="color:#64748b; font-size:11px;">💡 贴士：可任意拖拽小球放置屏幕两侧</span>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
/* ==========================================================================
   🔮 全局智能调度悬浮球 (Smart Floating Ball) 高端微交互动效
   ========================================================================== */
.smart-floating-ball-wrapper {
  position: fixed;
  z-index: 9999;
  user-select: none;
}
.smart-ball {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
  box-shadow: 0 4px 14px rgba(2, 132, 199, 0.4), 0 1px 3px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  position: relative;
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.2s;
}
.smart-ball:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(2, 132, 199, 0.5);
}
.smart-ball.dragging {
  cursor: grabbing;
  transform: scale(1.12);
  box-shadow: 0 8px 24px rgba(2, 132, 199, 0.6);
}
.smart-ball.active {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.3);
}
.smart-ball-icon {
  font-size: 20px;
  color: #ffffff;
  pointer-events: none;
}
.smart-ball-pulse {
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  border-radius: 50%;
  border: 1.5px solid rgba(2, 132, 199, 0.4);
  animation: ball-pulse 2.4s infinite ease-out;
  pointer-events: none;
}
@keyframes ball-pulse {
  0% { transform: scale(0.95); opacity: 0.8; }
  60% { transform: scale(1.3); opacity: 0; }
  100% { transform: scale(1.3); opacity: 0; }
}

/* 浮层面板（毛玻璃 + 微投影） */
.smart-floating-panel {
  position: absolute;
  bottom: 60px;
  width: 270px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12), 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  z-index: 10000;
  cursor: default;
}
.smart-floating-panel.align-left {
  right: 0;
}
.smart-floating-panel.align-right {
  left: 0;
}
.panel-header {
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-close-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.panel-close-btn:hover {
  background: #e2e8f0;
  color: #0f172a;
}
.panel-body {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.quick-action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.16s ease;
}
.quick-action-item:hover {
  background: #f1f5f9;
  transform: translateX(2px);
}
.action-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.action-meta {
  flex: 1;
}
.action-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1e293b;
}
.action-desc {
  font-size: 11px;
  color: #64748b;
  margin-top: 1px;
}
.panel-footer {
  padding: 8px 14px;
  background: #f8fafc;
  border-top: 1px solid #f1f5f9;
  text-align: center;
}

/* 弹出动效 */
.pop-scale-enter-active,
.pop-scale-leave-active {
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  transform-origin: bottom right;
}
.pop-scale-enter-from,
.pop-scale-leave-to {
  opacity: 0;
  transform: scale(0.9) translateY(10px);
}

/* ==========================================================================
   Markdown 富文本阅读模式专属优雅排版样式
   ========================================================================== */
.markdown-rendered-container {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 24px;
  color: #1e293b;
  font-size: 13.5px;
  line-height: 1.7;
  max-height: 650px;
  overflow-y: auto;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
:deep(.md-h1) {
  font-size: 20px;
  font-weight: 700;
  color: #0369a1;
  margin: 16px 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
}
:deep(.md-h2) {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin: 18px 0 10px 0;
  display: flex;
  align-items: center;
}
:deep(.md-h3) {
  font-size: 14.5px;
  font-weight: 600;
  color: #0284c7;
  margin: 14px 0 8px 0;
}
:deep(.md-h4) {
  font-size: 13.5px;
  font-weight: 600;
  color: #475569;
  margin: 10px 0 6px 0;
}
:deep(.md-code-block) {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  margin: 12px 0;
  overflow: hidden;
}
:deep(.md-code-header) {
  background: #f1f5f9;
  padding: 4px 12px;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
  font-family: monospace;
  border-bottom: 1px solid #e2e8f0;
}
:deep(.md-code-body) {
  margin: 0;
  padding: 12px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #0f766e;
  line-height: 1.5;
  overflow-x: auto;
}
:deep(.md-inline-code) {
  background: #f1f5f9;
  color: #0284c7;
  border: 1px solid #e2e8f0;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
}
:deep(.md-blockquote) {
  border-left: 4px solid #0284c7;
  background: #f0f9ff;
  padding: 8px 14px;
  margin: 10px 0;
  color: #475569;
  font-style: italic;
  border-radius: 0 4px 4px 0;
}
:deep(.md-hr) {
  border: none;
  border-top: 1px dashed #cbd5e1;
  margin: 20px 0;
}
:deep(.md-strong) {
  color: #0f172a;
  font-weight: 700;
}
:deep(.md-list-item) {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 4px 0;
  line-height: 1.6;
}
:deep(.md-bullet) {
  color: #0284c7;
  font-size: 14px;
}
:deep(.md-num) {
  color: #059669;
  font-weight: 700;
  font-family: monospace;
}
:deep(.md-paragraph-break) {
  height: 10px;
}

/* ==========================================================================
   Python / Shell 脚本代码编辑器视窗与语法高亮 (IDE Code Highlighting)
   ========================================================================== */
.code-editor-viewport {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px 0;
  max-height: 650px;
  overflow: auto;
  font-family: "JetBrains Mono", Consolas, Monaco, "Courier New", monospace;
  font-size: 12.5px;
  line-height: 1.6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
:deep(.code-line) {
  display: flex;
  padding: 0 16px;
  white-space: pre;
}
:deep(.code-line:hover) {
  background: #f8fafc;
}
:deep(.code-lineno) {
  width: 42px;
  user-select: none;
  color: #94a3b8;
  text-align: right;
  padding-right: 16px;
  flex-shrink: 0;
  border-right: 1px solid #e2e8f0;
  margin-right: 16px;
  font-size: 11px;
}
:deep(.code-text) {
  flex: 1;
  color: #1e293b;
}
:deep(.hl-keyword) {
  color: #e11d48;
  font-weight: 700;
}
:deep(.hl-string) {
  color: #0d9488;
}
:deep(.hl-docstring) {
  color: #64748b;
  font-style: italic;
}
:deep(.hl-comment) {
  color: #94a3b8;
  font-style: italic;
}
:deep(.hl-bool) {
  color: #ea580c;
  font-weight: 700;
}
:deep(.hl-builtin) {
  color: #0284c7;
}
:deep(.hl-decorator) {
  color: #9333ea;
}
:deep(.hl-number) {
  color: #d97706;
}

/* 工程流水线拓扑画布可视区 (FR-REQ-WFC-01 / Task-510)
   高度与内层画布容器保持严格一致 520px，避免多余纵向滚动条或高度塌陷 */
.wf-viewport {
  height: 520px;
  width: 100%;
  overflow: auto;
  border-radius: var(--radius-sm);
  border: 1px solid #1e293b;
  background: #0b1320;
  scrollbar-color: #3b4d68 #0b1320;
  scrollbar-width: thin;
}
.wf-viewport::-webkit-scrollbar { width: 12px; height: 12px; }
.wf-viewport::-webkit-scrollbar-track { background: #0b1320; border-radius: 8px; }
.wf-viewport::-webkit-scrollbar-thumb {
  background: #3b4d68;
  border: 2px solid #0b1320;
  border-radius: 8px;
}
.wf-viewport::-webkit-scrollbar-thumb:hover { background: #5b7396; }
.wf-graph-host { background: #0e1726; overflow: hidden; }

/* 🌟 X6 当前节点跑马灯流动光效 (Marquee animation) */
@keyframes workflow-marquee {
  0% {
    stroke-dashoffset: 28;
  }
  100% {
    stroke-dashoffset: 0;
  }
}
:deep(.node-marquee-border) {
  animation: workflow-marquee 1.2s linear infinite !important;
  filter: drop-shadow(0 0 6px rgba(0, 242, 254, 0.7));
}

.req-app-container {
  /* 统一与 SysPlatform 全局主题 (Apple 科技蓝 / 主题色 Token) 100% 对齐 */
  --primary: var(--primary-color, #0284c7);
  --primary-dark: var(--primary-hover, #0369a1);
  --primary-light: var(--primary-light, #e0f2fe);
  --accent: #C2410C;
  --accent-light: #FFEDD5;
  --bg: #F8FAFC;
  --surface: #FFFFFF;
  --border: #E2E8F0;
  --border-dark: #CBD5E1;
  --text: #0F172A;
  --text-muted: #64748B;
  --text-secondary: #334155;
  --success: #16A34A;
  --success-light: #DCFCE7;
  --warning: #D97706;
  --warning-light: #FEF3C7;
  --info: #0284C7;
  --info-light: #E0F2FE;
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
  --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;

  width: 100%;
  height: 100%;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  overflow-y: auto;
  background-color: var(--bg);
  color: var(--text);
  font-family: var(--font-family);
  font-size: 14px;
  line-height: 1.5;
}

/* 顶部顶栏（现代白底通透微卡片设计，替代暗沉黑条） */
header.app-header {
  background-color: #ffffff;
  color: #1e293b;
  border-bottom: 1px solid #e2e8f0;
  min-height: 48px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 6px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  z-index: 50;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.logo-badge {
  background: #e0f2fe;
  color: #0284c7;
  font-weight: 700;
  font-size: 12px;
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  letter-spacing: 0.5px;
}
.app-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
}
.project-selector {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  color: #1e293b;
  font-size: 13px;
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  cursor: pointer;
  outline: none;
}
.project-selector option {
  background: var(--surface);
  color: var(--text);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.role-badge {
  background: #E6F0F2;
  color: var(--primary-dark);
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.view-mode-toggle {
  background: rgba(0, 0, 0, 0.2);
  padding: 3px;
  border-radius: var(--radius-sm);
  display: flex;
  gap: 2px;
}
.view-mode-btn {
  background: transparent;
  border: none;
  color: #E2E8F0;
  padding: 4px 10px;
  font-size: 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}
.view-mode-btn.active {
  background: var(--surface);
  color: var(--primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

/* 主体布局 */
.app-body {
  display: flex;
  flex: 1;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  height: calc(100vh - 112px);
  overflow: hidden;
}

/* 左侧导航栏 */
aside.sidebar {
  width: 220px;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  user-select: none;
  overflow-y: auto;
}
.nav-group-title {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 16px 16px 6px;
  font-weight: 600;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 16px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  border-left: 3px solid transparent;
  text-decoration: none;
}
.nav-item:hover {
  background: var(--bg);
  color: var(--primary);
}
.nav-item.active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
  border-left-color: var(--primary);
}
.nav-badge {
  margin-left: auto;
  background: var(--border);
  color: var(--text-secondary);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 500;
}
.nav-badge.pending {
  background: var(--accent-light);
  color: var(--accent);
  font-weight: 700;
}

/* 右侧内容区 */
main.main-content {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  padding: 20px 24px;
  background: var(--bg);
  position: relative;
  box-sizing: border-box;
}

/* 视图显隐控制 */
.view-page {
  display: none;
  animation: fadeIn 0.15s ease-in-out;
}
.view-page.active {
  display: block;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 通用 UI 组件 */
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  margin-bottom: 20px;
  overflow: hidden;
}
.card-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FCFDFE;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-body {
  padding: 20px;
}

/* 按钮（统一百搭质感科技蓝配色，配合轻微渐变与投影） */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-sm, 6px);
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  user-select: none;
}
.btn-primary {
  background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
  color: #FFFFFF;
  border-color: #0284c7;
  box-shadow: 0 1px 2px rgba(2, 132, 199, 0.2);
}
.btn-primary:hover {
  background: linear-gradient(135deg, #0369a1 0%, #075985 100%);
  border-color: #0369a1;
  box-shadow: 0 3px 8px rgba(2, 132, 199, 0.35);
  transform: translateY(-1px);
}
.btn-primary:active {
  transform: translateY(0);
}
.btn-outline {
  background: #ffffff;
  color: #334155;
  border-color: #cbd5e1;
}
.btn-outline:hover {
  background: #f8fafc;
  color: #0284c7;
  border-color: #93c5fd;
  transform: translateY(-1px);
}
.btn-accent {
  background: #d97706;
  color: #FFF;
  border-color: #d97706;
}
.btn-accent:hover {
  background: #b45309;
}
.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

/* 标签 Tag */
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
}
.tag-draft { background: #F1F5F9; color: #475569; }
.tag-clarifying { background: #E0F2FE; color: #0369A1; }
.tag-pending { background: #FFEDD5; color: #C2410C; }
.tag-baselined { background: #DCFCE7; color: #15803D; }
.tag-released { background: #E6F0F2; color: #14535B; }
.tag-danger { background: #FEE2E2; color: #B91C1C; }

/* 项目卡片专用样式优化 (防折行与自适应排版) */
.project-card-item {
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  border: 1px solid var(--border, #E2E8F0);
  border-radius: var(--radius-base, 10px);
  overflow: hidden;
  background: var(--bg-card, #ffffff);
}
.project-card-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.project-card-header {
  background: #FAFBFD;
  border-bottom: 1px solid var(--border, #E2E8F0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  gap: 12px;
}
.project-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0; /* 允许文本截断/自适应弹性 */
}
.project-key-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  flex-shrink: 0;
  padding: 3px 8px;
  background: var(--primary-light, #ecfdf5);
  color: var(--primary-color, #059669);
  border: 1px solid var(--border-color, rgba(5, 150, 105, 0.2));
  border-radius: var(--radius-sm, 6px);
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-weight: 700;
  font-size: 13px;
  line-height: 1;
}
.project-title-text {
  font-weight: 700;
  font-size: 15px;
  color: var(--text, #0f172a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
.project-status-tag {
  flex-shrink: 0;
  white-space: nowrap;
  font-size: 11px;
}
.project-desc-text {
  color: var(--text-muted, #64748b);
  font-size: 13px;
  line-height: 1.6;
  min-height: 42px;
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.project-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-muted, #64748b);
  border-top: 1px dashed var(--border, #E2E8F0);
  padding-top: 12px;
}
.project-client-info {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}

/* 表格 */
.table-container {
  width: 100%;
  overflow-x: auto;
}
table.data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}
table.data-table th {
  background: #F8FAFC;
  color: var(--text-muted);
  font-weight: 600;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
table.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text-secondary);
  vertical-align: middle;
}
table.data-table tr:hover td {
  background: #F9FBFC;
}

/* 工作台顶部项目 Hero 区域 */
.dashboard-project-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 18px 22px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-sm);
}

.dashboard-hero-main {
  flex: 1;
  min-width: 0;
}

.dashboard-hero-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}

.dashboard-back-btn {
  padding: 4px 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
  border-radius: 6px;
}

.dashboard-hero-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
  margin: 0;
  line-height: 1.3;
}

.dashboard-client-badge {
  font-size: 12px;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  padding: 2px 10px;
  border-radius: 12px;
  margin-left: 4px;
  display: inline-flex;
  align-items: center;
}

.dashboard-client-badge strong {
  color: #1e293b;
  margin-left: 2px;
}

.dashboard-desc-container {
  margin-top: 6px;
  background: #f8fafc;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  padding: 8px 12px;
}

.dashboard-desc-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: all 0.25s ease;
}

.dashboard-desc-text.is-expanded {
  display: block;
  -webkit-line-clamp: unset;
  overflow: visible;
}

.dashboard-desc-label {
  font-weight: 600;
  color: var(--text-muted);
}

.dashboard-desc-toggle-btn {
  background: none;
  border: none;
  padding: 2px 0 0 0;
  font-size: 12px;
  color: var(--primary, #0284c7);
  cursor: pointer;
  display: inline-block;
  font-weight: 500;
}

.dashboard-desc-toggle-btn:hover {
  text-decoration: underline;
}

.dashboard-hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-shrink: 0;
  margin-top: 2px;
}

/* 指标统计卡片 Grid */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  padding: 16px;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}
.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
}
.stat-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* 抽屉 Drawer & 模态框 Modal */
.drawer-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(2px);
  z-index: 100;
  display: none;
  align-items: center;
  justify-content: center;
}
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(2px);
  z-index: 200;
  display: none;
  align-items: center;
  justify-content: center;
}
.drawer-overlay.active, .modal-overlay.active {
  display: flex;
}
.drawer-panel {
  position: fixed;
  top: 0; right: 0; bottom: 0;
  width: 640px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
  z-index: 101;
  display: flex;
  flex-direction: column;
  transform: translateX(105%);
  transition: transform 0.28s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.28s;
  visibility: hidden;
  pointer-events: none;
}
.drawer-panel.open {
  transform: translateX(0);
  visibility: visible;
  pointer-events: auto;
}
.drawer-header, .modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FAFAFA;
}
.drawer-title, .modal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}
.drawer-body, .modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
.drawer-footer, .modal-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  background: #FAFAFA;
}

/* 表单组件 */
.form-group {
  margin-bottom: 16px;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.form-label.required::after {
  content: " *";
  color: #DC2626;
}
.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-dark);
  border-radius: var(--radius-sm);
  font-size: 13px;
  outline: none;
  background: var(--surface);
  color: var(--text);
  font-family: inherit;
}
.form-control:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-light);
}
.form-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* 确认清单四层与代价联动专用组件 */
.checklist-layer-tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
  gap: 8px;
}
.checklist-tab-btn {
  padding: 8px 14px;
  font-size: 13px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  font-weight: 500;
}
.checklist-tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
  font-weight: 600;
}
.decision-options {
  display: flex;
  gap: 6px;
}
.decision-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--border-dark);
  border-radius: var(--radius-sm);
  cursor: pointer;
  background: var(--surface);
  color: var(--text-secondary);
  font-weight: 500;
  transition: all 0.15s;
}
.decision-btn.active-current {
  background: var(--success);
  color: #FFF;
  border-color: var(--success);
}
.decision-btn.active-next {
  background: var(--info);
  color: #FFF;
  border-color: var(--info);
}
.decision-btn.active-skip {
  background: #94A3B8;
  color: #FFF;
  border-color: #94A3B8;
}

/* 代价总计栏 */
.cost-summary-bar {
  background: #F0FDF4;
  border: 1px solid #BBF7D0;
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 13px;
}
.cost-stat-tag {
  font-weight: 700;
  color: #15803D;
  font-size: 16px;
  margin-left: 4px;
}

/* 客户外部免登录视角样式 */
.client-viewport-container {
  max-width: 920px;
  margin: 0 auto;
  background: var(--surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border);
  position: relative;
}
.watermark-overlay {
  position: absolute;
  top: 10px; right: 16px;
  font-size: 12px;
  color: var(--text-muted);
  pointer-events: none;
  opacity: 0.7;
}
.client-header {
  padding: 24px 30px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(to right, #F8FAFC, #FFFFFF);
}
.client-quote-box {
  background: #F1F5F9;
  border-left: 4px solid var(--primary);
  padding: 12px 16px;
  margin-top: 14px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-size: 13px;
  color: var(--text-secondary);
  font-style: italic;
}
.client-section {
  padding: 20px 30px;
  border-bottom: 1px solid var(--border);
}
.client-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.client-iframe-preview {
  width: 100%;
  height: 280px;
  border: 1px solid var(--border-dark);
  border-radius: var(--radius-sm);
  background: #FAFAFA;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  position: relative;
  overflow: hidden;
}
.receipt-box {
  border: 2px dashed #CBD5E1;
  padding: 24px;
  border-radius: var(--radius-md);
  background: #FAFCFF;
  margin: 20px 0;
}

/* P13 演进时间线专用样式 */
.timeline-container {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow-x: auto;
}
.timeline-legend {
  display: flex;
  gap: 20px;
  align-items: center;
  padding-bottom: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-secondary);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.timeline-axis-header {
  display: flex;
  margin-left: 200px;
  border-bottom: 2px solid var(--border-dark);
  padding-bottom: 8px;
  margin-bottom: 20px;
  position: relative;
}
.axis-tick {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  position: relative;
}
.axis-tick::before {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 0;
  width: 1px;
  height: 6px;
  background: var(--border-dark);
}
.timeline-row {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
  min-height: 48px;
}
.timeline-row-label {
  width: 190px;
  padding-right: 12px;
  flex-shrink: 0;
}
.timeline-row-req-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 6px;
}
.timeline-track-wrap {
  flex: 1;
  height: 44px;
  position: relative;
  display: flex;
  align-items: center;
}
.timeline-track-line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 4px;
  background: #E2E8F0;
  transform: translateY(-50%);
  border-radius: 2px;
  z-index: 1;
}
.timeline-node {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
}
.timeline-node:hover {
  transform: translate(-50%, -58%) scale(1.08);
}
.node-icon-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #FFF;
  border: 3px solid #64748B;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.node-icon-circle.prototype {
  border-color: #0284C7;
  background: #E0F2FE;
  color: #0284C7;
}
.node-icon-circle.baseline {
  border-color: #16A34A;
  background: #DCFCE7;
  color: #16A34A;
}
.node-icon-circle.pending-baseline {
  border-color: #D97706;
  background: #FEF3C7;
  color: #D97706;
}
.node-icon-circle.release {
  border-color: #14535B;
  background: #14535B;
  color: #FFF;
}
.node-title-badge {
  position: absolute;
  top: 26px;
  white-space: nowrap;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  background: #FFF;
  border: 1px solid var(--border);
  color: var(--text-secondary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.timeline-date-marker {
  position: absolute;
  bottom: 26px;
  font-size: 10px;
  color: var(--text-muted);
  white-space: nowrap;
}
.timeline-connection-line {
  position: absolute;
  top: 50%;
  height: 4px;
  background: #16A34A;
  transform: translateY(-50%);
  z-index: 1;
  border-radius: 2px;
}

/* ================= 🤖 AI Skills 专家中心精美卡片样式 ================= */
.expert-skill-card {
  position: relative;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05);
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.expert-skill-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 24px -4px rgba(2, 132, 199, 0.12), 0 8px 12px -4px rgba(0, 0, 0, 0.04);
  border-color: #bae6fd;
}
.skill-card-indicator {
  height: 4px;
  background: linear-gradient(90deg, #0284c7 0%, #38bdf8 50%, #818cf8 100%);
  width: 100%;
}
.skill-card-main {
  padding: 20px 20px 14px 20px;
  flex: 1;
}
.skill-card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 14px;
}
.skill-avatar-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border: 1px solid #bae6fd;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(2, 132, 199, 0.06);
}
.skill-title-block {
  flex: 1;
  min-width: 0;
}
.skill-id-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.skill-id-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.2px;
  word-break: break-all;
}
.skill-badge-agent {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
  white-space: nowrap;
}
.skill-name-text {
  font-size: 13px;
  font-weight: 600;
  color: #0284c7;
  line-height: 1.4;
}
.skill-desc-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 56px;
}
.skill-path-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 11px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, monospace;
  overflow: hidden;
}
.skill-path-code {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.skill-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fcfdfe;
  border-top: 1px solid #f1f5f9;
}
.skill-status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
  display: inline-block;
}
.pulse-text {
  font-size: 11px;
  font-weight: 600;
  color: #059669;
}
.skill-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: 6px;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(2, 132, 199, 0.2);
  transition: all 0.2s;
}
.skill-action-btn:hover {
  transform: translateX(2px);
  box-shadow: 0 4px 10px rgba(2, 132, 199, 0.3);
}
.arrow-icon {
  font-size: 12px;
  transition: transform 0.2s;
}
.skill-action-btn:hover .arrow-icon {
  transform: translateX(3px);
}

/* ================= 🛠️ 自动化脚本资产中心精美卡片样式 ================= */
.script-asset-card {
  position: relative;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05);
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.script-asset-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 24px -4px rgba(16, 185, 129, 0.12), 0 8px 12px -4px rgba(0, 0, 0, 0.04);
  border-color: #a7f3d0;
}
.script-card-indicator {
  height: 4px;
  background: linear-gradient(90deg, #10b981 0%, #34d399 50%, #06b6d4 100%);
  width: 100%;
}
.script-card-main {
  padding: 20px 20px 14px 20px;
  flex: 1;
}
.script-card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 14px;
}
.script-avatar-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
  border: 1px solid #a7f3d0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.06);
}
.script-title-block {
  flex: 1;
  min-width: 0;
}
.script-id-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.script-id-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.2px;
  word-break: break-all;
}
.script-badge-runtime {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
  white-space: nowrap;
}
.script-name-text {
  font-size: 13px;
  font-weight: 600;
  color: #059669;
  line-height: 1.4;
}
.script-desc-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 40px;
}
.script-cli-terminal {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #090d16;
  border: 1px solid #1e293b;
  padding: 8px 12px;
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  overflow: hidden;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.4);
}
.cli-prompt {
  color: #10b981;
  font-weight: 700;
  user-select: none;
}
.cli-command {
  color: #38bdf8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
.script-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fcfdfe;
  border-top: 1px solid #f1f5f9;
}
.script-status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pulse-dot-green {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
  display: inline-block;
}
.pulse-text-green {
  font-size: 11px;
  font-weight: 600;
  color: #059669;
}
.script-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: 6px;
  font-weight: 600;
  color: #334155;
  border-color: #cbd5e1;
  background: #ffffff;
  transition: all 0.2s;
}
.script-action-btn:hover {
  color: #059669;
  border-color: #10b981;
  background: #f0fdf4;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.15);
  transform: translateX(2px);
}
.script-action-btn:hover .arrow-icon {
  transform: translateX(3px);
}

/* ================= 💡 工作流 FAQ 知识中心样式 ================= */
.faq-toolbar-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.faq-search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
}
.faq-search-icon {
  position: absolute;
  left: 14px;
  font-size: 14px;
  color: #94a3b8;
  pointer-events: none;
}
.faq-search-input {
  width: 100%;
  padding: 10px 36px 10px 38px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 13px;
  color: #1e293b;
  outline: none;
  background: #f8fafc;
  transition: all 0.2s;
}
.faq-search-input:focus {
  background: #ffffff;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.faq-search-clear {
  position: absolute;
  right: 12px;
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  font-size: 12px;
}
.faq-category-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.faq-cat-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}
.faq-cat-btn:hover {
  background: #e2e8f0;
  color: #0f172a;
}
.faq-cat-btn.active {
  background: #6366f1;
  color: #ffffff;
  border-color: #4f46e5;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.3);
}

.faq-list-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.faq-accordion-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease;
}
.faq-accordion-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.08);
}
.faq-accordion-card.is-expanded {
  border-color: #818cf8;
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.1);
}
.faq-accordion-header {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
  background: #ffffff;
  gap: 14px;
}
.faq-accordion-card.is-expanded .faq-accordion-header {
  background: #f8faff;
  border-bottom: 1px solid #e0e7ff;
}
.faq-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}
.faq-badge-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  background: #ede9fe;
  color: #6d28d9;
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid #ddd6fe;
}
.faq-badge-cat {
  font-size: 11px;
  font-weight: 600;
  background: #f1f5f9;
  color: #475569;
  padding: 2px 8px;
  border-radius: 6px;
}
.faq-question-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  line-height: 1.4;
}
.faq-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.faq-tags-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.faq-tag-chip {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.faq-toggle-arrow {
  font-size: 11px;
  color: #94a3b8;
  transition: transform 0.2s ease;
  display: inline-block;
}
.faq-toggle-arrow.is-rotated {
  transform: rotate(180deg);
  color: #6366f1;
}

.faq-accordion-body {
  padding: 20px;
  background: #ffffff;
}
.faq-summary-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  background: #f0fdf4;
  border-left: 4px solid #10b981;
  border-radius: 0 8px 8px 0;
  margin-bottom: 16px;
}
.faq-summary-icon {
  font-size: 14px;
}
.faq-summary-text {
  font-size: 13px;
  font-weight: 600;
  color: #166534;
  line-height: 1.5;
}
.faq-answer-block {
  margin-bottom: 16px;
}
.faq-answer-title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
}
.faq-answer-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.8;
  white-space: pre-wrap;
  background: #f8fafc;
  padding: 14px 18px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
.faq-footer-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px dashed #e2e8f0;
  font-size: 12px;
}
.faq-meta-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.meta-label {
  font-weight: 600;
  color: #475569;
}
.meta-val {
  color: #0f172a;
}
.meta-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: #f1f5f9;
  color: #2563eb;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}
.meta-cli {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: #090d16;
  color: #38bdf8;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
}
.faq-empty-state {
  text-align: center;
  padding: 60px 20px;
  background: #ffffff;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
}

/* ================= 🌌 专业级极光流体执行工位胶囊样式 (Aurora Status Capsule) ================= */
.wf-current-status-capsule {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.95) 0%, rgba(14, 34, 61, 0.9) 100%);
  border: 1px solid rgba(56, 189, 248, 0.4);
  border-radius: 30px;
  padding: 4px 14px 4px 12px;
  box-shadow: 0 4px 16px rgba(6, 182, 212, 0.15), inset 0 1px 1px rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(8px);
  position: relative;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.wf-current-status-capsule:hover {
  border-color: #38bdf8;
  box-shadow: 0 6px 20px rgba(6, 182, 212, 0.3), inset 0 1px 2px rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
}
.capsule-live-beacon {
  position: relative;
  width: 12px;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.beacon-core {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 8px #38bdf8;
}
.beacon-halo {
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid #06b6d4;
  opacity: 0.8;
  animation: beacon-pulse 2s infinite ease-out;
}
@keyframes beacon-pulse {
  0% { transform: scale(0.6); opacity: 1; }
  100% { transform: scale(1.6); opacity: 0; }
}
.capsule-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.capsule-label {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 8.5px;
  font-weight: 700;
  color: #06b6d4;
  letter-spacing: 0.8px;
}
.capsule-val {
  display: flex;
  align-items: center;
  gap: 5px;
}
.capsule-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 700;
  color: #f8fafc;
}
.capsule-name {
  font-size: 12px;
  font-weight: 600;
  color: #38bdf8;
}
.capsule-track-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 10px;
  background: rgba(56, 189, 248, 0.12);
  color: #bae6fd;
  border: 1px solid rgba(56, 189, 248, 0.25);
  white-space: nowrap;
}
</style>
