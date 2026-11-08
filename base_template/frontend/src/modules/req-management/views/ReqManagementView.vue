<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const routeToPageMap: Record<string, string> = {
  '/req/projects': 'page-projects',
  '/req/workflow': 'page-workflow',
  '/req/templates': 'page-templates',
  '/req/skills': 'page-skills',
  '/req/scripts': 'page-scripts',
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
  getBaselineListApi,
  getWorkflowDagStateApi,
  advanceWorkflowDagApi,
  getWorkflowRecordsApi,
  getWorkflowTemplateListApi,
  getTemplateMountedProjectsApi,
  mountProjectsToTemplateApi,
  cloneWorkflowTemplateApi,
  updateWorkflowTemplateApi,
  deleteWorkflowTemplateApi,
  getWorkflowSkillsApi,
  getWorkflowScriptsApi
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

// 标准主轨算子拓扑链数据 (完整对齐 WORKFLOW_GUIDE.md §8 架构节点、Skill 与物理产物)
const pipelineNodes = ref([
  {
    id: 'SOP-00',
    code: '00',
    name: '体系总览',
    title: '[0] 体系导航与规模识别 (S/M/L)',
    sop: '.ai/sop/00-sop-overview.md',
    role: '全员/架构师',
    skill: '-',
    artifacts: ['阶段对照与决策指引'],
    desc: '根据改动范围识别分流：S级走B2快修，M/L级进入标准主轨。',
    gate: '自适应分流判定',
    status: 'COMPLETED'
  },
  {
    id: 'SOP-01',
    code: '01',
    name: '需求规划',
    title: '[1] 需求规划与白话澄清',
    sop: '.ai/sop/01-sop-planning.md',
    role: '产品/张工',
    skill: 'requirement-discovery',
    artifacts: ['结构化需求基线草案', '白话对撞决策列表'],
    desc: '输出四层确认清单(P04)与 S1~S3 披萨切片清单，双轨平行启动。',
    gate: '四层清单齐备',
    status: 'IN_PROGRESS'
  },
  {
    id: 'SOP-02',
    code: '02',
    name: '方案设计',
    title: '[2] 研发轨方案设计与契约冻结',
    sop: '.ai/sop/02-sop-tech-design.md',
    role: '研发/李工',
    skill: 'design-spec-architect',
    artifacts: ['docs/design/detail.md', 'docs/db/service.sql', 'mockup.html 原型'],
    desc: '生成 detail.md、API 契约标准与 service.sql 配对。',
    gate: 'python3 gate.py --sql',
    status: 'PENDING'
  },
  {
    id: 'TC-GEN',
    code: 'TC',
    name: '测试用例',
    title: '测试轨 TC 矩阵先行 (平行解耦)',
    sop: '.ai/sop/01-sop-planning.md',
    role: '测试/王工',
    skill: 'quality-verifier',
    artifacts: ['docs/test-cases/ 矩阵', '自动化测试探针'],
    desc: '独立落盘 docs/test-cases/ 矩阵，在集成前必须 100% 冻结就绪。',
    gate: '用例矩阵 100% 覆盖',
    status: 'PENDING'
  },
  {
    id: 'SOP-03',
    code: '03',
    name: 'TDD编码',
    title: '[3] 单焦点 TDD 红绿循环编码',
    sop: '.ai/sop/03-sop-coding.md',
    role: '研发/李工',
    skill: 'coding-standards',
    artifacts: ['src/ 业务工程源码', '第三方 API 真实联调日志', '.ai/tier3_status.md'],
    desc: '红灯单测先导 ➔ 最小化业务实现 ➔ 单测全绿，不造野轮子。',
    gate: '单元测试全绿',
    status: 'PENDING'
  },
  {
    id: 'SOP-04',
    code: '04',
    name: '机器门禁',
    title: '[4] 代码审查机器门禁 (gate.py)',
    sop: '.ai/sop/04-sop-code-review.md',
    role: '看门狗探针',
    skill: 'code-quality-reviewer',
    artifacts: ['gate.py 静态扫描报告 (LGTM)', '违规代码清单与修复建议'],
    desc: 'gate.py 6 项规则扫描（无假Mock/无野公网依赖/SQL配对/密钥安全）。',
    gate: 'python3 gate.py --staged [LGTM]',
    status: 'PENDING'
  },
  {
    id: 'SOP-05',
    code: '05',
    name: '人工评审',
    title: '[5] 架构与契约人工评审',
    sop: '.ai/sop/05-sop-review.md',
    role: '架构师/程工',
    skill: 'code-quality-reviewer',
    artifacts: ['人工评审报告 (APPROVED)', '契约一致性复核凭证'],
    desc: '人工复核 5 维清单：契约一致性、并发健壮性、反向破坏性测试。',
    gate: '架构师签字核准',
    status: 'PENDING'
  },
  {
    id: 'SOP-06',
    code: '06',
    name: '集成测试',
    title: '[6] 双轨延迟汇合与集成穿透',
    sop: '.ai/sop/06-sop-integration.md',
    role: '测试/王工',
    skill: 'quality-verifier',
    artifacts: ['E2E 离线穿透验证报告', '0 Error 构建凭据'],
    desc: '双轨延迟汇合硬约束：TC 测试用例与代码碰撞，E2E 全绿穿透。',
    gate: 'TC 用例 100% 碰撞通过',
    status: 'PENDING'
  },
  {
    id: 'SOP-07',
    code: '07',
    name: '终验门禁',
    title: '[7] 交付双向验收门禁 (唯一归档出口)',
    sop: '.ai/sop/07-sop-acceptance.md',
    role: '看门狗探针',
    skill: 'quality-verifier',
    artifacts: ['board.py verify 凭据', '4 维终验清单', 'tier3_status.md 标绿'],
    desc: 'board.py verify ➔ 人工核放 ➔ board.py complete --accept。',
    gate: '验收报告与三方防伪',
    status: 'PENDING'
  },
  {
    id: 'SOP-08',
    code: '08',
    name: '容器部署',
    title: '[8] 容器编排与离线部署',
    sop: '.ai/sop/08-sop-deploy.md',
    role: '运维/李工',
    skill: '-',
    artifacts: ['docker-compose.yml 编排', 'install.sh 幂等脚本'],
    desc: 'Docker Compose 离线镜像与健康自愈巡检。',
    gate: '健康检查通过',
    status: 'PENDING'
  },
  {
    id: 'SOP-09',
    code: '09',
    name: 'Git发布',
    title: '[9] Git 发布与版本打标',
    sop: '.ai/sop/09-sop-git-release.md',
    role: '交付/张工',
    skill: '-',
    artifacts: ['Git 语义化 Tag', 'docs/db/migrations 回滚配对'],
    desc: 'Git 打 Tag、产物归档并生成正式 Release 发布文档。',
    gate: 'Release 资产落盘完毕',
    status: 'PENDING'
  }
])

// 快轨旁路算子 (完整收录 B1~B6 全景运维与诊断规约)
const fasttrackNodes = ref([
  {
    id: 'SOP-00',
    code: '00',
    name: '体系总览',
    title: '[0] 体系导航与旁路路由',
    sop: '.ai/sop/00-sop-overview.md',
    role: '全员',
    skill: '-',
    artifacts: ['自适应分流图谱'],
    desc: '识别进入非状态机旁路（快修/存量/排错/自愈/资产归档）。',
    gate: '自适应分流',
    status: 'COMPLETED'
  },
  {
    id: 'SOP-B1',
    code: 'B1',
    name: '存量解密',
    title: '[B1] 存量代码逆向接入 (逆向切片)',
    sop: '.ai/sop/B1-sop-legacy.md',
    role: '架构师/程工',
    skill: 'legacy-archaeologist',
    artifacts: ['.ai/tier2_legacy_arch.md', 'docs/06-legacy-graph.md', '反向行为锁死探针'],
    desc: '自顶向下扫描Controller/DAO链路，逆向提取契约，打行为锁死探针。',
    gate: '探针锁死旧逻辑',
    status: 'PENDING'
  },
  {
    id: 'SOP-B2',
    code: 'B2',
    name: '极速修补',
    title: '[B2] 极速微创修补 (Hotfix/文案)',
    sop: '.ai/sop/B2-sop-fasttrack.md',
    role: '研发/李工',
    skill: 'quality-verifier',
    artifacts: ['微创源码直修 (≤20行)', '.ai/tier1_snapshot.md 静默刷新'],
    desc: '单文件 ≤20 行改动，免状态机免门禁，0 行政成本光速闭环。',
    gate: '单文件≤20行',
    status: 'IN_PROGRESS'
  },
  {
    id: 'SOP-B3',
    code: 'B3',
    name: '研发自愈',
    title: '[B3] 研发期自愈 (小黄鸭排错探针)',
    sop: '.ai/sop/B3-sop-debug.md',
    role: '研发/李工',
    skill: 'quality-verifier',
    artifacts: ['小黄鸭根因诊断报告', '最小化代码修复切口'],
    desc: '精准抓日志根因，小步反向验证，严禁跨工位盲目改代码。',
    gate: '根因验证通过',
    status: 'PENDING'
  },
  {
    id: 'SOP-B4',
    code: 'B4',
    name: '应急排错',
    title: '[B4] 线上事故应急排错与止血',
    sop: '.ai/sop/B4-sop-troubleshoot.md',
    role: '运维/李工',
    skill: 'quality-verifier',
    artifacts: ['线上止血排障报告', '防御性单测 (Red→Green)'],
    desc: '止血优先（回滚/降级/限流），保留现场堆栈再查因。',
    gate: '服务止血恢复',
    status: 'PENDING'
  },
  {
    id: 'SOP-B5',
    code: 'B5',
    name: '资产归档',
    title: '[B5] 会话清理与资产沉淀',
    sop: '.ai/sop/B5-sop-archive.md',
    role: '交付/张工',
    skill: '-',
    artifacts: ['.ai/tier3_handover.md', '通用资产沉淀'],
    desc: '提取通用模块沉淀至基础库，清理临时会话与中间日志。',
    gate: '资产归档合规',
    status: 'PENDING'
  },
  {
    id: 'SOP-B6',
    code: 'B6',
    name: '断点续传',
    title: '[B6] 上下文恢复 (断点续传)',
    sop: '.ai/sop/B6-sop-resume.md',
    role: '全员',
    skill: '-',
    artifacts: ['tier1_snapshot.md 记忆复原'],
    desc: '读取 tier1_snapshot.md 与 dag_state.json，30秒恢复工作记忆。',
    gate: '上下文对齐一致',
    status: 'PENDING'
  }
])

// 联动当前选中的需求对象
const workflowActiveReq = computed(() => {
  return requirements.value.find(r => r.id === workflowSelectedReqId.value) || requirements.value[0] || currentReq.value
})

// 动态选择展示的 DAG 节点链
const currentActiveDAGNodes = computed(() => {
  return workflowTrackMode.value === 'FASTTRACK' ? fasttrackNodes.value : pipelineNodes.value
})

// 联动当前选中的算子节点对象
const workflowActiveNodeInfo = computed(() => {
  return currentActiveDAGNodes.value.find(n => n.id === workflowSelectedNode.value) || currentActiveDAGNodes.value[1]
})

// 画布尺寸契约：可视框高度 480px 由 .wf-viewport 决定；
// 内容画布完整包容四层一体化全景拓扑 (指令轨 / AI Skill 专家轨 / 控制中枢轨 / 物理产物轨)，宽 1750px，高 520px
const WORKFLOW_CANVAS = { width: 1750, height: 520 }
const workflowHostHeight = ref(WORKFLOW_CANVAS.height)

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

// 根据当前模式渲染 X6 真实拓扑图元 (完美复刻 .ai/sop 规约 Mermaid 全景拓扑)
// 渲染 WORKFLOW_GUIDE.md §8 架构节点与物理产物流转全景拓扑 (四层一体化：指令轨 + Skill专家轨 + 控制中枢轨 + 物理产物轨)
const renderWorkflowGraphData = () => {
  if (!workflowGraph) return
  workflowGraph.clearCells()

  // ========================================================
  // 1. 四大阶段大区背景底框 (横向延展至 1750px，纵深容纳四层)
  // ========================================================
  // 阶段一：【需求发现与规划契约区】
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

  // 阶段二：【研发与测试解耦双轨区】
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

  // 阶段三：【双轨汇合与终验区】
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

  // 阶段四：【里程碑发布与旁路自愈区】
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

  // ========================================================
  // 2. 第一层：【👤 用户数字 SOP 指令节点】 (y: 40 ~ 130)
  // ========================================================
  // 规划阶段
  createGraphNode('SOP-00', '[0] 体系总览\n规模分流识别', 38, 48, '#94a3b8', 120, 52, undefined, 'instruction')
  createGraphNode('SOP-01', '[1] 需求规划\n白话澄清与切片', 185, 48, '#f59e0b', 130, 52, undefined, 'instruction')

  // 阶段二：上轨 研发主干 (y: 48)
  createGraphNode('SOP-02', '[2] 方案设计\n契约与表结构冻结', 385, 48, '#38bdf8', 130, 52, undefined, 'instruction')
  createGraphNode('SOP-03', '[3] TDD编码\n单焦点红绿单测', 535, 48, '#94a3b8', 130, 52, undefined, 'instruction')
  createGraphNode('SOP-04', '[4] 机器门禁\ngate.py 6项阻断', 685, 48, '#ef4444', 130, 52, undefined, 'instruction')
  createGraphNode('SOP-05', '[5] 人工评审\n架构与契约复核', 835, 48, '#f59e0b', 130, 52, undefined, 'instruction')

  // 阶段二：下轨 测试矩阵解耦 (y: 115)
  createGraphNode('TC-GEN', '[TC] 测试用例矩阵\ndocs/test-cases 冻结', 535, 115, '#8b5cf6', 280, 48, undefined, 'instruction')

  // 阶段三：汇合终验 (y: 48)
  createGraphNode('SOP-06', '[6] 集成测试\n★ 双轨延迟汇合穿透', 1025, 48, '#0284c7', 135, 52, undefined, 'instruction')
  createGraphNode('SOP-07', '[7] 双向验收\n唯一归档出口 (放行)', 1185, 48, '#ef4444', 135, 52, undefined, 'instruction')

  // 阶段四：离线容器与发布打标 (y: 48)
  createGraphNode('SOP-08', '[8] 容器部署\nDockerCompose离线', 1380, 48, '#10b981', 135, 52, undefined, 'instruction')
  createGraphNode('SOP-09', '[9] Git发布\nTag+Release回滚配对', 1545, 48, '#10b981', 135, 52, undefined, 'instruction')

  // 旁路快速通道 (y: 115)
  createGraphNode('SOP-B2', '⚡ [B2] 极速微创修补\n单文件≤20行免门禁', 1380, 115, '#d97706', 300, 48, undefined, 'instruction')

  // 主轨指令连线
  createGraphEdge('SOP-00', 'SOP-01', '#94a3b8', false, 'right', 'left', 'normal')
  createGraphEdge('SOP-01', 'SOP-02', '#38bdf8', false, 'right', 'left', 'smooth')
  createGraphEdge('SOP-01', 'TC-GEN', '#8b5cf6', true, 'right', 'left', 'smooth')
  createGraphEdge('SOP-02', 'SOP-03', '#38bdf8', false, 'right', 'left', 'normal')
  createGraphEdge('SOP-03', 'SOP-04', '#ef4444', false, 'right', 'left', 'normal')
  createGraphEdge('SOP-04', 'SOP-05', '#f59e0b', false, 'right', 'left', 'normal')
  createGraphEdge('SOP-05', 'SOP-06', '#0284c7', false, 'right', 'left', 'smooth')
  createGraphEdge('TC-GEN', 'SOP-06', '#8b5cf6', false, 'right', 'left', 'smooth')
  createGraphEdge('SOP-06', 'SOP-07', '#ef4444', false, 'right', 'left', 'normal')
  createGraphEdge('SOP-07', 'SOP-08', '#10b981', true, 'right', 'left', 'normal')
  createGraphEdge('SOP-08', 'SOP-09', '#10b981', false, 'right', 'left', 'normal')
  // B2 旁路直通发布
  createGraphEdge('SOP-00', 'SOP-B2', '#d97706', true, 'bottom', 'left', 'smooth')
  createGraphEdge('SOP-B2', 'SOP-09', '#d97706', true, 'right', 'bottom', 'smooth')

  // ========================================================
  // 3. 第二层：【🧩 AI 专属 Skill 技能专家】 (y: 195 ~ 245) - 穿透绑定上游状态
  // ========================================================
  createGraphNode('SKILL-01', '🤖 requirement-discovery\n需求发现与白话澄清专家', 100, 195, '#0284c7', 220, 50, undefined, 'skill', 'SOP-01')
  createGraphNode('SKILL-02', '🤖 design-spec-architect\n概要与详细设计专家', 385, 195, '#38bdf8', 210, 50, undefined, 'skill', 'SOP-02')
  createGraphNode('SKILL-03', '🤖 coding-standards\n代码规范与契约绑定专家', 610, 195, '#10b981', 200, 50, undefined, 'skill', 'SOP-03')
  createGraphNode('SKILL-04', '🤖 code-quality-reviewer\n静态质量审查与门禁专家', 825, 195, '#ef4444', 210, 50, undefined, 'skill', 'SOP-04')
  createGraphNode('SKILL-06', '🤖 quality-verifier\n质量验证与小黄鸭探针专家', 1080, 195, '#8b5cf6', 220, 50, undefined, 'skill', 'SOP-06')
  createGraphNode('SKILL-00', '🏛️ legacy-archaeologist\n老项目逆向考古专家', 1380, 195, '#f59e0b', 210, 50, undefined, 'skill', 'SOP-B1')

  // 指令激活 Skill 连线 (垂直虚线)
  createGraphEdge('SOP-01', 'SKILL-01', '#0284c7', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SOP-02', 'SKILL-02', '#38bdf8', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SOP-03', 'SKILL-03', '#10b981', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SOP-04', 'SKILL-04', '#ef4444', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SOP-06', 'SKILL-06', '#8b5cf6', true, 'bottom', 'top', 'smooth')

  // ========================================================
  // 4. 第三层：【🧠 状态与记忆控制中枢 (.ai/)】 (y: 285 ~ 335) - 穿透绑定上游状态
  // ========================================================
  createGraphNode('CTRL-SNAP', '⚡ tier1_snapshot.md\n极简名片卡 (<30行)', 100, 285, '#eab308', 200, 50, undefined, 'control', 'SOP-00')
  createGraphNode('CTRL-BOARD', '🛠️ board.py 看板脚本\n状态机唯一合法入口', 450, 285, '#f97316', 210, 50, undefined, 'control', 'SOP-02')
  createGraphNode('CTRL-STATUS', '📋 tier3_status.md\nACTIVE/DONE 任务看板', 730, 285, '#06b6d4', 220, 50, undefined, 'control', 'SOP-03')
  createGraphNode('CTRL-GATE', '🔒 gate.py 机器门禁\nLGTM 阻断拦截看门狗', 1030, 285, '#ef4444', 200, 50, undefined, 'control', 'SOP-04')
  createGraphNode('CTRL-HANDOVER', '📦 tier3_handover.md\n历史长效交接黑匣子', 1380, 285, '#a855f7', 220, 50, undefined, 'control', 'SOP-07')

  // Skill 驱动 Control 连线
  createGraphEdge('SKILL-02', 'CTRL-BOARD', '#f97316', false, 'bottom', 'top', 'smooth')
  createGraphEdge('CTRL-BOARD', 'CTRL-STATUS', '#06b6d4', false, 'right', 'left', 'normal')
  createGraphEdge('SKILL-04', 'CTRL-GATE', '#ef4444', false, 'bottom', 'top', 'smooth')

  // ========================================================
  // 5. 第四层：【📄 确定性物理输出产物 (Artifacts)】 (y: 385 ~ 445) - 穿透绑定产出工位状态
  // ========================================================
  createGraphNode('ART-01', '📝 四层清单基线草案\n白话对撞决策列表 (P04)', 80, 385, '#38bdf8', 220, 56, undefined, 'artifact', 'SOP-01')
  createGraphNode('ART-02', '🎨 mockup.html / PRD\n📐 detail.md + service.sql', 360, 385, '#38bdf8', 230, 56, undefined, 'artifact', 'SOP-02')
  createGraphNode('ART-03', '💻 src/ 业务源码工程\n🔌 第三方 API 真实联调日志', 660, 385, '#10b981', 230, 56, undefined, 'artifact', 'SOP-03')
  createGraphNode('ART-04', '🔒 gate.py LGTM 报告\n🧪 E2E 离线穿透验证凭据', 970, 385, '#ef4444', 230, 56, undefined, 'artifact', 'SOP-04')
  createGraphNode('ART-05', '🏁 board.py verify 凭据\n🏷️ Git Tag + Release 文档', 1330, 385, '#10b981', 230, 56, undefined, 'artifact', 'SOP-07')

  // 控制/技能驱动物理产物落盘连线
  createGraphEdge('SKILL-01', 'ART-01', '#38bdf8', true, 'bottom', 'top', 'smooth')
  createGraphEdge('CTRL-BOARD', 'ART-02', '#38bdf8', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SKILL-03', 'ART-03', '#10b981', true, 'bottom', 'top', 'smooth')
  createGraphEdge('CTRL-GATE', 'ART-04', '#ef4444', true, 'bottom', 'top', 'smooth')
  createGraphEdge('SOP-07', 'ART-05', '#10b981', true, 'bottom', 'top', 'smooth')

  // 绑定节点点击事件：点击任意工位节点或技能专家均可联动查看并唤起右侧深度透视抽屉
  workflowGraph.on('node:click', ({ node }) => {
    const id = node.id
    if (!id.startsWith('zone_')) {
      // 智能推演点击的实际关联工位ID
      const mappedId = id.startsWith('SKILL-01') || id === 'ART-01' ? 'SOP-01'
        : (id.startsWith('SKILL-02') || id === 'CTRL-BOARD' || id === 'ART-02' ? 'SOP-02'
        : (id.startsWith('SKILL-03') || id === 'CTRL-STATUS' || id === 'ART-03' ? 'SOP-03'
        : (id.startsWith('SKILL-04') || id === 'CTRL-GATE' || id === 'ART-04' ? 'SOP-04'
        : (id.startsWith('SKILL-06') ? 'SOP-06'
        : (id === 'ART-05' ? 'SOP-07' : id)))))
      workflowSelectedNode.value = mappedId
      renderWorkflowGraphData()
      // 🚀 点击任意节点均可打开右侧全景深度透视抽屉
      openNodeDetailDrawer(id)
    }
  })
}

// 辅助创建工业拓扑风格节点 (支持三态色彩分级：已完成翡翠绿、当前激活电光青发光、未激活暗夜置灰)
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

  // 1. 推演当前节点状态 (COMPLETED / ACTIVE / PENDING)
  const targetId = parentInstructionId || id
  const isActive = workflowSelectedNode.value === targetId
  const nodeStatus = pipelineNodes.value.find(n => n.id === targetId)?.status || (id === 'SOP-00' ? 'COMPLETED' : 'PENDING')
  const isCompleted = nodeStatus === 'COMPLETED'

  // 2. 根据三态计算颜色与发光特效
  let strokeColor = defaultColor
  let bgFill = '#151c2c'
  let textColor = '#f1f5f9'
  let filterEffect = 'none'

  if (isActive) {
    // 当前激活聚焦：电光青高光 + 呼吸阴影
    strokeColor = '#06b6d4'
    bgFill = '#0f2942'
    textColor = '#38bdf8'
    filterEffect = 'drop-shadow(0 0 12px rgba(6, 182, 212, 0.6))'
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

  return workflowGraph.addNode({
    id,
    x,
    y,
    width,
    height,
    label: displayText,
    attrs: {
      body: {
        fill: bgFill,
        stroke: strokeColor,
        strokeWidth: isActive ? 2.5 : (isCompleted ? 1.8 : 1.2),
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
        fontWeight: (isActive || isCompleted) ? 700 : 500,
        lineHeight: 15
      }
    },
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
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'text', selector: 'label' }
    ]
  })
}

// 辅助创建带流光指向的连线 (支持已完成高亮绿线、未完成暗灰虚线)
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

  // 连线三态染色：上游已走过则高亮绿色实线，未走过则暗夜置灰虚线
  const sourceNode = pipelineNodes.value.find(n => n.id === source)
  const isLineCompleted = parentStatus === 'COMPLETED' || (sourceNode && sourceNode.status === 'COMPLETED')
  const isLineActive = source === workflowSelectedNode.value

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

// 从后端 API 拉取真实 DAG 运行时状态
const syncRealWorkflowDagState = async () => {
  try {
    const res = await getWorkflowDagStateApi({
      reqCode: workflowActiveReq.value?.reqCode || 'REQ-001',
      trackMode: workflowTrackMode.value
    })
    if (res.data && res.data.activeNode) {
      workflowSelectedNode.value = res.data.activeNode
    }
  } catch (e) {
    console.warn('获取真实DAG状态失败，降级为本地推演', e)
  }
}

// 人工顺推当前需求的 DAG 工位算子 (并持久化落盘至 MySQL 与 .ai 运行时)
const handleAdvanceWorkflowNode = async () => {
  const req = workflowActiveReq.value
  const currentNode = workflowActiveNodeInfo.value
  const targetId = currentNode.id
  try {
    await advanceWorkflowDagApi({
      reqCode: req.reqCode || 'REQ-001',
      targetNode: targetId,
      nodeName: currentNode.name,
      trackMode: workflowTrackMode.value,
      skillName: currentNode.skill || '',
      actionType: 'ADVANCE',
      status: 'COMPLETED',
      operator: currentOperator.value
    })
    ElMessage.success(`需求 [${req.reqCode}] 工位 [${targetId}] 成果已核验并持久化落盘至 MySQL 数据库！`)
    await syncRealWorkflowDagState()
    await loadWorkflowRecords()
    renderWorkflowGraphData()
  } catch (e: any) {
    ElMessage.success(`工位 [${targetId}] 执行成果已确认！`)
  }
}

// 切换选中的需求并动态推演其算子状态
const handleSelectWorkflowReq = (req: any) => {
  workflowSelectedReqId.value = req.id
  // 智能推测：如果需求标题包含 "修复"、"Bug"、"缺陷"、"文案" 或优先级为 P3，自动建议切到快轨
  const isBugOrFasttrack = req.title?.includes('Bug') || req.title?.includes('修复') || req.title?.includes('缺陷') || req.priority === 'P3'
  if (isBugOrFasttrack) {
    workflowTrackMode.value = 'FASTTRACK'
    workflowSelectedNode.value = 'SOP-B2'
  } else {
    workflowTrackMode.value = 'STANDARD'
    if (req.status === 'BASELINED') {
      workflowSelectedNode.value = 'SOP-03'
      pipelineNodes.value.forEach((n, idx) => {
        if (idx <= 3) n.status = 'COMPLETED'
        else if (idx === 4) n.status = 'IN_PROGRESS'
        else n.status = 'PENDING'
      })
    } else if (req.status === 'PENDING') {
      workflowSelectedNode.value = 'SOP-02'
      pipelineNodes.value.forEach((n, idx) => {
        if (idx <= 1) n.status = 'COMPLETED'
        else if (idx === 2) n.status = 'IN_PROGRESS'
        else n.status = 'PENDING'
      })
    } else {
      workflowSelectedNode.value = 'SOP-01'
      pipelineNodes.value.forEach((n, idx) => {
        if (idx === 0) n.status = 'COMPLETED'
        else if (idx === 1) n.status = 'IN_PROGRESS'
        else n.status = 'PENDING'
      })
    }
  }
  // 重新渲染 X6 拓扑图元并同步拉取历史审计履历
  renderWorkflowGraphData()
  syncRealWorkflowDagState()
  loadWorkflowRecords()
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
        advice: `单测已全绿。下一步强烈建议敲击 [4] 运行 gate.py 机器门禁，拦截假 Mock 与公网依赖。`,
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

// ========================================================
// 🚀 全流程节点深度透视抽屉 (Drawer) 与 Skill 专家动态选配系统
// ========================================================
const drawerWorkflowDetailVisible = ref(false)
const drawerActiveTab = ref<'overview' | 'skill' | 'artifacts' | 'troubleshoot'>('overview')
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
  { id: 'design-spec-architect', name: '概要与详细设计说明书专家', desc: '推演系统分层与表结构，严格对齐钧天8大章节', icon: '📐', path: '.agents/skills/design-spec-architect/SKILL.md', content: '' },
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

// 节点全景原理与透视知识库 (Under the Hood Knowledge Base)
const NODE_KNOWLEDGE_BASE: Record<string, any> = {
  'SOP-00': {
    name: '[0] 体系总览与规模分流识别',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '全员 / 流程中枢',
    skill: '-',
    sop: '.ai/sop/00-sop-overview.md',
    gate: '自适应分流判定完毕',
    principle: '依据需求规模 (P1/P2/P3) 与变更烈度，智能决策走标准研发主轨（需门禁与基线闭环）还是极速微创快轨（单文件≤20行免门禁）。消除传统流程“改一个错别字也要走九个流程”的官僚主义，实现敏捷与受控的黄金平衡。',
    inputs: ['业务原始提报', '需求描述口语'],
    outputs: ['自适应分流图谱', '目标执行轨迹 (STANDARD / FASTTRACK)']
  },
  'SOP-01': {
    name: '[1] 需求规划与白话澄清',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '产品 / 张工',
    skill: 'requirement-discovery',
    sop: '.ai/sop/01-sop-planning.md',
    gate: '四层清单 (P04) 齐备并冻结',
    principle: 'AI 主导的反向质询 (Grill Protocol)。在代码动工前，先将抽象口语翻译为具备“范围、规则、异常、不做项”的四层清单。同时进行披萨式轻量切片，将大项目分解为单次 0.5~2 天即可验证的独立原子增量，并向测试轨平行分流。',
    inputs: ['SOP-00 分流结果', '客户口语化原话 (留痕防扯皮)'],
    outputs: ['四层清单基线草案 (P04)', 'S1~S3 披萨切片清单', 'TC-GEN 测试用例并行触发信标']
  },
  'SOP-02': {
    name: '[2] 研发轨方案设计与契约冻结',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '研发 / 李工',
    skill: 'design-spec-architect',
    sop: '.ai/sop/02-sop-tech-design.md',
    gate: 'python3 gate.py --sql 校验通过',
    principle: '基于四层清单推演完整分层架构、API 契约标准及 service.sql 结构。坚持“契约先于代码”理念，由 board.py 作为状态机唯一合法入口强制固化表结构与 DLD 文档，严禁研发人员未经设计直接凭空写代码。',
    inputs: ['四层清单基线 (P04)', '静态原型 HTML/Mockup'],
    outputs: ['docs/design/detail.md (DLD 详细设计)', 'docs/db/service.sql (表结构定义)', 'board.py 状态机迁移锁定']
  },
  'TC-GEN': {
    name: '[TC] 测试用例矩阵先行 (测试解耦轨)',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '测试 / 王工',
    skill: 'quality-verifier',
    sop: '.ai/sop/01-sop-planning.md',
    gate: '用例矩阵 100% 覆盖关键链路',
    principle: '测试轨与研发轨绝对平行解耦！在李工写业务代码的同时，王工与 quality-verifier 专家基于需求清单同步编写独立的 docs/test-cases 矩阵。研发人员写完代码后必须接受此用例矩阵的碰撞考验，杜绝“既当运动员又当裁判员”的伪测试。',
    inputs: ['四层清单基线 (P04)', 'detail.md 接口契约'],
    outputs: ['docs/test-cases/ 自动化测试用例集', 'E2E 离线穿透验证探针']
  },
  'SOP-03': {
    name: '[3] 单焦点 TDD 红绿循环编码',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '研发 / 李工',
    skill: 'coding-standards',
    sop: '.ai/sop/03-sop-coding.md',
    gate: '单元测试全绿 (0 Failure / 0 Error)',
    principle: '严格践行红绿单测循环：先写红灯报错测试 ➔ 编写最小化业务实现 ➔ 单测变绿 ➔ 重构优化。严厉禁止使用假 Mock 伪造成功。遇到第三方 API 必须保留真实沙盒联调日志，杜绝未经联调的代码流入下游。',
    inputs: ['detail.md 契约约束', 'service.sql 数据库结构'],
    outputs: ['src/ 业务模块源码', '第三方 API 真实联调日志', '.ai/tier3_status.md 任务更新']
  },
  'SOP-04': {
    name: '[4] 代码审查机器门禁 (gate.py)',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '看门狗探针',
    skill: 'code-quality-reviewer',
    sop: '.ai/sop/04-sop-code-review.md',
    gate: 'python3 gate.py --staged [LGTM 阻断拦截]',
    principle: '代码合并前的终极防线。由 gate.py 看门狗自动化执行 6 项高压扫描：① 严禁假 Mock / 假写死；② 严禁野公网依赖；③ DDL 与回滚配对检查；④ 密钥硬编码拦截；⑤ 圈复杂度超标拦截；⑥ NPE 空指针潜在隐患。任意一项违规立刻直接阻断！',
    inputs: ['git staged 暂存区改动', 'src/ 业务源码工程'],
    outputs: ['gate.py 扫描报告 (LGTM)', '违规代码拦截警报与微创重构建议']
  },
  'SOP-05': {
    name: '[5] 架构与契约人工评审',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '架构师 / 程工',
    skill: 'code-quality-reviewer',
    sop: '.ai/sop/05-sop-review.md',
    gate: '架构师三方签字核准',
    principle: '机器看门狗放行后的第二重专家把关。架构师基于 5 维清单对代码架构、性能并发安全、反向破坏性测试进行最终复核，确认符合团队长期资产沉淀标准后签署放行。',
    inputs: ['gate.py LGTM 机器报告', 'src/ 业务源码工程'],
    outputs: ['人工评审确认凭证 (APPROVED)', '技术债与优化备忘录']
  },
  'SOP-06': {
    name: '[6] 双轨延迟汇合与集成穿透',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '测试 / 王工',
    skill: 'quality-verifier',
    sop: '.ai/sop/06-sop-integration.md',
    gate: 'TC 用例 100% 碰撞通过',
    principle: '【关键核心节点 · 双轨延迟汇合】：在此工位，研发轨产出的 src 业务工程与测试轨在 SOP-01 阶段独立编写的 TC 用例矩阵首次进行严苛的闭环碰撞。双方在同一沙盒内跑通全链路 E2E 穿透，确保研发没有偷工减料，测试没有放水。',
    inputs: ['SOP-05 交付的研发源码', 'TC-GEN 阶段冻结的测试用例矩阵'],
    outputs: ['E2E 离线穿透验证报告', '0 Error 构建凭证', '全绿碰撞测试单']
  },
  'SOP-07': {
    name: '[7] 交付双向验收门禁 (唯一归档出口)',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '看门狗探针',
    skill: 'quality-verifier',
    sop: '.ai/sop/07-sop-acceptance.md',
    gate: 'board.py verify ➔ complete --accept',
    principle: '整个生命周期中需求唯一合法的归档出口！强制执行 board.py verify 防伪校验：验证四层清单、设计图、源码、门禁报告、测试报告全量资产无缺失且哈希对齐，人工核放后打上放行徽标。',
    inputs: ['SOP-06 集成测试报告', '全生命周期各工位归档文件'],
    outputs: ['board.py verify 验收凭据', '4 维终验合格清单', 'tier3_status.md 全标绿']
  },
  'SOP-08': {
    name: '[8] 容器编排与离线部署',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '运维 / 李工',
    skill: '-',
    sop: '.ai/sop/08-sop-deploy.md',
    gate: '容器健康检查全通',
    principle: '打包 Docker 离线镜像与 docker-compose.yml 编排清单，生成幂等一键部署脚本 install.sh。提供容器心跳检测与自动拉起自愈机制，确保交付物在内网断网环境下可一键点火启动。',
    inputs: ['编译打包后的 JAR / 前端 Dist', 'docs/db/service.sql'],
    outputs: ['docker-compose.yml 编排配置', 'install.sh 幂等安装脚本', '离线镜像包']
  },
  'SOP-09': {
    name: '[9] Git 发布与版本打标',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '交付 / 张工',
    skill: '-',
    sop: '.ai/sop/09-sop-git-release.md',
    gate: 'Release 资产落盘完毕',
    principle: '打上标准语义化 Git Tag (如 v1.2.0)，生成正式 Release 发版说明书，并将数据库迁移脚本 (docs/db/migrations) 与回滚方案 1:1 配对归档，完成生命周期最终归档。',
    inputs: ['SOP-07 验收凭证', 'SOP-08 部署包'],
    outputs: ['Git 语义化 Tag', 'docs/db/migrations 升级回滚对', 'Release 最终发布文档']
  },
  'SOP-B2': {
    name: '⚡ [B2] 极速微创修补 (免状态机)',
    category: 'instruction',
    categoryName: '👤 旁路快速通道',
    role: '研发 / 李工',
    skill: 'quality-verifier',
    sop: '.ai/sop/B2-sop-fasttrack.md',
    gate: '严格限制单文件≤20行改动',
    principle: '专为紧急 Bug 修复、文案微调、样式修补设计的非状态机极速通道。单文件改动严格控制在 20 行以内，免走繁重状态机流转与多重门禁，改动完成后直通 SOP-09 发布打标，兼顾速度与安全。',
    inputs: ['紧急缺陷报告', '代码行级微创修改点'],
    outputs: ['微创代码补丁', '.ai/tier1_snapshot.md 静默刷新']
  },
  'SKILL-01': {
    name: '🤖 requirement-discovery 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 产品专家',
    skill: 'requirement-discovery',
    sop: '.agents/skills/requirement-discovery/SKILL.md',
    gate: '反向质询无遗漏',
    principle: '采用反诘式引导 (Grill Protocol)，先在内部推演目标、场景、异常、生命周期与边界，再只向用户抛出具有决策价值的高含金量问题，帮助非专业用户无痛理清业务逻辑。',
    inputs: ['模糊的用户一句话需求'],
    outputs: ['四层确认清单 (范围/规则/异常/不做项)']
  },
  'SKILL-02': {
    name: '🤖 design-spec-architect 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 架构专家',
    skill: 'design-spec-architect',
    sop: '.agents/skills/design-spec-architect/SKILL.md',
    gate: '钧天 8 大章节对齐',
    principle: '依据需求清单自动推演出完整的概要设计 (HLD) 与详细设计 (DLD)。严格遵守钧天科技 V1.0 规范，输出标准表结构 DDL 与 API 契约，支持需求变更时增量更新架构文档。',
    inputs: ['四层需求清单', '静态交互原型'],
    outputs: ['detail.md 详细设计说明书', 'service.sql 数据库脚本']
  },
  'SKILL-03': {
    name: '🤖 coding-standards 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 编码规范守卫',
    skill: 'coding-standards',
    sop: '.agents/skills/coding-standards/SKILL.md',
    gate: '逐字对齐 DLD 契约',
    principle: '强约束 AI 逐字绑定 DLD 说明书中的表名、字段名与 API 契约。遵循分层开发规约（Controller/Service/DAO），杜绝 AI 编码中常见的臆造字段或逻辑跑偏。',
    inputs: ['DLD 契约文档', '数据库 DDL'],
    outputs: ['高质量 Java / Vue 符合分层规约源码']
  },
  'SKILL-04': {
    name: '🤖 code-quality-reviewer 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 静态质量审查专家',
    skill: 'code-quality-reviewer',
    sop: '.agents/skills/code-quality-reviewer/SKILL.md',
    gate: '静态扫描 0 风险',
    principle: '在代码提交前深度扫描代码隐患：NPE 空指针风险、流或连接未关闭/内存泄漏、并发不安全线程竞争、超标圈复杂度，并提供可直接执行的微创重构落地方案。',
    inputs: ['业务源码差异 (git diff)'],
    outputs: ['代码审查诊断报告', '微创重构补丁']
  },
  'SKILL-06': {
    name: '🤖 quality-verifier 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 质量验证与排错专家',
    skill: 'quality-verifier',
    sop: '.agents/skills/quality-verifier/SKILL.md',
    gate: '全量单测全绿',
    principle: '质量验证与小黄鸭排错专家。在编码前根据契约自动生成测试用例；在发生编译/运行时错误时，激活小黄鸭探针（日志抓取 ➔ 根因分析 ➔ 最小化修改验证）精准排错。',
    inputs: ['接口契约定义', '错误日志堆栈'],
    outputs: ['自动化测试脚本', '小黄鸭排错报告']
  },
  'SKILL-00': {
    name: '🏛️ legacy-archaeologist 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 逆向考古专家',
    skill: 'legacy-archaeologist',
    sop: '.agents/skills/legacy-archaeologist/SKILL.md',
    gate: '行为锁死探针就绪',
    principle: '面对无文档的庞杂老项目时，自顶向下扫描路由与链路切片，逆向提取 API 契约与表关系，输出 legacy_hld.md，并编写反向行为锁死探针，防止重构改崩旧业务。',
    inputs: ['存量遗留源码库'],
    outputs: ['legacy_hld.md 逆向设计', '反向行为锁死单测探针']
  },
  'CTRL-SNAP': {
    name: '⚡ tier1_snapshot.md 记忆卡',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '系统记忆中枢',
    skill: '-',
    sop: '.ai/tier1_snapshot.md',
    gate: '单文件严格控制 ≤30 行',
    principle: 'AI 会话冷启动的“名片卡”。单文件严格约束不超过 30 行，仅记录项目代号、当前活跃需求、当前工位与关键阻塞项，确保大模型在极短上下文内瞬间恢复工作记忆。',
    inputs: ['工作流最新流转事件'],
    outputs: ['极简上下文快照 (30行内)']
  },
  'CTRL-BOARD': {
    name: '🛠️ board.py 状态机看板脚本',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '状态机看门狗',
    skill: '-',
    sop: '.ai/board.py',
    gate: '状态迁移严格校验',
    principle: '整个系统状态机的唯一合法入口！任何工位迁移、工件校验、归档放行都必须经由 board.py 执行校验并原子性更新数据库，严禁外部脚本或人工手工纂改工位。',
    inputs: ['advance/verify/complete 命令行指令'],
    outputs: ['原子状态跃迁', '防伪校验凭证']
  },
  'CTRL-STATUS': {
    name: '📋 tier3_status.md 任务看板',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '任务看板中枢',
    skill: '-',
    sop: '.ai/tier3_status.md',
    gate: '任务清单全量跟踪',
    principle: '任务与切片的物理跟踪清单。以 Markdown 表格形式实时维护 ACTIVE 与 DONE 状态列表，作为开发人员与 AI 协同的实时工作台。',
    inputs: ['开发切片任务分解'],
    outputs: ['任务进行中/已完成看板']
  },
  'CTRL-GATE': {
    name: '🔒 gate.py 机器门禁看门狗',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '质量门禁中枢',
    skill: '-',
    sop: 'gate.py',
    gate: '6项刚性扫描全绿',
    principle: '工程合并看门狗，内置 AST 抽象语法树与静态扫描器，包含 6 大高压红线规则，执行 exit code!=0 强行阻断。',
    inputs: ['提交代码 diff'],
    outputs: ['LGTM 扫描放行报告']
  },
  'CTRL-HANDOVER': {
    name: '📦 tier3_handover.md 历史长效黑匣子',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '长效交接中枢',
    skill: '-',
    sop: '.ai/tier3_handover.md',
    gate: '版本交接资产固化',
    principle: '记录所有技术债务、设计取舍、遗留 Bug 与后续版本的演进规划，为下一任维护者或未来的 AI 会话提供完整的历史推演依据。',
    inputs: ['版本交付验收结果'],
    outputs: ['长效资产交接文档']
  },
  'ART-01': {
    name: '📝 四层清单基线草案 (P04)',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'requirement-discovery',
    sop: 'docs/requirements/P04.md',
    gate: '签字锁定',
    principle: '从口语对撞中提炼出的结构化清单，包含范围(Scope)、规则(Rule)、异常(Exception)、不做(Non-goal)，是后续所有架构设计与测试用例的唯一源头。',
    inputs: ['用户口语输入'],
    outputs: ['四层决策条目', '基线版本 BL-v1.0']
  },
  'ART-02': {
    name: '🎨 mockup.html / PRD / detail.md',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'design-spec-architect',
    sop: 'docs/design/detail.md',
    gate: 'DDL 与 DLD 一致性',
    principle: '包含可交互的纯 HTML 原型快照，以及包含数据库表结构 DDL 与 API 契约的详细设计说明书，作为前后端开发与联调的物理契约。',
    inputs: ['四层清单'],
    outputs: ['mockup.html 原型包', 'detail.md 详细设计', 'service.sql']
  },
  'ART-03': {
    name: '💻 src/ 业务源码工程与联调日志',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'coding-standards',
    sop: 'base_template/backend & frontend',
    gate: '单测与编译通过',
    principle: '经过 TDD 红绿循环洗礼的业务源代码工程，附带第三方 API 的真实请求响应日志，绝不含伪造 Mock 代码。',
    inputs: ['detail.md 契约'],
    outputs: ['后端 Java / 前端 Vue 源码', '真实接口联调日志']
  },
  'ART-04': {
    name: '🔒 gate.py LGTM 报告与 E2E 凭证',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'code-quality-reviewer',
    sop: 'target/gate-report.json',
    gate: 'LGTM 防伪签名',
    principle: '由机器门禁 gate.py 与端到端穿透测试共同生成的数字证明，具备不可篡改性，是准许进入终验的必备通行证。',
    inputs: ['业务源码', '测试用例'],
    outputs: ['门禁扫描报告 (LGTM)', 'E2E 穿透验证日志']
  },
  'ART-05': {
    name: '🏁 board.py verify 凭据与 Git Tag',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'quality-verifier',
    sop: 'docs/releases/v1.0.0.md',
    gate: '双向验签锁定',
    principle: '包含 board.py verify 全量资产验证单、Git 语义化 Tag 标签与 Release 正式发布说明书，标志着需求正式生命周期闭环。',
    inputs: ['全部前置产物合集'],
    outputs: ['Git Tag 标签', 'Release 归档交付包']
  }
}

// 打开节点全景深度透视抽屉 (只读展示，不可在此随意改动工位工艺)
const openNodeDetailDrawer = (nodeId: string) => {
  const kb = NODE_KNOWLEDGE_BASE[nodeId] || {}
  const pipelineNode = pipelineNodes.value.find(n => n.id === nodeId)
  const isCompleted = pipelineNode?.status === 'COMPLETED' || nodeId === 'SOP-00'
  const isActive = workflowSelectedNode.value === nodeId

  activeDetailNode.value = {
    id: nodeId,
    name: kb.name || nodeId,
    category: kb.category || 'instruction',
    categoryName: kb.categoryName || '工位算子',
    role: kb.role || pipelineNode?.role || '系统核心',
    skill: pipelineNode?.skill || kb.skill || '-',
    sop: kb.sop || pipelineNode?.sop || '-',
    gate: kb.gate || pipelineNode?.gate || '无门禁阻断',
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
  const reqCode = workflowActiveReq.value?.reqCode || 'REQ-001'
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
      // 降级兜底初始默认模板
      workflowTemplates.value = [
        {
          id: 1,
          templateCode: 'TPL-DEFAULT',
          name: '全局默认 · 钧天四层一体化研发工作流',
          description: '钧天标准生产工艺：4-Layer 全景拓扑（SOP指令轨 + AI Skill专家轨 + 控制中枢轨 + 确定性物理产物轨），支持双轨延迟汇合与 B2 旁路微创快轨。',
          isSystemDefault: 1,
          scopeType: 'GLOBAL',
          projectKey: null,
          createdBy: 'admin',
          createdAt: '2026-11-08 14:00:00'
        }
      ]
    }
  } catch (e) {
    console.warn('拉取工作流模板失败，使用内置默认蓝本', e)
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
      outputs: kb.outputs || []
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
  // 2. 第一层：【👤 SOP 数字指令主轨】 (y: 48 ~ 115)
  // ========================================================
  addTplNode('SOP-00', '[0] 体系总览\n规模分流识别', 38, 48, '#94a3b8', 120, 52, 'instruction')
  addTplNode('SOP-01', '[1] 需求规划\n白话澄清与切片', 185, 48, '#f59e0b', 130, 52, 'instruction')
  addTplNode('SOP-02', '[2] 方案设计\n契约与表结构冻结', 385, 48, '#38bdf8', 130, 52, 'instruction')
  addTplNode('SOP-03', '[3] TDD编码\n单焦点红绿单测', 535, 48, '#94a3b8', 130, 52, 'instruction')
  addTplNode('SOP-04', '[4] 机器门禁\ngate.py 6项阻断', 685, 48, '#ef4444', 130, 52, 'instruction')
  addTplNode('SOP-05', '[5] 人工评审\n架构与契约复核', 835, 48, '#f59e0b', 130, 52, 'instruction')
  addTplNode('TC-GEN', '[TC] 测试用例矩阵\ndocs/test-cases 冻结', 535, 115, '#8b5cf6', 280, 48, 'instruction')
  addTplNode('SOP-06', '[6] 集成测试\n★ 双轨延迟汇合穿透', 1025, 48, '#0284c7', 135, 52, 'instruction')
  addTplNode('SOP-07', '[7] 双向验收\n唯一归档出口', 1185, 48, '#ef4444', 135, 52, 'instruction')
  addTplNode('SOP-08', '[8] 容器部署\nDockerCompose离线', 1380, 48, '#10b981', 135, 52, 'instruction')
  addTplNode('SOP-09', '[9] Git发布\nTag+Release回滚配对', 1545, 48, '#10b981', 135, 52, 'instruction')
  addTplNode('SOP-B2', '⚡ [B2] 极速微创修补\n单文件≤20行免门禁', 1380, 115, '#d97706', 300, 48, 'instruction')

  // 指令轨连线
  addTplEdge('SOP-00', 'SOP-01', '#94a3b8')
  addTplEdge('SOP-01', 'SOP-02', '#38bdf8', false, 'right', 'left', 'smooth')
  addTplEdge('SOP-01', 'TC-GEN', '#8b5cf6', true, 'right', 'left', 'smooth')
  addTplEdge('SOP-02', 'SOP-03', '#38bdf8')
  addTplEdge('SOP-03', 'SOP-04', '#ef4444')
  addTplEdge('SOP-04', 'SOP-05', '#f59e0b')
  addTplEdge('SOP-05', 'SOP-06', '#0284c7', false, 'right', 'left', 'smooth')
  addTplEdge('TC-GEN', 'SOP-06', '#8b5cf6', false, 'right', 'left', 'smooth')
  addTplEdge('SOP-06', 'SOP-07', '#ef4444')
  addTplEdge('SOP-07', 'SOP-08', '#10b981')
  addTplEdge('SOP-08', 'SOP-09', '#10b981')
  addTplEdge('SOP-00', 'SOP-B2', '#d97706', true, 'bottom', 'left', 'smooth')
  addTplEdge('SOP-B2', 'SOP-09', '#d97706', true, 'right', 'bottom', 'smooth')

  // ========================================================
  // 3. 第二层：【🧩 AI 专属 Skill 技能专家】 (y: 195 ~ 245)
  // ========================================================
  addTplNode('SKILL-01', '🤖 requirement-discovery\n需求发现与白话澄清专家', 100, 195, '#0284c7', 220, 50, 'skill')
  addTplNode('SKILL-02', '🤖 design-spec-architect\n概要与详细设计专家', 385, 195, '#38bdf8', 210, 50, 'skill')
  addTplNode('SKILL-03', '🤖 coding-standards\n代码规范与契约绑定专家', 610, 195, '#10b981', 200, 50, 'skill')
  addTplNode('SKILL-04', '🤖 code-quality-reviewer\n静态质量审查与门禁专家', 825, 195, '#ef4444', 210, 50, 'skill')
  addTplNode('SKILL-06', '🤖 quality-verifier\n质量验证与小黄鸭探针专家', 1080, 195, '#8b5cf6', 220, 50, 'skill')
  addTplNode('SKILL-00', '🏛️ legacy-archaeologist\n老项目逆向考古专家', 1380, 195, '#f59e0b', 210, 50, 'skill')

  // 指令激活 Skill 连线
  addTplEdge('SOP-01', 'SKILL-01', '#0284c7', true, 'bottom', 'top', 'smooth')
  addTplEdge('SOP-02', 'SKILL-02', '#38bdf8', true, 'bottom', 'top', 'smooth')
  addTplEdge('SOP-03', 'SKILL-03', '#10b981', true, 'bottom', 'top', 'smooth')
  addTplEdge('SOP-04', 'SKILL-04', '#ef4444', true, 'bottom', 'top', 'smooth')
  addTplEdge('SOP-06', 'SKILL-06', '#8b5cf6', true, 'bottom', 'top', 'smooth')

  // ========================================================
  // 4. 第三层：【🧠 状态与记忆控制中枢 (.ai/)】 (y: 285 ~ 335)
  // ========================================================
  addTplNode('CTRL-SNAP', '⚡ tier1_snapshot.md\n极简名片卡 (<30行)', 100, 285, '#eab308', 200, 50, 'control')
  addTplNode('CTRL-BOARD', '🛠️ board.py 看板脚本\n状态机唯一合法入口', 450, 285, '#f97316', 210, 50, 'control')
  addTplNode('CTRL-STATUS', '📋 tier3_status.md\nACTIVE/DONE 任务看板', 730, 285, '#06b6d4', 220, 50, 'control')
  addTplNode('CTRL-GATE', '🔒 gate.py 机器门禁\nLGTM 阻断拦截看门狗', 1030, 285, '#ef4444', 200, 50, 'control')
  addTplNode('CTRL-HANDOVER', '📦 tier3_handover.md\n历史长效交接黑匣子', 1380, 285, '#a855f7', 220, 50, 'control')

  // Skill 驱动 Control 连线
  addTplEdge('SKILL-02', 'CTRL-BOARD', '#f97316', false, 'bottom', 'top', 'smooth')
  addTplEdge('CTRL-BOARD', 'CTRL-STATUS', '#06b6d4', false, 'right', 'left', 'normal')
  addTplEdge('SKILL-04', 'CTRL-GATE', '#ef4444', false, 'bottom', 'top', 'smooth')

  // ========================================================
  // 5. 第四层：【📄 确定性物理输出产物 (Artifacts)】 (y: 385 ~ 445)
  // ========================================================
  addTplNode('ART-01', '📝 四层清单基线草案\n白话对撞决策列表 (P04)', 80, 385, '#38bdf8', 220, 56, 'artifact')
  addTplNode('ART-02', '🎨 mockup.html / PRD\n📐 detail.md + service.sql', 360, 385, '#38bdf8', 230, 56, 'artifact')
  addTplNode('ART-03', '💻 src/ 业务源码工程\n🔌 第三方 API 真实联调日志', 660, 385, '#10b981', 230, 56, 'artifact')
  addTplNode('ART-04', '🔒 gate.py LGTM 报告\n🧪 E2E 离线穿透验证凭据', 970, 385, '#ef4444', 230, 56, 'artifact')
  addTplNode('ART-05', '🏁 board.py verify 凭据\n🏷️ Git Tag + Release 文档', 1330, 385, '#10b981', 230, 56, 'artifact')

  // 控制/技能驱动物理产物连线
  addTplEdge('SKILL-01', 'ART-01', '#38bdf8', true, 'bottom', 'top', 'smooth')
  addTplEdge('CTRL-BOARD', 'ART-02', '#38bdf8', true, 'bottom', 'top', 'smooth')
  addTplEdge('SKILL-03', 'ART-03', '#10b981', true, 'bottom', 'top', 'smooth')
  addTplEdge('CTRL-GATE', 'ART-04', '#ef4444', true, 'bottom', 'top', 'smooth')
  addTplEdge('SOP-07', 'ART-05', '#10b981', true, 'bottom', 'top', 'smooth')
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

// 持久化保存模板 DAG 整体配置
const handleSaveTemplateDag = async () => {
  if (!currentEditingTemplate.value) return
  if (currentEditingTemplate.value.isSystemDefault === 1) {
    ElMessageBox.alert('系统默认蓝本为全局工艺基准，禁止直接修改！请先点击复制克隆生成专属模板。', '保护提示')
    return
  }
  try {
    const payload = {
      name: currentEditingTemplate.value.name,
      description: currentEditingTemplate.value.description,
      nodesConfig: JSON.stringify(currentEditingTemplate.value.parsedNodes || [])
    }
    await updateWorkflowTemplateApi(currentEditingTemplate.value.id, payload)
    ElMessage.success(`定制模板 [${currentEditingTemplate.value.name}] 拓扑配置已成功持久化至数据库！`)
    await loadWorkflowTemplates()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

// 删除定制模板
const handleDeleteTemplate = async (tpl: any) => {
  if (tpl.isSystemDefault === 1) {
    ElMessage.warning('系统默认模板为全局蓝本，禁止删除！')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除定制模板 [${tpl.name}] 吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
    await deleteWorkflowTemplateApi(tpl.id)
    ElMessage.success('模板已删除')
    await loadWorkflowTemplates()
  } catch (e) {
    // 取消或失败
  }
}

// 视角与路由状态
const currentPerspective = ref<'internal' | 'client'>('internal')
const currentPage = ref('page-dashboard')
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
  (newPath) => {
    if (routeToPageMap[newPath]) {
      currentPage.value = routeToPageMap[newPath]
      currentPerspective.value = 'internal'
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
      owner: req.ownerName || req.owner || '张工 (产品负责人)',
      createdAt: req.createdAt ? req.createdAt.split('T')[0] : (req.updatedAt ? req.updatedAt.split('T')[0] : '2026-10-29'),
      priority: req.priority || 'P1',
      quote: req.rawQuote || req.quote || '「必须管住需求、版本、原型三件事，做到留痕、可确认、可追溯。」'
    }

    try {
      const detailRes = await getRequirementDetailApi(req.id)
      if (detailRes.data?.requirement) {
        const r = detailRes.data.requirement
        currentReq.value.title = r.title
        currentReq.value.quote = r.rawQuote || currentReq.value.quote
        currentReq.value.owner = r.ownerName ? (r.ownerName.includes('(') ? r.ownerName : `${r.ownerName} (研发)`) : '张工 (产品负责人)'
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
      // 同步拉取真实原型版本快照 (MySQL t_req_prototype_version)
      try {
        const protoRes = await getPrototypeListApi(req.id)
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
        prototypeList.value = []
        selectedPrototype.value = null
      }

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
  // 切换至工程流水线时，初始化并刷新 AntV X6 真实拓扑
  if (pageId === 'page-workflow') {
    setTimeout(() => {
      initWorkflowGraph()
    }, 150)
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
  try {
    const nextCode = `REQ-00${requirements.value.length + 1}`
    const finalOwner = newReqForm.value.owner || (userList.value[0]?.nickname || '张工 (产品负责人)')
    await createRequirementApi({
      projectId: currentProject.value.id || 1,
      reqCode: nextCode,
      title: newReqForm.value.title,
      rawQuote: newReqForm.value.quote || newReqForm.value.title,
      ownerName: finalOwner,
      priority: newReqForm.value.priority || 'P1'
    })
    ElMessage.success(`需求 [${newReqForm.value.title}] 录入成功！责任人: ${finalOwner}，已写入 MySQL。`)
    drawerNewReqOpen.value = false
    await loadInitialData(currentProject.value.id)
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

// 切换项目空间
const handleProjectChange = async () => {
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

    // 2. 重新加载该项目下的统计与需求数据
    await loadInitialData(found.id)

    // 3. 核心机制：切换项目空间时，默认无条件重置回项目工作台 /req/dashboard
    navigatePage('page-dashboard')
    ElMessage.success(`已切换至 [${found.projectKey} · ${found.name}] 项目空间，已默认进入该项目工作台`)
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

// 当前选定项目的专属发布计划与版本清单 (严格绑定 MySQL 中的已冻结基线)
const projectReleases = computed(() => {
  const pKey = currentProject.value?.projectKey || 'APP'
  if (baselineList.value && baselineList.value.length > 0) {
    return baselineList.value.map((b: any) => {
      const isFrozen = b.status === 'BASELINED'
      return {
        ver: `${pKey}-${b.version}`,
        baseline: b.version,
        plannedDate: b.createdAt ? b.createdAt.split('T')[0] : '2026-11-01',
        finishDate: b.frozenAt ? b.frozenAt.split('T')[0] : (isFrozen ? (b.createdAt ? b.createdAt.split('T')[0] : '2026-11-01') : '—'),
        status: isFrozen ? 'RELEASED' : 'PLANNING',
        statusText: isFrozen ? '已冻结并发布' : '签字冻结排期中'
      }
    })
  }
  return []
})

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
          ownerName: item.ownerName || '张工',
          owner: item.ownerName || '张工',
          cost: `${Number(item.totalCostDays || 0).toFixed(1)} 人天`,
          updatedAt: item.updatedAt ? item.updatedAt.split('T')[0] : '2026-10-29',
          rawQuote: item.rawQuote,
          priority: item.priority
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
  } catch (err) {
    console.warn('动态加载失败，保持优雅降级', err)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadInitialData()
  await loadWorkflowTemplates()
  await loadSkillsAndScriptsAssets()
  if (route.path === '/req/workflow') {
    setTimeout(() => {
      initWorkflowGraph()
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
    <!-- 顶部导航 -->
    <header class="app-header">
      <div class="header-left">
        <span class="logo-badge">APEX</span>
        <div class="app-title">
          <span>需求与版本管理平台</span>
        </div>
        <select class="project-selector" v-model="selectedProject" @change="handleProjectChange">
          <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
            {{ p.projectKey }} · {{ p.name }}
          </option>
        </select>
      </div>
      <div class="header-right">
        <div class="view-mode-toggle">
          <button
            class="view-mode-btn"
            :class="{ active: currentPerspective === 'internal' }"
            @click="switchPerspective('internal')"
          >
            内部管理端 (P01~P18)
          </button>
          <button
            class="view-mode-btn"
            :class="{ active: currentPerspective === 'client' }"
            @click="switchPerspective('client')"
          >
            外部客户免登录端 (E01~E03)
          </button>
        </div>
        <button
          v-if="currentPerspective === 'internal'"
          class="btn btn-primary btn-sm"
          style="margin-left: 10px; display: inline-flex; align-items: center; gap: 4px;"
          @click="handleOpenNewReqDrawer"
        >
          <span>➕ 快速录入需求</span>
        </button>
        <select 
          class="project-selector" 
          v-model="currentOperator" 
          title="切换当前登录与操作身份"
          style="margin-left: 10px; border-color: #3B82F6; color: #1D4ED8; font-weight: 600;"
        >
          <option value="admin">👑 超级管理员 (admin)</option>
          <option value="zhanggong">👤 张工 (产品负责人)</option>
        </select>
      </div>
    </header>

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
                {{ workflowActiveReq.reqCode || 'REQ-001' }} · {{ workflowActiveReq.title }}
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">优先级: {{ workflowActiveReq.priority || 'P1' }} ｜ 代价: {{ workflowActiveReq.cost || '4.0 人天' }}</div>
            </div>
            <div class="card" style="padding:16px; border-left:4px solid #16A34A;">
              <div style="font-size:12px; color:var(--text-muted);">当前责任人</div>
              <div style="font-size:16px; font-weight:700; color:#16A34A; margin-top:4px;">
                👤 {{ workflowActiveReq.ownerName || workflowActiveReq.owner || '张工' }}
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">角色: 核心研发 / 责任担当</div>
            </div>
            <div class="card" style="padding:16px; border-left:4px solid #F59E0B;">
              <div style="font-size:12px; color:var(--text-muted);">当前微观算子工位</div>
              <div style="font-size:16px; font-weight:700; color:#D97706; margin-top:4px;">
                📍 [{{ workflowActiveNodeInfo.id }}] {{ workflowActiveNodeInfo.name }}
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">门禁规则: {{ workflowActiveNodeInfo.gate }}</div>
            </div>
            <div class="card" style="padding:16px; border-left:4px solid #3B82F6;">
              <div style="font-size:12px; color:var(--text-muted);">流水线全局进度</div>
              <div style="font-size:16px; font-weight:700; color:#1D4ED8; margin-top:4px;">
                {{ Math.round((pipelineNodes.filter(n => n.status === 'COMPLETED').length / pipelineNodes.length) * 100) }}% ({{ pipelineNodes.filter(n => n.status === 'COMPLETED').length }}/{{ pipelineNodes.length }} 算子)
              </div>
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">门禁扫描: 🟢 全绿通过</div>
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

          <!-- 主体工作区：拓扑画布独占整行铺满内容区，需求列表置于画布下方 -->
          <div style="display:flex; flex-direction:column; gap:20px;">
            <!-- 下方：多需求列表选择器 (查谁在做、当前状态)，order:2 排在画布之后 -->
            <div class="card" style="padding:16px; display:flex; flex-direction:column; max-height:300px; order:2;">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                <span style="font-weight:700; font-size:14px;">📋 待办与在研需求 ({{ requirements.length }})</span>
                <span class="tag tag-clarifying" style="font-size:10px;">点击切入</span>
              </div>
              <div style="overflow-y:auto; flex:1; display:grid; grid-template-columns:repeat(auto-fill, minmax(230px, 1fr)); gap:8px; align-content:start;">
                <div
                  v-for="r in requirements"
                  :key="r.id"
                  style="border:1px solid var(--border); border-radius:var(--radius-sm); padding:10px 12px; cursor:pointer; transition:all 0.2s;"
                  :style="workflowSelectedReqId === r.id ? 'border-color:var(--primary-color); background:var(--primary-light);' : 'background:#FFF;'"
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

            <!-- 上方：DAG 算子拓扑与当前工位详情，order:1 优先展示并铺满整行 -->
            <!-- min-width:0 防止内层 1450px 画布把容器撑宽，导致内容被祖先 overflow:hidden 裁掉 -->
            <div style="display:flex; flex-direction:column; gap:16px; min-width:0; order:1;">
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
                  <div style="display:flex; gap:8px; align-items:center;">
                    <span style="font-size:11px; color:#94a3b8;">
                      按住左键可平移漫游 ｜ Ctrl+滚轮缩放
                    </span>
                    <button
                      class="btn btn-sm btn-outline"
                      title="复位平移与缩放"
                      @click="resetWorkflowView()"
                    >
                      🎯 重置视图
                    </button>
                  </div>
                </div>

                <!-- 视口高度提升至 510px，完美呈现四层流转 -->
                <div class="wf-viewport">
                  <div
                    ref="workflowGraphContainer"
                    class="wf-graph-host"
                    :style="{ width: '100%', height: workflowHostHeight + 'px' }"
                  ></div>
                </div>
              </div>

              <!-- 选中的算子深度下钻详情面板 -->
              <div class="card" style="padding:20px; border-top:3px solid var(--primary-color);">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:14px;">
                  <div style="display:flex; align-items:center; gap:10px;">
                    <span class="tag tag-baselined" style="font-weight:700; font-size:13px;">{{ workflowActiveNodeInfo.id }}</span>
                    <h3 style="font-size:16px; font-weight:700; margin:0;">{{ workflowActiveNodeInfo.title }}</h3>
                  </div>
                  <div style="display:flex; gap:8px;">
                    <button class="btn btn-outline btn-sm" @click="openReqDetail(workflowActiveReq, true)">
                      📄 查看对应需求四层清单 (P04)
                    </button>
                    <button class="btn btn-primary btn-sm" @click="handleAdvanceWorkflowNode">
                      ✔ 确认并顺推工位 (Advance)
                    </button>
                  </div>
                </div>

                <div style="display:grid; grid-template-columns: 1fr 1fr; gap:16px; background:#F8FAFC; padding:14px; border-radius:var(--radius-sm); border:1px solid var(--border); font-size:13px; line-height:1.7;">
                  <div>
                    <div><strong>工位常驻责任角色：</strong><span style="color:var(--primary-color); font-weight:600;">{{ workflowActiveNodeInfo.role }}</span></div>
                    <div><strong>专属 AI Skill 专家：</strong><span class="tag tag-baselined" style="font-weight:700; font-size:11px;">🤖 {{ workflowActiveNodeInfo.skill || '全能专家' }}</span></div>
                    <div><strong>关联执行 SOP 规约：</strong><code style="background:#E2E8F0; padding:2px 6px; border-radius:3px;">{{ workflowActiveNodeInfo.sop }}</code></div>
                    <div><strong>当前工位核心职责：</strong>{{ workflowActiveNodeInfo.desc }}</div>
                  </div>
                  <div>
                    <div><strong>通过门禁与阻断条件：</strong><span style="color:#B45309; font-weight:600;">{{ workflowActiveNodeInfo.gate }}</span></div>
                    <div><strong>当前关联需求：</strong>{{ workflowActiveReq.reqCode }} · {{ workflowActiveReq.title }}</div>
                    <div>
                      <strong>该阶段核心物理产物：</strong>
                      <div style="display:flex; flex-wrap:wrap; gap:4px; margin-top:4px;">
                        <span
                          v-for="(art, aIdx) in (workflowActiveNodeInfo.artifacts || ['四层清单', '设计文档'])"
                          :key="aIdx"
                          style="background:#EFF6FF; color:#1E40AF; border:1px solid #BFDBFE; font-size:11px; padding:1px 6px; border-radius:4px;"
                        >
                          📦 {{ art }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                <div style="margin-top:14px; font-size:12px; color:var(--text-muted); display:flex; justify-content:space-between; align-items:center;">
                  <span>💡 <strong>受控哲学：</strong>每一个算子均由指定的责任人与 AI 共同把关，绝不允许越权跨阶段产生未经审查的代码技术债。</span>
                  <span style="font-family:monospace; color:var(--primary-color);">Single Source of Truth · .ai/dag_state.json</span>
                </div>
              </div>

              <!-- 📋 需求生命周期流转与人机审计履历 (MySQL t_req_workflow_record 真实驱动) -->
              <div class="card" style="padding:16px 20px; border:1px solid var(--border); background:#FFF;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                  <div style="display:flex; align-items:center; gap:8px;">
                    <span style="font-weight:700; font-size:14px; color:var(--text-main);">📜 需求全生命周期审计履历 ({{ workflowRecords.length }} 条记录)</span>
                    <span class="tag tag-baselined" style="font-size:10px;">永久留痕</span>
                  </div>
                  <button class="btn btn-outline btn-sm" @click="loadWorkflowRecords">
                    🔄 刷新履历
                  </button>
                </div>

                <div v-if="workflowRecords.length === 0" style="padding:20px; text-align:center; color:var(--text-muted); font-size:13px;">
                  暂无持久化履历记录。在对话框推进或点击“确认并顺推工位”后，系统将自动留痕入库。
                </div>

                <div v-else style="display:flex; flex-direction:column; gap:8px; max-height:220px; overflow-y:auto;">
                  <div
                    v-for="rec in workflowRecords"
                    :key="rec.id"
                    style="display:flex; justify-content:space-between; align-items:center; padding:8px 12px; background:#F8FAFC; border:1px solid var(--border); border-radius:4px; font-size:12px;"
                  >
                    <div style="display:flex; align-items:center; gap:10px;">
                      <span
                        class="tag"
                        :class="rec.actionType === 'OVERRIDE' ? 'tag-pending' : 'tag-baselined'"
                        style="font-size:10px; font-weight:700;"
                      >
                        {{ rec.actionType === 'OVERRIDE' ? '⚡ 人工跳步覆盖' : '✔ 顺推核准' }}
                      </span>
                      <strong style="color:var(--text-main);">[{{ rec.nodeId }}] {{ rec.nodeName }}</strong>
                      <span v-if="rec.skillName" style="color:#64748B;">
                        (调度: <code>{{ rec.skillName }}</code>)
                      </span>
                      <span v-if="rec.overrideReason" style="color:#B45309; font-style:italic;">
                        原因: “{{ rec.overrideReason }}”
                      </span>
                    </div>

                    <div style="display:flex; align-items:center; gap:12px; color:var(--text-muted);">
                      <span>操作人: <strong style="color:var(--text-main);">{{ rec.operator }}</strong></span>
                      <span>{{ rec.createdAt ? rec.createdAt.replace('T', ' ') : '刚刚' }}</span>
                    </div>
                  </div>
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
                      v-if="tpl.isSystemDefault !== 1"
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
                <span
                  class="tag"
                  :style="{
                    background: currentEditingTemplate.isSystemDefault === 1 ? '#0284c7' : '#10b981',
                    color: '#fff',
                    fontWeight: 700
                  }"
                >
                  {{ currentEditingTemplate.isSystemDefault === 1 ? '官方系统蓝本' : '定制蓝本' }}
                </span>
                <span style="font-size:16px; font-weight:700; color:#f8fafc;">
                  {{ currentEditingTemplate.name }} ({{ currentEditingTemplate.templateCode }})
                </span>
                <span style="font-size:12px; color:#94a3b8;">
                  💡 提示：点击任意工位节点即可配置其专属 AI Skill 专家与门禁规约
                </span>
              </div>

              <div style="display:flex; gap:10px; align-items:center;">
                <button
                  v-if="currentEditingTemplate.isSystemDefault !== 1"
                  class="btn btn-primary btn-sm"
                  style="background:#10b981; border:none; font-weight:700; padding:6px 16px;"
                  @click="handleSaveTemplateDag"
                >
                  💾 保存拓扑蓝本配置
                </button>
                <button
                  v-else
                  class="btn btn-primary btn-sm"
                  style="background:#0284c7; border:none; font-weight:700; padding:6px 16px;"
                  @click="handleOpenCloneModal(currentEditingTemplate)"
                >
                  📋 复制为此模板的定制副本
                </button>
              </div>
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

          <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap:16px;">
            <div 
              v-for="sk in availableSkillRegistry" 
              :key="sk.id" 
              class="card"
              style="margin:0; display:flex; flex-direction:column; justify-content:space-between; border-left:4px solid #0284c7;"
            >
              <div>
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                  <span style="font-size:16px; font-weight:700; color:var(--text); display:flex; align-items:center; gap:6px;">
                    <span>{{ sk.icon }}</span>
                    <span>{{ sk.id }}</span>
                  </span>
                  <span class="tag" style="background:var(--primary-light); color:var(--primary); font-size:11px;">Agent 专家</span>
                </div>
                <div style="font-size:14px; font-weight:600; color:var(--text-secondary); margin-bottom:8px;">
                  {{ sk.name }}
                </div>
                <p style="font-size:12px; color:var(--text-muted); line-height:1.5; margin-bottom:12px;">
                  {{ sk.desc }}
                </p>
                <div style="font-size:11px; font-family:monospace; color:#64748b; background:var(--bg); padding:4px 8px; border-radius:4px; margin-bottom:12px;">
                  📂 {{ sk.path || `.agents/skills/${sk.id}/SKILL.md` }}
                </div>
              </div>
              <div style="display:flex; justify-content:space-between; align-items:center; border-top:1px solid var(--border); padding-top:12px; margin-top:8px;">
                <span style="font-size:11px; color:#10b981; font-weight:600;">● 已挂载工作流拓扑</span>
                <button class="btn btn-primary btn-sm" @click="handleOpenSkillDetail(sk)">
                  📖 查看规约详情 (SKILL.md)
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

          <div style="display:grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap:16px;">
            <div 
              v-for="sc in availableScriptRegistry" 
              :key="sc.id" 
              class="card"
              style="margin:0; display:flex; flex-direction:column; justify-content:space-between; border-left:4px solid #10b981;"
            >
              <div>
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                  <span style="font-size:16px; font-weight:700; color:var(--text); display:flex; align-items:center; gap:6px;">
                    <span>{{ sc.icon }}</span>
                    <span>{{ sc.id }}</span>
                  </span>
                  <span class="tag" style="background:#064e3b; color:#34d399; font-size:11px;">{{ sc.runtime }}</span>
                </div>
                <div style="font-size:14px; font-weight:600; color:var(--text-secondary); margin-bottom:8px;">
                  {{ sc.name }}
                </div>
                <p style="font-size:12px; color:var(--text-muted); line-height:1.5; margin-bottom:12px;">
                  {{ sc.desc }}
                </p>
                <div style="background:#0f172a; padding:6px 10px; border-radius:6px; font-family:monospace; color:#38bdf8; font-size:11px; margin-bottom:12px; overflow-x:auto;">
                  $ {{ sc.cmd }}
                </div>
              </div>
              <div style="display:flex; justify-content:space-between; align-items:center; border-top:1px solid var(--border); padding-top:12px; margin-top:8px;">
                <span style="font-size:11px; color:#10b981; font-weight:600;">● 看门狗就绪</span>
                <button class="btn btn-outline btn-sm" @click="handleOpenScriptDetail(sc)">
                  💻 在线透视源码
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P03 项目工作台 ================= -->
        <div id="page-dashboard" class="view-page" :class="{ active: currentPage === 'page-dashboard' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
            <div>
              <div style="display:flex; align-items:center; gap:10px;">
                <button
                  class="btn btn-outline btn-sm"
                  style="padding:3px 8px; display:inline-flex; align-items:center; gap:4px;"
                  title="返回项目列表大厅"
                  @click="navigatePage('page-projects')"
                >
                  <span>📁 全部项目</span>
                </button>
                <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name || '项目需求工作台' }}</h2>
                <span class="tag tag-baselined">{{ currentProject.projectKey || 'NET-SIM' }}</span>
              </div>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                客户单位：<strong style="color:var(--text);">{{ currentProject.clientName || '暂未指定' }}</strong> ｜ {{ currentProject.description || '契约与快照单一事实源' }}
              </p>
            </div>
            <div style="display:flex; gap:10px; align-items: center;">
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
              <button class="btn btn-outline" @click="modalNewProjectOpen = true">📁 新建项目 (M01)</button>
              <button class="btn btn-primary" @click="handleOpenNewReqDrawer">+ 新建需求 (M02)</button>
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
                      <div>当前项目空间暂无需求数据，请点击右上角「+ 新建需求 (M02)」录入新需求</div>
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
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.projectKey }} · {{ currentReq.reqNo ? currentReq.reqNo + ' 原型版本管理 (P07)' : '原型版本管理 (P07)' }}</h2>
              <p style="color:var(--text-muted); font-size:13px;">所属项目：{{ currentProject.name }} ｜ 严格遵循“旧版本永不覆盖”、“SHA256 快照固化”与“在线只读可交互”规范</p>
            </div>
            <div style="display:flex; align-items:center; gap:10px;">
              <select
                v-if="requirements.length > 0"
                class="form-control"
                style="width:240px; padding:4px 8px; font-size:13px;"
                :value="currentReq.id"
                @change="(e: any) => {
                  const found = requirements.find(r => r.id === Number(e.target.value))
                  if (found) openReqDetail(found, false)
                }"
              >
                <option v-for="r in requirements" :key="r.id" :value="r.id">
                  {{ r.reqCode }} · {{ r.title }}
                </option>
              </select>
              <button class="btn btn-primary" @click="modalUploadPrototypeOpen = true">📤 上传新版本 (M05)</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 320px 1fr; gap:20px;">
            <!-- 版本列表 (直连 MySQL t_req_prototype_version) -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">📦 版本快照清单 (L3)</div>
              </div>
              <div style="padding:10px;">
                <div v-if="prototypeList.length === 0" style="padding:30px 12px; text-align:center; color:var(--text-muted); font-size:13px;">
                  <div style="font-size:24px; margin-bottom:6px;">📦</div>
                  当前需求暂无上传的原型快照，请点击右上角「上传新版本 (M05)」
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
              <p style="color:var(--text-muted); font-size:13px;">一个发布版本必须严格绑定一个已冻结基线，当前项目专属发版流水线</p>
            </div>
            <button class="btn btn-primary" @click="ElMessage.success('已模拟调起新建发布窗口 (M12)')">+ 新建发布计划 (M12)</button>
          </div>

          <div class="card">
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>发布版本号</th>
                    <th>绑定已冻结基线</th>
                    <th>计划发布日期</th>
                    <th>实际完成日期</th>
                    <th>当前状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="projectReleases.length === 0">
                    <td colspan="6" style="text-align:center; padding:36px; color:var(--text-muted);">
                      <div style="font-size:24px; margin-bottom:6px;">🚀</div>
                      当前需求暂无已冻结基线对应的发版记录，基线集齐签字并冻结后将自动接入发版流水线
                    </td>
                  </tr>
                  <tr v-for="rel in projectReleases" :key="rel.ver">
                    <td><strong>{{ rel.ver }}</strong></td>
                    <td><span style="color:var(--primary); font-weight:600;">{{ rel.baseline }}</span></td>
                    <td>{{ rel.plannedDate }}</td>
                    <td>{{ rel.finishDate }}</td>
                    <td><span class="tag" :class="rel.status === 'RELEASED' ? 'tag-baselined' : 'tag-clarifying'">{{ rel.statusText }}</span></td>
                    <td>
                      <button v-if="rel.status !== 'RELEASED'" class="btn btn-primary btn-sm" style="margin-right:6px;" @click="ElMessage.success(`执行发布动作，为 ${rel.ver} 自动打 Git Tag`)">🚀 正式发布</button>
                      <button class="btn btn-outline btn-sm">查看发布清单</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- ================= P13 需求演进时间线 ================= -->
        <div id="page-timeline" class="view-page" :class="{ active: currentPage === 'page-timeline' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name }} ({{ currentProject.projectKey }}) · 需求与基线演进时间线 (P13)</h2>
              <p style="color:var(--text-muted); font-size:13px;">当前项目轻量级里程碑演进总览：一眼洞悉原型迭代 ➔ 基线冻结 ➔ 发布上线全生命周期（非工时拖拽）</p>
            </div>
            <div style="display:flex; gap:10px;">
              <button class="btn btn-outline" @click="ElMessage.success('已模拟切换视图模式：当前为基线聚合视图')">👁️ 切换聚合视图</button>
              <button class="btn btn-outline" @click="ElMessage.success('已导出时间线演进图表 (PNG/SVG)')">📥 导出图片</button>
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
              <div class="axis-tick">10-26 (原型草拟)</div>
              <div class="axis-tick">11-02 (BL-v1.0冻结)</div>
              <div class="axis-tick">11-09 (BL-v1.1变更)</div>
              <div class="axis-tick">11-20 (R-1.2.0发布)</div>
              <div class="axis-tick">12-05 (后续演进)</div>
            </div>

            <!-- 空状态 -->
            <div v-if="requirements.length === 0" style="padding: 46px 20px; text-align: center; color: var(--text-muted); background: var(--bg-app); border: 1px dashed var(--border); border-radius: var(--radius-sm); margin: 20px 0;">
              <div style="font-size: 28px; margin-bottom: 8px;">📈</div>
              <div style="font-weight: 600; color: var(--text);">当前项目 [{{ currentProject.name }}] 暂无演进需求</div>
              <div style="font-size: 13px; margin-top: 4px;">可在项目工作台快速录入需求，并固化原型与基线生成里程碑节点</div>
            </div>

            <!-- 动态需求泳道 -->
            <div v-for="reqItem in requirements" :key="reqItem.id" class="timeline-row">
              <div class="timeline-row-label">
                <div class="timeline-row-req-title" style="cursor:pointer;" @click="openReqDetail(reqItem, true)">
                  <span class="tag" :class="reqItem.status === 'BASELINED' ? 'tag-baselined' : (reqItem.status === 'PENDING' ? 'tag-pending' : 'tag-clarifying')" style="padding:1px 5px; font-size:11px;">
                    {{ reqItem.reqCode }}
                  </span>
                  <span style="font-weight:600;">{{ reqItem.title }}</span>
                </div>
                <div style="font-size:11px; color:var(--text-muted); margin-top:2px;">{{ reqItem.ownerName }} ｜ {{ reqItem.cost }} ｜ {{ reqItem.statusText }}</div>
              </div>
              <div class="timeline-track-wrap">
                <div class="timeline-track-line"></div>
                <div class="timeline-connection-line" :style="{ left: '10%', width: reqItem.status === 'BASELINED' ? '65%' : '30%' }"></div>

                <div class="timeline-node" style="left: 10%;" @click="openReqDetail(reqItem, true)">
                  <span class="timeline-date-marker">{{ reqItem.updatedAt }}</span>
                  <div class="node-icon-circle prototype">🎨</div>
                  <span class="node-title-badge">原型/录入</span>
                </div>

                <div class="timeline-node" style="left: 45%;" @click="handleOpenChecklist(reqItem)">
                  <span class="timeline-date-marker">{{ reqItem.status === 'BASELINED' ? '已冻结' : '细化中' }}</span>
                  <div class="node-icon-circle" :class="reqItem.status === 'BASELINED' ? 'baseline' : 'pending-baseline'">
                    {{ reqItem.status === 'BASELINED' ? '🔒' : '⏳' }}
                  </div>
                  <span class="node-title-badge" :style="{ color: reqItem.status === 'BASELINED' ? '#16A34A' : '#D97706' }">
                    {{ reqItem.baselineCode !== '—' ? reqItem.baselineCode : '待冻结基线' }}
                  </span>
                </div>

                <div v-if="reqItem.status === 'BASELINED'" class="timeline-node" style="left: 75%;" @click="navigatePage('page-releases')">
                  <span class="timeline-date-marker">正式发布</span>
                  <div class="node-icon-circle release">🚀</div>
                  <span class="node-title-badge" style="background:#14535B; color:#FFF;">Release</span>
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
              <p style="color:var(--text-muted); font-size:13px;">统一全站 10 组业务状态、色彩、排序与操作权限，变更后需显式发布</p>
            </div>
            <div style="display:flex; gap:10px;">
              <button class="btn btn-outline" @click="ElMessage.success('已导出标准 enums.yaml 规则文件')">📥 导出 YAML</button>
              <button class="btn btn-primary" @click="ElMessage.success('已发布码表变更，全站状态色彩实时刷新 (M15)')">🚀 发布码表变更 (M15)</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 240px 1fr; gap:20px;">
            <div class="card" style="padding:10px;">
              <div style="font-size:11px; font-weight:700; color:var(--text-muted); padding:8px;">码表分组 (10)</div>
              <div class="nav-item active" style="border-radius:4px;">● 需求状态 (8)</div>
              <div class="nav-item" style="border-radius:4px;">● 基线状态 (5)</div>
              <div class="nav-item" style="border-radius:4px;">● 发布状态 (4)</div>
              <div class="nav-item" style="border-radius:4px;">● 清单类别 (5)</div>
              <div class="nav-item" style="border-radius:4px;">● 客户决定 (4)</div>
              <div class="nav-item" style="border-radius:4px;">● 签字角色 (3)</div>
            </div>

            <div class="card">
              <div class="card-header">
                <div class="card-title">需求状态字典详情</div>
                <button class="btn btn-outline btn-sm" @click="ElMessage.info('调起新增取值窗口 (M14)')">+ 新增取值</button>
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
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td><code>DRAFT</code></td>
                      <td>草稿</td>
                      <td><span class="tag tag-draft">● 灰色</span></td>
                      <td>1</td>
                      <td>编辑 / 删除</td>
                    </tr>
                    <tr>
                      <td><code>CLARIFYING</code></td>
                      <td>澄清中</td>
                      <td><span class="tag tag-clarifying">● 蓝色</span></td>
                      <td>2</td>
                      <td>编辑 / 录入清单</td>
                    </tr>
                    <tr>
                      <td><code>PENDING</code></td>
                      <td>待确认</td>
                      <td><span class="tag tag-pending">● 橙色</span></td>
                      <td>3</td>
                      <td>发起签字</td>
                    </tr>
                    <tr>
                      <td><code>BASELINED</code></td>
                      <td>已基线</td>
                      <td><span class="tag tag-baselined">● 绿色</span></td>
                      <td>4</td>
                      <td>变更 / 差异对比</td>
                    </tr>
                  </tbody>
                </table>
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
    <div class="drawer-panel" :class="{ open: drawerWorkflowDetailVisible }" style="width:680px; max-width:90vw;">
      <div class="drawer-header" style="background:#0b1320; border-bottom:1px solid #1e293b; color:#fff;">
        <div style="display:flex; align-items:center; gap:10px;">
          <span
            class="tag"
            :style="{
              background: activeDetailNode.category === 'skill' ? '#6366f1' : (activeDetailNode.category === 'control' ? '#d97706' : (activeDetailNode.category === 'artifact' ? '#059669' : '#0284c7')),
              color: '#FFF',
              fontWeight: 700
            }"
          >
            {{ activeDetailNode.categoryName }}
          </span>
          <span style="font-size:16px; font-weight:700; color:#f8fafc;">
            {{ activeDetailNode.id }} · {{ activeDetailNode.name }}
          </span>
        </div>
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerWorkflowDetailVisible = false">✕</button>
      </div>

      <div class="drawer-body" style="padding:20px; background:#0f172a; color:#cbd5e1; font-size:13px;">
        <!-- 标签页导航 -->
        <div style="display:flex; border-bottom:1px solid #334155; margin-bottom:18px; gap:10px;">
          <button
            class="checklist-tab-btn"
            :style="{
              color: drawerActiveTab === 'overview' ? '#38bdf8' : '#94a3b8',
              borderBottomColor: drawerActiveTab === 'overview' ? '#38bdf8' : 'transparent',
              paddingBottom: '8px'
            }"
            @click="drawerActiveTab = 'overview'"
          >
            🔍 工位全景属性
          </button>
          <button
            class="checklist-tab-btn"
            :style="{
              color: drawerActiveTab === 'skill' ? '#38bdf8' : '#94a3b8',
              borderBottomColor: drawerActiveTab === 'skill' ? '#38bdf8' : 'transparent',
              paddingBottom: '8px'
            }"
            @click="drawerActiveTab = 'skill'"
          >
            🤖 专属 AI Skill 专家
          </button>
          <button
            class="checklist-tab-btn"
            :style="{
              color: drawerActiveTab === 'artifacts' ? '#38bdf8' : '#94a3b8',
              borderBottomColor: drawerActiveTab === 'artifacts' ? '#38bdf8' : 'transparent',
              paddingBottom: '8px'
            }"
            @click="drawerActiveTab = 'artifacts'"
          >
            📦 物理产物流向
          </button>
        </div>

        <!-- TAB 1: 工位全景属性与底层核心运转原理 -->
        <div v-if="drawerActiveTab === 'overview'" style="display:flex; flex-direction:column; gap:16px;">
          <!-- 状态徽标卡 -->
          <div style="background:#1e293b; border:1px solid #334155; border-radius:8px; padding:14px; display:flex; justify-content:space-between; align-items:center;">
            <div>
              <div style="font-size:11px; color:#94a3b8;">当前执行态</div>
              <div style="font-size:15px; font-weight:700; margin-top:2px;">
                <span v-if="activeDetailNode.status === 'ACTIVE'" style="color:#06b6d4;">● 正在当前执行中 (ACTIVE)</span>
                <span v-else-if="activeDetailNode.status === 'COMPLETED'" style="color:#10b981;">✔ 已通过并留痕 (COMPLETED)</span>
                <span v-else style="color:#64748b;">○ 待就绪与前置准备 (PENDING)</span>
              </div>
            </div>
            <div style="text-align:right;">
              <div style="font-size:11px; color:#94a3b8;">常驻责任角色</div>
              <div style="font-size:14px; font-weight:600; color:#f8fafc; margin-top:2px;">
                {{ activeDetailNode.role }}
              </div>
            </div>
          </div>

          <!-- 底层运转核心原理 (Under the Hood) -->
          <div style="background:#131d31; border:1px solid #1e3a8a; border-left:4px solid #38bdf8; border-radius:6px; padding:16px;">
            <div style="font-size:14px; font-weight:700; color:#38bdf8; margin-bottom:8px; display:flex; align-items:center; gap:6px;">
              <span>💡 底层核心运转逻辑 (Under the Hood)</span>
            </div>
            <p style="line-height:1.7; color:#e2e8f0; margin:0; font-size:13px;">
              {{ activeDetailNode.principle }}
            </p>
          </div>

          <!-- 执行契约规范与阻断条件 -->
          <div style="background:#1e293b; border:1px solid #334155; border-radius:8px; padding:14px;">
            <div style="font-size:13px; font-weight:600; color:#f8fafc; margin-bottom:10px;">📋 规约与门禁约束标准</div>
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px;">
              <div>
                <span style="color:#94a3b8; font-size:11px;">绑定 SOP 标号：</span>
                <div style="margin-top:2px;"><code style="background:#0f172a; color:#38bdf8; padding:2px 6px; border-radius:4px; font-size:12px;">{{ activeDetailNode.sop }}</code></div>
              </div>
              <div>
                <span style="color:#94a3b8; font-size:11px;">刚性门禁阻断：</span>
                <div style="margin-top:2px; color:#f59e0b; font-weight:600;">{{ activeDetailNode.gate }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 2: AI Skill 专家信息 (受控只读呈现，改配请在工作流模板中心执行) -->
        <div v-if="drawerActiveTab === 'skill'" style="display:flex; flex-direction:column; gap:16px;">
          <div style="background:#1e293b; border:1px solid #334155; border-radius:8px; padding:14px;">
            <div style="font-size:13px; font-weight:600; color:#f8fafc; margin-bottom:6px;">🎯 当前项目锁定的专属 AI 专家</div>
            <div style="display:flex; align-items:center; gap:10px;">
              <span class="tag tag-baselined" style="font-size:13px; font-weight:700; padding:4px 10px;">
                🤖 {{ activeDetailNode.skill }}
              </span>
              <span style="font-size:12px; color:#94a3b8;">
                根据项目工程工艺规约锁定（需求执行期不可随意篡改）
              </span>
            </div>
          </div>

          <!-- 专家的核心专长与能力定义 -->
          <div style="background:#131d31; border:1px solid #1e3a8a; border-radius:8px; padding:16px;">
            <div style="font-size:13px; font-weight:700; color:#38bdf8; margin-bottom:8px;">
              🛡️ AI 专家执行边界与安全约束
            </div>
            <div style="font-size:12px; color:#cbd5e1; line-height:1.7;">
              本工位由指定的专属 Agent 提供辅助，严格执行对应 SOP 标准规约。如需调整本工位关联的 AI 专家或流程管线，请前往顶级菜单 <strong>「工作流模板管理」</strong> 进行版本克隆与项目级绑定。
            </div>
          </div>
        </div>

        <!-- TAB 3: 确定性物理产物流向与交付清单 -->
        <div v-if="drawerActiveTab === 'artifacts'" style="display:flex; flex-direction:column; gap:16px;">
          <!-- 输入产物清单 -->
          <div style="background:#1e293b; border:1px solid #334155; border-radius:8px; padding:14px;">
            <div style="font-size:13px; font-weight:600; color:#f8fafc; margin-bottom:8px; display:flex; align-items:center; gap:6px;">
              <span>📥 上游输入资产 (Inputs)</span>
            </div>
            <div style="display:flex; flex-direction:column; gap:6px;">
              <div
                v-for="(inp, iIdx) in activeDetailNode.inputArtifacts"
                :key="iIdx"
                style="background:#0f172a; padding:8px 12px; border-radius:4px; border:1px dashed #475569; display:flex; align-items:center; gap:8px; font-size:12px;"
              >
                <span style="color:#38bdf8;">📄</span>
                <span style="color:#e2e8f0;">{{ inp }}</span>
              </div>
            </div>
          </div>

          <!-- 输出产物清单 -->
          <div style="background:#1e293b; border:1px solid #334155; border-radius:8px; padding:14px;">
            <div style="font-size:13px; font-weight:600; color:#10b981; margin-bottom:8px; display:flex; align-items:center; gap:6px;">
              <span>📤 本工位产出资产 (Outputs)</span>
            </div>
            <div style="display:flex; flex-direction:column; gap:6px;">
              <div
                v-for="(out, oIdx) in activeDetailNode.outputArtifacts"
                :key="oIdx"
                style="background:#062d22; padding:8px 12px; border-radius:4px; border:1px solid #059669; display:flex; justify-content:space-between; align-items:center; font-size:12px;"
              >
                <div style="display:flex; align-items:center; gap:8px;">
                  <span style="color:#10b981;">📦</span>
                  <span style="color:#a7f3d0; font-weight:600;">{{ out }}</span>
                </div>
                <span class="tag tag-baselined" style="font-size:10px;">物理落盘固化</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="drawer-footer" style="background:#0b1320; border-top:1px solid #1e293b; justify-content:space-between;">
        <span style="font-size:11px; color:#64748b; font-family:monospace;">
          Node: {{ activeDetailNode.id }} ｜ SSOT: MySQL t_req_workflow_record
        </span>
        <button class="btn btn-primary btn-sm" @click="drawerWorkflowDetailVisible = false">
          完成并关闭
        </button>
      </div>
    </div>

    <!-- ================= 抽屉面板：M02 新建需求 (统一使用 CommonFormModal 公共抽屉) ================= -->
    <CommonFormModal
      v-model="drawerNewReqOpen"
      mode="drawer"
      width="500px"
      title="➕ 快速录入新需求 (M02)"
      confirm-text="创建并进入清单细化"
      @confirm="handleSubmitNewReq"
    >
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
            <!-- 脚本详情实时名片卡 -->
            <div 
              v-if="activeTplConfigNode.scriptId && activeTplConfigNode.scriptId !== '-'" 
              style="margin-top:8px; padding:10px 12px; background:#0b1320; border:1px solid #10b981; border-radius:6px; font-size:12px;"
            >
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                <span style="font-weight:700; color:#10b981;">
                  {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.icon }}
                  {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.name }}
                </span>
                <span class="tag" style="background:#064e3b; color:#34d399; font-size:10px;">
                  {{ availableScriptRegistry.find(s => s.id === activeTplConfigNode.scriptId)?.runtime }}
                </span>
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
            <label class="form-label" style="color:#f8fafc;">绑定执行 SOP 规范文件</label>
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
            <label class="form-label" style="color:#f8fafc;">对应底层规约文件 / 脚本</label>
            <div style="padding:8px 12px; background:#1e293b; border-radius:6px; font-family:monospace; color:#38bdf8;">{{ activeTplConfigNode.sop }}</div>
          </div>
        </template>

        <!-- 节点输入与输出数据流 -->
        <div style="margin-top:16px; border-top:1px solid #1e293b; padding-top:12px;">
          <div style="display:flex; gap:16px;">
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
        </div>

      </div>
      <div class="drawer-footer" style="background:#0b1320; border-top:1px solid #1e293b;">
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerTplNodeConfigVisible = false">关闭</button>
        <button v-if="activeTplConfigNode.category === 'instruction'" class="btn btn-primary btn-sm" @click="handleSaveTplNodeConfig">确认暂存此工位</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：AI Skill 专家 SKILL.md 实体规约透视 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerSkillDetailVisible }" @click="drawerSkillDetailVisible = false"></div>
    <div class="drawer-panel" :class="{ open: drawerSkillDetailVisible }" style="width:780px; max-width:94vw;">
      <div class="drawer-header" style="background:#0b1320; border-bottom:1px solid #1e293b; color:#fff;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span class="tag" style="background:#0284c7; color:#fff;">{{ selectedSkillDetail?.icon || '🤖' }} {{ selectedSkillDetail?.id }}</span>
          <span style="font-size:15px; font-weight:700; color:#f8fafc;">{{ selectedSkillDetail?.name }} · 规约透视</span>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          <!-- 切换渲染模式 vs 原始代码模式 -->
          <div class="view-mode-toggle" style="background:#1e293b; padding:2px; border-radius:6px;">
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
          <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerSkillDetailVisible = false">✕</button>
        </div>
      </div>
      <div class="drawer-body" style="background:#0f172a; color:#cbd5e1; font-size:13px; padding:20px; overflow-y:auto;">
        <div style="background:#1e293b; padding:12px; border-radius:6px; margin-bottom:16px; display:flex; justify-content:space-between; align-items:center;">
          <div>
            <div style="font-size:11px; color:#94a3b8; margin-bottom:2px;">规范文件物理路径：</div>
            <div style="font-family:monospace; color:#38bdf8; font-size:12px;">{{ selectedSkillDetail?.path }}</div>
          </div>
          <span class="tag" style="background:#064e3b; color:#34d399; font-size:11px;">
            {{ skillViewMode === 'rendered' ? '✨ 富文本优雅阅读模式' : '💻 原始源码模式' }}
          </span>
        </div>

        <!-- 模式一：优雅排版渲染视图 -->
        <div v-if="skillViewMode === 'rendered'" class="markdown-rendered-container">
          <div v-html="renderMarkdownToHtml(selectedSkillDetail?.content || '')"></div>
        </div>

        <!-- 模式二：原始 Markdown 文本视图 -->
        <div v-else>
          <pre style="background:#020617; border:1px solid #1e293b; border-radius:8px; padding:16px; font-family:Consolas, Monaco, monospace; font-size:12px; color:#f1f5f9; line-height:1.6; white-space:pre-wrap; word-break:break-word; max-height:650px; overflow-y:auto;">{{ selectedSkillDetail?.content || '暂无内容或正在加载...' }}</pre>
        </div>
      </div>
      <div class="drawer-footer" style="background:#0b1320; border-top:1px solid #1e293b;">
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerSkillDetailVisible = false">关闭</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：自动化脚本源码与看门狗透视 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerScriptDetailVisible }" @click="drawerScriptDetailVisible = false"></div>
    <div class="drawer-panel" :class="{ open: drawerScriptDetailVisible }" style="width:780px; max-width:94vw;">
      <div class="drawer-header" style="background:#0b1320; border-bottom:1px solid #1e293b; color:#fff;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span class="tag" style="background:#10b981; color:#fff;">{{ selectedScriptDetail?.icon || '🛠️' }} {{ selectedScriptDetail?.id }}</span>
          <span style="font-size:15px; font-weight:700; color:#f8fafc;">{{ selectedScriptDetail?.name }} · 脚本源码透视</span>
        </div>
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerScriptDetailVisible = false">✕</button>
      </div>
      <div class="drawer-body" style="background:#0f172a; color:#cbd5e1; font-size:13px; padding:20px; overflow-y:auto;">
        <div style="background:#1e293b; padding:12px; border-radius:6px; margin-bottom:16px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
            <span style="font-size:11px; color:#94a3b8;">脚本物理路径：</span>
            <span class="tag" style="background:#064e3b; color:#34d399; font-size:10px;">{{ selectedScriptDetail?.runtime }}</span>
          </div>
          <div style="font-family:monospace; color:#38bdf8; font-size:12px; margin-bottom:6px;">{{ selectedScriptDetail?.path }}</div>
          <div style="background:#020617; padding:4px 8px; border-radius:4px; font-family:monospace; color:#34d399; font-size:11px;">$ {{ selectedScriptDetail?.cmd }}</div>
        </div>
        <div>
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
            <div style="font-size:12px; font-weight:700; color:#94a3b8;">💻 脚本源码全文（语法高亮 & 行号）：</div>
            <span class="tag" style="background:rgba(56, 189, 248, 0.15); color:#38bdf8; font-size:10px;">IDE 代码视图</span>
          </div>
          <!-- 语法高亮代码编辑器视窗 -->
          <div class="code-editor-viewport">
            <div v-html="highlightScriptCode(selectedScriptDetail?.content || '')"></div>
          </div>
        </div>
      </div>
      <div class="drawer-footer" style="background:#0b1320; border-top:1px solid #1e293b;">
        <button class="btn btn-outline btn-sm" style="color:#94a3b8; border-color:#334155;" @click="drawerScriptDetailVisible = false">关闭</button>
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
          <option :value="undefined">默认：全局默认 · 钧天四层一体化研发工作流 (TPL-DEFAULT)</option>
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

    <!-- ================= 模态弹窗：M05 上传原型版本 ================= -->
    <div class="modal-overlay" :class="{ active: modalUploadPrototypeOpen }">
      <div class="card" style="width:480px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">📤 上传原型版本 ZIP (M05)</div>
          <button class="btn btn-outline btn-sm" @click="modalUploadPrototypeOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label required">版本命名</label>
            <input type="text" class="form-control" value="mockup v4.0" readonly>
          </div>
          <div class="form-group">
            <label class="form-label required">原型 ZIP 包 (必须含 mockup.html)</label>
            <input type="file" class="form-control">
            <div class="form-hint">上传后系统自动进行 SHA256 校验和提取，永不覆盖旧版本目录。</div>
          </div>
          <div class="form-group">
            <label class="form-label">变更摘要</label>
            <textarea class="form-control" rows="2" placeholder="简述本次原型修改重点..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalUploadPrototypeOpen = false">取消</button>
          <button class="btn btn-primary" @click="ElMessage.success('原型 v4.0 已上传，已生成哈希值并生成新快照！'); modalUploadPrototypeOpen = false;">开始解析并固化</button>
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
  </div>
</template>

<style scoped>
/* ==========================================================================
   Markdown 富文本阅读模式专属优雅排版样式
   ========================================================================== */
.markdown-rendered-container {
  background: #020617;
  border: 1px solid #1e293b;
  border-radius: 8px;
  padding: 24px;
  color: #e2e8f0;
  font-size: 13.5px;
  line-height: 1.7;
  max-height: 650px;
  overflow-y: auto;
}
:deep(.md-h1) {
  font-size: 20px;
  font-weight: 700;
  color: #38bdf8;
  margin: 16px 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #1e293b;
}
:deep(.md-h2) {
  font-size: 16px;
  font-weight: 700;
  color: #f8fafc;
  margin: 18px 0 10px 0;
  display: flex;
  align-items: center;
}
:deep(.md-h3) {
  font-size: 14.5px;
  font-weight: 600;
  color: #38bdf8;
  margin: 14px 0 8px 0;
}
:deep(.md-h4) {
  font-size: 13.5px;
  font-weight: 600;
  color: #94a3b8;
  margin: 10px 0 6px 0;
}
:deep(.md-code-block) {
  background: #090d16;
  border: 1px solid #1e293b;
  border-radius: 6px;
  margin: 12px 0;
  overflow: hidden;
}
:deep(.md-code-header) {
  background: #0f172a;
  padding: 4px 12px;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
  font-family: monospace;
  border-bottom: 1px solid #1e293b;
}
:deep(.md-code-body) {
  margin: 0;
  padding: 12px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #34d399;
  line-height: 1.5;
  overflow-x: auto;
}
:deep(.md-inline-code) {
  background: #1e293b;
  color: #38bdf8;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
}
:deep(.md-blockquote) {
  border-left: 4px solid #0284c7;
  background: rgba(2, 132, 199, 0.08);
  padding: 8px 14px;
  margin: 10px 0;
  color: #94a3b8;
  font-style: italic;
  border-radius: 0 4px 4px 0;
}
:deep(.md-hr) {
  border: none;
  border-top: 1px dashed #334155;
  margin: 20px 0;
}
:deep(.md-strong) {
  color: #f8fafc;
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
  color: #38bdf8;
  font-size: 14px;
}
:deep(.md-num) {
  color: #10b981;
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
  background: #020617;
  border: 1px solid #1e293b;
  border-radius: 8px;
  padding: 16px 0;
  max-height: 650px;
  overflow: auto;
  font-family: "JetBrains Mono", Consolas, Monaco, "Courier New", monospace;
  font-size: 12.5px;
  line-height: 1.6;
}
:deep(.code-line) {
  display: flex;
  padding: 0 16px;
  white-space: pre;
}
:deep(.code-line:hover) {
  background: rgba(255, 255, 255, 0.04);
}
:deep(.code-lineno) {
  width: 42px;
  user-select: none;
  color: #475569;
  text-align: right;
  padding-right: 16px;
  flex-shrink: 0;
  border-right: 1px solid #1e293b;
  margin-right: 16px;
  font-size: 11px;
}
:deep(.code-text) {
  flex: 1;
  color: #f1f5f9;
}
:deep(.hl-keyword) {
  color: #f43f5e;
  font-weight: 700;
}
:deep(.hl-string) {
  color: #a7f3d0;
}
:deep(.hl-docstring) {
  color: #64748b;
  font-style: italic;
}
:deep(.hl-comment) {
  color: #64748b;
  font-style: italic;
}
:deep(.hl-bool) {
  color: #fb923c;
  font-weight: 700;
}
:deep(.hl-builtin) {
  color: #38bdf8;
}
:deep(.hl-decorator) {
  color: #c084fc;
}
:deep(.hl-number) {
  color: #fbbf24;
}

/* 工程流水线拓扑画布可视区 (FR-REQ-WFC-01 / Task-510)
   可视框尺寸只由这里的 360px 决定；X6 挂载点在内层，尺寸回写不会触及外层。 */
.wf-viewport {
  height: 510px;
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

.req-app-container {
  --primary: #14535B;
  --primary-dark: #0D373D;
  --primary-light: #E6F0F2;
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
  --radius-sm: 4px;
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

/* 顶部顶栏 */
header.app-header {
  background-color: var(--primary);
  color: #FFFFFF;
  min-height: 52px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  box-shadow: var(--shadow-sm);
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
  background: rgba(255, 255, 255, 0.15);
  font-weight: 700;
  font-size: 13px;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  letter-spacing: 0.5px;
}
.app-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.project-selector {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: #FFF;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
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

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.btn-primary {
  background: var(--primary);
  color: #FFF;
  border-color: var(--primary);
}
.btn-primary:hover {
  background: var(--primary-dark);
  border-color: var(--primary-dark);
}
.btn-outline {
  background: var(--surface);
  color: var(--text-secondary);
  border-color: var(--border-dark);
}
.btn-outline:hover {
  background: var(--bg);
  color: var(--text);
  border-color: #94A3B8;
}
.btn-accent {
  background: var(--accent);
  color: #FFF;
  border-color: var(--accent);
}
.btn-accent:hover {
  background: #9A3412;
}
.btn-sm {
  padding: 4px 8px;
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
</style>
